package app.spur

internal fun formatDurationMinutes(
    durationMillis: Long,
    zeroMinutesLabel: String,
): String {
    val totalMinutes = durationMillis.coerceAtLeast(0L) / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L -> "$hours h ${minutes.toString().padStart(2, '0')} min"
        totalMinutes > 0L -> "$totalMinutes min"
        else -> zeroMinutesLabel
    }
}
