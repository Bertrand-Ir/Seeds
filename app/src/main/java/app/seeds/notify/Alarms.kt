package app.seeds.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import app.seeds.data.db.Session
import app.seeds.data.db.SessionKind
import app.seeds.data.prefs.Settings
import app.seeds.seeds
import app.seeds.tracking.ActionReceiver
import app.seeds.tracking.Effects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object Alarms {
    private const val RC_MORNING = 101
    private const val RC_NIGHT = 102
    private const val RC_WORK_START = 103
    private const val RC_WORK_END = 104
    private const val RC_CHECKIN = 105
    private const val RC_CHECKIN_TIMEOUT = 106

    /** Evening report arrives a little before bedtime, while there's still time to reflect. */
    const val NIGHT_LEAD_MIN = 15

    fun scheduleAll(ctx: Context, s: Settings) {
        if (!s.onboarded) return
        val now = System.currentTimeMillis()
        if (s.morningBrief) set(ctx, RC_MORNING, ActionReceiver.MORNING, nextAt(s.wakeMin, now)) else cancel(ctx, RC_MORNING, ActionReceiver.MORNING)
        if (s.nightReview) set(ctx, RC_NIGHT, ActionReceiver.NIGHT, nextAt(Math.floorMod(s.sleepMin - NIGHT_LEAD_MIN, 1440), now)) else cancel(ctx, RC_NIGHT, ActionReceiver.NIGHT)
        if (s.driftEnabled) {
            set(ctx, RC_WORK_START, ActionReceiver.WORK_START, nextAt(s.workStartMin, now) { s.isWorkDay(it) })
            set(ctx, RC_WORK_END, ActionReceiver.WORK_END, nextAt(s.workEndMin, now))
        } else {
            cancel(ctx, RC_WORK_START, ActionReceiver.WORK_START)
            cancel(ctx, RC_WORK_END, ActionReceiver.WORK_END)
        }
    }

    fun scheduleCheckIn(ctx: Context, active: Session?, s: Settings) {
        cancel(ctx, RC_CHECKIN, ActionReceiver.CHECKIN_DUE)
        if (active == null || s.checkInMin <= 0 || active.kind == SessionKind.DRIFT) return
        val now = System.currentTimeMillis()
        val due = (active.lastConfirmedAt + s.checkInMin * 60_000L).coerceAtLeast(now + 60_000L)
        set(ctx, RC_CHECKIN, ActionReceiver.CHECKIN_DUE, due) {
            putExtra(ActionReceiver.EXTRA_SESSION, active.id)
        }
    }

    fun scheduleCheckInTimeout(ctx: Context, sessionId: Long, dueAt: Long, graceMin: Int) {
        set(ctx, RC_CHECKIN_TIMEOUT, ActionReceiver.CHECKIN_TIMEOUT, System.currentTimeMillis() + graceMin * 60_000L) {
            putExtra(ActionReceiver.EXTRA_SESSION, sessionId)
            putExtra(ActionReceiver.EXTRA_DUE, dueAt)
        }
    }

    /** Next epoch millis at [minOfDay] local time, optionally only on days passing [dayOk] (ISO day of week). */
    fun nextAt(minOfDay: Int, now: Long, zone: ZoneId = ZoneId.systemDefault(), dayOk: (Int) -> Boolean = { true }): Long {
        var d = LocalDate.now(zone)
        repeat(8) {
            val t = ZonedDateTime.of(d.atStartOfDay(), zone).plusMinutes(minOfDay.toLong()).toInstant().toEpochMilli()
            if (t > now + 5_000 && dayOk(d.dayOfWeek.value)) return t
            d = d.plusDays(1)
        }
        return now + 86_400_000L
    }

    private fun pending(ctx: Context, code: Int, action: String, extras: Intent.() -> Unit = {}): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java).setAction(action).apply(extras)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun set(ctx: Context, code: Int, action: String, at: Long, extras: Intent.() -> Unit = {}) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, code, action, extras)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancel(ctx: Context, code: Int, action: String) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, code, action))
    }
}

/** Re-arm everything after reboot, app update or clock changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Alarms.scheduleAll(context, context.seeds.settings.current())
                Effects.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}
