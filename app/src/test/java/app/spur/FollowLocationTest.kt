package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap

class FollowLocationTest {
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
    fun automaticFollowingKeepsTheMapPreviewVisible() {
        assertFalse(
            shouldShowMapPreviewLoading(
                isFollowingLocation = true,
                cameraMoveReason =
                    MapLibreMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION,
            ),
        )
        assertTrue(
            shouldShowMapPreviewLoading(
                isFollowingLocation = true,
                cameraMoveReason =
                    MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE,
            ),
        )
        assertTrue(
            shouldShowMapPreviewLoading(
                isFollowingLocation = false,
                cameraMoveReason =
                    MapLibreMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION,
            ),
        )
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
