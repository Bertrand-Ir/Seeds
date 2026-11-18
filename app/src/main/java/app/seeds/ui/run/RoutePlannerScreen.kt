package app.seeds.ui.run

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.seeds.data.db.RunRoute
import app.seeds.domain.Fmt
import app.seeds.domain.Geo
import app.seeds.domain.LatLng
import app.seeds.domain.PolylineCodec
import app.seeds.notify.Alarms
import app.seeds.run.Router
import app.seeds.seeds
import app.seeds.ui.components.ButtonStyle
import app.seeds.ui.components.ChoiceChip
import app.seeds.ui.components.PillButton
import app.seeds.ui.components.SeedsSheet
import app.seeds.ui.components.SwitchRow
import app.seeds.ui.components.TimeDialog
import app.seeds.ui.money.seedsFieldColors
import app.seeds.ui.theme.Pill
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Tap the map to lay out a route. Seeds snaps it to real paths and measures it. */
@Composable
fun RoutePlannerScreen(routeId: Long, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.seeds
    val scope = rememberCoroutineScope()
    val waypoints = remember { mutableStateListOf<LatLng>() }
    var path by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var followRoads by remember { mutableStateOf(true) }
    var routing by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
    var existing by remember { mutableStateOf<RunRoute?>(null) }
    var camera by remember { mutableStateOf(Camera(center = lastKnown(ctx) ?: KIGALI, zoom = 16.0)) }
    var camKey by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var permAsked by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) lastKnown(ctx)?.let { camera = Camera(center = it, zoom = 16.0, key = ++camKey) }
    }
    val loc by rememberLocation(enabled = true, key = permAsked)
    var centeredOnMe by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasLocation(ctx)) { permAsked = true; permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
        if (routeId > 0) c.db.routes().get(routeId)?.let { r ->
            existing = r
            followRoads = r.followRoads
            val wps = PolylineCodec.decode(r.waypoints).ifEmpty { PolylineCodec.decode(r.encoded) }
            waypoints.addAll(wps)
            path = PolylineCodec.decode(r.encoded)
            camera = Camera(fit = path, key = ++camKey)
            centeredOnMe = true
        }
    }
    LaunchedEffect(loc) {
        val l = loc ?: return@LaunchedEffect
        if (!centeredOnMe && waypoints.isEmpty()) { centeredOnMe = true; camera = Camera(center = l.latLng(), zoom = 16.5, key = ++camKey) }
    }

    // Re-route whenever the points or the mode change (debounced).
    val snapshot = waypoints.toList()
    LaunchedEffect(snapshot, followRoads) {
        if (snapshot.size < 2) { path = snapshot; return@LaunchedEffect }
        delay(250)
        if (followRoads) {
            routing = true
            val r = Router.foot(snapshot)
            offline = r == null
            path = r ?: Router.straight(snapshot)
            routing = false
        } else {
            offline = false
            path = Router.straight(snapshot)
        }
    }
    val distance = Geo.length(path)

    Box(Modifier.fillMaxSize()) {
        SeedsMap(
            Modifier.fillMaxSize(), camera,
            planned = path, waypoints = waypoints, me = loc?.latLng(),
            onTap = { waypoints.add(it) },
        )

        // Top bar
        Row(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundBtn(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onDone)
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.weight(1f).shadow(8.dp, Pill).clip(Pill).background(SeedsTheme.colors.surface).padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    when {
                        waypoints.isEmpty() -> "Tap the map where you'll start"
                        waypoints.size == 1 -> "Now tap where you'll run to"
                        else -> "Tap to add more · ${waypoints.size} points"
                    },
                    style = SeedsType.label, color = SeedsTheme.colors.ink,
                )
            }
            Spacer(Modifier.width(10.dp))
            RoundBtn(Icons.Rounded.MyLocation, "My location") {
                val me = loc?.latLng() ?: lastKnown(ctx)
                if (me != null) camera = Camera(center = me, zoom = 17.0, key = ++camKey)
                else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        // Bottom panel
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(SeedsTheme.colors.bg)
                .navigationBarsPadding()
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Geo.km(distance), style = SeedsType.display, color = SeedsTheme.colors.ink)
                Spacer(Modifier.width(6.dp))
                Text("km", style = SeedsType.title, color = SeedsTheme.colors.inkMuted, modifier = Modifier.padding(bottom = 8.dp))
                Spacer(Modifier.weight(1f))
                if (routing) CircularProgressIndicator(Modifier.size(22.dp).padding(bottom = 10.dp), color = SeedsTheme.colors.accent, strokeWidth = 2.dp)
            }
            Text(
                if (distance > 0) "≈ ${Fmt.duration((distance / 1000 * 6 * 60_000).toLong())} at an easy 6:00 /km" +
                    if (offline && followRoads) " · offline, using straight lines" else ""
                else "Plan a loop or an out-and-back. Seeds follows footpaths.",
                style = SeedsType.caption, color = SeedsTheme.colors.inkMuted,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Follow paths", followRoads, { followRoads = true })
                ChoiceChip("Straight lines", !followRoads, { followRoads = false })
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolBtn(Icons.AutoMirrored.Rounded.Undo, "Undo", waypoints.isNotEmpty()) { waypoints.removeAt(waypoints.lastIndex) }
                ToolBtn(Icons.Rounded.Loop, "Loop", waypoints.size >= 2) { waypoints.add(waypoints.first()) }
                ToolBtn(Icons.Rounded.DeleteOutline, "Clear", waypoints.isNotEmpty()) { waypoints.clear() }
                ToolBtn(Icons.Rounded.Map, "Google", waypoints.size >= 2) {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Router.googleMapsUrl(waypoints))))
                }
                Spacer(Modifier.weight(1f))
                PillButton("Save", { saving = true }, enabled = path.size >= 2 && !routing, height = 48.dp)
            }
        }
    }

    if (saving) SaveRouteSheet(existing, onDismiss = { saving = false }) { name, sched, days, delete ->
        scope.launch {
            val r = existing
            if (delete && r != null) c.db.routes().update(r.copy(archived = true))
            else {
                val route = RunRoute(
                    id = r?.id ?: 0, name = name.ifBlank { "My route" }, encoded = PolylineCodec.encode(path),
                    waypoints = PolylineCodec.encode(waypoints), distanceM = distance, followRoads = followRoads,
                    createdAt = r?.createdAt ?: System.currentTimeMillis(), scheduleMin = sched, scheduleDays = if (sched == null) 0 else days,
                )
                if (r == null) c.db.routes().insert(route) else c.db.routes().update(route)
            }
            Alarms.scheduleRuns(ctx)
            saving = false
            onDone()
        }
    }
}

@Composable
private fun RoundBtn(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(SeedsTheme.colors.surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = SeedsTheme.colors.ink) }
}

@Composable
private fun ToolBtn(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = SeedsTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(4.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.surfaceHi), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (enabled) c.ink else c.inkFaint, modifier = Modifier.size(20.dp))
        }
        Text(label, style = SeedsType.caption, color = if (enabled) c.inkMuted else c.inkFaint)
    }
}

@Composable
private fun SaveRouteSheet(existing: RunRoute?, onDismiss: () -> Unit, onSave: (String, Int?, Int, Boolean) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var remind by remember { mutableStateOf(existing?.scheduleMin != null) }
    var time by remember { mutableIntStateOf(existing?.scheduleMin ?: (6 * 60)) }
    var days by remember { mutableIntStateOf(existing?.scheduleDays?.takeIf { it != 0 } ?: 0b0010101) }
    var picking by remember { mutableStateOf(false) }
    SeedsSheet(onDismiss) {
        Text(if (existing == null) "Save this route" else "Route settings", style = SeedsType.title, color = SeedsTheme.colors.ink)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = seedsFieldColors(),
            placeholder = { Text("e.g. Morning loop, Lake run") })
        Spacer(Modifier.height(8.dp))
        SwitchRow("Remind me to run it", if (remind) "At ${Fmt.clockOfDay(time)} on the days below" else "Get a nudge when it's time", remind) { remind = it }
        AnimatedVisibility(remind) {
            Column {
                PillButton("Time · ${Fmt.clockOfDay(time)}", { picking = true }, style = ButtonStyle.Secondary, height = 44.dp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, l ->
                        ChoiceChip(l, days and (1 shl i) != 0, { val n = days xor (1 shl i); if (n != 0) days = n })
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        PillButton("Save route", { onSave(name.trim(), if (remind) time else null, days, false) }, Modifier.fillMaxWidth())
        if (existing != null) {
            Spacer(Modifier.height(10.dp))
            PillButton("Delete route", { onSave(name, null, 0, true) }, Modifier.fillMaxWidth(), style = ButtonStyle.Danger)
        }
    }
    if (picking) TimeDialog("Run time", time, { picking = false }) { time = it; picking = false }
}
