package app.spur

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
import androidx.compose.ui.graphics.drawscope.Fill
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
internal fun MenuIcon(
    modifier: Modifier = Modifier.size(MapControlIconSize),
    strokeWidth: Float = LocalLucideStrokeWidth.current,
) = LucideIcon(
    paths = listOf("M12 5h.01", "M12 12h.01", "M12 19h.01"),
    modifier = modifier,
    strokeWidth = strokeWidth,
)

@Composable
internal fun ShareIcon(
    modifier: Modifier = Modifier.size(MapControlIconSize),
) = LucideIcon(
    paths = listOf(
        "M21 5a3 3 0 1 1-6 0 3 3 0 1 1 6 0",
        "M9 12a3 3 0 1 1-6 0 3 3 0 1 1 6 0",
        "M21 19a3 3 0 1 1-6 0 3 3 0 1 1 6 0",
        "M8.59 13.51 15.42 17.49",
        "M15.41 6.51 8.59 10.49",
    ),
    modifier = modifier,
    strokeWidth = LucideRegularStrokeWidth,
)

@Composable
internal fun WaypointsIcon(
    color: Color = LocalContentColor.current,
    modifier: Modifier = Modifier.size(MapControlIconSize),
) = LucideIcon(
    paths = listOf(
        "m10.586 5.414-5.172 5.172",
        "m18.586 13.414-5.172 5.172",
        "M6 12h12",
        "M14 20a2 2 0 1 1-4 0 2 2 0 1 1 4 0",
        "M14 4a2 2 0 1 1-4 0 2 2 0 1 1 4 0",
        "M22 12a2 2 0 1 1-4 0 2 2 0 1 1 4 0",
        "M6 12a2 2 0 1 1-4 0 2 2 0 1 1 4 0",
    ),
    color = color,
    modifier = modifier,
    strokeWidth = LucideRegularStrokeWidth,
)

@Composable
internal fun TravelDirectionIcon(
    modifier: Modifier = Modifier.size(MapControlIconSize),
) = LucideIcon(
    paths = listOf(
        "M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15",
        "M9 19a3 3 0 1 1-6 0 3 3 0 1 1 6 0",
        "M21 5a3 3 0 1 1-6 0 3 3 0 1 1 6 0",
    ),
    modifier = modifier,
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
internal fun LucideMapIcon(
    modifier: Modifier = Modifier.size(MapControlIconSize),
) = LucideIcon(
    paths = listOf(
        "M14.5 4.5 9.5 2 4 4.5v15l5.5-2.5 5 2.5 5.5-2.5v-15z",
        "M9.5 2v15",
        "M14.5 4.5v15",
    ),
    modifier = modifier,
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
    strokeWidth = LucideRegularStrokeWidth,
    modifier = Modifier.size(24.dp),
)

@Composable
internal fun MomentVideoIcon() = LucideIcon(
    paths = MomentVideoIconPaths,
    strokeWidth = LucideRegularStrokeWidth,
    modifier = Modifier.size(24.dp),
)

@Composable
internal fun MomentVoiceIcon() = LucideIcon(
    paths = MomentVoicePlaybackIconPaths,
    strokeWidth = LucideRegularStrokeWidth,
    modifier = Modifier.size(24.dp),
)

@Composable
internal fun MomentEmojiIcon() = LucideIcon(
    paths = listOf(
        "M8 14s1.5 2 4 2 4-2 4-2",
        "M9 9h.01",
        "M15 9h.01",
        "M21 12a9 9 0 1 1-18 0 9 9 0 1 1 18 0",
    ),
    strokeWidth = LucideRegularStrokeWidth,
    modifier = Modifier.size(24.dp),
)

@Composable
internal fun PlusIcon() = LucideIcon(
    paths = listOf("M5 12h14", "M12 5v14"),
)

@Composable
internal fun HistoryIcon() = LucideIcon(
    paths = listOf(
        "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
        "M3 3v5h5",
        "M12 7v5l4 2",
    ),
    strokeWidth = LucideRegularStrokeWidth,
)

@Composable
internal fun PersonStandingIcon(
    color: Color = LocalContentColor.current,
) = LucideIcon(
    paths = listOf(
        "M12 4a1 1 0 1 0 0 2 1 1 0 1 0 0-2",
        "m9 20 3-6 3 6",
        "m6 8 6 2 6-2",
        "M12 10v4",
    ),
    color = color,
    strokeWidth = LucideRegularStrokeWidth,
)

@Composable
internal fun PencilIcon(
    modifier: Modifier = Modifier.size(24.dp),
) = LucideIcon(
    paths = listOf(
        "M12 20h9",
        "M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z",
        "m15 5 3 3",
    ),
    modifier = modifier,
    strokeWidth = LucideRegularStrokeWidth,
)

@Composable
internal fun MicrophoneIcon(
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = LocalContentColor.current,
    strokeWidth: Float = LocalLucideStrokeWidth.current,
) = LucideIcon(
    paths = listOf(
        "M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3",
        "M19 10v2a7 7 0 0 1-14 0v-2",
        "M12 19v3",
    ),
    modifier = modifier,
    color = color,
    strokeWidth = strokeWidth,
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
internal fun FollowLocationIcon(
    selected: Boolean,
) {
    val trailColors = LocalTrailColors.current
    val mapControlColors = LocalMapControlColors.current
    Box(
        modifier = Modifier
            .size(MapControlSize)
            .clip(CircleShape)
            .background(if (selected) trailColors.background else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        PersonStandingIcon(
            color = if (selected) mapControlColors.foreground else LocalContentColor.current,
        )
    }
}

@Composable
internal fun HomeAsteriskIcon(isMoving: Boolean) {
    AcceleratingAsterisk(
        isRunning = isMoving,
        color = LocalContentColor.current,
        contentDescription = null,
        modifier = Modifier.size(MapControlIconSize),
    )
}

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
    modifier: Modifier = Modifier.size(MapControlIconSize),
    strokeWidth: Float = LocalLucideStrokeWidth.current,
    filled: Boolean = false,
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
                    style = if (filled) {
                        Fill
                    } else {
                        Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round,
                        )
                    },
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

@Composable
internal fun ChevronLeftIcon() = LucideIcon(
    paths = listOf("m15 18-6-6 6-6"),
    strokeWidth = LucideBoldStrokeWidth,
    modifier = Modifier.size(24.dp),
)

@Composable
internal fun ChevronUpIcon() = LucideIcon(
    paths = listOf("m18 15-6-6-6 6"),
    strokeWidth = LucideBoldStrokeWidth,
    modifier = Modifier.size(24.dp),
)

@Composable
internal fun ChevronDownIcon() = LucideIcon(
    paths = listOf("m6 9 6 6 6-6"),
    strokeWidth = LucideBoldStrokeWidth,
    modifier = Modifier.size(24.dp),
)
