package app.spur

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecenterTapTest {
    @Test
    fun secondTapHasAForgivingWindow() {
        assertFalse(isRecenterNorthTap(previousAt = 0L, now = 500L))
        assertTrue(isRecenterNorthTap(previousAt = 1_000L, now = 1_650L))
        assertFalse(isRecenterNorthTap(previousAt = 1_000L, now = 2_001L))
    }
}
