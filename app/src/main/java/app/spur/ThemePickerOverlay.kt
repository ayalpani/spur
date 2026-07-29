package app.spur

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun ThemePickerOverlay(
    visible: Boolean,
    selectedTheme: SpurColorTheme,
    onSelect: (SpurColorTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    var sheetVisible by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(visible) {
        if (visible) {
            dismissing = false
            sheetVisible = true
        }
    }
    if (!visible && !sheetVisible) return
    val dismiss: () -> Unit = {
        if (!dismissing) {
            dismissing = true
            sheetVisible = false
            scope.launch {
                delay(PanelMotionDurationMillis.toLong())
                onDismiss()
            }
        }
    }
    BackHandler(enabled = visible, onBack = dismiss)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = dismiss,
            ),
    ) {
        AnimatedVisibility(
            visible = sheetVisible,
            enter = slideInVertically(
                animationSpec = tween(PanelMotionDurationMillis),
                initialOffsetY = { -it },
            ) + fadeIn(tween(PanelMotionDurationMillis)),
            exit = slideOutVertically(
                animationSpec = tween(PanelMotionDurationMillis),
                targetOffsetY = { -it },
            ) + fadeOut(tween(PanelMotionDurationMillis)),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                color = SheetBackground,
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(bottom = 24.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(
                            onClick = dismiss,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .semantics { contentDescription = "Theme-Auswahl schließen" },
                        ) {
                            PhotoCloseIcon()
                        }
                        Text(
                            text = "Theme",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(SpurColorTheme.entries) { theme ->
                            ThemePreview(
                                theme = theme,
                                selected = theme == selectedTheme,
                                onClick = { onSelect(theme) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePreview(
    theme: SpurColorTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(width = 154.dp, height = 192.dp)
            .semantics {
                contentDescription = "Theme ${theme.label}"
                this.selected = selected
            },
        shape = RoundedCornerShape(22.dp),
        color = Sand,
        border = BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) theme.primary.color else Ink.copy(alpha = 0.16f),
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                ThemeMapPreview(theme)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = theme.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (selected) {
                    LucideIcon(
                        paths = listOf("M20 6 9 17l-5-5"),
                        color = theme.primary.color,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeMapPreview(theme: SpurColorTheme) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(size.width * 0.08f, size.height * 0.78f)
                cubicTo(
                    size.width * 0.28f,
                    size.height * 0.72f,
                    size.width * 0.34f,
                    size.height * 0.25f,
                    size.width * 0.72f,
                    size.height * 0.38f,
                )
            }
            drawPath(
                path = path,
                color = theme.primary.color.copy(alpha = TrailStrokeAlpha),
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
            )
            drawPath(
                path = path,
                color = theme.accent.color,
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
            )
            drawLine(
                color = Ink.copy(alpha = 0.08f),
                start = Offset(size.width * 0.1f, size.height * 0.2f),
                end = Offset(size.width * 0.9f, size.height * 0.08f),
                strokeWidth = 2.dp.toPx(),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = theme.secondary.color,
                contentColor = theme.accent.color,
                border = BorderStroke(1.dp, theme.primary.color.copy(alpha = 0.2f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    PlusIcon()
                }
            }
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = theme.primary.color,
                contentColor = theme.secondary.color,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(
                        LocalTrailColors provides theme.trailColors,
                    ) {
                        FollowLocationIcon(selected = true)
                    }
                }
            }
        }
    }
}
