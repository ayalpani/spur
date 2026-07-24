package app.spur

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TourStoreTest {
    @Test
    fun pointFilterRejectsNoiseAndImpossibleJumps() {
        assertTrue(shouldAcceptPoint(8f, null, null))
        assertFalse(shouldAcceptPoint(41f, null, null))
        assertFalse(shouldAcceptPoint(8f, 3f, 5_000L))
        assertFalse(shouldAcceptPoint(8f, 600f, 5_000L))
        assertTrue(shouldAcceptPoint(8f, 600f, 5_000L, allowFastMovement = true))
        assertTrue(shouldAcceptPoint(8f, 12f, 5_000L))
    }
}
