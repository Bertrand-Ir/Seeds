package app.seeds.data.repo

import androidx.room.withTransaction
import app.seeds.data.db.Seed
import app.seeds.data.db.SeedsDatabase
import app.seeds.data.db.Session
import app.seeds.data.db.SessionKind
import kotlinx.coroutines.flow.Flow

/**
 * Every write here calls [onChange] so the tracker service, check-in alarms and widgets stay in sync.
 */
class TrackingRepository(
    private val db: SeedsDatabase,
    private val onChange: suspend () -> Unit,
) {
    private val seedDao = db.seeds()
    private val sessionDao = db.sessions()

    val seeds: Flow<List<Seed>> = seedDao.observeActive()
    val allSeeds: Flow<List<Seed>> = seedDao.observeAll()
    val active: Flow<Session?> = sessionDao.observeActive()

    fun sessions(from: Long, to: Long): Flow<List<Session>> = sessionDao.observeRange(from, to)
    suspend fun sessionsNow(from: Long, to: Long) = sessionDao.range(from, to)
    suspend fun activeNow(): Session? = sessionDao.active()
    suspend fun seed(id: Long): Seed? = seedDao.get(id)
    suspend fun activeSeeds(): List<Seed> = seedDao.active()

    // --- Sessions ------------------------------------------------------------------------------

    suspend fun start(
        seedId: Long?,
        kind: SessionKind = SessionKind.SEED,
        at: Long = System.currentTimeMillis(),
        source: String = Session.SOURCE_TIMER,
        appPackage: String? = null,
    ): Session {
        val session = db.withTransaction {
            sessionDao.active()?.let { cur ->
                if (cur.kind == kind && cur.seedId == seedId) return@withTransaction cur
                endInternal(cur, at)
            }
            val s = Session(seedId = seedId, kind = kind, startedAt = at, lastConfirmedAt = at, source = source, appPackage = appPackage)
            s.copy(id = sessionDao.insert(s))
        }
        onChange()
        return session
    }

    suspend fun stop(at: Long = System.currentTimeMillis()): Session? {
        val cur = sessionDao.active() ?: return null
        endInternal(cur, at)
        onChange()
        return cur
    }

    private suspend fun endInternal(cur: Session, at: Long) {
        val end = at.coerceAtLeast(cur.startedAt)
        if (end - cur.startedAt < 30_000 && cur.source == Session.SOURCE_TIMER) {
            sessionDao.delete(cur) // a mis-tap; don't keep 10-second sessions
        } else {
            sessionDao.update(cur.copy(endedAt = end))
        }
    }

    suspend fun confirm(at: Long = System.currentTimeMillis()) {
        val cur = sessionDao.active() ?: return
        sessionDao.update(cur.copy(lastConfirmedAt = at))
        onChange()
    }

    /** End an unconfirmed session at the moment its check-in became due. */
    suspend fun trim(sessionId: Long, at: Long): Boolean {
        val cur = sessionDao.get(sessionId) ?: return false
        if (!cur.isActive) return false
        sessionDao.update(cur.copy(endedAt = at.coerceAtLeast(cur.startedAt), source = Session.SOURCE_TRIMMED))
        onChange()
        return true
    }

    /** "I was still on it" — reopen a trimmed session if nothing else has started since. */
    suspend fun restore(sessionId: Long): Boolean {
        val s = sessionDao.get(sessionId) ?: return false
        if (sessionDao.active() != null) return false
        val now = System.currentTimeMillis()
        sessionDao.update(s.copy(endedAt = null, lastConfirmedAt = now, source = Session.SOURCE_TIMER))
        onChange()
        return true
    }

    suspend fun logManual(seedId: Long?, kind: SessionKind, start: Long, end: Long, note: String? = null) {
        if (end <= start) return
        sessionDao.insert(Session(seedId = seedId, kind = kind, startedAt = start, endedAt = end, lastConfirmedAt = end, source = Session.SOURCE_MANUAL, note = note))
        onChange()
    }

    suspend fun updateSession(s: Session) { sessionDao.update(s); onChange() }
    suspend fun deleteSession(s: Session) { sessionDao.delete(s); onChange() }

    // --- Seeds ---------------------------------------------------------------------------------

    suspend fun saveSeed(seed: Seed): Long {
        val id = if (seed.id == 0L) seedDao.insert(seed.copy(sortOrder = seedDao.maxSort() + 1)) else { seedDao.update(seed); seed.id }
        onChange()
        return id
    }

    suspend fun archiveSeed(seed: Seed, archived: Boolean = true) {
        if (archived && sessionDao.active()?.seedId == seed.id) stop()
        seedDao.update(seed.copy(archived = archived))
        onChange()
    }

    suspend fun reorder(seeds: List<Seed>) {
        seedDao.updateAll(seeds.mapIndexed { i, s -> s.copy(sortOrder = i) })
        onChange()
    }

    suspend fun plantInitial(seeds: List<Seed>) {
        if (seedDao.active().isEmpty()) seedDao.insertAll(seeds.mapIndexed { i, s -> s.copy(sortOrder = i) })
        onChange()
    }
}
