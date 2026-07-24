package app.spur

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location

data class Tour(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val distanceMeters: Double,
    val pointCount: Int,
)

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val recordedAt: Long,
)

internal fun shouldAcceptPoint(
    accuracyMeters: Float,
    distanceMeters: Float?,
    elapsedMillis: Long?,
    allowFastMovement: Boolean = false,
): Boolean {
    if (accuracyMeters > 40f) return false
    if (distanceMeters == null || elapsedMillis == null) return true
    if (elapsedMillis <= 0L) return false
    if (!allowFastMovement && distanceMeters / (elapsedMillis / 1_000f) > 55f) return false
    val noiseFloor = (accuracyMeters * 0.5f).coerceIn(4f, 10f)
    return distanceMeters >= noiseFloor
}

class TourStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "spur.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE tours (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                started_at INTEGER NOT NULL,
                ended_at INTEGER,
                distance_meters REAL NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE track_points (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tour_id INTEGER NOT NULL REFERENCES tours(id) ON DELETE CASCADE,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                recorded_at INTEGER NOT NULL,
                accuracy_meters REAL NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX track_points_tour_id ON track_points(tour_id, id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun startTour(now: Long = System.currentTimeMillis()): Long {
        writableDatabase.execSQL(
            "UPDATE tours SET ended_at = ? WHERE ended_at IS NULL",
            arrayOf(now),
        )
        return writableDatabase.insertOrThrow(
            "tours",
            null,
            ContentValues().apply { put("started_at", now) },
        )
    }

    @Synchronized
    fun finishTour(id: Long, now: Long = System.currentTimeMillis()) {
        writableDatabase.update(
            "tours",
            ContentValues().apply { put("ended_at", now) },
            "id = ? AND ended_at IS NULL",
            arrayOf(id.toString()),
        )
    }

    @Synchronized
    fun appendLocation(
        tourId: Long,
        location: Location,
        allowFastMovement: Boolean = false,
    ): Boolean {
        val db = writableDatabase
        db.rawQuery(
            """
            SELECT t.ended_at, p.latitude, p.longitude, p.recorded_at
            FROM tours t
            LEFT JOIN track_points p ON p.tour_id = t.id
            WHERE t.id = ?
            ORDER BY p.id DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(tourId.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst() || !cursor.isNull(0)) return false
            val previous = if (!cursor.isNull(1)) {
                Location("stored").apply {
                    latitude = cursor.getDouble(1)
                    longitude = cursor.getDouble(2)
                    time = cursor.getLong(3)
                }
            } else {
                null
            }
            val distance = previous?.distanceTo(location)
            val elapsed = previous?.let { location.time - it.time }
            if (
                !shouldAcceptPoint(
                    accuracyMeters = location.accuracy,
                    distanceMeters = distance,
                    elapsedMillis = elapsed,
                    allowFastMovement = allowFastMovement,
                )
            ) {
                return false
            }

            db.beginTransaction()
            try {
                db.insertOrThrow(
                    "track_points",
                    null,
                    ContentValues().apply {
                        put("tour_id", tourId)
                        put("latitude", location.latitude)
                        put("longitude", location.longitude)
                        put("recorded_at", location.time)
                        put("accuracy_meters", location.accuracy)
                    },
                )
                if (distance != null) {
                    db.execSQL(
                        "UPDATE tours SET distance_meters = distance_meters + ? WHERE id = ?",
                        arrayOf(distance, tourId),
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        return true
    }

    internal fun appendSimulatedLocation(
        tourId: Long,
        coordinate: SpurCoordinate,
        now: Long = System.currentTimeMillis(),
    ): Boolean = appendLocation(
        tourId = tourId,
        location = Location("spur-simulation").apply {
            latitude = coordinate.latitude
            longitude = coordinate.longitude
            accuracy = 3f
            time = now
        },
        allowFastMovement = true,
    )

    @Synchronized
    fun activeTour(): Tour? =
        queryTours(where = "t.ended_at IS NULL", tail = "ORDER BY t.started_at DESC LIMIT 1")
            .firstOrNull()

    @Synchronized
    fun tour(id: Long): Tour? =
        queryTours(where = "t.id = ?", args = arrayOf(id.toString()), tail = "LIMIT 1")
            .firstOrNull()

    @Synchronized
    fun tours(): List<Tour> = queryTours(tail = "ORDER BY t.started_at DESC")

    @Synchronized
    fun points(tourId: Long): List<TrackPoint> =
        readableDatabase.rawQuery(
            """
            SELECT latitude, longitude, recorded_at
            FROM track_points
            WHERE tour_id = ?
            ORDER BY id
            """.trimIndent(),
            arrayOf(tourId.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        TrackPoint(
                            latitude = cursor.getDouble(0),
                            longitude = cursor.getDouble(1),
                            recordedAt = cursor.getLong(2),
                        ),
                    )
                }
            }
        }

    private fun queryTours(
        where: String? = null,
        args: Array<String> = emptyArray(),
        tail: String = "",
    ): List<Tour> =
        readableDatabase.rawQuery(
            """
            SELECT t.id, t.started_at, t.ended_at, t.distance_meters, COUNT(p.id)
            FROM tours t
            LEFT JOIN track_points p ON p.tour_id = t.id
            ${where?.let { "WHERE $it" }.orEmpty()}
            GROUP BY t.id
            $tail
            """.trimIndent(),
            args,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Tour(
                            id = cursor.getLong(0),
                            startedAt = cursor.getLong(1),
                            endedAt = if (cursor.isNull(2)) null else cursor.getLong(2),
                            distanceMeters = cursor.getDouble(3),
                            pointCount = cursor.getInt(4),
                        ),
                    )
                }
            }
        }
}
