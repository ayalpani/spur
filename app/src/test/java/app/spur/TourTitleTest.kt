package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TourTitleTest {
    @Test
    fun `title is trimmed and blank title clears the custom name`() {
        assertEquals("Abendrunde", normalizeTourTitle("  Abendrunde  "))
        assertNull(normalizeTourTitle("   "))
    }

    @Test
    fun `title is bounded for the header and history`() {
        assertEquals(
            TourTitleMaximumCharacters,
            normalizeTourTitle("x".repeat(TourTitleMaximumCharacters + 20))?.length,
        )
    }
}
