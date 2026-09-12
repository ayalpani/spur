package app.spur

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.location.Location

// Decisions describe ingestion, not whether a point survives later pause clustering/editing.
internal enum class RawLocationDecision(val accepted: Boolean = false) {
    ACCEPTED(true),
    STATIONARY_MERGED(true),
    POOR_ACCURACY,
    NON_MONOTONIC_TIME,
    IMPLAUSIBLE_SPEED,
    BELOW_NOISE_FLOOR,
    STARTUP_PENDING,
    STATIONARY_EXIT_PENDING,
    INACTIVE_TOUR,
    STALE_SESSION,
    MANUAL_LOCATION,
    PRE_ROLL_ACCEPTED(true),
    PRE_ROLL_DUPLICATE,
}

internal fun pointRejectionReason(
    accuracyMeters: Float,
    distanceMeters: Float?,
    elapsedMillis: Long?,
    allowFastMovement: Boolean = false,
): RawLocationDecision? {
    if (accuracyMeters > 40f) return RawLocationDecision.POOR_ACCURACY
    if (distanceMeters == null || elapsedMillis == null) return null
    if (elapsedMillis <= 0L) return RawLocationDecision.NON_MONOTONIC_TIME
    if (!allowFastMovement && distanceMeters / (elapsedMillis / 1_000f) > 55f) {
        return RawLocationDecision.IMPLAUSIBLE_SPEED
    }
    val noiseFloor = (accuracyMeters * 0.5f).coerceIn(4f, 10f)
    return if (distanceMeters >= noiseFloor) null else RawLocationDecision.BELOW_NOISE_FLOOR
}

internal const val RawLocationRetentionMillis = 14 * 24 * 60 * 60 * 1_000L

internal fun SQLiteDatabase.ensureRawLocationTable() {
    // Additive diagnostics only: the established v4 tours/track_points contract stays intact.
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS raw_locations (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            tour_id INTEGER NOT NULL,
            recorded_at INTEGER NOT NULL,
            received_at INTEGER NOT NULL,
            latitude REAL NOT NULL,
            longitude REAL NOT NULL,
            accuracy_meters REAL,
            elapsed_realtime_nanos INTEGER,
            provider TEXT,
            decision TEXT NOT NULL
        )
        """.trimIndent(),
    )
    execSQL("CREATE INDEX IF NOT EXISTS raw_locations_tour ON raw_locations(tour_id, id)")
    execSQL("CREATE INDEX IF NOT EXISTS raw_locations_age ON raw_locations(received_at)")
}

internal fun SQLiteDatabase.pruneRawLocations(now: Long = System.currentTimeMillis()) {
    delete("raw_locations", "received_at < ?", arrayOf((now - RawLocationRetentionMillis).toString()))
}

internal fun SQLiteDatabase.recordRawLocation(
    tourId: Long,
    location: Location,
    decision: RawLocationDecision,
    receivedAt: Long = System.currentTimeMillis(),
) {
    // A callback queued before deletion must not recreate private data for a deleted tour.
    val exists = rawQuery("SELECT 1 FROM tours WHERE id = ?", arrayOf(tourId.toString())).use {
        it.moveToFirst()
    }
    if (!exists) return
    insertOrThrow(
        "raw_locations",
        null,
        ContentValues().apply {
            put("tour_id", tourId)
            put("recorded_at", location.time)
            put("received_at", receivedAt)
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            if (location.hasAccuracy()) put("accuracy_meters", location.accuracy)
            if (location.elapsedRealtimeNanos > 0L) {
                put("elapsed_realtime_nanos", location.elapsedRealtimeNanos)
            }
            put("provider", location.provider)
            put("decision", decision.name)
        },
    )
}
