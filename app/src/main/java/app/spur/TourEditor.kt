package app.spur

import android.location.Location
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
private fun TourEditorScreen(
    store: TourStore,
    tourId: Long,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    showFeedbackNotice: ShowFeedbackNotice,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tour by remember(tourId) { mutableStateOf<Tour?>(null) }
    var points by remember(tourId) { mutableStateOf(emptyList<TrackPoint>()) }
    var moments by remember(tourId) { mutableStateOf(emptyList<MapMoment>()) }
    var selectedPointId by rememberSaveable(tourId) { mutableStateOf<Long?>(null) }
    var placementTarget by remember { mutableStateOf<MomentPlacementTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<EditorDeleteTarget?>(null) }

    LaunchedEffect(tourId) {
        while (true) {
            val result = withContext(Dispatchers.IO) {
                val loadedTour = store.tour(tourId) ?: return@withContext null
                Triple(
                    loadedTour,
                    store.points(tourId),
                    mapMomentsForTour(context.loadMapMoments(), loadedTour),
                )
            } ?: return@LaunchedEffect
            val isInitialLoad = tour == null
            tour = result.first
            points = result.second
            moments = result.third
            if (isInitialLoad) selectedPointId = result.second.lastOrNull()?.id
            if (result.first.endedAt != null) return@LaunchedEffect
            delay(1_000)
        }
    }

    val currentTour = tour
    val locations = remember(currentTour, points, moments) {
        currentTour?.let {
            editorLocations(
                tour = it,
                points = points,
                moments = moments,
            )
        }.orEmpty()
    }
    val selectedLocation = locations.firstOrNull { it.point.id == selectedPointId }
        ?: locations.lastOrNull()

    suspend fun saveMoments(updated: List<MapMoment>) {
        withContext(Dispatchers.IO) {
            val editorMomentIds = (moments + updated).mapTo(mutableSetOf(), MapMoment::id)
            context.saveMapMoments(
                context.loadMapMoments().filterNot { it.id in editorMomentIds } + updated,
            )
        }
        moments = updated
        onChanged()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Sand),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            TourEditorMap(
                points = points,
                selectedPoint = selectedLocation?.point,
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 18.dp, top = 14.dp),
                color = Color.White,
                shape = CircleShape,
                shadowElevation = 2.dp,
            ) {
                Text(
                    text = "Tour bearbeiten",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = MapControlHorizontalPadding, top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(MapControlGap),
            ) {
                MapIconButton(
                    contentDescription = "Editor schließen",
                    onClick = onBack,
                    secondary = true,
                ) {
                    BackIcon()
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(Sand),
        ) {
            if (selectedLocation == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Keine Wegpunkte",
                        color = Ink.copy(alpha = 0.58f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatClock(selectedLocation.point.recordedAt),
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Uhrzeit",
                                    color = Ink.copy(alpha = 0.54f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    enabled = points.size > 1,
                                    onClick = {
                                        deleteTarget =
                                            EditorDeleteTarget.Location(
                                                selectedLocation.point,
                                            )
                                    },
                                    modifier = Modifier
                                        .size(52.dp)
                                        .semantics {
                                            contentDescription = "Wegpunkt löschen"
                                        },
                                    contentPadding = PaddingValues(0.dp),
                                    shape = CircleShape,
                                    border = BorderStroke(
                                        1.dp,
                                        Ink.copy(alpha = 0.18f),
                                    ),
                                ) {
                                    PhotoDeleteIcon(color = Ink)
                                }
                                Button(
                                    onClick = {
                                        placementTarget =
                                            MomentPlacementTarget.RecordedLocation(
                                                tourId = tourId,
                                                trackPointId = selectedLocation.point.id,
                                                coordinate = SpurCoordinate(
                                                    selectedLocation.point.latitude,
                                                    selectedLocation.point.longitude,
                                                ),
                                            )
                                    },
                                    modifier = Modifier
                                        .size(52.dp)
                                        .semantics {
                                            contentDescription =
                                                "Moment an diesem Wegpunkt hinzufügen"
                                        },
                                    contentPadding = PaddingValues(0.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Ink,
                                    ),
                                ) {
                                    PlusIcon()
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatEditorElapsed(
                                        selectedLocation.elapsedMillis,
                                    ),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "seit Start",
                                    color = Ink.copy(alpha = 0.54f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatKilometers(
                                        selectedLocation.distanceFromStartMeters,
                                    ),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "vom Start",
                                    color = Ink.copy(alpha = 0.54f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                    }

                    selectedLocation.moments.forEach { moment ->
                        Surface(
                            color = Color.White,
                            shape = CircleShape,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp, end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = moment.type.editorLabel(),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                IconButton(
                                    onClick = {
                                        deleteTarget = EditorDeleteTarget.Moment(moment)
                                    },
                                ) {
                                    PhotoDeleteIcon(color = Ink)
                                }
                            }
                        }
                    }
                }
                WaypointRail(
                    locations = locations,
                    selectedPointId = selectedLocation.point.id,
                    onSelected = { selectedPointId = it },
                )
            }
        }
    }

    MomentComposer(
        target = placementTarget,
        showFeedbackNotice = showFeedbackNotice,
        onDismiss = { placementTarget = null },
        onMomentAccepted = { target, pending ->
            val location = target as? MomentPlacementTarget.RecordedLocation
            if (location == null) {
                pending.deletePayload()
            } else {
                val moment = MapMoment(
                    id = pending.id,
                    type = pending.type,
                    latitude = location.coordinate.latitude,
                    longitude = location.coordinate.longitude,
                    payload = pending.payload,
                    tourId = location.tourId,
                    trackPointId = location.trackPointId,
                )
                scope.launch { saveMoments(moments + moment) }
            }
            placementTarget = null
        },
    )

    deleteTarget?.let { target ->
        EditorDeleteSheet(
            title = when (target) {
                is EditorDeleteTarget.Location -> "Wegpunkt löschen?"
                is EditorDeleteTarget.Moment -> "${target.moment.type.editorLabel()} löschen?"
            },
            primaryLabel = when (target) {
                is EditorDeleteTarget.Location -> "Wegpunkt löschen"
                is EditorDeleteTarget.Moment -> "${target.moment.type.editorLabel()} löschen"
            },
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                when (target) {
                    is EditorDeleteTarget.Moment -> scope.launch {
                        val updated = context.deleteMapMoment(
                            moment = target.moment,
                            moments = context.loadMapMoments(),
                        )
                        if (updated == null) {
                            showFeedbackNotice(
                                FeedbackNoticeKind.ERROR,
                                "${target.moment.type.editorLabel()} konnte nicht gelöscht werden.",
                            )
                        } else {
                            moments = mapMomentsForTour(updated, currentTour ?: return@launch)
                            onChanged()
                        }
                    }
                    is EditorDeleteTarget.Location -> scope.launch {
                        val deletedIndex = points.indexOf(target.point)
                        val retained = points.filterNot { it.id == target.point.id }
                        val retainedIds = retained.mapTo(mutableSetOf(), TrackPoint::id)
                        withContext(Dispatchers.IO) {
                            store.updateTourPoints(tourId, retainedIds)
                        }
                        points = retained
                        saveMoments(
                            moments.map { moment ->
                                if (moment.trackPointId != target.point.id) {
                                    moment
                                } else {
                                    moment.copy(
                                        trackPointId = nearestTrackPoint(
                                            retained,
                                            moment.latitude,
                                            moment.longitude,
                                        )?.id,
                                    )
                                }
                            },
                        )
                        selectedPointId = retained.getOrNull(
                            deletedIndex.coerceAtMost(retained.lastIndex),
                        )?.id
                        tour = withContext(Dispatchers.IO) { store.tour(tourId) }
                    }
                }
            },
        )
    }
}
