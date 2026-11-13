package app.opaw.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.opaw.MainActivity
import app.opaw.R
import app.opaw.core.CareKind
import app.opaw.core.GeoPoint
import app.opaw.core.OpawFormat
import app.opaw.core.WalkCalculator
import app.opaw.core.WalkStats
import app.opaw.data.WalkPin
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.time.Duration
import java.time.Instant

/**
 * Records a walk with GPS and keeps stats up to date. Mirrors WalkRecorder.swift. The ongoing
 * notification from [WalkService] stands in for the Live Activity.
 */
object WalkRecorder {
    enum class State { IDLE, RECORDING, PAUSED }

    data class FinishedWalk(val startedAt: Instant, val endedAt: Instant, val points: List<GeoPoint>, val pins: List<WalkPin>)

    var state by mutableStateOf(State.IDLE)
        private set
    var points by mutableStateOf<List<GeoPoint>>(emptyList())
        private set
    var pins by mutableStateOf<List<WalkPin>>(emptyList())
        private set
    var stats by mutableStateOf(WalkStats.ZERO)
        private set
    var startedAt by mutableStateOf<Instant?>(null)
        private set
    var lastLocation by mutableStateOf<GeoPoint?>(null)
        private set
    var dogName by mutableStateOf("")
        private set

    private var pausedTotal: Duration = Duration.ZERO
    private var pauseStartedAt: Instant? = null
    private var appContext: Context? = null
    private var warmCallback: LocationCallback? = null

    fun isAuthorized(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Elapsed time excluding pauses. */
    fun elapsed(now: Instant = Instant.now()): Double {
        val start = startedAt ?: return 0.0
        val currentPause = pauseStartedAt?.let { Duration.between(it, now) } ?: Duration.ZERO
        return maxOf(0.0, (Duration.between(start, now) - pausedTotal - currentPause).toMillis() / 1000.0)
    }

    /** Starts location updates without recording, to center the map before a walk. */
    @SuppressLint("MissingPermission")
    fun warmUp(context: Context) {
        if (!isAuthorized(context) || warmCallback != null || state != State.IDLE) return
        val client = LocationServices.getFusedLocationProviderClient(context)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { lastLocation = GeoPoint(it.latitude, it.longitude, Instant.ofEpochMilli(it.time), it.accuracy.toDouble()) }
            }
        }
        warmCallback = callback
        client.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build(), callback, Looper.getMainLooper())
    }

    fun coolDown(context: Context) {
        warmCallback?.let { LocationServices.getFusedLocationProviderClient(context).removeLocationUpdates(it) }
        warmCallback = null
    }

    fun start(context: Context, dogName: String) {
        if (state != State.IDLE) return
        coolDown(context)
        appContext = context.applicationContext
        this.dogName = dogName
        points = emptyList()
        pins = emptyList()
        stats = WalkStats.ZERO
        pausedTotal = Duration.ZERO
        pauseStartedAt = null
        startedAt = Instant.now()
        state = State.RECORDING
        ContextCompat.startForegroundService(context, Intent(context, WalkService::class.java))
    }

    fun pause() {
        if (state != State.RECORDING) return
        state = State.PAUSED
        pauseStartedAt = Instant.now()
        WalkService.update()
    }

    fun resume() {
        if (state != State.PAUSED) return
        pauseStartedAt?.let { pausedTotal += Duration.between(it, Instant.now()) }
        pauseStartedAt = null
        state = State.RECORDING
        WalkService.update()
    }

    /** Stops recording and returns everything needed to save the walk. */
    fun finish(): FinishedWalk? {
        val start = startedAt
        if (state == State.IDLE || start == null) return null
        if (state == State.PAUSED) resume()
        val result = FinishedWalk(start, Instant.now(), points, pins)
        state = State.IDLE
        startedAt = null
        appContext?.let { it.stopService(Intent(it, WalkService::class.java)) }
        return result
    }

    /** Discards the current walk. */
    fun cancel() {
        finish()
        points = emptyList()
        pins = emptyList()
        stats = WalkStats.ZERO
    }

    /** Drops a pee or poop pin at the current location. */
    fun dropPin(kind: CareKind) {
        val location = lastLocation ?: return
        pins = pins + WalkPin(kind = kind.raw, latitude = location.latitude, longitude = location.longitude, date = Instant.now())
        WalkService.update()
    }

    internal fun handle(locations: List<GeoPoint>) {
        val latest = locations.lastOrNull() ?: return
        lastLocation = latest
        if (state != State.RECORDING) return
        points = points + locations
        stats = WalkCalculator.stats(points)
        WalkService.update(force = false)
    }
}

/** Keeps GPS running with the screen off and shows the walk in an ongoing notification. */
class WalkService : Service() {
    private var callback: LocationCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        instance = this
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Walk in progress", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Distance and time while a walk records"
            setShowBadge(false)
        })
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, ID, notification(), type)
        if (!WalkRecorder.isAuthorized(this)) { stopSelf(); return }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateDistanceMeters(5f)
            .setWaitForAccurateLocation(false)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                WalkRecorder.handle(result.locations.map { GeoPoint(it.latitude, it.longitude, Instant.ofEpochMilli(it.time), it.accuracy.toDouble()) })
            }
        }
        callback = cb
        LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(request, cb, Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> WalkRecorder.pause()
            ACTION_RESUME -> WalkRecorder.resume()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        callback?.let { LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(it) }
        callback = null
        instance = null
        super.onDestroy()
    }

    private fun notification(): Notification {
        val paused = WalkRecorder.state == WalkRecorder.State.PAUSED
        val pee = WalkRecorder.pins.count { it.kind == CareKind.PEE.raw }
        val poop = WalkRecorder.pins.count { it.kind == CareKind.POOP.raw }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val toggle = PendingIntent.getService(
            this, 1, Intent(this, WalkService::class.java).setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toilet = listOfNotNull(pee.takeIf { it > 0 }?.let { "$it pee" }, poop.takeIf { it > 0 }?.let { "$it poop" }).joinToString(" · ")
        val distance = OpawFormat.distance(WalkRecorder.stats.distanceMeters)
        val name = WalkRecorder.dogName.ifEmpty { "your dog" }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (paused) "Paused · $distance" else "$distance with $name")
            .setContentText(if (paused) OpawFormat.duration(WalkRecorder.elapsed()) else toilet.ifEmpty { "Walk in progress" })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .apply {
                if (!paused) {
                    // The system clock counts up on its own, adjusted for pauses.
                    setUsesChronometer(true)
                    setWhen(System.currentTimeMillis() - (WalkRecorder.elapsed() * 1000).toLong())
                    setShowWhen(true)
                }
            }
            .addAction(0, if (paused) "Resume" else "Pause", toggle)
            .addAction(0, "Finish", open)
            .build()
    }

    companion object {
        private const val CHANNEL = "walk"
        private const val ID = 7
        private const val ACTION_PAUSE = "pause"
        private const val ACTION_RESUME = "resume"
        private var instance: WalkService? = null
        private var lastUpdate = 0L

        /** Refreshes the notification; distance changes are throttled to every few seconds. */
        fun update(force: Boolean = true) {
            val service = instance ?: return
            val now = SystemClock.elapsedRealtime()
            if (!force && now - lastUpdate < 5000) return
            lastUpdate = now
            runCatching { service.getSystemService(NotificationManager::class.java).notify(ID, service.notification()) }
        }
    }
}
