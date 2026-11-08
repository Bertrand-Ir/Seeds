package app.seeds.domain

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object Fmt {
    private val number: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)
    private val clock24 = DateTimeFormatter.ofPattern("HH:mm")
    private val dayLong = DateTimeFormatter.ofPattern("EEEE, d MMMM")
    private val dayShort = DateTimeFormatter.ofPattern("EEE d MMM")

    /** 2h 10m · 45m · 0m */
    fun duration(ms: Long): String {
        val totalMin = (ms / 60_000).coerceAtLeast(0)
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0L -> "${m}m"
            m == 0L -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    fun minutes(min: Int): String = duration(min * 60_000L)

    /** 1:02:45 or 12:45 */
    fun timer(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    fun money(amount: Long, currency: String = "RWF"): String = "${number.format(amount)} $currency"

    fun signedMoney(amount: Long, inbound: Boolean, currency: String = "RWF"): String =
        (if (inbound) "+" else "−") + money(abs(amount), currency)

    fun compactMoney(amount: Long): String = when {
        abs(amount) >= 1_000_000 -> "%.1fM".format(amount / 1_000_000.0).replace(".0M", "M")
        abs(amount) >= 10_000 -> "${amount / 1000}k"
        else -> number.format(amount)
    }

    fun clock(t: Long, zone: ZoneId = ZoneId.systemDefault()): String = Instant.ofEpochMilli(t).atZone(zone).format(clock24)

    fun clockOfDay(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

    fun dayLong(d: LocalDate): String = d.format(dayLong)
    fun dayShort(d: LocalDate): String = d.format(dayShort)

    fun relativeDay(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(dayShort)
    }
}
