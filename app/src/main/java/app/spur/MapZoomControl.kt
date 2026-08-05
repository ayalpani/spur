package app.spur

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val MapZoomMinimum = 1.0
internal const val MapZoomMaximum = 20.0
internal const val SatelliteMapZoomMaximum = 19.0
internal const val MapZoomButtonAnimationMillis = 180
private const val MapZoomNumberLingerMillis = 1_000L
private const val MapZoomDragDpPerLevel = 56f
private val MapZoomButtonHeight = 52.dp
private val MapZoomMiddleHeight = 32.dp
private val MapZoomControlHeight = 136.dp

internal data class MapZoomRequest(
    val id: Long,
    val zoom: Double,
    val animated: Boolean,
)

internal fun mapZoomMaximum(satellite: Boolean): Double =
    if (satellite) SatelliteMapZoomMaximum else MapZoomMaximum

internal fun normalizedMapZoom(zoom: Double, satellite: Boolean): Double =
    zoom.coerceIn(MapZoomMinimum, mapZoomMaximum(satellite))

internal fun mapZoomAfterDrag(
    startZoom: Double,
    upwardDragPixels: Float,
    pixelsPerLevel: Float,
    maximumZoom: Double = MapZoomMaximum,
): Double {
    if (pixelsPerLevel <= 0f) return startZoom.coerceIn(MapZoomMinimum, maximumZoom)
    return (startZoom + upwardDragPixels / pixelsPerLevel)
        .coerceIn(MapZoomMinimum, maximumZoom)
}

internal fun steppedMapZoom(
    zoom: Double,
    direction: Int,
    maximumZoom: Double = MapZoomMaximum,
): Double = (zoom + direction).coerceIn(MapZoomMinimum, maximumZoom)

internal fun displayedMapZoomLevel(
    zoom: Double,
    maximumZoom: Double = MapZoomMaximum,
): Int = zoom.roundToInt().coerceIn(MapZoomMinimum.toInt(), maximumZoom.toInt())

internal fun isDefaultMapZoomLevel(
    zoom: Double,
    defaultZoom: Double,
    maximumZoom: Double = MapZoomMaximum,
): Boolean = displayedMapZoomLevel(zoom, maximumZoom) ==
    displayedMapZoomLevel(defaultZoom, maximumZoom)

internal fun selectedDefaultMapZoom(
    zoom: Double,
    maximumZoom: Double = MapZoomMaximum,
): Double = displayedMapZoomLevel(zoom, maximumZoom).toDouble()

@Composable
internal fun MapZoomControl(
    zoom: Double,
    defaultZoom: Double,
    maximumZoom: Double,
    isInteractionActive: Boolean,
    onZoomChange: (zoom: Double, animated: Boolean) -> Unit,
    onDefaultZoomSelected: (Double) -> Unit,
    onInteractionActiveChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedColors = LocalMapControlColors.current
    val idleStyle = secondaryMapControlStyle(selectedColors)
    val targetColors = if (isInteractionActive) selectedColors else idleStyle.colors
    val backgroundColor by animateColorAsState(
        targetValue = targetColors.background,
        animationSpec = tween(MotionDurationDefaultMillis),
        label = "Zoom control background",
    )
    val foregroundColor by animateColorAsState(
        targetValue = targetColors.foreground,
        animationSpec = tween(MotionDurationDefaultMillis),
        label = "Zoom control foreground",
    )
    val shape = RoundedCornerShape(percent = 50)
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val currentHapticFeedback by rememberUpdatedState(hapticFeedback)
    val currentZoom by rememberUpdatedState(zoom)
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)
    val currentOnInteractionActiveChanged by rememberUpdatedState(onInteractionActiveChanged)
    val pixelsPerLevel = with(density) { MapZoomDragDpPerLevel.dp.toPx() }
    val coroutineScope = rememberCoroutineScope()
    var showZoomNumber by remember { mutableStateOf(false) }
    var zoomNumberHideJob by remember { mutableStateOf<Job?>(null) }
    val displayedZoomLevel = displayedMapZoomLevel(zoom, maximumZoom)
    val isDefaultZoom = isDefaultMapZoomLevel(zoom, defaultZoom, maximumZoom)
    val zoomNumberAlpha by animateFloatAsState(
        targetValue = if (showZoomNumber) 1f else 0f,
        animationSpec = tween(MotionDurationDefaultMillis),
        label = "Zoom number alpha",
    )
    Surface(
        modifier = modifier
            .width(MapControlSize)
            .height(MapZoomControlHeight)
            .mapControlShadow(shape)
            .semantics {
                contentDescription = "Kartenzoom"
                stateDescription = buildString {
                    append("Zoomstufe ${zoom.roundToInt()}")
                    if (isDefaultZoom) append(", Standardzoom")
                }
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = zoom.toFloat(),
                    range = MapZoomMinimum.toFloat()..maximumZoom.toFloat(),
                    steps = 0,
                )
            }
            .pointerInput(pixelsPerLevel, maximumZoom) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    zoomNumberHideJob?.cancel()
                    showZoomNumber = true
                    currentOnInteractionActiveChanged(true)
                    currentHapticFeedback.performHapticFeedback(
                        HapticFeedbackType.TextHandleMove,
                    )
                    val startZoom = currentZoom
                    var latestZoom = startZoom
                    var dragging = false
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: break
                        val upwardDrag = down.position.y - change.position.y
                        if (!dragging && abs(upwardDrag) > viewConfiguration.touchSlop) {
                            dragging = true
                        }
                        if (dragging && change.pressed) {
                            change.consume()
                            latestZoom = mapZoomAfterDrag(
                                startZoom = startZoom,
                                upwardDragPixels = upwardDrag,
                                pixelsPerLevel = pixelsPerLevel,
                                maximumZoom = maximumZoom,
                            )
                            currentOnZoomChange(latestZoom, false)
                        }
                    } while (change.pressed)
                    currentOnInteractionActiveChanged(false)
                    zoomNumberHideJob = coroutineScope.launch {
                        delay(MapZoomNumberLingerMillis)
                        showZoomNumber = false
                    }
                    if (dragging) {
                        currentOnZoomChange(latestZoom, false)
                    }
                }
            },
        shape = shape,
        color = backgroundColor,
        contentColor = foregroundColor,
        border = idleStyle.border,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ZoomStepButton(
                contentDescription = "Heranzoomen",
                enabled = zoom < maximumZoom,
                onClick = {
                    val target = steppedMapZoom(
                        zoom = zoom,
                        direction = 1,
                        maximumZoom = maximumZoom,
                    )
                    onZoomChange(target, true)
                },
                modifier = Modifier.height(MapZoomButtonHeight),
            ) {
                ChevronUpIcon()
            }
            Box(
                modifier = Modifier
                    .height(MapZoomMiddleHeight)
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = if (isDefaultZoom) {
                            "Zoomstufe anzeigen"
                        } else {
                            "Als Standardzoom speichern"
                        },
                        onClick = {
                            if (!isDefaultMapZoomLevel(zoom, defaultZoom, maximumZoom)) {
                                onDefaultZoomSelected(
                                    selectedDefaultMapZoom(zoom, maximumZoom),
                                )
                            }
                        },
                    )
                    .semantics { selected = isDefaultZoom },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = displayedZoomLevel.toString(),
                        modifier = Modifier.alpha(zoomNumberAlpha),
                        color = foregroundColor.copy(
                            alpha = if (isDefaultZoom) 1f else IconTextLabelAlpha,
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = if (isDefaultZoom) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                    )
                    MenuIcon(
                        modifier = Modifier
                            .size(24.dp)
                            .alpha((1f - zoomNumberAlpha) * IconTextLabelAlpha),
                    )
                }
            }
            ZoomStepButton(
                contentDescription = "Herauszoomen",
                enabled = zoom > MapZoomMinimum,
                onClick = {
                    val target = steppedMapZoom(
                        zoom = zoom,
                        direction = -1,
                        maximumZoom = maximumZoom,
                    )
                    onZoomChange(target, true)
                },
                modifier = Modifier.height(MapZoomButtonHeight),
            ) {
                ChevronDownIcon()
            }
        }
    }
}

@Composable
private fun ZoomStepButton(
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClickLabel = contentDescription,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
