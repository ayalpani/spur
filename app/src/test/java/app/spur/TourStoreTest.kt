package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun accurateGpsFixStartsImmediately() {
        val gate = GpsStartGate()

        assertTrue(gate.isReady(fix(latitude = 52.52000, accuracy = 8f), observedAtMillis = 0L))
    }

    @Test
    fun usableGpsFixStartsAfterShortFallbackDelay() {
        val gate = GpsStartGate()

        assertFalse(gate.isReady(fix(latitude = 52.52000, accuracy = 24f), observedAtMillis = 0L))
        assertFalse(gate.isReady(fix(latitude = 52.52005, accuracy = 24f), observedAtMillis = 9_999L))
        assertTrue(gate.isReady(fix(latitude = 52.52010, accuracy = 24f), observedAtMillis = 10_000L))
    }

    @Test
    fun unusableGpsFixNeverStartsTour() {
        val gate = GpsStartGate()

        assertFalse(gate.isReady(fix(latitude = 52.52000, accuracy = 80f), observedAtMillis = 0L))
        assertFalse(gate.isReady(fix(latitude = 52.52005, accuracy = 80f), observedAtMillis = 60_000L))
    }

    @Test
    fun fiveMinuteStationaryWindowBecomesOneCluster() {
        val cluster = stationaryCluster(
            listOf(
                point(1, 52.52000, 0L),
                point(2, 52.52020, 150_000L),
                point(3, 52.52010, 300_000L),
            ),
        )

        assertNotNull(cluster)
        assertEquals(52.52010, cluster!!.latitude, 0.000_001)
        assertEquals(3, cluster.sampleCount)
        assertEquals(0L, cluster.startedAt)
        assertEquals(300_000L, cluster.recordedAt)
    }

    @Test
    fun movingMoreThanOneHundredMetersDoesNotCluster() {
        assertNull(
            stationaryCluster(
                listOf(
                    point(1, 52.52000, 0L),
                    point(2, 52.52010, 150_000L),
                    point(3, 52.52100, 300_000L),
                ),
            ),
        )
    }

    private fun fix(
        latitude: Double,
        accuracy: Float,
    ) = GpsStartFix(
        latitude = latitude,
        longitude = 13.405,
        accuracyMeters = accuracy,
    )

    private fun point(id: Long, latitude: Double, recordedAt: Long) =
        TrackPoint(id, latitude, 13.405, recordedAt)
}
