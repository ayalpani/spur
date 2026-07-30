package app.spur

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class TourModeHeaderTest {
    @Test
    fun normalMapIsNotReportedAsRunningTour() {
        assertFalse(isDisplayedActiveTour(tour = null, activeTour = null))
    }

    @Test
    fun matchingActiveTourIsReportedAsRunningTour() {
        val tour = Tour(
            id = 7L,
            startedAt = 1_000L,
            endedAt = null,
            distanceMeters = 0.0,
            pointCount = 0,
        )

        assertTrue(isDisplayedActiveTour(tour = tour, activeTour = tour))
    }

    @Test
    fun durationIsNotFormattedLikeAnotherClockTime() {
        assertEquals("18 min 11 s", formatTourHeaderDuration(18 * 60_000L + 11_000L))
    }

    @Test
    fun metadataSeparatesTimeRangeFromLabeledDuration() {
        val startedAt = Instant.parse("2026-07-30T01:55:00Z").toEpochMilli()
        val endedAt = startedAt + 18 * 60_000L + 11_000L
        val tour = Tour(
            id = 8L,
            startedAt = startedAt,
            endedAt = endedAt,
            distanceMeters = 0.0,
            pointCount = 0,
        )

        assertEquals(
            "30. Juli 2026 · 01:55–02:13 Uhr\nDauer 18 min 11 s",
            tourHeaderMetadata(tour, now = endedAt, zoneId = ZoneOffset.UTC),
        )
    }
}
