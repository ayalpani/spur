package app.spur

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
}
