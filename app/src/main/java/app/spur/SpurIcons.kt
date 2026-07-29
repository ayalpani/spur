package app.spur

import android.location.Location
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser as ComposePathParser
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip

@Composable
internal fun MenuIcon() = LucideIcon(
    paths = listOf("M4 12h.01", "M12 12h.01", "M20 12h.01"),
)

@Composable
internal fun ShareIcon() = LucideIcon(
    paths = listOf(
        "M21 5a3 3 0 1 1-6 0 3 3 0 1 1 6 0",
        "M9 12a3 3 0 1 1-6 0 3 3 0 1 1 6 0",
        "M21 19a3 3 0 1 1-6 0 3 3 0 1 1 6 0",
        "M8.59 13.51 15.42 17.49",
        "M15.41 6.51 8.59 10.49",
    ),
    strokeWidth = LucideRegularStrokeWidth,
)

@Composable
internal fun ExternalLinkIcon() = LucideIcon(
    paths = listOf(
        "M15 3h6v6",
        "M10 14 21 3",
        "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6",
    ),
    modifier = Modifier.size(18.dp),
    strokeWidth = LucideRegularStrokeWidth,
)

@Composable
internal fun MapPinIcon(
    color: Color = LocalContentColor.current,
    modifier: Modifier = Modifier.size(32.dp),
) = LucideIcon(
    paths = MapPinIconPaths,
    color = color,
    modifier = modifier,
)

@Composable
internal fun FilledMapPinAtCenter(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val pinPath = remember {
        ComposePathParser().parsePathString(MapPinIconPaths.first()).toPath()
    }
    Canvas(modifier = modifier) {
        val pinSize = 28.dp.toPx()
        val scale = pinSize / 24f
        withTransform({
            translate(
                left = (size.width - pinSize) / 2f,
                top = size.height / 2f - MapPinTipY * scale,
            )
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            drawPath(
                path = pinPath,
                color = color,
            )
        }
    }
}

@Composable
internal fun MomentPhotoIcon() = LucideIcon(
    paths = MomentPhotoIconPaths,
    strokeWidth = LucideBoldStrokeWidth,
    modifier = Modifier.size(30.dp),
)

@Composable
internal fun MomentVideoIcon() = LucideIcon(
    paths = MomentVideoIconPaths,
    strokeWidth = LucideBoldStrokeWidth,
    modifier = Modifier.size(30.dp),
)

@Composable
internal fun MomentVoiceIcon() = LucideIcon(
    paths = MomentVoicePlaybackIconPaths,
    strokeWidth = LucideBoldStrokeWidth,
    modifier = Modifier.size(30.dp),
)

@Composable
internal fun PlusIcon() = LucideIcon(
    paths = listOf("M5 12h14", "M12 5v14"),
)

@Composable
internal fun MicrophoneIcon(
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = LocalContentColor.current,
) = LucideIcon(
    paths = listOf(
        "M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3",
        "M19 10v2a7 7 0 0 1-14 0v-2",
        "M12 19v3",
    ),
    modifier = modifier,
    color = color,
)

@Composable
internal fun PlayIcon(
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = LocalContentColor.current,
) = LucideIcon(
    paths = listOf("m6 3 14 9-14 9z"),
    modifier = modifier,
    color = color,
)

@Composable
internal fun PauseIcon() = LucideIcon(
    paths = listOf("M8 5v14", "M16 5v14"),
)

@Composable
internal fun locationSignalButtonAlpha(
    selected: Boolean,
    pulseGeneration: Long?,
): Float =
    if (selected && pulseGeneration != null) {
        key(pulseGeneration) {
            val transition = rememberInfiniteTransition(label = "Location following signal")
            transition.animateFloat(
                initialValue = 1f,
                targetValue = LocationSignalIconMinimumAlpha,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = LocationSignalPeriodMillis / 2,
                        easing = LocationPulseEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "Location following icon opacity",
            ).value
        }
    } else {
        1f
    }

@Composable
internal fun FollowLocationIcon(selected: Boolean) {
    val trailColor = LocalTrailColors.current.fill
    Box(
        modifier = Modifier
            .size(MapControlSize)
            .clip(CircleShape)
            .background(if (selected) trailColor else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        FootprintsIcon(
            color = if (selected) Color.White else LocalContentColor.current,
        )
    }
}

@Composable
private fun FootprintsIcon(
    color: Color = LocalContentColor.current,
) = LucideIcon(
    paths = listOf(
        "M4 16v-2.38C4 11.5 2.97 10.5 3 8c.03-2.72 1.49-6 4.5-6C9.37 2 10 3.8 10 5.5c0 3.11-2 5.66-2 8.68V16a2 2 0 1 1-4 0Z",
        "M20 20v-2.38c0-2.12 1.03-3.12 1-5.62-.03-2.72-1.49-6-4.5-6C14.63 6 14 7.8 14 9.5c0 3.11 2 5.66 2 8.68V20a2 2 0 1 0 4 0Z",
        "M16 17h4",
        "M4 13h4",
    ),
    color = color,
    strokeWidth = LucideRegularStrokeWidth,
)

@Composable
internal fun HomeIcon(
    modifier: Modifier = Modifier.size(28.dp),
) = LucideIcon(
    paths = listOf(
        "M3 9.5 12 2l9 7.5",
        "M5 10v10h14V10",
        "M9 20v-6h6v6",
    ),
    modifier = modifier,
)

@Composable
internal fun LucideLocateOffIcon() = LucideIcon(
    paths = listOf(
        "M12 19v3",
        "M12 2v3",
        "M18.89 13.24a7 7 0 0 0-8.13-8.13",
        "M19 12h3",
        "M2 12h3",
        "m2 2 20 20",
        "M7.05 7.05a7 7 0 0 0 9.9 9.9",
    ),
)

@Composable
internal fun LucideIcon(
    paths: List<String>,
    color: Color = LocalContentColor.current,
    modifier: Modifier = Modifier.size(32.dp),
    strokeWidth: Float = LocalLucideStrokeWidth.current,
) {
    val parsedPaths = paths.map { path ->
        remember(path) { ComposePathParser().parsePathString(path).toPath() }
    }
    Canvas(modifier = modifier) {
        val scale = size.minDimension / 24f
        withTransform({
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            parsedPaths.forEach { path ->
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun BackIcon() = LucideIcon(
    paths = listOf("m12 19-7-7 7-7", "M19 12H5"),
    strokeWidth = LucideBoldStrokeWidth,
    modifier = Modifier
        .size(24.dp)
        .semantics { contentDescription = "Zurück zur Karte" },
)
