package app.spur

import android.content.Context
import androidx.compose.foundation.background

internal const val MapSettingsPreferences = "map-settings"
internal const val DefaultZoomPreference = "default-zoom"
internal const val DefaultRotationPreference = "default-rotation"
internal const val MapControlColorPreference = "map-control-color"
internal const val MapControlForegroundColorPreference = "map-control-foreground-color"
internal const val TrailFillColorPreference = "trail-fill-color"
internal const val TrailStrokeColorPreference = "trail-stroke-color"

internal fun Context.loadDefaultMapZoom(): Double =
    getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
        .getFloat(DefaultZoomPreference, DefaultMapZoom.toFloat())
        .toDouble()

internal fun Context.saveDefaultMapZoom(zoom: Double) {
    getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
        .edit()
        .putFloat(DefaultZoomPreference, zoom.toFloat())
        .apply()
}

internal fun Context.loadDefaultMapRotation(): MapRotation =
    mapRotationFromStored(
        getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
            .getString(DefaultRotationPreference, null),
    )

internal fun Context.saveDefaultMapRotation(rotation: MapRotation) {
    getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
        .edit()
        .putString(DefaultRotationPreference, rotation.name)
        .apply()
}

internal fun Context.loadMapControlColor(): MapControlColor =
    mapControlColorFromStored(
        getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
            .getString(MapControlColorPreference, null),
    )

internal fun Context.saveMapControlColor(color: MapControlColor) {
    getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
        .edit()
        .putString(MapControlColorPreference, color.name)
        .apply()
}

internal fun Context.loadMapControlForegroundColor(
    background: MapControlColor,
): MapControlColor {
    val stored = getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
        .getString(MapControlForegroundColorPreference, null)
    return stored
        ?.let(::mapControlColorFromStored)
        ?: defaultMapControlForeground(background)
}

internal fun Context.saveMapControlForegroundColor(color: MapControlColor) {
    getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
        .edit()
        .putString(MapControlForegroundColorPreference, color.name)
        .apply()
}

internal fun Context.loadTrailFillColor(): MapControlColor =
    mapControlColorFromStored(
        getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
            .getString(TrailFillColorPreference, null),
        fallback = MapControlColor.YELLOW,
    )

internal fun Context.saveTrailFillColor(color: MapControlColor) {
    getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
        .edit()
        .putString(TrailFillColorPreference, color.name)
        .apply()
}

internal fun Context.loadTrailStrokeColor(): MapControlColor =
    mapControlColorFromStored(
        getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
            .getString(TrailStrokeColorPreference, null),
        fallback = MapControlColor.BLACK,
    )

internal fun Context.saveTrailStrokeColor(color: MapControlColor) {
    getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
        .edit()
        .putString(TrailStrokeColorPreference, color.name)
        .apply()
}

internal fun Context.loadTrailColors(): TrailColors =
    TrailColors(
        fill = loadTrailFillColor().color,
        stroke = loadTrailStrokeColor().color.copy(alpha = TrailStrokeAlpha),
    )
