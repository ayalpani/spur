package app.spur

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.pow

class GoogleMapsTest {
    @Test
    fun currentMapViewportBecomesGoogleMapsUrl() {
        assertEquals(
            "https://www.google.com/maps/@?api=1&map_action=map" +
                "&center=52.520008%2C13.404954&zoom=18&basemap=satellite",
            googleMapsViewUrl(
                MapViewport(
                    center = SpurCoordinate(52.520008, 13.404954),
                    zoom = 16.6,
                    satellite = true,
                ),
            ),
        )
    }

    @Test
    fun mapLibreAndGoogleZoomsResolveToTheSameWorldScale() {
        val mapLibreZoom = 16.0
        val googleZoom = googleMapsZoom(mapLibreZoom)

        assertEquals(17, googleZoom)
        assertEquals(
            MapLibreWorldSizeAtZoomZero * 2.0.pow(mapLibreZoom),
            GoogleMapsWorldSizeAtZoomZero * 2.0.pow(googleZoom.toDouble()),
            0.0,
        )
    }

    @Test
    fun googleMapsZoomUsesNearestSupportedIntegerAndClampsToUrlRange() {
        assertEquals(18, googleMapsZoom(16.6))
        assertEquals(0, googleMapsZoom(-5.0))
        assertEquals(21, googleMapsZoom(25.0))
    }
}
