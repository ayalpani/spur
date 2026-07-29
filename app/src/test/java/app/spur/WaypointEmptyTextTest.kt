package app.spur

import org.junit.Assert.assertEquals
import org.junit.Test

class WaypointEmptyTextTest {
    @Test
    fun emptyTextReflectsTourState() {
        assertEquals(
            "Starte eine Tour, um Wegpunkte aufzuzeichnen.",
            waypointEmptyText(hasActiveTour = false, hasDisplayedTour = false),
        )
        assertEquals(
            "Warte auf GPS-Signal …",
            waypointEmptyText(hasActiveTour = true, hasDisplayedTour = true),
        )
        assertEquals(
            "Keine Wegpunkte aufgezeichnet.",
            waypointEmptyText(hasActiveTour = false, hasDisplayedTour = true),
        )
    }
}
