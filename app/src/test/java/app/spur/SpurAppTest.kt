package app.spur

import org.junit.Assert.assertEquals
import org.junit.Test

class SpurAppTest {
    @Test
    fun restoringAnActiveTourKeepsTheDisplayedArchiveSelection() {
        val active = tour(id = 9L)

        val restored = restoredActiveTourState(
            activeTour = active,
            displayedTourId = 4L,
        )

        assertEquals(active, restored.activeTour)
        assertEquals(4L, restored.displayedTourId)
    }

    @Test
    fun restoringAnActiveTourDisplaysItWhenNothingElseIsOpen() {
        val restored = restoredActiveTourState(
            activeTour = tour(id = 9L),
            displayedTourId = null,
        )

        assertEquals(9L, restored.displayedTourId)
    }

    private fun tour(id: Long) = Tour(
        id = id,
        startedAt = 1_000L,
        endedAt = null,
        distanceMeters = 12.0,
        pointCount = 3,
    )
}
