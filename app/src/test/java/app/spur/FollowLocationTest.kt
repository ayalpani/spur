package app.spur

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
}
