package app.spur

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.text.DateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

class TrackingService : Service() {
    private lateinit var store: TourStore
    private lateinit var locationClient: FusedLocationProviderClient
    private var tourId: Long? = null
    private var isDepartureCandidate = false
    private var candidateAt: Long? = null
    private val departureSamples = ArrayDeque<BufferedHomeLocation>()
    private val arrivalSamples = ArrayDeque<BufferedHomeLocation>()
    private val handler = Handler(Looper.getMainLooper())
    private val departureTimeout = Runnable {
        if (isDepartureCandidate) cancelDepartureCandidate()
    }
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::recordLocation)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = TourStore(this)
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Laufende Tour",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                clearDepartureCandidate()
                stopTracking()
                return START_NOT_STICKY
            }

            ACTION_CANCEL_HOME_DEPARTURE -> {
                clearDepartureCandidate()
                if (isDepartureCandidate || store.activeTour() == null) stopTracking()
                return START_NOT_STICKY
            }

            ACTION_CONFIRM_HOME_DEPARTURE -> {
                val requestedAt = intent.getLongExtra(EXTRA_DEPARTURE_CANDIDATE_AT, -1L)
                    .takeIf { it > 0L }
                    ?: departureCandidateAt()
                if (requestedAt == null) {
                    stopTracking()
                    return START_NOT_STICKY
                }
                beginDepartureConfirmation(requestedAt)
                return START_STICKY
            }
        }

        val requestedTourId = intent?.getLongExtra(EXTRA_TOUR_ID, -1L)?.takeIf { it > 0 }
            ?: store.activeTour()?.id
        val activeTour = requestedTourId?.let(store::tour)?.takeIf { it.endedAt == null }
        if (activeTour != null) {
            beginActiveTour(activeTour)
            return START_STICKY
        }

        val restoredCandidateAt = departureCandidateAt()
        if (restoredCandidateAt != null) {
            beginDepartureConfirmation(restoredCandidateAt)
            return START_STICKY
        }
        stopTracking()
        return START_NOT_STICKY
    }

    private fun beginActiveTour(tour: Tour) {
        handler.removeCallbacks(departureTimeout)
        clearDepartureCandidate()
        isDepartureCandidate = false
        candidateAt = null
        departureSamples.clear()
        arrivalSamples.clear()
        tourId = tour.id
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(tour),
            foregroundServiceType(),
        )
        requestUpdates()
    }

    private fun beginDepartureConfirmation(requestedAt: Long) {
        store.activeTour()?.let {
            beginActiveTour(it)
            return
        }
        val settings = loadHomeAutoStartSettings()
        val remaining = requestedAt + DepartureConfirmationTimeoutMillis -
            System.currentTimeMillis()
        if (!settings.enabled || settings.home == null || remaining <= 0L) {
            cancelDepartureCandidate()
            return
        }
        markDepartureCandidate(requestedAt)
        tourId = null
        isDepartureCandidate = true
        candidateAt = requestedAt
        departureSamples.clear()
        loadBufferedHomeLocations()
            .filter { it.recordedAt >= requestedAt }
            .sortedBy(BufferedHomeLocation::recordedAt)
            .forEach(departureSamples::addLast)
        arrivalSamples.clear()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            departureNotification(),
            foregroundServiceType(),
        )
        requestUpdates()
        handler.removeCallbacks(departureTimeout)
        handler.postDelayed(departureTimeout, remaining)
    }

    private fun requestUpdates() {
        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            if (isDepartureCandidate) clearDepartureCandidate()
            stopTracking()
            return
        }
        locationClient.removeLocationUpdates(locationCallback)
        val requestBuilder = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .setWaitForAccurateLocation(false)
        if (isDepartureCandidate) requestBuilder.setMinUpdateDistanceMeters(4f)
        val request = requestBuilder.build()
        locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun recordLocation(location: Location) {
        if (applicationContext.loadManualLocation() != null) return
        val sample = location.toBufferedHomeLocation()
        if (isDepartureCandidate) {
            recordDepartureCandidate(sample)
            return
        }
        val id = tourId ?: return
        val settings = loadHomeAutoStartSettings()
        val storedCoordinate = normalizedHomeCoordinate(
            settings = settings,
            coordinate = SpurCoordinate(location.latitude, location.longitude),
        )
        val storedLocation = if (
            storedCoordinate.latitude == location.latitude &&
            storedCoordinate.longitude == location.longitude
        ) {
            location
        } else {
            Location(location).apply {
                latitude = storedCoordinate.latitude
                longitude = storedCoordinate.longitude
            }
        }
        val appended = store.appendLocation(id, storedLocation)
        if (appended) {
            store.tour(id)?.let {
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, notification(it))
            }
        }
        arrivalSamples.addLast(sample)
        while (arrivalSamples.size > HomeConfirmationSampleCount) {
            arrivalSamples.removeFirst()
        }
        finishAutomaticTourIfHome(id, sample.recordedAt)
    }

    private fun recordDepartureCandidate(sample: BufferedHomeLocation) {
        val startedAt = candidateAt ?: return
        departureSamples.addLast(sample)
        while (departureSamples.size > HomeConfirmationSampleCount) {
            departureSamples.removeFirst()
        }
        saveBufferedHomeLocations(listOf(sample))
        val settings = loadHomeAutoStartSettings()
        if (!confirmedHomeDeparture(departureSamples.toList(), settings, startedAt)) return

        store.activeTour()?.let {
            beginActiveTour(it)
            return
        }
        val homePoint = automaticTourHomePoint(settings) ?: run {
            cancelDepartureCandidate()
            return
        }
        var createdTourId: Long? = null
        runCatching {
            val id = store.startTour(startedAt)
            createdTourId = id
            val measuredDeparture = departureLocations(
                locations = loadBufferedHomeLocations(),
                settings = settings,
                throughAt = sample.recordedAt,
            )
            store.mergeAutomaticStartLocations(
                tourId = id,
                startPoint = homePoint,
                locations = measuredDeparture,
                exitAt = startedAt,
            )
            markAutomaticTourOutside(id, startedAt)
            store.tour(id) ?: error("Created tour is missing")
        }.onSuccess { tour ->
            beginActiveTour(tour)
            applicationContext.vibrateTourStarted()
        }.onFailure {
            createdTourId?.let { store.finishTour(it) }
            clearAutomaticTourState()
            stopTracking()
        }
    }

    private fun finishAutomaticTourIfHome(id: Long, recordedAt: Long) {
        val outsideSince = automaticTourOutsideSince(id) ?: return
        if (!stayedOutsideHomeLongEnough(outsideSince, recordedAt)) return
        val settings = loadHomeAutoStartSettings()
        if (!confirmedHomeArrival(arrivalSamples.toList(), settings)) return
        val homePoint = automaticTourHomePoint(settings) ?: return
        if (!store.finishTourAt(id = id, endPoint = homePoint, now = recordedAt)) return
        applicationContext.markTourCompletionPending(id)
        applicationContext.vibrateTourEnded()
        clearAutomaticTourState()
        stopTracking()
    }

    private fun cancelDepartureCandidate() {
        clearDepartureCandidate()
        isDepartureCandidate = false
        candidateAt = null
        departureSamples.clear()
        handler.removeCallbacks(departureTimeout)
        stopTracking()
    }

    override fun onDestroy() {
        handler.removeCallbacks(departureTimeout)
        locationClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopTracking() {
        locationClient.removeLocationUpdates(locationCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }

    private fun notification(tour: Tour) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_location)
            .setContentTitle("Spur zeichnet deine Tour auf")
            .setContentText(
                "${formatKilometers(tour.distanceMeters)} · seit ${
                    DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
                        .format(Date(tour.startedAt))
                }",
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun departureNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_location)
            .setContentTitle("Spur prüft deinen Tourstart")
            .setContentText("Standort wird kurz bestätigt")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        const val ACTION_STOP = "app.spur.STOP_TRACKING"
        const val ACTION_CONFIRM_HOME_DEPARTURE = "app.spur.CONFIRM_HOME_DEPARTURE"
        const val ACTION_CANCEL_HOME_DEPARTURE = "app.spur.CANCEL_HOME_DEPARTURE"
        const val EXTRA_TOUR_ID = "tour_id"
        const val EXTRA_DEPARTURE_CANDIDATE_AT = "departure_candidate_at"
        private const val CHANNEL_ID = "active-tour"
        private const val NOTIFICATION_ID = 41
    }
}
