package app.seeds.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Period { DAILY, WEEKLY, MONTHLY }

/** SEED = growing one of your seeds, BREAK = intentional rest, DRIFT = time lost to distraction. */
enum class SessionKind { SEED, BREAK, DRIFT }

enum class Direction { IN, OUT }

enum class DebtDirection { OWED_TO_ME, I_OWE }

/** A non-negotiable: something you commit time to every day, week or month. */
@Entity(tableName = "seeds")
data class Seed(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val color: Int,
    val targetMinutes: Int,
    val period: Period,
    /** Focus seeds are protected by the drift guard while they run. */
    val focus: Boolean = false,
    /** Bit 0 = Monday … bit 6 = Sunday. Only meaningful for daily seeds. */
    val activeDays: Int = ALL_DAYS,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun appliesOn(dayOfWeekIso: Int): Boolean = activeDays and (1 shl (dayOfWeekIso - 1)) != 0

    companion object {
        const val ALL_DAYS = 0b1111111
        const val WEEKDAYS = 0b0011111
    }
}

@Entity(
    tableName = "sessions",
    indices = [Index("startedAt"), Index("endedAt"), Index("seedId")],
)
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seedId: Long?,
    val kind: SessionKind,
    val startedAt: Long,
    val endedAt: Long? = null,
    /** Last moment the user confirmed they were still on this. */
    val lastConfirmedAt: Long = startedAt,
    val source: String = SOURCE_TIMER,
    val appPackage: String? = null,
    val note: String? = null,
) {
    val isActive: Boolean get() = endedAt == null
    fun endOr(now: Long): Long = endedAt ?: now

    companion object {
        const val SOURCE_TIMER = "timer"
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_DRIFT = "drift"
        const val SOURCE_TRIMMED = "trimmed"
    }
}

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["externalId"], unique = true), Index("timestamp"), Index("categoryId")],
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val externalId: String,
    val timestamp: Long,
    val amount: Long,
    val direction: Direction,
    val fee: Long = 0,
    val balanceAfter: Long? = null,
    val counterparty: String? = null,
    val categoryId: Long? = null,
    val note: String? = null,
    val raw: String? = null,
    val provider: String = PROVIDER_MOMO,
    val debtId: Long? = null,
) {
    companion object {
        const val PROVIDER_MOMO = "MoMo"
        const val PROVIDER_CASH = "Cash"
    }
}

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val color: Int,
    val direction: Direction,
    val monthlyBudget: Long? = null,
    /** Links money to debts: see [Special]. */
    val special: String? = null,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
) {
    object Special {
        const val LOAN_GIVEN = "loan_given"       // OUT: I lent money -> someone owes me
        const val LOAN_TAKEN = "loan_taken"       // IN: I borrowed -> I owe
        const val REPAY_RECEIVED = "repay_in"     // IN: someone paid me back
        const val REPAY_SENT = "repay_out"        // OUT: I paid back what I owed
    }
}

@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    val amount: Long,
    val direction: DebtDirection,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val dueAt: Long? = null,
    val paid: Long = 0,
    val settledAt: Long? = null,
) {
    val remaining: Long get() = (amount - paid).coerceAtLeast(0)
}

/** The evening reflection for one logical day (ISO date string). */
@Entity(tableName = "reviews")
data class DayReview(
    @PrimaryKey val date: String,
    val mood: Int,
    val win: String = "",
    val lesson: String = "",
    val score: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
