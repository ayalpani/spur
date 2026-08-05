package app.spur

internal data class EditorLocation(
    val point: TrackPoint,
    val routeIndex: Int,
    val distanceFromStartMeters: Double,
    val elapsedMillis: Long,
    val moments: List<MapMoment>,
)

internal data class TourPresentation(
    val mapMoments: List<MapMoment>,
    val editorLocations: List<EditorLocation>,
    val editorLocationsByPointId: Map<Long, EditorLocation>,
) {
    companion object {
        val Empty = TourPresentation(emptyList(), emptyList(), emptyMap())
    }
}

internal data class TrackPointDeletion(
    val retainedPoints: List<TrackPoint>,
    val retainedPointIds: Set<Long>,
    val updatedMoments: List<MapMoment>,
    val selectedPointId: Long?,
)

internal fun trackPointDeletion(
    points: List<TrackPoint>,
    moments: List<MapMoment>,
    deletedPointId: Long,
): TrackPointDeletion? {
    val deletedIndex = points.indexOfFirst { it.id == deletedPointId }
    if (deletedIndex < 0) return null
    val retained = points.filterNot { it.id == deletedPointId }
    return TrackPointDeletion(
        retainedPoints = retained,
        retainedPointIds = retained.mapTo(mutableSetOf(), TrackPoint::id),
        updatedMoments = moments.map { moment ->
            if (moment.trackPointId != deletedPointId) {
                moment
            } else {
                moment.copy(
                    trackPointId = nearestTrackPoint(
                        retained,
                        moment.latitude,
                        moment.longitude,
                    )?.id,
                )
            }
        },
        selectedPointId = retained.getOrNull(
            deletedIndex.coerceAtMost(retained.lastIndex),
        )?.id,
    )
}

internal fun tourPresentation(
    tour: Tour,
    points: List<TrackPoint>,
    moments: List<MapMoment>,
): TourPresentation {
    if (points.isEmpty()) {
        return TourPresentation(
            mapMoments = moments,
            editorLocations = emptyList(),
            editorLocationsByPointId = emptyMap(),
        )
    }
    val pointsById = points.associateBy(TrackPoint::id)
    val attachedMoments = moments.map { moment ->
        val point = moment.trackPointId?.let(pointsById::get)
            ?: nearestTrackPoint(points, moment.latitude, moment.longitude)
        moment to point
    }
    val momentsByPoint = attachedMoments
        .filter { it.second != null }
        .groupBy(
            keySelector = { requireNotNull(it.second).id },
            valueTransform = { it.first },
        )
    val cumulativeDistance = DoubleArray(points.size)
    for (index in 1 until points.size) {
        cumulativeDistance[index] = cumulativeDistance[index - 1] +
            editorDistanceMeters(points[index - 1], points[index])
    }
    val editorLocations = points.mapIndexed { index, point ->
        EditorLocation(
            point = point,
            routeIndex = index,
            distanceFromStartMeters = cumulativeDistance[index],
            elapsedMillis = (point.recordedAt - tour.startedAt).coerceAtLeast(0L),
            moments = momentsByPoint[point.id].orEmpty(),
        )
    }
    return TourPresentation(
        mapMoments = attachedMoments.map { (moment, point) ->
            point?.let {
                moment.copy(latitude = it.latitude, longitude = it.longitude)
            } ?: moment
        },
        editorLocations = editorLocations,
        editorLocationsByPointId = editorLocations.associateBy { it.point.id },
    )
}

internal fun editorLocations(
    tour: Tour,
    points: List<TrackPoint>,
    moments: List<MapMoment>,
): List<EditorLocation> {
    return tourPresentation(tour, points, moments).editorLocations
}

internal fun nearestTrackPoint(
    points: List<TrackPoint>,
    latitude: Double,
    longitude: Double,
): TrackPoint? =
    points.minByOrNull { point ->
        haversineDistanceMeters(
            fromLatitude = point.latitude,
            fromLongitude = point.longitude,
            toLatitude = latitude,
            toLongitude = longitude,
        )
    }

internal fun momentsAttachedToTrackPoints(
    moments: List<MapMoment>,
    points: List<TrackPoint>,
): List<MapMoment> {
    val syntheticTour = Tour(
        id = 0L,
        startedAt = points.firstOrNull()?.recordedAt ?: 0L,
        endedAt = null,
        distanceMeters = 0.0,
        pointCount = points.size,
    )
    return tourPresentation(syntheticTour, points, moments).mapMoments
}

private fun editorDistanceMeters(from: TrackPoint, to: TrackPoint): Double =
    haversineDistanceMeters(
        fromLatitude = from.latitude,
        fromLongitude = from.longitude,
        toLatitude = to.latitude,
        toLongitude = to.longitude,
    )
