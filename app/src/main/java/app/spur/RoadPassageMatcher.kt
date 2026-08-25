package app.spur

import kotlin.math.ceil

internal data class RoadTrackSample(
    val coordinate: SpurCoordinate,
    val recordedAtMillis: Long? = null,
)

private data class RoadPassageState(
    val side: Int,
    val crossedInterior: Boolean,
    val lastSeenAtMillis: Long?,
    val lastSeenObservation: Int,
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
        val routeCounts = mutableMapOf<String, Int>()
        val matchedDistanceTotals = mutableMapOf<String, Double>()
        val matchedObservationCounts = mutableMapOf<String, Int>()
        var observationIndex = 0
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
                    matchedDistanceTotals[match.road.key] =
                        matchedDistanceTotals.getOrDefault(match.road.key, 0.0) +
                        match.projection.distanceMeters
                    matchedObservationCounts[match.road.key] =
                        matchedObservationCounts.getOrDefault(match.road.key, 0) + 1
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
                        if (
                            previousState != null &&
                            previousState.side != side &&
                            previousState.crossedInterior
                        ) {
                            routeCounts[match.road.key] =
                                routeCounts.getOrDefault(match.road.key, 0) + 1
                        }
                        states[match.road.key] = RoadPassageState(
                            side = side,
                            crossedInterior = false,
                            lastSeenAtMillis = observedAt,
                            lastSeenObservation = observationIndex,
                        )
                    } else if (previousState != null) {
                        states[match.road.key] = previousState.copy(
                            crossedInterior = true,
                            lastSeenAtMillis = observedAt,
                            lastSeenObservation = observationIndex,
                        )
                    }
                }
            }
        }
        collapseParallelPassages(
            counts = routeCounts,
            roadsByKey = roadsByKey,
            matchedDistanceTotals = matchedDistanceTotals,
            matchedObservationCounts = matchedObservationCounts,
        ).forEach { (roadKey, count) ->
            counts[roadKey] = counts.getOrDefault(roadKey, 0) + count
        }
    }

    return counts.mapValues { (roadKey, count) ->
        CompletedRoad(road = roadsByKey.getValue(roadKey), count = count)
    }
}

private fun collapseParallelPassages(
    counts: Map<String, Int>,
    roadsByKey: Map<String, RenderedRoadSegment>,
    matchedDistanceTotals: Map<String, Double>,
    matchedObservationCounts: Map<String, Int>,
): Map<String, Int> {
    val remaining = counts.keys.toMutableSet()
    return buildMap {
        while (remaining.isNotEmpty()) {
            val cluster = mutableSetOf(remaining.first())
            var expanded: Boolean
            do {
                expanded = false
                remaining.filterNot(cluster::contains).forEach { candidateKey ->
                    if (
                        cluster.any { clusterKey ->
                            roadsAreParallelAlternatives(
                                roadsByKey.getValue(clusterKey),
                                roadsByKey.getValue(candidateKey),
                            )
                        }
                    ) {
                        cluster += candidateKey
                        expanded = true
                    }
                }
            } while (expanded)
            remaining.removeAll(cluster)
            val representative = cluster.minWithOrNull(
                compareBy<String> { roadKey ->
                    val observations = matchedObservationCounts[roadKey].orZero()
                    if (observations == 0) Double.MAX_VALUE else
                        matchedDistanceTotals.getOrDefault(roadKey, 0.0) / observations
                }.thenBy { it },
            ) ?: continue
            put(representative, cluster.maxOf(counts::getValue))
        }
    }
}

private fun roadsAreParallelAlternatives(
    first: RenderedRoadSegment,
    second: RenderedRoadSegment,
): Boolean {
    if (first.grade != second.grade) return false
    val firstLength = first.points.zipWithNext(::localCoordinateDistanceMeters).sum()
    val secondLength = second.points.zipWithNext(::localCoordinateDistanceMeters).sum()
    val shorter = if (firstLength <= secondLength) first else second
    val longer = if (shorter === first) second else first
    val shorterMidpoint = roadPrefix(shorter.points, 0.5).last()
    val probes = listOf(shorter.points.first(), shorterMidpoint, shorter.points.last())
    if (
        probes.any { probe ->
            projectOntoRoad(probe, longer.points)?.distanceMeters
                ?.let { it > RoadPassageParallelRoadDistanceMeters } != false
        }
    ) {
        return false
    }
    val shorterDirection = shorter.points.first() to shorter.points.last()
    val longerDirection = longer.points.first() to longer.points.last()
    return coordinatePairAlignment(shorterDirection, longerDirection) >=
        RoadPassageParallelAlignment
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

private fun Int?.orZero(): Int = this ?: 0

private fun interpolatedTime(
    fromMillis: Long?,
    toMillis: Long?,
    fraction: Double,
): Long? {
    if (fromMillis == null || toMillis == null) return null
    return fromMillis + ((toMillis - fromMillis) * fraction).toLong()
}

private const val RoadPassageMatchDistanceMeters = 30.0
private const val RoadPassageParallelRoadDistanceMeters = 5.0
private const val RoadPassageParallelAlignment = 0.9
private const val RoadPassageStartFraction = 0.35
private const val RoadPassageEndFraction = 0.65
private const val RoadPassageMaximumGapMeters = 300.0
private const val RoadPassageMaximumTimeGapMillis = 60_000L
private const val RoadPassageMaximumInterruptionMillis = 60_000L
private const val RoadPassageMaximumMissedObservations = 4
private const val RoadPassageSampleSpacingMeters = 8.0
