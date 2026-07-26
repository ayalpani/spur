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
    fun promptFadesBeforeTheHandleCanCrossIt() {
        assertEquals(1f, stopSwipePromptAlpha(offset = 0f, maximum = 100f), 0.001f)
        assertEquals(1f, stopSwipePromptAlpha(offset = 15f, maximum = 100f), 0.001f)
        assertEquals(0.5f, stopSwipePromptAlpha(offset = 35f, maximum = 100f), 0.001f)
        assertEquals(0f, stopSwipePromptAlpha(offset = 55f, maximum = 100f), 0.001f)
    }

    @Test
    fun activeTourDistanceUsesWholeMeters() {
        assertEquals("93 m", formatMeters(92.6))
        assertEquals("0 m", formatMeters(-1.0))
        assertEquals("22.027 m", formatMeters(22_027.0))
    }
}
