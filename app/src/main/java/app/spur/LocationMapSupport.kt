package app.spur

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.location.LocationComponentConstants
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circlePitchAlignment
import org.maplibre.android.style.layers.PropertyFactory.circlePitchScale
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.visibility
import java.io.File

internal const val SpurLocationPulseLayer = "spur-location-pulse-layer"

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
    markerColors: LocationMarkerColors,
    pulseColor: Color,
) {
    if (!context.hasLocationPermission()) return

    val locationComponent = map.locationComponent
    val options = LocationComponentOptions.builder(context)
        .spurLocationAppearance(markerColors)
        .build()
    locationComponent.activateLocationComponent(
        LocationComponentActivationOptions.builder(context, style)
            .locationComponentOptions(options)
            .useDefaultLocationEngine(false)
            .build(),
    )
    locationComponent.isLocationComponentEnabled = manualLocation == null
    locationComponent.renderMode = RenderMode.NORMAL
    locationComponent.cameraMode = CameraMode.NONE
    style.installSpurLocationPulse(pulseColor)

    val location = map.currentSpurCoordinate(
        context = context,
        manual = manualLocation,
    )
    if (manualLocation == null && location != null) {
        locationComponent.forceLocationUpdate(
            Location("spur-home-normalized").apply {
                latitude = location.latitude
                longitude = location.longitude
            },
        )
    }
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

internal fun MapLibreMap.refreshLocationAppearance(
    colors: LocationMarkerColors,
    pulseColor: Color,
) {
    val component = locationComponent
    if (!component.isLocationComponentActivated) return
    component.applyStyle(
        component.locationComponentOptions
            .toBuilder()
            .spurLocationAppearance(colors)
            .build(),
    )
    style?.installSpurLocationPulse(pulseColor)
}

private fun LocationComponentOptions.Builder.spurLocationAppearance(
    colors: LocationMarkerColors,
): LocationComponentOptions.Builder =
    foregroundTintColor(colors.fill.toArgb())
        .backgroundTintColor(colors.outline.toArgb())
        .foregroundStaleTintColor(colors.fill.toArgb())
        .backgroundStaleTintColor(colors.outline.toArgb())
        .bearingTintColor(colors.fill.toArgb())
        .accuracyAlpha(0f)
        .pulseEnabled(false)

private fun Style.installSpurLocationPulse(color: Color) {
    if (getSource(LocationComponentConstants.LOCATION_SOURCE) == null) return
    val layer = getLayerAs<CircleLayer>(SpurLocationPulseLayer)
    if (layer != null) {
        layer.setProperties(circleColor(color.toArgb()))
        return
    }
    val pulseLayer = CircleLayer(
        SpurLocationPulseLayer,
        LocationComponentConstants.LOCATION_SOURCE,
    ).withProperties(
        circleColor(color.toArgb()),
        circleRadius(0f),
        circleOpacity(0f),
        circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_VIEWPORT),
        circlePitchScale(Property.CIRCLE_PITCH_SCALE_VIEWPORT),
    )
    if (getLayer(LocationComponentConstants.BACKGROUND_LAYER) == null) {
        addLayer(pulseLayer)
    } else {
        addLayerBelow(pulseLayer, LocationComponentConstants.BACKGROUND_LAYER)
    }
}

internal fun Style.showSpurLocationPulse(progress: Float) {
    getLayerAs<CircleLayer>(SpurLocationPulseLayer)?.setProperties(
        circleRadius(locationPulseRadius(progress)),
        circleOpacity(locationPulseOpacity(progress)),
    )
}

internal fun Style.hideSpurLocationPulse() {
    getLayerAs<CircleLayer>(SpurLocationPulseLayer)?.setProperties(
        circleOpacity(0f),
    )
}

private fun Style.setSpurLocationPulseVisible(visible: Boolean) {
    getLayerAs<CircleLayer>(SpurLocationPulseLayer)?.setProperties(
        visibility(if (visible) Property.VISIBLE else Property.NONE),
    )
}

internal fun locationPulseRadius(progress: Float): Float =
    LocationPulseMaxRadius * progress.coerceIn(0f, 1f)

internal fun locationPulseOpacity(progress: Float): Float =
    LocationPulseAlpha * (1f - progress.coerceIn(0f, 1f))

internal fun MapLibreMap.followLocation(
    context: Context,
    manualLocation: SpurCoordinate?,
    transitionDuration: Long,
    targetZoom: Double,
    defaultMapBearing: Double,
) {
    if (manualLocation == null && locationComponent.isLocationComponentActivated) {
        locationComponent.setCameraMode(
            CameraMode.TRACKING,
            transitionDuration,
            targetZoom,
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
            .zoom(targetZoom)
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
        manual = manual,
        gps = gps?.toSpurCoordinate()?.let {
            normalizedHomeCoordinate(context.loadHomeAutoStartSettings(), it)
        },
    )
}

@SuppressLint("MissingPermission")
internal fun MapLibreMap.showGpsLocationPuck(
    context: Context,
    show: Boolean,
) {
    if (!context.hasLocationPermission() || !locationComponent.isLocationComponentActivated) return
    locationComponent.isLocationComponentEnabled = show
    style?.setSpurLocationPulseVisible(show)
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
