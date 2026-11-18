package app.seeds.run

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.seeds.R
import app.seeds.data.db.Run
import app.seeds.data.db.SessionKind
import app.seeds.domain.Fmt
import app.seeds.domain.Geo
import app.seeds.domain.LatLng
import app.seeds.domain.PolylineCodec
import app.seeds.notify.Notifier
import app.seeds.seeds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

enum class RunPhase { RUNNING, PAUSED, SAVING, DONE }

data class LiveRun(
    val routeId: Long?,
    val name: String,
    val route: List<LatLng>,
    val routeLength: Double,
    val seedId: Long?,
    val phase: RunPhase = RunPhase.RUNNING,
    val startedAt: Long = System.currentTimeMillis(),
    val movingMs: Long = 0,
    /** Wall time of the last fix that counted as moving — the UI ticks the clock from here. */
    val movingSince: Long? = null,
    val distanceM: Double = 0.0,
    val track: List<LatLng> = emptyList(),
    val currentPace: Int = 0,
    val splits: List<Int> = emptyList(),
    val accuracy: Float? = null,
    val offRouteM: Double? = null,
    val alongM: Double = 0.0,
    val steps: Int = 0,
    val cadence: Int = 0,
    val elevationGain: Double = 0.0,
    val autoPaused: Boolean = false,
    val savedRunId: Long? = null,
) {
    fun moving(now: Long): Long = movingMs + (movingSince?.let { (now - it).coerceIn(0, 4_000) } ?: 0)
    val avgPace: Int get() = if (distanceM < 30) 0 else (movingMs / 1000.0 / (distanceM / 1000.0)).toInt()
    val offRoute: Boolean get() = (offRouteM ?: 0.0) > OFF_ROUTE_M
    val remainingM: Double get() = (routeLength - alongM).coerceAtLeast(0.0)

    companion object { const val OFF_ROUTE_M = 40.0 }
}

/** Shared between the service and the UI (same process). */
object RunState {
    internal val _live = MutableStateFlow<LiveRun?>(null)
    val live: StateFlow<LiveRun?> = _live
    val isActive: Boolean get() = live.value?.phase.let { it == RunPhase.RUNNING || it == RunPhase.PAUSED }

    /** Called by the UI once it has shown the saved run. */
    fun clear() { if (!isActive) _live.value = null }
}

/**
 * Foreground GPS tracking for a run: distance, pace, splits, cadence, elevation, and staying on the
 * planned route. Keeps going with the screen off. Speaks every kilometre if voice is on.
 */
class RunService : Service(), LocationListener, SensorEventListener, TextToSpeech.OnInitListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var voice = true

    // Raw per-point data for "best 1 km / 5 km"
    private val cumDist = ArrayList<Double>()
    private val cumMoving = ArrayList<Long>()
    private var lastFix: Location? = null
    private var lastAccepted: LatLng? = null
    private var lastAcceptedT = 0L
    private var stillSince: Long? = null
    private var altRef: Double? = null
    private var stepBase: Float? = null
    private val stepWindow = ArrayDeque<Pair<Long, Int>>()
    private var offSince: Long? = null
    private var warnedOff = false
    private var announcedDone = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        voice = getSharedPreferences("seeds_ui", MODE_PRIVATE).getBoolean("run_voice", true)
        if (voice) tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_PAUSE -> setPaused(true)
            ACTION_RESUME -> setPaused(false)
            ACTION_FINISH -> finish()
            ACTION_DISCARD -> discard()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun start(intent: Intent) {
        if (RunState.live.value?.phase.let { it == RunPhase.RUNNING || it == RunPhase.PAUSED }) return
        val route = intent.getStringExtra(EXTRA_ROUTE)?.let(PolylineCodec::decode).orEmpty()
        RunState._live.value = LiveRun(
            routeId = intent.getLongExtra(EXTRA_ROUTE_ID, -1).takeIf { it > 0 },
            name = intent.getStringExtra(EXTRA_NAME) ?: "Free run",
            route = route,
            routeLength = Geo.length(route),
            seedId = intent.getLongExtra(EXTRA_SEED, -1).takeIf { it > 0 },
        )
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, Notifier.ID_RUN, notification(), type)

        val lm = getSystemService(LocationManager::class.java)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, this, Looper.getMainLooper())
        }
        val sm = getSystemService(SensorManager::class.java)
        sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }

        speak("Run started. Have a good one.")
        scope.launch {
            while (isActive) {
                delay(5_000)
                if (RunState.live.value?.phase in setOf(RunPhase.RUNNING, RunPhase.PAUSED)) Notifier.post(this@RunService, Notifier.ID_RUN, notification())
            }
        }
    }

    private fun setPaused(paused: Boolean) {
        RunState._live.update { r ->
            r?.copy(
                phase = if (paused) RunPhase.PAUSED else RunPhase.RUNNING,
                movingMs = if (paused) r.moving(System.currentTimeMillis()) else r.movingMs,
                movingSince = null,
            )
        }
        lastAccepted = null // don't bridge the gap with a straight line
        speak(if (paused) "Paused." else "Resumed.")
        Notifier.post(this, Notifier.ID_RUN, notification())
    }

    // ---- GPS ------------------------------------------------------------------------------------

    override fun onLocationChanged(loc: Location) {
        val r = RunState.live.value ?: return
        val now = loc.time.takeIf { it > 0 } ?: System.currentTimeMillis()
        RunState._live.value = r.copy(accuracy = loc.accuracy)
        if (r.phase != RunPhase.RUNNING) return
        if (loc.hasAccuracy() && loc.accuracy > 30f) return
        val p = LatLng(loc.latitude, loc.longitude)
        val prev = lastAccepted
        lastFix = loc
        if (prev == null) {
            lastAccepted = p; lastAcceptedT = now
            RunState._live.value = r.copy(track = r.track + p, accuracy = loc.accuracy)
            cumDist.add(r.distanceM); cumMoving.add(r.movingMs)
            return
        }
        val d = Geo.distance(prev, p)
        val dt = (now - lastAcceptedT).coerceAtLeast(1)
        val speed = if (loc.hasSpeed()) loc.speed.toDouble() else d / (dt / 1000.0)
        if (d / (dt / 1000.0) > 9.0) return // GPS jump — faster than a sprinter

        var movingMs = r.movingMs
        var autoPaused = r.autoPaused
        var movingSince: Long? = r.movingSince
        if (speed >= 0.6) {
            if (dt < 10_000) movingMs += dt
            stillSince = null; autoPaused = false; movingSince = System.currentTimeMillis()
        } else {
            val s = stillSince ?: now.also { stillSince = it }
            if (now - s > 10_000) { autoPaused = true; movingSince = null }
            else if (dt < 10_000) movingMs += dt
        }

        if (d < 2.5 && speed < 0.6) { // jitter while standing still
            lastAcceptedT = now
            RunState._live.value = r.copy(movingMs = movingMs, autoPaused = autoPaused, movingSince = movingSince, accuracy = loc.accuracy)
            return
        }

        val distance = r.distanceM + d
        val track = r.track + p
        cumDist.add(distance); cumMoving.add(movingMs)
        lastAccepted = p; lastAcceptedT = now

        // current pace: last ~30 s
        var i = cumDist.size - 1
        while (i > 0 && movingMs - cumMoving[i] < 30_000) i--
        val wd = distance - cumDist[i]
        val wt = movingMs - cumMoving[i]
        val pace = if (wd > 20) (wt / 1000.0 / (wd / 1000.0)).toInt() else r.currentPace

        // splits
        var splits = r.splits
        val km = (distance / 1000).toInt()
        if (km > splits.size) {
            val prevTotal = splits.sum() * 1000L
            val split = ((movingMs - prevTotal) / 1000).toInt()
            splits = splits + split
            speak("$km kilometre${if (km > 1) "s" else ""}. Pace ${spokenPace(split)}." + (if (r.routeLength > 0) " ${"%.1f".format(Locale.US, (r.routeLength - r.alongM).coerceAtLeast(0.0) / 1000)} to go." else ""))
            buzz(longArrayOf(0, 120, 80, 120))
        }

        // elevation
        var gain = r.elevationGain
        if (loc.hasAltitude()) {
            val ref = altRef
            if (ref == null) altRef = loc.altitude
            else if (abs(loc.altitude - ref) > 4) { if (loc.altitude > ref) gain += loc.altitude - ref; altRef = loc.altitude }
        }

        // route
        var off: Double? = null
        var along = r.alongM
        if (r.route.size >= 2) {
            val (o, a) = Geo.project(p, r.route)
            off = o
            if (a > along - 60) along = maxOf(along, a) // never jump backwards on loops
            if (o > LiveRun.OFF_ROUTE_M) {
                val s = offSince ?: now.also { offSince = it }
                if (!warnedOff && now - s > 15_000) { warnedOff = true; speak("You're off route."); buzz(longArrayOf(0, 300, 150, 300)) }
            } else {
                if (warnedOff) speak("Back on route.")
                offSince = null; warnedOff = false
            }
            if (!announcedDone && along >= r.routeLength - 30 && distance >= r.routeLength * 0.8) {
                announcedDone = true
                speak("Route complete. ${Geo.km(distance, 1)} kilometres. Well done.")
                buzz(longArrayOf(0, 200, 100, 200, 100, 400))
            }
        }

        RunState._live.value = r.copy(
            movingMs = movingMs, movingSince = movingSince, autoPaused = autoPaused, distanceM = distance, track = track,
            currentPace = pace, splits = splits, accuracy = loc.accuracy, offRouteM = off, alongM = along, elevationGain = gain,
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    // ---- Steps -----------------------------------------------------------------------------------

    override fun onSensorChanged(event: SensorEvent) {
        val r = RunState.live.value ?: return
        val total = event.values[0]
        val base = stepBase ?: total.also { stepBase = it }
        val steps = (total - base).toInt()
        val now = System.currentTimeMillis()
        stepWindow.addLast(now to steps)
        while (stepWindow.size > 1 && now - stepWindow.first().first > 30_000) stepWindow.removeFirst()
        val first = stepWindow.first()
        val cadence = if (now - first.first > 5_000) ((steps - first.second) * 60_000L / (now - first.first)).toInt() else r.cadence
        RunState._live.value = r.copy(steps = steps, cadence = if (r.phase == RunPhase.RUNNING && !r.autoPaused) cadence else 0)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // ---- Finish -----------------------------------------------------------------------------------

    private fun finish() {
        val r = RunState.live.value ?: return stopEverything()
        if (r.phase == RunPhase.SAVING || r.phase == RunPhase.DONE) return
        val end = System.currentTimeMillis()
        val moving = r.moving(end)
        RunState._live.value = r.copy(phase = RunPhase.SAVING, movingMs = moving, movingSince = null)
        stopSensors()
        scope.launch {
            val c = seeds
            val id = if (r.distanceM < 50) null else {
                val run = Run(
                    routeId = r.routeId, name = r.name, startedAt = r.startedAt, endedAt = end, movingMs = moving,
                    distanceM = r.distanceM, track = PolylineCodec.encode(r.track), splits = r.splits.joinToString(","),
                    steps = r.steps, elevationGainM = r.elevationGain, best1kSec = best(1000.0), best5kSec = best(5000.0), seedId = r.seedId,
                )
                val newId = c.db.runs().insert(run)
                // A run grows your exercise seed.
                if (r.seedId != null) c.tracking.logManual(r.seedId, SessionKind.SEED, r.startedAt, end, note = "Run · ${Geo.km(r.distanceM)} km")
                newId
            }
            speak(if (id != null) "Run saved. ${Geo.km(r.distanceM, 1)} kilometres in ${spokenDuration(moving)}." else "Run discarded. Too short to save.")
            RunState._live.value = r.copy(phase = RunPhase.DONE, movingMs = moving, movingSince = null, savedRunId = id ?: -1)
            delay(2_500) // let the voice finish
            stopEverything()
        }
    }

    private fun discard() {
        stopSensors()
        RunState._live.value = null
        stopEverything()
    }

    /** Fastest moving time over any stretch of [window] metres. */
    private fun best(window: Double): Int? {
        if (cumDist.isEmpty() || cumDist.last() < window) return null
        var bestMs = Long.MAX_VALUE
        var i = 0
        for (j in cumDist.indices) {
            while (i < j && cumDist[j] - cumDist[i + 1] >= window) i++
            if (cumDist[j] - cumDist[i] >= window) bestMs = minOf(bestMs, cumMoving[j] - cumMoving[i])
        }
        return if (bestMs == Long.MAX_VALUE) null else (bestMs / 1000).toInt()
    }

    private fun stopSensors() {
        runCatching { getSystemService(LocationManager::class.java).removeUpdates(this) }
        runCatching { getSystemService(SensorManager::class.java).unregisterListener(this) }
    }

    private fun stopEverything() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSensors()
        tts?.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    // ---- Voice & haptics ---------------------------------------------------------------------------

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale.UK
    }

    private fun speak(text: String) {
        if (voice && ttsReady) tts?.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
    }

    private fun spokenPace(sec: Int) = "${sec / 60} minutes ${sec % 60} seconds"
    private fun spokenDuration(ms: Long): String {
        val m = ms / 60_000
        return if (m >= 60) "${m / 60} hours ${m % 60} minutes" else "$m minutes"
    }

    private fun buzz(pattern: LongArray) {
        runCatching { getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }

    private fun notification(): android.app.Notification {
        val r = RunState.live.value
        val now = System.currentTimeMillis()
        val title = when (r?.phase) {
            RunPhase.PAUSED -> "Paused · ${r.name}"
            else -> "Running · ${r?.name ?: "Run"}"
        }
        val text = r?.let { "${Geo.km(it.distanceM)} km · ${Fmt.timer(it.moving(now))} · ${Geo.pace(it.currentPace)} /km" } ?: "Getting GPS…"
        val b = NotificationCompat.Builder(this, Notifier.CH_RUN)
            .setSmallIcon(R.drawable.ic_seeds_mark)
            .setColor(0xFF1E7A4A.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(Notifier.openApp(this, "run/live", 60))
        if (r?.phase == RunPhase.PAUSED) b.addAction(0, "Resume", pending(ACTION_RESUME, 61))
        else b.addAction(0, "Pause", pending(ACTION_PAUSE, 62))
        b.addAction(0, "Finish", pending(ACTION_FINISH, 63))
        if (r != null && r.routeLength > 0) b.setProgress(1000, ((r.alongM / r.routeLength) * 1000).toInt().coerceIn(0, 1000), false)
        return b.build()
    }

    private fun pending(action: String, code: Int) = android.app.PendingIntent.getService(
        this, code, Intent(this, RunService::class.java).setAction(action),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_START = "app.seeds.run.START"
        const val ACTION_PAUSE = "app.seeds.run.PAUSE"
        const val ACTION_RESUME = "app.seeds.run.RESUME"
        const val ACTION_FINISH = "app.seeds.run.FINISH"
        const val ACTION_DISCARD = "app.seeds.run.DISCARD"
        private const val EXTRA_ROUTE = "route"
        private const val EXTRA_ROUTE_ID = "routeId"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_SEED = "seed"

        fun start(ctx: Context, routeId: Long?, name: String, encodedRoute: String?, seedId: Long?) {
            val i = Intent(ctx, RunService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_ROUTE_ID, routeId ?: -1).putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_ROUTE, encodedRoute).putExtra(EXTRA_SEED, seedId ?: -1)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun send(ctx: Context, action: String) {
            ctx.startService(Intent(ctx, RunService::class.java).setAction(action))
        }
    }
}
