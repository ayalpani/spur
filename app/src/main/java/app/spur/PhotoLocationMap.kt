package app.spur

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.PropertyFactory.iconPitchAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.symbolZOrder
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PhotoLocationMapZoom = PhotoMapPreviewZoom + 1.0
private const val PhotoLocationSource = "photo-location-source"
private const val PhotoLocationLayer = "photo-location-layer"
private const val PhotoLocationImage = "photo-location-image"

@Composable
internal fun PhotoLocationMapOverlay(
    photo: MapMoment,
    sourceBounds: Rect,
    progress: Float,
    expanded: Boolean,
    showControls: Boolean,
    onReady: () -> Unit,
    onClose: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val transform = photoLocationMapTransform(
            sourceBounds = sourceBounds,
            containerWidth = constraints.maxWidth.toFloat(),
            containerHeight = constraints.maxHeight.toFloat(),
            progress = progress,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = transform.translationX
                    translationY = transform.translationY
                    scaleX = transform.scaleX
                    scaleY = transform.scaleY
                    clip = true
                },
        ) {
            PhotoLocationMap(
                photo = photo,
                expanded = expanded,
                onReady = onReady,
                onMarkerClick = onClose,
            )
        }
        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(MotionDurationDefaultMillis)),
            exit = fadeOut(tween(MotionDurationDefaultMillis / 2)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                PhotoActionButton(
                    contentDescription = "Karte schließen",
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) {
                    PhotoCloseIcon()
                }
            }
        }
    }
}

@Composable
private fun PhotoLocationMap(
    photo: MapMoment,
    expanded: Boolean,
    onReady: () -> Unit,
    onMarkerClick: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)
    val location = remember(photo.id, photo.latitude, photo.longitude) {
        LatLng(photo.latitude, photo.longitude)
    }
    val mapView = remember(photo.id, photo.latitude, photo.longitude) {
        MapLibre.getInstance(context)
        val options = MapLibreMapOptions.createFromAttributes(context)
            .camera(
                CameraPosition.Builder()
                    .target(location)
                    .zoom(PhotoMapPreviewZoom)
                    .build(),
            )
            .compassEnabled(false)
            .rotateGesturesEnabled(false)
            .tiltGesturesEnabled(false)
            .textureMode(true)
        MapView(context, options).apply { onCreate(null) }
    }
    var map by remember(photo.id, photo.latitude, photo.longitude) {
        mutableStateOf<MapLibreMap?>(null)
    }
    var styleLoaded by remember(photo.id, photo.latitude, photo.longitude) {
        mutableStateOf(false)
    }

    MapViewLifecycle(mapView, lifecycle)

    LaunchedEffect(mapView, photo.id, photo.type, photo.payload) {
        val marker = withContext(Dispatchers.Default) {
            createMomentMarkerBitmap(
                context = context.applicationContext,
                moment = photo,
                selected = false,
            )
        }
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.setStyle(StreetMapStyle) { style ->
                style.showMomentLocation(photo, marker)
                styleLoaded = true
                currentOnReady()
            }
        }
    }

    DisposableEffect(map, photo.id) {
        val readyMap = map
        val clickListener = MapLibreMap.OnMapClickListener { coordinate ->
            val screenPoint = readyMap?.projection?.toScreenLocation(coordinate)
                ?: return@OnMapClickListener false
            val hit = readyMap.queryRenderedFeatures(
                screenPoint,
                PhotoLocationLayer,
            ).isNotEmpty()
            if (hit) currentOnMarkerClick()
            hit
        }
        readyMap?.addOnMapClickListener(clickListener)
        onDispose { readyMap?.removeOnMapClickListener(clickListener) }
    }

    LaunchedEffect(map, styleLoaded, expanded, photo.id) {
        val readyMap = map?.takeIf { styleLoaded } ?: return@LaunchedEffect
        readyMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                location,
                if (expanded) PhotoLocationMapZoom else PhotoMapPreviewZoom,
            ),
            HomePanelMotionDurationMillis,
        )
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription =
                    "Karte des Aufnahmeorts mit Moment-Marker. " +
                        "Verschieben und Zoomen möglich."
            },
    )
}

private fun Style.showMomentLocation(
    moment: MapMoment,
    marker: Bitmap,
) {
    addImage(PhotoLocationImage, marker)
    addSource(
        GeoJsonSource(
            PhotoLocationSource,
            Feature.fromGeometry(Point.fromLngLat(moment.longitude, moment.latitude)),
        ),
    )
    addLayer(
        SymbolLayer(PhotoLocationLayer, PhotoLocationSource).withProperties(
            iconImage(PhotoLocationImage),
            iconOffset(arrayOf(0f, -MapMomentLocationClearance)),
            iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
            iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
            iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
            symbolZOrder(Property.SYMBOL_Z_ORDER_VIEWPORT_Y),
        ),
    )
}

internal data class PhotoLocationMapTransform(
    val translationX: Float,
    val translationY: Float,
    val scaleX: Float,
    val scaleY: Float,
)

internal fun photoLocationMapTransform(
    sourceBounds: Rect,
    containerWidth: Float,
    containerHeight: Float,
    progress: Float,
): PhotoLocationMapTransform {
    if (containerWidth <= 0f || containerHeight <= 0f) {
        return PhotoLocationMapTransform(0f, 0f, 1f, 1f)
    }
    val fraction = progress.coerceIn(0f, 1f)
    val remaining = 1f - fraction
    val initialScaleX = (sourceBounds.width / containerWidth).coerceIn(0f, 1f)
    val initialScaleY = (sourceBounds.height / containerHeight).coerceIn(0f, 1f)
    return PhotoLocationMapTransform(
        translationX = sourceBounds.left * remaining,
        translationY = sourceBounds.top * remaining,
        scaleX = initialScaleX + (1f - initialScaleX) * fraction,
        scaleY = initialScaleY + (1f - initialScaleY) * fraction,
    )
}
