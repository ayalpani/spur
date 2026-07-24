package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpurCoordinateTest {
    @Test
    fun manualLocationOverridesGpsUntilReset() {
        val gps = SpurCoordinate(52.52, 13.405)
        val manual = SpurCoordinate(48.137, 11.575)

        assertEquals(manual, resolveSpurCoordinate(manual, gps))
        assertEquals(gps, resolveSpurCoordinate(null, gps))
        assertNull(resolveSpurCoordinate(null, null))
    }
}
