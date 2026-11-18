package app.seeds.tracking

import android.content.Context
import android.content.Intent
import app.seeds.data.db.Session
import app.seeds.data.db.SessionKind
import app.seeds.notify.Notifier
import app.seeds.seeds

/** What you can answer when Seeds catches a long stint in a distracting app. */
object DriftActions {
    private fun since(now: Long) = DriftState.currentSince.takeIf { it > 0 } ?: now

    /** Honest: log it as drift from when the stint began. It ends by itself when you leave the app. */
    suspend fun drifting(ctx: Context) {
        val now = System.currentTimeMillis()
        val t = ctx.seeds.tracking
        val active = t.activeNow()
        if (active?.kind == SessionKind.DRIFT) return
        DriftState.resumeSeedId = active?.takeIf { it.kind == SessionKind.SEED }?.seedId ?: DriftState.resumeSeedId
        t.start(null, SessionKind.DRIFT, at = since(now), source = Session.SOURCE_DRIFT, appPackage = DriftState.currentPkg)
        DriftState.snoozeUntil = now + 15 * 60_000L
        DriftState.driftsToday++
        Notifier.cancel(ctx, Notifier.ID_DRIFT)
    }

    /** It's work — leave me alone for a while. */
    fun research(ctx: Context) {
        DriftState.snoozeUntil = System.currentTimeMillis() + 30 * 60_000L
        Notifier.cancel(ctx, Notifier.ID_DRIFT)
    }

    /** A deliberate break: rest, not drift. */
    suspend fun breakTime(ctx: Context) {
        val now = System.currentTimeMillis()
        val t = ctx.seeds.tracking
        DriftState.resumeSeedId = t.activeNow()?.takeIf { it.kind == SessionKind.SEED }?.seedId ?: DriftState.resumeSeedId
        t.start(null, SessionKind.BREAK, at = since(now))
        DriftState.snoozeUntil = now + 30 * 60_000L
        Notifier.cancel(ctx, Notifier.ID_DRIFT)
    }

    /** Closing it — just give a short grace period. */
    fun closing(ctx: Context) {
        DriftState.snoozeUntil = System.currentTimeMillis() + 5 * 60_000L
        Notifier.cancel(ctx, Notifier.ID_DRIFT)
    }

    /** After admitting drift: start the seed you left (if any) and leave the app. */
    suspend fun backTo(ctx: Context, seedId: Long?) {
        if (seedId != null) ctx.seeds.tracking.start(seedId) else ctx.seeds.tracking.stop()
        DriftState.resumeSeedId = null
        goHome(ctx)
    }

    fun goHome(ctx: Context) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
