package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap

class FollowLocationTest {
    @Test
    fun playerStacksOnlyBelowItsCalculatedMinimumWidth() {
        assertTrue(shouldStackMapPlayer(screenWidthDp = 337))
        assertFalse(shouldStackMapPlayer(screenWidthDp = 338))
        assertFalse(shouldStackMapPlayer(screenWidthDp = 600))
    }

    @Test
    fun activeFollowingButtonOpensTourOverviewOnlyWhenRouteExists() {
        assertTrue(
            shouldShowTourOverview(
                isFollowingLocation = true,
                isTourActive = true,
                routePointCount = 2,
            ),
        )
        assertFalse(
            shouldShowTourOverview(
                isFollowingLocation = false,
                isTourActive = true,
                routePointCount = 2,
            ),
        )
        assertFalse(
            shouldShowTourOverview(
                isFollowingLocation = true,
                isTourActive = false,
                routePointCount = 2,
            ),
        )
        assertFalse(
            shouldShowTourOverview(
                isFollowingLocation = true,
                isTourActive = true,
                routePointCount = 0,
            ),
        )
    }

    @Test
    fun asteriskStartsAtWalkingSpeedAndUsesHysteresis() {
        assertFalse(movingForSpeed(0.99, wasMoving = false))
        assertTrue(movingForSpeed(1.0, wasMoving = false))
        assertTrue(movingForSpeed(0.5, wasMoving = true))
        assertFalse(movingForSpeed(0.49, wasMoving = true))
        assertFalse(movingForSpeed(null, wasMoving = true))
    }

    @Test
    fun asteriskNeverRotatesInsideTheHomeZone() {
        assertFalse(
            movingForMapSignal(
                isAtHome = true,
                speedKilometersPerHour = 25.0,
                wasMoving = false,
            ),
        )
        assertFalse(
            movingForMapSignal(
                isAtHome = true,
                speedKilometersPerHour = 25.0,
                wasMoving = true,
            ),
        )
        assertTrue(
            movingForMapSignal(
                isAtHome = false,
                speedKilometersPerHour = 1.0,
                wasMoving = false,
            ),
        )
    }

    @Test
    fun onlyUserGesturesStopFollowing() {
        assertTrue(
            shouldStopFollowing(
                MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE,
            ),
        )
        assertFalse(
            shouldStopFollowing(
                MapLibreMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION,
            ),
        )
        assertFalse(
            shouldStopFollowing(
                MapLibreMap.OnCameraMoveStartedListener.REASON_API_ANIMATION,
            ),
        )
    }

    @Test
    fun automaticFollowingKeepsTheEstablishedMapPreviewStable() {
        assertTrue(
            shouldRefreshAlternateMapPreview(
                isFollowingLocation = true,
                hasPreviewCameraPosition = false,
            ),
        )
        assertFalse(
            shouldRefreshAlternateMapPreview(
                isFollowingLocation = true,
                hasPreviewCameraPosition = true,
            ),
        )
        assertTrue(
            shouldRefreshAlternateMapPreview(
                isFollowingLocation = false,
                hasPreviewCameraPosition = true,
            ),
        )
    }

    @Test
    fun directMapGestureHidesTourChrome() {
        assertFalse(shouldShowTourChrome(isMapGestureActive = true))
        assertTrue(shouldShowTourChrome(isMapGestureActive = false))
    }

    @Test
    fun startupLoaderDoesNotReturnAfterLeavingTheMap() {
        assertTrue(
            shouldShowInitialMapLoading(
                initialLoadingComplete = false,
                isMapReady = false,
            ),
        )
        assertFalse(
            shouldShowInitialMapLoading(
                initialLoadingComplete = true,
                isMapReady = false,
            ),
        )
    }

    @Test
    fun manualLocationRequiresFiveSecondsOnlyForModeEntry() {
        assertEquals(5_000L, manualLocationHoldDurationMillis(false))
        assertEquals(1_000L, manualLocationHoldDurationMillis(true))
    }

    @Test
    fun storedMapRotationFallsBackToNorth() {
        assertEquals(MapRotation.WEST, mapRotationFromStored("WEST"))
        assertEquals(270.0, MapRotation.WEST.bearing, 0.0)
        assertEquals(MapRotation.NORTH, mapRotationFromStored("invalid"))
        assertEquals(MapRotation.NORTH, mapRotationFromStored(null))
    }

    @Test
    fun compassUsesTheShortestRotation() {
        assertEquals(-360f, nearestCompassRotation(current = -270f, target = 0f))
        assertEquals(-90f, nearestCompassRotation(current = 0f, target = -90f))
        assertEquals(0f, nearestCompassRotation(current = -90f, target = 0f))
    }

    @Test
    fun compassOptionGapUsesTheEdgeFacingTheCircle() {
        val horizontalDistance = mapRotationOptionCenterDistance(
            circleRadius = 42f,
            gap = 16f,
            halfWidth = 50f,
            halfHeight = 16f,
            angleRadians = 0.0,
        )
        val verticalDistance = mapRotationOptionCenterDistance(
            circleRadius = 42f,
            gap = 16f,
            halfWidth = 50f,
            halfHeight = 16f,
            angleRadians = Math.PI / 2,
        )

        assertEquals(16f, horizontalDistance - 42f - 50f, 0.001f)
        assertEquals(16f, verticalDistance - 42f - 16f, 0.001f)
    }
}
