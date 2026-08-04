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
