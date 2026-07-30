package app.spur

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun TourModeHeader(
    tour: Tour?,
    active: Boolean,
    now: Long,
    pulseAlpha: Float,
    visible: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && tour != null,
        modifier = modifier,
        enter = slideInVertically(
            animationSpec = tween(MotionDurationDefaultMillis),
            initialOffsetY = { -it },
        ) + fadeIn(tween(MotionDurationDefaultMillis)),
        exit = slideOutVertically(
            animationSpec = tween(MotionDurationDefaultMillis),
            targetOffsetY = { -it },
        ) + fadeOut(tween(MotionDurationDefaultMillis)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SheetBackground),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(
                            onClickLabel = "Tour-Ansicht schließen",
                            onClick = onClose,
                        )
                        .semantics {
                            contentDescription = "Tour-Ansicht schließen"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    PhotoCloseIcon()
                }
                if (tour != null) {
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (active) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(10.dp)
                                        .alpha(pulseAlpha)
                                        .background(FollowGreen, CircleShape),
                                )
                            }
                            Text(
                                text = if (active) "Laufende Tour" else "Archiv-Tour",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            text = tourHeaderMetadata(tour, now),
                            color = Ink.copy(alpha = 0.62f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            HorizontalDivider(color = Ink.copy(alpha = 0.12f))
        }
    }
}

internal fun tourHeaderMetadata(
    tour: Tour,
    now: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val endedAt = tour.endedAt ?: now
    val formatter = DateTimeFormatter.ofPattern("d. MMMM yyyy · HH:mm", Locale.GERMAN)
    val started = Instant.ofEpochMilli(tour.startedAt).atZone(zoneId)
    val endTime = Instant.ofEpochMilli(endedAt)
        .atZone(zoneId)
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN))
    return "${started.format(formatter)}–$endTime Uhr\n" +
        "Dauer ${formatTourHeaderDuration(endedAt - tour.startedAt)}"
}

internal fun formatTourHeaderDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return buildString {
        if (hours > 0L) append("$hours h ")
        append("$minutes min $seconds s")
    }
}

internal fun isDisplayedActiveTour(tour: Tour?, activeTour: Tour?): Boolean =
    tour != null && activeTour != null && tour.id == activeTour.id && tour.endedAt == null
