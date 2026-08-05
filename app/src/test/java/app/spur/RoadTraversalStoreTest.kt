package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadTraversalStoreTest {
    @Test
    fun roundTripsCompletedRoadCountsAndGeometry() {
        val road = RenderedRoadSegment(
            key = "ground:street|road",
            points = listOf(
                SpurCoordinate(52.5, 13.4),
                SpurCoordinate(52.5005, 13.401),
            ),
            grade = "ground",
            kind = RoadKind.SERVICE,
        )
        val completed = CompletedRoad(road, count = 3)

        assertEquals(
            mapOf(road.key to completed),
            decodeCompletedRoads(encodeCompletedRoads(listOf(completed))),
        )
    }

    @Test
    fun rejectsBrokenCompletedRoadCache() {
        assertNull(decodeCompletedRoads(byteArrayOf(0, 1, 2)))
    }

    @Test
    fun preservesProgressOnlyForAppendOnlyHistory() {
        val cached = RoadHistoryFingerprint(7, 3, 8)

        assertTrue(
            preservesRoadProgress(
                cached = cached,
                current = RoadHistoryFingerprint(9, 5, 12),
                added = RoadHistoryFingerprint(9, 2, 4),
            ),
        )
        assertFalse(
            preservesRoadProgress(
                cached = cached,
                current = RoadHistoryFingerprint(7, 2, 6),
                added = RoadHistoryFingerprint(),
            ),
        )
    }

    @Test
    fun greenRoadAndTraversalBadgeUseTheSameCompletedRoad() {
        val road = RenderedRoadSegment(
            key = "ground:street|shared",
            points = listOf(
                SpurCoordinate(52.5, 13.4),
                SpurCoordinate(52.5005, 13.401),
            ),
        )
        val completed = CompletedRoad(road, count = 2)

        assertEquals(listOf(road.points), roadCoverageSegments(listOf(completed)))
        assertEquals(
            "×2",
            roadCountFeatures(listOf(completed)).single()
                .getStringProperty(RoadCountLabelProperty),
        )
    }
}
