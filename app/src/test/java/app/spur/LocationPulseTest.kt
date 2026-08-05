package app.spur

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationPulseTest {
    @Test
    fun `location pulse keeps its radius in screen pixels`() {
        assertEquals(0f, locationPulseRadius(0f), 0.0001f)
        assertEquals(LocationPulseMaxRadius, locationPulseRadius(1f), 0.0001f)
        assertEquals(LocationPulseMaxRadius, locationPulseRadius(2f), 0.0001f)
    }

    @Test
    fun `location pulse fades in and out while expanding`() {
        assertEquals(0f, locationPulseOpacity(0f), 0.0001f)
        assertEquals(LocationPulseAlpha, locationPulseOpacity(0.2f), 0.0001f)
        assertEquals(0f, locationPulseOpacity(1f), 0.0001f)
    }
}
