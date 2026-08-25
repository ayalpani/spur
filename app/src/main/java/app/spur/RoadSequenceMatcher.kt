package app.spur

import kotlin.math.abs

internal data class RoadMatchObservation(
    val coordinate: SpurCoordinate,
    val movementMeters: Double,
    val accuracyMeters: Double = RoadDefaultAccuracyMeters,
    val candidates: List<RoadCandidate>,
)

internal data class RoadTrackSample(
    val coordinate: SpurCoordinate,
    val recordedAtMillis: Long? = null,
    val accuracyMeters: Double = RoadDefaultAccuracyMeters,
)

private data class RoadMatchState(
    val candidate: RoadCandidate?,
    val cost: Double,
    val previousIndex: Int,
)

internal fun mostLikelyRoadCandidates(
    observations: List<RoadMatchObservation>,
    initialRoad: RenderedRoadSegment? = null,
    shouldContinue: () -> Boolean = { true },
): List<RoadCandidate?> {
    if (observations.isEmpty()) return emptyList()
    val layers = ArrayList<List<RoadMatchState>>(observations.size)

    observations.forEach { observation ->
        if (!shouldContinue()) return emptyList()
        val candidates = observation.candidates
            .sortedBy { roadEmissionCost(it, observation.accuracyMeters) }
            .take(RoadSequenceCandidateLimit)
            .map<RoadCandidate, RoadCandidate?> { it } + null
        val previousLayer = layers.lastOrNull()
        val layer = candidates.map { candidate ->
            val emission = candidate?.let {
                roadEmissionCost(it, observation.accuracyMeters)
            }
                ?: RoadUnmatchedObservationCost
            if (previousLayer == null) {
                RoadMatchState(
                    candidate = candidate,
                    cost = emission + initialRoadTransitionCost(initialRoad, candidate),
                    previousIndex = -1,
                )
            } else {
                val bestPrevious = previousLayer.indices.minByOrNull { previousIndex ->
                    previousLayer[previousIndex].cost + roadTransitionCost(
                        previous = previousLayer[previousIndex].candidate,
                        current = candidate,
                        movementMeters = observation.movementMeters,
                    )
                } ?: -1
                RoadMatchState(
                    candidate = candidate,
                    cost = emission + previousLayer[bestPrevious].cost + roadTransitionCost(
                        previous = previousLayer[bestPrevious].candidate,
                        current = candidate,
                        movementMeters = observation.movementMeters,
                    ),
                    previousIndex = bestPrevious,
                )
            }
        }
        layers += layer
    }

    var stateIndex = layers.last().indices.minByOrNull { layers.last()[it].cost }
        ?: return emptyList()
    return MutableList<RoadCandidate?>(layers.size) { null }.also { matches ->
        for (layerIndex in layers.lastIndex downTo 0) {
            val state = layers[layerIndex][stateIndex]
            matches[layerIndex] = state.candidate
            stateIndex = state.previousIndex
        }
    }
}

private fun roadEmissionCost(
    candidate: RoadCandidate,
    accuracyMeters: Double,
): Double =
    candidate.projection.distanceMeters +
        candidate.headingPenalty +
        candidate.road.kind.matchPenaltyMeters +
        ((accuracyMeters - RoadDefaultAccuracyMeters).coerceAtLeast(0.0) *
            RoadAccuracyUncertaintyWeight)

private fun initialRoadTransitionCost(
    initialRoad: RenderedRoadSegment?,
    candidate: RoadCandidate?,
): Double = when {
    initialRoad == null || candidate == null || initialRoad.key == candidate.road.key -> 0.0
    connectedEndpointFractions(initialRoad, candidate.road) != null ->
        RoadConnectedTransitionCost
    else -> RoadUnmatchedTransitionCost
}

private fun roadTransitionCost(
    previous: RoadCandidate?,
    current: RoadCandidate?,
    movementMeters: Double,
): Double {
    if (previous == null || current == null) {
        return if (previous == null && current == null) 0.0 else RoadUnmatchedTransitionCost
    }
    val networkDistance = roadNetworkDistance(previous, current)
        ?: return Double.POSITIVE_INFINITY
    val connectionCost = if (previous.road.key == current.road.key) {
        0.0
    } else {
        RoadConnectedTransitionCost
    }
    return connectionCost +
        (abs(networkDistance - movementMeters) * RoadTransitionDifferenceWeight)
            .coerceAtMost(RoadMaximumTransitionDifferenceCost)
}

private fun roadNetworkDistance(
    previous: RoadCandidate,
    current: RoadCandidate,
): Double? {
    if (previous.road.key == current.road.key) {
        return abs(
            previous.projection.distanceAlongMeters - current.projection.distanceAlongMeters,
        )
    }
    val (previousEndpoint, currentEndpoint) =
        connectedEndpointFractions(previous.road, current.road) ?: return null
    return abs(previous.projection.fraction - previousEndpoint) *
        roadLengthMeters(previous.road.points) +
        abs(current.projection.fraction - currentEndpoint) *
        roadLengthMeters(current.road.points)
}

private const val RoadSequenceCandidateLimit = 8
private const val RoadDefaultAccuracyMeters = 10.0
private const val RoadAccuracyUncertaintyWeight = 0.2
private const val RoadUnmatchedObservationCost = 24.0
private const val RoadUnmatchedTransitionCost = 8.0
private const val RoadConnectedTransitionCost = 1.0
private const val RoadTransitionDifferenceWeight = 0.35
private const val RoadMaximumTransitionDifferenceCost = 24.0
