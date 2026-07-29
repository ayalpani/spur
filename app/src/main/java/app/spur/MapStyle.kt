package app.spur

import android.content.Context
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.selected
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleTranslate
import org.maplibre.android.style.layers.PropertyFactory.circleTranslateAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.PropertyFactory.iconPitchAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.symbolZOrder
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.PropertyFactory.textTranslate
import org.maplibre.android.style.layers.PropertyFactory.textTranslateAnchor
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.expressions.Expression
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlinx.coroutines.flow.filter

internal fun setMapStyle(
    context: Context,
    map: MapLibreMap,
    satellite: Boolean,
    centerOnLocation: Boolean,
    manualLocation: SpurCoordinate?,
    initialMapZoom: Double,
    defaultMapBearing: Double,
    routePoints: List<TrackPoint>,
    trailColors: TrailColors,
    locationPulseColor: Color,
    onLoaded: () -> Unit,
) {
    val cameraPosition = map.cameraPosition
    val styleLoaded: (Style) -> Unit = { style ->
        style.installSatelliteBaseMap()
        style.showSatelliteBaseMap(
            satellite = satellite,
        )
        style.hideDistractingPoiLayers()
        style.showOutlinedBuildings()
        enableLocationTracking(
            context = context,
            map = map,
            style = style,
            centerOnLocation = centerOnLocation,
            manualLocation = manualLocation,
            initialMapZoom = initialMapZoom,
            defaultMapBearing = defaultMapBearing,
            pulseColor = locationPulseColor,
        )
        style.showTourRoute(routePoints, trailColors)
        if (!centerOnLocation) {
            map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        }
        onLoaded()
    }

    map.setStyle(StreetMapStyle, styleLoaded)
}

private fun Style.installSatelliteBaseMap() {
    if (getSource(SatelliteSource) == null) {
        val tileSet = TileSet("2.2.0", SatelliteTileUrl)
        addSource(RasterSource(SatelliteSource, tileSet, 256))
    }
    if (getLayer(SatelliteLayer) == null) {
        addLayer(RasterLayer(SatelliteLayer, SatelliteSource))
    }
}

internal fun Style.showSatelliteBaseMap(
    satellite: Boolean,
) {
    getLayer(SatelliteLayer)?.setProperties(
        visibility(if (satellite) Property.VISIBLE else Property.NONE),
    )
}

internal fun Style.hideDistractingPoiLayers() {
    layers
        .filterIsInstance<SymbolLayer>()
        .filter { it.sourceLayer == MapPoiSourceLayer }
        .forEach { it.setProperties(visibility(Property.NONE)) }
}

internal fun Style.showOutlinedBuildings() {
    getLayerAs<FillLayer>(MapBuildingLayer)?.setMaxZoom(MapBuildingMaxZoom)
    getLayer(MapBuilding3dLayer)?.setProperties(visibility(Property.NONE))
}

internal data class PreparedMapMoments(
    val moments: List<MapMoment>,
    val images: HashMap<String, android.graphics.Bitmap>,
    val features: List<Feature>,
)

internal data class MapMomentAvoidanceLayout(
    val momentOffsets: Map<String, Offset> = emptyMap(),
    val clusterOffsets: Map<Long, Offset> = emptyMap(),
)

internal fun prepareMapMoments(
    context: Context,
    moments: List<MapMoment>,
): PreparedMapMoments {
    val images = HashMap<String, android.graphics.Bitmap>(moments.size * 3)
    val features = moments.mapIndexed { index, moment ->
        val imageId = MapMomentImagePrefix + moment.id
        val marker = createMomentMarkerBitmap(context, moment, selected = false)
        images[imageId] = marker
        listOf(2, 3).forEach { stackSize ->
            images[clusterMomentImageId(moment, stackSize)] =
                createMomentClusterBitmap(context, marker, stackSize)
        }
        Feature.fromGeometry(
            Point.fromLngLat(moment.longitude, moment.latitude),
        ).apply {
            addStringProperty(MapMomentIdProperty, moment.id)
            addStringProperty(MapMomentImageProperty, imageId)
            addNumberProperty(MapMomentRepresentativeProperty, index)
        }
    }
    return PreparedMapMoments(
        moments = moments,
        images = images,
        features = features,
    )
}

internal fun Style.showMapMoments(prepared: PreparedMapMoments) {
    val moments = prepared.moments
    val images = prepared.images
    val features = prepared.features
    if (images.isNotEmpty()) addImages(images)

    val source = getSourceAs<GeoJsonSource>(MapMomentSource)
        ?: GeoJsonSource(
            MapMomentSource,
            GeoJsonOptions()
                .withMaxZoom(20)
                .withCluster(true)
                .withClusterMaxZoom(MapMomentClusterMaxZoom)
                .withClusterRadius(MapMomentClusterRadius)
                .withClusterProperty(
                    MapMomentRepresentativeProperty,
                    Expression.max(
                        Expression.accumulated(),
                        Expression.get(MapMomentRepresentativeProperty),
                    ),
                    Expression.get(MapMomentRepresentativeProperty),
                ),
        ).also(::addSource)
    source.setGeoJson(FeatureCollection.fromFeatures(features))

    val momentOffset = momentOffsetExpression(moments, emptyMap())
    val momentLayer = getLayerAs<SymbolLayer>(MapMomentLayer)
    if (momentLayer == null) {
        addLayer(
            SymbolLayer(MapMomentLayer, MapMomentSource)
                .withFilter(
                    Expression.neq(Expression.get("cluster"), true),
                )
                .withProperties(
                    iconImage(Expression.get(MapMomentImageProperty)),
                    iconOffset(momentOffset),
                    iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                    iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                    iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                    symbolZOrder(Property.SYMBOL_Z_ORDER_VIEWPORT_Y),
                ),
        )
    } else {
        momentLayer.setProperties(
            iconImage(Expression.get(MapMomentImageProperty)),
            iconOffset(momentOffset),
        )
    }

    val clusterImage = clusterMomentImageExpression(moments)
    val clusterLayer = getLayerAs<SymbolLayer>(MapMomentClusterLayer)
    if (clusterLayer == null) {
        addLayer(
            SymbolLayer(MapMomentClusterLayer, MapMomentSource)
                .withFilter(Expression.has("point_count"))
                .withProperties(
                    iconImage(clusterImage),
                    iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                    iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                    iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                    symbolZOrder(Property.SYMBOL_Z_ORDER_VIEWPORT_Y),
                ),
        )
    } else {
        clusterLayer.setProperties(
            iconImage(clusterImage),
            iconOffset(Expression.literal(arrayOf(0f, 0f))),
        )
    }

    if (getLayer(MapMomentClusterCountBadgeLayer) == null) {
        val countBadgeLayer =
            CircleLayer(MapMomentClusterCountBadgeLayer, MapMomentSource)
                .withFilter(Expression.has("point_count"))
                .withProperties(
                    circleRadius(MapMomentClusterCountBadgeRadius),
                    circleColor(Ink.toArgb()),
                    circleTranslate(
                        arrayOf(
                            MapMomentClusterCountPositionX,
                            MapMomentClusterCountPositionY,
                        ),
                    ),
                    circleTranslateAnchor(Property.CIRCLE_TRANSLATE_ANCHOR_VIEWPORT),
                )
        if (getLayer(MapMomentClusterCountLayer) == null) {
            addLayer(countBadgeLayer)
        } else {
            addLayerBelow(countBadgeLayer, MapMomentClusterCountLayer)
        }
    }

    if (getLayer(MapMomentClusterCountLayer) == null) {
        addLayer(
            SymbolLayer(MapMomentClusterCountLayer, MapMomentSource)
                .withFilter(Expression.has("point_count"))
                .withProperties(
                    textField(Expression.toString(Expression.get("point_count_abbreviated"))),
                    textFont(arrayOf("Noto Sans Bold")),
                    textSize(13f),
                    textColor(android.graphics.Color.WHITE),
                    textTranslate(
                        arrayOf(
                            MapMomentClusterCountPositionX,
                            MapMomentClusterCountPositionY,
                        ),
                    ),
                    textTranslateAnchor(Property.TEXT_TRANSLATE_ANCHOR_VIEWPORT),
                    textAnchor(Property.TEXT_ANCHOR_CENTER),
                    textAllowOverlap(true),
                    textIgnorePlacement(true),
                    symbolZOrder(Property.SYMBOL_Z_ORDER_VIEWPORT_Y),
                ),
        )
    }
}

internal fun Style.showMapMomentAvoidanceLayout(
    moments: List<MapMoment>,
    layout: MapMomentAvoidanceLayout,
) {
    getLayerAs<SymbolLayer>(MapMomentLayer)?.setProperties(
        iconOffset(momentOffsetExpression(moments, layout.momentOffsets)),
    )
    getLayerAs<SymbolLayer>(MapMomentClusterLayer)?.setProperties(
        iconOffset(clusterOffsetExpression(layout.clusterOffsets)),
    )
    showAvoidedClusterCounts(layout.clusterOffsets)
}

private fun clusterMomentImageExpression(moments: List<MapMoment>): Expression =
    Expression.switchCase(
        Expression.eq(
            Expression.toNumber(Expression.get("point_count")),
            Expression.literal(2),
        ),
        representativeClusterImageExpression(moments, 2),
        representativeClusterImageExpression(moments, 3),
    )

private fun representativeClusterImageExpression(
    moments: List<MapMoment>,
    stackSize: Int,
): Expression {
    val fallback = moments.firstOrNull()?.let {
        clusterMomentImageId(it, stackSize)
    }.orEmpty()
    val stops = moments.mapIndexed { index, moment ->
        Expression.stop(index, clusterMomentImageId(moment, stackSize))
    }.toTypedArray()
    return Expression.match(
        Expression.toNumber(Expression.get(MapMomentRepresentativeProperty)),
        Expression.literal(fallback),
        *stops,
    )
}

private fun clusterMomentImageId(moment: MapMoment, stackSize: Int): String =
    "$MapMomentClusterImagePrefix$stackSize-${moment.id}"

private fun momentOffsetExpression(
    moments: List<MapMoment>,
    avoidanceOffsets: Map<String, Offset>,
): Expression {
    val offsets = overlappingMomentOffsets(moments) + avoidanceOffsets
    val center = Expression.literal(arrayOf(0f, 0f))
    if (offsets.isEmpty()) return center
    val stops = offsets.map { (momentId, offset) ->
        Expression.stop(momentId, Expression.literal(arrayOf(offset.x, offset.y)))
    }.toTypedArray()
    return Expression.match(
        Expression.get(MapMomentIdProperty),
        center,
        *stops,
    )
}

private fun clusterOffsetExpression(offsets: Map<Long, Offset>): Expression {
    val center = Expression.literal(arrayOf(0f, 0f))
    if (offsets.isEmpty()) return center
    val stops = offsets.map { (clusterId, offset) ->
        Expression.stop(
            clusterId,
            Expression.literal(arrayOf(offset.x, offset.y)),
        )
    }.toTypedArray()
    return Expression.match(
        Expression.toNumber(Expression.get(MapMomentClusterIdProperty)),
        center,
        *stops,
    )
}

private fun Style.showAvoidedClusterCounts(offsets: Map<Long, Offset>) {
    val excludedIds = offsets.keys
    val normalFilter = Expression.all(
        Expression.has("point_count"),
        *excludedIds.map { clusterId ->
            Expression.neq(
                Expression.toNumber(Expression.get(MapMomentClusterIdProperty)),
                Expression.literal(clusterId),
            )
        }.toTypedArray(),
    )
    getLayerAs<CircleLayer>(MapMomentClusterCountBadgeLayer)?.setFilter(normalFilter)
    getLayerAs<SymbolLayer>(MapMomentClusterCountLayer)?.setFilter(normalFilter)

    val retainedLayerIds = offsets.keys.flatMap { clusterId ->
        listOf(
            MapMomentAvoidedClusterBadgePrefix + clusterId,
            MapMomentAvoidedClusterCountPrefix + clusterId,
        )
    }.toSet()
    layers
        .map { it.id }
        .filter {
            (
                it.startsWith(MapMomentAvoidedClusterBadgePrefix) ||
                    it.startsWith(MapMomentAvoidedClusterCountPrefix)
                ) && it !in retainedLayerIds
        }
        .forEach(::removeLayer)

    offsets.forEach { (clusterId, offset) ->
        val filter = Expression.eq(
            Expression.toNumber(Expression.get(MapMomentClusterIdProperty)),
            Expression.literal(clusterId),
        )
        val translate = arrayOf(
            offset.x + MapMomentClusterCountPositionX,
            offset.y + MapMomentClusterCountPositionY,
        )
        val badgeId = MapMomentAvoidedClusterBadgePrefix + clusterId
        val badge = getLayerAs<CircleLayer>(badgeId)
        if (badge == null) {
            addLayer(
                CircleLayer(badgeId, MapMomentSource)
                    .withFilter(filter)
                    .withProperties(
                        circleRadius(MapMomentClusterCountBadgeRadius),
                        circleColor(Ink.toArgb()),
                        circleTranslate(translate),
                        circleTranslateAnchor(Property.CIRCLE_TRANSLATE_ANCHOR_VIEWPORT),
                    ),
            )
        } else {
            badge.setFilter(filter)
            badge.setProperties(circleTranslate(translate))
        }

        val countId = MapMomentAvoidedClusterCountPrefix + clusterId
        val count = getLayerAs<SymbolLayer>(countId)
        if (count == null) {
            addLayer(
                SymbolLayer(countId, MapMomentSource)
                    .withFilter(filter)
                    .withProperties(
                        textField(
                            Expression.toString(
                                Expression.get("point_count_abbreviated"),
                            ),
                        ),
                        textFont(arrayOf("Noto Sans Bold")),
                        textSize(13f),
                        textColor(android.graphics.Color.WHITE),
                        textTranslate(translate),
                        textTranslateAnchor(Property.TEXT_TRANSLATE_ANCHOR_VIEWPORT),
                        textAnchor(Property.TEXT_ANCHOR_CENTER),
                        textAllowOverlap(true),
                        textIgnorePlacement(true),
                        symbolZOrder(Property.SYMBOL_Z_ORDER_VIEWPORT_Y),
                    ),
            )
        } else {
            count.setFilter(filter)
            count.setProperties(textTranslate(translate))
        }
    }
}
