package app.spur

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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

    DisposableEffect(lifecycle, mapView) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

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

internal fun MapLibreMap.fitTourRoute(
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
) = fitTourRoute(
    points = points,
    leftPaddingPixels = (40 * density).roundToInt(),
    topPaddingPixels = (104 * density).roundToInt(),
    rightPaddingPixels = (40 * density).roundToInt(),
    bottomPaddingPixels = (184 * density).roundToInt(),
    pointZoom = pointZoom,
    animated = animated,
)

internal fun MapLibreMap.fitTourRoute(
    points: List<TrackPoint>,
    leftPaddingPixels: Int,
    topPaddingPixels: Int,
    rightPaddingPixels: Int,
    bottomPaddingPixels: Int,
    pointZoom: Double,
    animated: Boolean,
) {
    if (points.isEmpty()) return
    val update = if (points.size == 1) {
        CameraUpdateFactory.newCameraPosition(
            org.maplibre.android.camera.CameraPosition.Builder(cameraPosition)
                .target(LatLng(points.first().latitude, points.first().longitude))
                .zoom(pointZoom)
                .build(),
        )
    } else {
        val bounds = LatLngBounds.Builder()
            .includes(points.map { LatLng(it.latitude, it.longitude) })
            .build()
        val camera = getCameraForLatLngBounds(
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
        camera?.let(CameraUpdateFactory::newCameraPosition)
            ?: CameraUpdateFactory.newLatLngBounds(
                bounds,
                leftPaddingPixels,
                topPaddingPixels,
                rightPaddingPixels,
                bottomPaddingPixels,
            )
    }
    if (animated) animateCamera(update, 220) else moveCamera(update)
}
