package app.seeds.domain

import app.seeds.data.db.Period
import app.seeds.data.db.Seed
import app.seeds.data.db.Session
import app.seeds.data.db.SessionKind
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MIN = 60_000L

data class SeedStat(
    val seed: Seed,
    /** Time grown in the current period (day / week / month). */
    val doneMs: Long,
    val targetMs: Long,
    val todayMs: Long,
    /** Days left in the period, including today. */
    val daysLeft: Int,
    /** For weekly/monthly seeds: what today needs to stay on pace. */
    val paceTodayMs: Long,
    val appliesToday: Boolean,
) {
    val progress: Float get() = if (targetMs <= 0) 0f else (doneMs.toFloat() / targetMs).coerceIn(0f, 1f)
    val grown: Boolean get() = targetMs in 1..doneMs
    val remainingMs: Long get() = (targetMs - doneMs).coerceAtLeast(0)
}

enum class Growth { SEED, SPROUT, BLOOM }

fun growthOf(progress: Float): Growth = when {
    progress >= 1f -> Growth.BLOOM
    progress > 0f -> Growth.SPROUT
    else -> Growth.SEED
}

data class Overview(
    val day: LocalDate,
    val now: Long,
    val daily: List<SeedStat>,
    val weekly: List<SeedStat>,
    val monthly: List<SeedStat>,
    val score: Int?,
    val grown: Int,
    val dailyCount: Int,
    val focusMs: Long,
    val driftMs: Long,
    val breakMs: Long,
    val wakeAt: Long,
    val sleepAt: Long,
    val todaySessions: List<Session>,
    val active: Session?,
    val activeSeed: Seed?,
) {
    val trackedMs get() = focusMs + driftMs + breakMs
    val plannedTodayMs: Long get() = daily.filter { it.appliesToday }.sumOf { it.targetMs } +
        (weekly + monthly).sumOf { it.paceTodayMs }
    val activeStat: SeedStat? get() = activeSeed?.let { s -> (daily + weekly + monthly).firstOrNull { it.seed.id == s.id } }
    /** The next seed worth starting: an unfinished daily seed, else the most behind weekly/monthly one. */
    val nextUp: SeedStat? get() =
        daily.firstOrNull { it.appliesToday && !it.grown && it.seed.id != activeSeed?.id }
            ?: (weekly + monthly).filter { !it.grown && it.paceTodayMs > it.todayMs && it.seed.id != activeSeed?.id }
                .maxByOrNull { it.paceTodayMs - it.todayMs }
}

object Engine {
    const val DRIFT_KEY = -1L
    const val BREAK_KEY = -2L

    fun overlap(s: Session, from: Long, to: Long, now: Long): Long =
        max(0L, min(min(s.endOr(now), to), now) - max(s.startedAt, from))

    fun keyOf(s: Session): Long = when (s.kind) {
        SessionKind.SEED -> s.seedId ?: DRIFT_KEY
        SessionKind.DRIFT -> DRIFT_KEY
        SessionKind.BREAK -> BREAK_KEY
    }

    /** Totals per key (seed id, or DRIFT/BREAK keys) inside [from, to). */
    fun totals(sessions: List<Session>, from: Long, to: Long, now: Long): Map<Long, Long> {
        val out = HashMap<Long, Long>()
        for (s in sessions) {
            val ms = overlap(s, from, to, now)
            if (ms > 0) out.merge(keyOf(s), ms, Long::plus)
        }
        return out
    }

    /** Per logical day totals across [fromDate, toDate] inclusive. */
    fun dailyTotals(
        sessions: List<Session>, clock: DayClock, fromDate: LocalDate, toDate: LocalDate, now: Long,
    ): Map<LocalDate, Map<Long, Long>> {
        val out = HashMap<LocalDate, HashMap<Long, Long>>()
        val rangeStart = clock.dayStart(fromDate)
        val rangeEnd = clock.dayEnd(toDate)
        for (s in sessions) {
            val start = max(s.startedAt, rangeStart)
            val end = min(min(s.endOr(now), now), rangeEnd)
            if (end <= start) continue
            var d = clock.logicalDate(start)
            while (true) {
                val ds = clock.dayStart(d)
                val de = clock.dayEnd(d)
                val ms = min(end, de) - max(start, ds)
                if (ms > 0) out.getOrPut(d) { HashMap() }.merge(keyOf(s), ms, Long::plus)
                if (de >= end) break
                d = d.plusDays(1)
            }
        }
        return out
    }

    fun dayScore(seeds: List<Seed>, day: LocalDate, clock: DayClock, totals: Map<Long, Long>): Int? {
        val applicable = seeds.filter {
            it.period == Period.DAILY && it.appliesOn(day.dayOfWeek.value) && it.targetMinutes > 0 &&
                clock.logicalDate(it.createdAt) <= day
        }
        if (applicable.isEmpty()) return null
        val avg = applicable.map { s -> min(1f, (totals[s.id] ?: 0L).toFloat() / (s.targetMinutes * MIN)) }.average()
        return (avg * 100).roundToInt()
    }

    fun overview(clock: DayClock, seeds: List<Seed>, sessions: List<Session>, now: Long): Overview {
        val day = clock.today(now)
        val dayFrom = clock.dayStart(day)
        val dayTo = clock.dayEnd(day)
        val today = totals(sessions, dayFrom, dayTo, now)

        fun stat(seed: Seed): SeedStat {
            val (ps, pe) = clock.periodDates(seed.period, day)
            val done = if (seed.period == Period.DAILY) today[seed.id] ?: 0L
            else totals(sessions, clock.dayStart(ps), clock.dayStart(pe), now)[seed.id] ?: 0L
            val target = seed.targetMinutes * MIN
            val daysLeft = ChronoUnit.DAYS.between(day, pe).toInt().coerceAtLeast(1)
            val todayMs = today[seed.id] ?: 0L
            val pace = if (seed.period == Period.DAILY) target
            else {
                // what's left at the start of today, spread evenly over the remaining days
                val leftAtDayStart = (target - (done - todayMs)).coerceAtLeast(0)
                leftAtDayStart / daysLeft
            }
            return SeedStat(seed, done, target, todayMs, daysLeft, pace, seed.appliesOn(day.dayOfWeek.value))
        }

        val stats = seeds.map(::stat)
        val daily = stats.filter { it.seed.period == Period.DAILY }
            .sortedWith(compareByDescending<SeedStat> { it.appliesToday }.thenBy { it.grown }.thenBy { it.seed.sortOrder })
        val applicable = daily.filter { it.appliesToday }
        val active = sessions.lastOrNull { it.isActive }
        return Overview(
            day = day,
            now = now,
            daily = daily,
            weekly = stats.filter { it.seed.period == Period.WEEKLY },
            monthly = stats.filter { it.seed.period == Period.MONTHLY },
            score = dayScore(seeds, day, clock, today),
            grown = applicable.count { it.grown },
            dailyCount = applicable.size,
            focusMs = today.filterKeys { it >= 0 }.values.sum(),
            driftMs = today[DRIFT_KEY] ?: 0L,
            breakMs = today[BREAK_KEY] ?: 0L,
            wakeAt = clock.wakeAt(day),
            sleepAt = clock.sleepAt(day),
            todaySessions = sessions.filter { overlap(it, dayFrom, dayTo, now) > 0 },
            active = active,
            activeSeed = active?.seedId?.let { id -> seeds.firstOrNull { it.id == id } },
        )
    }

    /** Range of sessions the overview needs: from the earliest period start to the end of today. */
    fun overviewRange(clock: DayClock, now: Long): LongRange {
        val day = clock.today(now)
        val weekStart = clock.periodDates(Period.WEEKLY, day).first
        val monthStart = clock.periodDates(Period.MONTHLY, day).first
        val from = if (weekStart < monthStart) weekStart else monthStart
        return clock.dayStart(from) until clock.dayEnd(day)
    }
}

// ---------------------------------------------------------------------------------------------
// Insights — the garden of past days
// ---------------------------------------------------------------------------------------------

data class GardenDay(val date: LocalDate, val score: Int?, val focusMs: Long, val driftMs: Long)

data class SeedTrend(val seed: Seed, val actualMs: Long, val plannedMs: Long, val streak: Int, val best: Int)

data class Insights(
    val garden: List<GardenDay>,
    val trends: List<SeedTrend>,
    val avgScore: Int?,
    val avgFocusMs: Long,
    val totalFocusMs: Long,
    val totalDriftMs: Long,
    val totalBreakMs: Long,
    val awakeMs: Long,
    val bestDay: GardenDay?,
    val reviewedDays: Int,
)

object InsightsEngine {
    const val GARDEN_DAYS = 84

    fun compute(clock: DayClock, seeds: List<Seed>, sessions: List<Session>, now: Long, rangeDays: Int): Insights {
        val today = clock.today(now)
        val gardenFrom = today.minusDays((GARDEN_DAYS - 1).toLong())
        val perDay = Engine.dailyTotals(sessions, clock, gardenFrom, today, now)

        val garden = (0 until GARDEN_DAYS).map { i ->
            val d = gardenFrom.plusDays(i.toLong())
            val t = perDay[d].orEmpty()
            GardenDay(d, Engine.dayScore(seeds, d, clock, t), t.filterKeys { it >= 0 }.values.sum(), t[Engine.DRIFT_KEY] ?: 0)
        }

        val rangeFrom = today.minusDays((rangeDays - 1).toLong())
        val inRange = garden.filter { it.date >= rangeFrom }
        val rangeTotals = HashMap<Long, Long>()
        perDay.filterKeys { it >= rangeFrom }.values.forEach { m -> m.forEach { (k, v) -> rangeTotals.merge(k, v, Long::plus) } }

        val trends = seeds.map { seed ->
            val created = clock.logicalDate(seed.createdAt)
            val daysCounted = inRange.count { it.date >= created && (seed.period != Period.DAILY || seed.appliesOn(it.date.dayOfWeek.value)) }
            val planned = when (seed.period) {
                Period.DAILY -> seed.targetMinutes * MIN * daysCounted
                Period.WEEKLY -> (seed.targetMinutes * MIN * daysCounted / 7.0).toLong()
                Period.MONTHLY -> (seed.targetMinutes * MIN * daysCounted / 30.0).toLong()
            }
            val (streak, best) = if (seed.period == Period.DAILY) streaks(seed, garden.map { it.date }, perDay, today, created) else 0 to 0
            SeedTrend(seed, rangeTotals[seed.id] ?: 0, planned, streak, best)
        }

        val scored = inRange.mapNotNull { it.score }
        val totalFocus = rangeTotals.filterKeys { it >= 0 }.values.sum()
        val awakePerDay = Math.floorMod(clock.settings.sleepMin - clock.settings.wakeMin, 1440) * MIN
        return Insights(
            garden = garden,
            trends = trends,
            avgScore = if (scored.isEmpty()) null else scored.average().roundToInt(),
            avgFocusMs = totalFocus / rangeDays,
            totalFocusMs = totalFocus,
            totalDriftMs = rangeTotals[Engine.DRIFT_KEY] ?: 0,
            totalBreakMs = rangeTotals[Engine.BREAK_KEY] ?: 0,
            awakeMs = awakePerDay * rangeDays,
            bestDay = inRange.filter { it.score != null }.maxWithOrNull(compareBy<GardenDay> { it.score }.thenBy { it.focusMs }),
            reviewedDays = 0,
        )
    }

    private fun streaks(
        seed: Seed, days: List<LocalDate>, perDay: Map<LocalDate, Map<Long, Long>>, today: LocalDate, created: LocalDate,
    ): Pair<Int, Int> {
        val target = seed.targetMinutes * MIN
        fun grown(d: LocalDate) = (perDay[d]?.get(seed.id) ?: 0L) >= target
        var best = 0
        var run = 0
        for (d in days) {
            if (d < created || !seed.appliesOn(d.dayOfWeek.value)) continue
            if (grown(d)) { run++; best = max(best, run) } else if (d != today) run = 0
        }
        // current streak: walk back from today; an unfinished today doesn't break it
        var current = 0
        var d = today
        while (d >= created && d >= days.first()) {
            if (seed.appliesOn(d.dayOfWeek.value)) {
                if (grown(d)) current++ else if (d != today) break
            }
            d = d.minusDays(1)
        }
        return current to max(best, current)
    }
}
