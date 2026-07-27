package app.spur

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class EditorWaypoint(
    val trackPoint: TrackPoint,
    val distanceFromStartMeters: Double,
    val elapsedMillis: Long,
    val moments: List<MapMoment>,
)

internal fun buildEditorWaypoints(
    tour: Tour,
    points: List<TrackPoint>,
    moments: List<MapMoment>,
    selectedTrackPointId: Long? = null,
): List<EditorWaypoint> {
    if (points.isEmpty()) return emptyList()

    val momentPointIds = moments.mapNotNullTo(mutableSetOf()) { it.trackPointId }
    selectedTrackPointId?.let(momentPointIds::add)
    val cumulativeDistances = DoubleArray(points.size)
    for (index in 1 until points.size) {
        cumulativeDistances[index] = cumulativeDistances[index - 1] +
            distanceMeters(points[index - 1], points[index])
    }

    val included = mutableSetOf(0, points.lastIndex)
    var lastIncludedIndex = 0
    for (index in 1 until points.lastIndex) {
        val point = points[index]
        val distanceSinceLast = cumulativeDistances[index] - cumulativeDistances[lastIncludedIndex]
        val timeSinceLast = point.recordedAt - points[lastIncludedIndex].recordedAt
        if (
            distanceSinceLast >= WaypointDistanceMeters ||
            timeSinceLast >= WaypointElapsedMillis ||
            point.id in momentPointIds
        ) {
            included += index
            lastIncludedIndex = index
        }
    }
    points.indexOfFirst { it.id == selectedTrackPointId }
        .takeIf { it >= 0 }
        ?.let(included::add)

    val momentsByPoint = moments.groupBy { it.trackPointId }
    return included.sorted().map { index ->
        val point = points[index]
        EditorWaypoint(
            trackPoint = point,
            distanceFromStartMeters = cumulativeDistances[index],
            elapsedMillis = (point.recordedAt - tour.startedAt).coerceAtLeast(0L),
            moments = momentsByPoint[point.id].orEmpty(),
        )
    }
}

private fun distanceMeters(a: TrackPoint, b: TrackPoint): Double {
    val earthRadiusMeters = 6_371_000.0
    val latitudeDelta = Math.toRadians(b.latitude - a.latitude)
    val longitudeDelta = Math.toRadians(b.longitude - a.longitude)
    val startLatitude = Math.toRadians(a.latitude)
    val endLatitude = Math.toRadians(b.latitude)
    val haversine = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
        cos(startLatitude) * cos(endLatitude) *
        sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
    return earthRadiusMeters * 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
}

private const val WaypointDistanceMeters = 100.0
private const val WaypointElapsedMillis = 5 * 60 * 1_000L
