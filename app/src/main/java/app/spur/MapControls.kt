package app.spur

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun MomentOption(
    label: String,
    accentColor: Color,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = accentColor,
        ),
        border = BorderStroke(2.dp, Ink.copy(alpha = 0.18f)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun MapIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val controlColors = LocalMapControlColors.current
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(MapControlSize)
            .mapControlShadow(CircleShape)
            .semantics { this.contentDescription = contentDescription },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = controlColors.background,
            contentColor = controlColors.foreground,
        ),
    ) {
        CompositionLocalProvider(
            LocalLucideStrokeWidth provides LucideBoldStrokeWidth,
            content = content,
        )
    }
}

@Composable
internal fun MapStyleButton(
    contentDescription: String,
    preview: ImageBitmap?,
    isLoading: Boolean,
    fallbackPreview: Int,
    onClick: () -> Unit,
) {
    val tourControlColors = LocalMapControlColors.current.inverted
    val previewShape = CircleShape
    val blurRadius by animateDpAsState(
        targetValue = if (isLoading) 7.dp else 0.dp,
        animationSpec = tween(180),
        label = "Map preview blur",
    )
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(MapControlSize)
            .mapControlShadow(previewShape)
            .semantics { this.contentDescription = contentDescription },
        shape = previewShape,
        color = Color.Transparent,
        border = BorderStroke(3.dp, tourControlColors.background),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val previewModifier = Modifier
                .fillMaxSize()
                .blur(blurRadius)
            if (preview == null) {
                Image(
                    painter = painterResource(fallbackPreview),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = previewModifier,
                )
            } else {
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = previewModifier,
                )
            }
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(tween(140)),
                exit = fadeOut(tween(220)),
            ) {
                MapPreviewLoadingOverlay()
            }
        }
    }
}

@Composable
private fun MapPreviewLoadingOverlay() {
    val transition = rememberInfiniteTransition(label = "Map preview haze")
    val drift by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Map preview haze drift",
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Color.White.copy(alpha = 0.5f))
        drawCircle(
            color = Color.White.copy(alpha = 0.28f),
            radius = size.width * 0.9f,
            center = Offset(
                x = size.width * drift,
                y = size.height * (0.25f + drift * 0.35f),
            ),
        )
        drawCircle(
            color = Color(0xFFD9E9E2).copy(alpha = 0.24f),
            radius = size.width * 0.75f,
            center = Offset(
                x = size.width * (1f - drift),
                y = size.height * (0.75f - drift * 0.3f),
            ),
        )
    }
}
