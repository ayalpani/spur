package app.spur

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoDetailTransitionTest {
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
