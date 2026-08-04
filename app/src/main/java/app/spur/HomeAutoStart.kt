package app.spur

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
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
import java.util.ArrayDeque

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
private const val HomeActivityTransitionAction = "app.spur.HOME_ACTIVITY_TRANSITION"
private const val BufferedLocations = "buffered-locations"
private const val DepartureCandidateAt = "departure-candidate-at"
private const val AutomaticTourId = "automatic-tour-id"
private const val OutsideSince = "outside-since"
private const val PreRollIntervalMillis = 20_000L
private const val PreRollBatchDelayMillis = 2 * 60_000L
private const val PreRollWindowMillis = 30 * 60_000L
private const val MaximumBufferedLocations = 500
internal const val HomeRadiusMeters = 100f
internal const val HomeArrivalRadiusMeters = 25f
internal const val MinimumOutsideHomeMillis = 5 * 60_000L
internal const val HomeConfirmationSampleCount = 10
internal const val HomeConfirmationRequiredMatches = 6
internal const val HomeConfirmationMinimumSpanMillis = 20_000L
internal const val DepartureConfirmationTimeoutMillis = 2 * 60_000L
private const val DepartureMaximumAccuracyMeters = 50f
private const val ArrivalMaximumAccuracyMeters = 35f
private const val HomeDepartureBridgeRadiusMeters = 35.0
private val homeDepartureActivityTypes = listOf(
    DetectedActivity.WALKING,
    DetectedActivity.RUNNING,
    DetectedActivity.ON_BICYCLE,
    DetectedActivity.IN_VEHICLE,
)
private val homeAutoStartRuntimeLock = Any()

internal data class BufferedHomeLocation(
    val latitude: Double,
    val longitude: Double,
    val recordedAt: Long,
    val accuracyMeters: Float,
)

internal data class AutomaticTourSignalResult(
    val appended: Boolean,
    val finished: Boolean,
)

internal class AutomaticHomeArrivalTracker {
    private val samples = ArrayDeque<BufferedHomeLocation>()

    fun observe(
        sample: BufferedHomeLocation,
        settings: HomeAutoStartSettings,
        outsideSince: Long,
    ): SpurCoordinate? {
        samples.addLast(sample)
        while (samples.size > HomeConfirmationSampleCount) {
            samples.removeFirst()
        }
        return automaticTourHomePoint(settings)?.takeIf {
            stayedOutsideHomeLongEnough(outsideSince, sample.recordedAt) &&
                confirmedHomeArrival(samples.toList(), settings)
        }
    }
}

internal class AutomaticTourSignalProcessor<T>(
    private val settings: HomeAutoStartSettings,
    private val outsideSince: Long,
    private val appendMeasured: (T) -> Boolean,
    private val finishAtHome: (SpurCoordinate, Long) -> Boolean,
) {
    private val arrivalTracker = AutomaticHomeArrivalTracker()

    fun record(
        sample: BufferedHomeLocation,
        measured: T,
    ): AutomaticTourSignalResult {
        val homeEndpoint = arrivalTracker.observe(sample, settings, outsideSince)
        val appended = appendMeasured(measured)
        val finished = homeEndpoint?.let {
            finishAtHome(it, sample.recordedAt)
        } ?: false
        return AutomaticTourSignalResult(appended = appended, finished = finished)
    }
}

internal fun Location.toBufferedHomeLocation() = BufferedHomeLocation(
    latitude = latitude,
    longitude = longitude,
    recordedAt = time.takeIf { it > 0L } ?: System.currentTimeMillis(),
    accuracyMeters = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
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

internal fun automaticTourHomePoint(settings: HomeAutoStartSettings): SpurCoordinate? =
    settings.startPoint ?: settings.home

internal fun isWithinHomeZone(
    settings: HomeAutoStartSettings,
    coordinate: SpurCoordinate,
): Boolean {
    val home = settings.home ?: return false
    return coordinateDistanceMeters(
        fromLatitude = home.latitude,
        fromLongitude = home.longitude,
        toLatitude = coordinate.latitude,
        toLongitude = coordinate.longitude,
    ) <= HomeRadiusMeters
}

internal fun normalizedHomeCoordinate(
    settings: HomeAutoStartSettings,
    coordinate: SpurCoordinate,
): SpurCoordinate {
    val startPoint = automaticTourHomePoint(settings) ?: return coordinate
    return if (isWithinHomeZone(settings, coordinate)) startPoint else coordinate
}

internal fun Context.hasBackgroundLocationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

internal fun Context.hasActivityRecognitionPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED

internal fun isHomeDepartureActivity(activityType: Int): Boolean =
    activityType in homeDepartureActivityTypes

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
        .setTransitionTypes(
            Geofence.GEOFENCE_TRANSITION_EXIT or Geofence.GEOFENCE_TRANSITION_ENTER,
        )
        .build()
    val request = GeofencingRequest.Builder()
        .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
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
        registerHomeDepartureActivityTransitions()
        geofenceRegistered
    }.getOrDefault(false)
}

internal fun Context.removeHomeAutoStart() {
    removeHomeExitGeofence()
    LocationServices.getFusedLocationProviderClient(this)
        .removeLocationUpdates(homePreRollPendingIntent())
    removeHomeDepartureActivityTransitions()
    homeAutoStartPreferences().edit()
        .remove(BufferedLocations)
        .remove(DepartureCandidateAt)
        .remove(AutomaticTourId)
        .remove(OutsideSince)
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

private fun Context.homeActivityTransitionPendingIntent(): PendingIntent =
    PendingIntent.getBroadcast(
        this,
        2,
        Intent(this, HomeExitReceiver::class.java).setAction(HomeActivityTransitionAction),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

private fun Context.registerHomeDepartureActivityTransitions() {
    if (!hasActivityRecognitionPermission()) return
    val transitions = homeDepartureActivityTypes.map { activityType ->
        ActivityTransition.Builder()
            .setActivityType(activityType)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build()
    }
    try {
        ActivityRecognition.getClient(this).requestActivityTransitionUpdates(
            ActivityTransitionRequest(transitions),
            homeActivityTransitionPendingIntent(),
        )
    } catch (_: SecurityException) {
        // The runtime permission can be revoked between the check and registration.
    }
}

private fun Context.removeHomeDepartureActivityTransitions() {
    if (!hasActivityRecognitionPermission()) return
    try {
        ActivityRecognition.getClient(this)
            .removeActivityTransitionUpdates(homeActivityTransitionPendingIntent())
    } catch (_: SecurityException) {
        // The runtime permission can be revoked between the check and removal.
    }
}

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
    throughAt: Long,
): List<BufferedHomeLocation> {
    val startPoint = automaticTourHomePoint(settings) ?: return emptyList()
    val eligible = locations
        .asSequence()
        .filter { it.recordedAt in (throughAt - PreRollWindowMillis)..throughAt }
        .filter { it.accuracyMeters <= 50f }
        .sortedBy(BufferedHomeLocation::recordedAt)
        .toList()
    val lastNearStart = eligible.indexOfLast {
        coordinateDistanceMeters(
            startPoint.latitude,
            startPoint.longitude,
            it.latitude,
            it.longitude,
        ) <= HomeDepartureBridgeRadiusMeters
    }
    val departure = eligible.drop(lastNearStart.coerceAtLeast(0))
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

internal fun automaticStartLocations(
    startPoint: SpurCoordinate,
    measured: List<BufferedHomeLocation>,
    exitAt: Long,
): List<BufferedHomeLocation> {
    val firstMeasured = measured.firstOrNull() ?: return emptyList()
    val hasMeasuredBridge = coordinateDistanceMeters(
        startPoint.latitude,
        startPoint.longitude,
        firstMeasured.latitude,
        firstMeasured.longitude,
    ) <= HomeDepartureBridgeRadiusMeters
    if (!hasMeasuredBridge) return measured
    val syntheticStart = BufferedHomeLocation(
        latitude = startPoint.latitude,
        longitude = startPoint.longitude,
        recordedAt = minOf(exitAt, firstMeasured.recordedAt - 1L),
        accuracyMeters = 3f,
    )
    return listOf(syntheticStart) + measured
}

private fun confirmationWindow(
    locations: List<BufferedHomeLocation>,
): List<BufferedHomeLocation> {
    val window = locations
        .sortedBy(BufferedHomeLocation::recordedAt)
        .takeLast(HomeConfirmationSampleCount)
    if (window.size < HomeConfirmationSampleCount) return emptyList()
    if (
        window.last().recordedAt - window.first().recordedAt <
        HomeConfirmationMinimumSpanMillis
    ) return emptyList()
    return window
}

internal fun confirmedHomeDeparture(
    locations: List<BufferedHomeLocation>,
    settings: HomeAutoStartSettings,
    candidateAt: Long,
): Boolean {
    val home = settings.home ?: return false
    val window = confirmationWindow(locations.filter { it.recordedAt >= candidateAt })
    if (window.isEmpty()) return false
    fun isReliablyOutside(location: BufferedHomeLocation): Boolean =
        location.accuracyMeters.isFinite() &&
            location.accuracyMeters in 0f..DepartureMaximumAccuracyMeters &&
            coordinateDistanceMeters(
                home.latitude,
                home.longitude,
                location.latitude,
                location.longitude,
            ) - location.accuracyMeters >= HomeRadiusMeters
    return isReliablyOutside(window.last()) &&
        window.count(::isReliablyOutside) >= HomeConfirmationRequiredMatches
}

internal fun confirmedHomeArrival(
    locations: List<BufferedHomeLocation>,
    settings: HomeAutoStartSettings,
): Boolean {
    val homePoint = automaticTourHomePoint(settings) ?: return false
    val window = confirmationWindow(locations)
    if (window.isEmpty()) return false
    fun isReliablyHome(location: BufferedHomeLocation): Boolean =
        location.accuracyMeters.isFinite() &&
            location.accuracyMeters in 0f..ArrivalMaximumAccuracyMeters &&
            coordinateDistanceMeters(
                homePoint.latitude,
                homePoint.longitude,
                location.latitude,
                location.longitude,
            ) <= HomeArrivalRadiusMeters
    return isReliablyHome(window.last()) &&
        window.count(::isReliablyHome) >= HomeConfirmationRequiredMatches
}

internal fun Context.loadBufferedHomeLocations(): List<BufferedHomeLocation> =
    decodeBufferedHomeLocations(
        homeAutoStartPreferences().getString(BufferedLocations, null),
    )

internal fun Context.saveBufferedHomeLocations(
    incoming: List<BufferedHomeLocation>,
): List<BufferedHomeLocation> = synchronized(homeAutoStartRuntimeLock) {
    val preferences = homeAutoStartPreferences()
    mergeBufferedHomeLocations(
        existing = decodeBufferedHomeLocations(preferences.getString(BufferedLocations, null)),
        incoming = incoming,
        now = System.currentTimeMillis(),
    ).also { merged ->
        preferences.edit()
            .putString(BufferedLocations, encodeBufferedHomeLocations(merged))
            .apply()
    }
}

private fun Context.claimDepartureCandidate(candidateAt: Long): Boolean =
    synchronized(homeAutoStartRuntimeLock) {
        val preferences = homeAutoStartPreferences()
        if (preferences.getLong(DepartureCandidateAt, 0L) > 0L) {
            false
        } else {
            preferences.edit().putLong(DepartureCandidateAt, candidateAt).commit()
        }
    }

internal fun Context.markDepartureCandidate(candidateAt: Long) {
    synchronized(homeAutoStartRuntimeLock) {
        homeAutoStartPreferences().edit().putLong(DepartureCandidateAt, candidateAt).apply()
    }
}

internal fun Context.departureCandidateAt(): Long? =
    homeAutoStartPreferences()
        .getLong(DepartureCandidateAt, 0L)
        .takeIf { it > 0L }

internal fun Context.clearDepartureCandidate() {
    synchronized(homeAutoStartRuntimeLock) {
        homeAutoStartPreferences().edit().remove(DepartureCandidateAt).apply()
    }
}

private fun Context.requestHomeDepartureConfirmation(candidateAt: Long) {
    if (!claimDepartureCandidate(candidateAt)) return
    LocationServices.getFusedLocationProviderClient(this).flushLocations()
    runCatching {
        ContextCompat.startForegroundService(
            this,
            Intent(this, TrackingService::class.java)
                .setAction(TrackingService.ACTION_CONFIRM_HOME_DEPARTURE)
                .putExtra(TrackingService.EXTRA_DEPARTURE_CANDIDATE_AT, candidateAt),
        )
    }.onFailure {
        clearDepartureCandidate()
    }
}

internal fun Context.markAutomaticTourOutside(tourId: Long, outsideSince: Long) {
    homeAutoStartPreferences().edit()
        .putLong(AutomaticTourId, tourId)
        .putLong(OutsideSince, outsideSince)
        .apply()
}

private fun Context.markExistingAutomaticTourOutside(tourId: Long, outsideSince: Long) {
    val preferences = homeAutoStartPreferences()
    if (
        preferences.getLong(AutomaticTourId, -1L) == tourId &&
        !preferences.contains(OutsideSince)
    ) {
        preferences.edit().putLong(OutsideSince, outsideSince).apply()
    }
}

internal fun Context.automaticTourOutsideSince(tourId: Long): Long? {
    val preferences = homeAutoStartPreferences()
    if (preferences.getLong(AutomaticTourId, -1L) != tourId) return null
    return preferences.getLong(OutsideSince, 0L).takeIf { it > 0L }
}

internal fun Context.clearAutomaticTourState() {
    homeAutoStartPreferences().edit()
        .remove(AutomaticTourId)
        .remove(OutsideSince)
        .remove(DepartureCandidateAt)
        .apply()
}

internal fun stayedOutsideHomeLongEnough(outsideSince: Long, returnedAt: Long): Boolean =
    returnedAt >= outsideSince && returnedAt - outsideSince >= MinimumOutsideHomeMillis

class HomeExitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.registerHomeAutoStart()
            return
        }
        if (ActivityTransitionResult.hasResult(intent)) {
            ActivityTransitionResult.extractResult(intent)
                ?.transitionEvents
                ?.lastOrNull { event ->
                    event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER &&
                        isHomeDepartureActivity(event.activityType)
                }
                ?: return
            val settings = context.loadHomeAutoStartSettings()
            if (!settings.enabled) return
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    if (TourStore(context).activeTour() == null) {
                        context.requestHomeDepartureConfirmation(System.currentTimeMillis())
                    }
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }
        if (LocationResult.hasResult(intent)) {
            val result = LocationResult.extractResult(intent) ?: return
            val settings = context.loadHomeAutoStartSettings()
            if (!settings.enabled) return
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    context.saveBufferedHomeLocations(
                        result.locations.map(Location::toBufferedHomeLocation),
                    )
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }
        val event = GeofencingEvent.fromIntent(intent) ?: return
        val settings = context.loadHomeAutoStartSettings()
        val isHomeTransition =
            event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT ||
                event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER
        if (
            event.hasError() ||
            !isHomeTransition ||
            !settings.enabled
        ) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val store = TourStore(context)
                val exitLocation = event.triggeringLocation
                val transitionAt = exitLocation?.time?.takeIf { it > 0L }
                    ?: System.currentTimeMillis()
                if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                    if (context.departureCandidateAt() != null && store.activeTour() == null) {
                        context.clearDepartureCandidate()
                        runCatching {
                            context.startService(
                                Intent(context, TrackingService::class.java)
                                    .setAction(TrackingService.ACTION_CANCEL_HOME_DEPARTURE),
                            )
                        }
                    }
                    return@launch
                }

                val activeTour = store.activeTour()
                if (activeTour != null) {
                    context.markExistingAutomaticTourOutside(activeTour.id, transitionAt)
                    return@launch
                }
                context.saveBufferedHomeLocations(
                    listOfNotNull(exitLocation?.toBufferedHomeLocation()),
                )
                context.requestHomeDepartureConfirmation(System.currentTimeMillis())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
