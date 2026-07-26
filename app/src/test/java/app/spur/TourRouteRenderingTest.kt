package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.maplibre.geojson.LineString

class TourRouteRenderingTest {
    @Test
    fun routeGeometryStartsWithTwoPointsAndKeepsTheirOrder() {
        assertNull(tourRouteFeature(listOf(point(1, 52.52, 13.40))))

        val route = tourRouteFeature(
            listOf(
                point(1, 52.52, 13.40),
                point(2, 52.53, 13.41),
            ),
        )
        val coordinates = (route?.geometry() as LineString).coordinates()

        assertEquals(13.40, coordinates[0].longitude(), 0.0)
        assertEquals(52.52, coordinates[0].latitude(), 0.0)
        assertEquals(13.41, coordinates[1].longitude(), 0.0)
        assertEquals(52.53, coordinates[1].latitude(), 0.0)
    }

    private fun point(
        id: Long,
        latitude: Double,
        longitude: Double,
    ) = TrackPoint(
        id = id,
        latitude = latitude,
        longitude = longitude,
        recordedAt = id,
    )
}
