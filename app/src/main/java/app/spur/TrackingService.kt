package app.spur

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
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
    private lateinit var sensorManager: SensorManager
    private var tourId: Long? = null
    private var mode = HomeDepartureTrackingMode.STOPPED
    private var candidateAt: Long? = null
    private val departureSamples = ArrayDeque<BufferedHomeLocation>()
    private var significantMotionSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    private var significantMotionArmed = false
    private var stepDetectorArmed = false
    private var automaticTourSignalProcessor: AutomaticTourSignalProcessor<Location>? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var trackingThread: HandlerThread
    private lateinit var trackingHandler: Handler
    @Volatile
    private var trackingSessionId = 0L
    @Volatile
    private var activeTrackingSession: ActiveTrackingSession? = null
    private val departureTimeout = Runnable {
        if (mode == HomeDepartureTrackingMode.CONFIRMING) finishFalseDepartureCandidate()
    }
    private val significantMotionListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            significantMotionArmed = false
            beginMotionTriggeredDeparture(HomeDepartureTriggerSource.SIGNIFICANT_MOTION)
        }
    }
    private val stepDetectorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            beginMotionTriggeredDeparture(HomeDepartureTriggerSource.STEP_DETECTOR)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (mode == HomeDepartureTrackingMode.CONFIRMING) {
                result.locations
                    .map(::Location)
                    .map(Location::toBufferedHomeLocation)
                    .forEach(::recordDepartureCandidate)
                return
            }
            val session = activeTrackingSession ?: return
            result.locations.map(::Location).forEach { location ->
                trackingHandler.post { persistLocation(session, location) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        trackingThread = HandlerThread("spur-tracking").also(HandlerThread::start)
        trackingHandler = Handler(trackingThread.looper)
        store = TourStore(this)
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SensorManager::class.java)
        significantMotionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
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
                clearAutomaticTourState()
                return if (loadHomeAutoStartSettings().enabled) {
                    beginArmedWaiting()
                    START_STICKY
                } else {
                    stopTracking()
                    START_NOT_STICKY
                }
            }

            ACTION_DISARM_HOME_DEPARTURE -> {
                store.activeTourNotificationSummary()?.let {
                    beginActiveTour(it)
                    return START_STICKY
                }
                stopTracking()
                return START_NOT_STICKY
            }

            ACTION_CANCEL_HOME_DEPARTURE -> {
                store.activeTourNotificationSummary()?.let {
                    beginActiveTour(it)
                    return START_STICKY
                }
                finishFalseDepartureCandidate()
                return if (mode == HomeDepartureTrackingMode.ARMED) {
                    START_STICKY
                } else {
                    START_NOT_STICKY
                }
            }

            ACTION_CONFIRM_HOME_DEPARTURE -> {
                val requestedAt = intent.getLongExtra(EXTRA_DEPARTURE_CANDIDATE_AT, -1L)
                    .takeIf { it > 0L }
                    ?: departureCandidateAt()
                if (requestedAt == null) {
                    return restoreTracking()
                }
                beginDepartureConfirmation(requestedAt)
                return START_STICKY
            }
        }

        val requestedTourId = intent?.getLongExtra(EXTRA_TOUR_ID, -1L)?.takeIf { it > 0 }
            ?: store.activeTourNotificationSummary()?.id
        val activeTour = requestedTourId
            ?.let(store::tourNotificationSummary)
            ?.takeIf { store.activeTourNotificationSummary()?.id == it.id }
        if (activeTour != null) {
            beginActiveTour(activeTour)
            return START_STICKY
        }
        return restoreTracking()
    }

    private fun restoreTracking(): Int {
        val activeTour = store.activeTourNotificationSummary()
        val settings = loadHomeAutoStartSettings()
        val restoredCandidateAt = departureCandidateAt()
        return when (
            restoredHomeDepartureMode(
                hasActiveTour = activeTour != null,
                automationEnabled = settings.enabled,
                candidateAt = restoredCandidateAt,
                now = System.currentTimeMillis(),
            )
        ) {
            HomeDepartureTrackingMode.ACTIVE -> {
                beginActiveTour(requireNotNull(activeTour))
                START_STICKY
            }

            HomeDepartureTrackingMode.CONFIRMING -> {
                beginDepartureConfirmation(requireNotNull(restoredCandidateAt))
                START_STICKY
            }

            HomeDepartureTrackingMode.ARMED -> {
                beginArmedWaiting()
                START_STICKY
            }

            HomeDepartureTrackingMode.STOPPED -> {
                stopTracking()
                START_NOT_STICKY
            }
        }
    }

    private fun beginActiveTour(tour: TourNotificationSummary) {
        handler.removeCallbacks(departureTimeout)
        clearDepartureCandidate()
        disarmMotionSensors()
        mode = HomeDepartureTrackingMode.ACTIVE
        candidateAt = null
        departureSamples.clear()
        tourId = tour.id
        if (!enterForeground(notification(tour), "fgs_active")) return
        reconcileAutomaticDeparture(store, tour.id)
        automaticTourSignalProcessor = automaticTourOutsideSince(tour.id)?.let { outsideSince ->
            AutomaticTourSignalProcessor(
                settings = loadHomeAutoStartSettings(),
                outsideSince = outsideSince,
                appendMeasured = { location -> store.appendLocation(tour.id, location) },
                finishAtHome = { homeEndpoint, recordedAt ->
                    store.finishTourAt(
                        id = tour.id,
                        endPoint = homeEndpoint,
                        now = recordedAt,
                    )
                },
            )
        }
        val session = ActiveTrackingSession(
            id = nextTrackingSessionId(),
            tourId = tour.id,
            notificationText = notificationText(tour),
            automaticProcessor = automaticTourSignalProcessor,
        )
        activeTrackingSession = session
        requestHighAccuracyUpdates()
    }

    private fun beginArmedWaiting() {
        val settings = loadHomeAutoStartSettings()
        if (!settings.enabled || settings.home == null || !hasBackgroundLocationPermission()) {
            stopTracking()
            return
        }
        handler.removeCallbacks(departureTimeout)
        clearDepartureCandidate()
        mode = HomeDepartureTrackingMode.ARMED
        candidateAt = null
        departureSamples.clear()
        tourId = null
        automaticTourSignalProcessor = null
        invalidateActiveTrackingSession()
        locationClient.removeLocationUpdates(locationCallback)
        if (!enterForeground(armedNotification(), "fgs_armed")) return
        armMotionSensors()
    }

    private fun armMotionSensors() {
        disarmMotionSensors()
        significantMotionSensor?.let { sensor ->
            significantMotionArmed = runCatching {
                sensorManager.requestTriggerSensor(significantMotionListener, sensor)
            }.getOrDefault(false)
        }
        if (hasActivityRecognitionPermission()) {
            stepDetectorSensor?.let { sensor ->
                stepDetectorArmed = runCatching {
                    sensorManager.registerListener(
                        stepDetectorListener,
                        sensor,
                        SensorManager.SENSOR_DELAY_NORMAL,
                    )
                }.getOrDefault(false)
            }
        }
    }

    private fun disarmMotionSensors() {
        if (significantMotionArmed) {
            sensorManager.cancelTriggerSensor(
                significantMotionListener,
                significantMotionSensor,
            )
        }
        significantMotionArmed = false
        if (stepDetectorArmed) sensorManager.unregisterListener(stepDetectorListener)
        stepDetectorArmed = false
    }

    private fun beginMotionTriggeredDeparture(source: HomeDepartureTriggerSource) {
        if (mode != HomeDepartureTrackingMode.ARMED) return
        val triggeredAt = System.currentTimeMillis()
        if (!claimDepartureCandidate(triggeredAt)) return
        mode = mode.afterMotion()
        recordHomeDepartureTrigger(source, triggeredAt)
        beginDepartureConfirmation(triggeredAt)
    }

    private fun beginDepartureConfirmation(requestedAt: Long) {
        store.activeTourNotificationSummary()?.let {
            beginActiveTour(it)
            return
        }
        val settings = loadHomeAutoStartSettings()
        val remaining = requestedAt + DepartureConfirmationTimeoutMillis -
            System.currentTimeMillis()
        if (!settings.enabled || settings.home == null || remaining <= 0L) {
            finishFalseDepartureCandidate()
            return
        }
        disarmMotionSensors()
        markDepartureCandidate(requestedAt)
        tourId = null
        mode = HomeDepartureTrackingMode.CONFIRMING
        candidateAt = requestedAt
        departureSamples.clear()
        invalidateActiveTrackingSession()
        if (!enterForeground(departureNotification(), "fgs_confirmation")) return
        orderedConfirmationSamples(
            existing = emptyList(),
            incoming = loadBufferedHomeLocations().filter { it.recordedAt >= requestedAt },
        )
            .forEach(departureSamples::addLast)
        automaticTourSignalProcessor = null
        requestHighAccuracyUpdates()
        locationClient.flushLocations().addOnFailureListener {
            recordHomeDepartureRuntimeFailure("pre_roll_flush", it)
        }
        handler.removeCallbacks(departureTimeout)
        handler.postDelayed(departureTimeout, remaining)
    }

    private fun requestHighAccuracyUpdates() {
        check(
            locationCaptureFor(mode) == HomeDepartureLocationCapture.HIGH_ACCURACY,
        )
        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            if (mode == HomeDepartureTrackingMode.CONFIRMING) clearDepartureCandidate()
            stopTracking()
            return
        }
        locationClient.removeLocationUpdates(locationCallback)
        val requestBuilder = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .setWaitForAccurateLocation(false)
        if (mode == HomeDepartureTrackingMode.CONFIRMING) {
            requestBuilder.setMinUpdateDistanceMeters(4f)
        }
        val request = requestBuilder.build()
        runCatching {
            locationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper(),
            )
        }.onSuccess { task ->
            task.addOnFailureListener(::handleLocationRequestFailure)
        }.onFailure(::handleLocationRequestFailure)
    }

    private fun handleLocationRequestFailure(error: Throwable) {
        recordHomeDepartureRuntimeFailure("high_accuracy", error)
        if (mode == HomeDepartureTrackingMode.CONFIRMING) {
            finishFalseDepartureCandidate()
        } else {
            stopTracking()
        }
    }

    private fun persistLocation(
        session: ActiveTrackingSession,
        location: Location,
    ) {
        if (!isCurrent(session)) return
        if (applicationContext.loadManualLocation() != null) return
        val sample = location.toBufferedHomeLocation()
        val automaticResult = session.automaticProcessor?.record(
            sample = sample,
            measured = location,
        )
        val appended = automaticResult?.appended ?: store.appendLocation(session.tourId, location)
        if (appended) {
            store.tourNotificationSummary(session.tourId)?.let { summary ->
                val text = notificationText(summary)
                if (text != session.notificationText) {
                    session.notificationText = text
                    handler.post {
                        if (isCurrent(session)) {
                            getSystemService(NotificationManager::class.java)
                                .notify(NOTIFICATION_ID, notification(summary))
                        }
                    }
                }
            }
        }
        if (automaticResult?.finished == true) {
            handler.post {
                finishAutomaticTourAtHome(session.tourId)
            }
        }
    }

    private fun recordDepartureCandidate(sample: BufferedHomeLocation) {
        val startedAt = candidateAt ?: return
        val ordered = orderedConfirmationSamples(departureSamples, listOf(sample))
        departureSamples.clear()
        ordered.forEach(departureSamples::addLast)
        saveBufferedHomeLocations(listOf(sample))
        if (sample.recordedAt >= startedAt) recordHomeDepartureFirstFix(startedAt, sample)
        val settings = loadHomeAutoStartSettings()
        if (!confirmedHomeDeparture(departureSamples.toList(), settings, startedAt)) return

        store.activeTourNotificationSummary()?.let {
            beginActiveTour(it)
            return
        }
        automaticTourHomePoint(settings) ?: run {
            finishFalseDepartureCandidate()
            return
        }
        var createdTourId: Long? = null
        runCatching {
            val id = store.startTour(startedAt)
            createdTourId = id
            markAutomaticTourOutside(
                tourId = id,
                outsideSince = startedAt,
                departureThroughAt = sample.recordedAt,
            )
            reconcileAutomaticDeparture(store, id)
            store.tourNotificationSummary(id) ?: error("Created tour is missing")
        }.onSuccess { tour ->
            beginActiveTour(tour)
            applicationContext.vibrateTourStarted()
        }.onFailure {
            createdTourId?.let { store.finishTour(it) }
            clearAutomaticTourState()
            recordHomeDepartureRuntimeFailure("tour_start", it)
            finishFalseDepartureCandidate()
        }
    }

    private fun finishAutomaticTourAtHome(id: Long) {
        applicationContext.markTourCompletionPending(id)
        applicationContext.vibrateTourEnded()
        if (!shouldTransitionAfterAutomaticCompletion(id, activeTrackingSession?.tourId)) return
        clearAutomaticTourState()
        returnToArmedWaitingOrStop()
    }

    private fun finishFalseDepartureCandidate() {
        clearDepartureCandidate()
        candidateAt = null
        departureSamples.clear()
        handler.removeCallbacks(departureTimeout)
        returnToArmedWaitingOrStop()
    }

    private fun returnToArmedWaitingOrStop() {
        mode = idleHomeDepartureMode(loadHomeAutoStartSettings().enabled)
        if (mode == HomeDepartureTrackingMode.ARMED) {
            beginArmedWaiting()
        } else {
            stopTracking()
        }
    }

    override fun onDestroy() {
        invalidateActiveTrackingSession()
        handler.removeCallbacks(departureTimeout)
        disarmMotionSensors()
        locationClient.removeLocationUpdates(locationCallback)
        trackingHandler.removeCallbacksAndMessages(null)
        trackingThread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    private fun stopTracking() {
        mode = HomeDepartureTrackingMode.STOPPED
        automaticTourSignalProcessor = null
        invalidateActiveTrackingSession()
        handler.removeCallbacks(departureTimeout)
        disarmMotionSensors()
        locationClient.removeLocationUpdates(locationCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun foregroundServiceType(): Int = if (Build.VERSION.SDK_INT >= 29) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
    } else {
        0
    }

    private fun enterForeground(
        notification: Notification,
        operation: String,
    ): Boolean = runCatching {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            foregroundServiceType(),
        )
        true
    }.onFailure {
        recordHomeDepartureRuntimeFailure(operation, it)
        stopTracking()
    }.getOrDefault(false)

    private fun notification(tour: TourNotificationSummary) = serviceNotification(
        title = "Spur zeichnet deine Tour auf",
        text = notificationText(tour),
    )

    private fun notificationText(tour: TourNotificationSummary) =
        "${formatKilometers(tour.distanceMeters)} · seit ${
            DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
                .format(Date(tour.startedAt))
        }"

    @Synchronized
    private fun nextTrackingSessionId(): Long = ++trackingSessionId

    private fun invalidateActiveTrackingSession() {
        nextTrackingSessionId()
        activeTrackingSession = null
    }

    private fun isCurrent(session: ActiveTrackingSession): Boolean =
        activeTrackingSession === session && trackingSessionId == session.id

    private fun departureNotification() =
        serviceNotification("Spur prüft deinen Tourstart", "Standort wird kurz bestätigt")

    private fun armedNotification() =
        serviceNotification("Spur wartet auf deinen Start", "Startautomatik ist bereit")

    private fun serviceNotification(title: String, text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_location)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        const val ACTION_STOP = "app.spur.STOP_TRACKING"
        const val ACTION_CONFIRM_HOME_DEPARTURE = "app.spur.CONFIRM_HOME_DEPARTURE"
        const val ACTION_CANCEL_HOME_DEPARTURE = "app.spur.CANCEL_HOME_DEPARTURE"
        const val ACTION_ARM_HOME_DEPARTURE = "app.spur.ARM_HOME_DEPARTURE"
        const val ACTION_DISARM_HOME_DEPARTURE = "app.spur.DISARM_HOME_DEPARTURE"
        const val EXTRA_TOUR_ID = "tour_id"
        const val EXTRA_DEPARTURE_CANDIDATE_AT = "departure_candidate_at"
        private const val CHANNEL_ID = "active-tour"
        private const val NOTIFICATION_ID = 41
    }
}

private data class ActiveTrackingSession(
    val id: Long,
    val tourId: Long,
    var notificationText: String,
    val automaticProcessor: AutomaticTourSignalProcessor<Location>?,
)

internal fun shouldTransitionAfterAutomaticCompletion(
    completedTourId: Long,
    activeTourId: Long?,
): Boolean = completedTourId == activeTourId
