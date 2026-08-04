package app.spur

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSummaryTest {
    private val zone = ZoneId.of("Europe/Berlin")

    @Test
    fun `summary contains only completed tours from the last seven calendar days`() {
        val now = time(2026, 8, 3, 18, 0)
        val summary = homeWeekSummary(
            tours = listOf(
                tour(1, time(2026, 8, 3, 10, 0), durationMinutes = 60, distance = 10_000.0),
                tour(2, time(2026, 8, 1, 10, 0), durationMinutes = 90, distance = 5_500.0),
                tour(3, time(2026, 7, 27, 10, 0), durationMinutes = 30, distance = 2_000.0),
                tour(4, time(2026, 8, 2, 10, 0), durationMinutes = null, distance = 3_000.0),
            ),
            now = now,
            zoneId = zone,
        )

        assertEquals(2, summary.tourCount)
        assertEquals(150 * 60_000L, summary.durationMillis)
        assertEquals(15_500.0, summary.distanceMeters, 0.0)
        assertEquals(7, summary.days.size)
        assertEquals(5_500.0, summary.days[4].distanceMeters, 0.0)
        assertEquals(10_000.0, summary.days.last().distanceMeters, 0.0)
    }

    @Test
    fun `headline handles singular and plural`() {
        assertEquals("1 Tour in 7 Tagen", homeWeekHeadline(1))
        assertEquals("4 Touren in 7 Tagen", homeWeekHeadline(4))
    }

    @Test
    fun `empty week metadata starts at zero minutes`() {
        assertEquals(
            "0 min unterwegs · 0 m",
            homeWeekMetadata(
                HomeWeekSummary(
                    days = emptyList(),
                    tourCount = 0,
                    durationMillis = 0,
                    distanceMeters = 0.0,
                ),
            ),
        )
    }

    private fun tour(
        id: Long,
        startedAt: Long,
        durationMinutes: Long?,
        distance: Double,
    ) = Tour(
        id = id,
        startedAt = startedAt,
        endedAt = durationMinutes?.let { startedAt + it * 60_000L },
        distanceMeters = distance,
        pointCount = 0,
    )

    private fun time(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = LocalDateTime.of(year, month, day, hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}
