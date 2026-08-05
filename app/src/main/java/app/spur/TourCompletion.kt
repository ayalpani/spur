package app.spur

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

private const val TourCompletionPreferences = "tour-completion"
private const val PendingTourCompletionId = "pending-tour-id"

internal object TourCompletionEvents {
    private val mutableFinishedTourIds = MutableSharedFlow<Long>(
        replay = 1,
        extraBufferCapacity = 1,
    )
    val finishedTourIds = mutableFinishedTourIds.asSharedFlow()

    fun notifyFinished(tourId: Long) {
        mutableFinishedTourIds.tryEmit(tourId)
    }
}

internal fun Context.markTourCompletionPending(tourId: Long) {
    getSharedPreferences(TourCompletionPreferences, Context.MODE_PRIVATE)
        .edit()
        .putLong(PendingTourCompletionId, tourId)
        .apply()
    TourCompletionEvents.notifyFinished(tourId)
}

internal fun Context.pendingTourCompletionId(): Long? =
    getSharedPreferences(TourCompletionPreferences, Context.MODE_PRIVATE)
        .getLong(PendingTourCompletionId, -1L)
        .takeIf { it > 0L }

internal fun Context.clearPendingTourCompletion(tourId: Long) {
    val preferences = getSharedPreferences(TourCompletionPreferences, Context.MODE_PRIVATE)
    if (preferences.getLong(PendingTourCompletionId, -1L) != tourId) return
    preferences.edit().remove(PendingTourCompletionId).apply()
}

internal fun eligibleTourCompletion(
    pendingTour: Tour?,
    activeTour: Tour?,
): Tour? = pendingTour?.takeIf {
    activeTour == null && it.endedAt != null
}

internal data class TourCompletionStats(
    val distance: String,
    val duration: String,
    val averageSpeed: String,
)

internal fun tourCompletionStats(tour: Tour): TourCompletionStats {
    val durationMillis = tour.endedAt
        ?.minus(tour.startedAt)
        ?.coerceAtLeast(0L)
        ?: 0L
    val averageSpeed = if (durationMillis > 0L) {
        tour.distanceMeters / durationMillis * 3_600.0
    } else {
        null
    }
    return TourCompletionStats(
        distance = if (tour.distanceMeters < 1_000.0) {
            formatMeters(tour.distanceMeters)
        } else {
            formatKilometers(tour.distanceMeters)
        },
        duration = formatDuration(durationMillis),
        averageSpeed = formatRecentSpeed(averageSpeed),
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun TourCompletionBottomSheet(
    tour: Tour,
    preview: File?,
    previewLoading: Boolean,
    points: List<TrackPoint>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
) {
    SpurModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        TourCompletionContent(
            tour = tour,
            preview = preview,
            previewLoading = previewLoading,
            points = points,
            onContinue = onDismiss,
        )
    }
}

@Composable
private fun TourCompletionContent(
    tour: Tour,
    preview: File?,
    previewLoading: Boolean,
    points: List<TrackPoint>,
    onContinue: () -> Unit,
) {
    val stats = remember(tour) { tourCompletionStats(tour) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        val needsScrolling = tourCompletionNeedsScrolling(maxHeight)
        val scrollState = rememberScrollState()
        val contentModifier = if (needsScrolling) {
            Modifier.verticalScroll(scrollState)
        } else {
            Modifier
        }
        Column(
            modifier = contentModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BottomSheetHeader(title = "Tour abgeschlossen")
            TourCompletionPreview(
                tourId = tour.id,
                preview = preview,
                loading = previewLoading,
                points = points,
                modifier = if (needsScrolling) {
                    Modifier
                        .fillMaxWidth()
                        .height(TourCompletionMinimumPreviewHeight)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = TourCompletionMinimumPreviewHeight)
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompletionStat(
                    value = stats.distance,
                    label = "Strecke",
                    modifier = Modifier.weight(1f),
                )
                CompletionStatDivider()
                CompletionStat(
                    value = stats.duration,
                    label = "Zeit",
                    modifier = Modifier.weight(1f),
                )
                CompletionStatDivider()
                CompletionStat(
                    value = stats.averageSpeed,
                    label = "Ø Tempo",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            SpurPrimaryButton(
                label = "Weiter",
                onClick = onContinue,
            )
        }
    }
}

@Composable
private fun CompletionStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = Ink.copy(alpha = IconTextLabelAlpha),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
        Text(
            text = value,
            modifier = Modifier.padding(top = 4.dp),
            color = Ink,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun TourCompletionPreview(
    tourId: Long,
    preview: File?,
    loading: Boolean,
    points: List<TrackPoint>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    var renderedPreview by remember(tourId) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(tourId, points, size) {
        if (points.size < 2 || size.width <= 0 || size.height <= 0) return@LaunchedEffect
        renderedPreview = context.renderTourPreview(
            points = points,
            widthPixels = size.width,
            heightPixels = size.height,
        )
    }
    DisposableEffect(renderedPreview) {
        val ownedPreview = renderedPreview
        onDispose { ownedPreview?.recycle() }
    }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(NeutralSurface)
            .onSizeChanged { size = it },
        contentAlignment = Alignment.Center,
    ) {
        if (renderedPreview != null) {
            Image(
                bitmap = renderedPreview!!.asImageBitmap(),
                contentDescription = "Gesamter Verlauf der Tour $tourId",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (preview == null) {
            Text(
                text = if (loading) {
                    "Kartenübersicht wird erstellt …"
                } else {
                    "Keine Kartenübersicht verfügbar"
                },
                color = Ink.copy(alpha = IconTextLabelAlpha),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        } else {
            AsyncImage(
                model = preview,
                contentDescription = "Gesamter Verlauf der Tour $tourId",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val TourCompletionMinimumPreviewHeight = 120.dp
private val TourCompletionMinimumComfortableHeight = 480.dp

internal fun tourCompletionNeedsScrolling(availableHeight: Dp): Boolean =
    availableHeight < TourCompletionMinimumComfortableHeight

@Composable
private fun CompletionStatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(44.dp)
            .background(Ink.copy(alpha = 0.12f)),
    )
}
