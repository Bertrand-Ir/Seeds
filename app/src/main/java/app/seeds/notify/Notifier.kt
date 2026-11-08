package app.seeds.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.seeds.MainActivity
import app.seeds.R
import app.seeds.data.db.Category
import app.seeds.data.db.Direction
import app.seeds.data.db.Seed
import app.seeds.data.db.Session
import app.seeds.data.db.SessionKind
import app.seeds.data.db.Txn
import app.seeds.domain.Fmt
import app.seeds.domain.Overview
import app.seeds.domain.SeedStat
import app.seeds.tracking.ActionReceiver
import app.seeds.ui.switcher.QuickSwitchActivity
import app.seeds.ui.theme.SeedPalette

object Notifier {
    const val CH_TRACKING = "tracking"
    const val CH_CHECKIN = "checkin"
    const val CH_DRIFT = "drift"
    const val CH_MONEY = "money"
    const val CH_DAILY = "daily"
    const val CH_UPDATES = "updates"

    const val ID_TRACKING = 1
    const val ID_CHECKIN = 2
    const val ID_DRIFT = 3
    const val ID_TRIMMED = 4
    const val ID_MORNING = 5
    const val ID_NIGHT = 6
    const val ID_UPDATE = 7
    private const val ID_MONEY_BASE = 10_000

    fun moneyId(txnId: Long) = ID_MONEY_BASE + (txnId % 50_000).toInt()

    private const val BRAND = 0xFF1E7A4A.toInt()

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        fun ch(id: String, name: String, importance: Int, desc: String) =
            NotificationChannel(id, name, importance).apply { description = desc; setShowBadge(false) }
        nm.createNotificationChannels(
            listOf(
                ch(CH_TRACKING, "Now growing", NotificationManager.IMPORTANCE_LOW, "The live timer for what you are doing right now."),
                ch(CH_CHECKIN, "Check-ins", NotificationManager.IMPORTANCE_HIGH, "Asks if you are still on the same thing."),
                ch(CH_DRIFT, "Drift guard", NotificationManager.IMPORTANCE_HIGH, "Notices when work time turns into scrolling."),
                ch(CH_MONEY, "Money", NotificationManager.IMPORTANCE_HIGH, "Asks what a Mobile Money payment was for."),
                ch(CH_DAILY, "Morning & evening", NotificationManager.IMPORTANCE_DEFAULT, "Your morning plan and nightly report."),
                ch(CH_UPDATES, "Updates", NotificationManager.IMPORTANCE_DEFAULT, "When a new version of Seeds is ready."),
            ),
        )
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    fun post(ctx: Context, id: Int, n: android.app.Notification) {
        if (canPost(ctx)) NotificationManagerCompat.from(ctx).notify(id, n)
    }

    fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)

    // ---- Intents -------------------------------------------------------------------------------

    fun openApp(ctx: Context, route: String? = null, code: Int = 0): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { route?.let { putExtra(MainActivity.EXTRA_ROUTE, it) } }
        return PendingIntent.getActivity(ctx, 3000 + code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun quickSwitch(ctx: Context): PendingIntent {
        val i = Intent(ctx, QuickSwitchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(ctx, 2999, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun action(ctx: Context, action: String, code: Int, build: Intent.() -> Unit = {}): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java).setAction(action).apply(build)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun base(ctx: Context, channel: String) = NotificationCompat.Builder(ctx, channel)
        .setSmallIcon(R.drawable.ic_seeds_mark)
        .setColor(BRAND)

    // ---- Tracking ------------------------------------------------------------------------------

    fun sessionTitle(session: Session, seed: Seed?, appLabel: String?): String = when (session.kind) {
        SessionKind.SEED -> seed?.name ?: "Untitled"
        SessionKind.BREAK -> "Break"
        SessionKind.DRIFT -> "Drift" + (appLabel?.let { " · $it" } ?: "")
    }

    fun tracking(ctx: Context, session: Session, seed: Seed?, stat: SeedStat?, appLabel: String?): android.app.Notification {
        val period = when (seed?.period) {
            app.seeds.data.db.Period.WEEKLY -> "this week"
            app.seeds.data.db.Period.MONTHLY -> "this month"
            else -> "today"
        }
        val text = when {
            stat != null -> "${Fmt.duration(stat.doneMs)} of ${Fmt.duration(stat.targetMs)} $period"
            session.kind == SessionKind.BREAK -> "Rest is part of growth."
            session.kind == SessionKind.DRIFT -> "Counting as drift. Tap Switch when you're back."
            else -> "Growing"
        }
        val b = base(ctx, CH_TRACKING)
            .setContentTitle(sessionTitle(session, seed, appLabel))
            .setContentText(text)
            .setUsesChronometer(true)
            .setWhen(session.startedAt)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openApp(ctx))
            .addAction(0, "Switch", quickSwitch(ctx))
            .addAction(0, "Done", action(ctx, ActionReceiver.STOP, 1))
        seed?.let { b.setColor(SeedPalette.deep(it.color)) }
        if (stat != null && stat.targetMs > 0) b.setProgress(1000, (stat.progress * 1000).toInt(), false)
        return b.build()
    }

    fun guarding(ctx: Context, workEnds: String): android.app.Notification =
        base(ctx, CH_TRACKING)
            .setContentTitle("Work hours · drift guard on")
            .setContentText("Until $workEnds. Seeds will check in if scrolling takes over.")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp(ctx))
            .addAction(0, "Start a seed", quickSwitch(ctx))
            .build()

    fun placeholder(ctx: Context): android.app.Notification =
        base(ctx, CH_TRACKING).setContentTitle("Seeds").setContentText("Getting ready…").setSilent(true).build()

    fun checkIn(ctx: Context, session: Session, title: String, graceMin: Int, autoTrim: Boolean) {
        val running = Fmt.duration(System.currentTimeMillis() - session.startedAt)
        val text = "Running for $running." + if (autoTrim) " No answer in $graceMin min pauses it at ${Fmt.clock(System.currentTimeMillis())}." else ""
        val n = base(ctx, CH_CHECKIN)
            .setContentTitle("Still on $title?")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .addAction(0, "Still on it", action(ctx, ActionReceiver.CONFIRM, 10))
            .addAction(0, "Switched", quickSwitch(ctx))
            .addAction(0, "Done", action(ctx, ActionReceiver.STOP, 11))
            .build()
        post(ctx, ID_CHECKIN, n)
    }

    fun trimmed(ctx: Context, sessionId: Long, title: String, at: Long) {
        val n = base(ctx, CH_CHECKIN)
            .setContentTitle("Paused $title at ${Fmt.clock(at)}")
            .setContentText("No answer, so Seeds stopped counting. Were you still on it?")
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .addAction(0, "I was still on it", action(ctx, ActionReceiver.RESTORE, 20) { putExtra(ActionReceiver.EXTRA_SESSION, sessionId) })
            .addAction(0, "Start something", quickSwitch(ctx))
            .build()
        post(ctx, ID_TRIMMED, n)
    }

    // ---- Drift --------------------------------------------------------------------------------

    fun drift(ctx: Context, appLabel: String, minutes: Int, during: String?) {
        val text = if (during != null) "You started $during. Still on it, or drifting?" else "It's work hours. Still working, or drifting?"
        val n = base(ctx, CH_DRIFT)
            .setContentTitle("$minutes min on $appLabel")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(0xFFD93F2E.toInt())
            .setAutoCancel(true)
            .setTimeoutAfter(20 * 60_000L)
            .addAction(0, "Back to work", action(ctx, ActionReceiver.DRIFT_BACK, 30))
            .addAction(0, "On a break", action(ctx, ActionReceiver.DRIFT_BREAK, 31))
            .addAction(0, "Log as drift", action(ctx, ActionReceiver.DRIFT_LOG, 32))
            .build()
        post(ctx, ID_DRIFT, n)
    }

    fun driftEnded(ctx: Context, appLabel: String, ms: Long, resume: Seed?) {
        val b = base(ctx, CH_DRIFT)
            .setContentTitle("Drifted ${Fmt.duration(ms)} on $appLabel")
            .setContentText(if (resume != null) "Ready to go back to ${resume.name}?" else "Back on track? Pick what's next.")
            .setAutoCancel(true)
            .setContentIntent(quickSwitch(ctx))
        if (resume != null) b.addAction(0, "Resume ${resume.name}", action(ctx, ActionReceiver.START, 40) { putExtra(ActionReceiver.EXTRA_SEED, resume.id) })
        b.addAction(0, "Pick another", quickSwitch(ctx))
        post(ctx, ID_DRIFT, b.build())
    }

    // ---- Money --------------------------------------------------------------------------------

    fun money(ctx: Context, txn: Txn, currency: String, suggestions: List<Category>) {
        val inbound = txn.direction == Direction.IN
        val who = txn.counterparty?.let { if (inbound) " from $it" else " to $it" } ?: ""
        val bal = txn.balanceAfter?.let { " Balance ${Fmt.money(it, currency)}." } ?: ""
        val b = base(ctx, CH_MONEY)
            .setContentTitle(Fmt.signedMoney(txn.amount, inbound, currency) + who)
            .setContentText((if (inbound) "Where did this come from?" else "What was this for?") + bal)
            .setColor(if (inbound) BRAND else 0xFFD9552F.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, "txn/${txn.id}", (txn.id % 1000).toInt()))
        suggestions.take(2).forEachIndexed { i, c ->
            b.addAction(0, c.name, action(ctx, ActionReceiver.LABEL, (5000 + txn.id * 4 + i).toInt()) {
                putExtra(ActionReceiver.EXTRA_TXN, txn.id); putExtra(ActionReceiver.EXTRA_CATEGORY, c.id)
            })
        }
        b.addAction(0, "Other…", openApp(ctx, "txn/${txn.id}", 500 + (txn.id % 1000).toInt()))
        post(ctx, moneyId(txn.id), b.build())
    }

    // ---- Rituals ------------------------------------------------------------------------------

    fun morning(ctx: Context, name: String, o: Overview) {
        val todo = o.daily.count { it.appliesToday } + (o.weekly + o.monthly).count { it.paceTodayMs > 0 && !it.grown }
        val first = o.nextUp?.seed?.name
        val text = "$todo seeds to grow · ${Fmt.duration(o.plannedTodayMs)} planned." + (first?.let { " Start with $it." } ?: "")
        val n = base(ctx, CH_DAILY)
            .setContentTitle(if (name.isBlank()) "Good morning" else "Good morning, $name")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .addAction(0, "Start a seed", quickSwitch(ctx))
            .build()
        post(ctx, ID_MORNING, n)
    }

    fun night(ctx: Context, o: Overview, spentToday: Long, currency: String) {
        val score = o.score?.let { "Your day: $it" } ?: "Your day in Seeds"
        val text = buildString {
            if (o.dailyCount > 0) append("${o.grown} of ${o.dailyCount} seeds grown · ")
            append("${Fmt.duration(o.focusMs)} growing")
            if (o.driftMs > 0) append(" · ${Fmt.duration(o.driftMs)} drift")
            if (spentToday > 0) append(" · spent ${Fmt.money(spentToday, currency)}")
            append(". Take a minute to reflect.")
        }
        val n = base(ctx, CH_DAILY)
            .setContentTitle(score)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, "review", 1))
            .build()
        post(ctx, ID_NIGHT, n)
    }

    fun update(ctx: Context, version: String) {
        val n = base(ctx, CH_UPDATES)
            .setContentTitle("Seeds $version is ready")
            .setContentText("Tap to see what's new and update.")
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, "update", 2))
            .build()
        post(ctx, ID_UPDATE, n)
    }
}
