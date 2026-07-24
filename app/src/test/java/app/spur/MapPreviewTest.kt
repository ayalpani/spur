package app.spur

import org.junit.Assert.assertEquals
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
}
