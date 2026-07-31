package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadCoverageStoreTest {
    @Test
    fun roundTripsCachedRoadGeometry() {
        val segments = listOf(
            listOf(
                SpurCoordinate(52.5001, 13.4001),
                SpurCoordinate(52.5002, 13.4002),
            ),
            listOf(
                SpurCoordinate(52.5003, 13.4003),
                SpurCoordinate(52.5004, 13.4004),
                SpurCoordinate(52.5005, 13.4005),
            ),
        )

        assertEquals(segments, decodeRoadCoverageSegments(encodeRoadCoverageSegments(segments)))
        assertNull(decodeRoadCoverageSegments(byteArrayOf(0, 1, 2)))
    }

    @Test
    fun combinesAppendOnlyHistoryWithoutExactDuplicates() {
        val first = listOf(
            SpurCoordinate(52.5, 13.4),
            SpurCoordinate(52.6, 13.5),
        )
        val second = listOf(
            SpurCoordinate(52.6, 13.5),
            SpurCoordinate(52.7, 13.6),
        )

        assertEquals(
            listOf(first, second),
            combineRoadCoverageSegments(listOf(first), listOf(first.asReversed(), second)),
        )
        assertEquals(
            RoadHistoryFingerprint(9, 5, 12),
            RoadHistoryFingerprint(7, 3, 8) + RoadHistoryFingerprint(9, 2, 4),
        )
    }

    @Test
    fun preservesCoverageOnlyForAppendOnlyHistory() {
        val cached = RoadHistoryFingerprint(7, 3, 8)

        assertTrue(
            preservesRoadCoverage(
                cached = cached,
                current = RoadHistoryFingerprint(9, 5, 12),
                added = RoadHistoryFingerprint(9, 2, 4),
            ),
        )
        assertFalse(
            preservesRoadCoverage(
                cached = cached,
                current = RoadHistoryFingerprint(7, 2, 6),
                added = RoadHistoryFingerprint(),
            ),
        )
    }

    @Test
    fun refreshesCompatibleCoverageForEachNewRoadSourceRevision() {
        assertTrue(
            shouldRefreshRoadCoverageGeometry(
                hasCachedCoverage = true,
                cacheIsCurrent = true,
                historyCanAppend = false,
                needsCompaction = false,
                roadLoadIsApplied = false,
            ),
        )
        assertTrue(
            shouldRefreshRoadCoverageGeometry(
                hasCachedCoverage = true,
                cacheIsCurrent = false,
                historyCanAppend = true,
                needsCompaction = false,
                roadLoadIsApplied = false,
            ),
        )
        assertFalse(
            shouldRefreshRoadCoverageGeometry(
                hasCachedCoverage = true,
                cacheIsCurrent = false,
                historyCanAppend = false,
                needsCompaction = false,
                roadLoadIsApplied = false,
            ),
        )
        assertFalse(
            shouldRefreshRoadCoverageGeometry(
                hasCachedCoverage = true,
                cacheIsCurrent = true,
                historyCanAppend = false,
                needsCompaction = false,
                roadLoadIsApplied = true,
            ),
        )
    }

    @Test
    fun highZoomPanningMovesToANewRoadCacheCell() {
        val first = roadNetworkCameraKey(
            zoom = 20.0,
            target = SpurCoordinate(52.5001, 13.4001),
        )
        val panned = roadNetworkCameraKey(
            zoom = 20.0,
            target = SpurCoordinate(52.5004, 13.4004),
        )

        assertNotEquals(first, panned)
    }

    @Test
    fun overviewPanningMovesToANewRoadCacheCell() {
        val first = roadNetworkCameraKey(
            zoom = 14.0,
            target = SpurCoordinate(52.5001, 13.4001),
        )
        val panned = roadNetworkCameraKey(
            zoom = 14.0,
            target = SpurCoordinate(52.5001, 13.4051),
        )

        assertNotEquals(first, panned)
    }

    @Test
    fun roadHistoryBoundsCoverEveryLoadedRoadPlusPadding() {
        val bounds = requireNotNull(
            roadHistoryBounds(
                roads = listOf(
                    RenderedRoadSegment(
                        key = "road",
                        points = listOf(
                            SpurCoordinate(52.50, 13.40),
                            SpurCoordinate(52.52, 13.44),
                        ),
                    ),
                ),
                paddingMeters = 100.0,
            ),
        )

        assertTrue(bounds.minimumLatitude < 52.50)
        assertTrue(bounds.maximumLatitude > 52.52)
        assertTrue(bounds.minimumLongitude < 13.40)
        assertTrue(bounds.maximumLongitude > 13.44)
    }

    @Test
    fun overviewUsesPreparedCoverageCellsFromEveryVisibleZoom() {
        assertFalse(isOverviewRoadCoverageCacheKey("2:12:100:200"))
        assertTrue(isOverviewRoadCoverageCacheKey("2:13:100:200"))
        assertTrue(isOverviewRoadCoverageCacheKey("2:14:100:200"))
        assertTrue(isOverviewRoadCoverageCacheKey("2:15:100:200"))
        assertFalse(isOverviewRoadCoverageCacheKey("1:14:100:200"))
        assertFalse(isDetailRoadCoverageCacheKey("2:13:100:200"))
        assertFalse(isDetailRoadCoverageCacheKey("2:14:100:200"))
        assertTrue(isDetailRoadCoverageCacheKey("2:15:100:200"))
        assertTrue(isDetailRoadCoverageCacheKey("2:18:100:200"))
        assertFalse(isDetailRoadCoverageCacheKey("1:18:100:200"))
        assertFalse(isDetailRoadCoverageCacheKey("broken"))
    }

    @Test
    fun overviewDrawsAnOverlappingRoadArcOnlyOnce() {
        val first = SpurCoordinate(52.5, 13.4)
        val second = SpurCoordinate(52.5, 13.401)
        val third = SpurCoordinate(52.5, 13.402)
        val fourth = SpurCoordinate(52.5, 13.403)

        assertEquals(
            listOf(listOf(first, second, third, fourth)),
            mergeUniqueRoadCoverageArcs(
                listOf(
                    listOf(first, second, third),
                    listOf(second, third, fourth),
                    listOf(third, second),
                ),
            ),
        )
    }

    @Test
    fun appendsANewOverviewCellWithoutDuplicatingItsOverlap() {
        val first = SpurCoordinate(52.5, 13.4)
        val second = SpurCoordinate(52.5, 13.401)
        val third = SpurCoordinate(52.5, 13.402)
        val fourth = SpurCoordinate(52.5, 13.403)

        assertEquals(
            listOf(listOf(first, second, third, fourth)),
            appendOverviewRoadCoverageSegments(
                existing = listOf(listOf(first, second, third)),
                additions = listOf(listOf(second, third, fourth)),
            ),
        )
    }

    @Test
    fun appendingACellNeverRemovesRenderedRoadCoverage() {
        val existing = listOf(
            SpurCoordinate(52.50, 13.40),
            SpurCoordinate(52.50, 13.41),
        )
        val addition = listOf(
            SpurCoordinate(52.51, 13.42),
            SpurCoordinate(52.51, 13.43),
        )

        val rendered = appendOverviewRoadCoverageSegments(
            existing = listOf(existing),
            additions = listOf(addition),
        )

        assertTrue(rendered.contains(existing))
        assertTrue(rendered.contains(addition))
    }

    @Test
    fun overviewCollapsesSlightlyOffsetCopiesOfTheSameRoad() {
        val first = listOf(
            SpurCoordinate(52.5, 13.4),
            SpurCoordinate(52.5, 13.401),
        )
        val offsetByOneMeter = first.map { point ->
            point.copy(latitude = point.latitude + 1.0 / 111_320.0)
        }

        assertEquals(
            listOf(first),
            mergeOverviewRoadCoverageSegments(
                listOf(15 to first, 17 to offsetByOneMeter),
            ),
        )
    }

    @Test
    fun overviewKeepsASeparateParallelRoad() {
        val first = listOf(
            SpurCoordinate(52.5, 13.4),
            SpurCoordinate(52.5, 13.401),
        )
        val fiveMetersAway = first.map { point ->
            point.copy(latitude = point.latitude + 5.0 / 111_320.0)
        }

        assertEquals(
            2,
            mergeOverviewRoadCoverageSegments(
                listOf(15 to first, 17 to fiveMetersAway),
            ).size,
        )
    }
}
