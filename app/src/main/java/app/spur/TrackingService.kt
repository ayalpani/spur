package app.spur

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
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
import java.util.Date
import java.util.Locale

class TrackingService : Service() {
    private lateinit var store: TourStore
    private lateinit var locationClient: FusedLocationProviderClient
    private var tourId: Long? = null
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
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }

        tourId = intent?.getLongExtra(EXTRA_TOUR_ID, -1L)?.takeIf { it > 0 }
            ?: store.activeTour()?.id
        val activeTour = tourId?.let(store::tour)
        if (activeTour == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(activeTour),
            if (Build.VERSION.SDK_INT >= 29) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            },
        )
        requestUpdates()
        return START_STICKY
    }

    private fun requestUpdates() {
        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            stopTracking()
            return
        }
        locationClient.removeLocationUpdates(locationCallback)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .setMinUpdateDistanceMeters(4f)
            .setWaitForAccurateLocation(false)
            .build()
        locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun recordLocation(location: Location) {
        if (applicationContext.loadManualLocation() != null) return
        val id = tourId ?: return
        if (store.appendLocation(id, location)) {
            store.tour(id)?.let {
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, notification(it))
            }
        }
    }

    override fun onDestroy() {
        locationClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopTracking() {
        locationClient.removeLocationUpdates(locationCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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

    companion object {
        const val ACTION_STOP = "app.spur.STOP_TRACKING"
        const val EXTRA_TOUR_ID = "tour_id"
        private const val CHANNEL_ID = "active-tour"
        private const val NOTIFICATION_ID = 41
    }
}
