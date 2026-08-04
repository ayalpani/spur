package app.spur

import org.junit.Assert.assertEquals
import org.junit.Test

class WaypointRailFormattingTest {
    @Test
    fun `waypoint position uses a compact slash`() {
        assertEquals("643/870", waypointPositionText(selectedIndex = 642, total = 870))
    }
}
