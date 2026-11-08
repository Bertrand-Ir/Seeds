package app.seeds.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.seeds.data.db.Direction
import app.seeds.data.db.Session
import app.seeds.data.db.SessionKind
import app.seeds.domain.DayClock
import app.seeds.notify.Alarms
import app.seeds.notify.Notifier
import app.seeds.seeds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles notification buttons, widget taps and scheduled alarms. */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                handle(context.applicationContext, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(ctx: Context, intent: Intent) {
        val c = ctx.seeds
        val tracking = c.tracking
        val now = System.currentTimeMillis()
        when (intent.action) {
            START -> {
                val seedId = intent.getLongExtra(EXTRA_SEED, -1).takeIf { it > 0 }
                if (seedId != null) tracking.start(seedId) else TrackerService.start(ctx)
                Notifier.cancel(ctx, Notifier.ID_DRIFT)
                Notifier.cancel(ctx, Notifier.ID_TRIMMED)
            }
            STOP -> {
                tracking.stop(now)
                Notifier.cancel(ctx, Notifier.ID_CHECKIN)
            }
            CONFIRM -> {
                tracking.confirm(now)
                Notifier.cancel(ctx, Notifier.ID_CHECKIN)
            }
            CHECKIN_DUE -> {
                val s = c.settings.current()
                val active = tracking.activeNow() ?: return
                if (active.id != intent.getLongExtra(EXTRA_SESSION, -1)) { Effects.refresh(ctx); return }
                val due = active.lastConfirmedAt + s.checkInMin * 60_000L
                if (now < due - 60_000) { Alarms.scheduleCheckIn(ctx, active, s); return }
                val seed = active.seedId?.let { tracking.seed(it) }
                val title = Notifier.sessionTitle(active, seed, active.appPackage?.let { AppLabels.label(ctx, it) })
                Notifier.checkIn(ctx, active, title, s.graceMin, s.autoTrim)
                if (s.autoTrim) Alarms.scheduleCheckInTimeout(ctx, active.id, due.coerceAtMost(now), s.graceMin)
            }
            CHECKIN_TIMEOUT -> {
                val s = c.settings.current()
                if (!s.autoTrim) return
                val id = intent.getLongExtra(EXTRA_SESSION, -1)
                val due = intent.getLongExtra(EXTRA_DUE, now)
                val active = tracking.activeNow() ?: return
                if (active.id != id || active.lastConfirmedAt >= due) return // answered in time
                if (tracking.trim(id, due)) {
                    Notifier.cancel(ctx, Notifier.ID_CHECKIN)
                    val seed = active.seedId?.let { tracking.seed(it) }
                    Notifier.trimmed(ctx, id, Notifier.sessionTitle(active, seed, null), due)
                }
            }
            RESTORE -> {
                val id = intent.getLongExtra(EXTRA_SESSION, -1)
                tracking.restore(id)
                Notifier.cancel(ctx, Notifier.ID_TRIMMED)
            }

            // --- Drift guard --------------------------------------------------------------------
            DRIFT_BACK -> {
                DriftState.snoozeUntil = now + 10 * 60_000L
                Notifier.cancel(ctx, Notifier.ID_DRIFT)
            }
            DRIFT_BREAK -> {
                val since = DriftState.currentSince.takeIf { it > 0 } ?: now
                tracking.start(null, SessionKind.BREAK, at = since)
                DriftState.snoozeUntil = now + 30 * 60_000L
                Notifier.cancel(ctx, Notifier.ID_DRIFT)
            }
            DRIFT_LOG -> {
                val pkg = DriftState.currentPkg
                val since = DriftState.currentSince.takeIf { it > 0 } ?: now
                DriftState.resumeSeedId = tracking.activeNow()?.takeIf { it.kind == SessionKind.SEED }?.seedId
                tracking.start(null, SessionKind.DRIFT, at = since, source = Session.SOURCE_DRIFT, appPackage = pkg)
                Notifier.cancel(ctx, Notifier.ID_DRIFT)
            }

            // --- Money ---------------------------------------------------------------------------
            LABEL -> {
                val txn = intent.getLongExtra(EXTRA_TXN, -1)
                val cat = intent.getLongExtra(EXTRA_CATEGORY, -1)
                if (txn > 0 && cat > 0) c.money.label(txn, cat)
                Notifier.cancel(ctx, Notifier.moneyId(txn))
            }

            // --- Rituals & rhythm -----------------------------------------------------------------
            MORNING -> {
                val s = c.settings.current()
                Notifier.morning(ctx, s.name, Effects.overview(ctx, now))
                Alarms.scheduleAll(ctx, s)
            }
            NIGHT -> {
                val s = c.settings.current()
                val o = Effects.overview(ctx, now)
                val clock = DayClock(s)
                val spent = c.money.rangeNow(clock.dayStart(o.day), clock.dayEnd(o.day))
                    .filter { it.direction == Direction.OUT }.sumOf { it.amount + it.fee }
                Notifier.night(ctx, o, spent, s.currency)
                Alarms.scheduleAll(ctx, s)
            }
            WORK_START, WORK_END -> {
                Alarms.scheduleAll(ctx, c.settings.current())
                Effects.refresh(ctx)
            }
        }
    }

    companion object {
        const val START = "app.seeds.START"
        const val STOP = "app.seeds.STOP"
        const val CONFIRM = "app.seeds.CONFIRM"
        const val CHECKIN_DUE = "app.seeds.CHECKIN_DUE"
        const val CHECKIN_TIMEOUT = "app.seeds.CHECKIN_TIMEOUT"
        const val RESTORE = "app.seeds.RESTORE"
        const val DRIFT_BACK = "app.seeds.DRIFT_BACK"
        const val DRIFT_BREAK = "app.seeds.DRIFT_BREAK"
        const val DRIFT_LOG = "app.seeds.DRIFT_LOG"
        const val LABEL = "app.seeds.LABEL"
        const val MORNING = "app.seeds.MORNING"
        const val NIGHT = "app.seeds.NIGHT"
        const val WORK_START = "app.seeds.WORK_START"
        const val WORK_END = "app.seeds.WORK_END"

        const val EXTRA_SEED = "seed"
        const val EXTRA_SESSION = "session"
        const val EXTRA_DUE = "due"
        const val EXTRA_TXN = "txn"
        const val EXTRA_CATEGORY = "category"
    }
}
