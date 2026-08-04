package app.spur

import com.google.android.gms.location.DetectedActivity
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
    fun movementActivitiesArmThePreciseHomeDepartureCapture() {
        assertTrue(isHomeDepartureActivity(DetectedActivity.WALKING))
        assertTrue(isHomeDepartureActivity(DetectedActivity.RUNNING))
        assertTrue(isHomeDepartureActivity(DetectedActivity.ON_BICYCLE))
        assertTrue(isHomeDepartureActivity(DetectedActivity.IN_VEHICLE))
        assertFalse(isHomeDepartureActivity(DetectedActivity.STILL))
        assertFalse(isHomeDepartureActivity(DetectedActivity.UNKNOWN))
    }

    @Test
    fun homeAutomationUsesAOneHundredMeterGeofence() {
        assertEquals(100f, HomeRadiusMeters)
    }

    @Test
    fun automaticTourEndsOnlyAfterFiveMinutesOutside() {
        assertFalse(stayedOutsideHomeLongEnough(outsideSince = 1_000L, returnedAt = 300_999L))
        assertTrue(stayedOutsideHomeLongEnough(outsideSince = 1_000L, returnedAt = 301_000L))
        assertFalse(stayedOutsideHomeLongEnough(outsideSince = 1_000L, returnedAt = 999L))
    }

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

        val result = automaticTourHomePoint(
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

        val result = automaticTourHomePoint(
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
    fun homeStatusFollowsTheVisibleHomeZone() {
        val settings = HomeAutoStartSettings(
            enabled = false,
            home = SpurCoordinate(latitude = 52.0, longitude = 13.0),
        )

        assertTrue(
            isWithinHomeZone(
                settings,
                SpurCoordinate(latitude = 52.0005, longitude = 13.0),
            ),
        )
        assertFalse(
            isWithinHomeZone(
                settings,
                SpurCoordinate(latitude = 52.002, longitude = 13.0),
            ),
        )
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
            requireNotNull(automaticTourHomePoint(settings)),
            normalizedHomeCoordinate(settings, firstOutsideFix),
        )

        assertEquals(listOf(startPoint, firstOutsideFix), points)
    }

    @Test
    fun batchedLocationsKeepTheirOriginalTimestampsAndOrder() {
        val locations = listOf(
            BufferedHomeLocation(52.002, 13.0, recordedAt = 30_000L, accuracyMeters = 8f),
            BufferedHomeLocation(52.001, 13.0, recordedAt = 20_000L, accuracyMeters = 7f),
        )

        val restored = decodeBufferedHomeLocations(encodeBufferedHomeLocations(locations))

        assertEquals(locations, restored)
    }

    @Test
    fun departureDropsStationaryHomeSamplesButKeepsMeasuredRoute() {
        val settings = HomeAutoStartSettings(
            enabled = true,
            home = SpurCoordinate(52.0, 13.0),
            startPoint = SpurCoordinate(52.0001, 13.0),
        )
        val locations = listOf(
            BufferedHomeLocation(52.0, 13.0, 1_000L, 8f),
            BufferedHomeLocation(52.0001, 13.0, 2_000L, 8f),
            BufferedHomeLocation(52.0005, 13.0, 3_000L, 8f),
            BufferedHomeLocation(52.0010, 13.0, 4_000L, 8f),
            BufferedHomeLocation(52.0016, 13.0, 5_000L, 8f),
        )

        val departure = departureLocations(locations, settings, throughAt = 5_000L)

        assertEquals(listOf(3_000L, 4_000L, 5_000L), departure.map { it.recordedAt })
    }

    @Test
    fun departureNeedsSixOfTenReliableOutsideFixesIncludingTheLatest() {
        val outside = setOf(0, 1, 2, 3, 4, 9)
        val locations = confirmationLocations { index ->
            if (index in outside) 52.0012 else 52.0
        }

        assertTrue(
            confirmedHomeDeparture(
                locations = locations,
                settings = confirmationSettings(),
                candidateAt = 1_000L,
            ),
        )
    }

    @Test
    fun departureRejectsAJumpPoorAccuracyAndTooShortAWindow() {
        val settings = confirmationSettings()
        val fiveOutside = setOf(0, 1, 2, 3, 9)
        val singleJump = confirmationLocations { index ->
            if (index == 9) 52.0012 else 52.0
        }
        val fiveOfTen = confirmationLocations { index ->
            if (index in fiveOutside) 52.0012 else 52.0
        }
        val latestInside = confirmationLocations { index ->
            if (index < 6) 52.0012 else 52.0
        }
        val poorAccuracy = confirmationLocations { 52.002 }
            .map { it.copy(accuracyMeters = 60f) }
        val tooFast = confirmationLocations(intervalMillis = 2_000L) {
            52.0012
        }

        assertFalse(confirmedHomeDeparture(singleJump, settings, candidateAt = 1_000L))
        assertFalse(confirmedHomeDeparture(fiveOfTen, settings, candidateAt = 1_000L))
        assertFalse(confirmedHomeDeparture(latestInside, settings, candidateAt = 1_000L))
        assertFalse(confirmedHomeDeparture(poorAccuracy, settings, candidateAt = 1_000L))
        assertFalse(confirmedHomeDeparture(tooFast, settings, candidateAt = 1_000L))
    }

    @Test
    fun arrivalNeedsSixOfTenHomeFixesIncludingTheLatest() {
        val atHome = setOf(0, 1, 2, 3, 4, 9)
        val locations = confirmationLocations { index ->
            if (index in atHome) 52.0001 else 52.0006
        }

        assertTrue(confirmedHomeArrival(locations, confirmationSettings()))
    }

    @Test
    fun arrivalRejectsTheKioskAndASingleGpsJumpHome() {
        val settings = confirmationSettings()
        val kiosk = confirmationLocations { 52.0006 }
        val singleJump = confirmationLocations { index ->
            if (index == 9) 52.0001 else 52.0006
        }

        assertFalse(confirmedHomeArrival(kiosk, settings))
        assertFalse(confirmedHomeArrival(singleJump, settings))
    }

    @Test
    fun rollingBufferSortsDeduplicatesAndExpiresOldSamples() {
        val point = BufferedHomeLocation(52.0, 13.0, 10_000L, 8f)

        val merged = mergeBufferedHomeLocations(
            existing = listOf(point),
            incoming = listOf(
                BufferedHomeLocation(52.001, 13.0, 20_000L, 8f),
                point,
            ),
            now = 20_000L,
        )

        assertEquals(listOf(10_000L, 20_000L), merged.map { it.recordedAt })
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

    private fun confirmationSettings() = HomeAutoStartSettings(
        enabled = true,
        home = SpurCoordinate(latitude = 52.0, longitude = 13.0),
        startPoint = SpurCoordinate(latitude = 52.0001, longitude = 13.0),
    )

    private fun confirmationLocations(
        intervalMillis: Long = 4_000L,
        latitude: (Int) -> Double,
    ): List<BufferedHomeLocation> =
        (0 until HomeConfirmationSampleCount).map { index ->
            BufferedHomeLocation(
                latitude = latitude(index),
                longitude = 13.0,
                recordedAt = 1_000L + index * intervalMillis,
                accuracyMeters = 8f,
            )
        }
}
