package app.seeds.domain

import app.seeds.data.db.PlanItem
import java.time.LocalDateTime

/** The five windows of a day. Plans live in windows, not minutes. */
enum class DaySlot(val label: String, val hint: String, val startMin: Int, val phrase: String) {
    MORNING("Morning", "from waking", 0, "This morning"),
    NOON("Noon", "around midday", 11 * 60 + 30, "Around noon"),
    AFTERNOON("Afternoon", "after lunch", 14 * 60, "This afternoon"),
    EVENING("Evening", "after work", 17 * 60 + 30, "This evening"),
    NIGHT("Night", "before bed", 21 * 60, "Tonight");

    companion object {
        /** Small hours (before 04:00) still belong to the night before. */
        const val SMALL_HOURS_END = 4 * 60

        fun at(minuteOfDay: Int): DaySlot =
            if (minuteOfDay < SMALL_HOURS_END) NIGHT else entries.last { minuteOfDay >= it.startMin }

        fun now(): DaySlot = LocalDateTime.now().let { at(it.hour * 60 + it.minute) }

        /** Minutes of the day where the window changes — widgets refresh at each one. */
        val boundaries: List<Int> = listOf(SMALL_HOURS_END) + entries.drop(1).map { it.startMin }
    }
}

/**
 * What the plan says you should be doing right now.
 * NOW = open items in the current window. NEXT = nothing left now, so the next window with open items.
 * EARLIER = nothing now or later, but something from earlier today is still open.
 */
data class PlanFocus(val slot: DaySlot, val kind: Kind, val items: List<PlanItem>) {
    enum class Kind { NOW, NEXT, EARLIER }

    val label: String
        get() = when (kind) {
            Kind.NOW -> slot.phrase
            Kind.NEXT -> "Up next · ${slot.label}"
            Kind.EARLIER -> "Still open · ${slot.label}"
        }

    companion object {
        fun of(items: List<PlanItem>, now: DaySlot): PlanFocus? {
            val open = items.filter { !it.done }
            if (open.isEmpty()) return null
            open.filter { it.slot == now.ordinal }.takeIf { it.isNotEmpty() }?.let { return PlanFocus(now, Kind.NOW, it) }
            open.filter { it.slot > now.ordinal }.minByOrNull { it.slot }?.let { n ->
                return PlanFocus(DaySlot.entries[n.slot], Kind.NEXT, open.filter { it.slot == n.slot })
            }
            val e = open.maxBy { it.slot }
            return PlanFocus(DaySlot.entries[e.slot], Kind.EARLIER, open.filter { it.slot == e.slot })
        }
    }
}
