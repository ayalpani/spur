package app.spur

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val HistoryThumbnailSize = 80.dp
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
    val tours: List<HistoryTourItem>,
)

@Composable
internal fun HistoryBottomSheet(
    store: TourStore,
    revision: Long,
    onOpenTour: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(emptyList<HistoryTourItem>()) }

    suspend fun loadItems(): List<HistoryTourItem> = withContext(Dispatchers.IO) {
        val moments = context.loadMapMoments()
        moments
            .filter { it.type == MomentType.VIDEO }
            .forEach { ensureVideoThumbnail(File(it.payload)) }
        store.tours().map { tour ->
            HistoryTourItem(
                tour = tour,
                place = context.loadTourPlaceMetadata(tour.id),
                preview = context.tourPreviewFile(tour.id)
                    .takeIf { it.isFile && it.length() > 0L },
                moments = visualMomentsForTour(moments, tour),
            )
        }
    }

    LaunchedEffect(revision) {
        items = loadItems()
        items
            .filter { it.tour.endedAt != null }
            .forEach { item ->
                val points = withContext(Dispatchers.IO) {
                    store.points(item.tour.id)
                }
                if (context.ensureTourHistoryAssets(item.tour, points)) {
                    items = loadItems()
                }
            }
    }

    val sections = remember(items) {
        historySections(items, System.currentTimeMillis())
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            HistorySheetHeader()
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Noch keine Touren",
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
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    sections.forEach { section ->
                        stickyHeader(key = "section-${section.label}") {
                            HistorySectionHeader(section.label)
                        }
                        itemsIndexed(
                            items = section.tours,
                            key = { _, item -> item.tour.id },
                        ) { index, item ->
                            HistoryTourRow(
                                item = item,
                                onClick = { onOpenTour(item.tour.id) },
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
        HistoryCloseButton(
            onDismiss = onDismiss,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun HistorySheetHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(
            10.dp,
            Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HistoryIcon()
        Text(
            text = "Deine Touren",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HistorySectionHeader(label: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SheetBackground),
    ) {
        HorizontalDivider(color = Ink.copy(alpha = 0.12f))
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        HorizontalDivider(color = Ink.copy(alpha = 0.12f))
    }
}

@Composable
private fun HistoryTourRow(
    item: HistoryTourItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = "${historyTourTitle(item)} öffnen",
                onClick = onClick,
            )
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
                    HistoryMomentThumbnail(moment)
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
                .background(Mist),
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
private fun HistoryMomentThumbnail(moment: MapMoment) {
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
            .clip(RoundedCornerShape(10.dp)),
    )
}

@Composable
private fun HistoryCloseButton(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        val colors = LocalMapControlColors.current
        Button(
            onClick = onDismiss,
            modifier = Modifier.height(60.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.background,
                contentColor = colors.foreground,
            ),
        ) {
            PhotoCloseIcon()
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Schließen",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun historySections(
    items: List<HistoryTourItem>,
    now: Long,
): List<HistorySection> = items
    .groupBy { historySectionLabel(it.tour.startedAt, now) }
    .map { (label, tours) -> HistorySection(label, tours) }

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

private fun historyTourTitle(item: HistoryTourItem): String =
    item.place?.displayName
        ?: item.tour.activity
        ?: "Tour"

internal fun historyTourMetadata(tour: Tour): String {
    val end = tour.endedAt ?: System.currentTimeMillis()
    return "${formatClock(tour.startedAt)} · ${formatHistoryDuration(end - tour.startedAt)} · " +
        formatMeters(tour.distanceMeters)
}

internal fun formatHistoryDuration(durationMillis: Long): String {
    val totalMinutes = durationMillis.coerceAtLeast(0L) / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L -> "$hours h ${minutes.toString().padStart(2, '0')} min"
        totalMinutes > 0L -> "$totalMinutes min"
        else -> "< 1 min"
    }
}
