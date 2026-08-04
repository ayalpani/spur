package app.spur

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.os.SystemClock
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
    val title: String? = null,
)

internal data class TourNotificationSummary(
    val id: Long,
    val startedAt: Long,
    val distanceMeters: Double,
)

internal const val TourTitleMaximumCharacters = 80

internal fun normalizeTourTitle(title: String): String? =
    title.trim().take(TourTitleMaximumCharacters).ifEmpty { null }

data class TrackPoint(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val recordedAt: Long,
)

internal data class TourStartResult(
    val id: Long,
    val created: Boolean,
)

internal data class StationaryCluster(
    val latitude: Double,
    val longitude: Double,
    val startedAt: Long,
    val recordedAt: Long,
    val sampleCount: Int,
)

private const val StationaryWindowMillis = 5 * 60 * 1_000L
private const val StationaryMaximumSpreadMeters = 100.0
private const val StationaryExitFixCount = 3

internal fun stationaryCluster(
    points: List<TrackPoint>,
    windowMillis: Long = StationaryWindowMillis,
    maximumSpreadMeters: Double = StationaryMaximumSpreadMeters,
): StationaryCluster? {
    if (points.size < 3) return null
    val ordered = points.sortedBy(TrackPoint::recordedAt)
    if (ordered.last().recordedAt - ordered.first().recordedAt < windowMillis) return null
    for (fromIndex in ordered.indices) {
        for (toIndex in fromIndex + 1 until ordered.size) {
            val from = ordered[fromIndex]
            val to = ordered[toIndex]
            if (
                coordinateDistanceMeters(
                    fromLatitude = from.latitude,
                    fromLongitude = from.longitude,
                    toLatitude = to.latitude,
                    toLongitude = to.longitude,
                ) > maximumSpreadMeters
            ) {
                return null
            }
        }
    }
    return StationaryCluster(
        latitude = ordered.map(TrackPoint::latitude).average(),
        longitude = ordered.map(TrackPoint::longitude).average(),
        startedAt = ordered.first().recordedAt,
        recordedAt = ordered.last().recordedAt,
        sampleCount = ordered.size,
    )
}

internal data class GpsStartFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
)

internal class GpsStartGate(
    private val immediateAccuracyMeters: Float = 12f,
    private val fallbackAccuracyMeters: Float = 40f,
    private val fallbackDelayMillis: Long = 10_000L,
) {
    private var firstFixObservedAtMillis: Long? = null

    fun isReady(fix: GpsStartFix, observedAtMillis: Long): Boolean {
        val firstObservedAt = firstFixObservedAtMillis
            ?: observedAtMillis.also { firstFixObservedAtMillis = it }
        if (fix.accuracyMeters <= immediateAccuracyMeters) return true
        return fix.accuracyMeters <= fallbackAccuracyMeters &&
            observedAtMillis - firstObservedAt >= fallbackDelayMillis
    }
}

internal fun coordinateDistanceMeters(
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

internal fun stationaryCollapseDistanceDelta(
    previous: TrackPoint?,
    replaced: List<TrackPoint>,
    replacement: StationaryCluster,
): Double {
    if (replaced.isEmpty()) return 0.0
    val oldDistance = trackDistanceMeters(listOfNotNull(previous) + replaced)
    val newDistance = previous?.let {
        coordinateDistanceMeters(
            fromLatitude = it.latitude,
            fromLongitude = it.longitude,
            toLatitude = replacement.latitude,
            toLongitude = replacement.longitude,
        )
    } ?: 0.0
    return newDistance - oldDistance
}

internal fun automaticTourEndRecordedAt(
    lastRecordedAt: Long?,
    returnedAt: Long,
): Long = lastRecordedAt?.let { maxOf(returnedAt, it + 1L) } ?: returnedAt

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

private data class StoredTrackPoint(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val recordedAt: Long,
    val clusterStartedAt: Long?,
    val clusterSampleCount: Int,
) {
    fun asLocation() = Location("stored").apply {
        latitude = this@StoredTrackPoint.latitude
        longitude = this@StoredTrackPoint.longitude
        time = recordedAt
    }
}

private data class ActiveTourTail(val point: StoredTrackPoint?)

private data class StoredRoadHistoryPoint(
    val tourId: Long,
    val id: Long,
    val coordinate: SpurCoordinate,
)

class TourStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "spur.db", null, 4) {
    private val gpsStartGates = mutableMapOf<Long, GpsStartGate>()
    private val stationaryExitFixes = mutableMapOf<Long, MutableList<Location>>()

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
                accuracy_meters REAL NOT NULL,
                cluster_started_at INTEGER,
                cluster_sample_count INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX track_points_tour_id ON track_points(tour_id, id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE tours ADD COLUMN activity TEXT")
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE track_points ADD COLUMN cluster_started_at INTEGER")
            db.execSQL(
                "ALTER TABLE track_points ADD COLUMN cluster_sample_count " +
                    "INTEGER NOT NULL DEFAULT 1",
            )
        }
    }

    @Synchronized
    fun startTour(
        now: Long = System.currentTimeMillis(),
    ): Long {
        gpsStartGates.clear()
        stationaryExitFixes.clear()
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
    internal fun activeTourOrStart(
        now: Long = System.currentTimeMillis(),
    ): TourStartResult {
        activeTour()?.let { return TourStartResult(id = it.id, created = false) }
        return TourStartResult(id = startTour(now), created = true)
    }

    @Synchronized
    fun finishTour(id: Long, now: Long = System.currentTimeMillis()): Boolean {
        gpsStartGates.remove(id)
        stationaryExitFixes.remove(id)
        return writableDatabase.update(
            "tours",
            ContentValues().apply { put("ended_at", now) },
            "id = ? AND ended_at IS NULL",
            arrayOf(id.toString()),
        ) > 0
    }

    @Synchronized
    fun updateTourTitle(id: Long, title: String): Boolean =
        writableDatabase.update(
            "tours",
            ContentValues().apply {
                put("activity", normalizeTourTitle(title))
            },
            "id = ?",
            arrayOf(id.toString()),
        ) > 0

    @Synchronized
    internal fun finishTourAt(
        id: Long,
        endPoint: SpurCoordinate,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        gpsStartGates.remove(id)
        stationaryExitFixes.remove(id)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val activeTail = latestPointOfActiveTour(db, id) ?: return false
            val endRecordedAt = automaticTourEndRecordedAt(
                lastRecordedAt = activeTail.point?.recordedAt,
                returnedAt = now,
            )
            insertRawLocation(
                db = db,
                tourId = id,
                latitude = endPoint.latitude,
                longitude = endPoint.longitude,
                recordedAt = endRecordedAt,
                accuracyMeters = 3f,
            )
            val finished = db.update(
                "tours",
                ContentValues().apply {
                    put("ended_at", endRecordedAt)
                    put("distance_meters", trackDistanceMeters(points(db, id)))
                },
                "id = ? AND ended_at IS NULL",
                arrayOf(id.toString()),
            ) > 0
            if (!finished) return false
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun appendLocation(
        tourId: Long,
        location: Location,
        allowFastMovement: Boolean = false,
    ): Boolean {
        val db = writableDatabase
        val previous = latestPointOfActiveTour(db, tourId) ?: return false
        val previousLocation = previous.point?.asLocation()
        val accuracyMeters = if (location.hasAccuracy()) {
            location.accuracy
        } else {
            Float.POSITIVE_INFINITY
        }
        if (previousLocation != null) gpsStartGates.remove(tourId)
        if (
            previousLocation == null &&
            !allowFastMovement &&
            !gpsStartGates.getOrPut(tourId) { GpsStartGate() }.isReady(
                GpsStartFix(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = accuracyMeters,
                ),
                observedAtMillis = SystemClock.elapsedRealtime(),
            )
        ) {
            return false
        }

        val storedPrevious = previous.point
        if (
            !allowFastMovement &&
            storedPrevious?.clusterStartedAt != null
        ) {
            return appendToStationaryCluster(
                db = db,
                tourId = tourId,
                clusterPoint = storedPrevious,
                location = location,
                accuracyMeters = accuracyMeters,
            )
        }

        val distance = previousLocation?.distanceTo(location)
        val elapsed = previousLocation?.let { location.time - it.time }
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

        insertLocations(
            db = db,
            tourId = tourId,
            locations = listOf(Location(location)),
            previous = previousLocation,
        )
        gpsStartGates.remove(tourId)
        stationaryExitFixes.remove(tourId)
        if (!allowFastMovement) collapseStationaryWindow(db, tourId, location.time)
        return true
    }

    private fun latestPointOfActiveTour(
        db: SQLiteDatabase,
        tourId: Long,
    ): ActiveTourTail? =
        db.rawQuery(
            """
            SELECT t.ended_at, p.id, p.latitude, p.longitude, p.recorded_at,
                   p.cluster_started_at, p.cluster_sample_count
            FROM tours t
            LEFT JOIN track_points p ON p.tour_id = t.id
            WHERE t.id = ?
            ORDER BY p.recorded_at DESC, p.id DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(tourId.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst() || !cursor.isNull(0)) return@use null
            val point = if (cursor.isNull(1)) {
                null
            } else {
                StoredTrackPoint(
                    id = cursor.getLong(1),
                    latitude = cursor.getDouble(2),
                    longitude = cursor.getDouble(3),
                    recordedAt = cursor.getLong(4),
                    clusterStartedAt = if (cursor.isNull(5)) null else cursor.getLong(5),
                    clusterSampleCount = cursor.getInt(6),
                )
            }
            ActiveTourTail(point)
        }

    private fun appendToStationaryCluster(
        db: SQLiteDatabase,
        tourId: Long,
        clusterPoint: StoredTrackPoint,
        location: Location,
        accuracyMeters: Float,
    ): Boolean {
        val clusterLocation = clusterPoint.asLocation()
        val distance = clusterLocation.distanceTo(location)
        val elapsed = location.time - clusterPoint.recordedAt
        if (accuracyMeters > 40f || elapsed <= 0L) return false
        if (distance <= StationaryMaximumSpreadMeters) {
            stationaryExitFixes.remove(tourId)
            val oldCount = clusterPoint.clusterSampleCount.coerceAtLeast(1)
            val newCount = oldCount + 1
            val latitude =
                (clusterPoint.latitude * oldCount + location.latitude) / newCount
            val longitude =
                (clusterPoint.longitude * oldCount + location.longitude) / newCount
            updateStationaryCluster(
                db = db,
                tourId = tourId,
                clusterPoint = clusterPoint,
                latitude = latitude,
                longitude = longitude,
                recordedAt = location.time,
                accuracyMeters = accuracyMeters,
                sampleCount = newCount,
            )
            return true
        }
        if (
            !shouldAcceptPoint(
                accuracyMeters = accuracyMeters,
                distanceMeters = distance,
                elapsedMillis = elapsed,
            )
        ) {
            return false
        }
        val exitFixes = stationaryExitFixes.getOrPut(tourId) { mutableListOf() }
        exitFixes += Location(location)
        if (exitFixes.size < StationaryExitFixCount) return false
        stationaryExitFixes.remove(tourId)
        insertLocations(
            db = db,
            tourId = tourId,
            locations = exitFixes,
            previous = clusterLocation,
        )
        return true
    }

    private fun insertLocations(
        db: SQLiteDatabase,
        tourId: Long,
        locations: List<Location>,
        previous: Location?,
    ) {
        var previousLocation = previous
        var addedDistance = 0f
        db.beginTransaction()
        try {
            locations.forEach { location ->
                db.insertOrThrow(
                    "track_points",
                    null,
                    ContentValues().apply {
                        put("tour_id", tourId)
                        put("latitude", location.latitude)
                        put("longitude", location.longitude)
                        put("recorded_at", location.time)
                        put(
                            "accuracy_meters",
                            if (location.hasAccuracy()) {
                                location.accuracy
                            } else {
                                Float.POSITIVE_INFINITY
                            },
                        )
                    },
                )
                previousLocation?.let { addedDistance += it.distanceTo(location) }
                previousLocation = location
            }
            if (addedDistance > 0f) {
                db.execSQL(
                    "UPDATE tours SET distance_meters = distance_meters + ? WHERE id = ?",
                    arrayOf(addedDistance, tourId),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun collapseStationaryWindow(
        db: SQLiteDatabase,
        tourId: Long,
        recordedAt: Long,
    ) {
        val recent = pointsSinceWindowStart(
            db = db,
            tourId = tourId,
            windowStart = recordedAt - StationaryWindowMillis,
        )
        val cluster = stationaryCluster(recent) ?: return
        val representative = recent.last()
        val removedIds = recent.dropLast(1).map(TrackPoint::id)
        val previous = pointBefore(db, tourId, recent.first())
        val distanceDelta = stationaryCollapseDistanceDelta(
            previous = previous,
            replaced = recent,
            replacement = cluster,
        )
        db.beginTransaction()
        try {
            if (removedIds.isNotEmpty()) {
                db.delete(
                    "track_points",
                    "tour_id = ? AND id IN (${removedIds.joinToString { "?" }})",
                    arrayOf(tourId.toString(), *removedIds.map(Long::toString).toTypedArray()),
                )
            }
            db.update(
                "track_points",
                ContentValues().apply {
                    put("latitude", cluster.latitude)
                    put("longitude", cluster.longitude)
                    put("recorded_at", cluster.recordedAt)
                    put("cluster_started_at", cluster.startedAt)
                    put("cluster_sample_count", cluster.sampleCount)
                },
                "tour_id = ? AND id = ?",
                arrayOf(tourId.toString(), representative.id.toString()),
            )
            db.execSQL(
                """
                UPDATE tours
                SET distance_meters = MAX(0, distance_meters + ?)
                WHERE id = ?
                """.trimIndent(),
                arrayOf(distanceDelta, tourId),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun pointBefore(
        db: SQLiteDatabase,
        tourId: Long,
        point: TrackPoint,
    ): TrackPoint? =
        db.rawQuery(
            """
            SELECT id, latitude, longitude, recorded_at
            FROM track_points
            WHERE tour_id = ? AND (recorded_at < ? OR (recorded_at = ? AND id < ?))
            ORDER BY recorded_at DESC, id DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(
                tourId.toString(),
                point.recordedAt.toString(),
                point.recordedAt.toString(),
                point.id.toString(),
            ),
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                TrackPoint(
                    id = cursor.getLong(0),
                    latitude = cursor.getDouble(1),
                    longitude = cursor.getDouble(2),
                    recordedAt = cursor.getLong(3),
                )
            }
        }

    private fun pointsSinceWindowStart(
        db: SQLiteDatabase,
        tourId: Long,
        windowStart: Long,
    ): List<TrackPoint> =
        db.rawQuery(
            """
            SELECT id, latitude, longitude, recorded_at
            FROM track_points
            WHERE tour_id = ?
            ORDER BY recorded_at DESC, id DESC
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
                    if (cursor.getLong(3) <= windowStart) break
                }
            }.asReversed()
        }

    private fun updateStationaryCluster(
        db: SQLiteDatabase,
        tourId: Long,
        clusterPoint: StoredTrackPoint,
        latitude: Double,
        longitude: Double,
        recordedAt: Long,
        accuracyMeters: Float,
        sampleCount: Int,
    ) {
        val previousPoint = db.rawQuery(
            """
            SELECT latitude, longitude, recorded_at
            FROM track_points
            WHERE tour_id = ? AND (recorded_at < ? OR (recorded_at = ? AND id < ?))
            ORDER BY recorded_at DESC, id DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(
                tourId.toString(),
                clusterPoint.recordedAt.toString(),
                clusterPoint.recordedAt.toString(),
                clusterPoint.id.toString(),
            ),
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else Location("stored").apply {
                this.latitude = cursor.getDouble(0)
                this.longitude = cursor.getDouble(1)
                time = cursor.getLong(2)
            }
        }
        val oldDistance = previousPoint?.distanceTo(clusterPoint.asLocation()) ?: 0f
        val newLocation = Location("cluster").apply {
            this.latitude = latitude
            this.longitude = longitude
            time = recordedAt
        }
        val newDistance = previousPoint?.distanceTo(newLocation) ?: 0f
        db.beginTransaction()
        try {
            db.update(
                "track_points",
                ContentValues().apply {
                    put("latitude", latitude)
                    put("longitude", longitude)
                    put("recorded_at", recordedAt)
                    put("accuracy_meters", accuracyMeters)
                    put("cluster_sample_count", sampleCount)
                },
                "tour_id = ? AND id = ?",
                arrayOf(tourId.toString(), clusterPoint.id.toString()),
            )
            db.execSQL(
                """
                UPDATE tours
                SET distance_meters = MAX(0, distance_meters + ?)
                WHERE id = ?
                """.trimIndent(),
                arrayOf(newDistance - oldDistance, tourId),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
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
    internal fun mergeAutomaticStartLocations(
        tourId: Long,
        startPoint: SpurCoordinate,
        locations: List<BufferedHomeLocation>,
        exitAt: Long,
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val prepared = automaticStartLocations(startPoint, locations, exitAt)
            val known = points(db, tourId).toMutableList()
            prepared.forEach { location ->
                val duplicate = known.any {
                    kotlin.math.abs(it.recordedAt - location.recordedAt) <= 1_000L &&
                        coordinateDistanceMeters(
                            it.latitude,
                            it.longitude,
                            location.latitude,
                            location.longitude,
                        ) <= 2.0
                }
                if (!duplicate) {
                    val id = insertRawLocation(
                        db = db,
                        tourId = tourId,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        recordedAt = location.recordedAt,
                        accuracyMeters = location.accuracyMeters,
                    )
                    known += TrackPoint(
                        id = id,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        recordedAt = location.recordedAt,
                    )
                }
            }
            val ordered = points(db, tourId)
            db.update(
                "tours",
                ContentValues().apply {
                    put("started_at", ordered.firstOrNull()?.recordedAt ?: exitAt)
                    put("distance_meters", trackDistanceMeters(ordered))
                },
                "id = ?",
                arrayOf(tourId.toString()),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun insertRawLocation(
        db: SQLiteDatabase,
        tourId: Long,
        latitude: Double,
        longitude: Double,
        recordedAt: Long,
        accuracyMeters: Float,
    ): Long = db.insertOrThrow(
        "track_points",
        null,
        ContentValues().apply {
            put("tour_id", tourId)
            put("latitude", latitude)
            put("longitude", longitude)
            put("recorded_at", recordedAt)
            put("accuracy_meters", accuracyMeters)
        },
    )

    @Synchronized
    fun activeTour(): Tour? =
        queryTours(where = "t.ended_at IS NULL", tail = "ORDER BY t.started_at DESC LIMIT 1")
            .firstOrNull()

    @Synchronized
    internal fun activeTourNotificationSummary(): TourNotificationSummary? =
        queryTourNotificationSummary(
            where = "ended_at IS NULL",
            tail = "ORDER BY started_at DESC LIMIT 1",
        )

    @Synchronized
    internal fun tourNotificationSummary(id: Long): TourNotificationSummary? =
        queryTourNotificationSummary(
            where = "id = ?",
            args = arrayOf(id.toString()),
            tail = "LIMIT 1",
        )

    @Synchronized
    fun tour(id: Long): Tour? =
        queryTours(where = "t.id = ?", args = arrayOf(id.toString()), tail = "LIMIT 1")
            .firstOrNull()

    @Synchronized
    fun tours(
        limit: Int? = null,
        offset: Int = 0,
    ): List<Tour> {
        require(limit == null || limit > 0)
        require(offset >= 0)
        val paging = when {
            limit != null -> " LIMIT $limit OFFSET $offset"
            offset > 0 -> " LIMIT -1 OFFSET $offset"
            else -> ""
        }
        return queryTours(tail = "ORDER BY t.started_at DESC$paging")
    }

    @Synchronized
    fun points(tourId: Long): List<TrackPoint> =
        points(readableDatabase, tourId)

    @Synchronized
    internal fun roadHistoryFingerprint(
        afterPointId: Long? = null,
        excludingTourId: Long? = null,
    ): RoadHistoryFingerprint {
        val conditions = buildList {
            afterPointId?.let { add("id > ?") }
            excludingTourId?.let { add("tour_id != ?") }
        }
        val selection = conditions.takeIf { it.isNotEmpty() }
            ?.joinToString(separator = " AND ", prefix = "WHERE ")
            .orEmpty()
        val arguments = buildList {
            afterPointId?.let { add(it.toString()) }
            excludingTourId?.let { add(it.toString()) }
        }.toTypedArray()
        return readableDatabase.rawQuery(
            """
            SELECT
                COALESCE(MAX(id), 0),
                COUNT(*),
                COALESCE(SUM(
                    (id % $RoadHistorySignaturePrime) +
                    (recorded_at % $RoadHistorySignaturePrime) +
                    CAST(ROUND(latitude * $RoadHistoryCoordinatePrecision) AS INTEGER) * 31 +
                    CAST(ROUND(longitude * $RoadHistoryCoordinatePrecision) AS INTEGER)
                ), 0)
            FROM track_points
            $selection
            """.trimIndent(),
            arguments,
        ).use { cursor ->
            cursor.moveToFirst()
            RoadHistoryFingerprint(
                maximumPointId = cursor.getLong(0),
                pointCount = cursor.getLong(1),
                signature = cursor.getLong(2),
            )
        }
    }

    @Synchronized
    internal fun roadHistoryRoutes(
        bounds: RoadHistoryBounds,
        afterPointId: Long? = null,
        excludingTourId: Long? = null,
    ): List<List<SpurCoordinate>> {
        val selection: String
        val arguments: Array<String>
        if (afterPointId == null) {
            selection =
                """
                WHERE tour_id IN (
                    SELECT DISTINCT tour_id
                    FROM track_points
                    WHERE latitude BETWEEN ? AND ?
                      AND longitude BETWEEN ? AND ?
                )
                ${excludingTourId?.let { "AND tour_id != ?" }.orEmpty()}
                """.trimIndent()
            arguments = buildList {
                add(bounds.minimumLatitude.toString())
                add(bounds.maximumLatitude.toString())
                add(bounds.minimumLongitude.toString())
                add(bounds.maximumLongitude.toString())
                excludingTourId?.let { add(it.toString()) }
            }.toTypedArray()
        } else {
            selection =
                """
                WHERE (
                    id > ?
                    OR id IN (
                    SELECT MAX(previous.id)
                    FROM track_points AS previous
                    WHERE previous.id <= ?
                      AND previous.tour_id IN (
                        SELECT DISTINCT added.tour_id
                        FROM track_points AS added
                        WHERE added.id > ?
                    )
                    GROUP BY previous.tour_id
                    )
                )
                ${excludingTourId?.let { "AND tour_id != ?" }.orEmpty()}
                """.trimIndent()
            arguments = buildList {
                repeat(3) { add(afterPointId.toString()) }
                excludingTourId?.let { add(it.toString()) }
            }.toTypedArray()
        }
        return readableDatabase.rawQuery(
            """
            SELECT tour_id, id, latitude, longitude
            FROM track_points
            $selection
            ORDER BY tour_id, recorded_at, id
            """.trimIndent(),
            arguments,
        ).use { cursor ->
            val routes = mutableListOf<List<SpurCoordinate>>()
            var activeRoute = mutableListOf<SpurCoordinate>()
            var previous: StoredRoadHistoryPoint? = null

            fun finishActiveRoute() {
                if (activeRoute.size >= 2) routes += activeRoute
                activeRoute = mutableListOf()
            }

            while (cursor.moveToNext()) {
                val current = StoredRoadHistoryPoint(
                    tourId = cursor.getLong(0),
                    id = cursor.getLong(1),
                    coordinate = SpurCoordinate(
                        latitude = cursor.getDouble(2),
                        longitude = cursor.getDouble(3),
                    ),
                )
                val from = previous
                if (
                    from != null &&
                    from.tourId == current.tourId &&
                    (afterPointId == null || from.id > afterPointId || current.id > afterPointId) &&
                    bounds.intersects(from.coordinate, current.coordinate)
                ) {
                    if (activeRoute.isEmpty()) activeRoute += from.coordinate
                    if (activeRoute.last() != from.coordinate) {
                        finishActiveRoute()
                        activeRoute += from.coordinate
                    }
                    activeRoute += current.coordinate
                } else {
                    finishActiveRoute()
                }
                previous = current
            }
            finishActiveRoute()
            routes
        }
    }

    private fun points(db: SQLiteDatabase, tourId: Long): List<TrackPoint> =
        db.rawQuery(
            """
            SELECT id, latitude, longitude, recorded_at
            FROM track_points
            WHERE tour_id = ?
            ORDER BY recorded_at, id
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
        stationaryExitFixes.remove(tourId)
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
        gpsStartGates.remove(id)
        stationaryExitFixes.remove(id)
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
                            title = cursor.getString(5),
                        ),
                    )
                }
            }
        }

    private fun queryTourNotificationSummary(
        where: String,
        args: Array<String> = emptyArray(),
        tail: String,
    ): TourNotificationSummary? =
        readableDatabase.rawQuery(
            """
            SELECT id, started_at, distance_meters
            FROM tours
            WHERE $where
            $tail
            """.trimIndent(),
            args,
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                TourNotificationSummary(
                    id = cursor.getLong(0),
                    startedAt = cursor.getLong(1),
                    distanceMeters = cursor.getDouble(2),
                )
            }
        }
}

private const val RoadHistorySignaturePrime = 1_000_000_007L
private const val RoadHistoryCoordinatePrecision = 1_000_000L
