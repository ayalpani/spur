package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.Feature
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

class HomeBuildingSelectionTest {
    @Test
    fun buildingsBecomeSelectableAtConfiguredZoom() {
        assertFalse(canSelectHomeBuilding(HomeBuildingMinimumSelectionZoom - 0.01))
        assertTrue(canSelectHomeBuilding(HomeBuildingMinimumSelectionZoom))
    }

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

    @Test
    fun gpsNearHomeUsesTheChosenStartPoint() {
        val startPoint = SpurCoordinate(latitude = 52.0002, longitude = 13.0003)
        val settings = HomeAutoStartSettings(
            enabled = false,
            home = SpurCoordinate(latitude = 52.0, longitude = 13.0),
            startPoint = startPoint,
        )

        val result = normalizedHomeCoordinate(
            settings = settings,
            coordinate = SpurCoordinate(latitude = 52.0005, longitude = 13.0),
        )

        assertEquals(startPoint, result)
    }

    @Test
    fun gpsOutsideTheHomeZoneRemainsUnchanged() {
        val gps = SpurCoordinate(latitude = 52.002, longitude = 13.0)
        val settings = HomeAutoStartSettings(
            enabled = false,
            home = SpurCoordinate(latitude = 52.0, longitude = 13.0),
            startPoint = SpurCoordinate(latitude = 52.0002, longitude = 13.0003),
        )

        assertEquals(gps, normalizedHomeCoordinate(settings, gps))
    }

    @Test
    fun automaticTourBeginsAtChosenStartPointBeforeFirstOutsideFix() {
        val startPoint = SpurCoordinate(latitude = 52.0002, longitude = 13.0003)
        val firstOutsideFix = SpurCoordinate(latitude = 52.002, longitude = 13.0)
        val settings = HomeAutoStartSettings(
            enabled = true,
            home = SpurCoordinate(latitude = 52.0, longitude = 13.0),
            startPoint = startPoint,
        )

        val points = listOf(
            requireNotNull(automaticTourStartPoint(settings)),
            normalizedHomeCoordinate(settings, firstOutsideFix),
        )

        assertEquals(listOf(startPoint, firstOutsideFix), points)
    }

    @Test
    fun existingMapMomentsNearHomeRenderAtTheChosenStartPoint() {
        val startPoint = SpurCoordinate(latitude = 52.0002, longitude = 13.0003)
        val moment = MapMoment(
            id = "emoji-home",
            type = MomentType.EMOJI,
            latitude = 52.0005,
            longitude = 13.0,
            payload = "🏠",
        )

        val result = normalizedHomeMoments(
            moments = listOf(moment),
            settings = HomeAutoStartSettings(
                enabled = false,
                home = SpurCoordinate(latitude = 52.0, longitude = 13.0),
                startPoint = startPoint,
            ),
        ).single()

        assertEquals(startPoint.latitude, result.latitude, 0.0)
        assertEquals(startPoint.longitude, result.longitude, 0.0)
    }

    @Test
    fun selectedBuildingIsHighlightedAlongsideTheStoredHome() {
        val home = buildingFeature(longitude = 13.0)
        val selected = buildingFeature(longitude = 13.001)

        assertEquals(selected, selectedBuildingHighlight(home, selected))
        assertEquals(null, selectedBuildingHighlight(home, home))
    }

    @Test
    fun buildingTapExtractsOnlyThePolygonContainingTheTap() {
        val first = requireNotNull(buildingFeature(longitude = 13.0).geometry() as? Polygon)
        val second = requireNotNull(buildingFeature(longitude = 13.001).geometry() as? Polygon)
        val grouped = Feature.fromGeometry(
            MultiPolygon.fromLngLats(
                listOf(first.coordinates(), second.coordinates()),
            ),
        )

        val selected = requireNotNull(
            buildingFeatureAt(
                grouped,
                SpurCoordinate(latitude = 52.0001, longitude = 13.0011),
            ),
        )

        assertEquals(second, selected.geometry())
    }

    private fun buildingFeature(longitude: Double): Feature =
        Feature.fromGeometry(
            Polygon.fromLngLats(
                listOf(
                    listOf(
                        Point.fromLngLat(longitude, 52.0),
                        Point.fromLngLat(longitude + 0.0002, 52.0),
                        Point.fromLngLat(longitude + 0.0002, 52.0002),
                        Point.fromLngLat(longitude, 52.0002),
                        Point.fromLngLat(longitude, 52.0),
                    ),
                ),
            ),
        )
}
