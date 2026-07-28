package app.spur

import org.junit.Assert.assertEquals
import org.junit.Test

class TourEditingTest {
    private val points = listOf(
        TrackPoint(1, 52.5200, 13.4050, 1_000),
        TrackPoint(2, 52.5205, 13.4055, 2_000),
        TrackPoint(3, 52.5210, 13.4060, 3_000),
        TrackPoint(4, 52.5215, 13.4065, 4_000),
    )

    @Test
    fun trimmingKeepsOnlyPointsBetweenBothHandles() {
        assertEquals(setOf(2L, 3L), retainedPointIds(points, 1, 2))
    }

    @Test
    fun distanceIsRecalculatedFromTheRemainingRoute() {
        val fullDistance = trackDistanceMeters(points)
        val trimmedDistance = trackDistanceMeters(points.subList(1, 3))

        assertEquals(true, fullDistance > trimmedDistance)
        assertEquals(65.1, trimmedDistance, 2.0)
    }
}
