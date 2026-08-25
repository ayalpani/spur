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
    }

    @Test
    fun deletionAndPointEditInvalidateEveryCachedRoadCount() {
        val cached = RoadHistoryFingerprint(9, 5, 12)

        assertFalse(
            preservesRoadProgress(
                cached = cached,
                current = RoadHistoryFingerprint(9, 4, 10),
                added = RoadHistoryFingerprint(),
            ),
        )
        assertFalse(
            preservesRoadProgress(
                cached = cached,
                current = RoadHistoryFingerprint(9, 5, 13),
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

    @Test
    fun rebuildComparisonClassifiesEveryOldAndNewRoadCount() {
        fun completed(key: String, count: Int) = CompletedRoad(
            road = RenderedRoadSegment(
                key = key,
                points = listOf(
                    SpurCoordinate(52.5, 13.4),
                    SpurCoordinate(52.5005, 13.401),
                ),
            ),
            count = count,
        )

        val change = compareRoadTraversals(
            old = listOf(
                completed("same", 2),
                completed("up", 2),
                completed("down", 4),
                completed("removed", 3),
            ).associateBy { it.road.key },
            new = listOf(
                completed("same", 2),
                completed("up", 5),
                completed("down", 1),
                completed("added", 7),
            ).associateBy { it.road.key },
        )

        assertEquals(1, change.unchangedRoads)
        assertEquals(1, change.increasedRoads)
        assertEquals(1, change.decreasedRoads)
        assertEquals(1, change.addedRoads)
        assertEquals(1, change.removedRoads)
        assertEquals(4, change.changedRoads)
        assertEquals(11L, change.oldTraversalTotal)
        assertEquals(15L, change.newTraversalTotal)
    }
}
