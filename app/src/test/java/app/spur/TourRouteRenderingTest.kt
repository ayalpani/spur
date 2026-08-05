package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiPoint
import org.maplibre.geojson.Point

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

    @Test
    fun endpointGeometryMarksFirstPointAsStartAndLastPointAsEnd() {
        val endpoints = tourEndpointFeatures(
            listOf(
                point(1, 52.52, 13.40),
                point(2, 52.53, 13.41),
                point(3, 52.54, 13.42),
            ),
        ).features().orEmpty()

        val start = endpoints[0].geometry() as Point
        val end = endpoints[1].geometry() as Point
        assertEquals(13.40, start.longitude(), 0.0)
        assertEquals(52.52, start.latitude(), 0.0)
        assertEquals(13.42, end.longitude(), 0.0)
        assertEquals(52.54, end.latitude(), 0.0)
        assertEquals("end", endpoints[1].getStringProperty("endpoint-type"))
    }

    @Test
    fun routeGeometryIncludesOneWaypointForEveryGpsPoint() {
        val features = tourRouteFeatures(
            listOf(
                point(1, 52.52, 13.40),
                point(2, 52.53, 13.41),
                point(3, 52.54, 13.42),
            ),
        ).features().orEmpty()
        val waypoints = (features.single { it.geometry() is MultiPoint }.geometry() as MultiPoint)
            .coordinates()

        assertEquals(3, waypoints.size)
        assertEquals(13.40, waypoints[0].longitude(), 0.0)
        assertEquals(52.54, waypoints[2].latitude(), 0.0)
    }

    @Test
    fun thousandsOfWaypointsStayInOneMultiPointFeature() {
        val features = tourRouteFeatures(
            (1L..5_000L).map { id ->
                point(id, 52.52 + id / 1_000_000.0, 13.40)
            },
        ).features().orEmpty()
        val waypoints = features.single { it.geometry() is MultiPoint }.geometry() as MultiPoint

        assertEquals(2, features.size)
        assertEquals(5_000, waypoints.coordinates().size)
    }

    @Test
    fun pauseGeometryCarriesItsVisibleDuration() {
        val pausePoint = TrackPoint(
            id = 2,
            latitude = 52.53,
            longitude = 13.41,
            recordedAt = 4_380_000L,
            pauseStartedAt = 300_000L,
            sampleCount = 20,
        )
        val pauseFeature = tourPauseFeatures(listOf(pausePoint)).features().orEmpty().single()
        val coordinate = pauseFeature.geometry() as Point

        assertEquals(13.41, coordinate.longitude(), 0.0)
        assertEquals(52.53, coordinate.latitude(), 0.0)
        assertEquals("Pause · 1 h 08 min", pauseFeature.getStringProperty("pause-label"))
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
