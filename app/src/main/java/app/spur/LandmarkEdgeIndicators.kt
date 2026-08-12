package app.spur

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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

private fun ProjectedLandmark.clampedTo(
    bounds: LandmarkIndicatorBounds,
    center: LandmarkScreenPoint,
): LandmarkEdgeIndicator? {
    if (!point.x.isFinite() || !point.y.isFinite()) return null
    val deltaX = point.x - center.x
    val deltaY = point.y - center.y
    if (deltaX == 0f && deltaY == 0f) {
        return LandmarkEdgeIndicator(landmark, point, LandmarkLabelPlacement.RIGHT)
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
    return LandmarkEdgeIndicator(landmark, clamped, placement)
}

@Composable
internal fun LandmarkEdgeOverlay(
    indicators: State<List<LandmarkEdgeIndicator>>,
    modifier: Modifier = Modifier,
) {
    val displayed = indicators.value
    Layout(
        modifier = modifier.semantics {
            contentDescription = "Orte in der Umgebung"
        },
        content = {
            displayed.forEach { indicator ->
                LandmarkIndicatorContent(indicator)
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val dotRadius = LandmarkIndicatorDotSize.roundToPx() / 2
        layout(constraints.maxWidth, constraints.maxHeight) {
            displayed.zip(placeables).forEach { (indicator, placeable) ->
                val anchorX = when (indicator.labelPlacement) {
                    LandmarkLabelPlacement.RIGHT -> dotRadius
                    LandmarkLabelPlacement.LEFT -> placeable.width - dotRadius
                    LandmarkLabelPlacement.BELOW,
                    LandmarkLabelPlacement.ABOVE,
                    -> placeable.width / 2
                }
                val anchorY = when (indicator.labelPlacement) {
                    LandmarkLabelPlacement.BELOW -> dotRadius
                    LandmarkLabelPlacement.ABOVE -> placeable.height - dotRadius
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
private fun LandmarkIndicatorContent(indicator: LandmarkEdgeIndicator) {
    when (indicator.labelPlacement) {
        LandmarkLabelPlacement.RIGHT -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LandmarkIndicatorGap),
        ) {
            LandmarkDot()
            LandmarkLabel(indicator.landmark.title)
        }
        LandmarkLabelPlacement.LEFT -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LandmarkIndicatorGap),
        ) {
            LandmarkLabel(indicator.landmark.title)
            LandmarkDot()
        }
        LandmarkLabelPlacement.BELOW -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LandmarkIndicatorGap),
        ) {
            LandmarkDot()
            LandmarkLabel(indicator.landmark.title)
        }
        LandmarkLabelPlacement.ABOVE -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LandmarkIndicatorGap),
        ) {
            LandmarkLabel(indicator.landmark.title)
            LandmarkDot()
        }
    }
}

@Composable
private fun LandmarkDot() {
    val style = secondaryMapControlStyle(LocalMapControlColors.current)
    Surface(
        modifier = Modifier.size(LandmarkIndicatorDotSize),
        shape = CircleShape,
        color = style.colors.background,
        border = style.border,
    ) {}
}

@Composable
private fun LandmarkLabel(title: String) {
    val style = secondaryMapControlStyle(LocalMapControlColors.current)
    Surface(
        modifier = Modifier.widthIn(max = 136.dp),
        shape = CircleShape,
        color = style.colors.background,
        contentColor = style.colors.foreground,
        border = style.border,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val LandmarkIndicatorDotSize = 12.dp
private val LandmarkIndicatorGap = 6.dp
internal const val LandmarkEdgeInsetDp = 24f
internal const val LandmarkMinimumSeparationDp = 112f
internal const val LandmarkMaximumVisibleCount = 5
