package app.spur

import android.view.Surface
import androidx.camera.core.CameraSelector
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

    @Test
    fun `physical orientation covers reverse portrait without configuration change`() {
        assertEquals(Surface.ROTATION_0, cameraTargetRotationFromOrientation(0))
        assertEquals(Surface.ROTATION_270, cameraTargetRotationFromOrientation(90))
        assertEquals(Surface.ROTATION_180, cameraTargetRotationFromOrientation(180))
        assertEquals(Surface.ROTATION_90, cameraTargetRotationFromOrientation(270))
    }

    @Test
    fun `unknown physical orientation leaves the current camera rotation unchanged`() {
        assertEquals(null, cameraTargetRotationFromOrientation(-1))
        assertEquals(null, cameraTargetRotationFromOrientation(Int.MAX_VALUE))
    }

    @Test
    fun `only the front lens receives the round selfie presentation`() {
        assertEquals(true, isSelfieLens(CameraSelector.LENS_FACING_FRONT))
        assertEquals(false, isSelfieLens(CameraSelector.LENS_FACING_BACK))
        assertEquals(false, isSelfieLens(CameraSelector.LENS_FACING_EXTERNAL))
    }
}
