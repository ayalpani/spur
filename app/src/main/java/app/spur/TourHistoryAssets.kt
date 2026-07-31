package app.spur

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.location.Address
import android.location.Geocoder
import android.location.Location
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.max

private const val TourHistoryPreferences = "tour-history"
private const val TourPreviewDirectory = "tour-previews"
internal const val TourPreviewSizePixels = 640
private const val SamePlaceMaximumDistanceMeters = 100f
private const val TourPreviewPaddingFraction = 0.14
private const val TourPreviewMinimumPaddingDegrees = 0.0005
private const val TourPreviewSource = "tour-preview-route-source"
private const val TourPreviewEndpointSource = "tour-preview-endpoint-source"
private const val TourPreviewBorderLayer = "tour-preview-border-layer"
private const val TourPreviewRouteLayer = "tour-preview-route-layer"
private const val TourPreviewWaypointLayer = "tour-preview-waypoint-layer"
private const val TourPreviewEndpointLayer = "tour-preview-endpoint-layer"
private const val TourPreviewAttribution = "© OpenFreeMap · © OpenStreetMap"
private const val TourPreviewAttributionTextSizePixels = 9f
private val TourPreviewMutex = Mutex()

internal data class TourPlaceMetadata(
    val startPlace: String,
    val endPlace: String,
) {
    val displayName: String
        get() = if (startPlace.equals(endPlace, ignoreCase = true)) {
            startPlace
        } else {
            "$startPlace → $endPlace"
        }
}

internal fun shouldReuseStartPlace(distanceMeters: Float): Boolean =
    distanceMeters <= SamePlaceMaximumDistanceMeters

internal fun Context.loadTourPlaceMetadata(tourId: Long): TourPlaceMetadata? {
    val stored = getSharedPreferences(TourHistoryPreferences, Context.MODE_PRIVATE)
        .getString(tourId.toString(), null)
        ?: return null
    return runCatching {
        val json = JSONObject(stored)
        TourPlaceMetadata(
            startPlace = json.getString("start"),
            endPlace = json.getString("end"),
        )
    }.getOrNull()
}

private fun Context.saveTourPlaceMetadata(
    tourId: Long,
    metadata: TourPlaceMetadata,
) {
    getSharedPreferences(TourHistoryPreferences, Context.MODE_PRIVATE)
        .edit()
        .putString(
            tourId.toString(),
            JSONObject()
                .put("start", metadata.startPlace)
                .put("end", metadata.endPlace)
                .toString(),
        )
        .apply()
}

internal fun Context.tourPreviewFile(tourId: Long): File =
    File(File(filesDir, TourPreviewDirectory), "$tourId.jpg")

internal fun Context.deleteTourHistoryAssets(tourId: Long) {
    getSharedPreferences(TourHistoryPreferences, Context.MODE_PRIVATE)
        .edit()
        .remove(tourId.toString())
        .apply()
    tourPreviewFile(tourId).delete()
}

internal suspend fun Context.ensureTourHistoryAssets(
    tour: Tour,
    points: List<TrackPoint>,
): Boolean {
    if (points.isEmpty()) return false
    var changed = false
    if (loadTourPlaceMetadata(tour.id) == null) {
        resolveTourPlaces(points)?.let {
            saveTourPlaceMetadata(tour.id, it)
            changed = true
        }
    }
    changed = ensureTourPreview(tour, points) || changed
    return changed
}

internal suspend fun Context.ensureTourPreview(
    tour: Tour,
    points: List<TrackPoint>,
): Boolean {
    if (points.isEmpty()) return false
    return TourPreviewMutex.withLock {
        val preview = tourPreviewFile(tour.id)
        if (preview.isFile && preview.length() > 0L) return@withLock false
        val bitmap = withContext(Dispatchers.Main.immediate) {
            renderTourPreview(
                points = points,
                widthPixels = TourPreviewSizePixels,
                heightPixels = TourPreviewSizePixels,
            )
        } ?: return@withLock false
        val saved = withContext(Dispatchers.IO) {
            runCatching {
                preview.parentFile?.mkdirs()
                preview.outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)
                }
            }.getOrDefault(false)
        }
        bitmap.recycle()
        if (!saved) preview.delete()
        saved
    }
}

private fun addTourPreviewAttribution(bitmap: Bitmap) {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ink.toArgb()
        textSize = TourPreviewAttributionTextSizePixels
    }
    val textWidth = textPaint.measureText(TourPreviewAttribution)
    val padding = 4f
    Canvas(bitmap).apply {
        drawRect(
            bitmap.width - textWidth - padding * 2,
            bitmap.height - TourPreviewAttributionTextSizePixels - padding * 2,
            bitmap.width.toFloat(),
            bitmap.height.toFloat(),
            Paint().apply {
                color = SheetBackground.toArgb()
                alpha = 220
            },
        )
        drawText(
            TourPreviewAttribution,
            bitmap.width - textWidth - padding,
            bitmap.height - padding,
            textPaint,
        )
    }
}

private suspend fun Context.resolveTourPlaces(
    points: List<TrackPoint>,
): TourPlaceMetadata? = withContext(Dispatchers.IO) {
    val start = points.first()
    val end = points.last()
    val startPlace = reverseGeocode(start) ?: return@withContext null
    if (shouldReuseStartPlace(distanceBetween(start, end))) {
        return@withContext TourPlaceMetadata(startPlace, startPlace)
    }
    val endPlace = reverseGeocode(end) ?: return@withContext null
    TourPlaceMetadata(startPlace, endPlace)
}

private fun distanceBetween(start: TrackPoint, end: TrackPoint): Float {
    val result = FloatArray(1)
    Location.distanceBetween(
        start.latitude,
        start.longitude,
        end.latitude,
        end.longitude,
        result,
    )
    return result[0]
}

@Suppress("DEPRECATION")
private fun Context.reverseGeocode(point: TrackPoint): String? {
    if (!Geocoder.isPresent()) return null
    return runCatching {
        Geocoder(this, Locale.getDefault())
            .getFromLocation(point.latitude, point.longitude, 1)
            ?.firstOrNull()
            ?.placeName()
    }.getOrNull()
}

private fun Address.placeName(): String? =
    locality
        ?: subAdminArea
        ?: adminArea
        ?: featureName

internal suspend fun Context.renderTourPreview(
    points: List<TrackPoint>,
    widthPixels: Int,
    heightPixels: Int,
): Bitmap? {
    MapLibre.getInstance(this)
    val colors = loadTrailColors()
    val route = tourRouteFeatures(points)
    val endpoints = tourEndpointFeatures(points)
    val style = Style.Builder()
        .fromUri(StreetMapStyle)
        .withSources(
            GeoJsonSource(TourPreviewSource, route),
            GeoJsonSource(TourPreviewEndpointSource, endpoints),
        )
        .withLayers(
            LineLayer(TourPreviewBorderLayer, TourPreviewSource).withProperties(
                lineColor(colors.stroke.toArgb()),
                lineWidth(TourRouteBorderWidthPixels),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            ),
            LineLayer(TourPreviewRouteLayer, TourPreviewSource).withProperties(
                lineColor(colors.background.toArgb()),
                lineWidth(TourRouteWidthPixels),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            ),
            CircleLayer(TourPreviewWaypointLayer, TourPreviewSource).withProperties(
                circleColor(colors.stroke.toArgb()),
                circleRadius(TourWaypointRadiusPixels),
            ),
            CircleLayer(TourPreviewEndpointLayer, TourPreviewEndpointSource).withProperties(
                circleColor(colors.background.toArgb()),
                circleRadius(TourEndpointRadius),
            ),
        )
    val options = MapSnapshotter.Options(
        widthPixels.coerceAtLeast(1),
        heightPixels.coerceAtLeast(1),
    )
        .withStyleBuilder(style)
        .withRegion(tourPreviewBounds(points))
        .withPixelRatio(1f)
        .withLogo(false)
    return suspendCancellableCoroutine { continuation ->
        val snapshotter = MapSnapshotter(applicationContext, options)
        continuation.invokeOnCancellation { snapshotter.cancel() }
        snapshotter.start(
            { snapshot ->
                if (continuation.isActive) continuation.resume(snapshot.bitmap)
            },
            {
                if (continuation.isActive) continuation.resume(null)
            },
        )
    }?.also(::addTourPreviewAttribution)
}

internal fun tourPreviewBounds(points: List<TrackPoint>): LatLngBounds {
    val minLatitude = points.minOf(TrackPoint::latitude)
    val maxLatitude = points.maxOf(TrackPoint::latitude)
    val minLongitude = points.minOf(TrackPoint::longitude)
    val maxLongitude = points.maxOf(TrackPoint::longitude)
    val latitudePadding = max(
        (maxLatitude - minLatitude) * TourPreviewPaddingFraction,
        TourPreviewMinimumPaddingDegrees,
    )
    val longitudePadding = max(
        (maxLongitude - minLongitude) * TourPreviewPaddingFraction,
        TourPreviewMinimumPaddingDegrees,
    )
    return LatLngBounds.from(
        (maxLatitude + latitudePadding).coerceAtMost(90.0),
        (maxLongitude + longitudePadding).coerceAtMost(180.0),
        (minLatitude - latitudePadding).coerceAtLeast(-90.0),
        (minLongitude - longitudePadding).coerceAtLeast(-180.0),
    )
}
