package app.spur

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal data class SpurBackupInfo(
    val createdAt: Long,
    val appVersion: String,
    val tourCount: Int,
    val momentCount: Int,
    val mediaCount: Int,
)

private data class BackupPayload(
    val archivePath: String,
    val open: () -> InputStream,
)

private data class PreparedMoments(
    val encoded: Set<String>,
    val media: List<BackupPayload>,
)

private data class ExtractedBackup(
    val info: SpurBackupInfo,
    val database: File,
    val preferences: BackupPreferenceSnapshot,
    val mediaPaths: Set<String>,
)

internal data class SpurDatabaseSummary(val tourCount: Int)

private val SpurBackupMutex = Mutex()

internal suspend fun createSpurBackupFile(
    context: Context,
    destination: File,
): SpurBackupInfo = SpurBackupMutex.withLock {
    val appContext = context.applicationContext
    val database = File.createTempFile("spur-backup-database-", ".db", appContext.cacheDir)
    try {
        appContext.tourStore().copyDatabaseTo(database)
        val databaseSummary = validateSpurDatabase(database)
        val moments = prepareBackupMoments(appContext)
        val preferences = encodeBackupPreferences(
            appContext.captureBackupPreferences(moments.encoded),
        )
        val info = SpurBackupInfo(
            createdAt = System.currentTimeMillis(),
            appVersion = appContext.packageManager
                .getPackageInfo(appContext.packageName, 0)
                .versionName
                .orEmpty(),
            tourCount = databaseSummary.tourCount,
            momentCount = moments.encoded.size,
            mediaCount = moments.media.size,
        )
        val payloads = listOf(
            BackupPayload(BackupDatabaseEntry, database::inputStream),
            BackupPayload(BackupPreferencesEntry) { ByteArrayInputStream(preferences) },
        ) + moments.media
        destination.parentFile?.mkdirs()
        writeBackupArchive(destination, info, payloads)
        info
    } finally {
        database.delete()
    }
}

internal fun inspectSpurBackup(context: Context, uri: Uri): SpurBackupInfo =
    context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
        ZipInputStream(input).use { zip ->
            val first = zip.nextEntry ?: error("Das Backup ist leer.")
            require(!first.isDirectory && first.name == BackupManifestEntry)
            parseBackupManifest(zip.readEntryBytes(BackupMetadataMaximumBytes))
        }
    } ?: error("Das Backup konnte nicht geöffnet werden.")

internal suspend fun restoreSpurBackup(
    context: Context,
    uri: Uri,
): SpurBackupInfo = SpurBackupMutex.withLock {
    val appContext = context.applicationContext
    val store = appContext.tourStore()
    check(store.activeTour() == null) {
        "Während einer laufenden Tour kann kein Backup wiederhergestellt werden."
    }
    val staging = File(appContext.filesDir, "backup-restore-${UUID.randomUUID()}")
    check(staging.mkdir()) { "Das Backup konnte nicht vorbereitet werden." }
    val rollbackDatabase = File(staging, "rollback.db")
    val oldPreferences = appContext.captureBackupPreferences()
    val currentMedia = File(appContext.filesDir, "moments")
    val previousMedia = File(appContext.filesDir, "moments-before-restore-${UUID.randomUUID()}")
    var databaseReplaced = false
    var mediaReplaced = false
    try {
        val extracted = extractSpurBackup(appContext, uri, staging)
        store.copyDatabaseTo(rollbackDatabase)
        appContext.removeHomeAutoStart()
        store.replaceDatabaseFrom(extracted.database)
        databaseReplaced = true
        replaceMomentMedia(
            restored = File(staging, "moments").apply { mkdirs() },
            current = currentMedia,
            previous = previousMedia,
        )
        mediaReplaced = true
        appContext.applyBackupPreferences(
            extracted.preferences.withRestoredMomentPaths(
                filesDir = appContext.filesDir,
                archivedMedia = extracted.mediaPaths,
            ),
        )
        File(appContext.filesDir, "tour-previews").deleteRecursively()
        appContext.getSharedPreferences("tour-history", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        appContext.getSharedPreferences("tour-completion", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        reconcileRestoredHomeAutoStart(appContext)
        previousMedia.deleteRecursively()
        extracted.info
    } catch (error: Exception) {
        if (databaseReplaced) {
            runCatching { store.replaceDatabaseFrom(rollbackDatabase) }
                .exceptionOrNull()
                ?.let(error::addSuppressed)
        }
        if (mediaReplaced) {
            currentMedia.deleteRecursively()
            if (previousMedia.exists() && !previousMedia.renameTo(currentMedia)) {
                error.addSuppressed(IllegalStateException("Momentdateien konnten nicht zurückgesetzt werden."))
            }
        }
        runCatching { appContext.applyBackupPreferences(oldPreferences) }
            .exceptionOrNull()
            ?.let(error::addSuppressed)
        runCatching { reconcileRestoredHomeAutoStart(appContext) }
        throw error
    } finally {
        staging.deleteRecursively()
    }
}

private fun prepareBackupMoments(context: Context): PreparedMoments {
    val media = linkedMapOf<String, BackupPayload>()
    val encoded = context.loadMapMoments().mapTo(mutableSetOf()) { moment ->
        if (moment.type == MomentType.EMOJI) return@mapTo encodeMapMoment(moment)
        val directory = when (moment.type) {
            MomentType.PHOTO -> "photos"
            MomentType.VIDEO -> "videos"
            MomentType.VOICE -> "voice"
            MomentType.EMOJI -> error("Emoji moments do not use files")
        }
        val allowedDirectory = File(context.filesDir, "moments/$directory").canonicalFile
        val source = File(moment.payload).canonicalFile
        require(source.isFile && source.parentFile == allowedDirectory)
        require(isSafeBackupFileName(source.name))
        val archivePath = "moments/$directory/${source.name}"
        media.putIfAbsent(archivePath, BackupPayload(archivePath, source::inputStream))
        encodeMapMoment(moment.copy(payload = archivePath))
    }
    return PreparedMoments(encoded = encoded, media = media.values.toList())
}

private fun writeBackupArchive(
    destination: File,
    info: SpurBackupInfo,
    payloads: List<BackupPayload>,
) {
    val checksums = linkedMapOf<String, String>()
    ZipOutputStream(destination.outputStream().buffered()).use { zip ->
        zip.setLevel(Deflater.BEST_SPEED)
        zip.writeBytesEntry(
            BackupManifestEntry,
            backupManifest(info).toByteArray(Charsets.UTF_8),
        )
        payloads.forEach { payload ->
            val digest = MessageDigest.getInstance("SHA-256")
            zip.putNextEntry(ZipEntry(payload.archivePath))
            payload.open().buffered().use { input ->
                input.copyToDigest(zip, digest)
            }
            zip.closeEntry()
            checksums[payload.archivePath] = digest.hexDigest()
        }
        val checksumJson = JSONObject()
        checksums.forEach(checksumJson::put)
        zip.writeBytesEntry(
            BackupChecksumsEntry,
            checksumJson.toString().toByteArray(Charsets.UTF_8),
        )
    }
}

private fun extractSpurBackup(
    context: Context,
    uri: Uri,
    staging: File,
): ExtractedBackup {
    var info: SpurBackupInfo? = null
    var preferences: BackupPreferenceSnapshot? = null
    var database: File? = null
    var expectedChecksums: Map<String, String>? = null
    val observedChecksums = linkedMapOf<String, String>()
    val mediaPaths = mutableSetOf<String>()
    val seen = mutableSetOf<String>()
    var totalBytes = 0L
    context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && seen.add(entry.name))
                when {
                    entry.name == BackupManifestEntry -> {
                        require(info == null && seen.size == 1)
                        info = parseBackupManifest(zip.readEntryBytes(BackupMetadataMaximumBytes))
                    }
                    entry.name == BackupPreferencesEntry -> {
                        val bytes = zip.readEntryBytes(BackupPreferencesMaximumBytes)
                        totalBytes += bytes.size
                        preferences = decodeBackupPreferences(bytes)
                        observedChecksums[entry.name] = bytes.sha256()
                    }
                    entry.name == BackupDatabaseEntry -> {
                        val target = File(staging, BackupDatabaseEntry)
                        target.parentFile?.mkdirs()
                        val result = zip.copyEntryTo(target, BackupDatabaseMaximumBytes)
                        totalBytes += result.first
                        database = target
                        observedChecksums[entry.name] = result.second
                    }
                    entry.name == BackupChecksumsEntry -> {
                        expectedChecksums = parseChecksums(
                            zip.readEntryBytes(BackupMetadataMaximumBytes),
                        )
                    }
                    backupMediaTypeForPath(entry.name) != null -> {
                        require(mediaPaths.size < BackupMaximumMediaFiles)
                        val target = File(staging, entry.name)
                        target.parentFile?.mkdirs()
                        val result = zip.copyEntryTo(target, BackupMediaMaximumBytes)
                        totalBytes += result.first
                        mediaPaths += entry.name
                        observedChecksums[entry.name] = result.second
                    }
                    else -> error("Unbekannter Backup-Inhalt.")
                }
                require(totalBytes <= BackupTotalMaximumBytes)
                zip.closeEntry()
            }
        }
    } ?: error("Das Backup konnte nicht geöffnet werden.")
    val backupInfo = requireNotNull(info)
    val databaseFile = requireNotNull(database)
    val backupPreferences = requireNotNull(preferences)
    require(requireNotNull(expectedChecksums) == observedChecksums)
    val databaseSummary = validateSpurDatabase(databaseFile)
    val moments = backupPreferences.files.getValue("map-moments")["entries"] as? Set<*>
        ?: emptySet<String>()
    require(databaseSummary.tourCount == backupInfo.tourCount)
    require(moments.size == backupInfo.momentCount)
    require(mediaPaths.size == backupInfo.mediaCount)
    backupPreferences.withRestoredMomentPaths(context.filesDir, mediaPaths)
    return ExtractedBackup(backupInfo, databaseFile, backupPreferences, mediaPaths)
}

internal fun validateSpurDatabase(file: File): SpurDatabaseSummary {
    require(file.isFile && file.length() in 1..BackupDatabaseMaximumBytes)
    return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
        val version = database.rawQuery("PRAGMA user_version", null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
        require(version == SpurDatabaseVersion)
        val integrity = database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }
        require(integrity == "ok")
        database.requireExactColumns(
            table = "tours",
            expected = setOf("id", "started_at", "ended_at", "distance_meters", "activity"),
        )
        database.requireExactColumns(
            table = "track_points",
            expected = setOf(
                "id",
                "tour_id",
                "latitude",
                "longitude",
                "recorded_at",
                "accuracy_meters",
                "cluster_started_at",
                "cluster_sample_count",
            ),
        )
        val executableSchemaObjects = database.rawQuery(
            "SELECT COUNT(*) FROM sqlite_master WHERE type IN ('trigger', 'view')",
            null,
        ).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
        require(executableSchemaObjects == 0)
        val count = database.rawQuery("SELECT COUNT(*) FROM tours", null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
        database.rawQuery("SELECT COUNT(*) FROM track_points", null).use { it.moveToFirst() }
        SpurDatabaseSummary(count)
    }
}

private fun SQLiteDatabase.requireExactColumns(table: String, expected: Set<String>) {
    val actual = rawQuery("PRAGMA table_info($table)", null).use { cursor ->
        buildSet {
            while (cursor.moveToNext()) add(cursor.getString(1))
        }
    }
    require(actual == expected)
}

private fun replaceMomentMedia(restored: File, current: File, previous: File) {
    check(!previous.exists())
    val hadCurrent = current.exists()
    if (hadCurrent && !current.renameTo(previous)) {
        error("Die aktuellen Momentdateien konnten nicht gesichert werden.")
    }
    if (!restored.renameTo(current)) {
        if (hadCurrent) previous.renameTo(current)
        error("Die Momentdateien konnten nicht wiederhergestellt werden.")
    }
}

private fun reconcileRestoredHomeAutoStart(context: Context) {
    invalidateHomeAutoStartSettingsCache()
    val settings = context.loadHomeAutoStartSettings()
    if (!settings.enabled) {
        context.removeHomeAutoStart()
    } else if (!context.registerHomeAutoStart()) {
        context.saveHomeAutoStartSettings(settings.copy(enabled = false))
        context.removeHomeAutoStart()
    }
}

private fun backupManifest(info: SpurBackupInfo): String = JSONObject()
    .put("format", SpurBackupFormatVersion)
    .put("createdAt", info.createdAt)
    .put("appVersion", info.appVersion)
    .put("tourCount", info.tourCount)
    .put("momentCount", info.momentCount)
    .put("mediaCount", info.mediaCount)
    .toString()

private fun parseBackupManifest(bytes: ByteArray): SpurBackupInfo {
    val json = JSONObject(bytes.toString(Charsets.UTF_8))
    require(json.getInt("format") == SpurBackupFormatVersion)
    return SpurBackupInfo(
        createdAt = json.getLong("createdAt").also { require(it > 0L) },
        appVersion = json.getString("appVersion").take(80),
        tourCount = json.getInt("tourCount").also { require(it >= 0) },
        momentCount = json.getInt("momentCount").also { require(it >= 0) },
        mediaCount = json.getInt("mediaCount").also { require(it >= 0) },
    )
}

private fun parseChecksums(bytes: ByteArray): Map<String, String> {
    val json = JSONObject(bytes.toString(Charsets.UTF_8))
    return json.keys().asSequence().associateWith { key ->
        json.getString(key).also { checksum ->
            require(checksum.length == 64 && checksum.all { it in "0123456789abcdef" })
        }
    }
}

private fun ZipOutputStream.writeBytesEntry(name: String, bytes: ByteArray) {
    putNextEntry(ZipEntry(name))
    write(bytes)
    closeEntry()
}

private fun ZipInputStream.readEntryBytes(maximum: Long): ByteArray {
    val output = ByteArrayOutputStream()
    copyToLimited(output, maximum)
    return output.toByteArray()
}

private fun ZipInputStream.copyEntryTo(target: File, maximum: Long): Pair<Long, String> {
    val digest = MessageDigest.getInstance("SHA-256")
    val count = target.outputStream().buffered().use { output ->
        copyToLimited(output, maximum, digest)
    }
    return count to digest.hexDigest()
}

private fun InputStream.copyToLimited(
    output: OutputStream,
    maximum: Long,
    digest: MessageDigest? = null,
): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        require(total <= maximum)
        output.write(buffer, 0, read)
        digest?.update(buffer, 0, read)
    }
    return total
}

private fun InputStream.copyToDigest(output: OutputStream, digest: MessageDigest) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read < 0) return
        output.write(buffer, 0, read)
        digest.update(buffer, 0, read)
    }
}

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(this).toHex()

private fun MessageDigest.hexDigest(): String = digest().toHex()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private const val SpurBackupFormatVersion = 1
private const val SpurDatabaseVersion = 4
private const val BackupManifestEntry = "manifest.json"
private const val BackupDatabaseEntry = "database/spur.db"
private const val BackupPreferencesEntry = "preferences.json"
private const val BackupChecksumsEntry = "checksums.json"
private const val BackupMetadataMaximumBytes = 1L * 1024 * 1024
private const val BackupPreferencesMaximumBytes = 16L * 1024 * 1024
private const val BackupDatabaseMaximumBytes = 4L * 1024 * 1024 * 1024
private const val BackupMediaMaximumBytes = 20L * 1024 * 1024 * 1024
private const val BackupTotalMaximumBytes = 100L * 1024 * 1024 * 1024
private const val BackupMaximumMediaFiles = 100_000
