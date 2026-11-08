package app.seeds.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.seeds.BuildConfig
import app.seeds.data.prefs.SettingsRepository
import app.seeds.notify.Notifier
import app.seeds.seeds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class Release(
    val version: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val pageUrl: String,
    val sizeBytes: Long,
)

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Running(val progress: Float) : DownloadState
    data class Ready(val file: File) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * Seeds updates itself from GitHub Releases: every push to main publishes a signed APK,
 * and this class finds it, downloads it and hands it to the system installer.
 */
class UpdateManager(private val app: Context, private val settings: SettingsRepository) {
    private val _available = MutableStateFlow<Release?>(null)
    val available: StateFlow<Release?> = _available
    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = _download

    val currentVersion: String get() = BuildConfig.VERSION_NAME.removeSuffix("-dev")

    suspend fun check(force: Boolean = false): Result<Release?> = withContext(Dispatchers.IO) {
        val s = settings.current()
        val repo = s.githubRepo.trim()
        if (!repo.matches(Regex("[\\w.-]+/[\\w.-]+")) || repo.startsWith("OWNER/")) {
            return@withContext Result.failure(IllegalStateException("Set your GitHub repository in Settings → Updates."))
        }
        if (!force && System.currentTimeMillis() - s.lastUpdateCheck < 60 * 60_000L && _available.value != null) {
            return@withContext Result.success(_available.value)
        }
        runCatching {
            val conn = (URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Seeds-Android")
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            try {
                if (conn.responseCode == 404) return@runCatching null
                if (conn.responseCode !in 200..299) error("GitHub answered ${conn.responseCode}")
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val tag = json.optString("tag_name").removePrefix("v")
                val assets = json.optJSONArray("assets")
                var apk: JSONObject? = null
                if (assets != null) for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) { apk = a; break }
                }
                settings.update { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
                if (apk == null || !isNewer(tag, currentVersion)) null
                else Release(
                    version = tag,
                    title = json.optString("name").ifBlank { "Seeds $tag" },
                    notes = json.optString("body").trim(),
                    apkUrl = apk.getString("browser_download_url"),
                    pageUrl = json.optString("html_url"),
                    sizeBytes = apk.optLong("size"),
                )
            } finally {
                conn.disconnect()
            }
        }.onSuccess { _available.value = it }
    }

    suspend fun downloadAndInstall(context: Context, release: Release) {
        val state = _download.value
        if (state is DownloadState.Running) return
        if (state is DownloadState.Ready && state.file.name.contains(release.version)) { install(context, state.file); return }
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(app.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val out = File(dir, "seeds-${release.version}.apk")
                var url = URL(release.apkUrl)
                var conn: HttpURLConnection
                var redirects = 0
                while (true) {
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", "Seeds-Android")
                        connectTimeout = 20_000
                        readTimeout = 30_000
                    }
                    val code = conn.responseCode
                    if (code in 300..399 && redirects++ < 5) { url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); continue }
                    if (code !in 200..299) error("Download failed ($code)")
                    break
                }
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            read += n
                            if (total > 0) _download.value = DownloadState.Running((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                conn.disconnect()
                out
            }
        }
        file.onSuccess {
            _download.value = DownloadState.Ready(it)
            install(context, it)
        }.onFailure {
            _download.value = DownloadState.Failed(it.message ?: "Download failed")
        }
    }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(context: Context, file: File) {
        if (!canInstall(context)) { openInstallPermission(context); return }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    companion object {
        /** Numeric, dot-separated comparison: 1.0.12 > 1.0.9. */
        fun isNewer(remote: String, local: String): Boolean {
            val r = remote.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val l = local.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(r.size, l.size)) {
                val a = r.getOrElse(i) { 0 }
                val b = l.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return false
        }
    }
}

/** Checks for a new release a few times a day and notifies once per version. */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.seeds
        val s = c.settings.current()
        if (!s.autoUpdate) return Result.success()
        val release = c.updates.check(force = true).getOrNull() ?: return Result.success()
        if (s.notifiedVersion != release.version) {
            Notifier.update(applicationContext, release.version)
            c.settings.update { it.copy(notifiedVersion = release.version) }
        }
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("seeds-update", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
