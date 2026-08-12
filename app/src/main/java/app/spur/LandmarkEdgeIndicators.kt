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
    projected.sortedBy { it.landmark.priority }.forEach { candidate ->
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
        .sortedBy { it.landmark.priority }
        .mapNotNull { it.clampedTo(bounds, center) }
        .toList()
}

private fun ProjectedLandmark.clampedTo(
    bounds: LandmarkIndicatorBounds,
    center: LandmarkScreenPoint,
): LandmarkEdgeIndicator? {
    if (!point.x.isFinite() || !point.y.isFinite()) return null
    val deltaX = point.x - center.x
    val deltaY = point.y - center.y
    if (deltaX == 0f && deltaY == 0f) {
        return LandmarkEdgeIndicator(
            landmark = landmark,
            point = point,
            labelPlacement = LandmarkLabelPlacement.RIGHT,
            angleDegrees = 0f,
            isEdgeArrow = false,
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
    val placement = when {
        scale == 1f && clamped.x <= center.x -> LandmarkLabelPlacement.RIGHT
        scale == 1f -> LandmarkLabelPlacement.LEFT
        horizontalScale < verticalScale && deltaX < 0f -> LandmarkLabelPlacement.RIGHT
        horizontalScale < verticalScale -> LandmarkLabelPlacement.LEFT
        deltaY < 0f -> LandmarkLabelPlacement.BELOW
        else -> LandmarkLabelPlacement.ABOVE
    }
    return LandmarkEdgeIndicator(
        landmark = landmark,
        point = clamped,
        labelPlacement = placement,
        angleDegrees = Math.toDegrees(atan2(deltaY, deltaX).toDouble()).toFloat(),
        isEdgeArrow = scale < 1f,
    )
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
                val anchorX = when (indicator.labelPlacement) {
                    LandmarkLabelPlacement.RIGHT -> markerRadius
                    LandmarkLabelPlacement.LEFT -> placeable.width - markerRadius
                    LandmarkLabelPlacement.BELOW,
                    LandmarkLabelPlacement.ABOVE,
                    -> placeable.width / 2
                }
                val anchorY = when (indicator.labelPlacement) {
                    LandmarkLabelPlacement.BELOW -> markerRadius
                    LandmarkLabelPlacement.ABOVE -> placeable.height - markerRadius
                    LandmarkLabelPlacement.RIGHT,
                    LandmarkLabelPlacement.LEFT,
                    -> placeable.height / 2
                }
                placeable.place(
                    x = indicator.point.x.roundToInt() - anchorX,
                    y = indicator.point.y.roundToInt() - anchorY,
                )
            }
        }
    }
}

@Composable
private fun LandmarkIndicatorContent(
    indicator: LandmarkEdgeIndicator,
    alpha: State<Float>,
) {
    val fadeModifier = Modifier.graphicsLayer { this.alpha = alpha.value }
    when (indicator.labelPlacement) {
        LandmarkLabelPlacement.RIGHT -> Row(
            modifier = fadeModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LandmarkIndicatorGap),
        ) {
            LandmarkMarker(indicator)
            LandmarkLabel(indicator.landmark)
        }
        LandmarkLabelPlacement.LEFT -> Row(
            modifier = fadeModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LandmarkIndicatorGap),
        ) {
            LandmarkLabel(indicator.landmark)
            LandmarkMarker(indicator)
        }
        LandmarkLabelPlacement.BELOW -> Column(
            modifier = fadeModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LandmarkIndicatorGap),
        ) {
            LandmarkMarker(indicator)
            LandmarkLabel(indicator.landmark)
        }
        LandmarkLabelPlacement.ABOVE -> Column(
            modifier = fadeModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LandmarkIndicatorGap),
        ) {
            LandmarkLabel(indicator.landmark)
            LandmarkMarker(indicator)
        }
    }
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
    val color = Color(indicator.landmark.colorArgb)
    Box(
        modifier = Modifier
            .size(LandmarkIndicatorArrowSize)
            .rotate(indicator.angleDegrees + LandmarkNavigationDefaultAngleCorrection),
        contentAlignment = Alignment.Center,
    ) {
        LucideIcon(
            paths = LandmarkNavigationIconPaths,
            color = SheetBackground,
            modifier = Modifier.size(LandmarkIndicatorArrowSize),
            strokeWidth = LandmarkNavigationOutlineWidth,
        )
        LucideIcon(
            paths = LandmarkNavigationIconPaths,
            color = color,
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
                text = landmark.title,
                color = Color(landmark.colorArgb),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val LandmarkNavigationIconPaths = listOf("M3 11 22 2l-9 19-2-8-8-2z")
private const val LandmarkNavigationDefaultAngleCorrection = 45f
private const val LandmarkNavigationOutlineWidth = 6f
private val LandmarkIndicatorArrowSize = 18.dp
private val LandmarkIndicatorDotSize = 12.dp
private val LandmarkMarkerOutlineWidth = 3.dp
private val LandmarkIndicatorGap = 6.dp
internal const val LandmarkEdgeInsetDp = 24f
internal const val LandmarkMinimumSeparationDp = 112f
internal const val LandmarkMaximumVisibleCount = 5
internal const val LandmarkIndicatorHideDelayMillis = 500L
