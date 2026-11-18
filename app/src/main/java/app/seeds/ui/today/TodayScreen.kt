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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Widgets
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
import androidx.compose.ui.text.style.TextAlign
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
import app.seeds.ui.components.EmptyState
import app.seeds.ui.components.Meter
import app.seeds.ui.components.PillButton
import app.seeds.ui.components.Ring
import app.seeds.ui.components.SectionHeader
import app.seeds.ui.components.SeedGlyph
import app.seeds.ui.components.SeedsCard
import app.seeds.ui.components.SeedsSheet
import app.seeds.ui.components.rememberNow
import app.seeds.ui.plan.TodayPlanCard
import app.seeds.ui.rememberDayData
import app.seeds.ui.rememberResumeTick
import app.seeds.ui.switcher.QuickSwitchContent
import app.seeds.ui.theme.Dimens
import app.seeds.ui.theme.Ink
import app.seeds.ui.theme.InnerShape
import app.seeds.ui.theme.Pill
import app.seeds.ui.theme.SeedPalette
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType
import app.seeds.ui.theme.seedTone
import app.seeds.widget.Widgets
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Today answers three questions, top to bottom:
 *   1. How am I doing?        → one-line summary (tap for the full review)
 *   2. What am I doing now?   → the Now card, with one obvious next action
 *   3. What's left?           → your plan for this part of the day, then your seeds
 */
@Composable
fun TodayScreen(
    settings: Settings,
    onOpenReview: () -> Unit,
    onPlantSeed: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenPlan: (String) -> Unit,
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
    var showLog by remember { mutableStateOf(false) }

    val uiPrefs = remember { ctx.getSharedPreferences("seeds_ui", android.content.Context.MODE_PRIVATE) }
    val resume = rememberResumeTick()
    var tipDismissed by remember { mutableStateOf(uiPrefs.getBoolean("widget_tip_dismissed", false)) }
    val showWidgetTip = remember(resume, tipDismissed) { !tipDismissed && Widgets.canPin(ctx) && !Widgets.placed(ctx) }
    val dismissWidgetTip = { tipDismissed = true; uiPrefs.edit().putBoolean("widget_tip_dismissed", true).apply() }

    val toggle: (SeedStat) -> Unit = { st ->
        scope.launch { if (o.active?.seedId == st.seed.id) c.tracking.stop() else c.tracking.start(st.seed.id) }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, top = 8.dp, bottom = 140.dp),
    ) {
        item { Header(settings, o, now) }

        update?.let { r ->
            item {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(Pill).background(SeedsTheme.colors.accentSoft).clickable(onClick = onOpenUpdate)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.SystemUpdate, null, tint = SeedsTheme.colors.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Seeds ${r.version} is ready", style = SeedsType.label, color = SeedsTheme.colors.ink, modifier = Modifier.weight(1f))
                    Text("Update", style = SeedsType.label, color = SeedsTheme.colors.accent)
                }
            }
        }

        // 1 · How am I doing?
        item {
            Spacer(Modifier.height(16.dp))
            SummaryRow(o, onOpenReview)
        }

        // 2 · What am I doing now?
        item {
            Spacer(Modifier.height(Dimens.gap))
            NowCard(
                o, now,
                onSwitch = { showSwitch = true },
                onStart = { id -> scope.launch { c.tracking.start(id) } },
                onStop = { scope.launch { c.tracking.stop() } },
            )
        }

        // 3 · What's left?
        item {
            Spacer(Modifier.height(Dimens.gap))
            TodayPlanCard(settings, onOpen = { onOpenPlan("today") }, onPlanTomorrow = { onOpenPlan("tomorrow") })
        }

        if (data.loaded && data.seeds.isEmpty()) {
            item {
                EmptyState("No seeds yet", "A seed is something you commit time to — every day, week or month.") {
                    PillButton("Plant your first seed", onPlantSeed, icon = Icons.Rounded.Add)
                }
            }
        }

        val dailyToday = o.daily.filter { it.appliesToday }
        if (dailyToday.isNotEmpty()) {
            item { SectionHeader("Today's seeds · ${o.grown}/${o.dailyCount}", action = "+ Log past time", onAction = { showLog = true }) }
            items(dailyToday, key = { "d${it.seed.id}" }) { st ->
                SeedRow(st, o.active?.seedId == st.seed.id && o.active.kind == SessionKind.SEED) { toggle(st) }
            }
        }
        val longer = o.weekly + o.monthly
        if (longer.isNotEmpty()) {
            item { SectionHeader("This week & month") }
            items(longer, key = { "l${it.seed.id}" }) { st ->
                SeedRow(st, o.active?.seedId == st.seed.id) { toggle(st) }
            }
        }

        if (showWidgetTip) {
            item {
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth().clip(InnerShape).background(SeedsTheme.colors.surface).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Widgets, null, tint = SeedsTheme.colors.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Add Seeds to your home screen", style = SeedsType.label, color = SeedsTheme.colors.ink, modifier = Modifier.weight(1f))
                    Text("Later", style = SeedsType.label, color = SeedsTheme.colors.inkMuted, modifier = Modifier.clip(Pill).clickable { dismissWidgetTip() }.padding(8.dp))
                    Text("Add", style = SeedsType.label, color = SeedsTheme.colors.accent, modifier = Modifier.clip(Pill).clickable { Widgets.requestPin(ctx); dismissWidgetTip() }.padding(8.dp))
                }
            }
        }
    }

    if (showSwitch) SeedsSheet(onDismiss = { showSwitch = false }) { QuickSwitchContent(settings) { showSwitch = false } }
    if (showLog) LogTimeSheet(settings, data.seeds, null) { showLog = false }
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
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(Fmt.dayLong(o.day), style = SeedsType.label, color = SeedsTheme.colors.inkMuted)
        Text(if (s.name.isBlank()) greeting else "$greeting, ${s.name.substringBefore(' ')}", style = SeedsType.headline, color = SeedsTheme.colors.ink, maxLines = 2)
    }
}

/** How the day is going, in one glance. Tap for the full review. */
@Composable
private fun SummaryRow(o: Overview, onOpen: () -> Unit) {
    val c = SeedsTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(InnerShape).background(c.surface).clickable(onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Ring((o.score ?: 0) / 100f, c.accent, Modifier.size(52.dp), stroke = 5.dp)
            Text(o.score?.toString() ?: "–", style = SeedsType.bodyStrong, color = c.ink)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (o.dailyCount > 0) "${o.grown} of ${o.dailyCount} seeds grown" else "Your day so far",
                style = SeedsType.bodyStrong, color = c.ink,
            )
            Text(
                "${Fmt.duration(o.focusMs)} growing" + if (o.driftMs > 0) " · ${Fmt.duration(o.driftMs)} drift" else "",
                style = SeedsType.caption, color = if (o.driftMs > 30 * 60_000) c.drift else c.inkMuted,
            )
        }
        Text("Review", style = SeedsType.label, color = c.accent)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.accent, modifier = Modifier.size(18.dp))
    }
}

/** The one thing that matters right now — and the obvious next action. */
@Composable
private fun NowCard(o: Overview, now: Long, onSwitch: () -> Unit, onStart: (Long) -> Unit, onStop: () -> Unit) {
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
                    Text("NOW", style = SeedsType.overline, color = Ink.copy(alpha = 0.7f))
                }
                Spacer(Modifier.height(8.dp))
                Text(title, style = SeedsType.title, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Fmt.timer(now - a.startedAt), style = SeedsType.display, color = Ink)
                if (stat != null && stat.targetMs > 0) {
                    Spacer(Modifier.height(10.dp))
                    Meter(stat.progress, Ink, track = Ink.copy(alpha = 0.14f), height = 6.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (stat.grown) "Grown — ${Fmt.duration(stat.doneMs)} ✓" else "${Fmt.duration(stat.remainingMs)} left to grow ${periodWord(stat)}",
                        style = SeedsType.label, color = Ink.copy(alpha = 0.75f),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InkButton("Switch", Icons.Rounded.SwapHoriz, filled = false, modifier = Modifier.weight(1f), onClick = onSwitch)
                    InkButton("Done", null, filled = true, modifier = Modifier.weight(1f), onClick = onStop)
                }
            }
        } else {
            val next = o.nextUp
            SeedsCard(padding = PaddingValues(22.dp)) {
                Text("NOTHING GROWING", style = SeedsType.overline, color = SeedsTheme.colors.inkMuted)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (next != null) "What's next?" else if (o.dailyCount > 0) "Every seed has grown today." else "Ready when you are.",
                    style = SeedsType.title, color = SeedsTheme.colors.ink,
                )
                Spacer(Modifier.height(16.dp))
                if (next != null) {
                    PillButton("Start ${next.seed.name}", { onStart(next.seed.id) }, Modifier.fillMaxWidth(), style = ButtonStyle.Sprout, icon = Icons.Rounded.PlayArrow)
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    if (next != null) "or choose something else" else "Start something",
                    style = SeedsType.label, color = SeedsTheme.colors.accent, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(Pill).clickable(onClick = onSwitch).padding(10.dp),
                )
            }
        }
    }
}

private fun periodWord(st: SeedStat) = when (st.seed.period) {
    Period.DAILY -> "today"
    Period.WEEKLY -> "this week"
    Period.MONTHLY -> "this month"
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

/** One seed: its progress ring, its name, one plain line, and a single action. */
@Composable
private fun SeedRow(st: SeedStat, active: Boolean, onTap: () -> Unit) {
    val c = SeedsTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(InnerShape)
            .background(if (active) c.accentSoft else c.surface)
            .clickable(onClick = onTap)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeedGlyph(st.seed.icon, st.seed.color, st.progress, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(st.seed.name, style = SeedsType.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = when {
                st.grown -> "Grown ✓  ${Fmt.duration(st.doneMs)}"
                st.seed.period == Period.DAILY -> "${Fmt.duration(st.doneMs)} of ${Fmt.duration(st.targetMs)}"
                else -> "${Fmt.duration(st.doneMs)} of ${Fmt.duration(st.targetMs)} ${periodWord(st)}"
            }
            Text(line, style = SeedsType.caption, color = if (st.grown) seedTone(st.seed.color) else c.inkMuted, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        if (active) {
            Row(Modifier.clip(Pill).background(c.accent).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Pulse(c.onAccent)
                Spacer(Modifier.width(6.dp))
                Text("Growing", style = SeedsType.caption, color = c.onAccent)
            }
        } else if (!st.grown) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.surfaceHi), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, "Start ${st.seed.name}", tint = c.ink, modifier = Modifier.size(20.dp))
            }
        }
    }
}
