package app.seeds.data.repo

import app.seeds.data.db.PlanItem
import app.seeds.data.db.SeedsDatabase
import app.seeds.domain.DaySlot

/** Every plan change goes through here, so the home-screen widget always shows the current plan. */
class PlanRepository(private val db: SeedsDatabase, private val onChange: suspend () -> Unit) {
    private val dao get() = db.plan()

    fun observe(date: String) = dao.observe(date)
    suspend fun forDate(date: String) = dao.forDate(date)

    suspend fun add(date: String, slot: DaySlot, title: String, seedId: Long?) {
        if (title.isBlank()) return
        val order = dao.forDate(date).count { it.slot == slot.ordinal }
        dao.insert(PlanItem(date = date, slot = slot.ordinal, title = title.trim(), seedId = seedId, sortOrder = order))
        onChange()
    }

    suspend fun toggle(item: PlanItem) { dao.update(item.copy(done = !item.done)); onChange() }

    suspend fun markDone(id: Long) {
        val item = dao.get(id) ?: return
        if (!item.done) dao.update(item.copy(done = true))
        onChange()
    }

    suspend fun delete(item: PlanItem) { dao.delete(item); onChange() }

    /** Moves unfinished items to another day, keeping their windows. */
    suspend fun carryOver(items: List<PlanItem>, toDate: String) {
        items.forEach { dao.insert(it.copy(id = 0, date = toDate, done = false)); dao.delete(it) }
        onChange()
    }
}
