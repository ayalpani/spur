package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemSpatialMathTest {
    @Test
    fun `location must be fresh and at most fifteen meters accurate`() {
        assertTrue(isPublishableLocation(ItemLocationFixQuality(15.0, 10_000)))
        assertEquals(false, isPublishableLocation(ItemLocationFixQuality(15.01, 9_000)))
        assertEquals(false, isPublishableLocation(ItemLocationFixQuality(4.0, 10_001)))
    }
}
