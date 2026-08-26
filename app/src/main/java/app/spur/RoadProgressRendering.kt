package app.spur

import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.symbolSortKey
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textPadding
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point

internal fun MapLibreMap.osmRoadPolylines(
    viewport: RectF,
    near: SpurCoordinate? = null,
): List<RoadPolyline> {
    val currentStyle = style ?: return emptyList()
    val roadLayers = currentStyle.layers
        .filterIsInstance<LineLayer>()
        .filter { layer ->
            layer.sourceLayer == RoadSourceLayer &&
                RoadExcludedLayerTerms.none(layer.id::contains)
        }
    if (roadLayers.isEmpty()) return emptyList()
    return queryRenderedFeatures(
        viewport,
        *roadLayers.map(LineLayer::getId).toTypedArray(),
    )
        .asSequence()
        .filter { feature ->
            feature.stringProperty("class") in RoadIncludedClasses
        }
        .flatMap { feature ->
            val roadClass = feature.stringProperty("class")
            val grade = listOf(
                feature.stringProperty("brunnel"),
                feature.stringProperty("layer"),
                feature.stringProperty("level"),
                feature.stringProperty("indoor"),
            ).joinToString(":")
            feature.roadPointLists().asSequence().map { points ->
                RoadPolyline(
                    points = points,
                    grade = grade,
                    kind = roadKind(roadClass),
                )
            }
        }
        .filter { it.points.size >= 2 }
        .filter { polyline ->
            near == null || projectOntoRoad(near, polyline.points)
                ?.distanceMeters
                ?.let { it <= RoadNetworkRadiusMeters } == true
        }
        .distinctBy { canonicalRoadKey(it.points, it.grade) }
        .toList()
}

internal fun MapLibreMap.isOsmRoadSource(sourceId: String): Boolean =
    style?.layers
        ?.filterIsInstance<LineLayer>()
        ?.any { layer ->
            layer.sourceId == sourceId &&
                layer.sourceLayer == RoadSourceLayer &&
                RoadExcludedLayerTerms.none(layer.id::contains)
        } == true

internal fun Style.showRoadProgressFeatures(
    features: FeatureCollection,
) {
    val progressSource = roadGeoJsonSource(RoadProgressSource)
    val progressLayer = getLayerAs<LineLayer>(RoadProgressLayer)
        ?: LineLayer(RoadProgressLayer, RoadProgressSource).also(::addRoadProgressLayer)
    progressLayer.minZoom = RoadHistoryMinimumZoom.toFloat()
    progressLayer.setProperties(
        lineColor(GameRoadGreen.toArgb()),
        lineOpacity(GameRoadOpacity),
        lineWidth(
            Expression.interpolate(
                Expression.linear(),
                Expression.zoom(),
                Expression.stop(RoadHistoryMinimumZoom, GameRoadOverviewWidthPixels),
                Expression.stop(RoadHistoryDetailZoom, GameRoadWidthPixels),
            ),
        ),
        lineCap(Property.LINE_CAP_BUTT),
        lineJoin(Property.LINE_JOIN_ROUND),
    )

    progressSource.setGeoJson(features)
}

internal fun roadProgressOverviewFeatureCollection(
    segments: List<List<SpurCoordinate>>,
): FeatureCollection {
    val lines = segments.mapNotNull { segment ->
        segment.takeIf { it.size >= 2 }?.map { coordinate ->
            Point.fromLngLat(coordinate.longitude, coordinate.latitude)
        }
    }
    return if (lines.isEmpty()) {
        FeatureCollection.fromFeatures(emptyList())
    } else {
        FeatureCollection.fromFeature(
            Feature.fromGeometry(MultiLineString.fromLngLats(lines)),
        )
    }
}

internal fun Style.showRoadCompletionPulse(
    road: RenderedRoadSegment?,
    progress: Float,
) {
    val source = roadGeoJsonSource(RoadPulseSource)
    if (getLayer(RoadPulseGlowLayer) == null) {
        addRoadLayer(
            CircleLayer(RoadPulseGlowLayer, RoadPulseSource).withProperties(
                circleColor(GameRoadGreen.toArgb()),
                circleRadius(13f),
                circleOpacity(0.28f),
            ),
            above = TourRouteLayer,
        )
    }
    if (getLayer(RoadPulseCoreLayer) == null) {
        addRoadLayer(
            CircleLayer(RoadPulseCoreLayer, RoadPulseSource).withProperties(
                circleColor(Color.White.toArgb()),
                circleRadius(4.5f),
                circleStrokeColor(GameRoadGreen.toArgb()),
                circleStrokeWidth(2f),
            ),
            above = RoadPulseGlowLayer,
        )
    }
    val features = if (road == null) {
        emptyList()
    } else {
        listOfNotNull(
            roadPointAtFraction(
                road.points,
                (progress.coerceIn(0f, 1f) * 0.5f).toDouble(),
            ),
            roadPointAtFraction(
                road.points,
                (1f - progress.coerceIn(0f, 1f) * 0.5f).toDouble(),
            ),
        ).map { coordinate ->
            Feature.fromGeometry(Point.fromLngLat(coordinate.longitude, coordinate.latitude))
        }
    }
    source.setGeoJson(FeatureCollection.fromFeatures(features))
}

internal fun Style.showRoadCounts(completedRoads: Collection<CompletedRoad>) {
    val source = roadGeoJsonSource(RoadCountSource)
    val layer = getLayerAs<SymbolLayer>(RoadCountLayer)
        ?: SymbolLayer(RoadCountLayer, RoadCountSource).also { countLayer ->
            addRoadLayer(countLayer, above = TourRouteLayer)
        }
    layer.minZoom = RoadCountMinimumZoom.toFloat()
    layer.setProperties(
        textField(Expression.get(RoadCountLabelProperty)),
        textFont(arrayOf("Noto Sans Bold")),
        textSize(RoadCountTextSizeSp),
        textColor(Ink.toArgb()),
        textHaloColor(GameRoadGreen.toArgb()),
        textHaloWidth(RoadCountHaloWidthPixels),
        textAnchor(Property.TEXT_ANCHOR_CENTER),
        textPadding(RoadCountCollisionPaddingPixels),
        textAllowOverlap(false),
        textIgnorePlacement(false),
        symbolSortKey(Expression.get(RoadCountSortProperty)),
    )
    source.setGeoJson(
        FeatureCollection.fromFeatures(roadCountFeatures(completedRoads)),
    )
}

internal fun roadCountFeatures(
    completedRoads: Collection<CompletedRoad>,
): List<Feature> = completedRoads.mapNotNull { completedRoad ->
    if (completedRoad.count < RoadCountMinimumVisibleCount) return@mapNotNull null
    val midpoint = roadPointAtFraction(completedRoad.road.points, 0.5)
        ?: return@mapNotNull null
    Feature.fromGeometry(
        Point.fromLngLat(midpoint.longitude, midpoint.latitude),
    ).apply {
        addStringProperty(RoadCountLabelProperty, "×${completedRoad.count}")
        addNumberProperty(RoadCountSortProperty, -completedRoad.count)
    }
}

internal fun roadCoverageSegments(
    completedRoads: Collection<CompletedRoad>,
): List<List<SpurCoordinate>> = completedRoads.map { completedRoad ->
    completedRoad.road.points
}

private fun Feature.roadPointLists(): List<List<SpurCoordinate>> =
    when (val geometry = geometry()) {
        is LineString -> listOf(geometry.coordinates().toSpurCoordinates())
        is MultiLineString -> geometry.coordinates().map { it.toSpurCoordinates() }
        else -> emptyList()
    }

private fun List<Point>.toSpurCoordinates() = map { point ->
    SpurCoordinate(latitude = point.latitude(), longitude = point.longitude())
}

private fun Feature.stringProperty(name: String): String =
    if (hasProperty(name)) getProperty(name).asString else ""

private fun roadKind(roadClass: String): RoadKind = when (roadClass) {
    "path", "track" -> RoadKind.PATH
    "service" -> RoadKind.SERVICE
    else -> RoadKind.STREET
}

private fun Style.roadGeoJsonSource(id: String): GeoJsonSource =
    getSourceAs<GeoJsonSource>(id) ?: GeoJsonSource(id).also(::addSource)

private fun Style.addRoadLayer(layer: org.maplibre.android.style.layers.Layer, above: String) {
    if (getLayer(above) == null) addLayer(layer) else addLayerAbove(layer, above)
}

private fun Style.addRoadProgressLayer(layer: org.maplibre.android.style.layers.Layer) {
    if (getLayer(TourRouteBorderLayer) == null) {
        addLayer(layer)
    } else {
        addLayerBelow(layer, TourRouteBorderLayer)
    }
}

private const val RoadSourceLayer = "transportation"
private val RoadExcludedLayerTerms = listOf("casing", "hatching", "arrow")
private val RoadIncludedClasses = setOf(
    "motorway",
    "trunk",
    "primary",
    "secondary",
    "tertiary",
    "minor",
    "service",
    "path",
    "track",
)
internal const val RoadNetworkRadiusMeters = 750.0
internal const val RoadMaximumMatchDistanceMeters = 30.0
internal const val RoadHistoryMinimumZoom = 13.0
internal const val RoadHistoryDetailZoom = 15.0
internal const val RoadCountMinimumZoom = RoadHistoryDetailZoom
internal fun shouldShowRoadHistory(mapZoom: Double): Boolean =
    mapZoom >= RoadHistoryMinimumZoom
private const val RoadProgressSource = "road-progress-fill-source"
private const val RoadProgressLayer = "road-progress-fill-layer"
private const val GameRoadWidthPixels = TourRouteBorderWidthPixels + 8f
private const val GameRoadOverviewWidthPixels = 5f
private const val RoadPulseSource = "road-progress-pulse-source"
private const val RoadPulseGlowLayer = "road-progress-pulse-glow-layer"
private const val RoadPulseCoreLayer = "road-progress-pulse-core-layer"
private const val RoadCountSource = "road-progress-count-source"
private const val RoadCountLayer = "road-progress-count-layer"
internal const val RoadCountLabelProperty = "road_count_label"
internal const val RoadCountSortProperty = "road_count_sort"
internal const val RoadCountMinimumVisibleCount = 2
private const val RoadCountTextSizeSp = 14f
private const val RoadCountHaloWidthPixels = 2.5f
private const val RoadCountCollisionPaddingPixels = 12f
