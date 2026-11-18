package app.seeds.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import app.seeds.R
import app.seeds.data.db.Period
import app.seeds.data.db.SessionKind
import app.seeds.domain.Fmt
import app.seeds.domain.Overview
import app.seeds.notify.Notifier
import app.seeds.tracking.ActionReceiver
import app.seeds.tracking.Effects
import app.seeds.ui.theme.SeedPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NowWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refreshAsync(context, goAsync())
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        Widgets.refreshAsync(context, goAsync())
}

class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refreshAsync(context, goAsync())
}

/**
 * Home-screen widgets, kept deliberately simple.
 *
 * Now: what's growing + a live timer + Switch/Done. Idle: today's progress + one "start next" button.
 * While something grows, the card takes that seed's color.
 * Today: the day score ring and one line.
 */
object Widgets {
    private const val INK = 0xFF0E1210.toInt()
    private const val INK_MUTED = 0xB30E1210.toInt()

    fun placed(ctx: Context): Boolean {
        val mgr = AppWidgetManager.getInstance(ctx)
        return mgr.getAppWidgetIds(ComponentName(ctx, NowWidget::class.java)).isNotEmpty() ||
            mgr.getAppWidgetIds(ComponentName(ctx, TodayWidget::class.java)).isNotEmpty()
    }

    fun canPin(ctx: Context): Boolean = AppWidgetManager.getInstance(ctx).isRequestPinAppWidgetSupported

    /** Asks the launcher to drop the widget on the home screen (the user confirms). */
    fun requestPin(ctx: Context, today: Boolean = false): Boolean = runCatching {
        val cls = if (today) TodayWidget::class.java else NowWidget::class.java
        AppWidgetManager.getInstance(ctx).requestPinAppWidget(ComponentName(ctx, cls), null, null)
    }.getOrDefault(false)

    fun refreshAsync(ctx: Context, pending: android.content.BroadcastReceiver.PendingResult) {
        CoroutineScope(Dispatchers.Default).launch {
            try { updateAll(ctx) } finally { pending.finish() }
        }
    }

    suspend fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val nowIds = mgr.getAppWidgetIds(ComponentName(ctx, NowWidget::class.java))
        val todayIds = mgr.getAppWidgetIds(ComponentName(ctx, TodayWidget::class.java))
        if (nowIds.isEmpty() && todayIds.isEmpty()) return
        val o = runCatching { Effects.overview(ctx) }.getOrNull() ?: return

        for (id in nowIds) {
            val views = if (Build.VERSION.SDK_INT >= 31) {
                RemoteViews(mapOf(SizeF(100f, 80f) to small(ctx, o), SizeF(200f, 110f) to standard(ctx, o)))
            } else {
                val w = mgr.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                if (w in 1..199) small(ctx, o) else standard(ctx, o)
            }
            mgr.updateAppWidget(id, views)
        }
        if (todayIds.isNotEmpty()) mgr.updateAppWidget(todayIds, today(ctx, o))
    }

    private fun startSeed(ctx: Context, seedId: Long, code: Int) =
        Notifier.action(ctx, ActionReceiver.START, code) { putExtra(ActionReceiver.EXTRA_SEED, seedId) }

    private class Look(val fill: Int?, val bg: Int, val ink: Int, val muted: Int, val accent: Int)

    private fun look(ctx: Context, o: Overview): Look {
        val a = o.active
        val fill = when {
            a == null -> null
            a.kind == SessionKind.SEED -> SeedPalette.fill(o.activeSeed?.color ?: 0)
            a.kind == SessionKind.BREAK -> 0xFF8CCBFF.toInt()
            else -> 0xFFFF8A75.toInt()
        }
        return if (fill != null) Look(fill, fill, INK, INK_MUTED, INK)
        else Look(
            null,
            ContextCompat.getColor(ctx, R.color.widget_bg),
            ContextCompat.getColor(ctx, R.color.widget_ink),
            ContextCompat.getColor(ctx, R.color.widget_muted),
            ContextCompat.getColor(ctx, R.color.widget_accent),
        )
    }

    private fun title(o: Overview): String = when (o.active?.kind) {
        SessionKind.SEED -> o.activeSeed?.name ?: "Untitled"
        SessionKind.BREAK -> "On a break"
        SessionKind.DRIFT -> "Drifting"
        null -> ""
    }

    private fun base(v: RemoteViews, l: Look) {
        v.setInt(R.id.bg, "setColorFilter", l.bg)
        v.setInt(R.id.mark, "setColorFilter", l.accent)
    }

    // ------------------------------------------------------------------------------------------

    private fun standard(ctx: Context, o: Overview): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_now)
        val l = look(ctx, o)
        base(v, l)
        v.setOnClickPendingIntent(android.R.id.background, Notifier.openApp(ctx, code = 90))
        v.setTextColor(R.id.label, l.muted)
        v.setTextColor(R.id.seed_name, l.ink)
        v.setTextColor(R.id.timer, l.ink)
        v.setTextColor(R.id.idle_big, l.ink)
        v.setTextColor(R.id.detail, l.muted)

        val a = o.active
        if (a != null) {
            v.setTextViewText(R.id.label, "NOW")
            v.setTextViewText(R.id.seed_name, title(o))
            v.setViewVisibility(R.id.timer, View.VISIBLE)
            v.setViewVisibility(R.id.idle_big, View.GONE)
            v.setChronometer(R.id.timer, SystemClock.elapsedRealtime() - (System.currentTimeMillis() - a.startedAt), null, true)
            v.setViewVisibility(R.id.progress, View.GONE)
            val st = o.activeStat
            if (st != null && st.targetMs > 0) {
                v.setViewVisibility(R.id.progress_ink, View.VISIBLE)
                v.setProgressBar(R.id.progress_ink, 1000, (st.progress * 1000).toInt(), false)
                val per = when (st.seed.period) { Period.DAILY -> "today"; Period.WEEKLY -> "this week"; Period.MONTHLY -> "this month" }
                v.setTextViewText(R.id.detail, if (st.grown) "Grown ✓" else "${Fmt.duration(st.remainingMs)} left $per")
            } else {
                v.setViewVisibility(R.id.progress_ink, View.GONE)
                v.setTextViewText(R.id.detail, "since ${Fmt.clock(a.startedAt)}")
            }
            v.setViewVisibility(R.id.actions_active, View.VISIBLE)
            v.setViewVisibility(R.id.actions_idle, View.GONE)
            v.setOnClickPendingIntent(R.id.btn_switch, Notifier.quickSwitch(ctx))
            v.setOnClickPendingIntent(R.id.btn_stop, Notifier.action(ctx, ActionReceiver.STOP, 91))
        } else {
            v.setTextViewText(R.id.label, "TODAY")
            v.setTextViewText(R.id.seed_name, "Nothing growing")
            v.setViewVisibility(R.id.timer, View.GONE)
            v.setViewVisibility(R.id.idle_big, View.VISIBLE)
            v.setViewVisibility(R.id.progress_ink, View.GONE)
            if (o.dailyCount > 0) {
                v.setTextViewText(R.id.idle_big, "${o.grown} of ${o.dailyCount}")
                v.setViewVisibility(R.id.progress, View.VISIBLE)
                v.setProgressBar(R.id.progress, 1000, o.grown * 1000 / o.dailyCount, false)
                v.setTextViewText(R.id.detail, "seeds grown · ${Fmt.duration(o.focusMs)} so far")
            } else {
                v.setTextViewText(R.id.idle_big, Fmt.duration(o.focusMs))
                v.setViewVisibility(R.id.progress, View.GONE)
                v.setTextViewText(R.id.detail, "grown today")
            }
            v.setViewVisibility(R.id.actions_active, View.GONE)
            v.setViewVisibility(R.id.actions_idle, View.VISIBLE)
            val next = o.nextUp
            if (next != null) {
                v.setTextViewText(R.id.btn_next, "▶  ${next.seed.name}")
                v.setOnClickPendingIntent(R.id.btn_next, startSeed(ctx, next.seed.id, 92))
            } else {
                v.setTextViewText(R.id.btn_next, "Start something")
                v.setOnClickPendingIntent(R.id.btn_next, Notifier.quickSwitch(ctx))
            }
            v.setOnClickPendingIntent(R.id.btn_more, Notifier.quickSwitch(ctx))
        }
        return v
    }

    private fun small(ctx: Context, o: Overview): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_now_small)
        val l = look(ctx, o)
        base(v, l)
        v.setOnClickPendingIntent(android.R.id.background, Notifier.openApp(ctx, code = 90))
        v.setTextColor(R.id.seed_name, l.ink)
        v.setTextColor(R.id.timer, l.ink)
        v.setTextColor(R.id.idle_big, l.ink)
        val a = o.active
        if (a != null) {
            v.setTextViewText(R.id.seed_name, title(o))
            v.setViewVisibility(R.id.timer, View.VISIBLE)
            v.setViewVisibility(R.id.idle_big, View.GONE)
            v.setChronometer(R.id.timer, SystemClock.elapsedRealtime() - (System.currentTimeMillis() - a.startedAt), null, true)
            v.setTextViewText(R.id.btn_main, "Done")
            v.setInt(R.id.btn_main, "setBackgroundResource", R.drawable.widget_pill_ink)
            v.setTextColor(R.id.btn_main, 0xFFFFFFFF.toInt())
            v.setOnClickPendingIntent(R.id.btn_main, Notifier.action(ctx, ActionReceiver.STOP, 91))
        } else {
            v.setTextViewText(R.id.seed_name, "Seeds")
            v.setViewVisibility(R.id.timer, View.GONE)
            v.setViewVisibility(R.id.idle_big, View.VISIBLE)
            v.setTextViewText(R.id.idle_big, if (o.dailyCount > 0) "${o.grown} of ${o.dailyCount} grown" else "Nothing growing")
            v.setInt(R.id.btn_main, "setBackgroundResource", R.drawable.widget_btn_accent)
            v.setTextColor(R.id.btn_main, ContextCompat.getColor(ctx, R.color.widget_on_accent))
            val next = o.nextUp
            if (next != null) {
                v.setTextViewText(R.id.btn_main, "▶  ${next.seed.name}")
                v.setOnClickPendingIntent(R.id.btn_main, startSeed(ctx, next.seed.id, 92))
            } else {
                v.setTextViewText(R.id.btn_main, "Start")
                v.setOnClickPendingIntent(R.id.btn_main, Notifier.quickSwitch(ctx))
            }
        }
        return v
    }

    // ------------------------------------------------------------------------------------------

    private fun today(ctx: Context, o: Overview): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_today)
        v.setOnClickPendingIntent(android.R.id.background, Notifier.openApp(ctx, "review", 95))
        v.setInt(R.id.bg, "setColorFilter", ContextCompat.getColor(ctx, R.color.widget_bg))
        v.setTextViewText(R.id.score, o.score?.toString() ?: "–")
        v.setImageViewBitmap(R.id.ring, ring(ctx, (o.score ?: 0) / 100f))
        v.setTextViewText(R.id.summary, if (o.dailyCount > 0) "${o.grown} of ${o.dailyCount} grown" else "${Fmt.duration(o.focusMs)} growing")
        v.setTextViewText(R.id.summary2, o.activeSeed?.let { "● ${it.name}" } ?: "")
        v.setViewVisibility(R.id.summary2, if (o.activeSeed != null) View.VISIBLE else View.GONE)
        return v
    }

    private fun ring(ctx: Context, progress: Float): Bitmap {
        val size = 300
        val stroke = 24f
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = RectF(stroke / 2 + 2, stroke / 2 + 2, size - stroke / 2 - 2, size - stroke / 2 - 2)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        p.color = ContextCompat.getColor(ctx, R.color.widget_track)
        c.drawArc(r, 0f, 360f, false, p)
        if (progress > 0f) {
            p.color = ContextCompat.getColor(ctx, R.color.widget_accent)
            c.drawArc(r, -90f, 360f * progress.coerceIn(0.02f, 1f), false, p)
        }
        return bmp
    }
}
