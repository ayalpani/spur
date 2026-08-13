package app.spur

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

internal const val PublicItemSource = "public-item-source"
internal const val PublicItemLayer = "public-item-layer"
internal const val PublicItemClusterLayer = "public-item-cluster-layer"
internal const val PublicItemClusterCountLayer = "public-item-cluster-count-layer"
internal const val PublicItemIdProperty = "public-item-id"
private const val PublicItemKindProperty = "public-item-kind"
private const val PublicItemClusterCountProperty = "public-item-count"
private const val PublicItemImagePrefix = "public-item-"

internal fun Style.showPublicItems(
    context: Context,
    page: PublicItemsPage,
    selectedItemId: String?,
) {
    ItemKind.entries.forEach { kind ->
        val imageName = PublicItemImagePrefix + kind.name.lowercase()
        if (getImage(imageName) == null) {
            addImage(imageName, BitmapFactory.decodeResource(context.resources, kind.mapImageResource))
        }
    }
    val features = page.items.map { item ->
        Feature.fromGeometry(Point.fromLngLat(item.location.longitude, item.location.latitude)).apply {
            addStringProperty(PublicItemIdProperty, item.id)
            addStringProperty(PublicItemKindProperty, item.kind.name)
            addBooleanProperty("selected", item.id == selectedItemId)
        }
    } + page.clusters.map { cluster ->
        Feature.fromGeometry(Point.fromLngLat(cluster.longitude, cluster.latitude)).apply {
            addNumberProperty(PublicItemClusterCountProperty, cluster.count)
        }
    }
    val source = getSourceAs<GeoJsonSource>(PublicItemSource)
        ?: GeoJsonSource(PublicItemSource).also(::addSource)
    source.setGeoJson(FeatureCollection.fromFeatures(features))

    if (getLayer(PublicItemLayer) == null) {
        addLayerBelowLocationPulse(
            SymbolLayer(PublicItemLayer, PublicItemSource)
                .withFilter(Expression.has(PublicItemIdProperty))
                .withProperties(
                    iconImage(
                        Expression.concat(
                            Expression.literal(PublicItemImagePrefix),
                            Expression.downcase(Expression.get(PublicItemKindProperty)),
                        ),
                    ),
                    iconSize(
                        Expression.switchCase(
                            Expression.eq(Expression.get("selected"), true),
                            Expression.literal(0.88f),
                            Expression.literal(0.72f),
                        ),
                    ),
                    iconAnchor(Property.ICON_ANCHOR_CENTER),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                ),
        )
    }
    if (getLayer(PublicItemClusterLayer) == null) {
        addLayerBelowLocationPulse(
            CircleLayer(PublicItemClusterLayer, PublicItemSource)
                .withFilter(Expression.has(PublicItemClusterCountProperty))
                .withProperties(
                    circleRadius(19f),
                    circleColor(LocalClusterColor.toArgb()),
                ),
        )
    }
    if (getLayer(PublicItemClusterCountLayer) == null) {
        addLayerBelowLocationPulse(
            SymbolLayer(PublicItemClusterCountLayer, PublicItemSource)
                .withFilter(Expression.has(PublicItemClusterCountProperty))
                .withProperties(
                    textField(Expression.toString(Expression.get(PublicItemClusterCountProperty))),
                    textFont(arrayOf("Noto Sans Bold")),
                    textSize(13f),
                    textColor(android.graphics.Color.WHITE),
                    textAnchor(Property.TEXT_ANCHOR_CENTER),
                    textAllowOverlap(true),
                    textIgnorePlacement(true),
                ),
        )
    }
}

internal fun MapLibreMap.publicItemBounds(): ItemMapBounds {
    val bounds = projection.visibleRegion.latLngBounds
    return ItemMapBounds(
        west = bounds.longitudeWest,
        south = bounds.latitudeSouth,
        east = bounds.longitudeEast,
        north = bounds.latitudeNorth,
    )
}

internal fun MapLibreMap.zoomIntoPublicItemCluster(point: LatLng) {
    animateCamera(
        org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
            point,
            (cameraPosition.zoom + 2).coerceAtMost(18.0),
        ),
        MapRotationAnimationMillis.toInt(),
    )
}

private val LocalClusterColor: Color = MapControlColor.BLACK.color
