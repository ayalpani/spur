package app.spur

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val HistoryThumbnailSize = 80.dp
private const val InitialHistoryTourCount = 10
private const val RecentDaySectionCount = 7L
private const val DatedDaySectionCount = 30L

private data class HistoryTourItem(
    val tour: Tour,
    val place: TourPlaceMetadata?,
    val preview: File?,
    val moments: List<MapMoment>,
)

private data class HistorySection(
    val label: String,
    val dateLabel: String?,
    val tours: List<HistoryTourItem>,
)

@Composable
internal fun HomeScreen(
    store: TourStore,
    revision: Long,
    loadingEnabled: Boolean,
    backEnabled: Boolean,
    openingTourId: Long? = null,
    onBack: () -> Unit,
    onOpenTour: (Long) -> Unit,
    onOpenPhoto: (MapMoment, List<MapMoment>) -> Unit,
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(emptyList<HistoryTourItem>()) }
    var isLoading by remember { mutableStateOf(true) }

    suspend fun loadItems(
        limit: Int? = null,
        offset: Int = 0,
    ): List<HistoryTourItem> = withContext(Dispatchers.IO) {
        val moments = context.loadMapMoments()
        store.tours(limit = limit, offset = offset).map { tour ->
            val tourMoments = visualMomentsForTour(moments, tour)
            tourMoments
                .filter { it.type == MomentType.VIDEO }
                .forEach { ensureVideoThumbnail(File(it.payload)) }
            HistoryTourItem(
                tour = tour,
                place = context.loadTourPlaceMetadata(tour.id),
                preview = context.tourPreviewFile(tour.id)
                    .takeIf { it.isFile && it.length() > 0L },
                moments = tourMoments,
            )
        }
    }

    LaunchedEffect(loadingEnabled, revision) {
        if (!loadingEnabled) return@LaunchedEffect
        var loadedItems = if (isLoading) {
            val newestItems = loadItems(limit = InitialHistoryTourCount)
            items = newestItems
            isLoading = false
            var progressivelyLoadedItems = newestItems
            var offset = InitialHistoryTourCount
            do {
                val nextItems = loadItems(
                    limit = InitialHistoryTourCount,
                    offset = offset,
                )
                progressivelyLoadedItems = (progressivelyLoadedItems + nextItems)
                    .distinctBy { it.tour.id }
                items = progressivelyLoadedItems
                offset += nextItems.size
                yield()
            } while (nextItems.size == InitialHistoryTourCount)
            progressivelyLoadedItems
        } else {
            loadItems()
        }
        items = loadedItems
        loadedItems
            .filter { item ->
                item.tour.endedAt != null &&
                    (item.place == null ||
                        (item.tour.pointCount > 0 && item.preview == null))
            }
            .forEach { item ->
                val points = withContext(Dispatchers.IO) {
                    store.points(item.tour.id)
                }
                if (context.ensureTourHistoryAssets(item.tour, points)) {
                    loadedItems = loadItems()
                    items = loadedItems
                }
            }
    }

    val sections = remember(items) {
        historySections(items, System.currentTimeMillis())
    }
    val weekSummary = remember(items) {
        homeWeekSummary(
            tours = items.map(HistoryTourItem::tour),
            now = System.currentTimeMillis(),
        )
    }
    BackHandler(enabled = backEnabled, onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheetBackground)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent()
                }
            }
            .navigationBarsPadding(),
    ) {
        HomeScreenHeader(onBack = onBack)
        if (isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Touren werden geladen …",
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = Ink.copy(alpha = 0.62f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(key = "week-summary") {
                    HomeWeeklySummary(summary = weekSummary)
                }
                if (items.isEmpty()) {
                    item(key = "empty-history") {
                        Text(
                            text = "Noch keine Touren",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 40.dp),
                            color = Ink.copy(alpha = 0.62f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                sections.forEach { section ->
                    stickyHeader(key = "section-${section.label}") {
                        HistorySectionHeader(
                            label = section.label,
                            dateLabel = section.dateLabel,
                        )
                    }
                    itemsIndexed(
                        items = section.tours,
                        key = { _, item -> item.tour.id },
                    ) { index, item ->
                        Column(modifier = Modifier.animateItem()) {
                            HistoryTourRow(
                                item = item,
                                isOpening = openingTourId == item.tour.id,
                                enabled = openingTourId == null,
                                onClick = { onOpenTour(item.tour.id) },
                                onOpenPhoto = { photo ->
                                    onOpenPhoto(
                                        photo,
                                        orderedPhotoMoments(item.moments),
                                    )
                                },
                            )
                            if (index < section.tours.lastIndex) {
                                HorizontalDivider(
                                    color = Ink.copy(alpha = 0.12f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreenHeader(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clickable(
                        onClickLabel = "Zurück zur Karte",
                        onClick = onBack,
                    )
                    .semantics {
                        contentDescription = "Zurück zur Karte"
                    },
                contentAlignment = Alignment.Center,
            ) {
                ChevronLeftIcon()
            }
            Text(
                text = "Home",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        HorizontalDivider(color = Ink.copy(alpha = 0.12f))
    }
}

@Composable
private fun HistorySectionHeader(
    label: String,
    dateLabel: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeutralSurface)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Ink,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        dateLabel?.let {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = it,
                color = Ink.copy(alpha = 0.5f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun HistoryTourRow(
    item: HistoryTourItem,
    isOpening: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onOpenPhoto: (MapMoment) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tourOpeningEffect(isOpening)
            .clickable(
                enabled = enabled,
                onClickLabel = "${historyTourTitle(item)} öffnen",
                onClick = onClick,
            )
            .semantics {
                if (isOpening) stateDescription = "Tour wird vorbereitet"
            }
            .padding(vertical = 14.dp),
    ) {
        Text(
            text = historyTourTitle(item),
            modifier = Modifier.padding(horizontal = 18.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = historyTourMetadata(item.tour),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .padding(top = 2.dp),
            color = Ink.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (item.tour.pointCount > 0 || item.moments.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (item.tour.pointCount > 0) {
                    item(key = "map-${item.tour.id}") {
                        HistoryMapThumbnail(
                            file = item.preview,
                            tourId = item.tour.id,
                        )
                    }
                }
                items(
                    items = item.moments,
                    key = MapMoment::id,
                ) { moment ->
                    HistoryMomentThumbnail(
                        moment = moment,
                        enabled = enabled,
                        onOpenPhoto = onOpenPhoto,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryMapThumbnail(
    file: File?,
    tourId: Long,
) {
    if (file == null) {
        Box(
            modifier = Modifier
                .size(HistoryThumbnailSize)
                .clip(RoundedCornerShape(10.dp))
                .background(NeutralSurface),
            contentAlignment = Alignment.Center,
        ) {
            HistoryIcon()
        }
        return
    }
    AsyncImage(
        model = file,
        contentDescription = "Kartenübersicht der Tour $tourId",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(HistoryThumbnailSize)
            .clip(RoundedCornerShape(10.dp)),
    )
}

@Composable
private fun HistoryMomentThumbnail(
    moment: MapMoment,
    enabled: Boolean,
    onOpenPhoto: (MapMoment) -> Unit,
) {
    val context = LocalContext.current
    val previewFile = remember(moment.payload, moment.type) {
        if (moment.type == MomentType.VIDEO) {
            videoThumbnailFile(File(moment.payload))
        } else {
            File(moment.payload)
        }
    }
    val request = remember(previewFile) {
        ImageRequest.Builder(context)
            .data(previewFile)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = if (moment.type == MomentType.VIDEO) {
            "Video dieser Tour"
        } else {
            "Foto dieser Tour"
        },
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(HistoryThumbnailSize)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (moment.type == MomentType.PHOTO) {
                    Modifier.clickable(
                        enabled = enabled,
                        onClickLabel = "Foto öffnen",
                        onClick = { onOpenPhoto(moment) },
                    )
                } else {
                    Modifier
                },
            ),
    )
}

@Composable
private fun Modifier.tourOpeningEffect(active: Boolean): Modifier {
    if (!active) return this
    val transition = rememberInfiniteTransition(label = "Tour wird vorbereitet")
    val opacity by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = LocationSignalPeriodMillis / 2,
                easing = LocationPulseEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Pulsierende Tour-Zeile",
    )
    return alpha(opacity)
}

private fun historySections(
    items: List<HistoryTourItem>,
    now: Long,
): List<HistorySection> = items
    .groupBy { historySectionLabel(it.tour.startedAt, now) }
    .map { (label, tours) ->
        HistorySection(
            label = label,
            dateLabel = historySectionDateLabel(tours.first().tour.startedAt, now),
            tours = tours,
        )
    }

internal fun historySectionLabel(
    timestamp: Long,
    now: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val date = Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
    val ageDays = ChronoUnit.DAYS.between(date, today).coerceAtLeast(0)
    return when {
        ageDays == 0L -> "Heute"
        ageDays == 1L -> "Gestern"
        ageDays < RecentDaySectionCount ->
            date.format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN))
        ageDays < DatedDaySectionCount ->
            date.format(DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN))
        else ->
            date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))
                .replaceFirstChar { it.titlecase(Locale.GERMAN) }
    }
}

internal fun historySectionDateLabel(
    timestamp: Long,
    now: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String? {
    val date = Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
    val ageDays = ChronoUnit.DAYS.between(date, today).coerceAtLeast(0)
    return date
        .takeIf { ageDays < RecentDaySectionCount }
        ?.format(DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN))
}

private fun historyTourTitle(item: HistoryTourItem): String =
    item.tour.title
        ?: item.place?.displayName
        ?: "Tour"

internal fun historyTourMetadata(tour: Tour): String {
    val end = tour.endedAt ?: System.currentTimeMillis()
    return "${formatClock(tour.startedAt)} · ${formatHistoryDuration(end - tour.startedAt)} · " +
        formatMeters(tour.distanceMeters)
}

internal fun formatHistoryDuration(durationMillis: Long): String =
    formatDurationMinutes(durationMillis, zeroMinutesLabel = "< 1 min")
