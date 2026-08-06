package app.spur

import android.content.Context
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.selected
import java.io.File
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.location.LocationComponentConstants
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
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.expressions.Expression
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlinx.coroutines.flow.filter

private const val MapMomentLocationClearance = 8f

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
    locationMarkerColors: LocationMarkerColors,
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
            markerColors = locationMarkerColors,
            pulseColor = trailColors.stroke,
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
        val tileSet = TileSet("2.2.0", SatelliteTileUrl).apply {
            maxZoom = SatelliteMapZoomMaximum.toFloat()
        }
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
    val features: List<Feature>,
)

internal data class PreparedMapMomentImages(
    val keys: List<MapMomentImageKey>,
    val images: HashMap<String, android.graphics.Bitmap>,
    val photoPreviews: Map<String, PreparedPhotoPreview>,
)

internal data class MapMomentImageKey(
    val id: String,
    val type: MomentType,
    val payload: String,
)

internal fun mapMomentImageKeys(moments: List<MapMoment>): List<MapMomentImageKey> =
    moments.map { MapMomentImageKey(it.id, it.type, it.payload) }

internal data class PreparedPhotoPreview(
    val bitmap: android.graphics.Bitmap,
    val aspectRatio: Float,
)

internal fun prepareMapMomentImages(
    context: Context,
    moments: List<MapMoment>,
): PreparedMapMomentImages {
    val images = HashMap<String, android.graphics.Bitmap>(moments.size * 3)
    val photoPreviews = HashMap<String, PreparedPhotoPreview>()
    moments.forEach { moment ->
        val imageId = MapMomentImagePrefix + moment.id
        val marker = createMomentMarkerBitmap(
            context = context,
            moment = moment,
            selected = false,
            onPhotoDecoded = { bitmap ->
                photoPreviews[moment.id] = PreparedPhotoPreview(
                    bitmap = bitmap,
                    aspectRatio = photoAspectRatio(File(moment.payload)),
                )
            },
        )
        images[imageId] = marker
        listOf(2, 3).forEach { stackSize ->
            images[clusterMomentImageId(moment, stackSize)] =
                createMomentClusterBitmap(context, marker, stackSize)
        }
    }
    return PreparedMapMomentImages(
        keys = mapMomentImageKeys(moments),
        images = images,
        photoPreviews = photoPreviews,
    )
}

internal fun prepareMapMoments(
    moments: List<MapMoment>,
): PreparedMapMoments {
    val features = moments.mapIndexed { index, moment ->
        val imageId = MapMomentImagePrefix + moment.id
        Feature.fromGeometry(
            Point.fromLngLat(moment.longitude, moment.latitude),
        ).apply {
            addStringProperty(MapMomentIdProperty, moment.id)
            addStringProperty(MapMomentImageProperty, imageId)
            addNumberProperty(MapMomentRepresentativeProperty, index)
            addNumberProperty(MapMomentAtUserSpotProperty, 0)
        }
    }
    return PreparedMapMoments(
        moments = moments,
        features = features,
    )
}

internal fun Style.showMapMomentImages(prepared: PreparedMapMomentImages) {
    if (prepared.images.isNotEmpty()) addImages(prepared.images)
}

internal fun Style.showMapMoments(
    prepared: PreparedMapMoments,
    userSpotMomentIds: Set<String>,
) {
    val moments = prepared.moments
    val features = prepared.features
    features.forEach { feature ->
        feature.addNumberProperty(
            MapMomentAtUserSpotProperty,
            if (feature.getStringProperty(MapMomentIdProperty) in userSpotMomentIds) 1 else 0,
        )
    }

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
                )
                .withClusterProperty(
                    MapMomentAtUserSpotProperty,
                    Expression.max(
                        Expression.accumulated(),
                        Expression.get(MapMomentAtUserSpotProperty),
                    ),
                    Expression.get(MapMomentAtUserSpotProperty),
                )
        ).also(::addSource)
    source.setGeoJson(
        FeatureCollection.fromFeatures(features),
    )

    val momentOffset = momentOffsetExpression(moments)
    val unclusteredMomentFilter = Expression.neq(Expression.get("cluster"), true)
    val momentLayer = getLayerAs<SymbolLayer>(MapMomentLayer)
    if (momentLayer == null) {
        addLayerBelowLocationPulse(
            SymbolLayer(MapMomentLayer, MapMomentSource)
                .withFilter(unclusteredMomentFilter)
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
        momentLayer.setProperties(iconOffset(momentOffset))
    }

    val clusterImage = clusterMomentImageExpression(moments)
    val userSpotClusterFilter = Expression.all(
        Expression.has("point_count"),
        Expression.eq(
            Expression.toNumber(Expression.get(MapMomentAtUserSpotProperty)),
            Expression.literal(1),
        ),
    )
    val regularClusterFilter = Expression.all(
        Expression.has("point_count"),
        Expression.neq(
            Expression.toNumber(Expression.get(MapMomentAtUserSpotProperty)),
            Expression.literal(1),
        ),
    )
    val clusterLayer = getLayerAs<SymbolLayer>(MapMomentClusterLayer)
    if (clusterLayer == null) {
        addLayerBelowLocationPulse(
            SymbolLayer(MapMomentClusterLayer, MapMomentSource)
                .withFilter(regularClusterFilter)
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
        )
    }
    val userSpotClusterLayer = getLayerAs<SymbolLayer>(MapMomentUserSpotClusterLayer)
    if (userSpotClusterLayer == null) {
        addLayerBelowLocationPulse(
            SymbolLayer(MapMomentUserSpotClusterLayer, MapMomentSource)
                .withFilter(userSpotClusterFilter)
                .withProperties(
                    iconImage(clusterImage),
                    iconOffset(
                        arrayOf(
                            UserSpotMomentClusterOffsetX,
                            UserSpotMomentClusterOffsetY,
                        ),
                    ),
                    iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                    iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                    iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                    symbolZOrder(Property.SYMBOL_Z_ORDER_VIEWPORT_Y),
                ),
        )
    } else {
        userSpotClusterLayer.setProperties(iconImage(clusterImage))
    }

    if (getLayer(MapMomentClusterCountBadgeLayer) == null) {
        val countBadgeLayer =
            CircleLayer(MapMomentClusterCountBadgeLayer, MapMomentSource)
                .withFilter(regularClusterFilter)
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
            addLayerBelowLocationPulse(countBadgeLayer)
        } else {
            addLayerBelow(countBadgeLayer, MapMomentClusterCountLayer)
        }
    }
    if (getLayer(MapMomentUserSpotClusterCountBadgeLayer) == null) {
        val countBadgeLayer =
            CircleLayer(MapMomentUserSpotClusterCountBadgeLayer, MapMomentSource)
                .withFilter(userSpotClusterFilter)
                .withProperties(
                    circleRadius(MapMomentClusterCountBadgeRadius),
                    circleColor(Ink.toArgb()),
                    circleTranslate(
                        arrayOf(
                            MapMomentClusterCountPositionX + UserSpotMomentClusterOffsetX,
                            MapMomentClusterCountPositionY + UserSpotMomentClusterOffsetY,
                        ),
                    ),
                    circleTranslateAnchor(Property.CIRCLE_TRANSLATE_ANCHOR_VIEWPORT),
                )
        addLayerBelowLocationPulse(countBadgeLayer)
    }

    if (getLayer(MapMomentClusterCountLayer) == null) {
        addLayerBelowLocationPulse(
            SymbolLayer(MapMomentClusterCountLayer, MapMomentSource)
                .withFilter(regularClusterFilter)
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
    if (getLayer(MapMomentUserSpotClusterCountLayer) == null) {
        addLayerBelowLocationPulse(
            SymbolLayer(MapMomentUserSpotClusterCountLayer, MapMomentSource)
                .withFilter(userSpotClusterFilter)
                .withProperties(
                    textField(Expression.toString(Expression.get("point_count_abbreviated"))),
                    textFont(arrayOf("Noto Sans Bold")),
                    textSize(13f),
                    textColor(android.graphics.Color.WHITE),
                    textTranslate(
                        arrayOf(
                            MapMomentClusterCountPositionX + UserSpotMomentClusterOffsetX,
                            MapMomentClusterCountPositionY + UserSpotMomentClusterOffsetY,
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

private fun momentOffsetExpression(moments: List<MapMoment>): Expression {
    val offsets = overlappingMomentOffsets(moments)
    val center = Expression.literal(arrayOf(0f, -MapMomentLocationClearance))
    if (offsets.isEmpty()) return center
    val stops = offsets.map { (momentId, offset) ->
        Expression.stop(momentId, Expression.literal(arrayOf(offset.x, offset.y - MapMomentLocationClearance)))
    }.toTypedArray()
    return Expression.match(
        Expression.get(MapMomentIdProperty),
        center,
        *stops,
    )
}

internal fun Style.addLayerBelowLocationPulse(layer: Layer) {
    val pulseLayer = LocationComponentConstants.PULSING_CIRCLE_LAYER
    if (getLayer(pulseLayer) == null) {
        addLayer(layer)
    } else {
        addLayerBelow(layer, pulseLayer)
    }
}
