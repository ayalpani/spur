package app.spur

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class MapControlColorTest {
    @Test
    fun paletteHasNinePersistentOptionsAndDefaultsToBlack() {
        assertEquals(9, MapControlColor.entries.size)
        assertEquals(MapControlColor.BLUE, mapControlColorFromStored("BLUE"))
        assertEquals(MapControlColor.BLACK, mapControlColorFromStored("invalid"))
        assertEquals(MapControlColor.BLACK, mapControlColorFromStored(null))
    }

    @Test
    fun playerColorsInvertTheSelectedMapControlColors() {
        val selected = MapControlColors(
            background = Color(0xFF2563EB),
            foreground = Color.White,
        )

        assertEquals(Color.White, selected.inverted.background)
        assertEquals(Color(0xFF2563EB), selected.inverted.foreground)
    }
}
