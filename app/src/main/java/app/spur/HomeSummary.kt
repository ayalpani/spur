package app.spur

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private const val HomeSummaryDayCount = 7L
private val HomeActivityChartHeight = 88.dp
private val HomeActivityBarWidth = 18.dp

internal data class HomeActivityDay(
    val date: LocalDate,
    val distanceMeters: Double,
)

internal data class HomeWeekSummary(
    val days: List<HomeActivityDay>,
    val tourCount: Int,
    val durationMillis: Long,
    val distanceMeters: Double,
)

@Composable
internal fun HomeWeeklySummary(summary: HomeWeekSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Text(
            text = if (summary.tourCount == 0) {
                "Die Stadt wartet."
            } else {
                "Hey ho, let's go."
            },
            color = Ink.copy(alpha = 0.62f),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = homeWeekHeadline(summary.tourCount),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = homeWeekMetadata(summary),
            modifier = Modifier.padding(top = 4.dp),
            color = Ink.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(24.dp))
        HomeActivityChart(days = summary.days)
    }
}

@Composable
private fun HomeActivityChart(days: List<HomeActivityDay>) {
    val maximumDistance = days.maxOfOrNull(HomeActivityDay::distanceMeters) ?: 0.0
    val description = days.joinToString(
        prefix = "Aktivität der letzten sieben Tage: ",
        separator = ", ",
    ) { day ->
        val dayName = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN)
        "$dayName ${formatHomeDistance(day.distanceMeters)}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        days.forEachIndexed { index, day ->
            val fraction = if (maximumDistance > 0.0) {
                (day.distanceMeters / maximumDistance).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .height(HomeActivityChartHeight)
                        .width(HomeActivityBarWidth)
                        .clip(RoundedCornerShape(HomeActivityBarWidth / 2))
                        .background(Ink.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction.coerceAtLeast(0.08f))
                                .background(Ink),
                        )
                    }
                }
                Text(
                    text = day.date.dayOfWeek
                        .getDisplayName(TextStyle.SHORT, Locale.GERMAN)
                        .take(2),
                    modifier = Modifier.padding(top = 8.dp),
                    color = Ink.copy(alpha = if (index == days.lastIndex) 1f else 0.5f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (index == days.lastIndex) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                )
            }
        }
    }
}

internal fun homeWeekSummary(
    tours: List<Tour>,
    now: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): HomeWeekSummary {
    val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
    val firstDay = today.minusDays(HomeSummaryDayCount - 1L)
    val dates = (0L until HomeSummaryDayCount).map(firstDay::plusDays)
    val completedTours = tours.filter { tour ->
        val startedDate = Instant.ofEpochMilli(tour.startedAt).atZone(zoneId).toLocalDate()
        tour.endedAt != null && !startedDate.isBefore(firstDay) && !startedDate.isAfter(today)
    }
    val distanceByDate = completedTours.groupBy { tour ->
        Instant.ofEpochMilli(tour.startedAt).atZone(zoneId).toLocalDate()
    }.mapValues { (_, datedTours) ->
        datedTours.sumOf { it.distanceMeters.coerceAtLeast(0.0) }
    }
    return HomeWeekSummary(
        days = dates.map { date ->
            HomeActivityDay(date, distanceByDate[date] ?: 0.0)
        },
        tourCount = completedTours.size,
        durationMillis = completedTours.sumOf { tour ->
            (tour.endedAt!! - tour.startedAt).coerceAtLeast(0L)
        },
        distanceMeters = completedTours.sumOf { it.distanceMeters.coerceAtLeast(0.0) },
    )
}

internal fun homeWeekHeadline(tourCount: Int): String =
    if (tourCount == 1) {
        "1 Tour in $HomeSummaryDayCount Tagen"
    } else {
        "$tourCount Touren in $HomeSummaryDayCount Tagen"
    }

internal fun homeWeekMetadata(summary: HomeWeekSummary): String {
    val duration = if (summary.tourCount == 0) {
        "0 min"
    } else {
        formatHistoryDuration(summary.durationMillis)
    }
    return "$duration unterwegs · ${formatHomeDistance(summary.distanceMeters)}"
}

internal fun formatHomeDistance(distanceMeters: Double): String =
    if (distanceMeters >= 1_000.0) {
        String.format(Locale.GERMANY, "%.1f km", distanceMeters / 1_000.0)
    } else {
        String.format(Locale.GERMANY, "%.0f m", distanceMeters.coerceAtLeast(0.0))
    }
