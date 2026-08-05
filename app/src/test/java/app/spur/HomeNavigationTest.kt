package app.spur

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeNavigationTest {
    @Test
    fun archivedTourOpenedFromHomeReturnsBeforeDeletion() {
        val archivedTour = tour(id = 7L, endedAt = 2L)

        assertTrue(
            shouldRevealHomeBeforeDeletingTour(
                deletedTourId = archivedTour.id,
                displayedTour = archivedTour,
                previousRoute = SpurRoute.HOME,
            ),
        )
        assertFalse(
            shouldRevealHomeBeforeDeletingTour(
                deletedTourId = archivedTour.id,
                displayedTour = archivedTour.copy(endedAt = null),
                previousRoute = SpurRoute.HOME,
            ),
        )
        assertFalse(
            shouldRevealHomeBeforeDeletingTour(
                deletedTourId = 8L,
                displayedTour = archivedTour,
                previousRoute = SpurRoute.HOME,
            ),
        )
        assertFalse(
            shouldRevealHomeBeforeDeletingTour(
                deletedTourId = archivedTour.id,
                displayedTour = archivedTour,
                previousRoute = SpurRoute.MAP,
            ),
        )
    }

    private fun tour(id: Long, endedAt: Long?) = Tour(
        id = id,
        startedAt = 1L,
        endedAt = endedAt,
        distanceMeters = 0.0,
        pointCount = 0,
    )
}
