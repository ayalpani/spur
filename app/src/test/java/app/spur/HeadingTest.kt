package app.spur

import org.junit.Assert.assertEquals
import org.junit.Test

class HeadingTest {
    @Test
    fun compassTakesTheShortWayAcrossNorth() {
        assertEquals(361.0, unwrapHeading(359.0, 1.0), 0.001)
        assertEquals(-1.0, unwrapHeading(1.0, 359.0), 0.001)
    }
}
