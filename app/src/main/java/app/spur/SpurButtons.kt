package app.spur

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun SpurPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalMapControlColors.current
    val contentColor = if (destructive) Color.White else colors.foreground
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (destructive) StopRed else colors.background,
            contentColor = contentColor,
        ),
    ) {
        val renderedContentColor = if (enabled) contentColor else LocalContentColor.current
        leadingIcon?.let {
            it()
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = label,
            color = renderedContentColor,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun SpurSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    compactContent: Boolean = false,
    leadingIconSlotWidth: Dp? = null,
    enabled: Boolean = true,
) {
    val contentColor = secondaryButtonContentColor(LocalMapControlColors.current)
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, Ink.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
        ),
        contentPadding = if (leadingIconSlotWidth == null) {
            ButtonDefaults.ContentPadding
        } else {
            PaddingValues(horizontal = 6.dp, vertical = 6.dp)
        },
    ) {
        val renderedContentColor = if (enabled) contentColor else LocalContentColor.current
        val content = @Composable {
            leadingIcon?.let { icon ->
                if (leadingIconSlotWidth == null) {
                    icon()
                } else {
                    Box(
                        modifier = Modifier.size(leadingIconSlotWidth),
                        contentAlignment = Alignment.Center,
                    ) {
                        icon()
                    }
                }
                Spacer(modifier = Modifier.width(if (compactContent) 8.dp else 10.dp))
            }
            Text(
                text = label,
                color = if (leadingIcon == null) {
                    renderedContentColor
                } else {
                    renderedContentColor.copy(alpha = IconTextLabelAlpha)
                },
                style = if (compactContent) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.titleLarge
                },
                fontWeight = if (compactContent) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = if (compactContent) 1 else Int.MAX_VALUE,
            )
        }
        if (leadingIconSlotWidth == null) {
            content()
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                content()
            }
        }
    }
}

internal fun secondaryButtonContentColor(colors: MapControlColors): Color = when {
    colors.background.luminance() <= 0.5f -> colors.background
    colors.foreground.luminance() <= 0.5f -> colors.foreground
    else -> Ink
}
