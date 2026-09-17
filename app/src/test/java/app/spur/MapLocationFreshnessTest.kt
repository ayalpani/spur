package app.spur

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLocationFreshnessTest {
    @Test
    fun lateCachedAndDuplicateFixesCannotReplaceLiveGps() {
        assertTrue(isNewerMapLocation(200L, null))
        assertTrue(isNewerMapLocation(201L, 200L))
        assertFalse(isNewerMapLocation(100L, 200L))
        assertFalse(isNewerMapLocation(200L, 200L))
        assertFalse(isNewerMapLocation(0L, null))
    }

    @Test
    fun freshnessUsesMonotonicMeasurementTime() {
        assertTrue(isFreshMapLocation(7_000_000_000L, 10_000_000_000L))
        assertFalse(isFreshMapLocation(6_999_999_999L, 10_000_000_000L))
        assertFalse(isFreshMapLocation(11_000_000_000L, 10_000_000_000L))
        assertFalse(isFreshMapLocation(null, 10_000_000_000L))
    }

    @Test
    fun freshFixCentersImmediatelyAndStaleFixWaitsForLiveGps() = runBlocking {
        val fixes = MutableStateFlow<Long?>(10_000_000_000L)
        withTimeout(500L) { awaitFreshMapLocation(fixes) { 10_000_000_000L } }
        fixes.value = 1L
        val waiting = async { awaitFreshMapLocation(fixes) { 10_000_000_000L } }
        delay(30L)
        assertFalse(waiting.isCompleted)
        fixes.value = 10_000_000_000L
        withTimeout(500L) { waiting.await() }
    }

    @Test
    fun missingGpsHasABoundedWaitAndCanceledRequestsStayCanceled() = runBlocking {
        val fixes = MutableStateFlow<Long?>(null)
        withTimeout(2_000L) { awaitFreshMapLocation(fixes) { 10_000_000_000L } }
        val waiting = async { awaitFreshMapLocation(fixes) { 10_000_000_000L } }
        delay(30L)
        waiting.cancel()
        waiting.join()
        fixes.value = 10_000_000_000L
        assertTrue(waiting.isCancelled)
    }
}
