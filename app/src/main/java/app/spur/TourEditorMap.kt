package app.spur

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal const val TourEntryZoomOutLevels = 2.0

@Composable
internal fun TourEditorMap(
    points: List<TrackPoint>,
    selectedPoint: TrackPoint?,
) {
    val context = LocalContext.current
    val trailColors = remember { context.loadTrailColors() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentPoints by rememberUpdatedState(points)
    val currentSelectedPoint by rememberUpdatedState(selectedPoint)
    val density = LocalDensity.current
    val cameraPadding = with(density) { 52.dp.roundToPx() }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }

    MapViewLifecycle(mapView, lifecycle)

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            map.uiSettings.isCompassEnabled = false
            map.setStyle(StreetMapStyle) { style ->
                style.showTourRoute(currentPoints, trailColors)
                style.showSelectedTrackPoint(currentSelectedPoint)
                style.showTourEndpoints(currentPoints, trailColors)
                mapView.post {
                    map.fitTourRoute(currentPoints, cameraPadding, animated = false)
                }
            }
        }
    }

    LaunchedEffect(selectedPoint) {
        mapView.getMapAsync { map ->
            map.style?.showSelectedTrackPoint(selectedPoint)
            selectedPoint?.let { point ->
                map.moveCamera(
                    CameraUpdateFactory.newLatLng(
                        LatLng(point.latitude, point.longitude),
                    ),
                )
            }
        }
    }

    LaunchedEffect(points) {
        mapView.getMapAsync { map ->
            map.style?.showTourRoute(points, trailColors)
            map.style?.showTourEndpoints(points, trailColors)
            mapView.post { map.fitTourRoute(points, cameraPadding, animated = true) }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Kartenvorschau der bearbeiteten Tour" },
    )
}

private fun MapLibreMap.fitTourRoute(
    points: List<TrackPoint>,
    paddingPixels: Int,
    animated: Boolean,
) = fitTourRoute(
    points = points,
    leftPaddingPixels = paddingPixels,
    topPaddingPixels = paddingPixels,
    rightPaddingPixels = paddingPixels,
    bottomPaddingPixels = paddingPixels,
    pointZoom = DefaultMapZoom,
    animated = animated,
)

internal fun MapLibreMap.fitMapScreenTourRoute(
    points: List<TrackPoint>,
    density: Float,
    pointZoom: Double,
    animated: Boolean,
    zoomOutBeforeAnimation: Double = 0.0,
    tourEntryStartIsPrepared: Boolean = false,
    onAnimationFinished: () -> Unit = {},
) = fitTourRoute(
    points = points,
    leftPaddingPixels = (40 * density).roundToInt(),
    topPaddingPixels = (104 * density).roundToInt(),
    rightPaddingPixels = (40 * density).roundToInt(),
    bottomPaddingPixels = (184 * density).roundToInt(),
    pointZoom = pointZoom,
    animated = animated,
    zoomOutBeforeAnimation = zoomOutBeforeAnimation,
    tourEntryStartIsPrepared = tourEntryStartIsPrepared,
    onAnimationFinished = onAnimationFinished,
)

private fun MapLibreMap.fitTourRoute(
    points: List<TrackPoint>,
    leftPaddingPixels: Int,
    topPaddingPixels: Int,
    rightPaddingPixels: Int,
    bottomPaddingPixels: Int,
    pointZoom: Double,
    animated: Boolean,
    zoomOutBeforeAnimation: Double = 0.0,
    tourEntryStartIsPrepared: Boolean = false,
    onAnimationFinished: () -> Unit = {},
) {
    if (points.isEmpty()) return
    val bounds = if (points.size > 1) {
        LatLngBounds.Builder()
            .includes(points.map { LatLng(it.latitude, it.longitude) })
            .build()
    } else {
        null
    }
    val targetCamera = if (bounds == null) {
        CameraPosition.Builder(cameraPosition)
            .target(LatLng(points.first().latitude, points.first().longitude))
            .zoom(pointZoom)
            .build()
    } else {
        getCameraForLatLngBounds(
            bounds,
            intArrayOf(
                leftPaddingPixels,
                topPaddingPixels,
                rightPaddingPixels,
                bottomPaddingPixels,
            ),
            cameraPosition.bearing,
            cameraPosition.tilt,
        )
    }
    val update = targetCamera?.let(CameraUpdateFactory::newCameraPosition)
        ?: bounds?.let {
            CameraUpdateFactory.newLatLngBounds(
                it,
                leftPaddingPixels,
                topPaddingPixels,
                rightPaddingPixels,
                bottomPaddingPixels,
            )
        } ?: return
    if (animated) {
        if (
            targetCamera != null &&
            zoomOutBeforeAnimation > 0.0 &&
            !tourEntryStartIsPrepared
        ) {
            moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder(targetCamera)
                        .zoom(tourEntryStartZoom(targetCamera.zoom, zoomOutBeforeAnimation))
                        .build(),
                ),
            )
        }
        animateCamera(
            update,
            if (zoomOutBeforeAnimation > 0.0) {
                HomePanelMotionDurationMillis
            } else {
                220
            },
            object : MapLibreMap.CancelableCallback {
                override fun onCancel() = onAnimationFinished()

                override fun onFinish() = onAnimationFinished()
            },
        )
    } else {
        moveCamera(update)
        onAnimationFinished()
    }
}

internal fun tourEntryStartZoom(
    targetZoom: Double,
    zoomOutLevels: Double = TourEntryZoomOutLevels,
): Double = (targetZoom - zoomOutLevels).coerceAtLeast(MapZoomMinimum)
