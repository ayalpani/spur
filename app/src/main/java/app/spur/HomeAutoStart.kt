package app.spur

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.geojson.Feature
import kotlin.coroutines.resume

internal data class HomeAutoStartSettings(
    val enabled: Boolean,
    val home: SpurCoordinate?,
    val homeBuilding: Feature? = null,
)

private const val Preferences = "home-auto-start"
private const val Enabled = "enabled"
private const val Latitude = "latitude"
private const val Longitude = "longitude"
private const val HomeBuilding = "home-building"
private const val HomeGeofenceId = "spur-home"
private const val HomeRadiusMeters = 150f

internal fun Context.loadHomeAutoStartSettings(): HomeAutoStartSettings {
    val preferences = getSharedPreferences(Preferences, Context.MODE_PRIVATE)
    val home = if (preferences.contains(Latitude) && preferences.contains(Longitude)) {
        SpurCoordinate(
            latitude = Double.fromBits(preferences.getLong(Latitude, 0)),
            longitude = Double.fromBits(preferences.getLong(Longitude, 0)),
        )
    } else {
        null
    }
    return HomeAutoStartSettings(
        enabled = preferences.getBoolean(Enabled, false),
        home = home,
        homeBuilding = decodeHomeBuilding(preferences.getString(HomeBuilding, null)),
    )
}

internal fun Context.saveHomeAutoStartSettings(settings: HomeAutoStartSettings) {
    getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        .edit()
        .clear()
        .apply {
            putBoolean(Enabled, settings.enabled)
            settings.home?.let {
                putLong(Latitude, it.latitude.toBits())
                putLong(Longitude, it.longitude.toBits())
            }
            settings.homeBuilding?.let { putString(HomeBuilding, encodeHomeBuilding(it)) }
        }
        .apply()
}

internal fun encodeHomeBuilding(feature: Feature): String = feature.toJson()

internal fun decodeHomeBuilding(value: String?): Feature? =
    value?.let { runCatching { Feature.fromJson(it) }.getOrNull() }

internal fun Context.hasBackgroundLocationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

internal fun Context.registerHomeExitGeofence(): Boolean {
    val settings = loadHomeAutoStartSettings()
    val home = settings.home ?: return false
    if (!settings.enabled || !hasBackgroundLocationPermission()) return false
    if (
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
        PackageManager.PERMISSION_GRANTED
    ) return false

    val geofence = Geofence.Builder()
        .setRequestId(HomeGeofenceId)
        .setCircularRegion(home.latitude, home.longitude, HomeRadiusMeters)
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
        .build()
    val request = GeofencingRequest.Builder()
        .setInitialTrigger(0)
        .addGeofence(geofence)
        .build()
    return runCatching {
        LocationServices.getGeofencingClient(this)
            .addGeofences(request, homeGeofencePendingIntent())
            .addOnFailureListener {
                saveHomeAutoStartSettings(
                    HomeAutoStartSettings(enabled = false, home = null),
                )
            }
        true
    }.getOrDefault(false)
}

internal fun Context.removeHomeExitGeofence() {
    LocationServices.getGeofencingClient(this)
        .removeGeofences(homeGeofencePendingIntent())
}

private fun Context.homeGeofencePendingIntent(): PendingIntent =
    PendingIntent.getBroadcast(
        this,
        0,
        Intent(this, HomeExitReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

internal suspend fun Context.currentSpurLocation(): SpurCoordinate? {
    loadManualLocation()?.let { return it }
    if (
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
        PackageManager.PERMISSION_GRANTED
    ) return null
    return suspendCancellableCoroutine { continuation ->
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                continuation.resume(
                    location?.let { SpurCoordinate(it.latitude, it.longitude) },
                )
            }
            .addOnFailureListener { continuation.resume(null) }
    }
}

class HomeExitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.registerHomeExitGeofence()
            return
        }
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (
            event.hasError() ||
            event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_EXIT ||
            !context.loadHomeAutoStartSettings().enabled
        ) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val store = TourStore(context)
                if (store.activeTour() != null) return@launch
                val tourId = store.startTour()
                runCatching {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, TrackingService::class.java)
                            .putExtra(TrackingService.EXTRA_TOUR_ID, tourId),
                    )
                }.onFailure {
                    store.finishTour(tourId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
