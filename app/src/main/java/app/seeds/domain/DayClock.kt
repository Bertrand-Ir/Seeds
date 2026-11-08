package app.seeds.domain

import app.seeds.data.db.Period
import app.seeds.data.prefs.Settings
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Your day doesn't end at midnight — it ends when you sleep.
 * A "logical day" starts halfway through your sleep window, so 00:40 still belongs to yesterday
 * if you usually sleep at 23:00 and wake at 06:00.
 */
class DayClock(val settings: Settings, val zone: ZoneId = ZoneId.systemDefault()) {

    /** Minutes after midnight where one logical day turns into the next. */
    val boundaryMin: Int = run {
        val gap = Math.floorMod(settings.wakeMin - settings.sleepMin, 1440)
        (settings.sleepMin + gap / 2) % 1440
    }

    fun logicalDate(t: Long): LocalDate =
        Instant.ofEpochMilli(t).atZone(zone).minusMinutes(boundaryMin.toLong()).toLocalDate()

    fun today(now: Long = System.currentTimeMillis()): LocalDate = logicalDate(now)

    fun dayStart(d: LocalDate): Long =
        d.atStartOfDay(zone).plusMinutes(boundaryMin.toLong()).toInstant().toEpochMilli()

    fun dayEnd(d: LocalDate): Long = dayStart(d.plusDays(1))

    private fun offsetFromStart(minOfDay: Int) = Math.floorMod(minOfDay - boundaryMin, 1440).toLong()

    fun wakeAt(d: LocalDate): Long =
        d.atStartOfDay(zone).plusMinutes(boundaryMin + offsetFromStart(settings.wakeMin)).toInstant().toEpochMilli()

    fun sleepAt(d: LocalDate): Long =
        d.atStartOfDay(zone).plusMinutes(boundaryMin + offsetFromStart(settings.sleepMin)).toInstant().toEpochMilli()

    /** [start, end) as logical dates. */
    fun periodDates(period: Period, d: LocalDate): Pair<LocalDate, LocalDate> = when (period) {
        Period.DAILY -> d to d.plusDays(1)
        Period.WEEKLY -> {
            val s = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            s to s.plusDays(7)
        }
        Period.MONTHLY -> {
            val s = d.withDayOfMonth(1)
            s to s.plusMonths(1)
        }
    }

    fun periodRange(period: Period, d: LocalDate): LongRange {
        val (s, e) = periodDates(period, d)
        return dayStart(s) until dayEnd(e.minusDays(1))
    }
}
