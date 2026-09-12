package app.spur

import org.junit.Assert.*
import org.junit.Test

class RawLocationDiagnosticsTest {
    @Test
    fun reportsTheFirstApplicableFilterWithoutChangingAcceptance() {
        data class Case(val accuracy: Float, val distance: Float?, val elapsed: Long?, val reason: RawLocationDecision?)
        val cases = listOf(
            Case(41f, 100f, 10_000L, RawLocationDecision.POOR_ACCURACY),
            Case(Float.POSITIVE_INFINITY, null, null, RawLocationDecision.POOR_ACCURACY),
            Case(4f, 10f, 0L, RawLocationDecision.NON_MONOTONIC_TIME),
            Case(4f, 56f, 1_000L, RawLocationDecision.IMPLAUSIBLE_SPEED),
            Case(4f, 3f, 2_000L, RawLocationDecision.BELOW_NOISE_FLOOR),
            Case(40f, 10f, 2_000L, null),
            Case(4f, 55f, 1_000L, null),
            Case(4f, null, null, null),
            Case(39.02f, 2_090f, 461_164L, null),
        )
        cases.forEach {
            assertEquals(it.reason, pointRejectionReason(it.accuracy, it.distance, it.elapsed))
            assertEquals(it.reason == null, shouldAcceptPoint(it.accuracy, it.distance, it.elapsed))
        }
        assertNull(pointRejectionReason(3f, 1_000f, 1L, allowFastMovement = true))
        assertFalse(RawLocationDecision.STATIONARY_EXIT_PENDING.accepted)
        assertTrue(RawLocationDecision.STATIONARY_MERGED.accepted)
    }
}
