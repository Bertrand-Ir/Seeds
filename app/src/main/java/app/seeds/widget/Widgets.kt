package app.seeds.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import app.seeds.R
import app.seeds.data.db.SessionKind
import app.seeds.domain.Fmt
import app.seeds.domain.Overview
import app.seeds.notify.Notifier
import app.seeds.tracking.ActionReceiver
import app.seeds.tracking.AppLabels
import app.seeds.tracking.Effects
import app.seeds.ui.theme.SeedPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NowWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refreshAsync(context, goAsync())
}

class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refreshAsync(context, goAsync())
}

object Widgets {
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
        if (nowIds.isNotEmpty()) mgr.updateAppWidget(nowIds, nowViews(ctx, o))
        if (todayIds.isNotEmpty()) mgr.updateAppWidget(todayIds, todayViews(ctx, o))
    }

    private fun nowViews(ctx: Context, o: Overview): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_now)
        v.setOnClickPendingIntent(R.id.root, Notifier.openApp(ctx, code = 90))
        v.setTextViewText(R.id.score, o.score?.let { "$it today" } ?: "")
        val active = o.active
        val ink = ContextCompat.getColor(ctx, R.color.widget_ink)
        if (active != null) {
            val seed = o.activeSeed
            val title = when (active.kind) {
                SessionKind.SEED -> seed?.name ?: "Untitled"
                SessionKind.BREAK -> "Break"
                SessionKind.DRIFT -> "Drift" + (active.appPackage?.let { " · " + AppLabels.label(ctx, it) } ?: "")
            }
            val dotColor = when (active.kind) {
                SessionKind.SEED -> seed?.let { SeedPalette.fill(it.color) } ?: ink
                SessionKind.BREAK -> 0xFF8CCBFF.toInt()
                SessionKind.DRIFT -> 0xFFFF6B57.toInt()
            }
            v.setTextViewText(R.id.label, "NOW GROWING")
            v.setTextViewText(R.id.seed_name, title)
            v.setInt(R.id.dot, "setColorFilter", dotColor)
            v.setViewVisibility(R.id.timer, View.VISIBLE)
            v.setChronometer(R.id.timer, SystemClock.elapsedRealtime() - (System.currentTimeMillis() - active.startedAt), null, true)
            val stat = o.activeStat
            if (stat != null && stat.targetMs > 0) {
                v.setViewVisibility(R.id.progress, View.VISIBLE)
                v.setProgressBar(R.id.progress, 1000, (stat.progress * 1000).toInt(), false)
                if (Build.VERSION.SDK_INT >= 31) v.setColorStateList(R.id.progress, "setProgressTintList", ColorStateList.valueOf(dotColor))
                val left = stat.remainingMs
                v.setTextViewText(R.id.detail, "${Fmt.duration(stat.doneMs)} of ${Fmt.duration(stat.targetMs)}" + if (left > 0) " · ${Fmt.duration(left)} to go" else " · grown ✓")
            } else {
                v.setViewVisibility(R.id.progress, View.GONE)
                v.setTextViewText(R.id.detail, "Since ${Fmt.clock(active.startedAt)}")
            }
            v.setViewVisibility(R.id.actions_active, View.VISIBLE)
            v.setViewVisibility(R.id.actions_idle, View.GONE)
            v.setOnClickPendingIntent(R.id.btn_switch, Notifier.quickSwitch(ctx))
            v.setOnClickPendingIntent(R.id.btn_stop, Notifier.action(ctx, ActionReceiver.STOP, 91))
        } else {
            v.setTextViewText(R.id.label, "NOW")
            v.setTextViewText(R.id.seed_name, "Nothing growing")
            v.setInt(R.id.dot, "setColorFilter", ContextCompat.getColor(ctx, R.color.widget_track))
            v.setViewVisibility(R.id.timer, View.GONE)
            v.setViewVisibility(R.id.progress, View.VISIBLE)
            v.setProgressBar(R.id.progress, 1000, if (o.dailyCount == 0) 0 else o.grown * 1000 / o.dailyCount, false)
            if (Build.VERSION.SDK_INT >= 31) v.setColorStateList(R.id.progress, "setProgressTintList", ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.widget_accent)))
            v.setTextViewText(R.id.detail, if (o.dailyCount > 0) "${o.grown} of ${o.dailyCount} seeds grown · ${Fmt.duration(o.focusMs)} today" else "Tap a seed to start growing")
            v.setViewVisibility(R.id.actions_active, View.GONE)
            v.setViewVisibility(R.id.actions_idle, View.VISIBLE)
            val picks = (o.daily.filter { it.appliesToday && !it.grown } + o.weekly.filter { !it.grown } + o.monthly.filter { !it.grown }).take(2)
            listOf(R.id.chip1, R.id.chip2).forEachIndexed { i, id ->
                val p = picks.getOrNull(i)
                if (p == null) v.setViewVisibility(id, View.INVISIBLE) else {
                    v.setViewVisibility(id, View.VISIBLE)
                    v.setTextViewText(id, "▶ " + p.seed.name)
                    v.setOnClickPendingIntent(id, Notifier.action(ctx, ActionReceiver.START, 92 + i) { putExtra(ActionReceiver.EXTRA_SEED, p.seed.id) })
                }
            }
            v.setTextViewText(R.id.chip3, "More")
            v.setOnClickPendingIntent(R.id.chip3, Notifier.quickSwitch(ctx))
        }
        return v
    }

    private fun todayViews(ctx: Context, o: Overview): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_today)
        v.setOnClickPendingIntent(R.id.root, Notifier.openApp(ctx, code = 95))
        v.setTextViewText(R.id.score, o.score?.toString() ?: "–")
        v.setImageViewBitmap(R.id.ring, ring(ctx, (o.score ?: 0) / 100f))
        v.setTextViewText(
            R.id.summary,
            when {
                o.active != null && o.activeSeed != null -> "● ${o.activeSeed.name}"
                o.dailyCount > 0 -> "${o.grown}/${o.dailyCount} grown · ${Fmt.duration(o.focusMs)}"
                else -> Fmt.duration(o.focusMs) + " today"
            },
        )
        return v
    }

    private fun ring(ctx: Context, progress: Float): Bitmap {
        val size = 260
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
