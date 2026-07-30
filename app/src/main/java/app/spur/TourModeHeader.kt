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

@Composable
internal fun TourModeHeader(
    active: Boolean,
    archivedTour: Tour?,
    pulseAlpha: Float,
    visible: Boolean,
    onCloseArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && (active || archivedTour != null),
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
                if (archivedTour != null) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(
                                onClickLabel = "Archiv-Tour schließen",
                                onClick = onCloseArchive,
                            )
                            .semantics {
                                contentDescription = "Archiv-Tour schließen"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        PhotoCloseIcon()
                    }
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = "Archiv-Tour",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = archiveTourMetadata(archivedTour),
                            color = Ink.copy(alpha = 0.62f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(10.dp)
                            .alpha(pulseAlpha)
                            .background(FollowGreen, CircleShape),
                    )
                    Text(
                        text = "Laufende Tour",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            HorizontalDivider(color = Ink.copy(alpha = 0.12f))
        }
    }
}

internal fun archiveTourMetadata(tour: Tour): String {
    val endedAt = tour.endedAt ?: tour.startedAt
    return "${formatDate(tour.startedAt)} · ${formatClock(tour.startedAt)}–" +
        "${formatClock(endedAt)} · ${formatDuration(endedAt - tour.startedAt)}"
}
