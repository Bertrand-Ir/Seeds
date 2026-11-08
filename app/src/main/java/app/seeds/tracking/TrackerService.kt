package app.seeds.tracking

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.seeds.data.db.Session
import app.seeds.data.db.SessionKind
import app.seeds.data.prefs.Settings
import app.seeds.domain.Fmt
import app.seeds.notify.Notifier
import app.seeds.seeds
import app.seeds.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs while something is being tracked, or during work hours when the drift guard is on.
 * Shows the live timer notification and watches for long stints in distracting apps.
 */
class TrackerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: ForegroundWatcher? = null
    private var active: Session? = null
    private var settings: Settings = Settings()
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground(Notifier.placeholder(this))
        val c = seeds
        scope.launch {
            combine(c.tracking.active, c.settings.flow, c.tracking.seeds) { a, s, list -> Triple(a, s, list) }
                .collect { (a, s, list) ->
                    active = a
                    settings = s
                    render(a, s, list.firstOrNull { it.id == a?.seedId })
                    if (a == null && !shouldGuard(this@TrackerService, s)) stopSelfSafely()
                }
        }
        loop = scope.launch {
            var tick = 0
            while (isActive) {
                runCatching { watchDrift() }
                if (++tick % 3 == 0) {
                    runCatching { Widgets.updateAll(this@TrackerService) }
                    if (active == null && !shouldGuard(this@TrackerService, settings)) stopSelfSafely()
                }
                delay(20_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground(n: android.app.Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifier.ID_TRACKING, n, type)
    }

    private suspend fun render(a: Session?, s: Settings, seed: app.seeds.data.db.Seed?) {
        val n = if (a != null) {
            val stat = seed?.let { sd -> Effects.overview(this).let { o -> (o.daily + o.weekly + o.monthly).firstOrNull { it.seed.id == sd.id } } }
            Notifier.tracking(this, a, seed, stat, a.appPackage?.let { AppLabels.label(this, it) })
        } else {
            Notifier.guarding(this, Fmt.clockOfDay(s.workEndMin))
        }
        Notifier.post(this, Notifier.ID_TRACKING, n)
    }

    private suspend fun watchDrift() {
        val s = settings
        if (!s.driftEnabled || !Usage.hasAccess(this)) return
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isInteractive) return
        val w = watcher ?: ForegroundWatcher(this).also { watcher = it }
        val now = System.currentTimeMillis()
        val fg = w.poll(now)
        DriftState.currentPkg = fg?.first
        DriftState.currentSince = fg?.second ?: 0

        val a = active
        // An auto-logged drift ends as soon as you leave the app.
        if (a != null && a.kind == SessionKind.DRIFT && a.source == Session.SOURCE_DRIFT && a.appPackage != null && fg?.first != a.appPackage) {
            val c = seeds
            c.tracking.stop(now)
            val resume = DriftState.resumeSeedId?.let { c.tracking.seed(it) }
            DriftState.resumeSeedId = null
            Notifier.driftEnded(this, AppLabels.label(this, a.appPackage), now - a.startedAt, resume)
            return
        }

        val (pkg, since) = fg ?: return
        if (pkg !in s.distractingApps) return
        if (a != null && a.kind != SessionKind.SEED) return // already on a break or logging drift
        val focusSeed = a?.seedId?.let { seeds.tracking.seed(it) }?.takeIf { it.focus }
        val guarded = s.isWorkTime(now) || focusSeed != null
        if (!guarded) return
        if (now < DriftState.snoozeUntil || DriftState.promptedStint == since) return
        val minutes = ((now - since) / 60_000).toInt()
        if (minutes < s.driftThresholdMin) return
        DriftState.promptedStint = since
        Notifier.drift(this, AppLabels.label(this, pkg), minutes, focusSeed?.name)
    }

    private fun stopSelfSafely() {
        loop?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        fun shouldGuard(ctx: Context, s: Settings, now: Long = System.currentTimeMillis()): Boolean =
            s.onboarded && s.driftEnabled && s.isWorkTime(now) && Usage.hasAccess(ctx)

        fun start(ctx: Context) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, TrackerService::class.java)) }
        }
    }
}
