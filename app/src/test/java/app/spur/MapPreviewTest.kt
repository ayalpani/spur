package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPreviewTest {
    @Test
    fun previewKeepsTheMainMapExtentAtLowerResolution() {
        assertEquals(
            16.5,
            mapPreviewZoom(
                mapZoom = 17.5,
                mapWidthPixels = 1080,
                density = 3f,
                previewWidthPixels = 180,
            ),
            0.0001,
        )
    }

    @Test
    fun locationHitTargetUsesAComfortableSixtyDpSquare() {
        assertTrue(isWithinLocationHitTarget(70f, 70f, 100f, 100f, 60f))
        assertFalse(isWithinLocationHitTarget(69.9f, 100f, 100f, 100f, 60f))
    }
}
