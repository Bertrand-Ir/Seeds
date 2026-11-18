package app.seeds.ui.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seeds.data.db.Period
import app.seeds.data.db.PlanItem
import app.seeds.data.db.Seed
import app.seeds.data.prefs.Settings
import app.seeds.domain.DayClock
import app.seeds.domain.DaySlot
import app.seeds.domain.Fmt
import app.seeds.seeds
import app.seeds.ui.components.ButtonStyle
import app.seeds.ui.components.ChoiceChip
import app.seeds.ui.components.PillButton
import app.seeds.ui.components.SeedsCard
import app.seeds.ui.money.seedsFieldColors
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
import java.time.LocalDateTime

fun DaySlot.icon(): ImageVector = when (this) {
    DaySlot.MORNING -> Icons.Rounded.Coffee
    DaySlot.NOON -> Icons.Rounded.LightMode
    DaySlot.AFTERNOON -> Icons.Rounded.WbSunny
    DaySlot.EVENING -> Icons.Rounded.WbTwilight
    DaySlot.NIGHT -> Icons.Rounded.Bedtime
}

fun currentSlot(): DaySlot = LocalDateTime.now().let { DaySlot.at(it.hour * 60 + it.minute) }

/** Plan a day in windows, not minutes. Morning · Noon · Afternoon · Evening · Night. */
@Composable
fun PlanScreen(settings: Settings, which: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.seeds
    val scope = rememberCoroutineScope()
    val clock = remember(settings) { DayClock(settings) }
    val today = clock.today()
    var tomorrow by remember { mutableStateOf(which != "today") }
    val date = if (tomorrow) today.plusDays(1) else today
    val iso = date.toString()
    val itemsFlow = remember(iso) { c.db.plan().observe(iso) }
    val items by itemsFlow.collectAsStateWithLifecycle(emptyList())
    val todayFlow = remember(today) { c.db.plan().observe(today.toString()) }
    val todayItems by todayFlow.collectAsStateWithLifecycle(emptyList())
    val seeds by c.tracking.seeds.collectAsStateWithLifecycle(emptyList())
    var adding by remember { mutableStateOf<DaySlot?>(null) }
    val now = currentSlot()

    // Seeds that belong on this day but aren't placed yet.
    val unplaced = seeds.filter { s ->
        (s.period != Period.DAILY || s.appliesOn(date.dayOfWeek.value)) && items.none { it.seedId == s.id }
    }

    fun add(slot: DaySlot, title: String, seed: Seed?) = scope.launch {
        if (title.isBlank()) return@launch
        c.db.plan().insert(PlanItem(date = iso, slot = slot.ordinal, title = title.trim(), seedId = seed?.id, sortOrder = items.count { it.slot == slot.ordinal }))
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().imePadding(),
        contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, bottom = 80.dp),
    ) {
        item {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = SeedsTheme.colors.ink) }
            Text(Fmt.dayLong(date), style = SeedsType.label, color = SeedsTheme.colors.inkMuted)
            Text(if (tomorrow) "Plan tomorrow" else "Today's plan", style = SeedsType.headline, color = SeedsTheme.colors.ink)
            Spacer(Modifier.height(6.dp))
            Text("No exact times — just what goes where. Life moves, and your plan can too.", style = SeedsType.body, color = SeedsTheme.colors.inkMuted)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Today", !tomorrow, { tomorrow = false; adding = null })
                ChoiceChip("Tomorrow", tomorrow, { tomorrow = true; adding = null })
            }
        }

        if (unplaced.isNotEmpty()) item {
            Spacer(Modifier.height(16.dp))
            Text("SEEDS NOT PLACED YET · TAP A WINDOW BELOW, THEN A SEED", style = SeedsType.overline, color = SeedsTheme.colors.inkMuted)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                unplaced.forEach { s ->
                    ChoiceChip(s.name, false, { add(adding ?: defaultSlot(s), s.name, s) }, icon = SeedIcons.of(s.icon), tint = SeedPalette.fillColor(s.color))
                }
            }
        }

        DaySlot.entries.forEach { slot ->
            item(key = slot.name) {
                Spacer(Modifier.height(12.dp))
                SlotCard(
                    slot = slot,
                    items = items.filter { it.slot == slot.ordinal },
                    seeds = seeds,
                    isNow = !tomorrow && slot == now,
                    open = adding == slot,
                    onOpen = { adding = if (adding == slot) null else slot },
                    onAdd = { title, seed -> add(slot, title, seed) },
                    unplaced = unplaced,
                    onToggle = { i -> scope.launch { c.db.plan().update(i.copy(done = !i.done)) } },
                    onDelete = { i -> scope.launch { c.db.plan().delete(i) } },
                    onStart = if (!tomorrow) { s -> scope.launch { c.tracking.start(s) } } else null,
                )
            }
        }

        if (tomorrow) {
            val leftover = todayItems.filter { !it.done }
            if (leftover.isNotEmpty()) item {
                Spacer(Modifier.height(16.dp))
                PillButton("Bring ${leftover.size} unfinished from today", {
                    scope.launch {
                        leftover.forEach { c.db.plan().insert(it.copy(id = 0, date = iso, done = false)) }
                        leftover.forEach { c.db.plan().delete(it) }
                    }
                }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
            }
        }
        item {
            Spacer(Modifier.height(20.dp))
            PillButton(if (tomorrow) "Done — sleep well" else "Done", onBack, Modifier.fillMaxWidth())
        }
    }
}

/** Where a seed most naturally goes if no window is chosen. */
private fun defaultSlot(s: Seed): DaySlot = when (s.icon) {
    "still", "spa", "book" -> DaySlot.MORNING
    "fitness", "run", "walk", "sport" -> DaySlot.EVENING
    "family", "groups", "call" -> DaySlot.NIGHT
    else -> if (s.focus) DaySlot.MORNING else DaySlot.AFTERNOON
}

@Composable
private fun SlotCard(
    slot: DaySlot,
    items: List<PlanItem>,
    seeds: List<Seed>,
    isNow: Boolean,
    open: Boolean,
    onOpen: () -> Unit,
    onAdd: (String, Seed?) -> Unit,
    unplaced: List<Seed>,
    onToggle: (PlanItem) -> Unit,
    onDelete: (PlanItem) -> Unit,
    onStart: ((Long) -> Unit)?,
) {
    val c = SeedsTheme.colors
    var text by remember { mutableStateOf("") }
    SeedsCard(
        Modifier.animateContentSize().then(if (isNow || open) Modifier.border(2.dp, c.accent, app.seeds.ui.theme.CardShape) else Modifier),
        padding = PaddingValues(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(if (isNow) c.accent else c.surfaceHi), contentAlignment = Alignment.Center) {
                Icon(slot.icon(), null, tint = if (isNow) c.onAccent else c.ink, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(slot.label, style = SeedsType.subtitle, color = c.ink)
                    if (isNow) {
                        Spacer(Modifier.width(8.dp))
                        Text("NOW", style = SeedsType.overline, color = c.accent)
                    }
                }
                Text(slot.hint + if (items.isNotEmpty()) " · ${items.count { it.done }}/${items.size} done" else "", style = SeedsType.caption, color = c.inkMuted)
            }
            Icon(if (open) Icons.Rounded.Close else Icons.Rounded.Add, if (open) "Close" else "Add", tint = c.accent)
        }
        items.forEach { item ->
            PlanRow(item, seeds.firstOrNull { it.id == item.seedId }, onToggle, onDelete, onStart)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 10.dp)) {
                OutlinedTextField(
                    text, { text = it }, placeholder = { Text("What will you do?") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = seedsFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAdd(text, null); text = "" }),
                    trailingIcon = { IconButton(onClick = { onAdd(text, null); text = "" }) { Icon(Icons.Rounded.Check, "Add", tint = c.accent) } },
                )
                if (unplaced.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        unplaced.forEach { s -> ChoiceChip("+ ${s.name}", false, { onAdd(s.name, s) }, icon = SeedIcons.of(s.icon)) }
                    }
                }
            }
        }
    }
}

@Composable
fun PlanRow(item: PlanItem, seed: Seed?, onToggle: (PlanItem) -> Unit, onDelete: ((PlanItem) -> Unit)?, onStart: ((Long) -> Unit)?) {
    val c = SeedsTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).clip(CircleShape)
                .background(if (item.done) c.accent else androidx.compose.ui.graphics.Color.Transparent)
                .border(2.dp, if (item.done) c.accent else c.line, CircleShape)
                .clickable { onToggle(item) },
            contentAlignment = Alignment.Center,
        ) { if (item.done) Icon(Icons.Rounded.Check, "Done", tint = c.onAccent, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(12.dp))
        if (seed != null) {
            Icon(SeedIcons.of(seed.icon), null, tint = seedTone(seed.color), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            item.title, style = SeedsType.body.copy(textDecoration = if (item.done) TextDecoration.LineThrough else null),
            color = if (item.done) c.inkFaint else c.ink, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (onStart != null && seed != null && !item.done) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(SeedPalette.fillColor(seed.color)).clickable { onStart(seed.id) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, "Start ${seed.name}", tint = Ink, modifier = Modifier.size(18.dp))
            }
        }
        if (onDelete != null) {
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(32.dp).clip(CircleShape).clickable { onDelete(item) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "Remove", tint = c.inkFaint, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** On Today: what's planned for the window you're in now, and a peek at the rest. */
@Composable
fun TodayPlanCard(settings: Settings, onOpen: () -> Unit, onPlanTomorrow: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.seeds
    val scope = rememberCoroutineScope()
    val clock = remember(settings) { DayClock(settings) }
    val today = clock.today()
    val flow = remember(today) { c.db.plan().observe(today.toString()) }
    val items by flow.collectAsStateWithLifecycle(emptyList())
    val tFlow = remember(today) { c.db.plan().observe(today.plusDays(1).toString()) }
    val tomorrowItems by tFlow.collectAsStateWithLifecycle(emptyList())
    val seeds by c.tracking.seeds.collectAsStateWithLifecycle(emptyList())
    val now = currentSlot()
    val evening = now == DaySlot.EVENING || now == DaySlot.NIGHT

    SeedsCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(now.icon(), null, tint = SeedsTheme.colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("${now.label.uppercase()} PLAN", style = SeedsType.overline, color = SeedsTheme.colors.inkMuted, modifier = Modifier.weight(1f))
            if (items.isNotEmpty()) Text("${items.count { it.done }}/${items.size} today", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
        }
        val current = items.filter { it.slot == now.ordinal }
        if (items.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Nothing planned for today.", style = SeedsType.bodyStrong, color = SeedsTheme.colors.ink)
            Text("Put a few things into windows — morning, noon, afternoon…", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
        } else if (current.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Nothing set for the ${now.label.lowercase()}. Free to choose.", style = SeedsType.body, color = SeedsTheme.colors.inkMuted)
        } else {
            current.forEach { item ->
                PlanRow(item, seeds.firstOrNull { it.id == item.seedId },
                    onToggle = { i -> scope.launch { c.db.plan().update(i.copy(done = !i.done)) } },
                    onDelete = null,
                    onStart = { s -> scope.launch { c.tracking.start(s) } })
            }
        }
        val later = DaySlot.entries.filter { it.ordinal > now.ordinal }.map { s -> s to items.filter { it.slot == s.ordinal && !it.done } }.filter { it.second.isNotEmpty() }
        if (later.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Later: " + later.joinToString(" · ") { (s, l) -> "${s.label} ${l.size}" }, style = SeedsType.caption, color = SeedsTheme.colors.inkFaint)
        }
        if (evening) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(Pill).background(SeedsTheme.colors.accentSoft).clickable(onClick = onPlanTomorrow).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Bedtime, null, tint = SeedsTheme.colors.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (tomorrowItems.isEmpty()) "Plan tomorrow before bed" else "Tomorrow: ${tomorrowItems.size} planned · edit",
                    style = SeedsType.label, color = SeedsTheme.colors.ink, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Suppress("unused")
private val unusedShape = InnerShape
