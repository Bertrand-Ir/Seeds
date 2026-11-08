package app.seeds.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seeds.data.db.Period
import app.seeds.data.prefs.Settings
import app.seeds.domain.DayClock
import app.seeds.domain.Fmt
import app.seeds.domain.GardenDay
import app.seeds.domain.Insights
import app.seeds.domain.InsightsEngine
import app.seeds.seeds
import app.seeds.tracking.AppLabels
import app.seeds.tracking.Usage
import app.seeds.ui.components.ChoiceChip
import app.seeds.ui.components.Dot
import app.seeds.ui.components.EmptyState
import app.seeds.ui.components.Meter
import app.seeds.ui.components.ScreenTitle
import app.seeds.ui.components.SectionHeader
import app.seeds.ui.components.SeedGlyph
import app.seeds.ui.components.SeedsCard
import app.seeds.ui.components.StatTile
import app.seeds.ui.components.rememberNow
import app.seeds.ui.theme.Dimens
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType
import app.seeds.ui.theme.seedTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun GardenScreen(settings: Settings) {
    val ctx = LocalContext.current
    val c = ctx.seeds
    val clock = remember(settings) { DayClock(settings) }
    var rangeDays by rememberSaveable { mutableIntStateOf(7) }
    val now by rememberNow(60_000)
    val today = clock.today(now)
    val from = remember(today) { clock.dayStart(today.minusDays(InsightsEngine.GARDEN_DAYS.toLong())) }
    val to = remember(today) { clock.dayEnd(today) }
    val seeds by c.tracking.allSeeds.collectAsStateWithLifecycle(emptyList())
    val sessionsFlow = remember(from, to) { c.tracking.sessions(from, to) }
    val sessions by sessionsFlow.collectAsStateWithLifecycle(emptyList())
    val insights = remember(seeds, sessions, rangeDays, now) {
        InsightsEngine.compute(clock, seeds.filter { !it.archived }, sessions, now, rangeDays)
    }

    var scroll by remember { mutableStateOf<List<Pair<String, Long>>?>(null) }
    LaunchedEffect(rangeDays, today, settings.distractingApps) {
        scroll = withContext(Dispatchers.IO) {
            if (!Usage.hasAccess(ctx)) null
            else Usage.usage(ctx, clock.dayStart(today.minusDays((rangeDays - 1).toLong())), System.currentTimeMillis(), settings.distractingApps)
                .entries.sortedByDescending { it.value }.map { AppLabels.label(ctx, it.key) to it.value }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, top = 8.dp, bottom = 140.dp),
    ) {
        item { ScreenTitle("The truth, kindly told", "Your garden") }
        item {
            Spacer(Modifier.height(16.dp))
            GardenCard(insights.garden)
        }
        item {
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("7 days", rangeDays == 7, { rangeDays = 7 })
                ChoiceChip("30 days", rangeDays == 30, { rangeDays = 30 })
                ChoiceChip("12 weeks", rangeDays == 84, { rangeDays = 84 })
            }
        }
        item {
            Spacer(Modifier.height(Dimens.gap))
            SummaryCard(insights, rangeDays)
        }

        if (insights.trends.isEmpty()) {
            item { EmptyState("Nothing planted yet", "Plant seeds in You → Seeds, and your garden fills in day by day.") }
        } else {
            item { SectionHeader("Planned vs. actual") }
            insights.trends.forEach { t ->
                item(key = "t${t.seed.id}") {
                    val ratio = if (t.plannedMs > 0) t.actualMs.toFloat() / t.plannedMs else 0f
                    SeedsCard(Modifier.padding(vertical = 4.dp), padding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SeedGlyph(t.seed.icon, t.seed.color, ratio.coerceAtMost(1f), 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.seed.name, style = SeedsType.bodyStrong, color = SeedsTheme.colors.ink)
                                Text("${Fmt.duration(t.actualMs)} of ${Fmt.duration(t.plannedMs)} planned", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
                            }
                            Text("${(ratio * 100).roundToInt()}%", style = SeedsType.number, color = if (ratio >= 0.9f) seedTone(t.seed.color) else SeedsTheme.colors.ink)
                        }
                        Spacer(Modifier.height(12.dp))
                        Meter(ratio, seedTone(t.seed.color), marker = 1f.coerceAtMost(if (ratio > 1f) 1f / ratio else 1f))
                        if (t.seed.period == Period.DAILY && (t.streak > 0 || t.best > 0)) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                (if (t.streak > 0) "${t.streak}-day streak" else "Streak resets today") + " · best ${t.best}",
                                style = SeedsType.caption, color = SeedsTheme.colors.inkMuted,
                            )
                        }
                    }
                }
            }
        }

        item { SectionHeader("Scroll time") }
        item {
            SeedsCard {
                val list = scroll
                when {
                    list == null -> Text("Give Seeds usage access (You → Permissions) to see real time spent in distracting apps.", style = SeedsType.body, color = SeedsTheme.colors.inkMuted)
                    list.isEmpty() -> Text("No time in distracting apps. That's rare — well done.", style = SeedsType.body, color = SeedsTheme.colors.inkMuted)
                    else -> {
                        val total = list.sumOf { it.second }
                        Text(Fmt.duration(total), style = SeedsType.headline, color = SeedsTheme.colors.drift)
                        Text("in the last ${if (rangeDays == 84) "12 weeks" else "$rangeDays days"} · ${Fmt.duration(total / rangeDays)} a day", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
                        Spacer(Modifier.height(14.dp))
                        val max = list.first().second.coerceAtLeast(1)
                        list.take(6).forEach { (name, ms) ->
                            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(name, style = SeedsType.label, color = SeedsTheme.colors.ink, modifier = Modifier.width(96.dp), maxLines = 1)
                                Meter(ms.toFloat() / max, SeedsTheme.colors.drift, Modifier.weight(1f), height = 6.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(Fmt.duration(ms), style = SeedsType.caption, color = SeedsTheme.colors.inkMuted, modifier = Modifier.width(56.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(i: Insights, rangeDays: Int) {
    val c = SeedsTheme.colors
    SeedsCard {
        Row {
            StatTile("Avg score", i.avgScore?.toString() ?: "–", Modifier.weight(1f))
            StatTile("Growing / day", Fmt.duration(i.avgFocusMs), Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Row {
            StatTile("Drift", Fmt.duration(i.totalDriftMs), Modifier.weight(1f), valueColor = if (i.totalDriftMs > 0) c.drift else c.ink)
            val tracked = i.totalFocusMs + i.totalBreakMs + i.totalDriftMs
            val pct = if (i.awakeMs > 0) (tracked * 100 / i.awakeMs).toInt() else 0
            StatTile("Awake time tracked", "$pct%", Modifier.weight(1f), sub = "Untracked: ${Fmt.duration((i.awakeMs - tracked).coerceAtLeast(0) / rangeDays)}/day")
        }
        i.bestDay?.let {
            Spacer(Modifier.height(16.dp))
            Text("Best day: ${Fmt.dayShort(it.date)} · ${it.score} · ${Fmt.duration(it.focusMs)} growing", style = SeedsType.caption, color = c.inkMuted)
        }
    }
}

/** 12 weeks × 7 days. Each day is drawn as a seed, a sprout or a bloom. */
@Composable
private fun GardenCard(days: List<GardenDay>) {
    val c = SeedsTheme.colors
    SeedsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LAST 12 WEEKS", style = SeedsType.overline, color = c.inkMuted, modifier = Modifier.weight(1f))
            Legend("Seed", c.inkFaint, 0.35f)
            Spacer(Modifier.width(10.dp))
            Legend("Sprout", c.accent.copy(alpha = 0.55f), 0.6f)
            Spacer(Modifier.width(10.dp))
            Legend("Bloom", c.accent, 1f)
        }
        Spacer(Modifier.height(14.dp))
        val weeks = days.chunked(7)
        val line = c.line
        val accent = c.accent
        val faint = c.inkFaint
        val ink = c.ink
        Canvas(Modifier.fillMaxWidth().aspectRatio(weeks.size / 7f)) {
            val cell = size.width / weeks.size
            weeks.forEachIndexed { w, week ->
                week.forEachIndexed { d, day ->
                    val center = Offset(cell * w + cell / 2, cell * d + cell / 2)
                    val maxR = cell * 0.42f
                    val score = day.score
                    when {
                        score == null -> drawCircle(line, maxR * 0.22f, center)
                        score == 0 -> drawCircle(faint, maxR * 0.5f, center, style = Stroke(1.5f.dp.toPx()))
                        score < 40 -> drawCircle(accent.copy(alpha = 0.35f), maxR * 0.5f, center)
                        score < 80 -> drawCircle(accent.copy(alpha = 0.65f), maxR * 0.75f, center)
                        else -> {
                            drawCircle(accent, maxR, center)
                            if (score >= 100) drawCircle(ink.copy(alpha = 0.85f), maxR * 0.22f, center)
                        }
                    }
                }
            }
            // today marker
            val lastW = weeks.lastIndex
            val lastD = weeks.last().lastIndex
            drawCircle(ink, cell * 0.48f, Offset(cell * lastW + cell / 2, cell * lastD + cell / 2), style = Stroke(1.5f.dp.toPx()))
        }
    }
}

@Composable
private fun Legend(label: String, color: Color, scale: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(10.dp), contentAlignment = Alignment.Center) { Dot(color, (10 * scale).coerceAtLeast(4f).dp) }
        Spacer(Modifier.width(4.dp))
        Text(label, style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
    }
}
