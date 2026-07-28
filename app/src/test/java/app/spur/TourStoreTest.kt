package app.spur

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TourStoreTest {
    @Test
    fun pointFilterRejectsNoiseAndImpossibleJumps() {
        assertTrue(shouldAcceptPoint(8f, null, null))
        assertFalse(shouldAcceptPoint(41f, null, null))
        assertFalse(shouldAcceptPoint(8f, 3f, 5_000L))
        assertFalse(shouldAcceptPoint(8f, 600f, 5_000L))
        assertTrue(shouldAcceptPoint(8f, 600f, 5_000L, allowFastMovement = true))
        assertTrue(shouldAcceptPoint(8f, 12f, 5_000L))
    }

    @Test
    fun gpsStartRequiresThreeAccurateClusteredFixes() {
        val stabilizer = GpsStartStabilizer()

        assertFalse(stabilizer.isReady(fix(latitude = 52.52000, accuracy = 8f)))
        assertFalse(stabilizer.isReady(fix(latitude = 52.52005, accuracy = 10f)))
        assertTrue(stabilizer.isReady(fix(latitude = 52.52010, accuracy = 7f)))
    }

    @Test
    fun inaccurateOrDistantFixRestartsGpsStabilization() {
        val stabilizer = GpsStartStabilizer()

        assertFalse(stabilizer.isReady(fix(latitude = 52.52000, accuracy = 8f)))
        assertFalse(stabilizer.isReady(fix(latitude = 52.52005, accuracy = 13f)))
        assertFalse(stabilizer.isReady(fix(latitude = 52.52010, accuracy = 8f)))
        assertFalse(stabilizer.isReady(fix(latitude = 52.52100, accuracy = 8f)))
        assertFalse(stabilizer.isReady(fix(latitude = 52.52105, accuracy = 8f)))
        assertTrue(stabilizer.isReady(fix(latitude = 52.52110, accuracy = 8f)))
    }

    private fun fix(
        latitude: Double,
        accuracy: Float,
    ) = GpsStartFix(
        latitude = latitude,
        longitude = 13.405,
        accuracyMeters = accuracy,
    )
}
