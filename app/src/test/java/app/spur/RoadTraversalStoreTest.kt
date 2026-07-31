package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
