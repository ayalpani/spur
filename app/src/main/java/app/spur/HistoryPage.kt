package app.spur

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
internal fun HistoryPage(
    store: TourStore,
    revision: Long,
    isVisible: Boolean = true,
    onBack: () -> Unit,
    onEditTour: (Long) -> Unit,
    showFeedbackNotice: ShowFeedbackNotice = { _, _ -> },
    photoRevision: Long = 0L,
    onPhotoRotated: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tourListState = rememberLazyListState()
    var tours by remember { mutableStateOf(emptyList<Tour>()) }
    var mapMoments by remember { mutableStateOf(emptyList<MapMoment>()) }
    var selectedMoment by remember { mutableStateOf<MapMoment?>(null) }
    var isPhotoDetailVisible by remember { mutableStateOf(false) }
    var isMediaDetailVisible by remember { mutableStateOf(false) }
    val visualsByTour = remember(tours, mapMoments) {
        tours.associate { tour ->
            tour.id to visualMomentsForTour(mapMoments, tour)
        }
    }
    val historyPhotos = remember(visualsByTour) {
        orderedPhotoMoments(
            visualsByTour.values
                .flatten()
                .distinctBy(MapMoment::id),
        )
    }
    val selectedTourIndex = remember(selectedMoment?.id, tours, visualsByTour) {
        val selectedId = selectedMoment?.id
        tours.indexOfFirst { tour ->
            visualsByTour[tour.id].orEmpty().any { it.id == selectedId }
        }
    }
    BackHandler(
        enabled = isVisible &&
            !isPhotoDetailVisible &&
            !isMediaDetailVisible,
        onBack = onBack,
    )
    LaunchedEffect(revision) {
        val (loadedTours, loadedMoments) = withContext(Dispatchers.IO) {
            val moments = context.loadMapMoments()
            moments
                .filter { it.type == MomentType.VIDEO }
                .forEach { ensureVideoThumbnail(File(it.payload)) }
            store.tours() to moments
        }
        tours = loadedTours
        mapMoments = loadedMoments
    }
    LaunchedEffect(historyPhotos, selectedMoment?.id) {
        val selected = selectedMoment ?: return@LaunchedEffect
        if (
            selected.type == MomentType.PHOTO &&
            historyPhotos.none { it.id == selected.id }
        ) {
            isPhotoDetailVisible = false
            selectedMoment = null
        }
    }
    LaunchedEffect(selectedMoment?.id, selectedTourIndex) {
        if (selectedTourIndex < 0) return@LaunchedEffect
        val margin = with(context.resources.displayMetrics) { (24 * density).roundToInt() }
        val item = tourListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == selectedTourIndex }
        val viewportStart = tourListState.layoutInfo.viewportStartOffset + margin
        val viewportEnd = tourListState.layoutInfo.viewportEndOffset - margin
        if (
            item == null ||
            item.offset < viewportStart ||
            item.offset + item.size > viewportEnd
        ) {
            tourListState.animateScrollToItem(
                index = selectedTourIndex,
                scrollOffset = -margin,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Sand)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
            ) {
                BackIcon()
            }
            Text(
                text = "Deine Touren",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (tours.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Noch keine Touren",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Deine aufgezeichneten Wege erscheinen hier.",
                    modifier = Modifier.padding(top = 6.dp),
                    color = Ink.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                state = tourListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            ) {
                items(tours, key = { it.id }) { tour ->
                    val visuals = visualsByTour[tour.id].orEmpty()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditTour(tour.id) },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp)
                                        .padding(
                                            bottom = if (visuals.isEmpty()) 0.dp else 4.dp,
                                        ),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tour.activity ?: formatDate(tour.startedAt),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = if (tour.activity == null) {
                                                formatTourTime(tour)
                                            } else {
                                                "${formatDate(tour.startedAt)} · " +
                                                    formatTourTime(tour)
                                            },
                                            modifier = Modifier.padding(top = 3.dp),
                                            color = Ink.copy(alpha = 0.56f),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                    Text(
                                        text = formatMeters(tour.distanceMeters),
                                        color = Moss,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                if (visuals.isNotEmpty()) {
                                    TourMomentStrip(
                                        moments = visuals,
                                        photoRevision = photoRevision,
                                        selectedMomentId = selectedMoment?.id,
                                        onOpen = {
                                            selectedMoment = it
                                            if (it.type == MomentType.PHOTO) {
                                                isPhotoDetailVisible = true
                                            } else if (it.type == MomentType.VIDEO) {
                                                isMediaDetailVisible = true
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isPhotoDetailVisible) selectedMoment
        ?.takeIf { it.type == MomentType.PHOTO }
        ?.let { photo ->
        PhotoDetailPage(
            photos = historyPhotos,
            initialPhotoId = photo.id,
            showFeedbackNotice = showFeedbackNotice,
            photoRevision = photoRevision,
            onPhotoChanged = { selectedMoment = it },
            onPhotoRotated = onPhotoRotated,
            onPhotoDeleted = { deletedPhoto ->
                scope.launch {
                    val updatedMoments = context.deleteMapMoment(
                        moment = deletedPhoto,
                        moments = mapMoments,
                    )
                    if (updatedMoments == null) {
                        showFeedbackNotice(
                            FeedbackNoticeKind.ERROR,
                            "Das Bild konnte nicht gelöscht werden.",
                        )
                    } else {
                        mapMoments = updatedMoments
                        selectedMoment = null
                        isPhotoDetailVisible = false
                    }
                }
            },
            onDismiss = { isPhotoDetailVisible = false },
        )
    }

    if (isMediaDetailVisible) selectedMoment
        ?.takeIf { it.type == MomentType.VIDEO }
        ?.let { video ->
            MediaMomentDetailPage(
                moment = video,
                onDismiss = { isMediaDetailVisible = false },
            )
        }
}

@Composable
internal fun TourMomentStrip(
    moments: List<MapMoment>,
    photoRevision: Long,
    selectedMomentId: String?,
    onOpen: (MapMoment) -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val selectedIndex = remember(moments, selectedMomentId) {
        moments.indexOfFirst { it.id == selectedMomentId }
    }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0) return@LaunchedEffect
        val margin = with(context.resources.displayMetrics) { (18 * density).roundToInt() }
        val item = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == selectedIndex }
        val viewportStart = listState.layoutInfo.viewportStartOffset + margin
        val viewportEnd = listState.layoutInfo.viewportEndOffset - margin
        if (
            item == null ||
            item.offset < viewportStart ||
            item.offset + item.size > viewportEnd
        ) {
            listState.animateScrollToItem(
                index = selectedIndex,
                scrollOffset = -margin,
            )
        }
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            bottom = 18.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(moments, key = { it.id }) { moment ->
            val isSelected = moment.id == selectedMomentId
            val previewFile = remember(moment.payload, moment.type) {
                if (moment.type == MomentType.VIDEO) {
                    videoThumbnailFile(File(moment.payload))
                } else {
                    File(moment.payload)
                }
            }
            val imageRequest = remember(previewFile, photoRevision) {
                ImageRequest.Builder(context)
                    .data(previewFile)
                    .memoryCacheKey("${previewFile.absolutePath}:$photoRevision")
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build()
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .border(
                        width = 3.dp,
                        color = if (isSelected) {
                            TourMomentSelectionYellow
                        } else {
                            Color.Transparent
                        },
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(Mist)
                    .clickable { onOpen(moment) }
                    .semantics {
                        selected = isSelected
                        contentDescription = if (moment.type == MomentType.VIDEO) {
                            "Video dieser Tour öffnen"
                        } else {
                            "Foto dieser Tour öffnen"
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (moment.type == MomentType.VIDEO) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(Ink.copy(alpha = 0.62f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlayIcon(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}
