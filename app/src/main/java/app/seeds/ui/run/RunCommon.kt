package app.seeds.ui.run

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.seeds.data.db.Run
import app.seeds.data.db.Seed
import app.seeds.domain.LatLng

fun hasLocation(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
fun lastKnown(ctx: Context): LatLng? {
    if (!hasLocation(ctx)) return null
    val lm = ctx.getSystemService(LocationManager::class.java)
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
        ?.let { LatLng(it.latitude, it.longitude) }
}

/** Live location while this composable is on screen (and permission is granted). */
@SuppressLint("MissingPermission")
@Composable
fun rememberLocation(enabled: Boolean, key: Any? = null): State<Location?> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf<Location?>(null) }
    DisposableEffect(enabled, key) {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val listener = LocationListener { state.value = it }
        if (enabled && hasLocation(ctx)) {
            runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper()) }
            runCatching { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3_000L, 0f, listener, Looper.getMainLooper()) }
        }
        onDispose { runCatching { lm.removeUpdates(listener) } }
    }
    return state
}

fun Location.latLng() = LatLng(latitude, longitude)

/** The seed a run should grow by default: anything that looks like exercise. */
fun exerciseSeed(seeds: List<Seed>): Seed? =
    seeds.firstOrNull { it.icon in setOf("run", "fitness", "walk", "sport") || it.name.contains("run", true) || it.name.contains("exercise", true) }

data class Records(val best1k: Run?, val best5k: Run?, val longest: Run?, val fastestAvg: Run?)

fun records(runs: List<Run>): Records = Records(
    best1k = runs.filter { it.best1kSec != null }.minByOrNull { it.best1kSec!! },
    best5k = runs.filter { it.best5kSec != null }.minByOrNull { it.best5kSec!! },
    longest = runs.maxByOrNull { it.distanceM },
    fastestAvg = runs.filter { it.distanceM >= 1000 }.minByOrNull { it.paceSecPerKm },
)

fun mmss(sec: Int): String = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)
