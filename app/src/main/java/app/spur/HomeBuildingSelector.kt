package app.spur

import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun HomeBuildingSelector(
    origin: SpurCoordinate,
    initialBuilding: Feature?,
    initialStartPoint: SpurCoordinate?,
    selectionEnabled: Boolean,
    startPointSelection: Boolean,
    onBuildingSelected: (SelectedBuilding) -> Unit,
    onStartPointChanged: (SpurCoordinate) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentSelectionEnabled by rememberUpdatedState(selectionEnabled)
    val currentStartPointSelection by rememberUpdatedState(startPointSelection)
    val currentOnBuildingSelected by rememberUpdatedState(onBuildingSelected)
    val currentOnStartPointChanged by rememberUpdatedState(onStartPointChanged)
    val searchRadiusPixels = with(LocalDensity.current) {
        HomeBuildingSelectionSearchRadiusDp.dp.toPx()
    }
    val mapView = remember(origin) {
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

    DisposableEffect(mapView, origin, searchRadiusPixels) {
        var map: MapLibreMap? = null
        var selectedBuilding = initialBuilding

        fun publishStartPoint() {
            if (!currentStartPointSelection) return
            val target = map?.cameraPosition?.target ?: return
            currentOnStartPointChanged(
                SpurCoordinate(
                    latitude = target.latitude,
                    longitude = target.longitude,
                ),
            )
        }

        fun selectBuilding(feature: Feature) {
            val readyMap = map ?: return
            val home = homeCoordinate(feature) ?: return
            selectedBuilding = feature
            readyMap.style?.showSelectedHomeBuilding(feature)
            currentOnBuildingSelected(
                SelectedBuilding(
                    coordinate = home,
                    feature = feature,
                ),
            )
        }

        fun selectBuildingAt(screenPoint: PointF, searchNearby: Boolean): Boolean {
            val readyMap = map ?: return false
            val feature = readyMap.homeBuildingAt(
                screenPoint = screenPoint,
                searchRadiusPixels = if (searchNearby) searchRadiusPixels else 0f,
            ) ?: return false
            selectBuilding(feature)
            return true
        }

        val clickListener = MapLibreMap.OnMapClickListener { point ->
            if (!currentSelectionEnabled || currentStartPointSelection) {
                return@OnMapClickListener false
            }
            val readyMap = map ?: return@OnMapClickListener false
            selectBuildingAt(
                screenPoint = readyMap.projection.toScreenLocation(point),
                searchNearby = false,
            )
        }
        val cameraIdleListener = MapLibreMap.OnCameraIdleListener(::publishStartPoint)
        val renderListener = MapView.OnDidFinishRenderingMapListener { fully ->
            if (!fully || selectedBuilding != null) return@OnDidFinishRenderingMapListener
            val readyMap = map ?: return@OnDidFinishRenderingMapListener
            selectBuildingAt(
                screenPoint = readyMap.projection.toScreenLocation(
                    LatLng(origin.latitude, origin.longitude),
                ),
                searchNearby = true,
            )
        }
        mapView.addOnDidFinishRenderingMapListener(renderListener)
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.uiSettings.apply {
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = false
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
            }
            readyMap.addOnMapClickListener(clickListener)
            readyMap.addOnCameraIdleListener(cameraIdleListener)
            readyMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(origin.latitude, origin.longitude),
                    HomeBuildingSelectionZoom,
                ),
            )
            readyMap.setStyle(StreetMapStyle) { style ->
                style.hideDistractingPoiLayers()
                style.showOutlinedBuildings()
                style.showSelectableHomeBuildings()
                style.showSelectedHomeBuilding(selectedBuilding)
                mapView.postInvalidate()
            }
        }

        onDispose {
            map?.removeOnMapClickListener(clickListener)
            map?.removeOnCameraIdleListener(cameraIdleListener)
            mapView.removeOnDidFinishRenderingMapListener(renderListener)
        }
    }

    LaunchedEffect(mapView, startPointSelection) {
        if (!startPointSelection) return@LaunchedEffect
        val startPoint = initialStartPoint ?: return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(startPoint.latitude, startPoint.longitude),
                    map.cameraPosition.zoom,
                ),
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Mist)
                .semantics {
                    contentDescription = if (startPointSelection) {
                        "Startpunkt vor deinem Zuhause auswählen"
                    } else {
                        "Gebäudeauswahl für dein Zuhause"
                    }
                },
        ) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
            )
            if (startPointSelection) {
                HomeStartPointCrosshair(
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        if (selectionEnabled) {
            Text(
                text = "Alle sichtbaren Gebäude können ausgewählt werden.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun HomeStartPointCrosshair(
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .size(40.dp)
            .semantics { contentDescription = "Fadenkreuz für den Tourstartpunkt" },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val gap = 5.dp.toPx()
        val radius = 16.dp.toPx()
        val stroke = 3.dp.toPx()
        drawCircle(
            color = SheetBackground,
            radius = 7.dp.toPx(),
            center = center,
        )
        drawCircle(
            color = Ink,
            radius = 4.dp.toPx(),
            center = center,
        )
        drawLine(Ink, Offset(center.x, center.y - radius), Offset(center.x, center.y - gap), stroke)
        drawLine(Ink, Offset(center.x, center.y + gap), Offset(center.x, center.y + radius), stroke)
        drawLine(Ink, Offset(center.x - radius, center.y), Offset(center.x - gap, center.y), stroke)
        drawLine(Ink, Offset(center.x + gap, center.y), Offset(center.x + radius, center.y), stroke)
    }
}

private fun MapLibreMap.homeBuildingAt(
    screenPoint: PointF,
    searchRadiusPixels: Float,
): Feature? {
    val coordinate = projection.fromScreenLocation(screenPoint).let {
        SpurCoordinate(latitude = it.latitude, longitude = it.longitude)
    }
    queryRenderedFeatures(screenPoint, MapBuildingLayer)
        .firstNotNullOfOrNull { buildingFeatureAt(it, coordinate) }
        ?.let { return it }
    if (searchRadiusPixels <= 0f) return null
    val nearby = queryRenderedFeatures(
        RectF(
            screenPoint.x - searchRadiusPixels,
            screenPoint.y - searchRadiusPixels,
            screenPoint.x + searchRadiusPixels,
            screenPoint.y + searchRadiusPixels,
        ),
        MapBuildingLayer,
    )
    return nearby.mapNotNull { buildingFeatureAt(it, coordinate) }.minByOrNull { feature ->
        val center = homeCoordinate(feature) ?: return@minByOrNull Float.MAX_VALUE
        val renderedCenter = projection.toScreenLocation(
            LatLng(center.latitude, center.longitude),
        )
        val dx = renderedCenter.x - screenPoint.x
        val dy = renderedCenter.y - screenPoint.y
        dx * dx + dy * dy
    }
}

internal fun buildingFeatureAt(
    feature: Feature,
    coordinate: SpurCoordinate,
): Feature? = when (val geometry = feature.geometry()) {
    is Polygon -> feature
    is MultiPolygon -> {
        val polygon = geometry.coordinates().firstOrNull {
            polygonContainsCoordinate(it, coordinate)
        } ?: geometry.coordinates().minByOrNull { rings ->
            val center = homeCoordinate(Feature.fromGeometry(Polygon.fromLngLats(rings)))
                ?: return@minByOrNull Double.MAX_VALUE
            val latitude = center.latitude - coordinate.latitude
            val longitude = center.longitude - coordinate.longitude
            latitude * latitude + longitude * longitude
        }
        polygon?.let { Feature.fromGeometry(Polygon.fromLngLats(it)) }
    }
    else -> null
}

private fun polygonContainsCoordinate(
    rings: List<List<Point>>,
    coordinate: SpurCoordinate,
): Boolean {
    val outer = rings.firstOrNull() ?: return false
    return ringContainsCoordinate(outer, coordinate) &&
        rings.drop(1).none { ringContainsCoordinate(it, coordinate) }
}

private fun ringContainsCoordinate(
    ring: List<Point>,
    coordinate: SpurCoordinate,
): Boolean {
    if (ring.size < 3) return false
    var inside = false
    var previous = ring.last()
    ring.forEach { current ->
        if (
            (current.latitude() > coordinate.latitude) !=
            (previous.latitude() > coordinate.latitude)
        ) {
            val crossingLongitude =
                (previous.longitude() - current.longitude()) *
                    (coordinate.latitude - current.latitude()) /
                    (previous.latitude() - current.latitude()) +
                    current.longitude()
            if (coordinate.longitude < crossingLongitude) inside = !inside
        }
        previous = current
    }
    return inside
}

internal fun homeCoordinate(feature: Feature): SpurCoordinate? {
    val points = when (val geometry = feature.geometry()) {
        is Polygon -> geometry.coordinates().flatten()
        is MultiPolygon -> geometry.coordinates().flatten().flatten()
        else -> return null
    }
    if (points.isEmpty()) return null
    return SpurCoordinate(
        latitude = (points.minOf(Point::latitude) + points.maxOf(Point::latitude)) / 2,
        longitude = (points.minOf(Point::longitude) + points.maxOf(Point::longitude)) / 2,
    )
}

private fun Style.showSelectableHomeBuildings() {
    val buildings = getLayerAs<FillLayer>(MapBuildingLayer) ?: return
    val sourceLayer = buildings.sourceLayer ?: return
    if (getLayer(SelectableHomeBuildingsLayer) != null) return
    addLayerAbove(
        LineLayer(SelectableHomeBuildingsLayer, buildings.sourceId)
            .withSourceLayer(sourceLayer)
            .withProperties(
                lineColor(Ink.copy(alpha = 0.28f).toArgb()),
                lineWidth(1.25f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            ),
        MapBuildingLayer,
    )
}

internal fun highlightedBuildingFeatures(
    home: Feature?,
    selected: Feature?,
): List<Feature> = buildList {
    home?.let(::add)
    if (selected != null && selected.geometry() != home?.geometry()) add(selected)
}

private fun Style.showSelectedHomeBuilding(feature: Feature?) {
    showHighlightedBuildings(home = feature, selected = null)
}

internal fun Style.showHighlightedBuildings(
    home: Feature?,
    selected: Feature?,
) {
    val source = getSourceAs<GeoJsonSource>(HomeBuildingSource)
        ?: GeoJsonSource(HomeBuildingSource).also(::addSource)
    if (getLayer(HomeBuildingFillLayer) == null) {
        val layer = FillLayer(HomeBuildingFillLayer, HomeBuildingSource).withProperties(
            fillColor(Ink.toArgb()),
            fillOpacity(0.18f),
        )
        when {
            getLayer(SatelliteLayer) != null -> addLayerAbove(layer, SatelliteLayer)
            getLayer(MapBuildingLayer) != null -> addLayerAbove(layer, MapBuildingLayer)
            else -> addLayer(layer)
        }
    }
    if (getLayer(HomeBuildingOutlineLayer) == null) {
        addLayerAbove(
            LineLayer(HomeBuildingOutlineLayer, HomeBuildingSource).withProperties(
                lineColor(Ink.toArgb()),
                lineWidth(3f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            ),
            HomeBuildingFillLayer,
        )
    }
    val features = highlightedBuildingFeatures(home, selected)
    if (features.isEmpty()) {
        source.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
    } else {
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }
}
