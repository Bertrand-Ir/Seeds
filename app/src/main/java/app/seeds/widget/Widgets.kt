package app.seeds.widget

import android.app.PendingIntent
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
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import app.seeds.R
import app.seeds.data.db.Period
import app.seeds.data.db.SessionKind
import app.seeds.data.db.Txn
import app.seeds.domain.Fmt
import app.seeds.domain.Overview
import app.seeds.domain.SeedStat
import app.seeds.notify.Notifier
import app.seeds.seeds
import app.seeds.tracking.ActionReceiver
import app.seeds.tracking.AppLabels
import app.seeds.tracking.Effects
import app.seeds.ui.money.MoneyPromptActivity
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
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        Widgets.refreshAsync(context, goAsync())
}

/**
 * Widget design: one card, three sizes, no dead space. While something grows, the whole card
 * takes the seed's color — the same language as the Now card in the app.
 */
object Widgets {
    private enum class Size { SMALL, MEDIUM, LARGE }

    private const val INK = 0xFF0E1210.toInt()
    private const val INK_MUTED = 0xB30E1210.toInt()
    private const val INK_SOFT = 0x260E1210

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

        for (id in nowIds) {
            val views = if (Build.VERSION.SDK_INT >= 31) {
                // The launcher picks the biggest layout that fits — the card is always full.
                RemoteViews(
                    mapOf(
                        SizeF(100f, 80f) to now(ctx, o, unlabeled, Size.SMALL),
                        SizeF(220f, 100f) to now(ctx, o, unlabeled, Size.MEDIUM),
                        SizeF(220f, 250f) to now(ctx, o, unlabeled, Size.LARGE),
                    ),
                )
            } else {
                val opts = mgr.getAppWidgetOptions(id)
                val w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                val h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                now(ctx, o, unlabeled, when { w in 1..219 -> Size.SMALL; h >= 250 -> Size.LARGE; else -> Size.MEDIUM })
            }
            mgr.updateAppWidget(id, views)
        }
        if (todayIds.isNotEmpty()) mgr.updateAppWidget(todayIds, today(ctx, o, unlabeled))
    }

    private fun moneyPopup(ctx: Context, txn: Txn): PendingIntent = PendingIntent.getActivity(
        ctx, 96, MoneyPromptActivity.intent(ctx, txn.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun startSeed(ctx: Context, seedId: Long, code: Int) =
        Notifier.action(ctx, ActionReceiver.START, code) { putExtra(ActionReceiver.EXTRA_SEED, seedId) }

    // ------------------------------------------------------------------------------------------
    // Now
    // ------------------------------------------------------------------------------------------

    private fun now(ctx: Context, o: Overview, unlabeled: List<Txn>, size: Size): RemoteViews {
        val layout = when (size) { Size.SMALL -> R.layout.widget_now_small; Size.MEDIUM -> R.layout.widget_now; Size.LARGE -> R.layout.widget_now_large }
        val v = RemoteViews(ctx.packageName, layout)
        v.setOnClickPendingIntent(android.R.id.background, Notifier.openApp(ctx, code = 90))

        val active = o.active
        val seed = o.activeSeed
        val fill = when {
            active == null -> null
            active.kind == SessionKind.SEED -> seed?.let { SeedPalette.fill(it.color) } ?: SeedPalette.fill(0)
            active.kind == SessionKind.BREAK -> 0xFF8CCBFF.toInt()
            else -> 0xFFFF8A75.toInt()
        }
        val surface = ContextCompat.getColor(ctx, R.color.widget_bg)
        val ink = fill?.let { INK } ?: ContextCompat.getColor(ctx, R.color.widget_ink)
        val muted = fill?.let { INK_MUTED } ?: ContextCompat.getColor(ctx, R.color.widget_muted)
        val accent = fill?.let { INK } ?: ContextCompat.getColor(ctx, R.color.widget_accent)

        v.setInt(R.id.bg, "setColorFilter", fill ?: surface)
        v.setInt(R.id.mark, "setColorFilter", accent)
        v.setTextColor(R.id.seed_name, ink)
        v.setTextColor(R.id.timer, ink)
        v.setTextColor(R.id.idle_big, ink)

        if (size != Size.SMALL) {
            v.setTextColor(R.id.label, muted)
            v.setTextColor(R.id.score, ink)
            v.setTextColor(R.id.detail, muted)
            v.setTextViewText(R.id.score, o.score?.let { "$it" } ?: "")
            if (unlabeled.isNotEmpty()) {
                v.setViewVisibility(R.id.money, View.VISIBLE)
                v.setTextViewText(R.id.money, "${unlabeled.size} MoMo to label")
                v.setOnClickPendingIntent(R.id.money, moneyPopup(ctx, unlabeled.first()))
            } else v.setViewVisibility(R.id.money, View.GONE)
        }

        if (active != null) {
            val title = when (active.kind) {
                SessionKind.SEED -> seed?.name ?: "Untitled"
                SessionKind.BREAK -> "On a break"
                SessionKind.DRIFT -> "Drifting" + (active.appPackage?.let { " · " + AppLabels.label(ctx, it) } ?: "")
            }
            v.setTextViewText(R.id.seed_name, title)
            v.setViewVisibility(R.id.timer, View.VISIBLE)
            v.setViewVisibility(R.id.idle_big, View.GONE)
            v.setChronometer(R.id.timer, SystemClock.elapsedRealtime() - (System.currentTimeMillis() - active.startedAt), null, true)
            if (size == Size.SMALL) {
                v.setTextViewText(R.id.btn_main, "Done")
                v.setInt(R.id.btn_main, "setBackgroundResource", R.drawable.widget_pill_ink)
                v.setTextColor(R.id.btn_main, 0xFFFFFFFF.toInt())
                v.setOnClickPendingIntent(R.id.btn_main, Notifier.action(ctx, ActionReceiver.STOP, 91))
            } else {
                v.setTextViewText(R.id.label, "NOW GROWING")
                v.setViewVisibility(R.id.progress, View.GONE)
                val stat = o.activeStat
                if (stat != null && stat.targetMs > 0) {
                    v.setViewVisibility(R.id.progress_ink, View.VISIBLE)
                    v.setProgressBar(R.id.progress_ink, 1000, (stat.progress * 1000).toInt(), false)
                    val per = when (stat.seed.period) { Period.DAILY -> "today"; Period.WEEKLY -> "this week"; Period.MONTHLY -> "this month" }
                    v.setTextViewText(
                        R.id.detail,
                        "${Fmt.duration(stat.doneMs)} of ${Fmt.duration(stat.targetMs)} $per" +
                            if (stat.grown) " · grown ✓" else " · ${Fmt.duration(stat.remainingMs)} to go",
                    )
                } else {
                    v.setViewVisibility(R.id.progress_ink, View.INVISIBLE)
                    v.setTextViewText(R.id.detail, "Since ${Fmt.clock(active.startedAt)}")
                }
                v.setViewVisibility(R.id.actions_active, View.VISIBLE)
                v.setViewVisibility(R.id.actions_idle, View.GONE)
                v.setOnClickPendingIntent(R.id.btn_switch, Notifier.quickSwitch(ctx))
                v.setOnClickPendingIntent(R.id.btn_stop, Notifier.action(ctx, ActionReceiver.STOP, 91))
            }
        } else {
            val next = o.nextUp
            v.setViewVisibility(R.id.timer, View.GONE)
            v.setViewVisibility(R.id.idle_big, View.VISIBLE)
            if (size == Size.SMALL) {
                v.setTextViewText(R.id.seed_name, "Seeds")
                v.setTextViewText(R.id.idle_big, o.score?.let { "Day $it · ${o.grown}/${o.dailyCount}" } ?: "Nothing growing")
                v.setInt(R.id.btn_main, "setBackgroundResource", R.drawable.widget_btn_accent)
                v.setTextColor(R.id.btn_main, ContextCompat.getColor(ctx, R.color.widget_on_accent))
                if (next != null) {
                    v.setTextViewText(R.id.btn_main, "▶ ${next.seed.name}")
                    v.setOnClickPendingIntent(R.id.btn_main, startSeed(ctx, next.seed.id, 92))
                } else {
                    v.setTextViewText(R.id.btn_main, "Start")
                    v.setOnClickPendingIntent(R.id.btn_main, Notifier.quickSwitch(ctx))
                }
            } else {
                v.setTextViewText(R.id.label, "NOTHING GROWING")
                v.setTextViewText(R.id.seed_name, next?.let { "Next up: ${it.seed.name}" } ?: "All grown today")
                v.setTextViewText(R.id.idle_big, if (o.dailyCount > 0) "${o.grown} of ${o.dailyCount} grown" else Fmt.duration(o.focusMs))
                v.setViewVisibility(R.id.progress_ink, View.GONE)
                v.setViewVisibility(R.id.progress, View.VISIBLE)
                v.setProgressBar(R.id.progress, 1000, if (o.dailyCount == 0) 0 else o.grown * 1000 / o.dailyCount, false)
                v.setTextViewText(R.id.detail, "${Fmt.duration(o.focusMs)} growing" + if (o.driftMs > 0) " · ${Fmt.duration(o.driftMs)} drift" else "")
                v.setViewVisibility(R.id.actions_active, View.GONE)
                v.setViewVisibility(R.id.actions_idle, View.VISIBLE)
                val picks = (o.daily.filter { it.appliesToday && !it.grown } + o.weekly.filter { !it.grown } + o.monthly.filter { !it.grown })
                val chipIds = if (size == Size.LARGE) listOf(R.id.chip1) else listOf(R.id.chip1, R.id.chip2)
                chipIds.forEachIndexed { i, id ->
                    val p = picks.getOrNull(i)
                    if (p == null) v.setViewVisibility(id, View.INVISIBLE) else {
                        v.setViewVisibility(id, View.VISIBLE)
                        v.setTextViewText(id, "▶  " + p.seed.name)
                        v.setOnClickPendingIntent(id, startSeed(ctx, p.seed.id, 92 + i))
                    }
                }
                v.setOnClickPendingIntent(R.id.chip3, Notifier.quickSwitch(ctx))
            }
        }

        if (size == Size.LARGE) seedRows(ctx, v, o, ink, muted, fill != null)
        return v
    }

    /** Tall widget: today's seeds with progress, each one tap to start. */
    private fun seedRows(ctx: Context, v: RemoteViews, o: Overview, ink: Int, muted: Int, onColor: Boolean) {
        v.setTextColor(R.id.list_label, muted)
        val rows: List<SeedStat> = (o.daily.filter { it.appliesToday } + o.weekly + o.monthly)
            .filter { it.seed.id != o.activeSeed?.id }
            .sortedBy { it.grown }
            .take(4)
        v.setTextViewText(R.id.list_label, if (o.dailyCount > 0) "TODAY · ${o.grown}/${o.dailyCount} GROWN" else "YOUR SEEDS")
        val ids = listOf(
            Row(R.id.r1, R.id.r1_dot, R.id.r1_name, R.id.r1_val, R.id.r1_bar),
            Row(R.id.r2, R.id.r2_dot, R.id.r2_name, R.id.r2_val, R.id.r2_bar),
            Row(R.id.r3, R.id.r3_dot, R.id.r3_name, R.id.r3_val, R.id.r3_bar),
            Row(R.id.r4, R.id.r4_dot, R.id.r4_name, R.id.r4_val, R.id.r4_bar),
        )
        ids.forEachIndexed { i, r ->
            val st = rows.getOrNull(i)
            if (st == null) { v.setViewVisibility(r.row, View.INVISIBLE); return@forEachIndexed }
            v.setViewVisibility(r.row, View.VISIBLE)
            val tone = if (onColor) INK else SeedPalette.fill(st.seed.color)
            v.setInt(r.dot, "setColorFilter", tone)
            v.setTextViewText(r.name, st.seed.name)
            v.setTextColor(r.name, ink)
            v.setTextViewText(r.value, if (st.grown) "✓ ${Fmt.duration(st.doneMs)}" else "${Fmt.duration(st.doneMs)} / ${Fmt.duration(st.targetMs)}")
            v.setTextColor(r.value, muted)
            v.setProgressBar(r.bar, 1000, (st.progress * 1000).toInt(), false)
            if (Build.VERSION.SDK_INT >= 31) {
                v.setColorStateList(r.bar, "setProgressTintList", ColorStateList.valueOf(tone))
                v.setColorStateList(r.bar, "setProgressBackgroundTintList", ColorStateList.valueOf(if (onColor) INK_SOFT else ContextCompat.getColor(ctx, R.color.widget_track)))
            }
            v.setOnClickPendingIntent(r.row, startSeed(ctx, st.seed.id, 120 + i))
        }
    }

    private data class Row(val row: Int, val dot: Int, val name: Int, val value: Int, val bar: Int)

    // ------------------------------------------------------------------------------------------
    // Today
    // ------------------------------------------------------------------------------------------

    private fun today(ctx: Context, o: Overview, unlabeled: List<Txn>): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_today)
        v.setOnClickPendingIntent(android.R.id.background, Notifier.openApp(ctx, "review", 95))
        v.setInt(R.id.bg, "setColorFilter", ContextCompat.getColor(ctx, R.color.widget_bg))
        v.setTextViewText(R.id.score, o.score?.toString() ?: "–")
        v.setImageViewBitmap(R.id.ring, ring(ctx, (o.score ?: 0) / 100f))
        v.setTextViewText(
            R.id.summary,
            when {
                o.active != null && o.activeSeed != null -> "● ${o.activeSeed.name}"
                o.dailyCount > 0 -> "${o.grown} of ${o.dailyCount} grown"
                else -> Fmt.duration(o.focusMs) + " growing"
            },
        )
        if (unlabeled.isNotEmpty()) {
            v.setTextViewText(R.id.summary2, "● ${unlabeled.size} MoMo to label")
            v.setOnClickPendingIntent(R.id.summary2, moneyPopup(ctx, unlabeled.first()))
        } else {
            v.setTextViewText(R.id.summary2, "${Fmt.duration(o.focusMs)} growing" + if (o.driftMs > 0) " · ${Fmt.duration(o.driftMs)} drift" else "")
        }
        return v
    }

    private fun ring(ctx: Context, progress: Float): Bitmap {
        val size = 300
        val stroke = 26f
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
