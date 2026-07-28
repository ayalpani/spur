package app.spur

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class EditorLocation(
    val point: TrackPoint,
    val routeIndex: Int,
    val distanceFromStartMeters: Double,
    val elapsedMillis: Long,
    val moments: List<MapMoment>,
)

internal fun editorLocations(
    tour: Tour,
    points: List<TrackPoint>,
    moments: List<MapMoment>,
): List<EditorLocation> {
    if (points.isEmpty()) return emptyList()
    val cumulativeDistance = DoubleArray(points.size)
    for (index in 1 until points.size) {
        cumulativeDistance[index] = cumulativeDistance[index - 1] +
            editorDistanceMeters(points[index - 1], points[index])
    }

    val momentsByPoint = moments.groupBy { moment ->
        moment.trackPointId ?: closestPoint(points, moment)?.id
    }
    return points.mapIndexed { index, point ->
        EditorLocation(
            point = point,
            routeIndex = index,
            distanceFromStartMeters = cumulativeDistance[index],
            elapsedMillis = (point.recordedAt - tour.startedAt).coerceAtLeast(0L),
            moments = momentsByPoint[point.id].orEmpty(),
        )
    }
}

internal fun nearestTrackPoint(
    points: List<TrackPoint>,
    latitude: Double,
    longitude: Double,
): TrackPoint? =
    points.minByOrNull { point ->
        editorDistanceMeters(
            point,
            TrackPoint(
                id = -1,
                latitude = latitude,
                longitude = longitude,
                recordedAt = 0,
            ),
        )
    }

private fun closestPoint(points: List<TrackPoint>, moment: MapMoment): TrackPoint? =
    nearestTrackPoint(points, moment.latitude, moment.longitude)

private fun editorDistanceMeters(from: TrackPoint, to: TrackPoint): Double {
    val earthRadius = 6_371_000.0
    val latitudeDelta = Math.toRadians(to.latitude - from.latitude)
    val longitudeDelta = Math.toRadians(to.longitude - from.longitude)
    val startLatitude = Math.toRadians(from.latitude)
    val endLatitude = Math.toRadians(to.latitude)
    val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
        cos(startLatitude) * cos(endLatitude) *
        sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
    return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
}
