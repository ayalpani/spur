package app.spur

internal enum class HomeDepartureTrackingMode {
    STOPPED,
    ARMED,
    CONFIRMING,
    ACTIVE,
}

internal enum class HomeDepartureLocationCapture {
    NONE,
    HIGH_ACCURACY,
}

internal fun restoredHomeDepartureMode(
    hasActiveTour: Boolean,
    automationEnabled: Boolean,
    candidateAt: Long?,
    now: Long,
): HomeDepartureTrackingMode = when {
    hasActiveTour -> HomeDepartureTrackingMode.ACTIVE
    !automationEnabled -> HomeDepartureTrackingMode.STOPPED
    candidateAt != null && candidateAt <= now &&
        now - candidateAt < DepartureConfirmationTimeoutMillis ->
        HomeDepartureTrackingMode.CONFIRMING
    else -> HomeDepartureTrackingMode.ARMED
}

internal fun HomeDepartureTrackingMode.afterMotion(): HomeDepartureTrackingMode =
    if (this == HomeDepartureTrackingMode.ARMED) {
        HomeDepartureTrackingMode.CONFIRMING
    } else {
        this
    }

internal fun idleHomeDepartureMode(
    automationEnabled: Boolean,
): HomeDepartureTrackingMode = if (automationEnabled) {
    HomeDepartureTrackingMode.ARMED
} else {
    HomeDepartureTrackingMode.STOPPED
}

internal fun locationCaptureFor(
    mode: HomeDepartureTrackingMode,
): HomeDepartureLocationCapture = when (mode) {
    HomeDepartureTrackingMode.CONFIRMING,
    HomeDepartureTrackingMode.ACTIVE,
    -> HomeDepartureLocationCapture.HIGH_ACCURACY

    HomeDepartureTrackingMode.STOPPED,
    HomeDepartureTrackingMode.ARMED,
    -> HomeDepartureLocationCapture.NONE
}

internal fun orderedConfirmationSamples(
    existing: Collection<BufferedHomeLocation>,
    incoming: Collection<BufferedHomeLocation>,
): List<BufferedHomeLocation> = (existing + incoming)
    .distinctBy { Triple(it.recordedAt, it.latitude, it.longitude) }
    .sortedBy(BufferedHomeLocation::recordedAt)
    .takeLast(HomeConfirmationSampleCount)

internal class AutomaticStartDuplicateIndex(points: Collection<TrackPoint>) {
    private val pointsBySecond = mutableMapOf<Long, MutableList<TrackPoint>>()

    init {
        points.forEach(::add)
    }

    fun contains(location: BufferedHomeLocation): Boolean {
        val second = Math.floorDiv(location.recordedAt, 1_000L)
        return (second - 1..second + 1).any { candidateSecond ->
            pointsBySecond[candidateSecond].orEmpty().any { point ->
                kotlin.math.abs(point.recordedAt - location.recordedAt) <= 1_000L &&
                    haversineDistanceMeters(
                        point.latitude,
                        point.longitude,
                        location.latitude,
                        location.longitude,
                    ) <= 2.0
            }
        }
    }

    fun add(point: TrackPoint) {
        pointsBySecond
            .getOrPut(Math.floorDiv(point.recordedAt, 1_000L)) { mutableListOf() }
            .add(point)
    }
}
