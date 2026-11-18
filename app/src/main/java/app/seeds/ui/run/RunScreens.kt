package app.seeds.ui.run

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seeds.data.db.Run
import app.seeds.data.db.RunRoute
import app.seeds.domain.Fmt
import app.seeds.domain.Geo
import app.seeds.domain.PolylineCodec
import app.seeds.run.RunState
import app.seeds.seeds
import app.seeds.ui.components.ButtonStyle
import app.seeds.ui.components.EmptyState
import app.seeds.ui.components.PillButton
import app.seeds.ui.components.ScreenTitle
import app.seeds.ui.components.SectionHeader
import app.seeds.ui.components.SeedsCard
import app.seeds.ui.components.StatTile
import app.seeds.ui.theme.Dimens
import app.seeds.ui.theme.Ink
import app.seeds.ui.theme.InnerShape
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType
import app.seeds.ui.today.Pulse
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

private val zone get() = ZoneId.systemDefault()
private fun Run.date(): LocalDate = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()

@Composable
fun RunScreen(onPlanRoute: (Long) -> Unit, onStartRun: (Long) -> Unit, onOpenRun: (Long) -> Unit) {
    val c = LocalContext.current.seeds
    val runsFlow = remember { c.db.runs().observeAll() }
    val routesFlow = remember { c.db.routes().observeAll() }
    val runs by runsFlow.collectAsStateWithLifecycle(emptyList())
    val routes by routesFlow.collectAsStateWithLifecycle(emptyList())
    val live by RunState.live.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val thisWeek = runs.filter { !it.date().isBefore(weekStart) }
    val thisMonth = runs.filter { it.date().withDayOfMonth(1) == today.withDayOfMonth(1) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, top = 8.dp, bottom = 140.dp),
    ) {
        item { ScreenTitle("Move your body, clear your mind", "Run") }

        if (live != null && RunState.isActive) item {
            Spacer(Modifier.height(16.dp))
            SeedsCard(color = SeedsTheme.colors.accent, onClick = { onStartRun(live?.routeId ?: 0) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pulse(SeedsTheme.colors.onAccent)
                    Spacer(Modifier.width(10.dp))
                    Text("Run in progress · ${Geo.km(live!!.distanceM)} km", style = SeedsType.subtitle, color = SeedsTheme.colors.onAccent, modifier = Modifier.weight(1f))
                    Text("Return", style = SeedsType.label, color = SeedsTheme.colors.onAccent)
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            SeedsCard(color = SeedsTheme.colors.sprout, padding = PaddingValues(22.dp)) {
                Text("LACE UP", style = SeedsType.overline, color = Ink.copy(alpha = 0.6f))
                Text(if (thisWeek.isEmpty()) "No runs yet this week." else "${Geo.km(thisWeek.sumOf { it.distanceM }, 1)} km this week.", style = SeedsType.headline, color = Ink)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.weight(1f).height(52.dp).clip(CircleShape).background(Ink).clickable { onStartRun(0) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = SeedsTheme.colors.sprout)
                        Spacer(Modifier.width(6.dp))
                        Text("Free run", style = SeedsType.bodyStrong, color = androidx.compose.ui.graphics.Color.White)
                    }
                    Row(
                        Modifier.weight(1f).height(52.dp).clip(CircleShape).background(Ink.copy(alpha = 0.1f)).clickable { onPlanRoute(0) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Rounded.Route, null, tint = Ink)
                        Spacer(Modifier.width(6.dp))
                        Text("Plan a route", style = SeedsType.bodyStrong, color = Ink)
                    }
                }
            }
        }

        item { SectionHeader("Your routes", action = "New", onAction = { onPlanRoute(0) }) }
        item {
            if (routes.isEmpty()) {
                SeedsCard(onClick = { onPlanRoute(0) }) {
                    Text("Plan your first route", style = SeedsType.bodyStrong, color = SeedsTheme.colors.ink)
                    Text("Tap points on the map — Seeds follows real paths, measures it, and guides you on the run.", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(routes, key = { it.id }) { r -> RouteCard(r, runs.count { it.routeId == r.id }, { onStartRun(r.id) }, { onPlanRoute(r.id) }) }
                    item {
                        Box(
                            Modifier.size(150.dp, 196.dp).clip(InnerShape).background(SeedsTheme.colors.surface).clickable { onPlanRoute(0) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.Add, null, tint = SeedsTheme.colors.accent)
                                Text("New route", style = SeedsType.label, color = SeedsTheme.colors.ink)
                            }
                        }
                    }
                }
            }
        }

        if (runs.isEmpty()) {
            item { EmptyState("Your first run is waiting", "Every run you finish shows up here — distance, pace, splits, and how you're improving.") }
        } else {
            item { SectionHeader("Progress") }
            item {
                SeedsCard {
                    Row {
                        StatTile("This week", "${Geo.km(thisWeek.sumOf { it.distanceM }, 1)} km", Modifier.weight(1f), sub = "${thisWeek.size} run${if (thisWeek.size == 1) "" else "s"}")
                        StatTile("This month", "${Geo.km(thisMonth.sumOf { it.distanceM }, 1)} km", Modifier.weight(1f), sub = Fmt.duration(thisMonth.sumOf { it.movingMs }))
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("KM PER WEEK · LAST 12 WEEKS", style = SeedsType.overline, color = SeedsTheme.colors.inkMuted)
                    Spacer(Modifier.height(10.dp))
                    WeeklyBars(runs, weekStart)
                }
            }
            item { Spacer(Modifier.height(10.dp)); PaceTrend(runs) }
            item { SectionHeader("Records") }
            item { RecordsCard(records(runs), onOpenRun) }
            item { SectionHeader("Recent runs") }
            items(runs.take(30), key = { "r${it.id}" }) { r -> RunRow(r) { onOpenRun(r.id) } }
        }
    }
}

@Composable
private fun RouteCard(r: RunRoute, timesRun: Int, onRun: () -> Unit, onEdit: () -> Unit) {
    val pts = remember(r.encoded) { PolylineCodec.decode(r.encoded) }
    Column(Modifier.width(150.dp).clip(InnerShape).background(SeedsTheme.colors.surface).clickable(onClick = onRun).padding(12.dp)) {
        Box(Modifier.fillMaxWidth().height(96.dp).clip(InnerShape).background(SeedsTheme.colors.surfaceHi)) {
            RouteShape(pts, SeedsTheme.colors.accent, Modifier.fillMaxSize().padding(8.dp))
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).clip(CircleShape).background(SeedsTheme.colors.surface).clickable(onClick = onEdit), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Edit, "Edit", tint = SeedsTheme.colors.inkMuted, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(r.name, style = SeedsType.bodyStrong, color = SeedsTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${Geo.km(r.distanceM, 1)} km" + if (timesRun > 0) " · run $timesRun×" else "", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
        Text(
            r.scheduleMin?.let { m -> scheduleText(r.scheduleDays) + " · " + Fmt.clockOfDay(m) } ?: "Tap to run",
            style = SeedsType.caption, color = SeedsTheme.colors.accent, maxLines = 1,
        )
    }
}

fun scheduleText(days: Int): String {
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return when (days) {
        0b1111111 -> "Every day"
        0b0011111 -> "Weekdays"
        0b1100000 -> "Weekends"
        else -> names.filterIndexed { i, _ -> days and (1 shl i) != 0 }.joinToString(", ")
    }
}

@Composable
private fun WeeklyBars(runs: List<Run>, thisWeekStart: LocalDate) {
    val weeks = (11 downTo 0).map { thisWeekStart.minusWeeks(it.toLong()) }
    val km = weeks.map { w -> runs.filter { val d = it.date(); !d.isBefore(w) && d.isBefore(w.plusWeeks(1)) }.sumOf { it.distanceM } / 1000 }
    val max = (km.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val accent = SeedsTheme.colors.accent
    val track = SeedsTheme.colors.surfaceHi
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val gap = 6.dp.toPx()
        val w = (size.width - gap * (km.size - 1)) / km.size
        km.forEachIndexed { i, v ->
            val x = i * (w + gap)
            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(6.dp.toPx()))
            val h = (size.height * (v / max)).toFloat()
            if (v > 0) drawRoundRect(if (i == km.lastIndex) accent else accent.copy(alpha = 0.55f), Offset(x, size.height - h), Size(w, h), CornerRadius(6.dp.toPx()))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text("12 weeks ago", style = SeedsType.caption, color = SeedsTheme.colors.inkFaint, modifier = Modifier.weight(1f))
        Text("This week · ${"%.1f".format(km.last())} km", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
    }
}

@Composable
private fun PaceTrend(runs: List<Run>) {
    val pts = runs.filter { it.distanceM >= 1000 }.take(12).reversed()
    if (pts.size < 2) return
    val paces = pts.map { it.paceSecPerKm }
    val first = paces.take(3).average(); val last = paces.takeLast(3).average()
    val delta = (first - last).toInt()
    val accent = SeedsTheme.colors.accent
    SeedsCard {
        Text("AVERAGE PACE · LAST ${pts.size} RUNS", style = SeedsType.overline, color = SeedsTheme.colors.inkMuted)
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                delta > 3 -> "${delta}s/km faster than when you started. It's working."
                delta < -3 -> "${-delta}s/km slower lately — rest well, then build again."
                else -> "Holding steady at ${Geo.pace(last.toInt())} /km."
            },
            style = SeedsType.bodyStrong, color = SeedsTheme.colors.ink,
        )
        Spacer(Modifier.height(12.dp))
        val min = paces.min(); val max = paces.max().coerceAtLeast(min + 1)
        Canvas(Modifier.fillMaxWidth().height(80.dp)) {
            val step = size.width / (paces.size - 1)
            // faster = higher
            fun y(p: Int) = size.height * ((p - min).toFloat() / (max - min))
            val path = Path().apply { paces.forEachIndexed { i, p -> if (i == 0) moveTo(0f, y(p)) else lineTo(i * step, y(p)) } }
            drawPath(path, accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            paces.forEachIndexed { i, p -> drawCircle(accent, 4.dp.toPx(), Offset(i * step, y(p))) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("Best ${Geo.pace(min)}", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted, modifier = Modifier.weight(1f))
            Text("Latest ${Geo.pace(paces.last())}", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
        }
    }
}

@Composable
private fun RecordsCard(r: Records, onOpen: (Long) -> Unit) {
    SeedsCard {
        Row {
            Record("Fastest 1 km", r.best1k?.best1kSec?.let(::mmss), Modifier.weight(1f)) { r.best1k?.let { onOpen(it.id) } }
            Record("Fastest 5 km", r.best5k?.best5kSec?.let(::mmss), Modifier.weight(1f)) { r.best5k?.let { onOpen(it.id) } }
        }
        Spacer(Modifier.height(14.dp))
        Row {
            Record("Longest run", r.longest?.let { "${Geo.km(it.distanceM)} km" }, Modifier.weight(1f)) { r.longest?.let { onOpen(it.id) } }
            Record("Best avg pace", r.fastestAvg?.let { Geo.pace(it.paceSecPerKm) + " /km" }, Modifier.weight(1f)) { r.fastestAvg?.let { onOpen(it.id) } }
        }
    }
}

@Composable
private fun Record(label: String, value: String?, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(InnerShape).clickable(enabled = value != null, onClick = onClick).padding(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EmojiEvents, null, tint = if (value != null) SeedsTheme.colors.warn else SeedsTheme.colors.inkFaint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label.uppercase(), style = SeedsType.overline, color = SeedsTheme.colors.inkMuted)
        }
        Text(value ?: "—", style = SeedsType.number, color = SeedsTheme.colors.ink)
    }
}

@Composable
private fun RunRow(r: Run, onClick: () -> Unit) {
    val pts = remember(r.track) { PolylineCodec.decode(r.track) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(InnerShape).background(SeedsTheme.colors.surface).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(InnerShape).background(SeedsTheme.colors.surfaceHi)) {
            RouteShape(pts, SeedsTheme.colors.accent, Modifier.fillMaxSize().padding(6.dp), stroke = 3.5f)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, style = SeedsType.bodyStrong, color = SeedsTheme.colors.ink, maxLines = 1)
            Text("${Fmt.relativeDay(r.date(), LocalDate.now())} · ${Fmt.clock(r.startedAt)} · ${Fmt.timer(r.movingMs)}", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${Geo.km(r.distanceM)} km", style = SeedsType.bodyStrong, color = SeedsTheme.colors.ink)
            Text("${Geo.pace(r.paceSecPerKm)} /km", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// One run, in detail
// ---------------------------------------------------------------------------------------------

@Composable
fun RunDetailScreen(runId: Long, onBack: () -> Unit) {
    val c = LocalContext.current.seeds
    val scope = rememberCoroutineScope()
    var run by remember { mutableStateOf<Run?>(null) }
    var prev by remember { mutableStateOf<Run?>(null) }
    var planned by remember { mutableStateOf<List<app.seeds.domain.LatLng>>(emptyList()) }
    var all by remember { mutableStateOf<List<Run>>(emptyList()) }
    LaunchedEffect(runId) {
        val r = c.db.runs().get(runId) ?: return@LaunchedEffect onBack()
        run = r
        prev = r.routeId?.let { c.db.runs().previousOnRoute(it, r.id) }?.takeIf { it.startedAt < r.startedAt }
        planned = r.routeId?.let { c.db.routes().get(it) }?.let { PolylineCodec.decode(it.encoded) }.orEmpty()
        all = c.db.runs().all()
    }
    val r = run ?: return
    val track = remember(r.track) { PolylineCodec.decode(r.track) }
    val splits = r.splitList
    val rec = records(all)

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, bottom = 60.dp)) {
        item {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = SeedsTheme.colors.ink) }
            ScreenTitle("${Fmt.dayLong(r.date())} · ${Fmt.clock(r.startedAt)}", r.name)
        }
        item {
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(260.dp).clip(InnerShape)) {
                SeedsMap(Modifier.fillMaxSize(), Camera(fit = track.ifEmpty { planned }, key = 1), planned = planned, track = track, waypoints = listOfNotNull(track.firstOrNull()))
            }
        }
        item {
            val badges = buildList {
                if (rec.best1k?.id == r.id) add("New 1 km record")
                if (rec.best5k?.id == r.id) add("New 5 km record")
                if (rec.longest?.id == r.id && all.size > 1) add("Longest run yet")
            }
            if (badges.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    badges.forEach { b ->
                        Row(Modifier.clip(CircleShape).background(SeedsTheme.colors.warn.copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.EmojiEvents, null, tint = SeedsTheme.colors.warn, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(b, style = SeedsType.caption, color = SeedsTheme.colors.ink)
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            SeedsCard {
                Row {
                    StatTile("Distance", "${Geo.km(r.distanceM)} km", Modifier.weight(1f))
                    StatTile("Time", Fmt.timer(r.movingMs), Modifier.weight(1f))
                    StatTile("Avg pace", Geo.pace(r.paceSecPerKm), Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Row {
                    StatTile("Best km", splits.minOrNull()?.let(::mmss) ?: "—", Modifier.weight(1f))
                    val mins = r.movingMs / 60_000.0
                    StatTile("Cadence", if (r.steps > 0 && mins > 0) "${(r.steps / mins).toInt()}" else "—", Modifier.weight(1f), sub = "steps/min")
                    StatTile("Climb", "${r.elevationGainM.toInt()} m", Modifier.weight(1f))
                }
            }
        }
        prev?.let { p ->
            item {
                Spacer(Modifier.height(12.dp))
                val diff = p.paceSecPerKm - r.paceSecPerKm
                SeedsCard(color = SeedsTheme.colors.surfaceHi) {
                    Text("VS. LAST TIME ON THIS ROUTE", style = SeedsType.overline, color = SeedsTheme.colors.inkMuted)
                    Text(
                        when {
                            diff > 2 -> "${diff}s/km faster. You're improving."
                            diff < -2 -> "${-diff}s/km slower than ${Fmt.dayShort(p.date())}. Still showed up — that counts."
                            else -> "Same pace as ${Fmt.dayShort(p.date())}. Consistency builds."
                        },
                        style = SeedsType.bodyStrong, color = SeedsTheme.colors.ink,
                    )
                }
            }
        }
        if (splits.isNotEmpty()) {
            item { SectionHeader("Splits") }
            item {
                SeedsCard {
                    val fastest = splits.min(); val slowest = splits.max().coerceAtLeast(fastest + 1)
                    splits.forEachIndexed { i, s ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Km ${i + 1}", style = SeedsType.label, color = SeedsTheme.colors.inkMuted, modifier = Modifier.width(52.dp))
                            val frac = 0.45f + 0.55f * (slowest - s).toFloat() / (slowest - fastest)
                            Box(Modifier.weight(1f).height(10.dp)) {
                                Box(
                                    Modifier.fillMaxWidth(frac).height(10.dp).clip(CircleShape)
                                        .background(if (s == fastest) SeedsTheme.colors.sprout else SeedsTheme.colors.accent.copy(alpha = 0.6f)),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(mmss(s), style = SeedsType.label, color = SeedsTheme.colors.ink, modifier = Modifier.width(48.dp))
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(24.dp))
            PillButton("Delete run", { scope.launch { c.db.runs().delete(r); onBack() } }, Modifier.fillMaxWidth(), style = ButtonStyle.Danger)
        }
    }
}

@Suppress("unused")
private fun daysBetween(a: LocalDate, b: LocalDate) = ChronoUnit.DAYS.between(a, b)
