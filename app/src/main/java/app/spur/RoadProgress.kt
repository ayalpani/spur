package app.spur

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToLong

internal data class RenderedRoadSegment(
    val key: String,
    val points: List<SpurCoordinate>,
    val grade: String = "",
    val kind: RoadKind = RoadKind.STREET,
)

internal data class RoadPolyline(
    val points: List<SpurCoordinate>,
    val grade: String = "",
    val kind: RoadKind = RoadKind.STREET,
)

internal enum class RoadKind(val matchPenaltyMeters: Double) {
    STREET(0.0),
    SERVICE(4.0),
    PATH(12.0),
}

internal data class RoadProjection(
    val coordinate: SpurCoordinate,
    val distanceMeters: Double,
    val distanceAlongMeters: Double,
    val totalLengthMeters: Double,
    val segmentIndex: Int,
) {
    val fraction: Double
        get() = if (totalLengthMeters == 0.0) 0.0 else distanceAlongMeters / totalLengthMeters
}

internal data class RoadCandidate(
    val road: RenderedRoadSegment,
    val projection: RoadProjection,
    val previousProjection: RoadProjection? = null,
    val headingPenalty: Double = 0.0,
    val directionKnown: Boolean = true,
)

internal data class CompletedRoad(
    val road: RenderedRoadSegment,
    val count: Int,
)

internal data class RoadCompletion(
    val road: RenderedRoadSegment,
    val previousCount: Int,
    val count: Int,
    val generation: Long,
)

internal data class RoadProgressSnapshot(
    val road: RenderedRoadSegment? = null,
    val progress: Double = 0.0,
    val startsAtBeginning: Boolean = true,
    val completedRoads: Map<String, CompletedRoad> = emptyMap(),
    val completion: RoadCompletion? = null,
)

internal data class RoadTraversalCursor(
    val directionOrigin: SpurCoordinate? = null,
    val matchedRoadKey: String? = null,
)

internal data class RoadTraversalUpdate(
    val snapshot: RoadProgressSnapshot,
    val cursor: RoadTraversalCursor,
    val completions: List<RoadCompletion>,
)

private data class ActiveRoadTraversal(
    val road: RenderedRoadSegment,
    val startsAtBeginning: Boolean,
    val maximumProgress: Double = 0.0,
    val misses: Int = 0,
)

internal class RoadProgressTracker {
    private var active: ActiveRoadTraversal? = null
    private val completed = linkedMapOf<String, CompletedRoad>()
    private var completionGeneration = 0L

    fun resetTraversal(): RoadProgressSnapshot {
        active = null
        return snapshot()
    }

    fun replaceCompleted(completedRoads: Collection<CompletedRoad>): RoadProgressSnapshot {
        active = null
        completed.clear()
        completedRoads.forEach { completedRoad ->
            completed[completedRoad.road.key] = completedRoad
        }
        return snapshot()
    }

    fun currentSnapshot(): RoadProgressSnapshot = snapshot()

    fun update(candidates: List<RoadCandidate>): RoadProgressSnapshot {
        val current = active
        if (current != null) {
            val directedCandidate = chooseRoadCandidate(
                candidates.filter(RoadCandidate::directionKnown),
            )
            if (
                current.maximumProgress <= RoadCandidateSwitchProgressFraction &&
                directedCandidate != null &&
                directedCandidate.road.key != current.road.key
            ) {
                active = directedCandidate.toTraversalIfAtEndpoint()
                return snapshot()
            }
            val matching = candidates.firstOrNull { it.road.key == current.road.key }
            if (matching == null) {
                active = if (current.maximumProgress == 0.0) {
                    chooseRoadCandidate(candidates)?.toTraversalIfAtEndpoint()
                } else if (current.misses < RoadCandidateMissesBeforeReset - 1) {
                    current.copy(misses = current.misses + 1)
                } else {
                    chooseRoadCandidate(candidates)?.toTraversalIfAtEndpoint()
                }
                return snapshot()
            }

            val rawProgress = if (current.startsAtBeginning) {
                matching.projection.fraction
            } else {
                1.0 - matching.projection.fraction
            }.coerceIn(0.0, 1.0)
            val maximumProgress = maxOf(current.maximumProgress, rawProgress)
            active = current.copy(
                maximumProgress = maximumProgress,
                misses = 0,
            )
            val remainingDistance = matching.projection.totalLengthMeters * (1.0 - rawProgress)
            if (
                maximumProgress >= RoadCompletionFraction &&
                rawProgress >= RoadCompletionFraction &&
                remainingDistance <= RoadEndpointActivationMeters
            ) {
                val previousCount = completed[current.road.key]?.count ?: 0
                val finished = CompletedRoad(current.road, previousCount + 1)
                completed[current.road.key] = finished
                active = ActiveRoadTraversal(
                    road = current.road,
                    startsAtBeginning = !current.startsAtBeginning,
                )
                completionGeneration++
                return RoadProgressSnapshot(
                    road = current.road,
                    progress = 1.0,
                    startsAtBeginning = current.startsAtBeginning,
                    completedRoads = completed.toMap(),
                    completion = RoadCompletion(
                        road = current.road,
                        previousCount = previousCount,
                        count = finished.count,
                        generation = completionGeneration,
                    ),
                )
            }
            return snapshot()
        }

        val candidate = chooseRoadCandidate(
            candidates.filter(RoadCandidate::directionKnown),
        ) ?: return snapshot()
        active = candidate.toTraversalIfAtEndpoint()
        return snapshot()
    }

    private fun snapshot() = RoadProgressSnapshot(
        road = active?.road,
        progress = active?.maximumProgress ?: 0.0,
        startsAtBeginning = active?.startsAtBeginning ?: true,
        completedRoads = completed.toMap(),
    )

    private fun RoadCandidate.toTraversalIfAtEndpoint(): ActiveRoadTraversal? {
        val origin = previousProjection?.takeIf {
            minOf(
                it.distanceAlongMeters,
                it.totalLengthMeters - it.distanceAlongMeters,
            ) <= RoadEndpointActivationMeters
        } ?: projection
        val distanceAlong = origin.distanceAlongMeters
        val distanceToEnd = origin.totalLengthMeters - distanceAlong
        if (minOf(distanceAlong, distanceToEnd) > RoadEndpointActivationMeters) return null
        val startsAtBeginning = distanceAlong <= distanceToEnd
        val progress = if (startsAtBeginning) {
            projection.fraction
        } else {
            1.0 - projection.fraction
        }.coerceIn(0.0, 1.0)
        return ActiveRoadTraversal(
            road = road,
            startsAtBeginning = startsAtBeginning,
            maximumProgress = progress,
        )
    }
}

internal class RoadTraversalAnalyzer(
    roads: List<RenderedRoadSegment>,
) {
    private val roadIndex = RoadSpatialIndex(roads)
    private val roadsByKey = roads.associateBy(RenderedRoadSegment::key)

    fun updateRoute(
        route: List<SpurCoordinate>,
        tracker: RoadProgressTracker,
        initialCursor: RoadTraversalCursor = RoadTraversalCursor(),
        resetTraversal: Boolean = false,
        shouldContinue: () -> Boolean = { true },
    ): RoadTraversalUpdate = updateSamples(
        route = route.map(::RoadTrackSample),
        tracker = tracker,
        initialCursor = initialCursor,
        resetTraversal = resetTraversal,
        shouldContinue = shouldContinue,
    )

    fun updateSamples(
        route: List<RoadTrackSample>,
        tracker: RoadProgressTracker,
        initialCursor: RoadTraversalCursor = RoadTraversalCursor(),
        resetTraversal: Boolean = false,
        shouldContinue: () -> Boolean = { true },
    ): RoadTraversalUpdate {
        if (resetTraversal) tracker.resetTraversal()
        if (route.size < 2) {
            return RoadTraversalUpdate(
                snapshot = tracker.currentSnapshot(),
                cursor = initialCursor.copy(
                    directionOrigin = initialCursor.directionOrigin ?: route.firstOrNull()?.coordinate,
                ),
                completions = emptyList(),
            )
        }

        var directionOrigin = initialCursor.directionOrigin ?: route.first().coordinate
        var candidateDirectionOrigin = directionOrigin
        var previousRoad = initialCursor.matchedRoadKey?.let(roadsByKey::get)
        val completions = mutableListOf<RoadCompletion>()
        val observations = mutableListOf<RoadMatchObservation>()

        fun applyObservations() {
            if (observations.isEmpty()) return
            val matches = mostLikelyRoadCandidates(
                observations = observations,
                initialRoad = previousRoad,
                shouldContinue = shouldContinue,
            )
            observations.zip(matches).forEach { (observation, match) ->
                val snapshot = tracker.update(match?.let(::listOf).orEmpty())
                snapshot.completion?.let(completions::add)
                if (match?.directionKnown == true) {
                    previousRoad = match.road
                    directionOrigin = observation.coordinate
                } else if (match == null) {
                    previousRoad = null
                }
            }
            observations.clear()
        }

        route.zipWithNext().forEach { (fromSample, toSample) ->
            if (!shouldContinue()) {
                return RoadTraversalUpdate(
                    snapshot = tracker.currentSnapshot(),
                    cursor = RoadTraversalCursor(directionOrigin, previousRoad?.key),
                    completions = completions,
                )
            }
            val from = fromSample.coordinate
            val to = toSample.coordinate
            val distance = localCoordinateDistanceMeters(from, to)
            val elapsedMillis = fromSample.recordedAtMillis?.let { fromTime ->
                toSample.recordedAtMillis?.minus(fromTime)
            }
            if (
                distance > RoadHistoryMaximumGapMeters ||
                elapsedMillis?.let { it <= 0L || it > RoadHistoryMaximumTimeGapMillis } == true ||
                !roadIndex.intersectsLoadedBounds(from, to)
            ) {
                applyObservations()
                tracker.resetTraversal()
                directionOrigin = to
                candidateDirectionOrigin = to
                previousRoad = null
                return@forEach
            }
            if (distance == 0.0) return@forEach

            val stepCount = ceil(distance / RoadHistorySampleSpacingMeters).toInt()
                .coerceAtLeast(1)
            for (step in 1..stepCount) {
                if (!shouldContinue()) break
                val coordinate = interpolate(from, to, step.toDouble() / stepCount)
                val previousCoordinate = candidateDirectionOrigin.takeIf {
                    localCoordinateDistanceMeters(it, coordinate) >=
                        RoadHeadingMinimumMovementMeters
                }
                val candidates = roadIndex.near(coordinate)
                    .mapNotNull { road ->
                        val projection = projectOntoRoad(coordinate, road.points)
                            ?: return@mapNotNull null
                        if (projection.distanceMeters > RoadMaximumMatchDistanceMeters) {
                            return@mapNotNull null
                        }
                        RoadCandidate(
                            road = road,
                            projection = projection,
                            previousProjection = previousCoordinate?.let {
                                projectOntoRoad(it, road.points)
                            }?.takeIf {
                                it.distanceMeters <= RoadMaximumMatchDistanceMeters
                            },
                            headingPenalty = roadHeadingPenalty(
                                previous = previousCoordinate,
                                current = coordinate,
                                road = road,
                                projection = projection,
                            ),
                            directionKnown = previousCoordinate != null,
                        )
                    }
                    .sortedBy {
                        roadMatchScore(it.road, it.projection, it.headingPenalty)
                    }
                observations += RoadMatchObservation(
                    coordinate = coordinate,
                    movementMeters = previousCoordinate?.let {
                        localCoordinateDistanceMeters(it, coordinate)
                    } ?: 0.0,
                    accuracyMeters = maxOf(
                        fromSample.accuracyMeters,
                        toSample.accuracyMeters,
                    ),
                    candidates = candidates,
                )
                if (previousCoordinate != null) {
                    candidateDirectionOrigin = coordinate
                }
            }
        }
        applyObservations()
        return RoadTraversalUpdate(
            snapshot = tracker.currentSnapshot(),
            cursor = RoadTraversalCursor(directionOrigin, previousRoad?.key),
            completions = completions,
        )
    }
}

internal fun historicalRoadTraversals(
    routes: List<List<SpurCoordinate>>,
    roads: List<RenderedRoadSegment>,
    shouldContinue: () -> Boolean = { true },
): Map<String, CompletedRoad> {
    return historicalRoadSampleTraversals(
        routes = routes.map { route -> route.map(::RoadTrackSample) },
        roads = roads,
        shouldContinue = shouldContinue,
    )
}

internal fun historicalRoadSampleTraversals(
    routes: List<List<RoadTrackSample>>,
    roads: List<RenderedRoadSegment>,
    shouldContinue: () -> Boolean = { true },
): Map<String, CompletedRoad> {
    if (roads.isEmpty()) return emptyMap()
    val tracker = RoadProgressTracker()
    val analyzer = RoadTraversalAnalyzer(roads)
    routes.forEach { route ->
        if (!shouldContinue()) return emptyMap()
        analyzer.updateSamples(
            route = route,
            tracker = tracker,
            resetTraversal = true,
            shouldContinue = shouldContinue,
        )
    }
    return tracker.currentSnapshot().completedRoads
}

internal fun intersectionRoadEdges(polylines: List<RoadPolyline>): List<RenderedRoadSegment> =
    polylines
        .groupBy(RoadPolyline::grade)
        .flatMap { (grade, gradedPolylines) ->
            val coordinates = linkedMapOf<String, SpurCoordinate>()
            val arcs = linkedMapOf<String, RoadGraphArc<RoadKind>>()
            gradedPolylines.forEach { polyline ->
                polyline.points.zipWithNext().forEach { (from, to) ->
                    val fromKey = roadNodeKey(from)
                    val toKey = roadNodeKey(to)
                    if (fromKey == toKey) return@forEach
                    coordinates.putIfAbsent(fromKey, from)
                    coordinates.putIfAbsent(toKey, to)
                    val key = "$grade:${polyline.kind}|" +
                        "${minOf(fromKey, toKey)}|${maxOf(fromKey, toKey)}"
                    arcs.putIfAbsent(
                        key,
                        RoadGraphArc(key, fromKey, toKey, polyline.kind),
                    )
                }
            }
            linearRoadGraphPaths(arcs.values) { first, next -> first == next }
                .mapNotNull { path ->
                    val points = path.nodes.map(coordinates::getValue)
                    val kind = path.arcs.first().tag
                    if (roadLengthMeters(points) < RoadMinimumLengthMeters) {
                        return@mapNotNull null
                    }
                    val orientedPoints = if (
                        roadNodeKey(points.first()) <= roadNodeKey(points.last())
                    ) {
                        points
                    } else {
                        points.asReversed()
                    }
                    RenderedRoadSegment(
                        key = canonicalRoadKey(
                            orientedPoints,
                            "$grade:$kind",
                        ),
                        points = orientedPoints,
                        grade = grade,
                        kind = kind,
                    )
                }
        }
        .distinctBy(RenderedRoadSegment::key)

internal fun roadNodeKey(point: SpurCoordinate): String =
    "${(point.latitude * RoadKeyPrecision).roundToLong()}," +
        "${(point.longitude * RoadKeyPrecision).roundToLong()}"

internal fun chooseRoadCandidate(candidates: List<RoadCandidate>): RoadCandidate? =
    candidates.minByOrNull {
        roadMatchScore(it.road, it.projection, it.headingPenalty)
    }

private fun roadMatchScore(
    road: RenderedRoadSegment,
    projection: RoadProjection,
    headingPenalty: Double,
): Double = projection.distanceMeters + headingPenalty + road.kind.matchPenaltyMeters

internal fun projectOntoRoad(
    coordinate: SpurCoordinate,
    points: List<SpurCoordinate>,
): RoadProjection? {
    if (points.size < 2) return null
    val referenceLatitude = coordinate.latitude
    val projectedPoints = points.map { it.toLocalMeters(referenceLatitude) }
    val target = coordinate.toLocalMeters(referenceLatitude)
    val segmentLengths = projectedPoints.zipWithNext { from, to ->
        hypot(to.first - from.first, to.second - from.second)
    }
    val totalLength = segmentLengths.sum()
    if (totalLength == 0.0) return null

    var bestDistance = Double.MAX_VALUE
    var bestAlong = 0.0
    var bestCoordinate = points.first()
    var bestSegmentIndex = 0
    var distanceBeforeSegment = 0.0
    segmentLengths.forEachIndexed { index, length ->
        if (length == 0.0) return@forEachIndexed
        val from = projectedPoints[index]
        val to = projectedPoints[index + 1]
        val dx = to.first - from.first
        val dy = to.second - from.second
        val t = (
            ((target.first - from.first) * dx + (target.second - from.second) * dy) /
                (length * length)
            ).coerceIn(0.0, 1.0)
        val projectedX = from.first + dx * t
        val projectedY = from.second + dy * t
        val distance = hypot(target.first - projectedX, target.second - projectedY)
        if (distance < bestDistance) {
            bestDistance = distance
            bestAlong = distanceBeforeSegment + length * t
            bestCoordinate = interpolate(points[index], points[index + 1], t)
            bestSegmentIndex = index
        }
        distanceBeforeSegment += length
    }
    return RoadProjection(
        coordinate = bestCoordinate,
        distanceMeters = bestDistance,
        distanceAlongMeters = bestAlong,
        totalLengthMeters = totalLength,
        segmentIndex = bestSegmentIndex,
    )
}

internal fun roadHeadingPenalty(
    previous: SpurCoordinate?,
    current: SpurCoordinate,
    road: RenderedRoadSegment,
    projection: RoadProjection,
): Double {
    previous ?: return 0.0
    val movementDistance = localCoordinateDistanceMeters(previous, current)
    if (movementDistance < RoadHeadingMinimumMovementMeters) return 0.0
    val from = road.points[projection.segmentIndex]
    val to = road.points[projection.segmentIndex + 1]
    val referenceLatitude = current.latitude
    val movementStart = previous.toLocalMeters(referenceLatitude)
    val movementEnd = current.toLocalMeters(referenceLatitude)
    val roadStart = from.toLocalMeters(referenceLatitude)
    val roadEnd = to.toLocalMeters(referenceLatitude)
    val movementX = movementEnd.first - movementStart.first
    val movementY = movementEnd.second - movementStart.second
    val roadX = roadEnd.first - roadStart.first
    val roadY = roadEnd.second - roadStart.second
    val denominator = hypot(movementX, movementY) * hypot(roadX, roadY)
    if (denominator == 0.0) return 0.0
    val alignment = abs((movementX * roadX + movementY * roadY) / denominator)
    return (1.0 - alignment.coerceIn(0.0, 1.0)) * RoadHeadingMaximumPenaltyMeters
}

internal fun roadPrefix(
    points: List<SpurCoordinate>,
    fraction: Double,
    startsAtBeginning: Boolean = true,
): List<SpurCoordinate> {
    if (points.size < 2) return emptyList()
    val oriented = if (startsAtBeginning) points else points.asReversed()
    val clampedFraction = fraction.coerceIn(0.0, 1.0)
    if (clampedFraction == 0.0) return listOf(oriented.first())
    if (clampedFraction == 1.0) return oriented
    val lengths = oriented.zipWithNext(::localCoordinateDistanceMeters)
    val target = lengths.sum() * clampedFraction
    var traveled = 0.0
    val prefix = mutableListOf(oriented.first())
    lengths.forEachIndexed { index, length ->
        if (traveled + length < target) {
            prefix += oriented[index + 1]
            traveled += length
        } else {
            val remaining = target - traveled
            prefix += interpolate(
                oriented[index],
                oriented[index + 1],
                if (length == 0.0) 0.0 else remaining / length,
            )
            return prefix
        }
    }
    return oriented
}

internal fun roadPointAtFraction(
    points: List<SpurCoordinate>,
    fraction: Double,
): SpurCoordinate? = roadPrefix(points, fraction).lastOrNull()

internal fun canonicalRoadKey(
    points: List<SpurCoordinate>,
    discriminator: String = "",
): String {
    val forward = points.joinToString(";", transform = ::roadNodeKey)
    val reverse = points.asReversed().joinToString(";", transform = ::roadNodeKey)
    return "$discriminator|${minOf(forward, reverse)}"
}

internal fun roadLengthMeters(points: List<SpurCoordinate>): Double =
    points.zipWithNext(::localCoordinateDistanceMeters).sum()

private fun interpolate(
    from: SpurCoordinate,
    to: SpurCoordinate,
    fraction: Double,
) = SpurCoordinate(
    latitude = from.latitude + (to.latitude - from.latitude) * fraction,
    longitude = from.longitude + (to.longitude - from.longitude) * fraction,
)

internal fun localCoordinateDistanceMeters(
    from: SpurCoordinate,
    to: SpurCoordinate,
): Double {
    val referenceLatitude = (from.latitude + to.latitude) / 2.0
    val fromMeters = from.toLocalMeters(referenceLatitude)
    val toMeters = to.toLocalMeters(referenceLatitude)
    return hypot(toMeters.first - fromMeters.first, toMeters.second - fromMeters.second)
}

internal fun chooseContinuousRoadCandidate(
    candidates: List<RoadCandidate>,
    previousRoad: RenderedRoadSegment?,
): RoadCandidate? {
    fun transitionScore(candidate: RoadCandidate): Double =
        roadMatchScore(candidate.road, candidate.projection, candidate.headingPenalty) + when {
            previousRoad == null || candidate.road.key == previousRoad.key -> 0.0
            connectedEndpointFractions(previousRoad, candidate.road) != null -> 0.0
            else -> RoadUnconnectedTransitionPenaltyMeters
        }

    val best = candidates.minByOrNull(::transitionScore)
        ?: return null
    val continuing = previousRoad?.let { previous ->
        candidates.firstOrNull { it.road.key == previous.key }
    } ?: return best
    return if (
        transitionScore(continuing) <= transitionScore(best) + RoadMatchSwitchAdvantageMeters
    ) {
        continuing
    } else {
        best
    }
}

private data class RoadFractionRange(
    val start: Double,
    val end: Double,
)

private data class RoadBridge(
    val road: RenderedRoadSegment,
    val firstRoadFraction: Double,
    val bridgeStartFraction: Double,
    val bridgeEndFraction: Double,
    val secondRoadFraction: Double,
    val lengthMeters: Double,
)

internal fun normalizedRoadSegments(
    routes: List<List<SpurCoordinate>>,
    roads: List<RenderedRoadSegment>,
    shouldContinue: () -> Boolean = { true },
): List<List<SpurCoordinate>> {
    if (roads.isEmpty()) return emptyList()
    val roadIndex = RoadSpatialIndex(roads)
    val ranges = linkedMapOf<String, MutableList<RoadFractionRange>>()
    val bridgeCache = mutableMapOf<Pair<String, String>, RoadBridge?>()

    fun addRange(road: RenderedRoadSegment, firstFraction: Double, secondFraction: Double) {
        val start = minOf(firstFraction, secondFraction)
        val end = maxOf(firstFraction, secondFraction)
        if ((end - start) * roadLengthMeters(road.points) < RoadHistoryMinimumMeters) return
        ranges.getOrPut(road.key, ::mutableListOf) += RoadFractionRange(start, end)
    }

    fun addRange(match: RoadCandidate, otherFraction: Double) {
        addRange(match.road, match.projection.fraction, otherFraction)
    }

    fun shortBridge(first: RenderedRoadSegment, second: RenderedRoadSegment): RoadBridge? {
        val key = first.key to second.key
        if (key in bridgeCache) return bridgeCache[key]
        val bridge = roads.asSequence()
            .filter { it.key != first.key && it.key != second.key }
            .mapNotNull { road ->
                val firstConnection = connectedEndpointFractions(first, road)
                    ?: return@mapNotNull null
                val secondConnection = connectedEndpointFractions(road, second)
                    ?: return@mapNotNull null
                val length = abs(firstConnection.second - secondConnection.first) *
                    roadLengthMeters(road.points)
                if (length > RoadHistoryMaximumBridgeMeters) return@mapNotNull null
                RoadBridge(
                    road = road,
                    firstRoadFraction = firstConnection.first,
                    bridgeStartFraction = firstConnection.second,
                    bridgeEndFraction = secondConnection.first,
                    secondRoadFraction = secondConnection.second,
                    lengthMeters = length,
                )
            }
            .minByOrNull(RoadBridge::lengthMeters)
        bridgeCache[key] = bridge
        return bridge
    }

    fun connect(previous: RoadCandidate, current: RoadCandidate) {
        if (previous.road.key == current.road.key) {
            addRange(previous, current.projection.fraction)
            return
        }
        connectedEndpointFractions(previous.road, current.road)?.let { (from, to) ->
            addRange(previous, from)
            addRange(current, to)
            return
        }
        shortBridge(previous.road, current.road)?.let { bridge ->
            addRange(previous, bridge.firstRoadFraction)
            addRange(bridge.road, bridge.bridgeStartFraction, bridge.bridgeEndFraction)
            addRange(current, bridge.secondRoadFraction)
        }
    }

    routes.forEach { route ->
        var previousMatch: RoadCandidate? = null
        route.zipWithNext().forEach { (from, to) ->
            if (!shouldContinue()) return emptyList()
            val distance = localCoordinateDistanceMeters(from, to)
            if (
                distance < RoadHistoryMinimumMeters ||
                distance > RoadHistoryMaximumGapMeters ||
                !roadIndex.intersectsLoadedBounds(from, to)
            ) {
                previousMatch = null
                return@forEach
            }
            val stepCount = ceil(distance / RoadHistorySampleSpacingMeters).toInt()
            for (step in 0..stepCount) {
                if (!shouldContinue()) return emptyList()
                val coordinate = interpolate(from, to, step.toDouble() / stepCount)
                val candidates = roadIndex.near(coordinate)
                    .mapNotNull { road ->
                        projectOntoRoad(coordinate, road.points)?.let { projection ->
                            if (projection.distanceMeters > RoadHistoryMatchDistanceMeters) {
                                null
                            } else {
                                RoadCandidate(
                                    road = road,
                                    projection = projection,
                                    headingPenalty = roadHeadingPenalty(
                                        previous = from,
                                        current = to,
                                        road = road,
                                        projection = projection,
                                    ),
                                )
                            }
                        }
                    }
                val match = chooseContinuousRoadCandidate(
                    candidates = candidates,
                    previousRoad = previousMatch?.road,
                )
                if (match == null) {
                    previousMatch = null
                } else {
                    previousMatch?.let { connect(it, match) }
                    previousMatch = match
                }
            }
        }
    }

    val roadsByKey = roads.associateBy(RenderedRoadSegment::key)
    return ranges.flatMap { (roadKey, roadRanges) ->
        val road = roadsByKey.getValue(roadKey)
        mergeRoadRanges(roadRanges).mapNotNull { range ->
            roadSlice(road.points, range.start, range.end).takeIf { it.size >= 2 }
        }
    }
}

private class RoadSpatialIndex(
    roads: List<RenderedRoadSegment>,
) {
    private val cells = mutableMapOf<Pair<Int, Int>, MutableList<RenderedRoadSegment>>()
    private val minimumLatitude = roads.minOf { road ->
        road.points.minOf(SpurCoordinate::latitude)
    }
    private val maximumLatitude = roads.maxOf { road ->
        road.points.maxOf(SpurCoordinate::latitude)
    }
    private val minimumLongitude = roads.minOf { road ->
        road.points.minOf(SpurCoordinate::longitude)
    }
    private val maximumLongitude = roads.maxOf { road ->
        road.points.maxOf(SpurCoordinate::longitude)
    }

    init {
        roads.forEach { road ->
            val latitudeCells = cell(road.points.minOf(SpurCoordinate::latitude))..
                cell(road.points.maxOf(SpurCoordinate::latitude))
            val longitudeCells = cell(road.points.minOf(SpurCoordinate::longitude))..
                cell(road.points.maxOf(SpurCoordinate::longitude))
            latitudeCells.forEach { latitudeCell ->
                longitudeCells.forEach { longitudeCell ->
                    cells.getOrPut(latitudeCell to longitudeCell, ::mutableListOf) += road
                }
            }
        }
    }

    fun near(coordinate: SpurCoordinate): List<RenderedRoadSegment> {
        val latitudeCell = cell(coordinate.latitude)
        val longitudeCell = cell(coordinate.longitude)
        return buildMap<String, RenderedRoadSegment> {
            for (latitudeOffset in -RoadHistoryCellSearchRadius..RoadHistoryCellSearchRadius) {
                for (longitudeOffset in -RoadHistoryCellSearchRadius..RoadHistoryCellSearchRadius) {
                    cells[latitudeCell + latitudeOffset to longitudeCell + longitudeOffset]
                        ?.forEach { road -> put(road.key, road) }
                }
            }
        }.values.toList()
    }

    fun intersectsLoadedBounds(from: SpurCoordinate, to: SpurCoordinate): Boolean =
            maxOf(from.latitude, to.latitude) >= minimumLatitude - RoadHistoryBoundsPaddingDegrees &&
            minOf(from.latitude, to.latitude) <= maximumLatitude + RoadHistoryBoundsPaddingDegrees &&
            maxOf(from.longitude, to.longitude) >=
            minimumLongitude - RoadHistoryBoundsPaddingDegrees &&
            minOf(from.longitude, to.longitude) <=
            maximumLongitude + RoadHistoryBoundsPaddingDegrees

    private fun cell(value: Double): Int = floor(value / RoadHistoryCellDegrees).toInt()
}

internal fun connectedEndpointFractions(
    first: RenderedRoadSegment,
    second: RenderedRoadSegment,
): Pair<Double, Double>? {
    if (first.grade != second.grade) return null
    val firstEndpoints = arrayOf(first.points.first(), first.points.last())
    val secondEndpoints = arrayOf(second.points.first(), second.points.last())
    var closestDistance = Double.MAX_VALUE
    var closestFractions: Pair<Double, Double>? = null
    firstEndpoints.forEachIndexed { firstIndex, firstPoint ->
        secondEndpoints.forEachIndexed { secondIndex, secondPoint ->
            val distance = localCoordinateDistanceMeters(firstPoint, secondPoint)
            if (distance < closestDistance) {
                closestDistance = distance
                closestFractions = firstIndex.toDouble() to secondIndex.toDouble()
            }
        }
    }
    return closestFractions?.takeIf {
        closestDistance <= RoadEndpointConnectionToleranceMeters
    }
}

private fun mergeRoadRanges(ranges: List<RoadFractionRange>): List<RoadFractionRange> =
    ranges.sortedBy(RoadFractionRange::start).fold(mutableListOf()) { merged, next ->
        val previous = merged.lastOrNull()
        if (previous == null || next.start > previous.end + RoadHistoryFractionTolerance) {
            merged += next
        } else {
            merged[merged.lastIndex] = RoadFractionRange(
                start = previous.start,
                end = maxOf(previous.end, next.end),
            )
        }
        merged
    }

private fun roadSlice(
    points: List<SpurCoordinate>,
    startFraction: Double,
    endFraction: Double,
): List<SpurCoordinate> {
    if (points.size < 2) return emptyList()
    val start = startFraction.coerceIn(0.0, 1.0)
    val end = endFraction.coerceIn(start, 1.0)
    val lengths = points.zipWithNext(::localCoordinateDistanceMeters)
    val totalLength = lengths.sum()
    if (totalLength == 0.0) return emptyList()
    val startDistance = totalLength * start
    val endDistance = totalLength * end
    val result = mutableListOf(roadPointAtFraction(points, start) ?: return emptyList())
    var traveled = 0.0
    lengths.forEachIndexed { index, length ->
        traveled += length
        if (index + 1 < points.lastIndex && traveled > startDistance && traveled < endDistance) {
            result += points[index + 1]
        }
    }
    val endPoint = roadPointAtFraction(points, end) ?: return emptyList()
    if (localCoordinateDistanceMeters(result.last(), endPoint) > 0.01) result += endPoint
    return result
}

private fun SpurCoordinate.toLocalMeters(referenceLatitude: Double): Pair<Double, Double> {
    val latitudeRadians = referenceLatitude * PI / 180.0
    return Pair(
        longitude * MetersPerDegree * cos(latitudeRadians),
        latitude * MetersPerDegree,
    )
}

private const val MetersPerDegree = 111_320.0
private const val RoadKeyPrecision = 1_000_000.0
private const val RoadMinimumLengthMeters = 4.0
private const val RoadEndpointActivationMeters = 18.0
private const val RoadCompletionFraction = 0.95
private const val RoadCandidateSwitchProgressFraction = 0.1
private const val RoadCandidateMissesBeforeReset = 2
internal const val RoadHeadingMinimumMovementMeters = 5.0
private const val RoadHeadingMaximumPenaltyMeters = 14.0
private const val RoadMatchSwitchAdvantageMeters = 0.0
private const val RoadUnconnectedTransitionPenaltyMeters = 30.0
private const val RoadEndpointConnectionToleranceMeters = 3.0
private const val RoadHistoryMatchDistanceMeters = 30.0
private const val RoadHistoryMinimumMeters = 1.0
private const val RoadHistoryMaximumGapMeters = 300.0
private const val RoadHistoryMaximumTimeGapMillis = 60_000L
private const val RoadHistoryMaximumBridgeMeters = 80.0
private const val RoadHistorySampleSpacingMeters = 8.0
private const val RoadHistoryCellDegrees = 0.001
private const val RoadHistoryCellSearchRadius = 1
private const val RoadHistoryBoundsPaddingDegrees = 0.001
private const val RoadHistoryFractionTolerance = 0.000_001
