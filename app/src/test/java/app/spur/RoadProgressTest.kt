package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadProgressTest {
    private val road = RenderedRoadSegment(
        key = "test-road",
        points = listOf(
            SpurCoordinate(52.0, 13.0),
            SpurCoordinate(52.0, 13.001),
        ),
    )

    @Test
    fun showsRoadHistoryOnlyAtStreetZoom() {
        assertEquals(13.0, RoadHistoryMinimumZoom, 0.0)
        assertFalse(shouldShowRoadHistory(RoadHistoryMinimumZoom - 0.01))
        assertTrue(shouldShowRoadHistory(RoadHistoryMinimumZoom))
    }

    @Test
    fun overviewBatchesRoadsIntoOneMapLibreFeature() {
        val features = roadProgressOverviewFeatureCollection(
            listOf(road.points, road.points.asReversed()),
        )

        assertEquals(1, features.features()?.size)
        assertTrue(features.features()?.single()?.geometry() is org.maplibre.geojson.MultiLineString)
    }

    @Test
    fun projectsPositionOntoRoadAndReportsProgress() {
        val projection = projectOntoRoad(
            coordinate = SpurCoordinate(52.0001, 13.00025),
            points = road.points,
        )

        assertNotNull(projection)
        assertEquals(0.25, projection!!.fraction, 0.01)
        assertTrue(projection.distanceMeters in 10.0..12.0)
    }

    @Test
    fun slicesBentRoadByTraveledDistance() {
        val bent = listOf(
            SpurCoordinate(0.0, 0.0),
            SpurCoordinate(0.0, 0.001),
            SpurCoordinate(0.001, 0.001),
        )

        val prefix = roadPrefix(bent, fraction = 0.75)

        assertEquals(3, prefix.size)
        assertEquals(0.0005, prefix.last().latitude, 0.00002)
        assertEquals(0.001, prefix.last().longitude, 0.00002)
    }

    @Test
    fun normalizesRouteAcrossConnectedRoadEdges() {
        val firstRoad = RenderedRoadSegment(
            key = "ground|first-road",
            points = listOf(
                SpurCoordinate(52.0, 13.0),
                SpurCoordinate(52.0, 13.001),
            ),
        )
        val secondRoad = RenderedRoadSegment(
            key = "ground|second-road",
            points = listOf(
                SpurCoordinate(52.0, 13.001),
                SpurCoordinate(52.0, 13.002),
            ),
        )

        val segments = normalizedRoadSegments(
            routes = listOf(
                listOf(
                    SpurCoordinate(52.00005, 13.0002),
                    SpurCoordinate(52.00005, 13.0018),
                ),
            ),
            roads = listOf(firstRoad, secondRoad),
        )

        assertEquals(2, segments.size)
        assertEquals(13.0002, segments.first().first().longitude, 0.00003)
        assertEquals(13.001, segments.first().last().longitude, 0.00001)
        assertEquals(13.001, segments.last().first().longitude, 0.00001)
        assertEquals(13.0018, segments.last().last().longitude, 0.00003)
    }

    @Test
    fun keepsSeparateCoverageFromMultipleTours() {
        val segments = normalizedRoadSegments(
            routes = listOf(
                listOf(
                    SpurCoordinate(52.0, 13.0001),
                    SpurCoordinate(52.0, 13.0003),
                ),
                listOf(
                    SpurCoordinate(52.0, 13.0007),
                    SpurCoordinate(52.0, 13.0009),
                ),
            ),
            roads = listOf(road.copy(key = "ground|road")),
        )

        assertEquals(2, segments.size)
        assertEquals(13.0001, segments.first().first().longitude, 0.00003)
        assertEquals(13.0009, segments.last().last().longitude, 0.00003)
    }

    @Test
    fun normalizesSidewalkGpsOntoNearbyStreetCenterline() {
        val street = road.copy(key = "street", kind = RoadKind.STREET)
        val sidewalk = RenderedRoadSegment(
            key = "sidewalk",
            points = road.points.map { it.copy(latitude = 52.00005) },
            kind = RoadKind.PATH,
        )

        val segments = normalizedRoadSegments(
            routes = listOf(
                listOf(
                    SpurCoordinate(52.00005, 13.0001),
                    SpurCoordinate(52.00005, 13.0009),
                ),
            ),
            roads = listOf(street, sidewalk),
        )

        assertEquals(1, segments.size)
        assertTrue(segments.single().all { it.latitude == 52.0 })
    }

    @Test
    fun keepsStreetMatchStableAcrossSmallGpsSidewaysJitter() {
        val first = road.copy(key = "first-street")
        val second = RenderedRoadSegment(
            key = "second-street",
            points = road.points.map { it.copy(latitude = 52.00005) },
        )

        val segments = normalizedRoadSegments(
            routes = listOf(
                listOf(
                    SpurCoordinate(52.0, 13.0001),
                    SpurCoordinate(52.00004, 13.0005),
                    SpurCoordinate(52.00004, 13.0009),
                ),
            ),
            roads = listOf(first, second),
        )

        assertEquals(1, segments.size)
        assertTrue(segments.single().all { it.latitude == 52.0 })
    }

    @Test
    fun followsConnectedPathInsteadOfCloserParallelPath() {
        val junction = SpurCoordinate(52.0, 13.001)
        val incoming = RenderedRoadSegment(
            key = "incoming-path",
            points = listOf(SpurCoordinate(52.0, 13.0), junction),
            kind = RoadKind.PATH,
        )
        val continuation = RenderedRoadSegment(
            key = "continuing-path",
            points = listOf(
                SpurCoordinate(52.0, 13.00102),
                SpurCoordinate(52.0, 13.002),
            ),
            kind = RoadKind.PATH,
        )
        val parallel = RenderedRoadSegment(
            key = "parallel-path",
            points = listOf(
                SpurCoordinate(52.00008, 13.001),
                SpurCoordinate(52.00008, 13.002),
            ),
            kind = RoadKind.PATH,
        )

        val segments = normalizedRoadSegments(
            routes = listOf(
                listOf(
                    SpurCoordinate(52.0, 13.0001),
                    SpurCoordinate(52.0, 13.0009),
                    SpurCoordinate(52.00008, 13.0019),
                ),
            ),
            roads = listOf(incoming, continuation, parallel),
        )

        assertEquals(2, segments.size)
        assertTrue(segments.flatten().all { it.latitude == 52.0 })
    }

    @Test
    fun fillsShortRoadEdgeSkippedBetweenGpsSamples() {
        val incoming = RenderedRoadSegment(
            key = "incoming",
            points = listOf(
                SpurCoordinate(52.0, 13.0),
                SpurCoordinate(52.0, 13.0009),
            ),
        )
        val bridge = RenderedRoadSegment(
            key = "short-bridge",
            points = listOf(
                SpurCoordinate(52.0, 13.0009),
                SpurCoordinate(52.0, 13.00096),
            ),
        )
        val outgoing = RenderedRoadSegment(
            key = "outgoing",
            points = listOf(
                SpurCoordinate(52.0, 13.00096),
                SpurCoordinate(52.0, 13.002),
            ),
        )

        val segments = normalizedRoadSegments(
            routes = listOf(
                listOf(
                    SpurCoordinate(52.0, 13.0001),
                    SpurCoordinate(52.0, 13.0019),
                ),
            ),
            roads = listOf(incoming, bridge, outgoing),
        )

        assertEquals(3, segments.size)
        assertTrue(segments.any { it.first() == bridge.points.first() && it.last() == bridge.points.last() })
    }

    @Test
    fun usesPathWhenStreetIsTooFarAway() {
        val distantStreet = road.copy(
            key = "distant-street",
            points = road.points.map { it.copy(latitude = 52.0002) },
        )
        val path = road.copy(key = "path", kind = RoadKind.PATH)

        val segments = normalizedRoadSegments(
            routes = listOf(
                listOf(
                    SpurCoordinate(52.0, 13.0001),
                    SpurCoordinate(52.0, 13.0009),
                ),
            ),
            roads = listOf(distantStreet, path),
        )

        assertEquals(1, segments.size)
        assertTrue(segments.single().all { it.latitude == 52.0 })
    }

    @Test
    fun canonicalKeyDoesNotDependOnRoadDirection() {
        assertEquals(
            canonicalRoadKey(road.points, "street"),
            canonicalRoadKey(road.points.asReversed(), "street"),
        )
    }

    @Test
    fun traversalOnlyStartsAtJunctionAndCompletesAtOtherJunction() {
        val tracker = RoadProgressTracker()

        assertNull(tracker.update(candidateAt(0.5)).road)
        assertNotNull(tracker.update(candidateAt(0.05)).road)
        assertNull(tracker.update(candidateAt(0.55)).completion)
        assertNull(tracker.update(candidateAt(0.94)).completion)
        val completed = tracker.update(candidateAt(0.95))

        assertEquals(1, completed.completion?.count)
        assertEquals(1, completed.completedRoads.values.single().count)
    }

    @Test
    fun coveredProgressDoesNotMoveBackWithGpsJitter() {
        val tracker = RoadProgressTracker()

        tracker.update(candidateAt(0.05))
        val forward = tracker.update(candidateAt(0.6)).progress
        assertTrue(forward > 0.5)
        assertEquals(forward, tracker.update(candidateAt(0.48)).progress, 0.001)
    }

    @Test
    fun repeatedEndpointFixDoesNotCountAgainButReturnTraversalDoes() {
        val tracker = RoadProgressTracker()

        tracker.update(candidateAt(0.05))
        assertEquals(1, tracker.update(candidateAt(0.95)).completion?.count)
        assertNull(tracker.update(candidateAt(0.95)).completion)
        assertNull(tracker.update(candidateAt(0.8)).completion)
        val returned = tracker.update(candidateAt(0.05))

        assertEquals(2, returned.completion?.count)
        assertEquals(2, returned.completedRoads.values.single().count)
    }

    @Test
    fun activeTraversalSurvivesOneMissingRoadMatch() {
        val tracker = RoadProgressTracker()

        tracker.update(candidateAt(0.05))
        val progress = tracker.update(candidateAt(0.55)).progress
        assertTrue(progress > 0.4)
        assertEquals(progress, tracker.update(emptyList()).progress, 0.001)
        assertEquals(1, tracker.update(candidateAt(0.95)).completion?.count)
    }

    @Test
    fun firstMovementSelectsTheEdgeLeavingTheJunction() {
        val tracker = RoadProgressTracker()
        val leavingRoad = RenderedRoadSegment(
            key = "leaving-road",
            points = listOf(
                road.points.first(),
                SpurCoordinate(52.001, 13.0),
            ),
        )
        val start = projectOntoRoad(leavingRoad.points.first(), leavingRoad.points)!!
        val moved = projectOntoRoad(SpurCoordinate(52.0003, 13.0), leavingRoad.points)!!

        tracker.update(candidateAt(0.0))
        val selected = tracker.update(
            listOf(
                RoadCandidate(
                    road = leavingRoad,
                    projection = moved,
                    previousProjection = start,
                ),
            ),
        )

        assertEquals(leavingRoad, selected.road)
        assertEquals(0.3, selected.progress, 0.01)
    }

    @Test
    fun junctionDoesNotChooseAnEdgeBeforeMovementEstablishesDirection() {
        val tracker = RoadProgressTracker()

        val pending = tracker.update(
            listOf(candidateAt(0.0).single().copy(directionKnown = false)),
        )
        val moving = tracker.update(candidateAt(0.1))

        assertNull(pending.road)
        assertEquals(road, moving.road)
    }

    @Test
    fun historicalTraversalCountsEveryCompletedTour() {
        val counts = historicalRoadTraversals(
            routes = listOf(road.points, road.points),
            roads = listOf(road),
        )

        assertEquals(2, counts.getValue(road.key).count)
    }

    @Test
    fun repeatedLoopKeepsCountingConnectedRoadBesideCloserParallelRoad() {
        val southWest = SpurCoordinate(52.0, 13.0)
        val southEast = SpurCoordinate(52.0, 13.001)
        val northEast = SpurCoordinate(52.001, 13.001)
        val northWest = SpurCoordinate(52.001, 13.0)
        val south = RenderedRoadSegment("south", listOf(southWest, southEast))
        val east = RenderedRoadSegment("east", listOf(southEast, northEast))
        val north = RenderedRoadSegment("north", listOf(northEast, northWest))
        val west = RenderedRoadSegment("west", listOf(northWest, southWest))
        val parallel = RenderedRoadSegment(
            key = "parallel",
            points = listOf(
                SpurCoordinate(52.00004, 13.0),
                SpurCoordinate(52.00004, 13.001),
            ),
        )
        val lapAfterStart = listOf(
            SpurCoordinate(52.0, 13.0001),
            SpurCoordinate(52.00004, 13.00014),
            SpurCoordinate(52.0, 13.0009),
            southEast,
            northEast,
            northWest,
            southWest,
        )
        val route = buildList {
            add(southWest)
            repeat(22) { addAll(lapAfterStart) }
        }

        val baseCounts = historicalRoadTraversals(
            routes = listOf(route),
            roads = listOf(south, east, north, west),
        )
        val detailedCounts = historicalRoadTraversals(
            routes = listOf(route),
            roads = listOf(south, east, north, west, parallel),
        )

        assertEquals(22, baseCounts.getValue(south.key).count)
        assertEquals(22, detailedCounts.getValue(south.key).count)
        assertFalse(parallel.key in detailedCounts)
    }

    @Test
    fun roadCountLabelAppearsOnlyFromSecondTraversal() {
        assertEquals(15.0, RoadCountMinimumZoom, 0.0)
        val features = roadCountFeatures(
            listOf(
                CompletedRoad(road.copy(key = "once"), count = 1),
                CompletedRoad(road.copy(key = "twice"), count = 2),
            ),
        )

        assertEquals(1, features.size)
        assertEquals("×2", features.single().getStringProperty(RoadCountLabelProperty))
    }

    @Test
    fun liveTraversalContinuesFromHistoricalCount() {
        val analyzer = RoadTraversalAnalyzer(listOf(road))
        val tracker = RoadProgressTracker().apply {
            replaceCompleted(listOf(CompletedRoad(road, count = 2)))
        }

        val update = analyzer.updateRoute(
            route = road.points,
            tracker = tracker,
            resetTraversal = true,
        )

        assertEquals(3, update.snapshot.completedRoads.getValue(road.key).count)
        assertEquals(3, update.completions.single().count)
    }

    @Test
    fun denseWalkingSamplesStillCompleteRoad() {
        val denseRoute = (0..200).map { step ->
            SpurCoordinate(
                latitude = 52.0,
                longitude = 13.0 + 0.001 * step / 200.0,
            )
        }

        val counts = historicalRoadTraversals(
            routes = listOf(denseRoute),
            roads = listOf(road),
        )

        assertEquals(1, counts.getValue(road.key).count)
    }

    @Test
    fun partialHistoricalTraversalDoesNotCountRoad() {
        val counts = historicalRoadTraversals(
            routes = listOf(
                listOf(
                    road.points.first(),
                    SpurCoordinate(52.0, 13.0008),
                ),
            ),
            roads = listOf(road),
        )

        assertTrue(counts.isEmpty())
    }

    @Test
    fun intersectionSplitsRoadsIntoFourEdges() {
        val center = SpurCoordinate(52.0, 13.0)
        val edges = intersectionRoadEdges(
            listOf(
                RoadPolyline(
                    listOf(
                        SpurCoordinate(52.0, 12.999),
                        center,
                        SpurCoordinate(52.0, 13.001),
                    ),
                ),
                RoadPolyline(
                    listOf(
                        SpurCoordinate(51.999, 13.0),
                        center,
                        SpurCoordinate(52.001, 13.0),
                    ),
                ),
            ),
        )

        assertEquals(4, edges.size)
        assertTrue(edges.all { it.points.first() == center || it.points.last() == center })
    }

    @Test
    fun shapePointsAndOsmWayBoundariesDoNotCreateEdges() {
        val start = SpurCoordinate(52.0, 13.0)
        val shape = SpurCoordinate(52.0001, 13.0005)
        val wayBoundary = SpurCoordinate(52.0, 13.001)
        val end = SpurCoordinate(52.0, 13.002)

        val edges = intersectionRoadEdges(
            listOf(
                RoadPolyline(listOf(start, shape, wayBoundary)),
                RoadPolyline(listOf(wayBoundary, end)),
            ),
        )

        assertEquals(1, edges.size)
        assertEquals(4, edges.single().points.size)
        assertTrue(edges.single().points.first() == start || edges.single().points.last() == start)
    }

    @Test
    fun edgeDirectionIsStableWhenSourceGeometryIsReversed() {
        val points = listOf(
            SpurCoordinate(52.0, 13.0),
            SpurCoordinate(52.0001, 13.0005),
            SpurCoordinate(52.0, 13.001),
        )

        val forward = intersectionRoadEdges(listOf(RoadPolyline(points))).single()
        val reversed = intersectionRoadEdges(
            listOf(RoadPolyline(points.asReversed())),
        ).single()

        assertEquals(forward, reversed)
    }

    @Test
    fun intersectionGraphKeepsStreetAndPathSeparate() {
        val points = listOf(
            SpurCoordinate(52.0, 13.0),
            SpurCoordinate(52.0, 13.001),
        )

        val edges = intersectionRoadEdges(
            listOf(
                RoadPolyline(points, kind = RoadKind.STREET),
                RoadPolyline(points, kind = RoadKind.PATH),
            ),
        )

        assertEquals(setOf(RoadKind.STREET, RoadKind.PATH), edges.map { it.kind }.toSet())
    }

    @Test
    fun serviceRoadStillSplitsStreetAtIntersection() {
        val center = SpurCoordinate(52.0, 13.0)

        val edges = intersectionRoadEdges(
            listOf(
                RoadPolyline(
                    points = listOf(
                        SpurCoordinate(52.0, 12.999),
                        center,
                        SpurCoordinate(52.0, 13.001),
                    ),
                ),
                RoadPolyline(
                    points = listOf(
                        center,
                        SpurCoordinate(52.001, 13.0),
                    ),
                    kind = RoadKind.SERVICE,
                ),
            ),
        )

        assertEquals(3, edges.size)
        assertTrue(edges.all { it.points.first() == center || it.points.last() == center })
    }

    @Test
    fun headingPenaltyPrefersRoadAlignedWithMovement() {
        val eastWest = road
        val northSouth = RenderedRoadSegment(
            key = "cross-road",
            points = listOf(
                SpurCoordinate(51.9995, 13.0005),
                SpurCoordinate(52.0005, 13.0005),
            ),
        )
        val previous = SpurCoordinate(52.0, 13.0002)
        val current = SpurCoordinate(52.0, 13.0005)
        val eastWestProjection = projectOntoRoad(current, eastWest.points)!!
        val northSouthProjection = projectOntoRoad(current, northSouth.points)!!

        val aligned = roadHeadingPenalty(previous, current, eastWest, eastWestProjection)
        val crossing = roadHeadingPenalty(previous, current, northSouth, northSouthProjection)

        assertTrue(aligned < crossing)
    }

    private fun candidateAt(fraction: Double): List<RoadCandidate> {
        val coordinate = SpurCoordinate(
            latitude = road.points.first().latitude,
            longitude = road.points.first().longitude + 0.001 * fraction,
        )
        return listOf(
            RoadCandidate(
                road = road,
                projection = projectOntoRoad(coordinate, road.points)!!,
            ),
        )
    }
}
