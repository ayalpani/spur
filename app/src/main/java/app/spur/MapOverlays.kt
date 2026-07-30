package app.spur

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun SelectedTrackPointPuck(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        drawCircle(Color.White)
        drawCircle(Ink, radius = 7.dp.toPx())
    }
}

@Composable
internal fun PendingMomentMarker(
    moment: MapMoment,
    modifier: Modifier = Modifier,
) {
    val markerColor = momentMarkerColor(moment.type)
    val bounceScale = remember(moment.id) {
        Animatable(if (moment.type == MomentType.EMOJI) 0.25f else 1f)
    }
    var preview by remember(moment.id) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    LaunchedEffect(moment.id) {
        preview = withContext(Dispatchers.IO) {
            when (moment.type) {
                MomentType.PHOTO -> decodeMarkerPhoto(moment.payload)
                MomentType.VIDEO -> ensureVideoThumbnail(File(moment.payload))
                    ?.let { decodeMarkerPhoto(it.absolutePath) }
                MomentType.VOICE,
                MomentType.EMOJI,
                -> null
            }
        }
    }
    DisposableEffect(preview) {
        val bitmap = preview
        onDispose { bitmap?.recycle() }
    }
    LaunchedEffect(moment.id) {
        if (moment.type == MomentType.EMOJI) {
            bounceScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }
    Box(
        modifier = modifier
            .size(MomentMarkerWidth.dp, MomentMarkerHeight.dp)
            .graphicsLayer {
                scaleX = bounceScale.value
                scaleY = bounceScale.value
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = size.width / MomentMarkerWidth
            drawRoundRect(
                color = markerColor,
                topLeft = Offset(6f * scale, 2f * scale),
                size = Size(50f * scale, 50f * scale),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    10f * scale,
                    10f * scale,
                ),
            )
            drawPath(
                path = Path().apply {
                    moveTo(26f * scale, 50f * scale)
                    lineTo(36f * scale, 50f * scale)
                    lineTo(31f * scale, 57f * scale)
                    close()
                },
                color = markerColor,
            )
            drawPath(
                path = Path().apply {
                    moveTo(16f * scale, 2f * scale)
                    lineTo(46f * scale, 2f * scale)
                    cubicTo(
                        51.5f * scale,
                        2f * scale,
                        56f * scale,
                        6.5f * scale,
                        56f * scale,
                        12f * scale,
                    )
                    lineTo(56f * scale, 42f * scale)
                    cubicTo(
                        56f * scale,
                        47.5f * scale,
                        51.5f * scale,
                        52f * scale,
                        46f * scale,
                        52f * scale,
                    )
                    lineTo(34.5f * scale, 52f * scale)
                    lineTo(31f * scale, 57f * scale)
                    lineTo(27.5f * scale, 52f * scale)
                    lineTo(16f * scale, 52f * scale)
                    cubicTo(
                        10.5f * scale,
                        52f * scale,
                        6f * scale,
                        47.5f * scale,
                        6f * scale,
                        42f * scale,
                    )
                    lineTo(6f * scale, 12f * scale)
                    cubicTo(
                        6f * scale,
                        6.5f * scale,
                        10.5f * scale,
                        2f * scale,
                        16f * scale,
                        2f * scale,
                    )
                    close()
                },
                color = Color.White,
                style = Stroke(
                    width = MomentMarkerEdgeWidth * scale,
                    cap = StrokeCap.Round,
                ),
            )
        }
        if (moment.type == MomentType.EMOJI) {
            Text(
                text = moment.payload,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .semantics {
                        contentDescription = "Emoji ${moment.payload} wird abgelegt"
                    },
                fontSize = 28.sp,
            )
        } else {
            Crossfade(
                targetState = preview,
                animationSpec = tween(MotionDurationDefaultMillis),
                label = "Moment preview",
            ) { bitmap ->
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = when (moment.type) {
                            MomentType.PHOTO -> "Foto wird eingeblendet"
                            MomentType.VIDEO -> "Video wird eingeblendet"
                            else -> null
                        },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(40.dp)
                            .clip(RoundedCornerShape(5.dp)),
                    )
                } else {
                    RotatingAsterisk(
                        modifier = Modifier
                            .padding(top = 15.dp)
                            .size(24.dp),
                        color = momentMarkerContentColor(moment.type),
                        contentDescription = when (moment.type) {
                            MomentType.PHOTO -> "Foto wird geladen"
                            MomentType.VIDEO -> "Video wird geladen"
                            MomentType.VOICE -> "Sprache wird geladen"
                            MomentType.EMOJI -> "Moment wird geladen"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RotatingAsterisk(
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "Rotating asterisk")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AsteriskRotationDurationMillis,
                easing = LinearEasing,
            ),
        ),
        label = "Asterisk rotation",
    )
    AsteriskIcon(
        rotation = rotation,
        color = color,
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

@Composable
internal fun AcceleratingAsterisk(
    isRunning: Boolean,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isRunning) {
        if (!isRunning) {
            rotation.snapTo(0f)
            return@LaunchedEffect
        }
        rotation.animateTo(
            targetValue = LoaderAsteriskAccelerationDegrees,
            animationSpec = tween(
                durationMillis = LoaderAsteriskAccelerationDurationMillis,
                easing = Easing(::loaderAsteriskAcceleration),
            ),
        )
        while (true) {
            rotation.animateTo(
                targetValue = rotation.value + 360f,
                animationSpec = tween(
                    durationMillis = AsteriskRotationDurationMillis,
                    easing = LinearEasing,
                ),
            )
        }
    }
    AsteriskIcon(
        rotation = rotation.value,
        color = color,
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

internal fun loaderAsteriskAcceleration(fraction: Float): Float = fraction * fraction

@Composable
private fun AsteriskIcon(
    rotation: Float,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    LucideIcon(
        paths = listOf(
            "M12 6v12",
            "M17.196 9 6.804 15",
            "m6.804 9 10.392 6",
        ),
        color = color,
        strokeWidth = LucideBoldStrokeWidth,
        modifier = modifier
            .graphicsLayer { rotationZ = rotation }
            .semantics { this.contentDescription = contentDescription },
    )
}

@Composable
internal fun SimulatedLocationPuck(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(52.dp)
            .semantics { contentDescription = "Simulierter Standort" },
    ) {
        drawCircle(Ink.copy(alpha = 0.2f), radius = size.minDimension / 2)
        drawCircle(Color.White, radius = 10.dp.toPx())
        drawCircle(Ink, radius = 6.dp.toPx())
        drawCircle(
            color = Ink,
            radius = 10.dp.toPx(),
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}
