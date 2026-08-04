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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
                    .height(60.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (active) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .size(10.dp)
                                    .alpha(pulseAlpha)
                                    .background(FollowGreen, CircleShape),
                            )
                        }
                        Text(
                            text = if (active) "Laufende Tour" else "Archiv-Tour",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!active) {
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = historySectionLabel(tour.startedAt, now),
                                color = Ink.copy(alpha = 0.62f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = Ink.copy(alpha = 0.12f))
        }
    }
}

internal fun isDisplayedActiveTour(tour: Tour?, activeTour: Tour?): Boolean =
    tour != null && activeTour != null && tour.id == activeTour.id && tour.endedAt == null
