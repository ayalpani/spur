package app.spur

import android.view.MotionEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun TourSummaryPlayer(
    tourId: Long,
    distanceMeters: Double,
    elapsedMillis: Long,
    modifier: Modifier = Modifier,
) {
    val controlColors = LocalMapControlColors.current.inverted
    var showTrackingTime by rememberSaveable(tourId) { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .mapControlShadow(CircleShape)
            .clickable(
                onClickLabel = if (showTrackingTime) {
                    "Distanz anzeigen"
                } else {
                    "Tour-Dauer anzeigen"
                },
            ) {
                showTrackingTime = !showTrackingTime
            },
        color = controlColors.background,
        contentColor = controlColors.foreground,
        shape = CircleShape,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = tourProgressPlayerText(
                    distanceMeters = distanceMeters,
                    elapsedMillis = elapsedMillis,
                    showTrackingTime = showTrackingTime,
                ),
                color = controlColors.foreground,
                fontSize = if (showTrackingTime) 18.sp else 28.sp,
                fontWeight = if (showTrackingTime) {
                    FontWeight.Normal
                } else {
                    FontWeight.SemiBold
                },
                maxLines = 1,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun TourPlayer(
    tour: Tour,
    routePoints: List<TrackPoint>,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controlColors = LocalMapControlColors.current.inverted
    var armed by remember(tour.id) { mutableStateOf(false) }
    var showRecentSpeed by rememberSaveable(tour.id) { mutableStateOf(false) }
    var dragOffset by remember(tour.id) { mutableFloatStateOf(0f) }
    var dragStartX by remember(tour.id) { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val mainControlHeight = 60.dp
    val playerText = remember(tour.distanceMeters, routePoints, showRecentSpeed) {
        activeTourPlayerText(
            tour = tour,
            routePoints = routePoints,
            showRecentSpeed = showRecentSpeed,
        )
    }

    Box(
        modifier = modifier.height(mainControlHeight),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(mainControlHeight)
                .mapControlShadow(CircleShape),
            color = Color.Transparent,
            shape = CircleShape,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val handleSize = StopSwipeHandleSize
                val edgePadding = 4.dp
                val maximum = with(density) {
                    (maxWidth - handleSize - edgePadding * 2).toPx().coerceAtLeast(0f)
                }
                val edgePaddingPixels = with(density) { edgePadding.toPx() }
                val swipeProgress = stopSwipeProgress(dragOffset, maximum)
                val stopThresholdReached = shouldCompleteStopSwipe(dragOffset, maximum)
                val swipeColor = lerp(controlColors.background, StopRed, swipeProgress)
                val swipeForeground = lerp(StopRed, Color.White, swipeProgress)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(swipeColor),
                )

                AnimatedContent(
                    targetState = armed,
                    transitionSpec = {
                        fadeIn(tween(160)) togetherWith fadeOut(tween(100))
                    },
                    label = "Stop confirmation",
                    modifier = Modifier.fillMaxSize(),
                ) { confirmationVisible ->
                    if (confirmationVisible) {
                        SwipeStopPrompt(
                            color = swipeForeground,
                            stopThresholdReached = stopThresholdReached,
                            swipePromptAlpha = stopSwipePromptAlpha(dragOffset, maximum),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 68.dp, end = 12.dp)
                                .clickable(
                                    onClickLabel = if (showRecentSpeed) {
                                        "Distanz anzeigen"
                                    } else {
                                        "Geschwindigkeit anzeigen"
                                    },
                                ) {
                                    showRecentSpeed = !showRecentSpeed
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = playerText,
                                color = controlColors.foreground,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset {
                            IntOffset(
                                x = (edgePaddingPixels + dragOffset).roundToInt(),
                                y = 0,
                            )
                        }
                        .size(handleSize)
                        .background(
                            if (armed) {
                                swipeForeground.copy(alpha = 0.14f)
                            } else {
                                controlColors.foreground.copy(alpha = 0.14f)
                            },
                            CircleShape,
                        )
                        .semantics {
                            contentDescription = when {
                                stopThresholdReached ->
                                    "Loslassen, um die Tour zu beenden"
                                armed ->
                                    "Nach rechts wischen, um die Tour zu beenden"
                                else ->
                                    "Tour beenden vorbereiten"
                            }
                        }
                        .pointerInteropFilter { event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    armed = true
                                    dragOffset = 0f
                                    dragStartX = event.rawX
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    dragOffset = (event.rawX - dragStartX)
                                        .coerceIn(0f, maximum)
                                }
                                MotionEvent.ACTION_UP -> {
                                    if (shouldCompleteStopSwipe(dragOffset, maximum)) {
                                        dragOffset = maximum
                                        onStop()
                                    } else {
                                        armed = false
                                        dragOffset = 0f
                                    }
                                }
                                MotionEvent.ACTION_CANCEL -> {
                                    armed = false
                                    dragOffset = 0f
                                }
                            }
                            true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    LucideStopIcon(
                        color = if (armed) swipeForeground else controlColors.foreground,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeStopPrompt(
    color: Color,
    stopThresholdReached: Boolean,
    swipePromptAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!stopThresholdReached) Spacer(modifier = Modifier.width(StopSwipeHandleSize))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (stopThresholdReached) "Stop Tour" else "Swipe right",
                modifier = Modifier.graphicsLayer {
                    alpha = if (stopThresholdReached) 1f else swipePromptAlpha
                },
                color = color,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        if (stopThresholdReached) Spacer(modifier = Modifier.width(StopSwipeHandleSize))
    }
}

@Composable
internal fun LucideStopIcon(
    color: Color,
    contentDescription: String? = "Tour beenden",
    modifier: Modifier = Modifier.size(24.dp),
) = LucideIcon(
    paths = listOf(
        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2",
    ),
    color = color,
    strokeWidth = LucideBoldStrokeWidth,
    modifier = modifier
        .then(
            if (contentDescription == null) {
                Modifier
            } else {
                Modifier.semantics { this.contentDescription = contentDescription }
            },
        ),
)

internal fun formatKilometers(distanceMeters: Double): String =
    String.format(Locale.getDefault(), "%.2f km", distanceMeters / 1_000.0)

internal fun formatMeters(distanceMeters: Double): String =
    String.format(Locale.GERMANY, "%,.0f m", distanceMeters.coerceAtLeast(0.0))

internal fun activeTourPlayerText(
    tour: Tour,
    routePoints: List<TrackPoint>,
    showRecentSpeed: Boolean,
): String = if (showRecentSpeed) {
    formatRecentSpeed(recentSpeedKilometersPerHour(routePoints))
} else {
    formatMeters(tour.distanceMeters)
}

internal fun recentSpeedKilometersPerHour(
    points: List<TrackPoint>,
    distanceWindowMeters: Double = 100.0,
): Double? {
    if (points.size < 2 || distanceWindowMeters <= 0.0) return null
    val ordered = points.sortedBy(TrackPoint::recordedAt)
    val endAt = ordered.last().recordedAt
    var startAt = endAt
    var accumulatedDistance = 0.0
    var newer = ordered.last()
    for (index in ordered.lastIndex - 1 downTo 0) {
        val older = ordered[index]
        val elapsedMillis = newer.recordedAt - older.recordedAt
        if (elapsedMillis <= 0L) {
            newer = older
            continue
        }
        val segmentDistance = haversineDistanceMeters(
            older.latitude,
            older.longitude,
            newer.latitude,
            newer.longitude,
        )
        val remainingDistance = distanceWindowMeters - accumulatedDistance
        if (segmentDistance >= remainingDistance && segmentDistance > 0.0) {
            val fraction = remainingDistance / segmentDistance
            startAt = newer.recordedAt - (elapsedMillis * fraction).roundToLong()
            accumulatedDistance += remainingDistance
            break
        }
        accumulatedDistance += segmentDistance
        startAt = older.recordedAt
        newer = older
    }
    val elapsedMillis = endAt - startAt
    if (accumulatedDistance < 10.0 || elapsedMillis <= 0L) return null
    return accumulatedDistance / (elapsedMillis / 1_000.0) * 3.6
}

internal fun formatRecentSpeed(speedKilometersPerHour: Double?): String =
    speedKilometersPerHour?.let {
        String.format(Locale.GERMANY, "%.1f km/h", it.coerceAtLeast(0.0))
    } ?: "– km/h"

internal fun tourProgressPlayerText(
    distanceMeters: Double,
    elapsedMillis: Long,
    showTrackingTime: Boolean,
): String =
    if (showTrackingTime) formatPlayerDuration(elapsedMillis)
    else formatMeters(distanceMeters)

internal fun formatPlayerDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return buildString {
        if (hours > 0) append("${hours}h ")
        if (hours > 0 || minutes > 0) append("${minutes}m ")
        append("${seconds}s")
    }
}

internal fun formatClock(timestamp: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))

internal fun formatDate(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))

internal fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

internal fun formatTourTime(tour: Tour): String {
    val end = tour.endedAt ?: System.currentTimeMillis()
    return "${formatClock(tour.startedAt)}–${formatClock(end)} · ${
        formatDuration(end - tour.startedAt)
    }"
}
