package app.spur

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt

internal data class LandmarkScreenPoint(val x: Float, val y: Float)

internal data class LandmarkIndicatorBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal enum class LandmarkLabelPlacement {
    RIGHT,
    LEFT,
    BELOW,
    ABOVE,
}

internal data class ProjectedLandmark(
    val landmark: Landmark,
    val point: LandmarkScreenPoint,
)

internal data class LandmarkEdgeIndicator(
    val landmark: Landmark,
    val point: LandmarkScreenPoint,
    val labelPlacement: LandmarkLabelPlacement,
    val angleDegrees: Float,
    val isEdgeArrow: Boolean,
)

internal data class LocationEdgeIndicator(
    val point: LandmarkScreenPoint,
    val labelPlacement: LandmarkLabelPlacement,
    val angleDegrees: Float,
    val isOffscreen: Boolean,
)

private data class ClampedMapPoint(
    val point: LandmarkScreenPoint,
    val angleDegrees: Float,
    val scale: Float,
    val horizontalScale: Float,
    val verticalScale: Float,
    val deltaX: Float,
    val deltaY: Float,
)

internal fun landmarkEdgeIndicators(
    projected: List<ProjectedLandmark>,
    bounds: LandmarkIndicatorBounds,
    minimumSeparation: Float,
    maximumCount: Int,
): List<LandmarkEdgeIndicator> {
    if (maximumCount <= 0 || bounds.left >= bounds.right || bounds.top >= bounds.bottom) {
        return emptyList()
    }
    val center = LandmarkScreenPoint(
        x = (bounds.left + bounds.right) / 2f,
        y = (bounds.top + bounds.bottom) / 2f,
    )
    val minimumSeparationSquared = minimumSeparation * minimumSeparation
    val accepted = ArrayList<LandmarkEdgeIndicator>(maximumCount)
    projected.sortedWith(LandmarkSelectionOrder).forEach { candidate ->
        val indicator = candidate.clampedTo(bounds, center) ?: return@forEach
        val isSeparated = accepted.all { existing ->
            val deltaX = existing.point.x - indicator.point.x
            val deltaY = existing.point.y - indicator.point.y
            deltaX * deltaX + deltaY * deltaY >= minimumSeparationSquared
        }
        if (isSeparated) accepted += indicator
        if (accepted.size == maximumCount) return accepted
    }
    return accepted
}

internal fun retainedLandmarkEdgeIndicators(
    projected: List<ProjectedLandmark>,
    bounds: LandmarkIndicatorBounds,
    landmarkIds: Set<String>,
): List<LandmarkEdgeIndicator> {
    val center = LandmarkScreenPoint(
        x = (bounds.left + bounds.right) / 2f,
        y = (bounds.top + bounds.bottom) / 2f,
    )
    return projected
        .asSequence()
        .filter { it.landmark.id in landmarkIds }
        .sortedWith(LandmarkSelectionOrder)
        .mapNotNull { it.clampedTo(bounds, center) }
        .toList()
}

internal fun effectiveLocationIndicatorCoordinate(
    gpsLocation: SpurCoordinate?,
    manualLocation: SpurCoordinate?,
    isTrackPointSelected: Boolean,
): SpurCoordinate? = if (isTrackPointSelected) null else manualLocation ?: gpsLocation

internal fun locationEdgeIndicatorFor(
    point: LandmarkScreenPoint,
    bounds: LandmarkIndicatorBounds,
): LocationEdgeIndicator? {
    val center = LandmarkScreenPoint(
        x = (bounds.left + bounds.right) / 2f,
        y = (bounds.top + bounds.bottom) / 2f,
    )
    val clamped = point.clampedTo(bounds, center) ?: return null
    return LocationEdgeIndicator(
        point = clamped.point,
        labelPlacement = clamped.labelPlacement(center),
        angleDegrees = clamped.angleDegrees,
        isOffscreen = clamped.scale < 1f,
    )
}

private fun ProjectedLandmark.clampedTo(
    bounds: LandmarkIndicatorBounds,
    center: LandmarkScreenPoint,
): LandmarkEdgeIndicator? {
    val clamped = point.clampedTo(bounds, center) ?: return null
    return LandmarkEdgeIndicator(
        landmark = landmark,
        point = clamped.point,
        labelPlacement = clamped.labelPlacement(center),
        angleDegrees = clamped.angleDegrees,
        isEdgeArrow = clamped.scale < 1f,
    )
}

private fun ClampedMapPoint.labelPlacement(
    center: LandmarkScreenPoint,
): LandmarkLabelPlacement = when {
    scale == 1f && point.x <= center.x -> LandmarkLabelPlacement.RIGHT
    scale == 1f -> LandmarkLabelPlacement.LEFT
    horizontalScale < verticalScale && deltaX < 0f -> LandmarkLabelPlacement.RIGHT
    horizontalScale < verticalScale -> LandmarkLabelPlacement.LEFT
    deltaY < 0f -> LandmarkLabelPlacement.BELOW
    else -> LandmarkLabelPlacement.ABOVE
}

private fun LandmarkScreenPoint.clampedTo(
    bounds: LandmarkIndicatorBounds,
    center: LandmarkScreenPoint,
): ClampedMapPoint? {
    if (!x.isFinite() || !y.isFinite()) return null
    val deltaX = x - center.x
    val deltaY = y - center.y
    if (deltaX == 0f && deltaY == 0f) {
        return ClampedMapPoint(
            point = this,
            angleDegrees = 0f,
            scale = 1f,
            horizontalScale = Float.POSITIVE_INFINITY,
            verticalScale = Float.POSITIVE_INFINITY,
            deltaX = 0f,
            deltaY = 0f,
        )
    }
    val horizontalScale = when {
        deltaX > 0f -> (bounds.right - center.x) / deltaX
        deltaX < 0f -> (bounds.left - center.x) / deltaX
        else -> Float.POSITIVE_INFINITY
    }
    val verticalScale = when {
        deltaY > 0f -> (bounds.bottom - center.y) / deltaY
        deltaY < 0f -> (bounds.top - center.y) / deltaY
        else -> Float.POSITIVE_INFINITY
    }
    val scale = min(1f, min(horizontalScale, verticalScale))
    val clamped = LandmarkScreenPoint(
        x = center.x + deltaX * scale,
        y = center.y + deltaY * scale,
    )
    return ClampedMapPoint(
        point = clamped,
        angleDegrees = Math.toDegrees(atan2(deltaY, deltaX).toDouble()).toFloat(),
        scale = scale,
        horizontalScale = horizontalScale,
        verticalScale = verticalScale,
        deltaX = deltaX,
        deltaY = deltaY,
    )
}

@Composable
internal fun LocationEdgeOverlay(
    indicator: State<LocationEdgeIndicator?>,
    visible: Boolean,
    colors: LocationMarkerColors,
    pulseColor: Color,
    modifier: Modifier = Modifier,
) {
    val displayed = indicator.value
    val shown = visible && displayed?.isOffscreen == true
    val alpha = animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(MotionDurationDefaultMillis),
        label = "Own location edge indicator",
    )
    Layout(
        modifier = modifier.clearAndSetSemantics {
            if (shown) {
                contentDescription = "Eigener Standort außerhalb der Karte"
            }
        },
        content = {
            displayed?.let { location ->
                LocationEdgeIndicatorContent(
                    indicator = location,
                    colors = colors,
                    pulseColor = pulseColor,
                    modifier = Modifier.graphicsLayer { this.alpha = alpha.value },
                )
            }
        },
    ) { measurables, constraints ->
        val placeable = measurables.singleOrNull()
            ?.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (displayed != null && placeable != null) {
                val markerRadius = LandmarkIndicatorArrowSize.roundToPx() / 2
                placeable.place(
                    x = displayed.point.x.roundToInt() - displayed.labelPlacement.anchorX(
                        placeable.width,
                        markerRadius,
                    ),
                    y = displayed.point.y.roundToInt() - displayed.labelPlacement.anchorY(
                        placeable.height,
                        markerRadius,
                    ),
                )
            }
        }
    }
}

@Composable
private fun LocationEdgeIndicatorContent(
    indicator: LocationEdgeIndicator,
    colors: LocationMarkerColors,
    pulseColor: Color,
    modifier: Modifier = Modifier,
) {
    EdgeIndicatorContent(
        labelPlacement = indicator.labelPlacement,
        modifier = modifier,
        marker = {
            OutlinedNavigationArrow(
                angleDegrees = indicator.angleDegrees,
                fill = colors.fill,
                outline = colors.outline,
                outerOutline = pulseColor,
            )
        },
        label = { IndicatorLabel(text = "Ich", textColor = pulseColor) },
    )
}

@Composable
private fun EdgeIndicatorContent(
    labelPlacement: LandmarkLabelPlacement,
    modifier: Modifier,
    marker: @Composable () -> Unit,
    label: @Composable () -> Unit,
) {
    when (labelPlacement) {
        LandmarkLabelPlacement.RIGHT -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LandmarkIndicatorGap),
        ) {
            marker()
            label()
        }
        LandmarkLabelPlacement.LEFT -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LandmarkIndicatorGap),
        ) {
            label()
            marker()
        }
        LandmarkLabelPlacement.BELOW -> Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LandmarkIndicatorGap),
        ) {
            marker()
            label()
        }
        LandmarkLabelPlacement.ABOVE -> Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LandmarkIndicatorGap),
        ) {
            label()
            marker()
        }
    }
}

@Composable
internal fun LandmarkEdgeOverlay(
    indicators: State<List<LandmarkEdgeIndicator>>,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val displayed = indicators.value
    val alpha = animateFloatAsState(
        targetValue = if (visible && displayed.isNotEmpty()) 1f else 0f,
        animationSpec = tween(MotionDurationDefaultMillis),
        label = "Landmark indicators",
    )
    Layout(
        modifier = modifier
            .semantics { contentDescription = "Orte in der Umgebung" },
        content = {
            displayed.forEach { indicator ->
                LandmarkIndicatorContent(indicator, alpha)
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            displayed.zip(placeables).forEach { (indicator, placeable) ->
                val markerRadius = if (indicator.isEdgeArrow) {
                    LandmarkIndicatorArrowSize.roundToPx() / 2
                } else {
                    LandmarkIndicatorDotSize.roundToPx() / 2
                }
                placeable.place(
                    x = indicator.point.x.roundToInt() - indicator.labelPlacement.anchorX(
                        placeable.width,
                        markerRadius,
                    ),
                    y = indicator.point.y.roundToInt() - indicator.labelPlacement.anchorY(
                        placeable.height,
                        markerRadius,
                    ),
                )
            }
        }
    }
}

private fun LandmarkLabelPlacement.anchorX(width: Int, markerRadius: Int): Int = when (this) {
    LandmarkLabelPlacement.RIGHT -> markerRadius
    LandmarkLabelPlacement.LEFT -> width - markerRadius
    LandmarkLabelPlacement.BELOW,
    LandmarkLabelPlacement.ABOVE,
    -> width / 2
}

private fun LandmarkLabelPlacement.anchorY(height: Int, markerRadius: Int): Int = when (this) {
    LandmarkLabelPlacement.BELOW -> markerRadius
    LandmarkLabelPlacement.ABOVE -> height - markerRadius
    LandmarkLabelPlacement.RIGHT,
    LandmarkLabelPlacement.LEFT,
    -> height / 2
}

@Composable
private fun LandmarkIndicatorContent(
    indicator: LandmarkEdgeIndicator,
    alpha: State<Float>,
) {
    EdgeIndicatorContent(
        labelPlacement = indicator.labelPlacement,
        modifier = Modifier.graphicsLayer { this.alpha = alpha.value },
        marker = { LandmarkMarker(indicator) },
        label = { LandmarkLabel(indicator.landmark) },
    )
}

@Composable
private fun LandmarkMarker(indicator: LandmarkEdgeIndicator) {
    Crossfade(
        targetState = indicator.isEdgeArrow,
        animationSpec = tween(MotionDurationDefaultMillis),
        label = "Landmark marker type",
    ) { isEdgeArrow ->
        if (isEdgeArrow) {
            LandmarkArrow(indicator)
        } else {
            LandmarkDot(indicator.landmark)
        }
    }
}

@Composable
private fun LandmarkArrow(indicator: LandmarkEdgeIndicator) {
    OutlinedNavigationArrow(
        angleDegrees = indicator.angleDegrees,
        fill = Color(indicator.landmark.colorArgb),
        outline = SheetBackground,
    )
}

@Composable
private fun OutlinedNavigationArrow(
    angleDegrees: Float,
    fill: Color,
    outline: Color,
    outerOutline: Color? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(LandmarkIndicatorArrowSize)
            .rotate(angleDegrees + LandmarkNavigationDefaultAngleCorrection),
        contentAlignment = Alignment.Center,
    ) {
        outerOutline?.let { color ->
            LucideIcon(
                paths = LandmarkNavigationIconPaths,
                color = color,
                modifier = Modifier.size(LandmarkIndicatorArrowSize),
                strokeWidth = LocationNavigationOuterOutlineWidth,
            )
        }
        LucideIcon(
            paths = LandmarkNavigationIconPaths,
            color = outline,
            modifier = Modifier.size(LandmarkIndicatorArrowSize),
            strokeWidth = LandmarkNavigationOutlineWidth,
        )
        LucideIcon(
            paths = LandmarkNavigationIconPaths,
            color = fill,
            modifier = Modifier.size(LandmarkIndicatorArrowSize),
            filled = true,
        )
    }
}

@Composable
private fun LandmarkDot(landmark: Landmark) {
    Surface(
        modifier = Modifier.size(LandmarkIndicatorDotSize),
        shape = CircleShape,
        color = Color(landmark.colorArgb),
        border = BorderStroke(LandmarkMarkerOutlineWidth, SheetBackground),
    ) {}
}

@Composable
private fun LandmarkLabel(landmark: Landmark) {
    IndicatorLabel(
        text = landmark.title,
        textColor = Color(landmark.colorArgb),
    )
}

@Composable
private fun IndicatorLabel(
    text: String,
    textColor: Color,
) {
    val colors = LocalMapControlColors.current.inverted
    Surface(
        modifier = Modifier.widthIn(max = 136.dp),
        shape = RectangleShape,
        color = colors.background.copy(alpha = 1f - HomeStatusBackgroundTransparency),
        contentColor = colors.foreground,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = textColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val LandmarkNavigationIconPaths = listOf("M3 11 22 2l-9 19-2-8-8-2z")
private val LandmarkSelectionOrder = compareBy<ProjectedLandmark>(
    { !it.landmark.id.startsWith(CustomLandmarkIdPrefix) },
    {
        if (it.landmark.id.startsWith(CustomLandmarkIdPrefix)) {
            -it.landmark.priority
        } else {
            it.landmark.priority
        }
    },
)
private const val LandmarkNavigationDefaultAngleCorrection = 45f
private const val LandmarkNavigationOutlineWidth = 6f
private const val LocationNavigationOuterOutlineWidth = 10f
private val LandmarkIndicatorArrowSize = 18.dp
private val LandmarkIndicatorDotSize = 12.dp
private val LandmarkMarkerOutlineWidth = 3.dp
private val LandmarkIndicatorGap = 6.dp
internal const val LandmarkEdgeInsetDp = 24f
internal const val LocationEdgeInsetDp = 12f
internal const val LandmarkMinimumSeparationDp = 112f
internal const val LandmarkMaximumVisibleCount = 5
internal const val LandmarkIndicatorHideDelayMillis = 2_000L
