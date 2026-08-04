package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HistoryGroupingTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 7, 29)
    private val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun recentToursUseRelativeAndWeekdaySections() {
        assertEquals("Heute", section(0))
        assertEquals("Gestern", section(1))
        assertEquals("Montag", section(2))
        assertEquals("Donnerstag", section(6))
    }

    @Test
    fun olderToursUseDatesThenMonths() {
        assertEquals("22. Juli", section(7))
        assertEquals("30. Juni", section(29))
        assertEquals("Juni 2026", section(30))
    }

    @Test
    fun relativeSectionsAlsoShowTheirConcreteDate() {
        assertEquals("29. Juli", dateLabel(0))
        assertEquals("28. Juli", dateLabel(1))
        assertEquals("27. Juli", dateLabel(2))
        assertEquals(null, dateLabel(7))
        assertEquals(null, dateLabel(30))
    }

    @Test
    fun historyDurationStaysCompact() {
        assertEquals("< 1 min", formatHistoryDuration(15_000L))
        assertEquals("42 min", formatHistoryDuration(42 * 60_000L))
        assertEquals("1 h 08 min", formatHistoryDuration(68 * 60_000L))
    }

    @Test
    fun startPlaceReuseStopsAfterOneHundredMeters() {
        assertTrue(shouldReuseStartPlace(100f))
        assertFalse(shouldReuseStartPlace(100.01f))
    }

    @Test
    fun equalPlacesCollapseAndDifferentPlacesFormARoute() {
        assertEquals(
            "Münster",
            TourPlaceMetadata("Münster", "münster").displayName,
        )
        assertEquals(
            "Münster → Telgte",
            TourPlaceMetadata("Münster", "Telgte").displayName,
        )
    }

    private fun section(ageDays: Long): String {
        return historySectionLabel(timestamp(ageDays), now, zone)
    }

    private fun dateLabel(ageDays: Long): String? =
        historySectionDateLabel(timestamp(ageDays), now, zone)

    private fun timestamp(ageDays: Long): Long =
        today
            .minusDays(ageDays)
            .atTime(12, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
}
