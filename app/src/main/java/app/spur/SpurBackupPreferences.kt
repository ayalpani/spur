package app.spur

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class BackupPreferenceSnapshot(
    val files: Map<String, Map<String, Any>>,
)

private val StableBackupPreferenceFiles = listOf(
    "home-auto-start",
    "manual-location",
    "map-settings",
    "map-moments",
    "photo-places",
    EmojiPreferences,
)

internal fun Context.captureBackupPreferences(
    momentEntries: Set<String>? = null,
): BackupPreferenceSnapshot = BackupPreferenceSnapshot(
    StableBackupPreferenceFiles.associateWith { name ->
        getSharedPreferences(name, Context.MODE_PRIVATE)
            .all
            .mapValues { (_, value) ->
                @Suppress("UNCHECKED_CAST")
                if (value is Set<*>) (value as Set<String>).toSet() else requireNotNull(value)
            }
            .toMutableMap()
            .apply {
                if (name == "map-moments" && momentEntries != null) {
                    this["entries"] = momentEntries
                }
            }
    },
)

internal fun encodeBackupPreferences(snapshot: BackupPreferenceSnapshot): ByteArray {
    val root = JSONObject()
    snapshot.files.toSortedMap().forEach { (fileName, values) ->
        val file = JSONObject()
        values.toSortedMap().forEach { (key, value) ->
            val encoded = JSONObject()
            when (value) {
                is Boolean -> encoded.put("type", "boolean").put("value", value)
                is Float -> encoded.put("type", "float").put("value", value.toDouble())
                is Int -> encoded.put("type", "int").put("value", value)
                is Long -> encoded.put("type", "long").put("value", value)
                is String -> encoded.put("type", "string").put("value", value)
                is Set<*> -> encoded.put("type", "strings").put(
                    "value",
                    JSONArray(value.map { requireNotNull(it) as String }.sorted()),
                )
                else -> error("Nicht unterstützter Preference-Wert: ${value::class.java.name}")
            }
            file.put(key, encoded)
        }
        root.put(fileName, file)
    }
    return root.toString().toByteArray(Charsets.UTF_8)
}

internal fun decodeBackupPreferences(bytes: ByteArray): BackupPreferenceSnapshot {
    val root = JSONObject(bytes.toString(Charsets.UTF_8))
    require(root.keys().asSequence().toSet() == StableBackupPreferenceFiles.toSet())
    return BackupPreferenceSnapshot(
        StableBackupPreferenceFiles.associateWith { fileName ->
            val file = root.getJSONObject(fileName)
            file.keys().asSequence().associateWith { key ->
                val encoded = file.getJSONObject(key)
                when (encoded.getString("type")) {
                    "boolean" -> encoded.getBoolean("value")
                    "float" -> encoded.getDouble("value").toFloat()
                    "int" -> encoded.getInt("value")
                    "long" -> encoded.getLong("value")
                    "string" -> encoded.getString("value")
                    "strings" -> encoded.getJSONArray("value").let { values ->
                        buildSet(values.length()) {
                            repeat(values.length()) { add(values.getString(it)) }
                        }
                    }
                    else -> error("Unbekannter Preference-Typ.")
                }
            }
        },
    )
}

internal fun BackupPreferenceSnapshot.withRestoredMomentPaths(
    filesDir: File,
    archivedMedia: Set<String>,
): BackupPreferenceSnapshot {
    val mapMomentValues = files.getValue("map-moments").toMutableMap()
    val encodedMoments = mapMomentValues["entries"] as? Set<*>
        ?: emptySet<String>()
    val referencedMedia = mutableSetOf<String>()
    val restored = encodedMoments.mapTo(mutableSetOf()) { encoded ->
        val moment = decodeMapMoment(encoded as? String ?: error("Ungültiger Moment."))
            ?: error("Ungültiger Moment.")
        if (moment.type == MomentType.EMOJI) {
            encodeMapMoment(moment)
        } else {
            val archivePath = moment.payload
            require(backupMediaTypeForPath(archivePath) == moment.type)
            require(archivePath in archivedMedia)
            referencedMedia += archivePath
            encodeMapMoment(moment.copy(payload = File(filesDir, archivePath).absolutePath))
        }
    }
    require(referencedMedia == archivedMedia)
    mapMomentValues["entries"] = restored
    return copy(files = files + ("map-moments" to mapMomentValues))
}

internal fun Context.applyBackupPreferences(snapshot: BackupPreferenceSnapshot) {
    require(snapshot.files.keys == StableBackupPreferenceFiles.toSet())
    snapshot.files.forEach { (fileName, values) ->
        val editor = getSharedPreferences(fileName, Context.MODE_PRIVATE).edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as Set<String>)
                }
                else -> error("Nicht unterstützter Preference-Wert.")
            }
        }
        check(editor.commit()) { "Preferences konnten nicht gespeichert werden." }
    }
    invalidateHomeAutoStartSettingsCache()
}

internal fun backupMediaTypeForPath(path: String): MomentType? {
    val parts = path.split('/')
    if (parts.size != 3 || parts[0] != "moments" || !isSafeBackupFileName(parts[2])) {
        return null
    }
    return when (parts[1]) {
        "photos" -> MomentType.PHOTO
        "videos" -> MomentType.VIDEO
        "voice" -> MomentType.VOICE
        else -> null
    }
}

internal fun isSafeBackupFileName(name: String): Boolean =
    name.isNotBlank() &&
        name != "." &&
        name != ".." &&
        '/' !in name &&
        '\\' !in name &&
        '\u0000' !in name
