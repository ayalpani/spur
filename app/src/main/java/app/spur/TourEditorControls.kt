package app.spur

import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale
import kotlin.math.abs

internal sealed interface EditorDeleteTarget {
    data class Location(val point: TrackPoint) : EditorDeleteTarget
    data class Moment(val moment: MapMoment) : EditorDeleteTarget
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun EditorDeleteSheet(
    title: String,
    primaryLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    EditorDeleteSheet(
        title = title,
        primaryLabel = primaryLabel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun EditorDeleteSheet(
    title: String,
    primaryLabel: String,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    SpurModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BottomSheetHeader(
                title = title,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            SpurPrimaryButton(
                label = primaryLabel,
                onClick = onConfirm,
                destructive = true,
            )
            SpurSecondaryButton(
                label = "Abbrechen",
                onClick = onDismiss,
            )
        }
    }
}

@Composable
internal fun WaypointRail(
    locations: List<EditorLocation>,
    selectedPointId: Long?,
    focusRequest: Long = 0L,
    followLatest: Boolean = false,
    emptyText: String = "Keine Wegpunkte aufgezeichnet.",
    backgroundColor: Color = Color.White,
    onScrollInProgressChanged: (Boolean) -> Unit = {},
    onSelected: (Long) -> Unit,
) {
    if (locations.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WaypointRailHeight)
                .background(backgroundColor)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = emptyText,
                color = Ink.copy(alpha = 0.46f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    val resolvedSelectedPointId = if (followLatest) {
        locations.last().point.id
    } else {
        selectedPointId ?: locations.last().point.id
    }
    val initialIndex = locations.indexOfFirst {
        it.point.id == resolvedSelectedPointId
    }
        .coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val currentOnScrollInProgressChanged by rememberUpdatedState(
        onScrollInProgressChanged,
    )
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val tickTone = remember {
        runCatching {
            ToneGenerator(
                AudioManager.STREAM_MUSIC,
                WaypointTickVolumePercent,
            )
        }.getOrNull()
    }
    DisposableEffect(tickTone) {
        onDispose { tickTone?.release() }
    }
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    var lastTickedIndex by remember(
        locations.first().point.id,
        locations.last().point.id,
        locations.size,
    ) {
        mutableIntStateOf(initialIndex)
    }
    val fling = rememberSnapFlingBehavior(
        lazyListState = state,
        snapPosition = SnapPosition.Center,
    )
    val endpointDragState = rememberDraggableState { delta ->
        state.dispatchRawDelta(-delta)
    }
    val finishEndpointDrag: suspend (Float) -> Unit = { velocity ->
        try {
            state.scroll {
                with(fling) { performFling(-velocity) }
            }
        } finally {
            currentOnScrollInProgressChanged(false)
        }
    }

    suspend fun centerVisibleItem(
        index: Int,
        animated: Boolean,
        animateEndpointArrival: Boolean = false,
    ) {
        var item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (item == null) {
            val layout = state.layoutInfo
            val endpointApproachDistance = (
                (layout.viewportEndOffset - layout.viewportStartOffset) / 2f -
                    with(density) { (WaypointEndpointLabelWidth - 3.dp).toPx() }
                ).coerceAtLeast(0f)
            state.scrollToItem(index)
            if (animated && animateEndpointArrival && endpointApproachDistance > 0.5f) {
                state.scrollBy(
                    if (index == 0) {
                        endpointApproachDistance
                    } else {
                        -endpointApproachDistance
                    },
                )
            }
            withFrameNanos { }
            item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        }
        item?.let {
            val layout = state.layoutInfo
            val viewportCenter =
                (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            val itemCenter = it.offset + it.size / 2
            val distance = (itemCenter - viewportCenter).toFloat()
            if (abs(distance) > 0.5f) {
                if (animated) {
                    state.animateScrollBy(
                        value = distance,
                        animationSpec = tween(MotionDurationDefaultMillis),
                    )
                } else {
                    state.scrollBy(distance)
                }
            }
        }
    }

    fun selectAndCenter(index: Int) {
        val pointId = locations.getOrNull(index)?.point?.id ?: return
        isProgrammaticScroll = true
        onSelected(pointId)
        scope.launch {
            try {
                centerVisibleItem(
                    index = index,
                    animated = true,
                    animateEndpointArrival = index == 0 || index == locations.lastIndex,
                )
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    LaunchedEffect(state, locations.size) {
        isProgrammaticScroll = true
        try {
            centerVisibleItem(initialIndex, animated = false)
        } finally {
            isProgrammaticScroll = false
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .collect(currentOnScrollInProgressChanged)
    }

    DisposableEffect(state) {
        onDispose { currentOnScrollInProgressChanged(false) }
    }

    LaunchedEffect(state, focusRequest) {
        if (focusRequest == 0L) return@LaunchedEffect
        val index = locations.indexOfFirst {
            it.point.id == selectedPointId
        }.takeIf { it >= 0 } ?: return@LaunchedEffect
        isProgrammaticScroll = true
        try {
            centerVisibleItem(index, animated = true)
        } finally {
            isProgrammaticScroll = false
        }
    }

    LaunchedEffect(state, locations) {
        snapshotFlow {
            val layout = state.layoutInfo
            val center =
                (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull {
                abs(it.offset + it.size / 2 - center)
            }?.index
        }
            .distinctUntilChanged()
            .collect { index ->
                if (!isProgrammaticScroll) index?.let {
                    if (it != lastTickedIndex) {
                        tickTone?.stopTone()
                        tickTone?.startTone(
                            ToneGenerator.TONE_CDMA_PIP,
                            WaypointTickDurationMillis,
                        )
                    }
                    lastTickedIndex = it
                    locations.getOrNull(it)?.point?.id?.let(onSelected)
                }
            }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(WaypointRailHeight)
            .background(backgroundColor),
    ) {
        val selectedIndex = locations.indexOfFirst {
            it.point.id == resolvedSelectedPointId
        }.coerceAtLeast(0)
        val itemWidth = 10.dp
        val edgePadding = (maxWidth - itemWidth) / 2
        val endpointOffsets by remember(
            state,
            locations.size,
            edgePadding,
            maxWidth,
            density,
            followLatest,
        ) {
            derivedStateOf {
                val visibleItems = state.layoutInfo.visibleItemsInfo
                val maximumOffset =
                    (maxWidth - WaypointEndpointLabelWidth).coerceAtLeast(0.dp)
                fun endpointCenter(index: Int) = visibleItems
                    .firstOrNull { it.index == index }
                    ?.let { item ->
                        edgePadding + with(density) {
                            (item.offset + item.size / 2).toDp()
                        }
                    }
                val startOffset = endpointCenter(0)
                    ?.minus(WaypointEndpointLabelWidth - 3.dp)
                    ?.coerceIn(0.dp, maximumOffset)
                    ?: 0.dp
                val endOffset = if (followLatest) {
                    (maxWidth / 2 - 3.dp).coerceIn(0.dp, maximumOffset)
                } else {
                    endpointCenter(locations.lastIndex)
                        ?.minus(3.dp)
                        ?.coerceIn(0.dp, maximumOffset)
                        ?: maximumOffset
                }
                startOffset to endOffset
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(1f)
                .padding(top = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = formatClock(locations[selectedIndex].point.recordedAt),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = waypointPositionText(selectedIndex, locations.size),
                color = Ink.copy(alpha = 0.46f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
        LazyRow(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(horizontal = edgePadding),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            items(
                count = locations.size,
                key = { locations[it].point.id },
            ) { index ->
                val location = locations[index]
                val interactionSource = remember(location.point.id) {
                    MutableInteractionSource()
                }
                val isPressed by interactionSource.collectIsPressedAsState()
                Box(
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { selectAndCenter(index) },
                        )
                        .semantics {
                            contentDescription =
                                "Wegpunkt ${index + 1} von ${locations.size}"
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        if (location.moments.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(Moss, CircleShape),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .padding(bottom = 14.dp)
                                .width(6.dp)
                                .height(22.dp)
                                .border(
                                    width = 1.dp,
                                    color = Ink.copy(alpha = if (isPressed) 0.46f else 0f),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(20.dp)
                                    .background(Ink.copy(alpha = 0.16f), CircleShape),
                            )
                        }
                    }
                }
            }
        }
        WaypointEndpointLabel(
            label = "Start",
            timestamp = locations.first().point.recordedAt,
            backgroundColor = backgroundColor,
            onClick = { selectAndCenter(0) },
            dragState = endpointDragState,
            onDragStarted = { currentOnScrollInProgressChanged(true) },
            onDragStopped = finishEndpointDrag,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = endpointOffsets.first, y = (-2).dp),
        )
        WaypointEndpointLabel(
            label = "Ende",
            timestamp = locations.last().point.recordedAt,
            backgroundColor = backgroundColor,
            onClick = { selectAndCenter(locations.lastIndex) },
            dragState = endpointDragState,
            onDragStarted = { currentOnScrollInProgressChanged(true) },
            onDragStopped = finishEndpointDrag,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = endpointOffsets.second, y = (-2).dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .width(2.dp)
                .height(32.dp)
                .background(Ink, CircleShape),
        )
    }
}

@Composable
private fun WaypointEndpointLabel(
    label: String,
    timestamp: Long,
    backgroundColor: Color,
    onClick: () -> Unit,
    dragState: DraggableState,
    onDragStarted: () -> Unit,
    onDragStopped: suspend (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .requiredWidth(WaypointEndpointLabelWidth)
            .height(48.dp)
            .background(backgroundColor)
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStarted = { onDragStarted() },
                onDragStopped = { velocity -> onDragStopped(velocity) },
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label der Tour" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            color = Ink.copy(alpha = 0.28f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = formatClock(timestamp),
            color = Ink.copy(alpha = 0.46f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private val WaypointEndpointLabelWidth = 72.dp

internal fun waypointPositionText(selectedIndex: Int, total: Int): String =
    "${selectedIndex.coerceAtLeast(0) + 1}/${total.coerceAtLeast(0)}"

internal fun MomentType.editorLabel(): String = when (this) {
    MomentType.PHOTO -> "Foto"
    MomentType.VIDEO -> "Video"
    MomentType.VOICE -> "Sprachnachricht"
    MomentType.EMOJI -> "Emoji"
}

internal fun MapMoment.editorLabel(): String =
    if (isRoundVideo) "Selfie-Video" else type.editorLabel()

internal fun formatEditorElapsed(millis: Long): String {
    val minutes = millis.coerceAtLeast(0L) / 60_000
    return String.format(Locale.getDefault(), "%d:%02d", minutes / 60, minutes % 60)
}
