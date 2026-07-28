package app.spur

import android.location.Location
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    SpurModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape,
            ) {
                Text("Abbrechen", color = Ink)
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ink,
                    contentColor = Color.White,
                ),
            ) {
                Text(primaryLabel)
            }
        }
    }
}

@Composable
internal fun EditorLocationRail(
    locations: List<EditorLocation>,
    selectedPointId: Long,
    onSelected: (Long) -> Unit,
) {
    val initialIndex = locations.indexOfFirst { it.point.id == selectedPointId }
        .coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val scope = rememberCoroutineScope()
    val fling = rememberSnapFlingBehavior(
        lazyListState = state,
        snapPosition = SnapPosition.Center,
    )

    suspend fun centerVisibleItem(index: Int, animated: Boolean) {
        var item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (item == null) {
            state.scrollToItem(index)
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

    LaunchedEffect(state, locations.size) {
        centerVisibleItem(initialIndex, animated = false)
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
                index?.let {
                    locations.getOrNull(it)?.point?.id?.let(onSelected)
                }
            }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(EditorLocationRailHeight)
            .background(Color.White),
    ) {
        val selectedIndex = locations.indexOfFirst {
            it.point.id == selectedPointId
        }.coerceAtLeast(0)
        val itemWidth = 10.dp
        val edgePadding = (maxWidth - itemWidth) / 2
        Text(
            text = "${selectedIndex + 1} von ${locations.size}",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(horizontal = edgePadding),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp),
        ) {
            items(
                count = locations.size,
                key = { locations[it].point.id },
            ) { index ->
                val location = locations[index]
                Box(
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clickable {
                            scope.launch { centerVisibleItem(index, animated = true) }
                        }
                        .semantics {
                            contentDescription =
                                "GPS-Punkt ${index + 1} von ${locations.size}"
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (index == 0) {
                        Text(
                            text = "Start",
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(x = (-26).dp, y = (-10).dp)
                                .requiredWidth(40.dp),
                            color = Ink.copy(alpha = 0.28f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (index == locations.lastIndex) {
                        Text(
                            text = "Ende",
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(x = 26.dp, y = (-10).dp)
                                .requiredWidth(40.dp),
                            color = Ink.copy(alpha = 0.28f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
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
                                .padding(bottom = 8.dp)
                                .width(2.dp)
                                .height(20.dp)
                                .background(Ink.copy(alpha = 0.16f), CircleShape),
                        )
                    }
                }
            }
        }
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

internal fun MomentType.editorLabel(): String = when (this) {
    MomentType.PHOTO -> "Foto"
    MomentType.VIDEO -> "Video"
    MomentType.VOICE -> "Sprachnachricht"
    MomentType.EMOJI -> "Emoji"
}

internal fun formatEditorElapsed(millis: Long): String {
    val minutes = millis.coerceAtLeast(0L) / 60_000
    return String.format(Locale.getDefault(), "%d:%02d", minutes / 60, minutes % 60)
}
