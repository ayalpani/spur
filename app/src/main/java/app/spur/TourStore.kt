package app.spur

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Tour(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val distanceMeters: Double,
    val pointCount: Int,
    val activity: String? = null,
)

data class TrackPoint(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val recordedAt: Long,
)

internal data class GpsStartFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
)

internal class GpsStartStabilizer(
    private val requiredFixes: Int = 3,
    private val maximumAccuracyMeters: Float = 12f,
    private val maximumClusterRadiusMeters: Double = 20.0,
) {
    private var anchor: GpsStartFix? = null
    private var consecutiveFixes = 0

    fun isReady(fix: GpsStartFix): Boolean {
        if (fix.accuracyMeters > maximumAccuracyMeters) {
            reset()
            return false
        }
        if (
            anchor?.let {
                coordinateDistanceMeters(
                    fromLatitude = it.latitude,
                    fromLongitude = it.longitude,
                    toLatitude = fix.latitude,
                    toLongitude = fix.longitude,
                ) > maximumClusterRadiusMeters
            } == true
        ) {
            reset()
        }
        if (anchor == null) anchor = fix
        consecutiveFixes++
        return consecutiveFixes >= requiredFixes
    }

    private fun reset() {
        anchor = null
        consecutiveFixes = 0
    }
}

private fun coordinateDistanceMeters(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double,
): Double {
    val earthRadiusMeters = 6_371_000.0
    val latitudeDelta = Math.toRadians(toLatitude - fromLatitude)
    val longitudeDelta = Math.toRadians(toLongitude - fromLongitude)
    val fromLatitudeRadians = Math.toRadians(fromLatitude)
    val toLatitudeRadians = Math.toRadians(toLatitude)
    val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
        cos(fromLatitudeRadians) * cos(toLatitudeRadians) *
        sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
    val normalized = a.coerceIn(0.0, 1.0)
    return earthRadiusMeters * 2 * atan2(sqrt(normalized), sqrt(1 - normalized))
}

internal fun trackDistanceMeters(points: List<TrackPoint>): Double =
    points.zipWithNext().sumOf { (from, to) ->
        coordinateDistanceMeters(
            fromLatitude = from.latitude,
            fromLongitude = from.longitude,
            toLatitude = to.latitude,
            toLongitude = to.longitude,
        )
    }

internal fun retainedPointIds(
    points: List<TrackPoint>,
    startIndex: Int,
    endIndex: Int,
): Set<Long> {
    if (points.isEmpty()) return emptySet()
    val start = startIndex.coerceIn(points.indices)
    val end = endIndex.coerceIn(start, points.lastIndex)
    return points.subList(start, end + 1).mapTo(mutableSetOf(), TrackPoint::id)
}

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
    SQLiteOpenHelper(context.applicationContext, "spur.db", null, 3) {
    private val gpsStartStabilizers = mutableMapOf<Long, GpsStartStabilizer>()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE tours (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                started_at INTEGER NOT NULL,
                ended_at INTEGER,
                distance_meters REAL NOT NULL DEFAULT 0,
                activity TEXT
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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE tours ADD COLUMN activity TEXT")
    }

    @Synchronized
    fun startTour(
        now: Long = System.currentTimeMillis(),
    ): Long {
        gpsStartStabilizers.clear()
        writableDatabase.execSQL(
            "UPDATE tours SET ended_at = ? WHERE ended_at IS NULL",
            arrayOf(now),
        )
        return writableDatabase.insertOrThrow(
            "tours",
            null,
            ContentValues().apply {
                put("started_at", now)
            },
        )
    }

    @Synchronized
    fun finishTour(id: Long, now: Long = System.currentTimeMillis()) {
        gpsStartStabilizers.remove(id)
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
            val accuracyMeters = if (location.hasAccuracy()) {
                location.accuracy
            } else {
                Float.POSITIVE_INFINITY
            }
            if (previous != null) gpsStartStabilizers.remove(tourId)
            if (
                previous == null &&
                !allowFastMovement &&
                !gpsStartStabilizers.getOrPut(tourId) { GpsStartStabilizer() }.isReady(
                    GpsStartFix(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = accuracyMeters,
                    ),
                )
            ) {
                return false
            }
            val distance = previous?.distanceTo(location)
            val elapsed = previous?.let { location.time - it.time }
            if (
                !shouldAcceptPoint(
                    accuracyMeters = accuracyMeters,
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
                        put("accuracy_meters", accuracyMeters)
                    },
                )
                if (distance != null) {
                    db.execSQL(
                        "UPDATE tours SET distance_meters = distance_meters + ? WHERE id = ?",
                        arrayOf(distance, tourId),
                    )
                }
                db.setTransactionSuccessful()
                gpsStartStabilizers.remove(tourId)
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
            SELECT id, latitude, longitude, recorded_at
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
                            id = cursor.getLong(0),
                            latitude = cursor.getDouble(1),
                            longitude = cursor.getDouble(2),
                            recordedAt = cursor.getLong(3),
                        ),
                    )
                }
            }
        }

    @Synchronized
    fun updateTourPoints(tourId: Long, retainedIds: Set<Long>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            points(tourId)
                .filterNot { it.id in retainedIds }
                .forEach { point ->
                    db.delete(
                        "track_points",
                        "tour_id = ? AND id = ?",
                        arrayOf(tourId.toString(), point.id.toString()),
                    )
                }
            val retained = points(tourId)
            val values = ContentValues().apply {
                put("distance_meters", trackDistanceMeters(retained))
                retained.firstOrNull()?.let { put("started_at", it.recordedAt) }
                if (tour(tourId)?.endedAt != null) {
                    retained.lastOrNull()?.let { put("ended_at", it.recordedAt) }
                }
            }
            db.update("tours", values, "id = ?", arrayOf(tourId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun deleteTour(id: Long) {
        gpsStartStabilizers.remove(id)
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("track_points", "tour_id = ?", arrayOf(id.toString()))
            db.delete("tours", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun queryTours(
        where: String? = null,
        args: Array<String> = emptyArray(),
        tail: String = "",
    ): List<Tour> =
        readableDatabase.rawQuery(
            """
            SELECT t.id, t.started_at, t.ended_at, t.distance_meters, COUNT(p.id), t.activity
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
                            activity = cursor.getString(5),
                        ),
                    )
                }
            }
        }
}
