package app.spur

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.ui.graphics.toArgb
import java.util.UUID

internal data class Landmark(
    val id: String,
    val title: String,
    val coordinate: SpurCoordinate,
    val priority: Int,
    val colorArgb: Int = landmarkColor(priority).toArgb(),
)

internal const val LandmarkTitleMaximumCharacters = 60

internal fun normalizeLandmarkTitle(title: String): String? =
    title.trim().take(LandmarkTitleMaximumCharacters).ifEmpty { null }

internal class LandmarkStore(context: Context) :
    SQLiteOpenHelper(
        context.applicationContext,
        LandmarkDatabaseName,
        null,
        LandmarkDatabaseVersion,
    ) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE landmarks (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                priority INTEGER NOT NULL,
                color INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.beginTransaction()
        try {
            BerlinLandmarks.forEach { landmark ->
                db.insertOrThrow("landmarks", null, landmark.contentValues())
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "ALTER TABLE landmarks ADD COLUMN color INTEGER NOT NULL " +
                    "DEFAULT ${landmarkColor(0).toArgb()}",
            )
            val priorities = db.query(
                "landmarks",
                arrayOf("id", "priority"),
                null,
                null,
                null,
                null,
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getInt(1))
                }
            }
            priorities.forEach { (id, priority) ->
                db.update(
                    "landmarks",
                    ContentValues().apply { put("color", landmarkColor(priority).toArgb()) },
                    "id = ?",
                    arrayOf(id),
                )
            }
        }
    }

    @Synchronized
    fun landmarks(): List<Landmark> = readableDatabase.query(
        "landmarks",
        arrayOf("id", "title", "latitude", "longitude", "priority", "color"),
        null,
        null,
        null,
        null,
        "priority ASC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    Landmark(
                        id = cursor.getString(0),
                        title = cursor.getString(1),
                        coordinate = SpurCoordinate(
                            latitude = cursor.getDouble(2),
                            longitude = cursor.getDouble(3),
                        ),
                        priority = cursor.getInt(4),
                        colorArgb = cursor.getInt(5),
                    ),
                )
            }
        }
    }

    @Synchronized
    fun rename(id: String, title: String): Boolean {
        val normalized = normalizeLandmarkTitle(title) ?: return false
        return writableDatabase.update(
            "landmarks",
            ContentValues().apply { put("title", normalized) },
            "id = ?",
            arrayOf(id),
        ) == 1
    }

    @Synchronized
    fun add(title: String, coordinate: SpurCoordinate): Landmark? {
        val normalized = normalizeLandmarkTitle(title) ?: return null
        if (
            !coordinate.latitude.isFinite() ||
            !coordinate.longitude.isFinite() ||
            coordinate.latitude !in -90.0..90.0 ||
            coordinate.longitude !in -180.0..180.0
        ) return null

        val database = writableDatabase
        database.beginTransaction()
        return try {
            val priority = database.rawQuery(
                "SELECT COALESCE(MAX(priority), -1) + 1 FROM landmarks",
                null,
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            val landmark = Landmark(
                id = "custom-${UUID.randomUUID()}",
                title = normalized,
                coordinate = coordinate,
                priority = priority,
            )
            if (database.insert("landmarks", null, landmark.contentValues()) == -1L) {
                null
            } else {
                database.setTransactionSuccessful()
                landmark
            }
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun delete(id: String): Boolean = writableDatabase.delete(
        "landmarks",
        "id = ?",
        arrayOf(id),
    ) == 1

    private fun Landmark.contentValues() = ContentValues().apply {
        put("id", id)
        put("title", title)
        put("latitude", coordinate.latitude)
        put("longitude", coordinate.longitude)
        put("priority", priority)
        put("color", colorArgb)
    }
}

private const val LandmarkDatabaseName = "landmarks.db"
private const val LandmarkDatabaseVersion = 2

// Curated Berlin defaults. They seed a new local database once and are then user-owned.
private val BerlinLandmarks = listOf(
    Landmark("berlin-tv-tower", "Fernsehturm", SpurCoordinate(52.5208279, 13.4094214), 0),
    Landmark("victory-column", "Goldelse", SpurCoordinate(52.5145082, 13.3501108), 1),
    Landmark("brandenburg-gate", "Brandenburger Tor", SpurCoordinate(52.5162699, 13.3777034), 2),
    Landmark("reichstag", "Reichstag", SpurCoordinate(52.5186538, 13.3761015), 3),
    Landmark("museum-island", "Museumsinsel", SpurCoordinate(52.5213889, 13.3955556), 4),
    Landmark("berlin-cathedral", "Berliner Dom", SpurCoordinate(52.5190822, 13.4010942), 5),
    Landmark("memorial-church", "Gedächtniskirche", SpurCoordinate(52.5048945, 13.3346131), 6),
    Landmark("charlottenburg-palace", "Schloss Charlottenburg", SpurCoordinate(52.5206424, 13.2927104), 7),
    Landmark("radio-tower", "Funkturm", SpurCoordinate(52.5050275, 13.2781397), 8),
    Landmark("olympic-stadium", "Olympiastadion", SpurCoordinate(52.5145846, 13.2398144), 9),
    Landmark("east-side-gallery", "East Side Gallery", SpurCoordinate(52.5044542, 13.4408392), 10),
    Landmark("oberbaum-bridge", "Oberbaumbrücke", SpurCoordinate(52.5017288, 13.4457312), 11),
    Landmark("tempelhof-field", "Tempelhofer Feld", SpurCoordinate(52.4744192, 13.4026007), 12),
    Landmark("gendarmenmarkt", "Gendarmenmarkt", SpurCoordinate(52.5135698, 13.3922970), 13),
    Landmark("berlin-wall-memorial", "Gedenkstätte Berliner Mauer", SpurCoordinate(52.5375133, 13.3946755), 14),
    Landmark("teufelsberg", "Teufelsberg", SpurCoordinate(52.4977183, 13.2429543), 15),
    Landmark("molecule-man", "Molecule Man", SpurCoordinate(52.4969763, 13.4589482), 16),
    Landmark("soviet-memorial-treptow", "Sowjetisches Ehrenmal", SpurCoordinate(52.4876653, 13.4688201), 17),
    Landmark("koepenick-palace", "Schloss Köpenick", SpurCoordinate(52.4438598, 13.5727293), 18),
    Landmark("gardens-of-the-world", "Gärten der Welt", SpurCoordinate(52.5372553, 13.5749239), 19),
)
