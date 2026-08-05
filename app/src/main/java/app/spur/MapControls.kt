package app.spur

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun MapIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondary: Boolean = false,
    contentColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val selectedColors = LocalMapControlColors.current
    val style = if (secondary) {
        secondaryMapControlStyle(selectedColors)
    } else {
        MapControlButtonStyle(colors = selectedColors)
    }
    val colors = IconButtonDefaults.filledIconButtonColors(
        containerColor = style.colors.background,
        contentColor = contentColor ?: style.colors.foreground,
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(MapControlSize)
            .mapControlShadow(CircleShape)
            .semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = if (enabled) colors.containerColor else colors.disabledContainerColor,
        contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
        border = style.border,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(
                LocalLucideStrokeWidth provides LucideBoldStrokeWidth,
                content = content,
            )
        }
    }
}

internal data class MapControlButtonStyle(
    val colors: MapControlColors,
    val border: BorderStroke? = null,
)

internal fun secondaryMapControlStyle(
    colors: MapControlColors,
): MapControlButtonStyle {
    val inverted = colors.inverted
    return MapControlButtonStyle(
        colors = inverted.copy(
            background = inverted.background.copy(
                alpha = 1f - SecondaryMapControlBackgroundTransparency,
            ),
        ),
        border = BorderStroke(
            width = MapOutlineWidthDp.dp,
            color = inverted.foreground.copy(alpha = 0.25f),
        ),
    )
}

@Composable
internal fun MapStyleButton(
    contentDescription: String,
    preview: ImageBitmap?,
    fallbackPreview: Int,
    onClick: () -> Unit,
) {
    val secondaryStyle = secondaryMapControlStyle(LocalMapControlColors.current)
    val previewShape = CircleShape
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(MapControlSize)
            .mapControlShadow(previewShape)
            .semantics { this.contentDescription = contentDescription },
        shape = previewShape,
        color = Color.Transparent,
        border = secondaryStyle.border,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val previewModifier = Modifier.fillMaxSize()
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
        }
    }
}
