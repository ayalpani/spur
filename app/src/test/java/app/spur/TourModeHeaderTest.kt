package app.spur

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TourModeHeaderTest {
    @Test
    fun normalMapIsNotReportedAsRunningTour() {
        assertFalse(isDisplayedActiveTour(tour = null, activeTour = null))
    }

    @Test
    fun matchingActiveTourIsReportedAsRunningTour() {
        val tour = Tour(
            id = 7L,
            startedAt = 1_000L,
            endedAt = null,
            distanceMeters = 0.0,
            pointCount = 0,
        )

        assertTrue(isDisplayedActiveTour(tour = tour, activeTour = tour))
    }
}
