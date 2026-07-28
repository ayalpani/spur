package app.spur

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentConstants
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconPitchAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconTranslate
import org.maplibre.android.style.layers.PropertyFactory.iconTranslateAnchor
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import java.io.File

internal fun satelliteStyleBuilder(): Style.Builder {
    return Style.Builder().fromJson(SatelliteMapStyleJson)
}

@SuppressLint("MissingPermission")
internal fun enableLocationTracking(
    context: Context,
    map: MapLibreMap,
    style: Style,
    centerOnLocation: Boolean,
    manualLocation: SpurCoordinate?,
    initialMapZoom: Double,
    defaultMapBearing: Double,
    pulseColor: Color,
) {
    if (!context.hasLocationPermission()) return

    val locationComponent = map.locationComponent
    val options = LocationComponentOptions.builder(context)
        .spurLocationAppearance(pulseColor)
        .build()
    locationComponent.activateLocationComponent(
        LocationComponentActivationOptions.builder(context, style)
            .locationComponentOptions(options)
            .useDefaultLocationEngine(true)
            .build(),
    )
    locationComponent.isLocationComponentEnabled = manualLocation == null
    locationComponent.renderMode = RenderMode.NORMAL
    locationComponent.cameraMode = CameraMode.NONE
    style.showCurrentLocationFootprints(
        context = context,
        visible = manualLocation == null,
    )

    val location = map.currentSpurCoordinate(
        context = context,
        manual = manualLocation,
    )
    if (centerOnLocation && location != null) {
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                org.maplibre.android.camera.CameraPosition.Builder()
                    .target(LatLng(location.latitude, location.longitude))
                    .zoom(initialMapZoom)
                    .bearing(defaultMapBearing)
                    .build(),
            ),
        )
    }
}

internal fun MapLibreMap.restartLocationPulse(color: Color) {
    val component = locationComponent
    if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return
    component.applyStyle(
        component.locationComponentOptions
            .toBuilder()
            .spurLocationAppearance(color)
            .build(),
    )
}

private fun LocationComponentOptions.Builder.spurLocationAppearance(
    color: Color,
): LocationComponentOptions.Builder =
    foregroundTintColor(color.toArgb())
        .backgroundTintColor(color.toArgb())
        .foregroundStaleTintColor(color.toArgb())
        .backgroundStaleTintColor(color.toArgb())
        .bearingTintColor(color.toArgb())
        .accuracyColor(color.toArgb())
        .pulseEnabled(true)
        .pulseFadeEnabled(true)
        .pulseColor(Color.White.toArgb())
        .pulseSingleDuration(LocationPulseDurationMillis.toFloat())
        .pulseMaxRadius(LocationPulseMaxRadius)
        .pulseAlpha(LocationPulseAlpha)
        .pulseInterpolator(AccelerateDecelerateInterpolator())

private fun Style.showCurrentLocationFootprints(
    context: Context,
    visible: Boolean,
) {
    context.currentLocationFootprintsBitmap()?.let {
        addImage(CurrentLocationFootprintsImage, it)
    }
    val layer = getLayerAs<SymbolLayer>(CurrentLocationFootprintsLayer)
    if (layer == null) {
        addLayerAbove(
            SymbolLayer(
                CurrentLocationFootprintsLayer,
                LocationComponentConstants.LOCATION_SOURCE,
            ).withProperties(
                iconImage(CurrentLocationFootprintsImage),
                iconAnchor(Property.ICON_ANCHOR_CENTER),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                iconTranslate(arrayOf(0f, -CurrentLocationFootprintsLiftPixels)),
                iconTranslateAnchor(Property.ICON_TRANSLATE_ANCHOR_VIEWPORT),
                visibility(
                    if (visible) Property.VISIBLE else Property.NONE,
                ),
            ),
            LocationComponentConstants.FOREGROUND_LAYER,
        )
    } else {
        layer.setProperties(
            visibility(
                if (visible) Property.VISIBLE else Property.NONE,
            ),
        )
    }
}

private fun Context.currentLocationFootprintsBitmap(): android.graphics.Bitmap? {
    val halo = ContextCompat.getDrawable(
        this,
        R.drawable.ic_footprints_location_halo,
    )?.mutate() ?: return null
    val footprints = ContextCompat.getDrawable(
        this,
        R.drawable.ic_footprints_location,
    )?.mutate() ?: return null
    val width = maxOf(halo.intrinsicWidth, footprints.intrinsicWidth)
    val height = maxOf(halo.intrinsicHeight, footprints.intrinsicHeight)
    return android.graphics.Bitmap.createBitmap(
        width,
        height,
        android.graphics.Bitmap.Config.ARGB_8888,
    ).also { bitmap ->
        val canvas = android.graphics.Canvas(bitmap)
        halo.setTint(Color.White.toArgb())
        halo.setBounds(0, 0, width, height)
        halo.draw(canvas)
        footprints.setTint(Ink.toArgb())
        footprints.setBounds(0, 0, width, height)
        footprints.draw(canvas)
    }
}

internal fun MapLibreMap.followLocation(
    context: Context,
    manualLocation: SpurCoordinate?,
    transitionDuration: Long,
    defaultMapBearing: Double,
) {
    if (manualLocation == null && locationComponent.isLocationComponentActivated) {
        locationComponent.setCameraMode(
            CameraMode.TRACKING,
            transitionDuration,
            cameraPosition.zoom,
            defaultMapBearing,
            null,
            null,
        )
        return
    }

    val location = currentSpurCoordinate(context = context, manual = manualLocation) ?: return
    val update = CameraUpdateFactory.newCameraPosition(
        org.maplibre.android.camera.CameraPosition.Builder(cameraPosition)
            .target(LatLng(location.latitude, location.longitude))
            .bearing(defaultMapBearing)
            .build(),
    )
    if (transitionDuration == 0L) {
        moveCamera(update)
    } else {
        animateCamera(update, transitionDuration.toInt())
    }
}

internal fun Context.hasLocationPermission(): Boolean =
    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

internal fun Context.hasCameraPermission(): Boolean =
    checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

internal fun Context.hasAudioRecordingPermission(): Boolean =
    checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

internal fun Context.createMomentFile(type: MomentType): File {
    val (directoryName, extension) = when (type) {
        MomentType.PHOTO -> "photos" to "jpg"
        MomentType.VIDEO -> "videos" to "mp4"
        MomentType.VOICE -> "voice" to "m4a"
        MomentType.EMOJI -> error("Emoji moments do not use files")
    }
    val directory = File(filesDir, "moments/$directoryName").apply { mkdirs() }
    return File(directory, "${type.name.lowercase()}-${System.currentTimeMillis()}.$extension")
}

private fun Location.toSpurCoordinate() =
    SpurCoordinate(latitude = latitude, longitude = longitude)

@SuppressLint("MissingPermission")
internal fun MapLibreMap.currentSpurCoordinate(
    context: Context,
    manual: SpurCoordinate?,
): SpurCoordinate? {
    if (manual != null) return manual
    val gps = if (locationComponent.isLocationComponentActivated) {
        locationComponent.lastKnownLocation
    } else {
        null
    } ?: context.bestLastKnownLocation()
    return resolveSpurCoordinate(
        manual = null,
        gps = gps?.toSpurCoordinate(),
    )
}

@SuppressLint("MissingPermission")
internal fun MapLibreMap.showGpsLocationPuck(
    context: Context,
    show: Boolean,
) {
    if (!context.hasLocationPermission() || !locationComponent.isLocationComponentActivated) return
    locationComponent.isLocationComponentEnabled = show
    style?.getLayer(CurrentLocationFootprintsLayer)?.setProperties(
        visibility(
            if (show) Property.VISIBLE else Property.NONE,
        ),
    )
}

@SuppressLint("MissingPermission")
private fun Context.bestLastKnownLocation(): Location? {
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.getProviders(true)
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull(Location::getTime)
}

private const val ManualLocationPreferences = "manual-location"
private const val ManualLatitude = "latitude"
private const val ManualLongitude = "longitude"

internal fun Context.loadManualLocation(): SpurCoordinate? {
    val preferences = getSharedPreferences(ManualLocationPreferences, Context.MODE_PRIVATE)
    if (!preferences.contains(ManualLatitude) || !preferences.contains(ManualLongitude)) {
        return null
    }
    return SpurCoordinate(
        latitude = Double.fromBits(preferences.getLong(ManualLatitude, 0L)),
        longitude = Double.fromBits(preferences.getLong(ManualLongitude, 0L)),
    )
}

internal fun Context.saveManualLocation(location: SpurCoordinate?) {
    getSharedPreferences(ManualLocationPreferences, Context.MODE_PRIVATE)
        .edit()
        .apply {
            if (location == null) {
                remove(ManualLatitude)
                remove(ManualLongitude)
            } else {
                putLong(ManualLatitude, location.latitude.toBits())
                putLong(ManualLongitude, location.longitude.toBits())
            }
        }
        .apply()
}
