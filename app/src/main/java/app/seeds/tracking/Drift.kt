package app.seeds.tracking

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/** In-memory drift state shared by the tracker service and notification actions. */
object DriftState {
    @Volatile var currentPkg: String? = null
    @Volatile var currentSince: Long = 0
    /** The stint (by start time) we've already asked about. */
    @Volatile var promptedStint: Long = -1
    @Volatile var snoozeUntil: Long = 0
    /** Seed to offer resuming once an auto-logged drift ends. */
    @Volatile var resumeSeedId: Long? = null
}

object Usage {
    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private const val RESUMED = 1
    private const val PAUSED = 2
    private const val SCREEN_OFF = 16
    private const val KEYGUARD_SHOWN = 17

    /** Foreground time per package in [from, to) for the given packages. */
    fun usage(ctx: Context, from: Long, to: Long, packages: Set<String>): Map<String, Long> {
        if (!hasAccess(ctx)) return emptyMap()
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(from, to)
        val e = UsageEvents.Event()
        val out = HashMap<String, Long>()
        var fg: String? = null
        var since = 0L
        fun close(at: Long) {
            val p = fg ?: return
            if (p in packages && at > since) out.merge(p, at - since, Long::plus)
            fg = null
        }
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                RESUMED -> if (e.packageName != fg) { close(e.timeStamp); fg = e.packageName; since = e.timeStamp }
                PAUSED -> if (e.packageName == fg) close(e.timeStamp)
                SCREEN_OFF, KEYGUARD_SHOWN -> close(e.timeStamp)
            }
        }
        close(minOf(to, System.currentTimeMillis()))
        return out
    }
}

/** Incrementally follows which app is in the foreground and since when. */
class ForegroundWatcher(ctx: Context) {
    private val usm = ctx.getSystemService(UsageStatsManager::class.java)
    private var lastQuery = 0L
    private var pkg: String? = null
    private var since = 0L
    private var pausedAt: Long? = null

    /** Returns (package, since) of the app in front now, or null. */
    fun poll(now: Long = System.currentTimeMillis()): Pair<String, Long>? {
        val from = if (lastQuery == 0L) now - 3 * 3_600_000L else lastQuery - 1_000
        val events = usm.queryEvents(from, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                1 -> if (e.packageName == pkg) pausedAt = null else { pkg = e.packageName; since = e.timeStamp; pausedAt = null }
                2 -> if (e.packageName == pkg) pausedAt = e.timeStamp
                16, 17 -> { pkg = null; pausedAt = null }
            }
        }
        lastQuery = now
        // An activity change inside the same app pauses then resumes within a moment — keep the stint.
        pausedAt?.let { if (now - it > 5_000) { pkg = null; pausedAt = null } }
        return pkg?.let { it to since }
    }
}

object AppLabels {
    private val known = mapOf(
        "com.zhiliaoapp.musically" to "TikTok", "com.ss.android.ugc.trill" to "TikTok",
        "com.instagram.android" to "Instagram", "com.facebook.katana" to "Facebook", "com.facebook.lite" to "Facebook Lite",
        "com.twitter.android" to "X", "com.google.android.youtube" to "YouTube", "com.snapchat.android" to "Snapchat",
        "com.reddit.frontpage" to "Reddit", "com.netflix.mediaclient" to "Netflix", "com.instagram.barcelona" to "Threads",
    )

    fun label(ctx: Context, pkg: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull() ?: known[pkg] ?: pkg.substringAfterLast('.')
}
