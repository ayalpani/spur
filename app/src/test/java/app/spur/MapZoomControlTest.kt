package app.spur

import org.junit.Assert.assertEquals
import org.junit.Test

class MapZoomControlTest {
    @Test
    fun `dragging upward zooms in continuously beyond the control bounds`() {
        assertEquals(
            17.5,
            mapZoomAfterDrag(
                startZoom = 15.0,
                upwardDragPixels = 140f,
                pixelsPerLevel = 56f,
            ),
            0.0001,
        )
    }

    @Test
    fun `dragging downward zooms out and respects map limits`() {
        assertEquals(
            MapZoomMinimum,
            mapZoomAfterDrag(
                startZoom = 15.0,
                upwardDragPixels = -2_000f,
                pixelsPerLevel = 56f,
            ),
            0.0001,
        )
        assertEquals(MapZoomMaximum, steppedMapZoom(MapZoomMaximum, 1), 0.0001)
        assertEquals(
            SatelliteMapZoomMaximum,
            steppedMapZoom(
                zoom = SatelliteMapZoomMaximum,
                direction = 1,
                maximumZoom = SatelliteMapZoomMaximum,
            ),
            0.0001,
        )
    }

    @Test
    fun `displayed zoom follows the continuous map zoom from one to twenty`() {
        assertEquals(1, displayedMapZoomLevel(0.0))
        assertEquals(17, displayedMapZoomLevel(17.49))
        assertEquals(18, displayedMapZoomLevel(17.5))
        assertEquals(20, displayedMapZoomLevel(21.0))
    }

    @Test
    fun `default state compares the displayed zoom levels`() {
        assertEquals(true, isDefaultMapZoomLevel(17.6, 17.7))
        assertEquals(false, isDefaultMapZoomLevel(17.4, 17.7))
    }

    @Test
    fun `selecting a default stores the displayed integer level`() {
        assertEquals(18.0, selectedDefaultMapZoom(17.6), 0.0001)
    }

    @Test
    fun `map modes normalize to their supported maximum zoom`() {
        assertEquals(MapZoomMaximum, mapZoomMaximum(satellite = false), 0.0001)
        assertEquals(
            SatelliteMapZoomMaximum,
            mapZoomMaximum(satellite = true),
            0.0001,
        )
        assertEquals(MapZoomMaximum, normalizedMapZoom(24.0, satellite = false), 0.0001)
        assertEquals(
            SatelliteMapZoomMaximum,
            normalizedMapZoom(24.0, satellite = true),
            0.0001,
        )
    }
}
