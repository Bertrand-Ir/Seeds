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
import app.seeds.domain.DaySlot
import app.seeds.domain.Fmt
import app.seeds.domain.PlanFocus
import app.seeds.domain.Overview
import app.seeds.notify.Notifier
import app.seeds.tracking.ActionReceiver
import app.seeds.tracking.Effects
import app.seeds.ui.theme.SeedPalette
import android.app.PendingIntent
import app.seeds.data.db.Txn
import app.seeds.seeds
import app.seeds.ui.money.MoneyPromptActivity
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
        val unlabeled = runCatching { ctx.seeds.money.unlabeledNow() }.getOrDefault(emptyList())
        // What the plan says for this part of the day — shown instead of stats whenever nothing is running.
        val focus = runCatching { PlanFocus.of(ctx.seeds.plan.forDate(o.day.toString()), DaySlot.now()) }.getOrNull()

        for (id in nowIds) {
            // Narrow → compact. Very short (4×1) → one row. Short → buttons under the timer. Tall → standard.
            val views = if (Build.VERSION.SDK_INT >= 31) {
                RemoteViews(
                    mapOf(
                        SizeF(100f, 48f) to now(ctx, o, unlabeled, focus, Mode.ROW),
                        SizeF(100f, 120f) to small(ctx, o, focus),
                        SizeF(180f, 48f) to now(ctx, o, unlabeled, focus, Mode.ROW),
                        SizeF(180f, 104f) to now(ctx, o, unlabeled, focus, Mode.MEDIUM),
                        SizeF(180f, 200f) to now(ctx, o, unlabeled, focus, Mode.FULL),
                    ),
                )
            } else {
                val opts = mgr.getAppWidgetOptions(id)
                val w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                val h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                when {
                    w in 1..179 && h in 1..119 -> now(ctx, o, unlabeled, focus, Mode.ROW)
                    w in 1..179 -> small(ctx, o, focus)
                    h in 1..103 -> now(ctx, o, unlabeled, focus, Mode.ROW)
                    h in 104..199 -> now(ctx, o, unlabeled, focus, Mode.MEDIUM)
                    else -> now(ctx, o, unlabeled, focus, Mode.FULL)
                }
            }
            mgr.updateAppWidget(id, views)
        }
        if (todayIds.isNotEmpty()) mgr.updateAppWidget(todayIds, today(ctx, o, focus))
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

    /** FULL (tall), MEDIUM (buttons under the timer) and ROW (4×1, one line) share view ids. */
    private enum class Mode { ROW, MEDIUM, FULL }

    /** Only FULL has the eyebrow label and progress bars; MEDIUM and ROW simply skip them. */
    private fun now(ctx: Context, o: Overview, unlabeled: List<Txn>, focus: PlanFocus?, mode: Mode): RemoteViews {
        val layout = when (mode) { Mode.ROW -> R.layout.widget_now_wide; Mode.MEDIUM -> R.layout.widget_now_medium; Mode.FULL -> R.layout.widget_now }
        val v = RemoteViews(ctx.packageName, layout)
        val slim = mode != Mode.FULL
        val tiny = mode == Mode.ROW
        val l = look(ctx, o)
        base(v, l)
        v.setOnClickPendingIntent(android.R.id.background, Notifier.openApp(ctx, code = 90))
        if (!slim) v.setTextColor(R.id.label, l.muted)
        v.setTextColor(R.id.seed_name, l.ink)
        v.setTextColor(R.id.timer, l.ink)
        v.setTextColor(R.id.idle_big, l.ink)
        v.setTextColor(R.id.detail, l.muted)

        // MoMo payments waiting for a label — the little yellow pill, top right.
        if (unlabeled.isNotEmpty()) {
            v.setViewVisibility(R.id.money, View.VISIBLE)
            v.setTextViewText(R.id.money, if (tiny) "${unlabeled.size} MoMo" else "${unlabeled.size} MoMo to label")
            v.setOnClickPendingIntent(
                R.id.money,
                PendingIntent.getActivity(ctx, 96, MoneyPromptActivity.intent(ctx, unlabeled.first().id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
        } else v.setViewVisibility(R.id.money, View.GONE)

        val a = o.active
        val planMode = a == null && focus != null
        v.setViewVisibility(R.id.now_box, if (planMode) View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.plan_flipper, if (planMode) View.VISIBLE else View.GONE)
        if (planMode && focus != null) {
            when (mode) {
                Mode.FULL -> v.setTextViewText(R.id.label, focus.label.uppercase())
                Mode.MEDIUM -> v.setTextViewText(R.id.seed_name, focus.label)
                Mode.ROW -> Unit
            }
            if (mode == Mode.MEDIUM) v.setTextColor(R.id.seed_name, l.muted)
            if (mode == Mode.ROW) {
                v.setViewVisibility(R.id.actions_active, View.GONE)
                v.setViewVisibility(R.id.actions_idle, View.GONE)
            }
            val child = when (mode) { Mode.FULL -> R.layout.widget_plan_full; Mode.MEDIUM -> R.layout.widget_plan_medium; Mode.ROW -> R.layout.widget_plan_row }
            fillPlan(ctx, v, focus, child, withLabel = mode == Mode.ROW)
            return v
        }
        if (a != null) {
            if (!slim) v.setTextViewText(R.id.label, "NOW")
            v.setTextViewText(R.id.seed_name, title(o))
            v.setViewVisibility(R.id.timer, View.VISIBLE)
            v.setViewVisibility(R.id.idle_big, View.GONE)
            v.setChronometer(R.id.timer, SystemClock.elapsedRealtime() - (System.currentTimeMillis() - a.startedAt), null, true)
            if (!slim) v.setViewVisibility(R.id.progress, View.GONE)
            val st = o.activeStat
            if (st != null && st.targetMs > 0) {
                if (!slim) {
                    v.setViewVisibility(R.id.progress_ink, View.VISIBLE)
                    v.setProgressBar(R.id.progress_ink, 1000, (st.progress * 1000).toInt(), false)
                }
                val per = when (st.seed.period) { Period.DAILY -> "today"; Period.WEEKLY -> "this week"; Period.MONTHLY -> "this month" }
                v.setTextViewText(R.id.detail, if (st.grown) "Grown ✓" else if (tiny) "${Fmt.duration(st.remainingMs)} left" else "${Fmt.duration(st.remainingMs)} left $per")
            } else {
                if (!slim) v.setViewVisibility(R.id.progress_ink, View.GONE)
                v.setTextViewText(R.id.detail, "since ${Fmt.clock(a.startedAt)}")
            }
            v.setViewVisibility(R.id.actions_active, View.VISIBLE)
            v.setViewVisibility(R.id.actions_idle, View.GONE)
            v.setOnClickPendingIntent(R.id.btn_switch, Notifier.quickSwitch(ctx))
            v.setOnClickPendingIntent(R.id.btn_stop, Notifier.action(ctx, ActionReceiver.STOP, 91))
        } else {
            if (!slim) v.setTextViewText(R.id.label, "TODAY")
            v.setTextViewText(R.id.seed_name, "Nothing growing")
            v.setViewVisibility(R.id.timer, View.GONE)
            v.setViewVisibility(R.id.idle_big, View.VISIBLE)
            if (!slim) v.setViewVisibility(R.id.progress_ink, View.GONE)
            if (o.dailyCount > 0) {
                v.setTextViewText(R.id.idle_big, "${o.grown} of ${o.dailyCount}")
                if (!slim) {
                    v.setViewVisibility(R.id.progress, View.VISIBLE)
                    v.setProgressBar(R.id.progress, 1000, o.grown * 1000 / o.dailyCount, false)
                }
                v.setTextViewText(R.id.detail, if (tiny) "grown" else "seeds grown · ${Fmt.duration(o.focusMs)} so far")
            } else {
                v.setTextViewText(R.id.idle_big, Fmt.duration(o.focusMs))
                if (!slim) v.setViewVisibility(R.id.progress, View.GONE)
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

    private fun small(ctx: Context, o: Overview, focus: PlanFocus?): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_now_small)
        val l = look(ctx, o)
        base(v, l)
        v.setOnClickPendingIntent(android.R.id.background, Notifier.openApp(ctx, code = 90))
        v.setTextColor(R.id.seed_name, l.ink)
        v.setTextColor(R.id.timer, l.ink)
        v.setTextColor(R.id.idle_big, l.ink)
        val a = o.active
        val planMode = a == null && focus != null
        v.setViewVisibility(R.id.now_box, if (planMode) View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.btn_main, if (planMode) View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.plan_flipper, if (planMode) View.VISIBLE else View.GONE)
        if (planMode && focus != null) {
            v.setTextViewText(R.id.seed_name, focus.label)
            v.setTextColor(R.id.seed_name, l.muted)
            fillPlan(ctx, v, focus, R.layout.widget_plan_small, withLabel = false)
            return v
        }
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

    /**
     * One card per planned item, rotated by the ViewFlipper every few seconds. Each card carries its own
     * buttons: a planned seed gets ▶ Start (plus ✓ to tick it off); a plain task gets ✓ Done.
     */
    private fun fillPlan(ctx: Context, v: RemoteViews, focus: PlanFocus, layout: Int, withLabel: Boolean) {
        v.removeAllViews(R.id.plan_flipper)
        val n = focus.items.size
        focus.items.take(MAX_PLAN_CARDS).forEachIndexed { i, item ->
            val card = RemoteViews(ctx.packageName, layout)
            card.setTextViewText(R.id.plan_title, item.title)
            val count = if (n > 1) "${i + 1} of $n" else ""
            card.setTextViewText(
                R.id.plan_sub,
                when {
                    withLabel -> listOf(focus.label, count).filter { it.isNotEmpty() }.joinToString(" · ")
                    else -> count
                },
            )
            val done = Notifier.action(ctx, ActionReceiver.PLAN_DONE, RC_PLAN_DONE + i) { putExtra(ActionReceiver.EXTRA_PLAN, item.id) }
            val seedId = item.seedId
            if (seedId != null) {
                card.setTextViewText(R.id.plan_primary, "▶  Start")
                card.setOnClickPendingIntent(R.id.plan_primary, startSeed(ctx, seedId, RC_PLAN_START + i))
                card.setTextViewText(R.id.plan_secondary, "✓")
                card.setOnClickPendingIntent(R.id.plan_secondary, done)
            } else {
                card.setTextViewText(R.id.plan_primary, "✓  Done")
                card.setOnClickPendingIntent(R.id.plan_primary, done)
                card.setTextViewText(R.id.plan_secondary, "Other")
                card.setOnClickPendingIntent(R.id.plan_secondary, Notifier.quickSwitch(ctx))
            }
            v.addView(R.id.plan_flipper, card)
        }
    }

    private const val MAX_PLAN_CARDS = 8
    private const val RC_PLAN_START = 3000
    private const val RC_PLAN_DONE = 3100

    // ------------------------------------------------------------------------------------------

    private fun today(ctx: Context, o: Overview, focus: PlanFocus?): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_today)
        v.setOnClickPendingIntent(android.R.id.background, Notifier.openApp(ctx, "review", 95))
        v.setInt(R.id.bg, "setColorFilter", ContextCompat.getColor(ctx, R.color.widget_bg))
        v.setTextViewText(R.id.score, o.score?.toString() ?: "–")
        v.setImageViewBitmap(R.id.ring, ring(ctx, (o.score ?: 0) / 100f))
        v.setTextViewText(R.id.summary, if (o.dailyCount > 0) "${o.grown} of ${o.dailyCount} grown" else "${Fmt.duration(o.focusMs)} growing")
        val line = o.activeSeed?.let { "● ${it.name}" } ?: focus?.let { "${it.label}: ${it.items.first().title}" }
        v.setTextViewText(R.id.summary2, line ?: "")
        v.setViewVisibility(R.id.summary2, if (line != null) View.VISIBLE else View.GONE)
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
