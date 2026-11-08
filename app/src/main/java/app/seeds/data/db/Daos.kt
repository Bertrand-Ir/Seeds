package app.seeds.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SeedDao {
    @Query("SELECT * FROM seeds WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<Seed>>

    @Query("SELECT * FROM seeds ORDER BY archived, sortOrder, id")
    fun observeAll(): Flow<List<Seed>>

    @Query("SELECT * FROM seeds WHERE archived = 0 ORDER BY sortOrder, id")
    suspend fun active(): List<Seed>

    @Query("SELECT * FROM seeds")
    suspend fun all(): List<Seed>

    @Query("SELECT * FROM seeds WHERE id = :id")
    suspend fun get(id: Long): Seed?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM seeds")
    suspend fun maxSort(): Int

    @Insert
    suspend fun insert(seed: Seed): Long

    @Insert
    suspend fun insertAll(seeds: List<Seed>)

    @Update
    suspend fun update(seed: Seed)

    @Update
    suspend fun updateAll(seeds: List<Seed>)

    @Delete
    suspend fun delete(seed: Seed)

    @Query("DELETE FROM seeds")
    suspend fun clear()
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<Session?>

    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun active(): Session?

    @Query("SELECT * FROM sessions WHERE startedAt < :to AND (endedAt IS NULL OR endedAt > :from) ORDER BY startedAt")
    fun observeRange(from: Long, to: Long): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE startedAt < :to AND (endedAt IS NULL OR endedAt > :from) ORDER BY startedAt")
    suspend fun range(from: Long, to: Long): List<Session>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): Session?

    @Query("SELECT * FROM sessions")
    suspend fun all(): List<Session>

    @Insert
    suspend fun insert(session: Session): Long

    @Insert
    suspend fun insertAll(sessions: List<Session>)

    @Update
    suspend fun update(session: Session)

    @Delete
    suspend fun delete(session: Session)

    @Query("DELETE FROM sessions")
    suspend fun clear()
}

data class CategoryUse(val categoryId: Long, val uses: Int)

@Dao
interface TxnDao {
    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun observeRange(from: Long, to: Long): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    suspend fun range(from: Long, to: Long): List<Txn>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE categoryId IS NULL ORDER BY timestamp DESC")
    fun observeUnlabeled(): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE balanceAfter IS NOT NULL ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestBalance(): Flow<Txn?>

    @Query("SELECT categoryId, COUNT(*) AS uses FROM transactions WHERE categoryId IS NOT NULL AND direction = :direction GROUP BY categoryId ORDER BY uses DESC LIMIT :limit")
    suspend fun topCategories(direction: Direction, limit: Int): List<CategoryUse>

    @Query("SELECT categoryId FROM transactions WHERE counterparty = :who AND direction = :direction AND categoryId IS NOT NULL ORDER BY timestamp DESC LIMIT 1")
    suspend fun lastCategoryFor(who: String, direction: Direction): Long?

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): Txn?

    @Query("SELECT * FROM transactions")
    suspend fun all(): List<Txn>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(txn: Txn): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(txns: List<Txn>)

    @Update
    suspend fun update(txn: Txn)

    @Delete
    suspend fun delete(txn: Txn)

    @Query("DELETE FROM transactions")
    suspend fun clear()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE archived = 0 ORDER BY direction, sortOrder, id")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY direction, sortOrder, id")
    suspend fun all(): List<Category>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): Category?

    @Insert
    suspend fun insert(category: Category): Long

    @Insert
    suspend fun insertAll(categories: List<Category>)

    @Update
    suspend fun update(category: Category)

    @Query("DELETE FROM categories")
    suspend fun clear()
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY settledAt IS NOT NULL, createdAt DESC")
    fun observeAll(): Flow<List<Debt>>

    @Query("SELECT * FROM debts WHERE settledAt IS NULL AND direction = :direction AND person = :person COLLATE NOCASE ORDER BY createdAt LIMIT 1")
    suspend fun openFor(person: String, direction: DebtDirection): Debt?

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun get(id: Long): Debt?

    @Query("SELECT * FROM debts")
    suspend fun all(): List<Debt>

    @Insert
    suspend fun insert(debt: Debt): Long

    @Insert
    suspend fun insertAll(debts: List<Debt>)

    @Update
    suspend fun update(debt: Debt)

    @Delete
    suspend fun delete(debt: Debt)

    @Query("DELETE FROM debts")
    suspend fun clear()
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM reviews WHERE date = :date")
    fun observe(date: String): Flow<DayReview?>

    @Query("SELECT * FROM reviews WHERE date = :date")
    suspend fun get(date: String): DayReview?

    @Query("SELECT * FROM reviews ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DayReview>>

    @Query("SELECT * FROM reviews")
    suspend fun all(): List<DayReview>

    @Upsert
    suspend fun upsert(review: DayReview)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reviews: List<DayReview>)

    @Query("DELETE FROM reviews")
    suspend fun clear()
}
