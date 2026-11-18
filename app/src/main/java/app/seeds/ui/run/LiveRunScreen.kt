package app.seeds.ui.run

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WrongLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seeds.data.db.RunRoute
import app.seeds.domain.Fmt
import app.seeds.domain.Geo
import app.seeds.domain.LatLng
import app.seeds.domain.PolylineCodec
import app.seeds.run.LiveRun
import app.seeds.run.RunPhase
import app.seeds.run.RunService
import app.seeds.run.RunState
import app.seeds.seeds
import app.seeds.ui.components.ChoiceChip
import app.seeds.ui.components.Meter
import app.seeds.ui.components.rememberNow
import app.seeds.ui.theme.Ink
import app.seeds.ui.theme.Pill
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LiveRunScreen(routeId: Long, onBack: () -> Unit, onFinished: (Long) -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.seeds
    val live by RunState.live.collectAsStateWithLifecycle()
    var route by remember { mutableStateOf<RunRoute?>(null) }
    LaunchedEffect(routeId) { if (routeId > 0) route = c.db.routes().get(routeId) }

    val l = live
    LaunchedEffect(l?.phase, l?.savedRunId) {
        if (l?.phase == RunPhase.DONE) {
            val id = l.savedRunId
            RunState.clear()
            if (id != null && id > 0) onFinished(id) else onBack()
        }
    }

    if (l != null && l.phase != RunPhase.DONE) ActiveRun(l) else ReadyToRun(route, onBack)
}

// ---------------------------------------------------------------------------------------------
// Before the run
// ---------------------------------------------------------------------------------------------

@Composable
private fun ReadyToRun(route: RunRoute?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.seeds
    val scope = rememberCoroutineScope()
    val seeds by c.tracking.seeds.collectAsStateWithLifecycle(emptyList())
    val prefs = remember { ctx.getSharedPreferences("seeds_ui", android.content.Context.MODE_PRIVATE) }
    var voice by remember { mutableStateOf(prefs.getBoolean("run_voice", true)) }
    var seedId by remember { mutableStateOf<Long?>(null) }
    var seedTouched by remember { mutableStateOf(false) }
    LaunchedEffect(seeds) { if (!seedTouched) seedId = exerciseSeed(seeds)?.id }
    var granted by remember { mutableStateOf(hasLocation(ctx)) }
    var countdown by remember { mutableIntStateOf(0) }
    val loc by rememberLocation(enabled = true, key = granted)
    val planned = remember(route) { route?.let { PolylineCodec.decode(it.encoded) }.orEmpty() }
    var camKey by remember { mutableIntStateOf(0) }
    val camera = remember(planned, camKey) {
        if (planned.size >= 2) Camera(fit = planned, key = camKey) else Camera(center = lastKnown(ctx) ?: KIGALI, zoom = 16.5, key = camKey)
    }
    var centered by remember { mutableStateOf(false) }
    LaunchedEffect(loc) { if (loc != null && !centered && planned.isEmpty()) { centered = true; camKey++ } }

    fun begin() = scope.launch {
        for (i in 3 downTo 1) { countdown = i; delay(800) }
        countdown = 0
        RunService.start(ctx, route?.id, route?.name ?: "Free run", route?.encoded, seedId)
    }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        granted = res[Manifest.permission.ACCESS_FINE_LOCATION] == true || hasLocation(ctx)
        if (granted) begin()
    }
    fun start() {
        if (hasLocation(ctx)) begin()
        else perms.launch(
            buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray(),
        )
    }

    val acc = loc?.accuracy
    Box(Modifier.fillMaxSize().background(SeedsTheme.colors.bg)) {
        SeedsMap(
            Modifier.fillMaxSize(), camera, planned = planned,
            waypoints = listOfNotNull(planned.firstOrNull()), me = loc?.latLng(), followMe = planned.isEmpty() && loc != null,
        )
        Row(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(SeedsTheme.colors.surface).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = SeedsTheme.colors.ink)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.shadow(8.dp, Pill).clip(Pill).background(SeedsTheme.colors.surface).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(route?.name ?: "Free run", style = SeedsType.bodyStrong, color = SeedsTheme.colors.ink)
                Text(route?.let { "${Geo.km(it.distanceM)} km planned" } ?: "Run anywhere — Seeds tracks it", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(SeedsTheme.colors.bg).navigationBarsPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val good = acc != null && acc <= 20f
                Icon(if (good) Icons.Rounded.GpsFixed else Icons.Rounded.GpsNotFixed, null, tint = if (good) SeedsTheme.colors.accent else SeedsTheme.colors.warn, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        !granted -> "Location needed to track your run"
                        acc == null -> "Finding GPS… step outside for a better signal"
                        good -> "GPS ready · ±${acc.toInt()} m"
                        else -> "Weak GPS · ±${acc.toInt()} m — give it a moment"
                    },
                    style = SeedsType.label, color = SeedsTheme.colors.inkMuted,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("COUNTS TOWARD", style = SeedsType.overline, color = SeedsTheme.colors.inkFaint, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                seeds.forEach { s -> ChoiceChip(s.name, seedId == s.id, { seedTouched = true; seedId = s.id }) }
                ChoiceChip("Nothing", seedId == null, { seedTouched = true; seedId = null })
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                ChoiceChip(if (voice) "Voice cues on" else "Voice cues off", voice, {
                    voice = !voice; prefs.edit().putBoolean("run_voice", voice).apply()
                }, icon = Icons.Rounded.RecordVoiceOver)
            }
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.size(92.dp).shadow(12.dp, CircleShape).clip(CircleShape).background(SeedsTheme.colors.sprout).clickable { start() },
                contentAlignment = Alignment.Center,
            ) { Text("START", style = SeedsType.subtitle, color = Ink) }
        }

        AnimatedVisibility(countdown > 0, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(SeedsTheme.colors.bg.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
                AnimatedContent(countdown, transitionSpec = { scaleIn(initialScale = 1.6f) + fadeIn() togetherWith fadeOut() }, label = "count") { n ->
                    Text("$n", style = SeedsType.display.copy(fontSize = 160.sp, lineHeight = 160.sp), color = SeedsTheme.colors.sprout)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// During the run
// ---------------------------------------------------------------------------------------------

@Composable
private fun ActiveRun(r: LiveRun) {
    val ctx = LocalContext.current
    val now by rememberNow(1000)
    val c = SeedsTheme.colors
    val paused = r.phase == RunPhase.PAUSED
    val me = r.track.lastOrNull()
    val camera = remember { Camera(center = me ?: r.route.firstOrNull() ?: KIGALI, zoom = 17.0, key = 1) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.name.uppercase(), style = SeedsType.overline, color = c.inkMuted, modifier = Modifier.weight(1f))
                Text(
                    when { paused -> "PAUSED"; r.autoPaused -> "AUTO-PAUSED"; r.phase == RunPhase.SAVING -> "SAVING…"; else -> "" },
                    style = SeedsType.overline, color = c.warn,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Geo.km(r.distanceM), style = SeedsType.display.copy(fontSize = 84.sp, lineHeight = 88.sp), color = c.ink)
                Spacer(Modifier.width(8.dp))
                Text("km", style = SeedsType.title, color = c.inkMuted, modifier = Modifier.padding(bottom = 14.dp))
            }
            Row(Modifier.fillMaxWidth()) {
                BigStat("TIME", Fmt.timer(r.moving(now)), Modifier.weight(1f))
                BigStat("PACE", Geo.pace(r.currentPace), Modifier.weight(1f))
                BigStat("AVG", Geo.pace(r.avgPace), Modifier.weight(1f))
            }
            if (r.cadence > 0 || r.steps > 0) {
                Spacer(Modifier.height(6.dp))
                Text("${r.cadence} steps/min · ${r.steps} steps" + if (r.elevationGain >= 1) " · ↑ ${r.elevationGain.toInt()} m" else "", style = SeedsType.caption, color = c.inkMuted)
            }
            if (r.routeLength > 0) {
                Spacer(Modifier.height(12.dp))
                Meter((r.alongM / r.routeLength).toFloat(), c.accent, height = 8.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (r.remainingM < 30) "Route complete — finish when you're ready" else "${Geo.km(r.remainingM, 1)} km to go of ${Geo.km(r.routeLength, 1)}",
                    style = SeedsType.label, color = c.inkMuted,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            SeedsMap(Modifier.fillMaxSize(), camera, planned = r.route, track = r.track, me = me, followMe = true)
            if (r.offRoute) {
                Row(
                    Modifier.align(Alignment.TopCenter).padding(12.dp).clip(Pill).background(c.drift).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.WrongLocation, null, tint = Ink, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Off route by ${r.offRouteM?.toInt()} m", style = SeedsType.label, color = Ink)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(if (paused) c.sprout else c.surfaceHi)
                    .clickable { RunService.send(ctx, if (paused) RunService.ACTION_RESUME else RunService.ACTION_PAUSE) },
                contentAlignment = Alignment.Center,
            ) { Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (paused) "Resume" else "Pause", tint = if (paused) Ink else c.ink, modifier = Modifier.size(34.dp)) }
            HoldToFinish { RunService.send(ctx, RunService.ACTION_FINISH) }
        }
    }
}

@Composable
private fun BigStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = SeedsType.overline, color = SeedsTheme.colors.inkMuted)
        Text(value, style = SeedsType.headline.copy(fontFeatureSettings = "tnum"), color = SeedsTheme.colors.ink)
    }
}

/** A finish button you can't hit by accident in a pocket. */
@Composable
private fun HoldToFinish(onFinish: () -> Unit) {
    val c = SeedsTheme.colors
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val hold = remember { Animatable(0f) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(76.dp).pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        hold.animateTo(1f, tween(1200))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFinish()
                    }
                    tryAwaitRelease()
                    if (hold.value < 1f) { job.cancel(); scope.launch { hold.animateTo(0f, tween(200)) } }
                })
            },
            contentAlignment = Alignment.Center,
        ) {
            val ring = c.drift
            val track = c.line
            Canvas(Modifier.fillMaxSize()) {
                val s = 6.dp.toPx()
                drawArc(track, 0f, 360f, false, Offset(s / 2, s / 2), Size(size.width - s, size.height - s), style = Stroke(s))
                drawArc(ring, -90f, 360f * hold.value, false, Offset(s / 2, s / 2), Size(size.width - s, size.height - s), style = Stroke(s, cap = StrokeCap.Round))
            }
            Box(Modifier.size(58.dp).clip(CircleShape).background(c.drift.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Stop, "Finish", tint = c.drift, modifier = Modifier.size(30.dp))
            }
        }
        Text("Hold to finish", style = SeedsType.caption, color = c.inkMuted, textAlign = TextAlign.Center)
    }
}
