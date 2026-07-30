package app.spur

import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.toArgb
import org.maplibre.android.location.LocationComponentConstants
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineRoundLimit
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.expressions.Expression
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiPoint
import org.maplibre.geojson.Point

internal fun Style.showTourRoute(
    points: List<TrackPoint>,
    colors: TrailColors,
) {
    showTourRoute(tourRouteFeatures(points), colors)
}

internal fun tourRouteFeature(points: List<TrackPoint>): Feature? {
    val coordinates = points.map { Point.fromLngLat(it.longitude, it.latitude) }
    return if (coordinates.size >= 2) {
        Feature.fromGeometry(
            LineString.fromLngLats(coordinates),
        )
    } else {
        null
    }
}

internal fun tourRouteFeatures(points: List<TrackPoint>): FeatureCollection {
    val coordinates = points.map { Point.fromLngLat(it.longitude, it.latitude) }
    return FeatureCollection.fromFeatures(
        buildList {
            if (coordinates.size >= 2) {
                add(Feature.fromGeometry(LineString.fromLngLats(coordinates)))
            }
            if (coordinates.isNotEmpty()) {
                add(
                    Feature.fromGeometry(
                        MultiPoint.fromLngLats(coordinates),
                    ),
                )
            }
        },
    )
}

internal fun Style.showTourRoute(
    route: FeatureCollection,
    colors: TrailColors,
) {
    val source = getSourceAs<GeoJsonSource>(TourRouteSource)
        ?: GeoJsonSource(TourRouteSource).also(::addSource)
    val borderLayer = getLayerAs<LineLayer>(TourRouteBorderLayer)
    if (borderLayer == null) {
        val borderLayer = LineLayer(TourRouteBorderLayer, TourRouteSource).withProperties(
            lineColor(colors.stroke.toArgb()),
            lineWidth(TourRouteBorderWidthPixels),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND),
            lineRoundLimit(TourRouteRoundLimit),
        )
        if (getLayer(TourRouteLayer) == null) {
            addTourLayerBelowMarkers(borderLayer)
        } else {
            addLayerBelow(borderLayer, TourRouteLayer)
        }
    } else {
        borderLayer.setProperties(lineColor(colors.stroke.toArgb()))
    }
    val routeLayer = getLayerAs<LineLayer>(TourRouteLayer)
    if (routeLayer == null) {
        addTourLayerBelowMarkers(
            LineLayer(TourRouteLayer, TourRouteSource).withProperties(
                lineColor(colors.fill.toArgb()),
                lineWidth(TourRouteWidthPixels),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
                lineRoundLimit(TourRouteRoundLimit),
            ),
        )
    } else {
        routeLayer.setProperties(lineColor(colors.fill.toArgb()))
    }
    val waypointLayer = getLayerAs<CircleLayer>(TourWaypointLayer)
    if (waypointLayer == null) {
        addLayerAbove(
            CircleLayer(TourWaypointLayer, TourRouteSource).withProperties(
                circleColor(colors.stroke.toArgb()),
                circleRadius(TourWaypointRadiusPixels),
            ),
            TourRouteLayer,
        )
    } else {
        waypointLayer.setProperties(circleColor(colors.stroke.toArgb()))
    }
    source.setGeoJson(route)
}

private fun Style.addTourLayerBelowMarkers(layer: Layer) {
    val markerLayer = when {
        getLayer(MapMomentLayer) != null -> MapMomentLayer
        getLayer(LocationComponentConstants.PULSING_CIRCLE_LAYER) != null ->
            LocationComponentConstants.PULSING_CIRCLE_LAYER
        else -> null
    }
    if (markerLayer == null) {
        addLayer(layer)
    } else {
        addLayerBelow(layer, markerLayer)
    }
}

internal fun Style.showSelectedTrackPoint(point: TrackPoint?) {
    val source = getSourceAs<GeoJsonSource>(SelectedTrackPointSource)
        ?: GeoJsonSource(SelectedTrackPointSource).also(::addSource)
    if (getLayer(SelectedTrackPointLayer) == null) {
        val layer =
            CircleLayer(SelectedTrackPointLayer, SelectedTrackPointSource).withProperties(
                circleColor("#18201C"),
                circleRadius(7f),
                circleStrokeColor("#FFFFFF"),
                circleStrokeWidth(3f),
            )
        if (getLayer(TourEndpointRingLayer) == null) {
            addLayer(layer)
        } else {
            addLayerBelow(layer, TourEndpointRingLayer)
        }
    }
    if (point == null) {
        source.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
    } else {
        source.setGeoJson(
            Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)),
        )
    }
}

internal fun Style.showTourEndpoints(
    points: List<TrackPoint>,
    colors: TrailColors,
) {
    val source = getSourceAs<GeoJsonSource>(TourEndpointSource)
        ?: GeoJsonSource(TourEndpointSource).also(::addSource)
    val ringLayer = getLayerAs<CircleLayer>(TourEndpointRingLayer)
    if (ringLayer == null) {
        addLayerBelowLocationPulse(
            CircleLayer(TourEndpointRingLayer, TourEndpointSource).withProperties(
                circleColor(colors.fill.toArgb()),
                circleRadius(TourEndpointRadius),
                circleStrokeColor(colors.stroke.toArgb()),
                circleStrokeWidth(TourEndpointStrokeWidth),
            ),
        )
    } else {
        ringLayer.setProperties(
            circleColor(colors.fill.toArgb()),
            circleRadius(TourEndpointRadius),
            circleStrokeColor(colors.stroke.toArgb()),
            circleStrokeWidth(TourEndpointStrokeWidth),
        )
    }
    val endLayer = getLayerAs<CircleLayer>(TourEndpointEndLayer)
    if (endLayer == null) {
        addLayerBelowLocationPulse(
            CircleLayer(TourEndpointEndLayer, TourEndpointSource)
                .withFilter(
                    Expression.eq(
                        Expression.get(TourEndpointTypeProperty),
                        Expression.literal(TourEndpointEnd),
                    ),
                )
                .withProperties(
                    circleColor(colors.stroke.toArgb()),
                    circleRadius(TourEndpointEndRadius),
                ),
        )
    } else {
        endLayer.setProperties(
            circleColor(colors.stroke.toArgb()),
            circleRadius(TourEndpointEndRadius),
        )
    }
    source.setGeoJson(tourEndpointFeatures(points))
}

internal fun tourEndpointFeatures(points: List<TrackPoint>): FeatureCollection {
    val endpoints = buildList {
        points.firstOrNull()?.let { point ->
            add(
                Feature.fromGeometry(
                    Point.fromLngLat(point.longitude, point.latitude),
                ),
            )
        }
        points.lastOrNull()?.let { point ->
            add(
                Feature.fromGeometry(
                    Point.fromLngLat(point.longitude, point.latitude),
                ).apply {
                    addStringProperty(TourEndpointTypeProperty, TourEndpointEnd)
                },
            )
        }
    }
    return FeatureCollection.fromFeatures(endpoints)
}
