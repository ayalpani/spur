package app.spur

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max

internal data class RoadHistoryFingerprint(
    val maximumPointId: Long = 0L,
    val pointCount: Long = 0L,
    val signature: Long = 0L,
) {
    operator fun plus(other: RoadHistoryFingerprint) = RoadHistoryFingerprint(
        maximumPointId = maxOf(maximumPointId, other.maximumPointId),
        pointCount = pointCount + other.pointCount,
        signature = signature + other.signature,
    )
}

internal data class CachedRoadCoverage(
    val fingerprint: RoadHistoryFingerprint,
    val segments: List<List<SpurCoordinate>>,
    val needsCompaction: Boolean,
)

internal data class RoadHistoryBounds(
    val minimumLatitude: Double,
    val maximumLatitude: Double,
    val minimumLongitude: Double,
    val maximumLongitude: Double,
) {
    fun intersects(first: SpurCoordinate, second: SpurCoordinate): Boolean =
        maxOf(first.latitude, second.latitude) >= minimumLatitude &&
            minOf(first.latitude, second.latitude) <= maximumLatitude &&
            maxOf(first.longitude, second.longitude) >= minimumLongitude &&
            minOf(first.longitude, second.longitude) <= maximumLongitude
}

internal fun roadHistoryBounds(
    center: SpurCoordinate,
    radiusMeters: Double,
): RoadHistoryBounds {
    val latitudeRadius = radiusMeters / RoadHistoryMetersPerDegree
    val longitudeRadius = latitudeRadius /
        cos(center.latitude * PI / 180.0).coerceAtLeast(0.01)
    return RoadHistoryBounds(
        minimumLatitude = center.latitude - latitudeRadius,
        maximumLatitude = center.latitude + latitudeRadius,
        minimumLongitude = center.longitude - longitudeRadius,
        maximumLongitude = center.longitude + longitudeRadius,
    )
}

internal fun roadHistoryBounds(
    roads: List<RenderedRoadSegment>,
    paddingMeters: Double,
): RoadHistoryBounds? {
    val points = roads.asSequence().flatMap { it.points.asSequence() }
    var minimumLatitude = Double.POSITIVE_INFINITY
    var maximumLatitude = Double.NEGATIVE_INFINITY
    var minimumLongitude = Double.POSITIVE_INFINITY
    var maximumLongitude = Double.NEGATIVE_INFINITY
    points.forEach { point ->
        minimumLatitude = minOf(minimumLatitude, point.latitude)
        maximumLatitude = maxOf(maximumLatitude, point.latitude)
        minimumLongitude = minOf(minimumLongitude, point.longitude)
        maximumLongitude = maxOf(maximumLongitude, point.longitude)
    }
    if (!minimumLatitude.isFinite()) return null
    val centerLatitude = (minimumLatitude + maximumLatitude) / 2.0
    val latitudePadding = paddingMeters / RoadHistoryMetersPerDegree
    val longitudePadding = latitudePadding /
        cos(centerLatitude * PI / 180.0).coerceAtLeast(0.01)
    return RoadHistoryBounds(
        minimumLatitude = minimumLatitude - latitudePadding,
        maximumLatitude = maximumLatitude + latitudePadding,
        minimumLongitude = minimumLongitude - longitudePadding,
        maximumLongitude = maximumLongitude + longitudePadding,
    )
}

internal class RoadCoverageStore(context: Context) :
    SQLiteOpenHelper(
        context.applicationContext,
        RoadCoverageDatabaseName,
        null,
        RoadCoverageDatabaseVersion,
    ) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE road_coverage_cells (
                cache_key TEXT PRIMARY KEY,
                maximum_point_id INTEGER NOT NULL,
                point_count INTEGER NOT NULL,
                signature INTEGER NOT NULL,
                needs_compaction INTEGER NOT NULL,
                segments BLOB NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE road_coverage_state (
                id INTEGER PRIMARY KEY,
                maximum_point_id INTEGER NOT NULL,
                point_count INTEGER NOT NULL,
                signature INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS road_coverage_cells")
        db.execSQL("DROP TABLE IF EXISTS road_coverage_state")
        onCreate(db)
    }

    @Synchronized
    fun coverage(cacheKey: String): CachedRoadCoverage? =
        readableDatabase.query(
            "road_coverage_cells",
            arrayOf(
                "maximum_point_id",
                "point_count",
                "signature",
                "needs_compaction",
                "segments",
            ),
            "cache_key = ?",
            arrayOf(cacheKey),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val segments = decodeRoadCoverageSegments(cursor.getBlob(4))
                ?: return@use null
            CachedRoadCoverage(
                fingerprint = RoadHistoryFingerprint(
                    maximumPointId = cursor.getLong(0),
                    pointCount = cursor.getLong(1),
                    signature = cursor.getLong(2),
                ),
                needsCompaction = cursor.getInt(3) != 0,
                segments = segments,
            )
        }

    @Synchronized
    fun historyBaseline(): RoadHistoryFingerprint? =
        readableDatabase.query(
            "road_coverage_state",
            arrayOf("maximum_point_id", "point_count", "signature"),
            "id = ?",
            arrayOf(RoadCoverageStateId.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            RoadHistoryFingerprint(
                maximumPointId = cursor.getLong(0),
                pointCount = cursor.getLong(1),
                signature = cursor.getLong(2),
            )
        }

    @Synchronized
    fun replaceHistoryBaseline(fingerprint: RoadHistoryFingerprint) {
        writableDatabase.insertWithOnConflict(
            "road_coverage_state",
            null,
            ContentValues().apply {
                put("id", RoadCoverageStateId)
                put("maximum_point_id", fingerprint.maximumPointId)
                put("point_count", fingerprint.pointCount)
                put("signature", fingerprint.signature)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun replace(cacheKey: String, coverage: CachedRoadCoverage) {
        writableDatabase.insertWithOnConflict(
            "road_coverage_cells",
            null,
            ContentValues().apply {
                put("cache_key", cacheKey)
                put("maximum_point_id", coverage.fingerprint.maximumPointId)
                put("point_count", coverage.fingerprint.pointCount)
                put("signature", coverage.fingerprint.signature)
                put("needs_compaction", if (coverage.needsCompaction) 1 else 0)
                put("segments", encodeRoadCoverageSegments(coverage.segments))
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun overviewSegments(): List<List<SpurCoordinate>> =
        readableDatabase.query(
            "road_coverage_cells",
            arrayOf("cache_key", "segments"),
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            mergeOverviewRoadCoverageSegments(
                buildList {
                    while (cursor.moveToNext()) {
                        val cacheKey = cursor.getString(0)
                        if (!isOverviewRoadCoverageCacheKey(cacheKey)) continue
                        val zoom = roadCoverageCacheZoom(cacheKey) ?: continue
                        decodeRoadCoverageSegments(cursor.getBlob(1))?.forEach { segment ->
                            add(zoom to segment)
                        }
                    }
                },
            )
        }

    @Synchronized
    fun clear() {
        writableDatabase.delete("road_coverage_cells", null, null)
    }
}

internal fun preservesRoadCoverage(
    cached: RoadHistoryFingerprint,
    current: RoadHistoryFingerprint,
    added: RoadHistoryFingerprint,
): Boolean = cached == current || cached + added == current

internal fun shouldRefreshRoadCoverageGeometry(
    hasCachedCoverage: Boolean,
    cacheIsCurrent: Boolean,
    historyCanAppend: Boolean,
    needsCompaction: Boolean,
    roadLoadIsApplied: Boolean,
): Boolean = hasCachedCoverage &&
    (cacheIsCurrent || historyCanAppend) &&
    !needsCompaction &&
    !roadLoadIsApplied

internal fun isDetailRoadCoverageCacheKey(cacheKey: String): Boolean {
    val zoom = roadCoverageCacheZoom(cacheKey) ?: return false
    return zoom >= RoadHistoryDetailZoom.toInt()
}

internal fun isOverviewRoadCoverageCacheKey(cacheKey: String): Boolean {
    val zoom = roadCoverageCacheZoom(cacheKey) ?: return false
    return zoom >= RoadHistoryMinimumZoom.toInt()
}

private fun roadCoverageCacheZoom(cacheKey: String): Int? {
    val parts = cacheKey.split(':', limit = 3)
    return parts.takeIf {
        it.size == 3 && it[0].toIntOrNull() == RoadCoverageAlgorithmVersion
    }?.get(1)?.toIntOrNull()
}

private data class RoadCoverageArc(
    val key: String,
    val from: String,
    val to: String,
) {
    fun other(node: String): String = if (node == from) to else from
}

internal fun mergeUniqueRoadCoverageArcs(
    segments: List<List<SpurCoordinate>>,
): List<List<SpurCoordinate>> {
    val coordinates = linkedMapOf<String, SpurCoordinate>()
    val arcs = linkedMapOf<String, RoadCoverageArc>()
    segments.forEach { segment ->
        segment.zipWithNext().forEach { (from, to) ->
            val fromKey = roadNodeKey(from)
            val toKey = roadNodeKey(to)
            if (fromKey == toKey) return@forEach
            coordinates.putIfAbsent(fromKey, from)
            coordinates.putIfAbsent(toKey, to)
            val first = minOf(fromKey, toKey)
            val second = maxOf(fromKey, toKey)
            val key = "$first|$second"
            arcs.putIfAbsent(key, RoadCoverageArc(key, first, second))
        }
    }
    val adjacency = buildMap<String, MutableList<RoadCoverageArc>> {
        arcs.values.forEach { arc ->
            getOrPut(arc.from, ::mutableListOf) += arc
            getOrPut(arc.to, ::mutableListOf) += arc
        }
    }
    val visited = mutableSetOf<String>()
    val merged = mutableListOf<List<SpurCoordinate>>()

    fun consume(start: String, first: RoadCoverageArc) {
        if (first.key in visited) return
        val points = mutableListOf(coordinates.getValue(start))
        var node = start
        var arc = first
        while (arc.key !in visited) {
            visited += arc.key
            node = arc.other(node)
            points += coordinates.getValue(node)
            val connected = adjacency.getValue(node)
            if (connected.size != 2) break
            arc = connected.firstOrNull { it.key !in visited } ?: break
        }
        if (points.size >= 2) merged += points
    }

    adjacency
        .filterValues { it.size != 2 }
        .forEach { (node, connected) -> connected.forEach { consume(node, it) } }
    arcs.values.forEach { arc -> consume(arc.from, arc) }
    return merged
}

private data class CoverageGridCell(
    val latitude: Int,
    val longitude: Int,
)

private data class IndexedCoverageArc(
    val from: SpurCoordinate,
    val to: SpurCoordinate,
)

private class RoadCoverageOverlapIndex {
    private val cells = mutableMapOf<CoverageGridCell, MutableList<IndexedCoverageArc>>()

    fun covers(segment: List<SpurCoordinate>): Boolean = segment.zipWithNext().all { arc ->
        val (from, to) = arc
        listOf(
            from,
            SpurCoordinate(
                latitude = (from.latitude + to.latitude) / 2.0,
                longitude = (from.longitude + to.longitude) / 2.0,
            ),
            to,
        ).all { sample -> covers(sample, from, to) }
    }

    fun add(segment: List<SpurCoordinate>) {
        segment.zipWithNext().forEach { (from, to) ->
            val arc = IndexedCoverageArc(from, to)
            cellsFor(from, to).forEach { cell ->
                cells.getOrPut(cell, ::mutableListOf) += arc
            }
        }
    }

    private fun covers(
        sample: SpurCoordinate,
        candidateFrom: SpurCoordinate,
        candidateTo: SpurCoordinate,
    ): Boolean = cells[gridCell(sample)].orEmpty().any { arc ->
        directionsAlign(candidateFrom, candidateTo, arc.from, arc.to) &&
            projectOntoRoad(sample, listOf(arc.from, arc.to))
                ?.distanceMeters
                ?.let { it <= RoadCoverageOverlapToleranceMeters } == true
    }

    private fun cellsFor(
        from: SpurCoordinate,
        to: SpurCoordinate,
    ): Sequence<CoverageGridCell> {
        val fromCell = gridCell(from)
        val toCell = gridCell(to)
        val steps = max(
            abs(toCell.latitude - fromCell.latitude),
            abs(toCell.longitude - fromCell.longitude),
        ).coerceAtLeast(1)
        return sequence {
            val visited = mutableSetOf<CoverageGridCell>()
            for (step in 0..steps) {
                val fraction = step.toDouble() / steps
                val coordinate = SpurCoordinate(
                    latitude = from.latitude + (to.latitude - from.latitude) * fraction,
                    longitude = from.longitude + (to.longitude - from.longitude) * fraction,
                )
                val cell = gridCell(coordinate)
                for (latitudeOffset in -1..1) {
                    for (longitudeOffset in -1..1) {
                        val nearby = CoverageGridCell(
                            latitude = cell.latitude + latitudeOffset,
                            longitude = cell.longitude + longitudeOffset,
                        )
                        if (visited.add(nearby)) yield(nearby)
                    }
                }
            }
        }
    }

    private fun gridCell(coordinate: SpurCoordinate) = CoverageGridCell(
        latitude = floor(coordinate.latitude / RoadCoverageOverlapCellDegrees).toInt(),
        longitude = floor(coordinate.longitude / RoadCoverageOverlapCellDegrees).toInt(),
    )
}

internal fun mergeOverviewRoadCoverageSegments(
    candidates: List<Pair<Int, List<SpurCoordinate>>>,
): List<List<SpurCoordinate>> {
    val overlapIndex = RoadCoverageOverlapIndex()
    val accepted = buildList {
        candidates.sortedBy { it.first }.forEach { (_, segment) ->
            if (overlapIndex.covers(segment)) return@forEach
            add(segment)
            overlapIndex.add(segment)
        }
    }
    return mergeUniqueRoadCoverageArcs(accepted)
}

internal fun appendOverviewRoadCoverageSegments(
    existing: List<List<SpurCoordinate>>,
    additions: List<List<SpurCoordinate>>,
): List<List<SpurCoordinate>> {
    if (additions.isEmpty()) return existing
    val overlapIndex = RoadCoverageOverlapIndex()
    existing.forEach(overlapIndex::add)
    val accepted = additions.filter { segment ->
        if (overlapIndex.covers(segment)) {
            false
        } else {
            overlapIndex.add(segment)
            true
        }
    }
    return mergeUniqueRoadCoverageArcs(existing + accepted)
}

private fun directionsAlign(
    firstFrom: SpurCoordinate,
    firstTo: SpurCoordinate,
    secondFrom: SpurCoordinate,
    secondTo: SpurCoordinate,
): Boolean {
    val referenceLatitude = (
        firstFrom.latitude + firstTo.latitude +
            secondFrom.latitude + secondTo.latitude
        ) / 4.0
    val longitudeScale = cos(referenceLatitude * PI / 180.0)
    val firstX = (firstTo.longitude - firstFrom.longitude) * longitudeScale
    val firstY = firstTo.latitude - firstFrom.latitude
    val secondX = (secondTo.longitude - secondFrom.longitude) * longitudeScale
    val secondY = secondTo.latitude - secondFrom.latitude
    val denominator = hypot(firstX, firstY) * hypot(secondX, secondY)
    if (denominator == 0.0) return false
    return abs((firstX * secondX + firstY * secondY) / denominator) >=
        RoadCoverageOverlapMinimumAlignment
}

internal fun combineRoadCoverageSegments(
    cached: List<List<SpurCoordinate>>,
    added: List<List<SpurCoordinate>>,
): List<List<SpurCoordinate>> = (cached + added)
    .distinctBy { canonicalRoadKey(it) }

internal fun encodeRoadCoverageSegments(
    segments: List<List<SpurCoordinate>>,
): ByteArray = ByteArrayOutputStream().use { bytes ->
    DataOutputStream(bytes).use { output ->
        output.writeInt(RoadCoverageEncodingVersion)
        output.writeInt(segments.size)
        segments.forEach { segment ->
            output.writeInt(segment.size)
            segment.forEach { point ->
                output.writeDouble(point.latitude)
                output.writeDouble(point.longitude)
            }
        }
    }
    bytes.toByteArray()
}

internal fun decodeRoadCoverageSegments(bytes: ByteArray): List<List<SpurCoordinate>>? =
    runCatching {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == RoadCoverageEncodingVersion)
            val segmentCount = input.readInt()
            require(segmentCount in 0..RoadCoverageMaximumSegments)
            buildList(segmentCount) {
                repeat(segmentCount) {
                    val pointCount = input.readInt()
                    require(pointCount in 2..RoadCoverageMaximumPointsPerSegment)
                    add(
                        buildList(pointCount) {
                            repeat(pointCount) {
                                val latitude = input.readDouble()
                                val longitude = input.readDouble()
                                require(latitude.isFinite() && latitude in -90.0..90.0)
                                require(longitude.isFinite() && longitude in -180.0..180.0)
                                add(SpurCoordinate(latitude, longitude))
                            }
                        },
                    )
                }
            }.also { require(input.available() == 0) }
        }
    }.getOrNull()

internal const val RoadCoverageAlgorithmVersion = 2
private const val RoadCoverageDatabaseName = "road-coverage-cache.db"
private const val RoadCoverageDatabaseVersion = 3
private const val RoadCoverageStateId = 1
private const val RoadCoverageEncodingVersion = 1
private const val RoadCoverageMaximumSegments = 100_000
private const val RoadCoverageMaximumPointsPerSegment = 100_000
private const val RoadHistoryMetersPerDegree = 111_320.0
private const val RoadCoverageOverlapToleranceMeters = 2.0
private const val RoadCoverageOverlapCellDegrees = 0.0002
private const val RoadCoverageOverlapMinimumAlignment = 0.965925826
