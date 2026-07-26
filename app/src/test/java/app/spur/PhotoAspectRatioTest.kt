package app.spur

import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoAspectRatioTest {
    @Test
    fun swapsDimensionsForQuarterTurnExifOrientations() {
        assertEquals(
            0.75f,
            orientedPhotoAspectRatio(4_000, 3_000, ExifInterface.ORIENTATION_ROTATE_90),
        )
        assertEquals(
            0.75f,
            orientedPhotoAspectRatio(4_000, 3_000, ExifInterface.ORIENTATION_TRANSVERSE),
        )
    }

    @Test
    fun keepsDimensionsForUnrotatedExifOrientations() {
        assertEquals(
            4f / 3f,
            orientedPhotoAspectRatio(4_000, 3_000, ExifInterface.ORIENTATION_NORMAL),
        )
    }

    @Test
    fun fourLeftRotationsReturnToTheOriginalExifOrientation() {
        var orientation = ExifInterface.ORIENTATION_NORMAL

        repeat(4) {
            orientation = exifOrientationAfterLeftRotation(orientation)
        }

        assertEquals(ExifInterface.ORIENTATION_NORMAL, orientation)
    }
}
