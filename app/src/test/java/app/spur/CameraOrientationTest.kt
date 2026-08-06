package app.spur

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraOrientationTest {
    @Test
    fun `camera target follows every valid display rotation`() {
        listOf(
            Surface.ROTATION_0,
            Surface.ROTATION_90,
            Surface.ROTATION_180,
            Surface.ROTATION_270,
        ).forEach { rotation ->
            assertEquals(rotation, cameraTargetRotation(rotation))
        }
    }

    @Test
    fun `camera target safely defaults before display is attached`() {
        assertEquals(Surface.ROTATION_0, cameraTargetRotation(null))
        assertEquals(Surface.ROTATION_0, cameraTargetRotation(Int.MAX_VALUE))
    }
}
