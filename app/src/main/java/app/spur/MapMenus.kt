package app.spur

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun StartTourBottomSheet(
    onStartTour: () -> Unit,
) {
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BottomSheetHeader(
            title = "Tour starten",
        )
        Text(
            text = "Spur zeichnet deine Strecke weiter auf, " +
                "auch wenn dein Bildschirm aus ist.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            color = Ink.copy(alpha = 0.62f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(24.dp))
        SpurPrimaryButton(
            label = "Los geht’s",
            onClick = onStartTour,
        )
    }
}

@Composable
internal fun MainMenu(
    onOpenSettings: () -> Unit,
    onOpenGoogleMaps: () -> Unit,
    onRenameTour: (() -> Unit)?,
    onOpenAbout: () -> Unit,
    onShareTour: (() -> Unit)?,
    onStopTour: (() -> Unit)?,
    onDeleteTour: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        SheetMenuNavigationItem(label = "Über Spur", onClick = onOpenAbout)
        SheetMenuNavigationItem(label = "Settings", onClick = onOpenSettings)
        SheetMenuDivider()
        SheetMenuActionItem(
            label = "In G-Maps öffnen",
            onClick = onOpenGoogleMaps,
            icon = { LucideMapIcon(modifier = Modifier.size(SheetMenuIconSize)) },
        )
        onRenameTour?.let {
            SheetMenuActionItem(
                label = "Tour umbenennen",
                onClick = it,
                icon = { PencilIcon(modifier = Modifier.size(SheetMenuIconSize)) },
            )
        }
        if (onShareTour != null || onStopTour != null || onDeleteTour != null) {
            onShareTour?.let {
                SheetMenuActionItem(
                    label = "Tour teilen",
                    onClick = it,
                    icon = { ShareIcon(modifier = Modifier.size(SheetMenuIconSize)) },
                )
            }
            onStopTour?.let {
                SheetMenuActionItem(
                    label = "Tour beenden",
                    onClick = it,
                    destructive = true,
                    icon = {
                        LucideStopIcon(
                            color = StopRed,
                            modifier = Modifier.size(SheetMenuIconSize),
                        )
                    },
                )
            }
            onDeleteTour?.let {
                SheetMenuActionItem(
                    label = "Tour löschen",
                    onClick = it,
                    destructive = true,
                    icon = {
                        PhotoDeleteIcon(
                            color = StopRed,
                            modifier = Modifier.size(SheetMenuIconSize),
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun SettingsMenu(
    onOpenHome: () -> Unit,
    onOpenHomeAutoStart: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenDirection: () -> Unit,
) {
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        BottomSheetHeader(
            title = "Settings",
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SheetMenuNavigationItem(
            label = "Zuhause",
            onClick = onOpenHome,
        )
        SheetMenuNavigationItem(
            label = "Startautomatik",
            onClick = onOpenHomeAutoStart,
        )
        SheetMenuNavigationItem(
            label = "Backup",
            onClick = onOpenBackup,
        )
        SheetMenuNavigationItem(label = "Theme", onClick = onOpenTheme)
        SheetMenuNavigationItem(label = "Himmelsrichtung", onClick = onOpenDirection)
    }
}

@Composable
internal fun BottomSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun SheetMenuNavigationItem(
    label: String,
    onClick: () -> Unit,
) = SheetMenuItem(
    label = label,
    onClick = onClick,
    destructive = false,
    leading = null,
    trailing = true,
)

@Composable
internal fun SheetMenuActionItem(
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
    icon: @Composable () -> Unit,
) = SheetMenuItem(
    label = label,
    onClick = onClick,
    destructive = destructive,
    leading = icon,
    trailing = false,
)

@Composable
internal fun SheetMenuDivider() {
    HorizontalDivider(
        modifier = Modifier
            .testTag(SheetMenuDividerTestTag)
            .padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

internal const val SheetMenuDividerTestTag = "sheet-menu-divider"

@Composable
private fun SheetMenuItem(
    label: String,
    onClick: () -> Unit,
    destructive: Boolean,
    leading: (@Composable () -> Unit)?,
    trailing: Boolean,
) {
    val contentColor = if (destructive) StopRed else Ink
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        color = Color.Transparent,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = if (leading == null) {
                    contentColor
                } else {
                    contentColor.copy(alpha = IconTextLabelAlpha)
                },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = SheetMenuTextSize,
                ),
                fontWeight = FontWeight.Medium,
            )
            if (trailing) {
                LucideIcon(
                    paths = listOf("m9 18 6-6-6-6"),
                    color = Ink,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
internal fun MapRotationPicker(
    compassRotation: Float,
    selectedRotation: MapRotation,
    onSelect: (MapRotation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fixedRotations = MapRotation.entries.filter { it.bearing != null }
    Layout(
        modifier = modifier,
        content = {
            CompassCircle(
                selected = selectedRotation == MapRotation.TRAVEL_DIRECTION,
                onSelect = { onSelect(MapRotation.TRAVEL_DIRECTION) },
            )
            fixedRotations.forEach { rotation ->
                FilterChip(
                    selected = selectedRotation == rotation,
                    onClick = { onSelect(rotation) },
                    label = { Text(rotation.label) },
                )
            }
        },
    ) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val circle = measurables.first().measure(childConstraints)
        val options = measurables.drop(1).map { it.measure(childConstraints) }
        val gap = MapRotationOptionGap.roundToPx()
        val visualInset = FilterChipVisualInset.roundToPx()
        val height = (
            circle.height + 2 * gap + 2 * (options.maxOf { it.height } - visualInset)
        ).coerceIn(constraints.minHeight, constraints.maxHeight)
        val width = constraints.maxWidth
        val centerX = width / 2f
        val centerY = height / 2f

        layout(width, height) {
            circle.placeRelative(
                x = (centerX - circle.width / 2f).roundToInt(),
                y = (centerY - circle.height / 2f).roundToInt(),
            )
            fixedRotations.zip(options).forEach { (rotation, option) ->
                val bearing = checkNotNull(rotation.bearing)
                val angle = (bearing - 90.0 + compassRotation) * PI / 180.0
                val radius = mapRotationOptionCenterDistance(
                    circleRadius = circle.width / 2f,
                    gap = gap.toFloat(),
                    halfWidth = option.width / 2f,
                    halfHeight = (option.height / 2f - visualInset).coerceAtLeast(0f),
                    angleRadians = angle,
                )
                option.placeRelative(
                    x = (centerX + cos(angle).toFloat() * radius - option.width / 2f)
                        .roundToInt(),
                    y = (centerY + sin(angle).toFloat() * radius - option.height / 2f)
                        .roundToInt(),
                )
            }
        }
    }
}

@Composable
private fun CompassCircle(
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Box(
        modifier = Modifier.size(84.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 1.5.dp.toPx()
            drawCircle(
                color = Moss.copy(alpha = 0.3f),
                style = Stroke(width = strokeWidth),
            )
            drawLine(
                color = Moss.copy(alpha = 0.22f),
                start = Offset(size.width / 2, 0f),
                end = Offset(size.width / 2, size.height),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = Moss.copy(alpha = 0.22f),
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = strokeWidth,
            )
        }
        FilterChip(
            selected = selected,
            onClick = onSelect,
            label = {
                TravelDirectionIcon(modifier = Modifier.size(24.dp))
            },
            modifier = Modifier.semantics {
                contentDescription = MapRotation.TRAVEL_DIRECTION.label
            },
        )
    }
}
