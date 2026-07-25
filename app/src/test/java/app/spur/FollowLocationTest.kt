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
    fun cancelledMapSettingsRestoreThePreviousCamera() {
        assertTrue(shouldRestoreMapSettingsPreview(previewSession = 3, acceptedSession = null))
        assertFalse(shouldRestoreMapSettingsPreview(previewSession = 3, acceptedSession = 3))
        assertFalse(shouldRestoreMapSettingsPreview(previewSession = null, acceptedSession = 3))
    }
}
