package app.spur

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.os.SystemClock
import java.io.File
import java.io.IOException
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val processTourStoreLock = Any()

@Volatile
private var processTourStore: TourStore? = null

internal fun Context.tourStore(): TourStore =
    processTourStore ?: synchronized(processTourStoreLock) {
        processTourStore ?: TourStore(applicationContext).also { processTourStore = it }
    }

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

internal data class TourRevision(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val distanceMeters: Double,
    val pointCount: Int,
    val title: String?,
    val maximumPointId: Long?,
    val maximumPointRecordedAt: Long?,
) {
    fun asTour() = Tour(
        id = id,
        startedAt = startedAt,
        endedAt = endedAt,
        distanceMeters = distanceMeters,
        pointCount = pointCount,
        title = title,
    )
}

internal fun shouldReloadTour(
    previous: TourRevision?,
    current: TourRevision?,
): Boolean = previous != current

internal const val TourTitleMaximumCharacters = 80

internal fun normalizeTourTitle(title: String): String? =
    title.trim().take(TourTitleMaximumCharacters).ifEmpty { null }

data class TrackPoint(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val recordedAt: Long,
    val pauseStartedAt: Long? = null,
    val sampleCount: Int = 1,
)

internal data class TourStartResult(
    val id: Long,
    val created: Boolean,
)

private const val StationaryExitFixCount = 3

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

internal fun haversineDistanceMeters(
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
        haversineDistanceMeters(
            fromLatitude = from.latitude,
            fromLongitude = from.longitude,
            toLatitude = to.latitude,
            toLongitude = to.longitude,
        )
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
    val sample: RoadTrackSample,
)

class TourStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, SpurDatabaseName, null, 4) {
    private val appContext = context.applicationContext
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
        val finished = writableDatabase.update(
            "tours",
            ContentValues().apply { put("ended_at", now) },
            "id = ? AND ended_at IS NULL",
            arrayOf(id.toString()),
        ) > 0
        if (finished) appContext.scheduleAutomaticBackup()
        return finished
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
        val finished = try {
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
            val updated = db.update(
                "tours",
                ContentValues().apply {
                    put("ended_at", endRecordedAt)
                    put("distance_meters", trackDistanceMeters(points(db, id)))
                },
                "id = ? AND ended_at IS NULL",
                arrayOf(id.toString()),
            ) > 0
            if (!updated) return false
            db.setTransactionSuccessful()
            true
        } finally {
            db.endTransaction()
        }
        if (finished) appContext.scheduleAutomaticBackup()
        return finished
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
            !allowFastMovement &&
            storedPrevious != null &&
            distance != null &&
            elapsed != null &&
            isStationaryPauseCandidate(elapsed, distance, accuracyMeters)
        ) {
            updateStationaryCluster(
                db = db,
                tourId = tourId,
                clusterPoint = storedPrevious,
                latitude = (storedPrevious.latitude + location.latitude) / 2,
                longitude = (storedPrevious.longitude + location.longitude) / 2,
                recordedAt = location.time,
                accuracyMeters = accuracyMeters,
                sampleCount = storedPrevious.clusterSampleCount.coerceAtLeast(1) + 1,
                clusterStartedAt = storedPrevious.recordedAt,
            )
            return true
        }
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
        if (distance <= stationaryExitDistanceMeters(accuracyMeters)) {
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
        var addedDistance = 0.0
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
                previousLocation?.let {
                    addedDistance += haversineDistanceMeters(
                        fromLatitude = it.latitude,
                        fromLongitude = it.longitude,
                        toLatitude = location.latitude,
                        toLongitude = location.longitude,
                    )
                }
                previousLocation = location
            }
            if (addedDistance > 0.0) {
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

    private fun adjustTourDistance(
        db: SQLiteDatabase,
        tourId: Long,
        distanceDelta: Double,
    ) {
        db.execSQL(
            """
            UPDATE tours
            SET distance_meters = MAX(0, distance_meters + ?)
            WHERE id = ?
            """.trimIndent(),
            arrayOf(distanceDelta, tourId),
        )
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
        val previous = pointBefore(
            db = db,
            tourId = tourId,
            recordedAt = recent.first().recordedAt,
            pointId = recent.first().id,
        )
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
            adjustTourDistance(db, tourId, distanceDelta)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun pointBefore(
        db: SQLiteDatabase,
        tourId: Long,
        recordedAt: Long,
        pointId: Long,
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
                recordedAt.toString(),
                recordedAt.toString(),
                pointId.toString(),
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
        clusterStartedAt: Long? = clusterPoint.clusterStartedAt,
    ) {
        val previousPoint = pointBefore(
            db = db,
            tourId = tourId,
            recordedAt = clusterPoint.recordedAt,
            pointId = clusterPoint.id,
        )
        val oldDistance = previousPoint?.let {
            haversineDistanceMeters(
                fromLatitude = it.latitude,
                fromLongitude = it.longitude,
                toLatitude = clusterPoint.latitude,
                toLongitude = clusterPoint.longitude,
            )
        } ?: 0.0
        val newDistance = previousPoint?.let {
            haversineDistanceMeters(
                fromLatitude = it.latitude,
                fromLongitude = it.longitude,
                toLatitude = latitude,
                toLongitude = longitude,
            )
        } ?: 0.0
        db.beginTransaction()
        try {
            db.update(
                "track_points",
                ContentValues().apply {
                    put("latitude", latitude)
                    put("longitude", longitude)
                    put("recorded_at", recordedAt)
                    put("accuracy_meters", accuracyMeters)
                    put("cluster_started_at", clusterStartedAt)
                    put("cluster_sample_count", sampleCount)
                },
                "tour_id = ? AND id = ?",
                arrayOf(tourId.toString(), clusterPoint.id.toString()),
            )
            adjustTourDistance(db, tourId, newDistance - oldDistance)
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
            val duplicateIndex = AutomaticStartDuplicateIndex(points(db, tourId))
            prepared.forEach { location ->
                if (!duplicateIndex.contains(location)) {
                    val id = insertRawLocation(
                        db = db,
                        tourId = tourId,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        recordedAt = location.recordedAt,
                        accuracyMeters = location.accuracyMeters,
                    )
                    duplicateIndex.add(
                        TrackPoint(
                            id = id,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            recordedAt = location.recordedAt,
                        ),
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
    internal fun activeTourId(): Long? =
        readableDatabase.rawQuery(
            """
            SELECT id
            FROM tours
            WHERE ended_at IS NULL
            ORDER BY started_at DESC
            LIMIT 1
            """.trimIndent(),
            emptyArray(),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }

    @Synchronized
    internal fun tourRevision(id: Long): TourRevision? =
        readableDatabase.rawQuery(
            """
            SELECT
                t.id,
                t.started_at,
                t.ended_at,
                t.distance_meters,
                COUNT(p.id),
                t.activity,
                MAX(p.id),
                (
                    SELECT latest.recorded_at
                    FROM track_points latest
                    WHERE latest.tour_id = t.id
                    ORDER BY latest.id DESC
                    LIMIT 1
                )
            FROM tours t
            LEFT JOIN track_points p ON p.tour_id = t.id
            WHERE t.id = ?
            GROUP BY t.id
            LIMIT 1
            """.trimIndent(),
            arrayOf(id.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                TourRevision(
                    id = cursor.getLong(0),
                    startedAt = cursor.getLong(1),
                    endedAt = if (cursor.isNull(2)) null else cursor.getLong(2),
                    distanceMeters = cursor.getDouble(3),
                    pointCount = cursor.getInt(4),
                    title = cursor.getString(5),
                    maximumPointId = if (cursor.isNull(6)) null else cursor.getLong(6),
                    maximumPointRecordedAt = if (cursor.isNull(7)) null else cursor.getLong(7),
                )
            }
        }

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
    ): List<List<RoadTrackSample>> {
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
            SELECT tour_id, id, latitude, longitude, recorded_at
            FROM track_points
            $selection
            ORDER BY tour_id, recorded_at, id
            """.trimIndent(),
            arguments,
        ).use { cursor ->
            val routes = mutableListOf<List<RoadTrackSample>>()
            var activeRoute = mutableListOf<RoadTrackSample>()
            var previous: StoredRoadHistoryPoint? = null

            fun finishActiveRoute() {
                if (activeRoute.size >= 2) routes += activeRoute
                activeRoute = mutableListOf()
            }

            while (cursor.moveToNext()) {
                val current = StoredRoadHistoryPoint(
                    tourId = cursor.getLong(0),
                    id = cursor.getLong(1),
                    sample = RoadTrackSample(
                        coordinate = SpurCoordinate(
                            latitude = cursor.getDouble(2),
                            longitude = cursor.getDouble(3),
                        ),
                        recordedAtMillis = cursor.getLong(4),
                    ),
                )
                val from = previous
                if (
                    from != null &&
                    from.tourId == current.tourId &&
                    (afterPointId == null || from.id > afterPointId || current.id > afterPointId) &&
                    bounds.intersects(from.sample.coordinate, current.sample.coordinate)
                ) {
                    if (activeRoute.isEmpty()) activeRoute += from.sample
                    if (activeRoute.last() != from.sample) {
                        finishActiveRoute()
                        activeRoute += from.sample
                    }
                    activeRoute += current.sample
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
            SELECT id, latitude, longitude, recorded_at,
                   cluster_started_at, cluster_sample_count
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
                            pauseStartedAt = if (cursor.isNull(4)) null else cursor.getLong(4),
                            sampleCount = cursor.getInt(5),
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

    @Synchronized
    internal fun copyDatabaseTo(target: File) {
        val database = writableDatabase
        database.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
            if (cursor.moveToFirst()) check(cursor.getInt(0) == 0) {
                "Die Tourdatenbank ist noch beschäftigt."
            }
        }
        val source = File(database.path).canonicalFile
        val destination = target.canonicalFile
        require(source != destination)
        destination.parentFile?.mkdirs()
        source.inputStream().use { input ->
            destination.outputStream().use(input::copyTo)
        }
    }

    @Synchronized
    internal fun replaceDatabaseFrom(source: File) {
        validateSpurDatabase(source)
        val destination = appContext.getDatabasePath(SpurDatabaseName)
        require(source.canonicalFile != destination.canonicalFile)
        destination.parentFile?.mkdirs()
        val staged = File(destination.parentFile, "$SpurDatabaseName.restore")
        val previous = File(destination.parentFile, "$SpurDatabaseName.before-restore")
        close()
        staged.delete()
        previous.delete()
        source.copyTo(staged, overwrite = true)
        File("${destination.path}-wal").delete()
        File("${destination.path}-shm").delete()
        val hadDatabase = destination.exists()
        if (hadDatabase && !destination.renameTo(previous)) {
            staged.delete()
            throw IOException("Die aktuelle Tourdatenbank konnte nicht gesichert werden.")
        }
        if (!staged.renameTo(destination)) {
            if (hadDatabase) previous.renameTo(destination)
            throw IOException("Die wiederhergestellte Tourdatenbank konnte nicht aktiviert werden.")
        }
        previous.delete()
        gpsStartGates.clear()
        stationaryExitFixes.clear()
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
internal const val SpurDatabaseName = "spur.db"
private const val RoadHistoryCoordinatePrecision = 1_000_000L
