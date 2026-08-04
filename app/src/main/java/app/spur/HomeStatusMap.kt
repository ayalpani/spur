package app.spur

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.PropertyFactory.iconPitchAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

private const val HomeStatusSource = "home-status-source"
private const val HomeStatusLayer = "home-status-layer"
private const val HomeStatusImage = "home-status-image"
private const val HomeStatusText = "Du bist zu Hause."
private const val HomeStatusTextSizeSp = 16f
private const val HomeStatusHorizontalPaddingDp = 16f
private const val HomeStatusVerticalPaddingDp = 10f
private const val HomeStatusVerticalOffsetDp = 18f

internal fun homeStatusFeature(coordinate: SpurCoordinate?): Feature? = coordinate?.let {
    Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude))
}

internal fun Style.showHomeStatus(
    context: Context,
    colors: MapControlColors,
    coordinate: SpurCoordinate?,
) {
    val feature = homeStatusFeature(coordinate)
    val source = getSourceAs<GeoJsonSource>(HomeStatusSource)
        ?: if (feature == null) {
            return
        } else {
            GeoJsonSource(HomeStatusSource).also(::addSource)
        }
    source.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(feature)))
    if (feature == null) return

    addImage(HomeStatusImage, createHomeStatusMarkerBitmap(context, colors))
    if (getLayer(HomeStatusLayer) == null) {
        addLayer(
            SymbolLayer(HomeStatusLayer, HomeStatusSource).withProperties(
                iconImage(HomeStatusImage),
                iconOffset(arrayOf(0f, HomeStatusVerticalOffsetDp)),
                iconAnchor(Property.ICON_ANCHOR_TOP),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
            ),
        )
    }
}

private fun createHomeStatusMarkerBitmap(
    context: Context,
    colors: MapControlColors,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val scaledDensity = density * context.resources.configuration.fontScale
    val inverted = colors.inverted
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = HomeStatusTextSizeSp * scaledDensity
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    val fontMetrics = paint.fontMetrics
    val width = paint.measureText(HomeStatusText) + HomeStatusHorizontalPaddingDp * 2f * density
    val height = fontMetrics.descent - fontMetrics.ascent +
        HomeStatusVerticalPaddingDp * 2f * density
    val bitmap = Bitmap.createBitmap(
        width.roundToInt(),
        height.roundToInt(),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    paint.style = Paint.Style.FILL
    paint.color = inverted.background
        .copy(alpha = 1f - HomeStatusBackgroundTransparency)
        .toArgb()
    canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
    paint.color = inverted.foreground.toArgb()
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(
        HomeStatusText,
        bitmap.width / 2f,
        bitmap.height / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f,
        paint,
    )
    return bitmap
}
