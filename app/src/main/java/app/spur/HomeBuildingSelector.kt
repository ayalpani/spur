package app.spur

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

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

internal fun Style.showSelectableHomeBuildings(visible: Boolean = true) {
    val buildings = getLayerAs<FillLayer>(MapBuildingLayer) ?: return
    val sourceLayer = buildings.sourceLayer ?: return
    val layer = getLayer(SelectableHomeBuildingsLayer)
        ?: LineLayer(SelectableHomeBuildingsLayer, buildings.sourceId)
            .withSourceLayer(sourceLayer)
            .withProperties(
                lineColor(Ink.copy(alpha = 0.28f).toArgb()),
                lineWidth(1.25f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            )
            .also { addLayerAbove(it, MapBuildingLayer) }
    layer.setProperties(visibility(if (visible) Property.VISIBLE else Property.NONE))
}

internal fun selectedBuildingHighlight(
    home: Feature?,
    selected: Feature?,
): Feature? = selected?.takeUnless { it.geometry() == home?.geometry() }

private fun Style.showSelectedHomeBuilding(feature: Feature?) {
    showHighlightedBuildings(home = feature, selected = null)
}

internal fun Style.showHighlightedBuildings(
    home: Feature?,
    selected: Feature?,
) {
    showBuildingHighlight(
        feature = home,
        sourceId = HomeBuildingSource,
        fillLayerId = HomeBuildingFillLayer,
        outlineLayerId = HomeBuildingOutlineLayer,
        color = HomeBuildingGold,
    )
    showBuildingHighlight(
        feature = selectedBuildingHighlight(home, selected),
        sourceId = SelectedBuildingSource,
        fillLayerId = SelectedBuildingFillLayer,
        outlineLayerId = SelectedBuildingOutlineLayer,
        color = Ink,
        aboveLayerId = HomeBuildingOutlineLayer,
    )
}

private fun Style.showBuildingHighlight(
    feature: Feature?,
    sourceId: String,
    fillLayerId: String,
    outlineLayerId: String,
    color: Color,
    aboveLayerId: String? = null,
) {
    val source = getSourceAs<GeoJsonSource>(sourceId)
        ?: GeoJsonSource(sourceId).also(::addSource)
    if (getLayer(fillLayerId) == null) {
        val layer = FillLayer(fillLayerId, sourceId).withProperties(
            fillColor(color.toArgb()),
            fillOpacity(0.18f),
        )
        when {
            aboveLayerId != null && getLayer(aboveLayerId) != null ->
                addLayerAbove(layer, aboveLayerId)
            getLayer(SatelliteLayer) != null -> addLayerAbove(layer, SatelliteLayer)
            getLayer(MapBuildingLayer) != null -> addLayerAbove(layer, MapBuildingLayer)
            else -> addLayer(layer)
        }
    }
    if (getLayer(outlineLayerId) == null) {
        addLayerAbove(
            LineLayer(outlineLayerId, sourceId).withProperties(
                lineColor(color.toArgb()),
                lineWidth(3f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            ),
            fillLayerId,
        )
    }
    if (feature == null) {
        source.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
    } else {
        source.setGeoJson(FeatureCollection.fromFeature(feature))
    }
}
