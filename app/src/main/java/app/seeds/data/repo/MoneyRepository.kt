package app.seeds.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import app.seeds.data.db.Category
import app.seeds.data.db.Debt
import app.seeds.data.db.DebtDirection
import app.seeds.data.db.Direction
import app.seeds.data.db.SeedsDatabase
import app.seeds.data.db.Txn
import app.seeds.domain.MomoParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MoneyRepository(private val db: SeedsDatabase) {
    private val txns = db.txns()
    private val cats = db.categories()
    private val debts = db.debts()

    val categories: Flow<List<Category>> = cats.observeAll()
    val unlabeled: Flow<List<Txn>> = txns.observeUnlabeled()
    val latestBalance: Flow<Txn?> = txns.observeLatestBalance()
    val allDebts: Flow<List<Debt>> = debts.observeAll()
    fun recent(limit: Int = 60): Flow<List<Txn>> = txns.observeRecent(limit)
    fun range(from: Long, to: Long): Flow<List<Txn>> = txns.observeRange(from, to)
    suspend fun rangeNow(from: Long, to: Long) = txns.range(from, to)
    suspend fun txn(id: Long) = txns.get(id)
    suspend fun category(id: Long) = cats.get(id)

    suspend fun ensureDefaults() {
        if (cats.count() == 0) cats.insertAll(Defaults.categories)
    }

    /** Parse and store a money SMS. Returns the new transaction, or null if ignored/duplicate. */
    suspend fun recordSms(body: String, timestamp: Long): Txn? {
        val p = MomoParser.parse(body) ?: return null
        val ext = p.txId?.let { "tx:$it" } ?: "sms:${(body.trim() + (timestamp / 60_000)).hashCode()}"
        val txn = Txn(
            externalId = ext, timestamp = timestamp, amount = p.amount, direction = p.direction, fee = p.fee,
            balanceAfter = p.balanceAfter, counterparty = p.counterparty, raw = body, provider = Txn.PROVIDER_MOMO,
        )
        val id = txns.insert(txn)
        return if (id <= 0) null else txn.copy(id = id)
    }

    /** Category suggestions for a transaction: last used for this person first, then most used. */
    suspend fun suggestions(txn: Txn, limit: Int = 3): List<Category> {
        val all = cats.all().filter { !it.archived && it.direction == txn.direction }
        val byId = all.associateBy { it.id }
        val ordered = LinkedHashSet<Long>()
        txn.counterparty?.let { who -> txns.lastCategoryFor(who, txn.direction)?.let(ordered::add) }
        txns.topCategories(txn.direction, limit + 2).forEach { ordered.add(it.categoryId) }
        all.forEach { ordered.add(it.id) }
        return ordered.mapNotNull { byId[it] }.take(limit)
    }

    suspend fun label(txnId: Long, categoryId: Long, note: String? = null, person: String? = null) = db.withTransaction {
        val t = txns.get(txnId) ?: return@withTransaction
        val cat = cats.get(categoryId)
        val who = person?.takeIf { it.isNotBlank() } ?: t.counterparty ?: "Someone"
        var debtId = t.debtId
        if (debtId == null && cat?.special != null) {
            debtId = when (cat.special) {
                Category.Special.LOAN_GIVEN -> debts.insert(Debt(person = who, amount = t.amount, direction = DebtDirection.OWED_TO_ME, createdAt = t.timestamp))
                Category.Special.LOAN_TAKEN -> debts.insert(Debt(person = who, amount = t.amount, direction = DebtDirection.I_OWE, createdAt = t.timestamp))
                Category.Special.REPAY_RECEIVED -> repay(who, DebtDirection.OWED_TO_ME, t.amount, t.timestamp)
                Category.Special.REPAY_SENT -> repay(who, DebtDirection.I_OWE, t.amount, t.timestamp)
                else -> null
            }
        }
        txns.update(t.copy(categoryId = categoryId, note = note ?: t.note, counterparty = person?.takeIf { it.isNotBlank() } ?: t.counterparty, debtId = debtId))
    }

    private suspend fun repay(person: String, dir: DebtDirection, amount: Long, at: Long): Long? {
        val d = debts.openFor(person, dir) ?: return null
        val paid = d.paid + amount
        debts.update(d.copy(paid = paid, settledAt = if (paid >= d.amount) at else null))
        return d.id
    }

    suspend fun addManual(amount: Long, direction: Direction, categoryId: Long?, note: String?, counterparty: String?, at: Long): Long {
        val t = Txn(
            externalId = "manual:${System.nanoTime()}", timestamp = at, amount = amount, direction = direction,
            counterparty = counterparty?.takeIf { it.isNotBlank() }, note = note, provider = Txn.PROVIDER_CASH,
        )
        val id = txns.insert(t)
        if (categoryId != null) label(id, categoryId, note, counterparty)
        return id
    }

    suspend fun updateTxn(t: Txn) = txns.update(t)
    suspend fun deleteTxn(t: Txn) = txns.delete(t)

    suspend fun saveCategory(c: Category) { if (c.id == 0L) cats.insert(c) else cats.update(c) }

    suspend fun saveDebt(d: Debt) { if (d.id == 0L) debts.insert(d) else debts.update(d) }
    suspend fun deleteDebt(d: Debt) = debts.delete(d)
    suspend fun settle(d: Debt, amount: Long) {
        val paid = (d.paid + amount).coerceAtMost(d.amount)
        debts.update(d.copy(paid = paid, settledAt = if (paid >= d.amount) System.currentTimeMillis() else null))
    }

    /** Backfill from the SMS inbox. Returns how many new transactions were found. */
    suspend fun importInbox(context: Context, senders: Set<String>, days: Int = 120): Int = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() - days * 86_400_000L
        var added = 0
        val uri = Uri.parse("content://sms/inbox")
        runCatching {
            context.contentResolver.query(uri, arrayOf("address", "body", "date"), "date > ?", arrayOf(since.toString()), "date ASC")
        }.getOrNull()?.use { c ->
            val ia = c.getColumnIndex("address"); val ib = c.getColumnIndex("body"); val id = c.getColumnIndex("date")
            while (c.moveToNext()) {
                val address = c.getString(ia) ?: continue
                val body = c.getString(ib) ?: continue
                if (!isMoneySender(address, senders) || !MomoParser.looksLikeMoney(body)) continue
                if (recordSms(body, c.getLong(id)) != null) added++
            }
        }
        added
    }

    companion object {
        fun isMoneySender(address: String, senders: Set<String>): Boolean {
            val a = address.replace(" ", "").lowercase()
            return senders.any { s -> a.contains(s.replace(" ", "").lowercase()) }
        }
    }
}
