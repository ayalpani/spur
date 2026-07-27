package app.spur

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

internal sealed interface MomentPlacementTarget {
    data object CurrentLocation : MomentPlacementTarget

    data class Waypoint(
        val tourId: Long,
        val trackPointId: Long,
        val coordinate: SpurCoordinate,
    ) : MomentPlacementTarget
}

@Composable
@androidx.compose.material3.ExperimentalMaterial3Api
internal fun MomentPickerSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSelect: (MomentType) -> Unit,
) {
    ModalBottomSheet(
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
            Text(
                text = "Auf der Karte ablegen",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Was möchtest du an dieser Stelle festhalten?",
                style = MaterialTheme.typography.bodyLarge,
            )
            listOf(
                MomentType.VOICE to "Sprachnachricht",
                MomentType.EMOJI to "Emoji",
                MomentType.VIDEO to "Video",
                MomentType.PHOTO to "Foto",
            ).forEach { (type, label) ->
                OutlinedButton(
                    onClick = { onSelect(type) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = CircleShape,
                ) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
internal fun TourEditorDialog(
    tour: Tour,
    points: List<TrackPoint>,
    moments: List<MapMoment>,
    onDismiss: () -> Unit,
    onAddMoment: (MomentPlacementTarget.Waypoint) -> Unit,
    onDeleteMoment: (MapMoment) -> Unit,
    onDeleteTour: () -> Unit,
    onOpenMoment: (MapMoment) -> Unit,
    mapContent: @Composable (
        modifier: Modifier,
        focus: SpurCoordinate?,
        selectedTrackPointId: Long?,
    ) -> Unit,
) {
    var selectedId by rememberSaveable(tour.id) {
        mutableStateOf(points.lastOrNull()?.id)
    }
    val waypoints = remember(tour, points, moments, selectedId) {
        buildEditorWaypoints(tour, points, moments, selectedId)
    }
    val selected = waypoints.firstOrNull { it.trackPoint.id == selectedId }
        ?: waypoints.lastOrNull()
    var confirmTourDelete by remember { mutableStateOf(false) }
    var momentToDelete by remember { mutableStateOf<MapMoment?>(null) }

    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(EditorSand),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                mapContent(
                    Modifier.fillMaxSize(),
                    selected?.trackPoint?.let {
                        SpurCoordinate(it.latitude, it.longitude)
                    },
                    selected?.trackPoint?.id,
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EditorMapButton("Editor schließen", onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    EditorMapButton("Tour löschen", { confirmTourDelete = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                if (selected == null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Diese Tour hat noch keine Wegmarken.",
                            color = EditorInk.copy(alpha = 0.62f),
                        )
                    }
                } else {
                    WaypointDetails(
                        waypoint = selected,
                        index = waypoints.indexOf(selected),
                        count = waypoints.size,
                        onAdd = {
                            onAddMoment(
                                MomentPlacementTarget.Waypoint(
                                    tourId = tour.id,
                                    trackPointId = selected.trackPoint.id,
                                    coordinate = SpurCoordinate(
                                        selected.trackPoint.latitude,
                                        selected.trackPoint.longitude,
                                    ),
                                ),
                            )
                        },
                        onOpenMoment = onOpenMoment,
                        onDeleteMoment = { momentToDelete = it },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                    WaypointRail(
                        waypoints = waypoints,
                        selectedId = selected.trackPoint.id,
                        onSelected = { selectedId = it },
                    )
                }
            }
        }
    }

    if (confirmTourDelete) {
        AlertDialog(
            onDismissRequest = { confirmTourDelete = false },
            title = { Text("Tour wirklich löschen?") },
            text = { Text("Die Route und alle zugehörigen Anhänge werden dauerhaft entfernt.") },
            confirmButton = {
                TextButton(onClick = onDeleteTour) { Text("Tour löschen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmTourDelete = false }) { Text("Abbrechen") }
            },
        )
    }

    momentToDelete?.let { moment ->
        AlertDialog(
            onDismissRequest = { momentToDelete = null },
            title = { Text("Anhang löschen?") },
            text = { Text("Das Foto wird auch vom Gerät entfernt.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteMoment(moment)
                        momentToDelete = null
                    },
                ) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { momentToDelete = null }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun WaypointDetails(
    waypoint: EditorWaypoint,
    index: Int,
    count: Int,
    onAdd: () -> Unit,
    onOpenMoment: (MapMoment) -> Unit,
    onDeleteMoment: (MapMoment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Wegmarke ${index + 1} von $count",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${formatEditorDistance(waypoint.distanceFromStartMeters)} · " +
                        "${formatEditorDuration(waypoint.elapsedMillis)} seit Start · " +
                        DateFormat.getTimeInstance(DateFormat.SHORT)
                            .format(Date(waypoint.trackPoint.recordedAt)),
                    color = EditorInk.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = onAdd,
                modifier = Modifier.size(52.dp),
                contentPadding = PaddingValues(0.dp),
                shape = CircleShape,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Moment hinzufügen")
            }
        }

        waypoint.moments.forEach { moment ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenMoment(moment) },
                color = Color.White,
                shape = CircleShape,
            ) {
                Row(
                    modifier = Modifier.padding(start = 18.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = moment.type.editorLabel(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    IconButton(onClick = { onDeleteMoment(moment) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Anhang löschen")
                    }
                }
            }
        }
    }
}

@Composable
private fun WaypointRail(
    waypoints: List<EditorWaypoint>,
    selectedId: Long,
    onSelected: (Long) -> Unit,
) {
    val initialIndex = waypoints.indexOfFirst { it.trackPoint.id == selectedId }
        .coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val scope = rememberCoroutineScope()
    val flingBehavior = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Center,
    )

    LaunchedEffect(listState, waypoints) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { scrolling -> !scrolling }
            .collect {
                val layout = listState.layoutInfo
                val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
                val centered = layout.visibleItemsInfo.minByOrNull { item ->
                    abs(item.offset + item.size / 2 - center)
                }
                centered?.index?.let { index ->
                    waypoints.getOrNull(index)?.trackPoint?.id?.let(onSelected)
                }
            }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .navigationBarsPadding()
            .background(Color.White),
    ) {
        val edgePadding = (maxWidth - RailItemWidth) / 2
        LazyRow(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(horizontal = edgePadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                count = waypoints.size,
                key = { waypoints[it].trackPoint.id },
            ) { index ->
                val waypoint = waypoints[index]
                Box(
                    modifier = Modifier
                        .width(RailItemWidth)
                        .height(104.dp)
                        .border(
                            BorderStroke(
                                width = 0.5.dp,
                                color = EditorInk.copy(alpha = 0.16f),
                            ),
                        )
                        .clickable {
                            scope.launch { listState.animateScrollToItem(index) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${index + 1}",
                            fontWeight = FontWeight.SemiBold,
                            color = if (waypoint.trackPoint.id == selectedId) {
                                EditorMoss
                            } else {
                                EditorInk
                            },
                        )
                        if (waypoint.moments.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 7.dp)
                                    .size(5.dp)
                                    .background(EditorMoss, CircleShape),
                            )
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(RailItemWidth)
                .height(104.dp)
                .border(2.dp, EditorMoss),
        )
    }
}

@Composable
private fun EditorMapButton(
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .semantics { contentDescription = description },
        color = Color.White,
        shape = CircleShape,
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

private fun MomentType.editorLabel() = when (this) {
    MomentType.PHOTO -> "Foto"
    MomentType.VIDEO -> "Video"
    MomentType.VOICE -> "Sprachnachricht"
    MomentType.EMOJI -> "Emoji"
}

private fun formatEditorDistance(meters: Double) =
    String.format(Locale.getDefault(), "%.2f km", meters / 1_000.0)

private fun formatEditorDuration(millis: Long): String {
    val minutes = millis.coerceAtLeast(0L) / 60_000
    return String.format(Locale.getDefault(), "%d:%02d", minutes / 60, minutes % 60)
}

private val EditorSand = Color(0xFFF7F5F0)
private val EditorInk = Color(0xFF18201C)
private val EditorMoss = Color(0xFF23614A)
private val RailItemWidth = 72.dp
