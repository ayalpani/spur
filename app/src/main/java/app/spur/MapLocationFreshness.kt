package app.spur

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val RecenterFixMaximumAgeNanos = 3_000_000_000L
private const val RecenterFixWaitMillis = 1_000L

internal fun isNewerMapLocation(candidateNanos: Long, previousNanos: Long?): Boolean =
    candidateNanos > 0L && (previousNanos == null || candidateNanos > previousNanos)

internal fun isFreshMapLocation(fixNanos: Long?, nowNanos: Long): Boolean =
    fixNanos != null && fixNanos > 0L &&
        nowNanos - fixNanos in 0L..RecenterFixMaximumAgeNanos

// Reuse the map's live GPS subscription; a timeout keeps centering usable indoors.
internal suspend fun awaitFreshMapLocation(
    fixTimes: Flow<Long?>,
    nowNanos: () -> Long,
) {
    withTimeoutOrNull(RecenterFixWaitMillis) {
        fixTimes.first { isFreshMapLocation(it, nowNanos()) }
    }
}
