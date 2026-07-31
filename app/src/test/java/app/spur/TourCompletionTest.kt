package app.spur

import androidx.compose.ui.unit.dp
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TourCompletionTest {
    @Test
    fun `finished pending tour is eligible without an active tour`() {
        val finished = tour(id = 7L, endedAt = 60_000L)

        assertEquals(
            finished,
            eligibleTourCompletion(
                pendingTour = finished,
                activeTour = null,
            ),
        )
    }

    @Test
    fun `new active tour suppresses an older completion`() {
        assertNull(
            eligibleTourCompletion(
                pendingTour = tour(id = 7L, endedAt = 60_000L),
                activeTour = tour(id = 8L, endedAt = null),
            ),
        )
    }

    @Test
    fun `unfinished pending tour is not a completion`() {
        assertNull(
            eligibleTourCompletion(
                pendingTour = tour(id = 7L, endedAt = null),
                activeTour = null,
            ),
        )
    }

    @Test
    fun `completion statistics include distance duration and average speed`() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals(
                TourCompletionStats(
                    distance = "1,20 km",
                    duration = "10:00",
                    averageSpeed = "7,2 km/h",
                ),
                tourCompletionStats(
                    tour(
                        id = 7L,
                        endedAt = 600_000L,
                        distanceMeters = 1_200.0,
                    ),
                ),
            )
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun `completion scrolls only when the preview minimum cannot fit comfortably`() {
        assertTrue(tourCompletionNeedsScrolling(479.dp))
        assertFalse(tourCompletionNeedsScrolling(480.dp))
        assertFalse(tourCompletionNeedsScrolling(800.dp))
    }

    private fun tour(
        id: Long,
        endedAt: Long?,
        distanceMeters: Double = 0.0,
    ) = Tour(
        id = id,
        startedAt = 0L,
        endedAt = endedAt,
        distanceMeters = distanceMeters,
        pointCount = 0,
    )
}
