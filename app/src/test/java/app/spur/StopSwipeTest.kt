package app.spur

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
}
