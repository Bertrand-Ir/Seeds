package app.seeds.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Seed::class, Session::class, Txn::class, Category::class, Debt::class, DayReview::class,
        RunRoute::class, Run::class, PlanItem::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class SeedsDatabase : RoomDatabase() {
    abstract fun seeds(): SeedDao
    abstract fun sessions(): SessionDao
    abstract fun txns(): TxnDao
    abstract fun categories(): CategoryDao
    abstract fun debts(): DebtDao
    abstract fun reviews(): ReviewDao
    abstract fun routes(): RouteDao
    abstract fun runs(): RunDao
    abstract fun plan(): PlanDao

    companion object {
        fun build(context: Context): SeedsDatabase =
            Room.databaseBuilder(context, SeedsDatabase::class.java, "seeds.db").build()
    }
}
