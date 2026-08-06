package app.spur

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

internal data class SpurBackupSettings(
    val treeUri: Uri?,
    val destinationName: String?,
    val automatic: Boolean,
    val latestDocumentUri: Uri?,
    val lastSuccessAt: Long?,
    val lastError: String?,
)

internal fun Context.loadSpurBackupSettings(): SpurBackupSettings {
    val preferences = getSharedPreferences(BackupSettingsPreferences, Context.MODE_PRIVATE)
    return SpurBackupSettings(
        treeUri = preferences.getString(BackupTreeUri, null)?.let(Uri::parse),
        destinationName = preferences.getString(BackupDestinationName, null),
        automatic = preferences.getBoolean(BackupAutomatic, false),
        latestDocumentUri = preferences.getString(BackupDocumentUri, null)?.let(Uri::parse),
        lastSuccessAt = preferences.getLong(BackupLastSuccessAt, 0L).takeIf { it > 0L },
        lastError = preferences.getString(BackupLastError, null),
    )
}

internal fun Context.selectSpurBackupFolder(uri: Uri) {
    contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
    )
    getSharedPreferences(BackupSettingsPreferences, Context.MODE_PRIVATE)
        .edit()
        .putString(BackupTreeUri, uri.toString())
        .putString(BackupDestinationName, backupDestinationName(uri))
        .remove(BackupDocumentUri)
        .remove(BackupLastError)
        .apply()
}

internal fun Context.setAutomaticBackupEnabled(enabled: Boolean) {
    getSharedPreferences(BackupSettingsPreferences, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(BackupAutomatic, enabled)
        .apply()
    if (enabled) {
        if (loadSpurBackupSettings().lastSuccessAt == null) {
            scheduleAutomaticBackup(delaySeconds = 0L)
        }
    } else {
        WorkManager.getInstance(applicationContext).cancelUniqueWork(AutomaticBackupWorkName)
    }
}

internal fun Context.scheduleAutomaticBackup(delaySeconds: Long = 20L) {
    val settings = loadSpurBackupSettings()
    if (!settings.automatic || settings.treeUri == null) return
    val request = OneTimeWorkRequestBuilder<SpurBackupWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiresStorageNotLow(true)
                .build(),
        )
        .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
        .build()
    WorkManager.getInstance(applicationContext).enqueueUniqueWork(
        AutomaticBackupWorkName,
        ExistingWorkPolicy.REPLACE,
        request,
    )
}

internal suspend fun Context.createBackupInSelectedFolder(): SpurBackupInfo =
    withContext(Dispatchers.IO) {
        val appContext = applicationContext
        val settings = appContext.loadSpurBackupSettings()
        val treeUri = requireNotNull(settings.treeUri) { "Bitte zuerst einen Speicherort wählen." }
        val local = File.createTempFile("spur-backup-", SpurBackupExtension, appContext.cacheDir)
        var newDocument: Uri? = null
        try {
            val info = createSpurBackupFile(appContext, local)
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            val displayName = backupFileName(info.createdAt)
            newDocument = requireNotNull(
                DocumentsContract.createDocument(
                    appContext.contentResolver,
                    parent,
                    BackupMimeType,
                    displayName,
                ),
            ) { "Die Backupdatei konnte nicht angelegt werden." }
            val copied = appContext.contentResolver
                .openOutputStream(newDocument, "w")
                ?.buffered()
                ?.use { output -> local.inputStream().buffered().use { it.copyTo(output) } }
                ?: error("Die Backupdatei konnte nicht geöffnet werden.")
            check(copied == local.length()) { "Das Backup wurde nicht vollständig geschrieben." }
            check(appContext.getSharedPreferences(BackupSettingsPreferences, Context.MODE_PRIVATE)
                .edit()
                .putString(BackupDocumentUri, newDocument.toString())
                .putLong(BackupLastSuccessAt, info.createdAt)
                .remove(BackupLastError)
                .commit()) { "Der Backupstatus konnte nicht gespeichert werden." }
            settings.latestDocumentUri
                ?.takeIf { it != newDocument }
                ?.let { old ->
                    runCatching {
                        DocumentsContract.deleteDocument(appContext.contentResolver, old)
                    }
                }
            info
        } catch (error: Exception) {
            newDocument?.let { document ->
                runCatching {
                    DocumentsContract.deleteDocument(appContext.contentResolver, document)
                }
            }
            appContext.recordBackupError(error)
            throw error
        } finally {
            local.delete()
        }
    }

class SpurBackupWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val settings = applicationContext.loadSpurBackupSettings()
        if (!settings.automatic || settings.treeUri == null) return Result.success()
        return try {
            applicationContext.createBackupInSelectedFolder()
            Result.success()
        } catch (_: SecurityException) {
            Result.failure()
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

private fun Context.backupDestinationName(uri: Uri): String {
    val documentUri = DocumentsContract.buildDocumentUriUsingTree(
        uri,
        DocumentsContract.getTreeDocumentId(uri),
    )
    val folder = contentResolver.query(
        documentUri,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
    val provider = uri.authority
        ?.let { packageManager.resolveContentProvider(it, 0) }
        ?.loadLabel(packageManager)
        ?.toString()
    return listOfNotNull(provider, folder).distinct().joinToString(" · ")
        .ifBlank { "Gewählter Ordner" }
}

private fun Context.recordBackupError(error: Exception) {
    val message = when (error) {
        is SecurityException -> "Der Speicherort ist nicht mehr verfügbar."
        else -> "Das Backup konnte nicht erstellt werden."
    }
    getSharedPreferences(BackupSettingsPreferences, Context.MODE_PRIVATE)
        .edit()
        .putString(BackupLastError, message)
        .apply()
}

private fun backupFileName(createdAt: Long): String =
    "Spur Backup ${SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.ROOT).format(Date(createdAt))}" +
        SpurBackupExtension

internal const val SpurBackupExtension = ".spurbackup"
private const val BackupMimeType = "application/vnd.spur.backup"
private const val BackupSettingsPreferences = "backup"
private const val BackupTreeUri = "tree-uri"
private const val BackupDestinationName = "destination-name"
private const val BackupAutomatic = "automatic"
private const val BackupDocumentUri = "document-uri"
private const val BackupLastSuccessAt = "last-success-at"
private const val BackupLastError = "last-error"
private const val AutomaticBackupWorkName = "spur-automatic-backup"
