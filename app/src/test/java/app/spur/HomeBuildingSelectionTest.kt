package app.spur

import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

class HomeBuildingSelectionTest {
    @Test
    fun selectedHomeUsesTheCenterOfTheBuildingShape() {
        val building = Feature.fromGeometry(
            Polygon.fromLngLats(
                listOf(
                    listOf(
                        Point.fromLngLat(13.0, 52.0),
                        Point.fromLngLat(13.0004, 52.0),
                        Point.fromLngLat(13.0004, 52.0002),
                        Point.fromLngLat(13.0, 52.0002),
                        Point.fromLngLat(13.0, 52.0),
                    ),
                ),
            ),
        )

        val home = requireNotNull(homeCoordinate(building))

        assertEquals(52.0001, home.latitude, 0.000001)
        assertEquals(13.0002, home.longitude, 0.000001)
    }

    @Test
    fun selectedBuildingShapeSurvivesLocalStorageRoundTrip() {
        val building = Feature.fromGeometry(
            Polygon.fromLngLats(
                listOf(
                    listOf(
                        Point.fromLngLat(13.0, 52.0),
                        Point.fromLngLat(13.0004, 52.0),
                        Point.fromLngLat(13.0004, 52.0002),
                        Point.fromLngLat(13.0, 52.0002),
                        Point.fromLngLat(13.0, 52.0),
                    ),
                ),
            ),
        )

        val restored = requireNotNull(decodeHomeBuilding(encodeHomeBuilding(building)))

        assertEquals(building.geometry(), restored.geometry())
    }

    @Test
    fun automaticTourStartsAtTheChosenPointInsteadOfTheBuildingCenter() {
        val home = SpurCoordinate(latitude = 52.0, longitude = 13.0)
        val startPoint = SpurCoordinate(latitude = 52.0002, longitude = 13.0003)

        val result = automaticTourStartPoint(
            HomeAutoStartSettings(
                enabled = true,
                home = home,
                startPoint = startPoint,
            ),
        )

        assertEquals(startPoint, result)
    }

    @Test
    fun existingSettingsFallBackToTheBuildingCenter() {
        val home = SpurCoordinate(latitude = 52.0, longitude = 13.0)

        val result = automaticTourStartPoint(
            HomeAutoStartSettings(
                enabled = true,
                home = home,
            ),
        )

        assertEquals(home, result)
    }
}
