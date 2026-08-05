package app.spur

internal data class TourPause(
    val pointId: Long,
    val latitude: Double,
    val longitude: Double,
    val startedAt: Long,
    val endedAt: Long,
) {
    val durationMillis: Long
        get() = (endedAt - startedAt).coerceAtLeast(0L)
}

internal data class StationaryCluster(
    val latitude: Double,
    val longitude: Double,
    val startedAt: Long,
    val recordedAt: Long,
    val sampleCount: Int,
)

internal const val StationaryWindowMillis = 5 * 60 * 1_000L
private const val StationaryMaximumSpreadMeters = 25.0
private const val StationaryMinimumExitMeters = 12.0

internal fun tourPauses(points: List<TrackPoint>): List<TourPause> =
    points.mapNotNull { point ->
        val startedAt = point.pauseStartedAt ?: return@mapNotNull null
        if (point.recordedAt - startedAt < StationaryWindowMillis) return@mapNotNull null
        TourPause(
            pointId = point.id,
            latitude = point.latitude,
            longitude = point.longitude,
            startedAt = startedAt,
            endedAt = point.recordedAt,
        )
    }

internal fun isStationaryPauseCandidate(
    elapsedMillis: Long,
    distanceMeters: Float,
    accuracyMeters: Float,
): Boolean =
    elapsedMillis >= StationaryWindowMillis &&
        accuracyMeters <= 40f &&
        distanceMeters <= maxOf(StationaryMaximumSpreadMeters, accuracyMeters.toDouble())

internal fun stationaryExitDistanceMeters(accuracyMeters: Float): Double =
    maxOf(StationaryMinimumExitMeters, accuracyMeters.toDouble())

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
                haversineDistanceMeters(
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

internal fun stationaryCollapseDistanceDelta(
    previous: TrackPoint?,
    replaced: List<TrackPoint>,
    replacement: StationaryCluster,
): Double {
    if (replaced.isEmpty()) return 0.0
    val oldDistance = trackDistanceMeters(listOfNotNull(previous) + replaced)
    val newDistance = previous?.let {
        haversineDistanceMeters(
            fromLatitude = it.latitude,
            fromLongitude = it.longitude,
            toLatitude = replacement.latitude,
            toLongitude = replacement.longitude,
        )
    } ?: 0.0
    return newDistance - oldDistance
}
