package app.spur

import org.junit.Assert.assertEquals
import org.junit.Test

class LoadingAnimationTest {
    @Test
    fun asteriskAccelerationStartsStillAndBuildsQuadratically() {
        assertEquals(0f, loaderAsteriskAcceleration(0f))
        assertEquals(0.25f, loaderAsteriskAcceleration(0.5f))
        assertEquals(1f, loaderAsteriskAcceleration(1f))
    }
}
