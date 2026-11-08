package app.seeds.tracking

import android.content.Context
import app.seeds.domain.DayClock
import app.seeds.domain.Engine
import app.seeds.domain.Overview
import app.seeds.notify.Alarms
import app.seeds.seeds
import app.seeds.widget.Widgets

/** Side effects that must follow any change to what's being tracked. */
object Effects {
    suspend fun refresh(ctx: Context) {
        val c = ctx.seeds
        val s = c.settings.current()
        val active = c.tracking.activeNow()
        Alarms.scheduleCheckIn(ctx, active, s)
        if (active != null || TrackerService.shouldGuard(ctx, s)) TrackerService.start(ctx)
        Widgets.updateAll(ctx)
    }

    /** One-shot overview for notifications and widgets. */
    suspend fun overview(ctx: Context, now: Long = System.currentTimeMillis()): Overview {
        val c = ctx.seeds
        val clock = DayClock(c.settings.current())
        val range = Engine.overviewRange(clock, now)
        return Engine.overview(clock, c.tracking.activeSeeds(), c.tracking.sessionsNow(range.first, range.last + 1), now)
    }
}
