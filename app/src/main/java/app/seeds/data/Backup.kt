package app.seeds.data

import androidx.room.withTransaction
import app.seeds.data.db.Category
import app.seeds.data.db.DayReview
import app.seeds.data.db.Debt
import app.seeds.data.db.DebtDirection
import app.seeds.data.db.Direction
import app.seeds.data.db.Period
import app.seeds.data.db.Seed
import app.seeds.data.db.SeedsDatabase
import app.seeds.data.db.Session
import app.seeds.data.db.SessionKind
import app.seeds.data.db.Txn
import org.json.JSONArray
import org.json.JSONObject

/** Everything lives on your phone. Export it to a file now and then — this is that file. */
object Backup {
    private const val VERSION = 1

    suspend fun export(db: SeedsDatabase): String {
        val root = JSONObject()
        root.put("app", "seeds").put("version", VERSION).put("exportedAt", System.currentTimeMillis())
        root.put("seeds", JSONArray(db.seeds().all().map {
            JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon).put("color", it.color)
                .put("targetMinutes", it.targetMinutes).put("period", it.period.name).put("focus", it.focus)
                .put("activeDays", it.activeDays).put("archived", it.archived).put("sortOrder", it.sortOrder).put("createdAt", it.createdAt)
        }))
        root.put("sessions", JSONArray(db.sessions().all().map {
            JSONObject().put("id", it.id).put("seedId", it.seedId ?: JSONObject.NULL).put("kind", it.kind.name)
                .put("startedAt", it.startedAt).put("endedAt", it.endedAt ?: JSONObject.NULL).put("lastConfirmedAt", it.lastConfirmedAt)
                .put("source", it.source).put("appPackage", it.appPackage ?: JSONObject.NULL).put("note", it.note ?: JSONObject.NULL)
        }))
        root.put("categories", JSONArray(db.categories().all().map {
            JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon).put("color", it.color)
                .put("direction", it.direction.name).put("monthlyBudget", it.monthlyBudget ?: JSONObject.NULL)
                .put("special", it.special ?: JSONObject.NULL).put("sortOrder", it.sortOrder).put("archived", it.archived)
        }))
        root.put("transactions", JSONArray(db.txns().all().map {
            JSONObject().put("id", it.id).put("externalId", it.externalId).put("timestamp", it.timestamp).put("amount", it.amount)
                .put("direction", it.direction.name).put("fee", it.fee).put("balanceAfter", it.balanceAfter ?: JSONObject.NULL)
                .put("counterparty", it.counterparty ?: JSONObject.NULL).put("categoryId", it.categoryId ?: JSONObject.NULL)
                .put("note", it.note ?: JSONObject.NULL).put("raw", it.raw ?: JSONObject.NULL).put("provider", it.provider)
                .put("debtId", it.debtId ?: JSONObject.NULL)
        }))
        root.put("debts", JSONArray(db.debts().all().map {
            JSONObject().put("id", it.id).put("person", it.person).put("amount", it.amount).put("direction", it.direction.name)
                .put("note", it.note ?: JSONObject.NULL).put("createdAt", it.createdAt).put("dueAt", it.dueAt ?: JSONObject.NULL)
                .put("paid", it.paid).put("settledAt", it.settledAt ?: JSONObject.NULL)
        }))
        root.put("reviews", JSONArray(db.reviews().all().map {
            JSONObject().put("date", it.date).put("mood", it.mood).put("win", it.win).put("lesson", it.lesson)
                .put("score", it.score ?: JSONObject.NULL).put("createdAt", it.createdAt)
        }))
        return root.toString(2)
    }

    suspend fun import(db: SeedsDatabase, text: String) {
        val root = JSONObject(text)
        require(root.optString("app") == "seeds") { "This isn't a Seeds backup." }
        fun JSONObject.longOrNull(k: String) = if (isNull(k) || !has(k)) null else getLong(k)
        fun JSONObject.strOrNull(k: String) = if (isNull(k) || !has(k)) null else getString(k)
        fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

        db.withTransaction {
            db.sessions().clear(); db.seeds().clear(); db.txns().clear(); db.categories().clear(); db.debts().clear(); db.reviews().clear()
            db.seeds().insertAll(root.getJSONArray("seeds").objects().map {
                Seed(it.getLong("id"), it.getString("name"), it.getString("icon"), it.getInt("color"), it.getInt("targetMinutes"),
                    Period.valueOf(it.getString("period")), it.getBoolean("focus"), it.getInt("activeDays"), it.getBoolean("archived"),
                    it.getInt("sortOrder"), it.getLong("createdAt"))
            })
            db.sessions().insertAll(root.getJSONArray("sessions").objects().map {
                Session(it.getLong("id"), it.longOrNull("seedId"), SessionKind.valueOf(it.getString("kind")), it.getLong("startedAt"),
                    it.longOrNull("endedAt"), it.getLong("lastConfirmedAt"), it.getString("source"), it.strOrNull("appPackage"), it.strOrNull("note"))
            })
            db.categories().insertAll(root.getJSONArray("categories").objects().map {
                Category(it.getLong("id"), it.getString("name"), it.getString("icon"), it.getInt("color"), Direction.valueOf(it.getString("direction")),
                    it.longOrNull("monthlyBudget"), it.strOrNull("special"), it.getInt("sortOrder"), it.getBoolean("archived"))
            })
            db.txns().insertAll(root.getJSONArray("transactions").objects().map {
                Txn(it.getLong("id"), it.getString("externalId"), it.getLong("timestamp"), it.getLong("amount"), Direction.valueOf(it.getString("direction")),
                    it.getLong("fee"), it.longOrNull("balanceAfter"), it.strOrNull("counterparty"), it.longOrNull("categoryId"), it.strOrNull("note"),
                    it.strOrNull("raw"), it.getString("provider"), it.longOrNull("debtId"))
            })
            db.debts().insertAll(root.getJSONArray("debts").objects().map {
                Debt(it.getLong("id"), it.getString("person"), it.getLong("amount"), DebtDirection.valueOf(it.getString("direction")),
                    it.strOrNull("note"), it.getLong("createdAt"), it.longOrNull("dueAt"), it.getLong("paid"), it.longOrNull("settledAt"))
            })
            db.reviews().insertAll(root.getJSONArray("reviews").objects().map {
                DayReview(it.getString("date"), it.getInt("mood"), it.getString("win"), it.getString("lesson"),
                    if (it.isNull("score")) null else it.getInt("score"), it.getLong("createdAt"))
            })
        }
    }
}
