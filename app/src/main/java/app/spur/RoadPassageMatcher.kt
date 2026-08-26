package app.spur

import kotlin.math.ceil

internal data class RoadTrackSample(
    val coordinate: SpurCoordinate,
    val recordedAtMillis: Long? = null,
)

private data class RoadPassageState(
    val side: Int,
    val crossedInterior: Boolean,
    val startedAtObservation: Int,
    val distanceMeters: Double,
    val matchedObservations: Int,
    val lastSeenAtMillis: Long?,
    val lastSeenObservation: Int,
)

private data class RoadPassageEvidence(
    val road: RenderedRoadSegment,
    val section: Int,
    val startedAtObservation: Int,
    val endedAtObservation: Int,
    val distanceMeters: Double,
    val matchedObservations: Int,
) {
    val meanDistanceMeters: Double
        get() = if (matchedObservations == 0) Double.MAX_VALUE else
            distanceMeters / matchedObservations
}

private data class RoadPassageObservation(
    val coordinate: SpurCoordinate,
    val measured: Boolean,
)

internal fun historicalRoadPassages(
    routes: List<List<RoadTrackSample>>,
    roads: List<RenderedRoadSegment>,
    shouldContinue: () -> Boolean = { true },
): Map<String, CompletedRoad> {
    if (roads.isEmpty()) return emptyMap()
    val roadIndex = RoadSpatialIndex(roads)
    val roadsByKey = roads.associateBy(RenderedRoadSegment::key)
    val counts = mutableMapOf<String, Int>()

    routes.forEach { route ->
        val states = mutableMapOf<String, RoadPassageState>()
        val passages = mutableListOf<RoadPassageEvidence>()
        val observations = route.firstOrNull()?.let { first ->
            mutableListOf(RoadPassageObservation(first.coordinate, measured = true))
        } ?: mutableListOf()
        var observationIndex = observations.size
        var section = 0
        route.zipWithNext().forEach { (fromSample, toSample) ->
            if (!shouldContinue()) return emptyMap()
            val from = fromSample.coordinate
            val to = toSample.coordinate
            val distance = localCoordinateDistanceMeters(from, to)
            val elapsedMillis = fromSample.recordedAtMillis?.let { fromTime ->
                toSample.recordedAtMillis?.minus(fromTime)
            }
            if (
                distance > RoadPassageMaximumGapMeters ||
                elapsedMillis?.let { it <= 0L || it > RoadPassageMaximumTimeGapMillis } == true ||
                !roadIndex.intersectsLoadedBounds(from, to)
            ) {
                states.clear()
                section++
                observationIndex++
                observations += RoadPassageObservation(to, measured = true)
                return@forEach
            }
            if (distance == 0.0) return@forEach

            val stepCount = ceil(distance / RoadPassageSampleSpacingMeters).toInt()
                .coerceAtLeast(1)
            for (step in 1..stepCount) {
                if (!shouldContinue()) return emptyMap()
                observationIndex++
                val fraction = step.toDouble() / stepCount
                val coordinate = interpolate(from, to, fraction)
                val measured = step == stepCount
                observations += RoadPassageObservation(coordinate, measured)
                val candidates = roadIndex.near(coordinate)
                    .mapNotNull { road ->
                        val projection = projectOntoRoad(coordinate, road.points)
                            ?: return@mapNotNull null
                        if (projection.distanceMeters > RoadPassageMatchDistanceMeters) {
                            return@mapNotNull null
                        }
                        RoadCandidate(
                            road = road,
                            projection = projection,
                        )
                    }
                candidates.forEach { match ->
                    val observedAt = interpolatedTime(
                        fromSample.recordedAtMillis,
                        toSample.recordedAtMillis,
                        fraction,
                    )
                    val side = when {
                        match.projection.fraction <= RoadPassageStartFraction -> -1
                        match.projection.fraction >= RoadPassageEndFraction -> 1
                        else -> 0
                    }
                    val previousState = states[match.road.key]?.takeUnless { state ->
                        observationIndex - state.lastSeenObservation >
                            RoadPassageMaximumMissedObservations ||
                            (
                                state.lastSeenAtMillis != null && observedAt != null &&
                                    observedAt - state.lastSeenAtMillis >
                                    RoadPassageMaximumInterruptionMillis
                                )
                    }
                    if (side != 0) {
                        val completedState = previousState?.takeIf { state ->
                            state.side != side && state.crossedInterior
                        }
                        if (completedState != null) {
                            passages += RoadPassageEvidence(
                                road = match.road,
                                section = section,
                                startedAtObservation = completedState.startedAtObservation,
                                endedAtObservation = observationIndex,
                                distanceMeters = completedState.distanceMeters +
                                    match.projection.distanceMeters.takeIf { measured }.orZero(),
                                matchedObservations = completedState.matchedObservations +
                                    measured.toInt(),
                            )
                        }
                        states[match.road.key] = if (
                            completedState == null &&
                            previousState?.side == side &&
                            !previousState.crossedInterior
                        ) {
                            previousState.copy(
                                distanceMeters = previousState.distanceMeters +
                                    match.projection.distanceMeters.takeIf { measured }.orZero(),
                                matchedObservations = previousState.matchedObservations +
                                    measured.toInt(),
                                lastSeenAtMillis = observedAt,
                                lastSeenObservation = observationIndex,
                            )
                        } else {
                            RoadPassageState(
                                side = side,
                                crossedInterior = false,
                                startedAtObservation = observationIndex,
                                distanceMeters = match.projection.distanceMeters
                                    .takeIf { measured }
                                    .orZero(),
                                matchedObservations = measured.toInt(),
                                lastSeenAtMillis = observedAt,
                                lastSeenObservation = observationIndex,
                            )
                        }
                    } else if (previousState != null) {
                        states[match.road.key] = previousState.copy(
                            crossedInterior = true,
                            distanceMeters = previousState.distanceMeters +
                                match.projection.distanceMeters.takeIf { measured }.orZero(),
                            matchedObservations = previousState.matchedObservations +
                                measured.toInt(),
                            lastSeenAtMillis = observedAt,
                            lastSeenObservation = observationIndex,
                        )
                    }
                }
            }
        }
        selectPassageWinners(passages, observations).forEach { (roadKey, count) ->
            counts[roadKey] = counts.getOrDefault(roadKey, 0) + count
        }
    }

    return counts.mapValues { (roadKey, count) ->
        CompletedRoad(road = roadsByKey.getValue(roadKey), count = count)
    }
}

private fun selectPassageWinners(
    passages: List<RoadPassageEvidence>,
    observations: List<RoadPassageObservation>,
): Map<String, Int> {
    val remaining = passages.toMutableSet()
    return buildMap {
        while (remaining.isNotEmpty()) {
            val cluster = mutableSetOf(remaining.first())
            var expanded: Boolean
            do {
                expanded = false
                remaining.filterNot(cluster::contains).forEach { candidate ->
                    if (
                        cluster.any { passage -> passagesCompete(passage, candidate) }
                    ) {
                        cluster += candidate
                        expanded = true
                    }
                }
            } while (expanded)
            remaining.removeAll(cluster)
            val scores = passageScores(cluster, observations)
            val ranked = cluster.sortedWith(
                compareBy<RoadPassageEvidence>(scores::getValue)
                    .thenBy { it.road.key },
            )
            val winner = ranked.first()
            if (
                ranked.getOrNull(1)?.let { runnerUp ->
                    scores.getValue(runnerUp) - scores.getValue(winner) <
                        RoadPassageWinnerAdvantageMeters
                } == true
            ) {
                continue
            }
            put(winner.road.key, getOrDefault(winner.road.key, 0) + 1)
        }
    }
}

private fun passageScores(
    passages: Set<RoadPassageEvidence>,
    observations: List<RoadPassageObservation>,
): Map<RoadPassageEvidence, Double> {
    if (passages.size == 1) return mapOf(passages.single() to passages.single().meanDistanceMeters)
    val passageStartIndex = passages.minOf(RoadPassageEvidence::startedAtObservation)
        .minus(1)
        .coerceAtLeast(0)
    val passageEndIndex = passages.maxOf(RoadPassageEvidence::endedAtObservation)
        .coerceAtMost(observations.size)
    val startIndex = (passageStartIndex downTo 0)
        .firstOrNull { observations[it].measured }
        ?: passageStartIndex
    val endIndex = (passageEndIndex until observations.size)
        .firstOrNull { observations[it].measured }
        ?.plus(1)
        ?: passageEndIndex
    val distances = passages.associateWith { mutableListOf<Double>() }
    // Synthetic samples reveal a crossing, but only recorded GPS fixes may decide
    // which of two parallel roads the person actually used.
    observations.subList(startIndex, endIndex)
        .filter(RoadPassageObservation::measured)
        .forEach { observation ->
            val projections = passages.associateWith { passage ->
                projectOntoRoad(observation.coordinate, passage.road.points)
            }
            if (
                projections.values.any { projection ->
                    projection == null ||
                        projection.distanceMeters > RoadPassageMatchDistanceMeters
                }
            ) {
                return@forEach
            }
            projections.forEach { (passage, projection) ->
                distances.getValue(passage) += checkNotNull(projection).distanceMeters
            }
        }
    return passages.associateWith { passage ->
        distances.getValue(passage).takeIf(List<Double>::isNotEmpty)?.average()
            ?: passage.meanDistanceMeters
    }
}

private fun Boolean.toInt(): Int = if (this) 1 else 0

private fun Double?.orZero(): Double = this ?: 0.0

private fun passagesCompete(
    first: RoadPassageEvidence,
    second: RoadPassageEvidence,
): Boolean {
    if (
        first.section != second.section ||
        first.road.key == second.road.key ||
        first.road.grade != second.road.grade
    ) {
        return false
    }
    val gap = maxOf(first.startedAtObservation, second.startedAtObservation) -
        minOf(first.endedAtObservation, second.endedAtObservation) - 1
    val corridorLengthMeters = maxOf(roadLength(first.road), roadLength(second.road))
    val maximumGapObservations = ceil(corridorLengthMeters / RoadPassageSampleSpacingMeters)
        .toInt()
        .coerceAtMost(RoadPassageMaximumCompetitionGapObservations)
    return gap <= maximumGapObservations &&
        roadsAreParallelAlternatives(first.road, second.road)
}

private fun roadLength(road: RenderedRoadSegment): Double =
    road.points.zipWithNext(::localCoordinateDistanceMeters).sum()

private fun roadsAreParallelAlternatives(
    first: RenderedRoadSegment,
    second: RenderedRoadSegment,
): Boolean {
    val firstLength = roadLength(first)
    val secondLength = roadLength(second)
    val shorter = if (firstLength <= secondLength) first else second
    val longer = if (firstLength <= secondLength) second else first
    val probes = listOf(
        shorter.points.first(),
        roadPrefix(shorter.points, 0.5).last(),
        shorter.points.last(),
    )
    val projections = probes.map { probe ->
        projectOntoRoad(probe, longer.points) ?: return false
    }
    val projectedSpan = projections.maxOf(RoadProjection::distanceAlongMeters) -
        projections.minOf(RoadProjection::distanceAlongMeters)
    return projections.all { it.distanceMeters <= RoadPassageMatchDistanceMeters } &&
        projectedSpan >= maxOf(
            firstLength.coerceAtMost(secondLength) * RoadPassageMinimumParallelOverlap,
            RoadPassageMinimumParallelOverlapMeters,
        ) &&
        coordinatePairAlignment(
            first.points.first() to first.points.last(),
            second.points.first() to second.points.last(),
        ) >= RoadPassageParallelAlignment
}

private fun coordinatePairAlignment(
    first: Pair<SpurCoordinate, SpurCoordinate>,
    second: Pair<SpurCoordinate, SpurCoordinate>,
): Double {
    fun direction(pair: Pair<SpurCoordinate, SpurCoordinate>): Pair<Double, Double> {
        val referenceLatitude = (pair.first.latitude + pair.second.latitude) / 2.0
        val longitudeScale = kotlin.math.cos(Math.toRadians(referenceLatitude))
        return (pair.second.longitude - pair.first.longitude) * longitudeScale to
            (pair.second.latitude - pair.first.latitude)
    }
    val firstDirection = direction(first)
    val secondDirection = direction(second)
    val denominator = kotlin.math.hypot(firstDirection.first, firstDirection.second) *
        kotlin.math.hypot(secondDirection.first, secondDirection.second)
    if (denominator == 0.0) return 0.0
    return kotlin.math.abs(
        (firstDirection.first * secondDirection.first +
            firstDirection.second * secondDirection.second) / denominator,
    )
}

private fun interpolatedTime(
    fromMillis: Long?,
    toMillis: Long?,
    fraction: Double,
): Long? {
    if (fromMillis == null || toMillis == null) return null
    return fromMillis + ((toMillis - fromMillis) * fraction).toLong()
}

private const val RoadPassageMatchDistanceMeters = 30.0
private const val RoadPassageParallelAlignment = 0.9
private const val RoadPassageMinimumParallelOverlap = 0.5
private const val RoadPassageMinimumParallelOverlapMeters = 30.0
private const val RoadPassageWinnerAdvantageMeters = 2.0
private const val RoadPassageMaximumCompetitionGapObservations = 30
private const val RoadPassageStartFraction = 0.35
private const val RoadPassageEndFraction = 0.65
private const val RoadPassageMaximumGapMeters = 300.0
private const val RoadPassageMaximumTimeGapMillis = 60_000L
private const val RoadPassageMaximumInterruptionMillis = 60_000L
private const val RoadPassageMaximumMissedObservations = 4
private const val RoadPassageSampleSpacingMeters = 8.0
