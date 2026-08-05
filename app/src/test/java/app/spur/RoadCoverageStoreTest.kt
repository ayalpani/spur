package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadCoverageStoreTest {
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
    fun viewportCacheChangesWhenPanningRevealsANewCell() {
        val firstCenter = SpurCoordinate(52.5000, 13.3969)
        val pannedCenter = SpurCoordinate(52.5000, 13.3971)
        val firstCameraKey = roadNetworkCameraKey(zoom = 16.0, target = firstCenter)
        val pannedCameraKey = roadNetworkCameraKey(zoom = 16.0, target = pannedCenter)
        val firstViewportKey = roadNetworkViewportKey(
            zoom = 16.0,
            bounds = RoadHistoryBounds(
                minimumLatitude = 52.495,
                maximumLatitude = 52.505,
                minimumLongitude = 13.3919,
                maximumLongitude = 13.4019,
            ),
        )
        val pannedViewportKey = roadNetworkViewportKey(
            zoom = 16.0,
            bounds = RoadHistoryBounds(
                minimumLatitude = 52.495,
                maximumLatitude = 52.505,
                minimumLongitude = 13.3921,
                maximumLongitude = 13.4021,
            ),
        )

        assertEquals(firstCameraKey, pannedCameraKey)
        assertNotEquals(firstViewportKey, pannedViewportKey)
    }

    @Test
    fun globalLayerCacheAppendsWithoutRemovingPreparedCoverage() {
        val first = listOf(
            SpurCoordinate(52.50, 13.40),
            SpurCoordinate(52.50, 13.41),
        )
        val second = listOf(
            SpurCoordinate(52.51, 13.42),
            SpurCoordinate(52.51, 13.43),
        )
        val layerModel = RoadProgressLayerModel()

        val prepared = layerModel.replace("history", listOf(first))
        val appended = requireNotNull(layerModel.append("history", listOf(second)))

        assertTrue(appended.revision > prepared.revision)
        assertTrue(appended.segments.contains(first))
        assertTrue(appended.segments.contains(second))
        assertNull(layerModel.append("different-history", listOf(first)))
        assertEquals(appended, layerModel.current())
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
