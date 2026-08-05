package app.spur

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.expressions.Expression
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiPoint
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

private const val TourPauseSource = "tour-pause-source"
private const val TourPauseLayer = "tour-pause-layer"
private const val TourPauseImage = "tour-pause-image"
private const val TourPauseImageProperty = "pause-image"
private const val TourPauseLabelProperty = "pause-label"
private const val TourPauseMarkerSizeDp = 36f
private const val TourPauseMarkerOutlineDp = 3f
private const val TourPauseMarkerBarDp = 3f
private const val TourPauseLabelSize = 13f
private const val TourPauseLabelHaloWidth = 4f

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

internal fun formatTourPauseDuration(durationMillis: Long): String =
    formatDurationMinutes(durationMillis, zeroMinutesLabel = "0 min")

internal fun tourPauseFeatures(points: List<TrackPoint>): FeatureCollection =
    FeatureCollection.fromFeatures(
        tourPauses(points).map { pause ->
            Feature.fromGeometry(
                Point.fromLngLat(pause.longitude, pause.latitude),
            ).apply {
                addStringProperty(TourPauseImageProperty, TourPauseImage)
                addStringProperty(
                    TourPauseLabelProperty,
                    "Pause · ${formatTourPauseDuration(pause.durationMillis)}",
                )
            }
        },
    )

internal fun createTourPauseMarkerBitmap(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (TourPauseMarkerSizeDp * density).roundToInt()
    val center = size / 2f
    val radius = center - TourPauseMarkerOutlineDp * density / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.color = TourPauseMarkerColor.toArgb()
    paint.style = Paint.Style.FILL
    canvas.drawCircle(center, center, radius, paint)

    paint.color = TourPauseMarkerForeground.toArgb()
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = TourPauseMarkerOutlineDp * density
    canvas.drawCircle(center, center, radius, paint)

    paint.strokeWidth = TourPauseMarkerBarDp * density
    paint.strokeCap = Paint.Cap.ROUND
    val barOffset = 4f * density
    val barTop = center - 6f * density
    val barBottom = center + 6f * density
    canvas.drawLine(center - barOffset, barTop, center - barOffset, barBottom, paint)
    canvas.drawLine(center + barOffset, barTop, center + barOffset, barBottom, paint)
    return bitmap
}

internal fun Style.showTourPauses(
    pauses: FeatureCollection,
    marker: Bitmap,
) {
    val source = getSourceAs<GeoJsonSource>(TourPauseSource)
        ?: GeoJsonSource(TourPauseSource).also(::addSource)
    if (getLayer(TourPauseLayer) == null) {
        addImage(TourPauseImage, marker)
        addTourLayerBelowMarkers(
            SymbolLayer(TourPauseLayer, TourPauseSource).withProperties(
                iconImage(Expression.get(TourPauseImageProperty)),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                textField(Expression.get(TourPauseLabelProperty)),
                textFont(arrayOf("Noto Sans Bold")),
                textSize(TourPauseLabelSize),
                textColor(Ink.toArgb()),
                textHaloColor(SheetBackground.toArgb()),
                textHaloWidth(TourPauseLabelHaloWidth),
                textOffset(arrayOf(0f, 1.6f)),
                textAnchor(Property.TEXT_ANCHOR_TOP),
                textAllowOverlap(true),
                textIgnorePlacement(true),
            ),
        )
    }
    source.setGeoJson(pauses)
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
        ).withFilter(Expression.eq(Expression.geometryType(), "LineString"))
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
                lineColor(colors.background.toArgb()),
                lineWidth(TourRouteWidthPixels),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            ).withFilter(Expression.eq(Expression.geometryType(), "LineString")),
        )
    } else {
        routeLayer.setProperties(lineColor(colors.background.toArgb()))
    }
    val waypointLayer = getLayerAs<CircleLayer>(TourWaypointLayer)
    if (waypointLayer == null) {
        addLayerAbove(
            CircleLayer(TourWaypointLayer, TourRouteSource).withProperties(
                circleColor(colors.stroke.toArgb()),
                circleRadius(TourWaypointRadiusPixels),
            ).withFilter(Expression.eq(Expression.geometryType(), "Point")),
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
                circleColor(colors.background.toArgb()),
                circleRadius(TourEndpointRadius),
                circleStrokeColor(colors.stroke.toArgb()),
                circleStrokeWidth(TourEndpointStrokeWidth),
            ),
        )
    } else {
        ringLayer.setProperties(
            circleColor(colors.background.toArgb()),
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
