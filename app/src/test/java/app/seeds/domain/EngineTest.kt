package app.seeds.domain

import app.seeds.data.db.Period
import app.seeds.data.db.Seed
import app.seeds.data.db.Session
import app.seeds.data.db.SessionKind
import app.seeds.data.prefs.Settings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class EngineTest {
    private val zone = ZoneId.of("Africa/Kigali")
    private val settings = Settings(wakeMin = 6 * 60, sleepMin = 23 * 60)
    private val clock = DayClock(settings, zone)
    private fun at(d: String, t: String) = LocalDateTime.parse("${d}T$t").atZone(zone).toInstant().toEpochMilli()

    @Test fun logicalDayEndsAtSleepNotMidnight() {
        // boundary is halfway between 23:00 and 06:00 -> 02:30
        assertEquals(150, clock.boundaryMin)
        assertEquals(LocalDate.parse("2026-11-02"), clock.logicalDate(at("2026-11-03", "00:40")))
        assertEquals(LocalDate.parse("2026-11-03"), clock.logicalDate(at("2026-11-03", "03:00")))
    }

    @Test fun scoreAndPace() {
        val created = at("2026-10-01", "08:00")
        val deep = Seed(id = 1, name = "Deep", icon = "laptop", color = 0, targetMinutes = 120, period = Period.DAILY, createdAt = created)
        val read = Seed(id = 2, name = "Read", icon = "book", color = 1, targetMinutes = 30, period = Period.DAILY, createdAt = created)
        val learn = Seed(id = 3, name = "Learn", icon = "school", color = 2, targetMinutes = 7 * 60, period = Period.WEEKLY, createdAt = created)
        // Wed 4 Nov 2026
        val sessions = listOf(
            Session(1, 1, SessionKind.SEED, at("2026-11-04", "08:00"), at("2026-11-04", "09:00")), // 60 of 120
            Session(2, 2, SessionKind.SEED, at("2026-11-04", "21:00"), at("2026-11-04", "21:45")), // 45 of 30 -> capped
            Session(3, 3, SessionKind.SEED, at("2026-11-02", "18:00"), at("2026-11-02", "19:00")), // Monday: 60 of 420
            Session(4, null, SessionKind.DRIFT, at("2026-11-04", "10:00"), at("2026-11-04", "10:20")),
        )
        val now = at("2026-11-04", "22:00")
        val o = Engine.overview(clock, listOf(deep, read, learn), sessions, now)
        assertEquals(75, o.score)                         // (0.5 + 1.0) / 2
        assertEquals(1, o.grown)
        assertEquals(20 * 60_000L, o.driftMs)
        val w = o.weekly.single()
        assertEquals(60 * 60_000L, w.doneMs)
        assertEquals(5, w.daysLeft)                       // Wed..Sun
        assertEquals((360 / 5) * 60_000L, w.paceTodayMs)  // 6h left over 5 days
    }

    @Test fun sessionAcrossBoundarySplits() {
        val s = Session(1, 1, SessionKind.SEED, at("2026-11-03", "02:00"), at("2026-11-03", "03:00"))
        val totals = Engine.dailyTotals(listOf(s), clock, LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-03"), at("2026-11-04", "00:00"))
        assertEquals(30 * 60_000L, totals[LocalDate.parse("2026-11-02")]!![1L])
        assertEquals(30 * 60_000L, totals[LocalDate.parse("2026-11-03")]!![1L])
    }
}
