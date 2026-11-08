package app.seeds.ui.review

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.seeds.data.db.DayReview
import app.seeds.data.db.Direction
import app.seeds.data.db.Txn
import app.seeds.data.prefs.Settings
import app.seeds.domain.DayClock
import app.seeds.domain.Engine
import app.seeds.domain.Fmt
import app.seeds.domain.Overview
import app.seeds.seeds
import app.seeds.ui.components.Meter
import app.seeds.ui.components.PillButton
import app.seeds.ui.components.Ring
import app.seeds.ui.components.SectionHeader
import app.seeds.ui.components.SeedGlyph
import app.seeds.ui.components.SeedsCard
import app.seeds.ui.components.StatTile
import app.seeds.ui.money.seedsFieldColors
import app.seeds.ui.theme.Dimens
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType
import app.seeds.ui.theme.seedTone
import kotlinx.coroutines.launch
import java.time.LocalDate

private val moods = listOf("Heavy", "Meh", "Okay", "Good", "Great")

fun verdict(score: Int?): Pair<String, String> = when {
    score == null -> "A day of its own." to "No daily seeds were due today."
    score >= 95 -> "Full bloom." to "Every seed you planted for today grew. Remember how this felt."
    score >= 75 -> "Strong growth." to "Most of what matters got your time today."
    score >= 45 -> "Something grew." to "Not everything, but real progress. Tomorrow, start with the hardest seed."
    score > 0 -> "A seed is still a seed." to "Small days count. What got in the way?"
    else -> "Fallow ground." to "Nothing grew today, and that's honest. Tomorrow is fertile."
}

/** The nightly report: what you planned, what you actually did, and a moment to reflect. */
@Composable
fun ReviewScreen(settings: Settings, dateIso: String?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.seeds
    val scope = rememberCoroutineScope()
    val clock = remember(settings) { DayClock(settings) }
    val today = clock.today()
    var date by remember { mutableStateOf(dateIso?.let(LocalDate::parse) ?: today) }

    var o by remember { mutableStateOf<Overview?>(null) }
    var money by remember { mutableStateOf<List<Txn>>(emptyList()) }
    var review by remember { mutableStateOf<DayReview?>(null) }
    var mood by remember { mutableIntStateOf(0) }
    var win by remember { mutableStateOf("") }
    var lesson by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(date) {
        val now = minOf(System.currentTimeMillis(), clock.dayEnd(date) - 1)
        val range = Engine.overviewRange(clock, now)
        val ov = Engine.overview(clock, c.tracking.activeSeeds(), c.tracking.sessionsNow(range.first, range.last + 1), now)
        o = ov
        money = c.money.rangeNow(clock.dayStart(date), clock.dayEnd(date))
        val r = c.db.reviews().get(date.toString())
        review = r
        mood = r?.mood ?: 0; win = r?.win.orEmpty(); lesson = r?.lesson.orEmpty(); saved = r != null
    }
    val ov = o
    val scoreAnim = remember { Animatable(0f) }
    LaunchedEffect(ov?.score, date) { scoreAnim.snapTo(0f); scoreAnim.animateTo((ov?.score ?: 0) / 100f, tween(1200)) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, bottom = 60.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = SeedsTheme.colors.ink) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { date = date.minusDays(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous day", tint = SeedsTheme.colors.ink) }
                Text(Fmt.relativeDay(date, today), style = SeedsType.label, color = SeedsTheme.colors.ink)
                IconButton(onClick = { if (date < today) date = date.plusDays(1) }, enabled = date < today) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next day", tint = if (date < today) SeedsTheme.colors.ink else SeedsTheme.colors.inkFaint)
                }
            }
        }
        if (ov == null) return@LazyColumn

        item {
            val (head, sub) = verdict(ov.score)
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                    Ring(scoreAnim.value, SeedsTheme.colors.accent, Modifier.size(180.dp), stroke = 14.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(((scoreAnim.value * 100).toInt()).takeIf { ov.score != null }?.toString() ?: "–", style = SeedsType.display, color = SeedsTheme.colors.ink)
                        Text("DAY SCORE", style = SeedsType.overline, color = SeedsTheme.colors.inkMuted)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(head, style = SeedsType.headline, color = SeedsTheme.colors.ink, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(sub, style = SeedsType.body, color = SeedsTheme.colors.inkMuted, textAlign = TextAlign.Center)
            }
        }

        val due = ov.daily.filter { it.appliesToday }
        if (due.isNotEmpty()) {
            item { SectionHeader("Planned vs. actual · ${ov.grown}/${ov.dailyCount}") }
            item {
                SeedsCard {
                    due.forEachIndexed { i, st ->
                        if (i > 0) Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SeedGlyph(st.seed.icon, st.seed.color, st.progress, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(st.seed.name, style = SeedsType.bodyStrong, color = SeedsTheme.colors.ink)
                                Text("${Fmt.duration(st.doneMs)} of ${Fmt.duration(st.targetMs)}", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
                            }
                            val delta = st.doneMs - st.targetMs
                            Text(
                                (if (delta >= 0) "+" else "−") + Fmt.duration(kotlin.math.abs(delta)),
                                style = SeedsType.label, color = if (delta >= 0) seedTone(st.seed.color) else SeedsTheme.colors.drift,
                            )
                        }
                    }
                }
            }
        }
        val longer = ov.weekly + ov.monthly
        if (longer.isNotEmpty()) {
            item { SectionHeader("Bigger seeds") }
            item {
                SeedsCard {
                    longer.forEachIndexed { i, st ->
                        if (i > 0) Spacer(Modifier.height(14.dp))
                        Text(st.seed.name, style = SeedsType.bodyStrong, color = SeedsTheme.colors.ink)
                        Spacer(Modifier.height(6.dp))
                        Meter(st.progress, seedTone(st.seed.color), height = 6.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${Fmt.duration(st.doneMs)} of ${Fmt.duration(st.targetMs)} · ${Fmt.duration(st.todayMs)} today · ${st.daysLeft} days left",
                            style = SeedsType.caption, color = SeedsTheme.colors.inkMuted,
                        )
                    }
                }
            }
        }

        item { SectionHeader("Where the hours went") }
        item {
            val awake = (ov.sleepAt - ov.wakeAt).coerceAtLeast(1)
            val untracked = (minOf(ov.now, ov.sleepAt) - ov.wakeAt - ov.trackedMs).coerceAtLeast(0)
            SeedsCard {
                Row {
                    StatTile("Growing", Fmt.duration(ov.focusMs), Modifier.weight(1f))
                    StatTile("Drift", Fmt.duration(ov.driftMs), Modifier.weight(1f), valueColor = if (ov.driftMs > 0) SeedsTheme.colors.drift else SeedsTheme.colors.ink)
                }
                Spacer(Modifier.height(14.dp))
                Row {
                    StatTile("Breaks", Fmt.duration(ov.breakMs), Modifier.weight(1f))
                    StatTile("Untracked", Fmt.duration(untracked), Modifier.weight(1f), sub = "${untracked * 100 / awake}% of awake time")
                }
            }
        }

        if (money.isNotEmpty()) {
            item { SectionHeader("Money") }
            item {
                val out = money.filter { it.direction == Direction.OUT }.sumOf { it.amount + it.fee }
                val inn = money.filter { it.direction == Direction.IN }.sumOf { it.amount }
                SeedsCard {
                    Row {
                        StatTile("Spent", Fmt.money(out, settings.currency), Modifier.weight(1f))
                        StatTile("Received", Fmt.money(inn, settings.currency), Modifier.weight(1f), valueColor = SeedsTheme.colors.moneyIn)
                    }
                    val unl = money.count { it.categoryId == null }
                    if (unl > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text("$unl transaction${if (unl > 1) "s" else ""} still need a label.", style = SeedsType.caption, color = SeedsTheme.colors.warn)
                    }
                }
            }
        }

        item { SectionHeader("Reflect") }
        item {
            SeedsCard {
                Text("How did today feel?", style = SeedsType.subtitle, color = SeedsTheme.colors.ink)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    moods.forEachIndexed { i, label ->
                        val v = i + 1
                        val sel = mood == v
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(CircleShape).clickable { mood = v; saved = false }.padding(4.dp)) {
                            Box(
                                Modifier.size((28 + i * 4).dp).clip(CircleShape).background(if (sel) SeedsTheme.colors.accent else SeedsTheme.colors.surfaceHi),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(label, style = SeedsType.caption, color = if (sel) SeedsTheme.colors.ink else SeedsTheme.colors.inkMuted)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(win, { win = it; saved = false }, label = { Text("One win today") }, modifier = Modifier.fillMaxWidth(), colors = seedsFieldColors())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(lesson, { lesson = it; saved = false }, label = { Text("One thing to do differently") }, modifier = Modifier.fillMaxWidth(), colors = seedsFieldColors())
                Spacer(Modifier.height(16.dp))
                PillButton(if (saved) "Saved · sleep well" else "Save reflection", {
                    scope.launch {
                        c.db.reviews().upsert(DayReview(date.toString(), mood, win.trim(), lesson.trim(), ov.score))
                        saved = true
                    }
                }, Modifier.fillMaxWidth(), enabled = mood > 0 && !saved)
            }
        }

        if (date == today) {
            item {
                Spacer(Modifier.height(20.dp))
                val first = ov.daily.firstOrNull { it.seed.appliesOn(today.plusDays(1).dayOfWeek.value) }?.seed?.name
                Text(
                    "Tomorrow starts at ${Fmt.clockOfDay(settings.wakeMin)}." + (first?.let { " First seed: $it." } ?: ""),
                    style = SeedsType.body, color = SeedsTheme.colors.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
