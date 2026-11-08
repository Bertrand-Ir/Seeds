package app.seeds.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seeds.data.db.Seed
import app.seeds.data.db.Session
import app.seeds.data.prefs.Settings
import app.seeds.domain.DayClock
import app.seeds.domain.Engine
import app.seeds.seeds
import app.seeds.tracking.Usage
import app.seeds.ui.components.rememberNow

@Immutable
data class DayData(
    val settings: Settings,
    val clock: DayClock,
    val seeds: List<Seed>,
    val sessions: List<Session>,
    val loaded: Boolean,
)

/** Live seeds + sessions for the current week/month, re-keyed when the logical day turns. */
@Composable
fun rememberDayData(settings: Settings): DayData {
    val c = LocalContext.current.seeds
    val clock = remember(settings) { DayClock(settings) }
    val minute by rememberNow(60_000)
    val day = clock.today(minute)
    val range = remember(clock, day) { Engine.overviewRange(clock, System.currentTimeMillis()) }
    val seeds by c.tracking.seeds.collectAsStateWithLifecycle(emptyList())
    val sessionsFlow = remember(range) { c.tracking.sessions(range.first, range.last + 1) }
    val sessions by sessionsFlow.collectAsStateWithLifecycle(null)
    return DayData(settings, clock, seeds, sessions.orEmpty(), sessions != null)
}

object Perms {
    fun notifications(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun sms(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun usage(ctx: Context) = Usage.hasAccess(ctx)

    fun battery(ctx: Context) = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    /** "Display over other apps" — lets the money popup appear instantly over anything. */
    fun overlay(ctx: Context) = AndroidSettings.canDrawOverlays(ctx)

    fun openOverlaySettings(ctx: Context) = runCatching {
        ctx.startActivity(
            Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openUsageSettings(ctx: Context) = ctx.startActivity(
        Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    @Suppress("BatteryLife")
    fun requestBattery(ctx: Context) = runCatching {
        ctx.startActivity(
            Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openAppSettings(ctx: Context) = ctx.startActivity(
        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** Bumps every time the screen resumes — use as a key to re-check permissions. */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    return tick
}
