package app.spur

import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import org.maplibre.android.maps.ImageContent
import org.maplibre.android.maps.ImageStretches
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconTextFit
import org.maplibre.android.style.layers.PropertyFactory.iconTextFitPadding
import org.maplibre.android.style.layers.PropertyFactory.symbolSortKey
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textMaxWidth
import org.maplibre.android.style.layers.PropertyFactory.textOpacity
import org.maplibre.android.style.layers.PropertyFactory.textPadding
import org.maplibre.android.style.layers.PropertyFactory.textPitchAlignment
import org.maplibre.android.style.layers.PropertyFactory.textRadialOffset
import org.maplibre.android.style.layers.PropertyFactory.textRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.PropertyFactory.textVariableAnchor
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.TransitionOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

internal const val MapLandmarkPointLayer = "map-landmark-point-layer"
internal const val MapLandmarkLabelLayer = "map-landmark-label-layer"
internal const val MapLandmarkIdProperty = "landmark-id"

private const val MapLandmarkSource = "map-landmark-source"
private const val MapLandmarkTitleProperty = "landmark-title"
private const val MapLandmarkRedProperty = "landmark-red"
private const val MapLandmarkGreenProperty = "landmark-green"
private const val MapLandmarkBlueProperty = "landmark-blue"
private const val MapLandmarkSortProperty = "landmark-sort"
private const val MapLandmarkLabelBackgroundImage = "map-landmark-label-background"
private const val MapLandmarkPointRadius = 6f
private const val MapLandmarkPointStrokeWidth = 3f
private const val MapLandmarkLabelSize = 13f
private const val MapLandmarkLabelHaloWidth = 4f

internal fun Style.showMapLandmarks(landmarks: List<Landmark>) {
    val features = landmarks.sortedWith(LandmarkDisplayOrder).mapIndexed { index, landmark ->
        Feature.fromGeometry(
            Point.fromLngLat(
                landmark.coordinate.longitude,
                landmark.coordinate.latitude,
            ),
        ).apply {
            addStringProperty(MapLandmarkIdProperty, landmark.id)
            addStringProperty(MapLandmarkTitleProperty, landmark.title)
            addNumberProperty(MapLandmarkRedProperty, android.graphics.Color.red(landmark.colorArgb))
            addNumberProperty(MapLandmarkGreenProperty, android.graphics.Color.green(landmark.colorArgb))
            addNumberProperty(MapLandmarkBlueProperty, android.graphics.Color.blue(landmark.colorArgb))
            addNumberProperty(MapLandmarkSortProperty, index)
        }
    }
    val source = getSourceAs<GeoJsonSource>(MapLandmarkSource)
        ?: GeoJsonSource(MapLandmarkSource).also(::addSource)
    source.setGeoJson(FeatureCollection.fromFeatures(features))

    val featureColor = Expression.rgba(
        Expression.get(MapLandmarkRedProperty),
        Expression.get(MapLandmarkGreenProperty),
        Expression.get(MapLandmarkBlueProperty),
        Expression.literal(1f),
    )
    if (getLayer(MapLandmarkPointLayer) == null) {
        addLayerBelowLocationPulse(
            CircleLayer(MapLandmarkPointLayer, MapLandmarkSource)
                .withFilter(Expression.literal(false))
                .withProperties(
                    circleRadius(MapLandmarkPointRadius),
                    circleColor(featureColor),
                    circleStrokeColor(SheetBackground.toArgb()),
                    circleStrokeWidth(MapLandmarkPointStrokeWidth),
                    circleOpacity(0f),
                    circleStrokeOpacity(0f),
                    visibility(Property.NONE),
                ).also { layer ->
                    layer.setCircleOpacityTransition(landmarkTransition())
                    layer.setCircleStrokeOpacityTransition(landmarkTransition())
                },
        )
    }
    if (getLayer(MapLandmarkLabelLayer) == null) {
        addImage(
            MapLandmarkLabelBackgroundImage,
            landmarkLabelBackground(),
            listOf(ImageStretches(1f, 2f)),
            listOf(ImageStretches(1f, 2f)),
            ImageContent(1f, 1f, 2f, 2f),
        )
        addLayerBelowLocationPulse(
            SymbolLayer(MapLandmarkLabelLayer, MapLandmarkSource)
                .withFilter(Expression.literal(false))
                .withProperties(
                    iconImage(MapLandmarkLabelBackgroundImage),
                    iconTextFit(Property.ICON_TEXT_FIT_BOTH),
                    iconTextFitPadding(arrayOf(5f, 10f, 5f, 10f)),
                    iconAllowOverlap(false),
                    iconIgnorePlacement(false),
                    iconOpacity(0f),
                    textField(Expression.get(MapLandmarkTitleProperty)),
                    textFont(arrayOf("Noto Sans Regular")),
                    textSize(MapLandmarkLabelSize),
                    textColor(featureColor),
                    textHaloColor(
                        SheetBackground
                            .copy(alpha = 1f - HomeStatusBackgroundTransparency)
                            .toArgb(),
                    ),
                    textHaloWidth(MapLandmarkLabelHaloWidth),
                    textMaxWidth(10f),
                    textPadding(4f),
                    textVariableAnchor(
                        arrayOf(
                            Property.TEXT_ANCHOR_LEFT,
                            Property.TEXT_ANCHOR_RIGHT,
                            Property.TEXT_ANCHOR_TOP,
                            Property.TEXT_ANCHOR_BOTTOM,
                        ),
                    ),
                    textRadialOffset(0.9f),
                    textAllowOverlap(false),
                    textIgnorePlacement(false),
                    textPitchAlignment(Property.TEXT_PITCH_ALIGNMENT_VIEWPORT),
                    textRotationAlignment(Property.TEXT_ROTATION_ALIGNMENT_VIEWPORT),
                    symbolSortKey(Expression.get(MapLandmarkSortProperty)),
                    textOpacity(0f),
                    visibility(Property.NONE),
                ).also { layer ->
                    layer.setIconOpacityTransition(landmarkTransition())
                    layer.setTextOpacityTransition(landmarkTransition())
                },
        )
    }
}

internal fun Style.setMapLandmarkSelection(landmarkIds: Set<String>) {
    val filter = if (landmarkIds.isEmpty()) {
        Expression.literal(false)
    } else {
        Expression.any(
            *landmarkIds.map { id ->
                Expression.eq(
                    Expression.get(MapLandmarkIdProperty),
                    Expression.literal(id),
                )
            }.toTypedArray(),
        )
    }
    getLayerAs<CircleLayer>(MapLandmarkPointLayer)?.setFilter(filter)
    getLayerAs<SymbolLayer>(MapLandmarkLabelLayer)?.setFilter(filter)
}

internal fun Style.setMapLandmarksVisible(visible: Boolean) {
    val opacity = if (visible) 1f else 0f
    if (visible) setMapLandmarkLayerVisibility(Property.VISIBLE)
    getLayerAs<CircleLayer>(MapLandmarkPointLayer)?.setProperties(
        circleOpacity(opacity),
        circleStrokeOpacity(opacity),
    )
    getLayerAs<SymbolLayer>(MapLandmarkLabelLayer)?.setProperties(
        iconOpacity(opacity),
        textOpacity(opacity),
    )
}

internal fun Style.hideMapLandmarkLayers() {
    setMapLandmarkLayerVisibility(Property.NONE)
}

private fun Style.setMapLandmarkLayerVisibility(visibilityValue: String) {
    getLayer(MapLandmarkPointLayer)?.setProperties(visibility(visibilityValue))
    getLayer(MapLandmarkLabelLayer)?.setProperties(visibility(visibilityValue))
}

private fun landmarkLabelBackground(): Bitmap = Bitmap.createBitmap(
    3,
    3,
    Bitmap.Config.ARGB_8888,
).apply {
    eraseColor(
        SheetBackground
            .copy(alpha = 1f - HomeStatusBackgroundTransparency)
            .toArgb(),
    )
}

private fun landmarkTransition() = TransitionOptions(
    MotionDurationDefaultMillis.toLong(),
    0L,
)
