package app.spur

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

internal fun mergeUniqueRoadCoverageArcs(
    segments: List<List<SpurCoordinate>>,
): List<List<SpurCoordinate>> {
    val coordinates = linkedMapOf<String, SpurCoordinate>()
    val arcs = linkedMapOf<String, RoadGraphArc<Unit>>()
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
            arcs.putIfAbsent(key, RoadGraphArc(key, first, second, Unit))
        }
    }
    return linearRoadGraphPaths(arcs.values).map { path ->
        path.nodes.map(coordinates::getValue)
    }
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

private const val RoadHistoryMetersPerDegree = 111_320.0
private const val RoadCoverageOverlapToleranceMeters = 2.0
private const val RoadCoverageOverlapCellDegrees = 0.0002
private const val RoadCoverageOverlapMinimumAlignment = 0.965925826
