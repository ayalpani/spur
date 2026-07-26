package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopSwipeTest {
    @Test
    fun swipeMustReachTheEndOfTheTrack() {
        assertFalse(shouldCompleteStopSwipe(offset = 0f, maximum = 100f))
        assertFalse(shouldCompleteStopSwipe(offset = 81f, maximum = 100f))
        assertTrue(shouldCompleteStopSwipe(offset = 82f, maximum = 100f))
    }

    @Test
    fun promptFinishesFadingAtTheStopThreshold() {
        assertEquals(1f, stopSwipePromptAlpha(offset = 0f, maximum = 100f), 0.001f)
        assertEquals(1f, stopSwipePromptAlpha(offset = 60f, maximum = 100f), 0.001f)
        assertEquals(0.5f, stopSwipePromptAlpha(offset = 71f, maximum = 100f), 0.001f)
        assertEquals(0f, stopSwipePromptAlpha(offset = 82f, maximum = 100f), 0.001f)
    }

    @Test
    fun swipeProgressIsClampedForTheBackgroundTransition() {
        assertEquals(0f, stopSwipeProgress(offset = 20f, maximum = 0f), 0.001f)
        assertEquals(0f, stopSwipeProgress(offset = -20f, maximum = 100f), 0.001f)
        assertEquals(0.5f, stopSwipeProgress(offset = 50f, maximum = 100f), 0.001f)
        assertEquals(1f, stopSwipeProgress(offset = 120f, maximum = 100f), 0.001f)
    }

    @Test
    fun activeTourDistanceUsesWholeMeters() {
        assertEquals("93 m", formatMeters(92.6))
        assertEquals("0 m", formatMeters(-1.0))
        assertEquals("22.027 m", formatMeters(22_027.0))
    }

    @Test
    fun activeTourPlayerSwitchesBetweenDistanceAndTrackingTime() {
        val tour = Tour(
            id = 1,
            startedAt = 1_000L,
            endedAt = null,
            distanceMeters = 1_234.0,
            pointCount = 2,
        )

        assertEquals(
            "1.234 m",
            activeTourPlayerText(tour, now = 3_662_000L, showTrackingTime = false),
        )
        val trackingTime = activeTourPlayerText(
            tour,
            now = 3_662_000L,
            showTrackingTime = true,
        )
        assertTrue(trackingTime.startsWith("Seit "))
        assertTrue(trackingTime.endsWith("1:01:01"))
    }
}
