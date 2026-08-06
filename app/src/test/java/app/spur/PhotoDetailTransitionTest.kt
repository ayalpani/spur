package app.spur

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoDetailTransitionTest {
    @Test
    fun locationMapExpandsFromPreviewBoundsToFullScreen() {
        val source = Rect(left = 0f, top = 100f, right = 100f, bottom = 200f)

        assertEquals(
            PhotoLocationMapTransform(0f, 100f, 0.1f, 0.05f),
            photoLocationMapTransform(source, 1_000f, 2_000f, 0f),
        )
        assertEquals(
            PhotoLocationMapTransform(0f, 0f, 1f, 1f),
            photoLocationMapTransform(source, 1_000f, 2_000f, 1f),
        )
    }

    @Test
    fun previewRemainsUntilOpeningAnimationAndFullImageLoadAreComplete() {
        assertTrue(
            shouldShowOpeningPhotoPreview(
                animationProgress = 0.5f,
                openingPhotoId = "photo-1",
                selectedPhotoId = "photo-1",
                fullImageLoaded = true,
            ),
        )
        assertTrue(
            shouldShowOpeningPhotoPreview(
                animationProgress = 1f,
                openingPhotoId = "photo-1",
                selectedPhotoId = "photo-1",
                fullImageLoaded = false,
            ),
        )
        assertFalse(
            shouldShowOpeningPhotoPreview(
                animationProgress = 1f,
                openingPhotoId = "photo-1",
                selectedPhotoId = "photo-1",
                fullImageLoaded = true,
            ),
        )
    }

    @Test
    fun previewDoesNotCoverAnotherPagerPhoto() {
        assertFalse(
            shouldShowOpeningPhotoPreview(
                animationProgress = 1f,
                openingPhotoId = "photo-1",
                selectedPhotoId = "photo-2",
                fullImageLoaded = false,
            ),
        )
    }
}
