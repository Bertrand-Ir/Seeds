package app.seeds.domain

import app.seeds.data.db.PlanItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanTest {
    private fun item(id: Long, slot: DaySlot, done: Boolean = false) =
        PlanItem(id = id, date = "2026-11-20", slot = slot.ordinal, title = "t$id", done = done)

    @Test fun daySlots() {
        assertEquals(DaySlot.NIGHT, DaySlot.at(60))          // 01:00 still last night
        assertEquals(DaySlot.MORNING, DaySlot.at(7 * 60))
        assertEquals(DaySlot.NOON, DaySlot.at(12 * 60))
        assertEquals(DaySlot.AFTERNOON, DaySlot.at(15 * 60))
        assertEquals(DaySlot.EVENING, DaySlot.at(19 * 60))
        assertEquals(DaySlot.NIGHT, DaySlot.at(22 * 60))
        assertEquals(listOf(240, 690, 840, 1050, 1260), DaySlot.boundaries)
    }

    @Test fun focusPrefersNowThenNextThenEarlier() {
        val items = listOf(
            item(1, DaySlot.MORNING), item(2, DaySlot.AFTERNOON), item(3, DaySlot.AFTERNOON),
            item(4, DaySlot.AFTERNOON, done = true), item(5, DaySlot.NIGHT),
        )
        PlanFocus.of(items, DaySlot.AFTERNOON)!!.let {
            assertEquals(PlanFocus.Kind.NOW, it.kind); assertEquals(listOf(2L, 3L), it.items.map { i -> i.id })
        }
        PlanFocus.of(items, DaySlot.NOON)!!.let {
            assertEquals(PlanFocus.Kind.NEXT, it.kind); assertEquals(DaySlot.AFTERNOON, it.slot)
        }
        PlanFocus.of(listOf(item(1, DaySlot.MORNING)), DaySlot.EVENING)!!.let {
            assertEquals(PlanFocus.Kind.EARLIER, it.kind); assertEquals(DaySlot.MORNING, it.slot)
        }
        assertNull(PlanFocus.of(listOf(item(1, DaySlot.MORNING, done = true)), DaySlot.MORNING))
    }
}
