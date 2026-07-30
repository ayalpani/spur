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
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.maplibre.geojson.Feature

internal data class HomeAutoStartSettings(
    val enabled: Boolean,
    val home: SpurCoordinate?,
    val homeBuilding: Feature? = null,
    val startPoint: SpurCoordinate? = null,
)

private const val Preferences = "home-auto-start"
private const val RuntimePreferences = "home-auto-start-runtime"
private const val Enabled = "enabled"
private const val Latitude = "latitude"
private const val Longitude = "longitude"
private const val HomeBuilding = "home-building"
private const val StartLatitude = "start-latitude"
private const val StartLongitude = "start-longitude"
private const val HomeGeofenceId = "spur-home"
private const val HomePreRollAction = "app.spur.HOME_PRE_ROLL_LOCATION"
private const val BufferedLocations = "buffered-locations"
private const val PendingTourId = "pending-tour-id"
private const val PendingExitAt = "pending-exit-at"
private const val PreRollIntervalMillis = 20_000L
private const val PreRollBatchDelayMillis = 2 * 60_000L
private const val PreRollWindowMillis = 30 * 60_000L
private const val PendingReconciliationMillis = 24 * 60 * 60_000L
private const val MaximumBufferedLocations = 500
internal const val HomeRadiusMeters = 150f

internal data class BufferedHomeLocation(
    val latitude: Double,
    val longitude: Double,
    val recordedAt: Long,
    val accuracyMeters: Float,
)

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
    val startPoint = if (
        preferences.contains(StartLatitude) &&
        preferences.contains(StartLongitude)
    ) {
        SpurCoordinate(
            latitude = Double.fromBits(preferences.getLong(StartLatitude, 0)),
            longitude = Double.fromBits(preferences.getLong(StartLongitude, 0)),
        )
    } else {
        null
    }
    return HomeAutoStartSettings(
        enabled = preferences.getBoolean(Enabled, false),
        home = home,
        homeBuilding = decodeHomeBuilding(preferences.getString(HomeBuilding, null)),
        startPoint = startPoint,
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
            settings.startPoint?.let {
                putLong(StartLatitude, it.latitude.toBits())
                putLong(StartLongitude, it.longitude.toBits())
            }
        }
        .apply()
}

internal fun encodeHomeBuilding(feature: Feature): String = feature.toJson()

internal fun decodeHomeBuilding(value: String?): Feature? =
    value?.let { runCatching { Feature.fromJson(it) }.getOrNull() }

internal fun automaticTourStartPoint(settings: HomeAutoStartSettings): SpurCoordinate? =
    settings.startPoint ?: settings.home

internal fun normalizedHomeCoordinate(
    settings: HomeAutoStartSettings,
    coordinate: SpurCoordinate,
): SpurCoordinate {
    val home = settings.home ?: return coordinate
    val startPoint = automaticTourStartPoint(settings) ?: return coordinate
    val distanceFromHome = coordinateDistanceMeters(
        fromLatitude = home.latitude,
        fromLongitude = home.longitude,
        toLatitude = coordinate.latitude,
        toLongitude = coordinate.longitude,
    )
    return if (distanceFromHome <= HomeRadiusMeters) startPoint else coordinate
}

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
                saveHomeAutoStartSettings(settings.copy(enabled = false))
            }
        true
    }.getOrDefault(false)
}

internal fun Context.removeHomeExitGeofence() {
    LocationServices.getGeofencingClient(this)
        .removeGeofences(homeGeofencePendingIntent())
}

internal fun Context.registerHomeAutoStart(): Boolean {
    val geofenceRegistered = registerHomeExitGeofence()
    val settings = loadHomeAutoStartSettings()
    if (
        !settings.enabled ||
        settings.home == null ||
        !hasBackgroundLocationPermission() ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    val request = LocationRequest.Builder(
        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        PreRollIntervalMillis,
    )
        .setMinUpdateDistanceMeters(5f)
        .setMaxUpdateDelayMillis(PreRollBatchDelayMillis)
        .build()
    return runCatching {
        LocationServices.getFusedLocationProviderClient(this)
            .requestLocationUpdates(request, homePreRollPendingIntent())
        geofenceRegistered
    }.getOrDefault(false)
}

internal fun Context.removeHomeAutoStart() {
    removeHomeExitGeofence()
    LocationServices.getFusedLocationProviderClient(this)
        .removeLocationUpdates(homePreRollPendingIntent())
    homeAutoStartPreferences().edit()
        .remove(BufferedLocations)
        .remove(PendingTourId)
        .remove(PendingExitAt)
        .apply()
}

private fun Context.homeGeofencePendingIntent(): PendingIntent =
    PendingIntent.getBroadcast(
        this,
        0,
        Intent(this, HomeExitReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

private fun Context.homePreRollPendingIntent(): PendingIntent =
    PendingIntent.getBroadcast(
        this,
        1,
        Intent(this, HomeExitReceiver::class.java).setAction(HomePreRollAction),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

private fun Context.homeAutoStartPreferences() =
    getSharedPreferences(RuntimePreferences, Context.MODE_PRIVATE)

internal fun encodeBufferedHomeLocations(locations: List<BufferedHomeLocation>): String =
    locations.joinToString("\n") {
        "${it.latitude.toBits()},${it.longitude.toBits()},${it.recordedAt}," +
            it.accuracyMeters.toBits()
    }

internal fun decodeBufferedHomeLocations(value: String?): List<BufferedHomeLocation> =
    value.orEmpty().lineSequence().mapNotNull { line ->
        val parts = line.split(',')
        if (parts.size != 4) return@mapNotNull null
        runCatching {
            BufferedHomeLocation(
                latitude = Double.fromBits(parts[0].toLong()),
                longitude = Double.fromBits(parts[1].toLong()),
                recordedAt = parts[2].toLong(),
                accuracyMeters = Float.fromBits(parts[3].toInt()),
            )
        }.getOrNull()
    }.toList()

internal fun mergeBufferedHomeLocations(
    existing: List<BufferedHomeLocation>,
    incoming: List<BufferedHomeLocation>,
    now: Long,
): List<BufferedHomeLocation> {
    val plausible = (existing + incoming)
        .filter { it.recordedAt <= now + 5 * 60_000L }
    val newestMeasurementAt = plausible.maxOfOrNull(BufferedHomeLocation::recordedAt)
        ?: return emptyList()
    return plausible
        .filter { it.recordedAt >= newestMeasurementAt - PreRollWindowMillis }
        .distinctBy { Triple(it.recordedAt, it.latitude, it.longitude) }
        .sortedBy(BufferedHomeLocation::recordedAt)
        .takeLast(MaximumBufferedLocations)
}

internal fun departureLocations(
    locations: List<BufferedHomeLocation>,
    settings: HomeAutoStartSettings,
    exitAt: Long,
): List<BufferedHomeLocation> {
    val home = settings.home ?: return emptyList()
    val eligible = locations
        .asSequence()
        .filter { it.recordedAt in (exitAt - PreRollWindowMillis)..exitAt }
        .filter { it.accuracyMeters <= 50f }
        .sortedBy(BufferedHomeLocation::recordedAt)
        .toList()
    val lastNearHome = eligible.indexOfLast {
        coordinateDistanceMeters(
            home.latitude,
            home.longitude,
            it.latitude,
            it.longitude,
        ) <= 35.0
    }
    val departure = eligible.drop((lastNearHome + 1).coerceAtLeast(0))
    return departure.fold(emptyList()) { accepted, point ->
        val previous = accepted.lastOrNull()
        if (
            previous == null ||
            coordinateDistanceMeters(
                previous.latitude,
                previous.longitude,
                point.latitude,
                point.longitude,
            ) >= 4.0
        ) {
            accepted + point
        } else {
            accepted
        }
    }
}

private fun Context.saveBufferedLocations(incoming: List<BufferedHomeLocation>): List<BufferedHomeLocation> {
    val preferences = homeAutoStartPreferences()
    val merged = mergeBufferedHomeLocations(
        existing = decodeBufferedHomeLocations(preferences.getString(BufferedLocations, null)),
        incoming = incoming,
        now = System.currentTimeMillis(),
    )
    preferences.edit().putString(BufferedLocations, encodeBufferedHomeLocations(merged)).apply()
    return merged
}

private fun Context.markPendingReconciliation(tourId: Long, exitAt: Long) {
    homeAutoStartPreferences().edit()
        .putLong(PendingTourId, tourId)
        .putLong(PendingExitAt, exitAt)
        .apply()
}

private fun Context.reconcilePendingDeparture(
    store: TourStore,
    settings: HomeAutoStartSettings,
    buffered: List<BufferedHomeLocation>,
) {
    val preferences = homeAutoStartPreferences()
    val tourId = preferences.getLong(PendingTourId, -1L)
    val exitAt = preferences.getLong(PendingExitAt, 0L)
    if (tourId <= 0L || exitAt <= 0L) return
    if (
        System.currentTimeMillis() - exitAt > PendingReconciliationMillis ||
        store.tour(tourId) == null
    ) {
        preferences.edit().remove(PendingTourId).remove(PendingExitAt).apply()
        return
    }
    val startPoint = automaticTourStartPoint(settings) ?: return
    store.mergeAutomaticStartLocations(
        tourId = tourId,
        startPoint = startPoint,
        locations = departureLocations(buffered, settings, exitAt),
        exitAt = exitAt,
    )
}

class HomeExitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.registerHomeAutoStart()
            return
        }
        if (LocationResult.hasResult(intent)) {
            val result = LocationResult.extractResult(intent) ?: return
            val settings = context.loadHomeAutoStartSettings()
            if (!settings.enabled) return
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val buffered = context.saveBufferedLocations(
                        result.locations.map {
                            BufferedHomeLocation(
                                latitude = it.latitude,
                                longitude = it.longitude,
                                recordedAt = it.time,
                                accuracyMeters = if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE,
                            )
                        },
                    )
                    context.reconcilePendingDeparture(
                        store = TourStore(context),
                        settings = settings,
                        buffered = buffered,
                    )
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }
        val event = GeofencingEvent.fromIntent(intent) ?: return
        val settings = context.loadHomeAutoStartSettings()
        if (
            event.hasError() ||
            event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_EXIT ||
            !settings.enabled
        ) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val store = TourStore(context)
                val exitLocation = event.triggeringLocation
                val exitAt = exitLocation?.time?.takeIf { it > 0L }
                    ?: System.currentTimeMillis()
                val start = store.activeTourOrStart(exitAt)
                if (!start.created) return@launch
                val tourId = start.id
                context.markPendingReconciliation(tourId, exitAt)
                val buffered = context.saveBufferedLocations(
                    listOfNotNull(
                        exitLocation?.let {
                            BufferedHomeLocation(
                                latitude = it.latitude,
                                longitude = it.longitude,
                                recordedAt = it.time,
                                accuracyMeters = if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE,
                            )
                        },
                    ),
                )
                context.reconcilePendingDeparture(store, settings, buffered)
                LocationServices.getFusedLocationProviderClient(context).flushLocations()
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
