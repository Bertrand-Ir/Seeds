package app.seeds.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seeds.data.db.Period
import app.seeds.data.db.SessionKind
import app.seeds.data.prefs.Settings
import app.seeds.domain.Engine
import app.seeds.domain.Fmt
import app.seeds.domain.Overview
import app.seeds.domain.SeedStat
import app.seeds.seeds
import app.seeds.ui.components.ButtonStyle
import app.seeds.ui.components.DayStrip
import app.seeds.ui.components.EmptyState
import app.seeds.ui.components.Meter
import app.seeds.ui.components.PillButton
import app.seeds.ui.components.Ring
import app.seeds.ui.components.SectionHeader
import app.seeds.ui.components.SeedGlyph
import app.seeds.ui.components.SeedsCard
import app.seeds.ui.components.SeedsSheet
import app.seeds.ui.components.StatTile
import app.seeds.ui.components.rememberNow
import app.seeds.ui.rememberDayData
import app.seeds.ui.switcher.QuickSwitchContent
import app.seeds.ui.theme.Dimens
import app.seeds.ui.theme.Ink
import app.seeds.ui.theme.InnerShape
import app.seeds.ui.theme.Pill
import app.seeds.ui.theme.SeedIcons
import app.seeds.ui.theme.SeedPalette
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType
import app.seeds.ui.theme.seedTone
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@Composable
fun TodayScreen(
    settings: Settings,
    onOpenReview: () -> Unit,
    onPlantSeed: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    val ctx = LocalContext.current
    val c = ctx.seeds
    val scope = rememberCoroutineScope()
    val data = rememberDayData(settings)
    val now by rememberNow(1000)
    val bucket = now / 10_000
    val o = remember(data, bucket, data.sessions) { Engine.overview(data.clock, data.seeds, data.sessions, now) }
    val update by c.updates.available.collectAsStateWithLifecycle()

    var showSwitch by rememberSaveable { mutableStateOf(false) }
    var logFor by remember { mutableStateOf<Long?>(null) }
    var showLog by remember { mutableStateOf(false) }

    val colorOf: (Long?) -> Int? = { id -> data.seeds.firstOrNull { it.id == id }?.color }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, top = 8.dp, bottom = 140.dp),
    ) {
        item { Header(settings, o, now) }

        update?.let { r ->
            item {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(InnerShape).background(SeedsTheme.colors.accentSoft).clickable(onClick = onOpenUpdate).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.SystemUpdate, null, tint = SeedsTheme.colors.accent)
                    Spacer(Modifier.width(12.dp))
                    Text("Seeds ${r.version} is ready", style = SeedsType.bodyStrong, color = SeedsTheme.colors.ink, modifier = Modifier.weight(1f))
                    Text("Update", style = SeedsType.label, color = SeedsTheme.colors.accent)
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            NowCard(o, now, onSwitch = { showSwitch = true }, onStart = { id -> scope.launch { c.tracking.start(id) } }, onBreak = {
                scope.launch { c.tracking.start(null, SessionKind.BREAK) }
            }, onStop = { scope.launch { c.tracking.stop() } })
        }

        item {
            Spacer(Modifier.height(Dimens.gap))
            SeedsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("YOUR DAY", style = SeedsType.overline, color = SeedsTheme.colors.inkMuted, modifier = Modifier.weight(1f))
                    val left = (o.sleepAt - now).coerceAtLeast(0)
                    Text(if (left > 0) "${Fmt.duration(left)} until bed" else "Past bedtime", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
                }
                Spacer(Modifier.height(14.dp))
                DayStrip(o, colorOf)
                Spacer(Modifier.height(16.dp))
                Row {
                    StatTile("Growing", Fmt.duration(o.focusMs), Modifier.weight(1f))
                    StatTile("Breaks", Fmt.duration(o.breakMs), Modifier.weight(1f))
                    StatTile("Drift", Fmt.duration(o.driftMs), Modifier.weight(1f), valueColor = if (o.driftMs > 0) SeedsTheme.colors.drift else SeedsTheme.colors.ink)
                }
            }
        }

        if (data.loaded && data.seeds.isEmpty()) {
            item {
                EmptyState("No seeds yet", "A seed is a non-negotiable you give time to — daily, weekly or monthly.") {
                    PillButton("Plant a seed", onPlantSeed, icon = Icons.Rounded.Add)
                }
            }
        }

        val dailyToday = o.daily.filter { it.appliesToday }
        if (dailyToday.isNotEmpty()) {
            item { SectionHeader("Today · ${o.grown} of ${o.dailyCount} grown", action = "Log time", onAction = { logFor = null; showLog = true }) }
            items(dailyToday, key = { "d${it.seed.id}" }) { st ->
                SeedRow(st, o.active?.seedId == st.seed.id && o.active.kind == SessionKind.SEED,
                    onTap = { scope.launch { if (o.active?.seedId == st.seed.id) c.tracking.stop() else c.tracking.start(st.seed.id) } },
                    onLong = { logFor = st.seed.id; showLog = true })
            }
        }
        if (o.weekly.isNotEmpty()) {
            item { SectionHeader("This week") }
            items(o.weekly, key = { "w${it.seed.id}" }) { st ->
                SeedRow(st, o.active?.seedId == st.seed.id,
                    onTap = { scope.launch { if (o.active?.seedId == st.seed.id) c.tracking.stop() else c.tracking.start(st.seed.id) } },
                    onLong = { logFor = st.seed.id; showLog = true })
            }
        }
        if (o.monthly.isNotEmpty()) {
            item { SectionHeader("This month") }
            items(o.monthly, key = { "m${it.seed.id}" }) { st ->
                SeedRow(st, o.active?.seedId == st.seed.id,
                    onTap = { scope.launch { if (o.active?.seedId == st.seed.id) c.tracking.stop() else c.tracking.start(st.seed.id) } },
                    onLong = { logFor = st.seed.id; showLog = true })
            }
        }
        val resting = o.daily.filter { !it.appliesToday }
        if (resting.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text("Resting today: " + resting.joinToString { it.seed.name }, style = SeedsType.caption, color = SeedsTheme.colors.inkFaint)
            }
        }

        // Evening: the review is ready from two hours before bed.
        if (now >= o.sleepAt - 2 * 3_600_000L) {
            item {
                Spacer(Modifier.height(20.dp))
                SeedsCard(color = SeedsTheme.colors.surfaceHi, onClick = onOpenReview) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NightsStay, null, tint = SeedsTheme.colors.accent)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Your day is ready to review", style = SeedsType.bodyStrong, color = SeedsTheme.colors.ink)
                            Text("A minute of honesty before sleep.", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
                        }
                    }
                }
            }
        }
    }

    if (showSwitch) SeedsSheet(onDismiss = { showSwitch = false }) { QuickSwitchContent(settings) { showSwitch = false } }
    if (showLog) LogTimeSheet(settings, data.seeds, logFor) { showLog = false }
}

@Composable
private fun Header(s: Settings, o: Overview, now: Long) {
    val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
    val greeting = when (hour) {
        in 4..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Late night"
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(Fmt.dayLong(o.day), style = SeedsType.label, color = SeedsTheme.colors.inkMuted)
            Text(if (s.name.isBlank()) greeting else "$greeting, ${s.name.substringBefore(' ')}", style = SeedsType.headline, color = SeedsTheme.colors.ink, maxLines = 2)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            Ring((o.score ?: 0) / 100f, SeedsTheme.colors.accent, Modifier.size(60.dp), stroke = 6.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(o.score?.toString() ?: "–", style = SeedsType.subtitle, color = SeedsTheme.colors.ink)
            }
        }
    }
}

@Composable
private fun NowCard(o: Overview, now: Long, onSwitch: () -> Unit, onStart: (Long) -> Unit, onBreak: () -> Unit, onStop: () -> Unit) {
    val a = o.active
    AnimatedContent(targetState = a?.id, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) }, label = "now") { id ->
        if (id != null && a != null) {
            val seed = o.activeSeed
            val fill = when (a.kind) {
                SessionKind.SEED -> seed?.let { SeedPalette.fillColor(it.color) } ?: SeedsTheme.colors.sprout
                SessionKind.BREAK -> Color(0xFF8CCBFF)
                SessionKind.DRIFT -> Color(0xFFFF8A75)
            }
            val title = when (a.kind) {
                SessionKind.SEED -> seed?.name ?: "Untitled"
                SessionKind.BREAK -> "On a break"
                SessionKind.DRIFT -> "Drifting"
            }
            val stat = o.activeStat
            SeedsCard(color = fill, padding = PaddingValues(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pulse(Ink)
                    Spacer(Modifier.width(8.dp))
                    Text("NOW GROWING", style = SeedsType.overline, color = Ink.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                    if (seed != null) Icon(SeedIcons.of(seed.icon), null, tint = Ink, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(title, style = SeedsType.title, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Fmt.timer(now - a.startedAt), style = SeedsType.display, color = Ink)
                if (stat != null && stat.targetMs > 0) {
                    Spacer(Modifier.height(12.dp))
                    Meter(stat.progress, Ink, track = Ink.copy(alpha = 0.14f), height = 8.dp)
                    Spacer(Modifier.height(8.dp))
                    val per = when (stat.seed.period) { Period.DAILY -> "today"; Period.WEEKLY -> "this week"; Period.MONTHLY -> "this month" }
                    Text(
                        "${Fmt.duration(stat.doneMs)} of ${Fmt.duration(stat.targetMs)} $per" +
                            if (stat.grown) " · grown ✓" else " · ${Fmt.duration(stat.remainingMs)} to go",
                        style = SeedsType.label, color = Ink.copy(alpha = 0.75f),
                    )
                } else {
                    Text("Since ${Fmt.clock(a.startedAt)}", style = SeedsType.label, color = Ink.copy(alpha = 0.75f))
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InkButton("Switch", Icons.Rounded.SwapHoriz, filled = false, modifier = Modifier.weight(1f), onClick = onSwitch)
                    InkButton("Done", null, filled = true, modifier = Modifier.weight(1f), onClick = onStop)
                }
            }
        } else {
            SeedsCard(padding = PaddingValues(22.dp)) {
                Text("NOTHING GROWING", style = SeedsType.overline, color = SeedsTheme.colors.inkMuted)
                Spacer(Modifier.height(8.dp))
                Text("What are you giving your time to?", style = SeedsType.title, color = SeedsTheme.colors.ink)
                val next = o.nextUp
                Spacer(Modifier.height(16.dp))
                if (next != null) {
                    PillButton("Start ${next.seed.name}", { onStart(next.seed.id) }, Modifier.fillMaxWidth(), style = ButtonStyle.Sprout, icon = Icons.Rounded.PlayArrow)
                    Spacer(Modifier.height(10.dp))
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (o.daily.filter { it.appliesToday } + o.weekly + o.monthly).filter { it.seed.id != next?.seed?.id }.forEach { st ->
                        QuickChip(st) { onStart(st.seed.id) }
                    }
                    Row(
                        Modifier.clip(Pill).background(SeedsTheme.colors.surfaceHi).clickable(onClick = onBreak).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Coffee, null, tint = SeedsTheme.colors.inkMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Break", style = SeedsType.label, color = SeedsTheme.colors.ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickChip(st: SeedStat, onClick: () -> Unit) {
    Row(
        Modifier.clip(Pill).background(SeedsTheme.colors.surfaceHi).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SeedIcons.of(st.seed.icon), null, tint = seedTone(st.seed.color), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(st.seed.name, style = SeedsType.label, color = SeedsTheme.colors.ink, maxLines = 1)
    }
}

@Composable
private fun InkButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .height(48.dp)
            .clip(Pill)
            .background(if (filled) Ink else Ink.copy(alpha = 0.1f))
            .clickable { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (filled) Color.White else Ink
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = SeedsType.bodyStrong, color = fg)
    }
}

@Composable
fun Pulse(color: Color) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(8.dp).alpha(a).clip(CircleShape).background(color))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeedRow(st: SeedStat, active: Boolean, onTap: () -> Unit, onLong: () -> Unit) {
    val c = SeedsTheme.colors
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(InnerShape)
            .background(if (active) c.accentSoft else c.surface)
            .combinedClickable(onClick = onTap, onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLong() })
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeedGlyph(st.seed.icon, st.seed.color, st.progress, 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(st.seed.name, style = SeedsType.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = when {
                st.grown -> "Grown · ${Fmt.duration(st.doneMs)} of ${Fmt.duration(st.targetMs)}"
                st.seed.period == Period.DAILY -> "${Fmt.duration(st.doneMs)} of ${Fmt.duration(st.targetMs)}"
                else -> {
                    val need = (st.paceTodayMs - st.todayMs).coerceAtLeast(0)
                    "${Fmt.duration(st.doneMs)} of ${Fmt.duration(st.targetMs)} · " +
                        if (need > 0) "${Fmt.duration(need)} today keeps pace" else "on pace today"
                }
            }
            Text(line, style = SeedsType.caption, color = if (st.grown) seedTone(st.seed.color) else c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        if (active) {
            Row(
                Modifier.clip(Pill).background(c.accent).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pulse(c.onAccent)
                Spacer(Modifier.width(6.dp))
                Text("Growing", style = SeedsType.caption, color = c.onAccent)
            }
        } else {
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.surfaceHi), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = c.ink, modifier = Modifier.size(20.dp))
            }
        }
    }
}
