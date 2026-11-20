package app.seeds.ui.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seeds.data.db.PlanItem
import app.seeds.data.db.Seed
import app.seeds.data.prefs.Settings
import app.seeds.domain.DayClock
import app.seeds.domain.DaySlot
import app.seeds.domain.PlanFocus
import app.seeds.seeds
import app.seeds.ui.components.ButtonStyle
import app.seeds.ui.components.PillButton
import app.seeds.ui.components.SeedsCard
import app.seeds.ui.components.rememberNow
import app.seeds.ui.money.seedsFieldColors
import app.seeds.ui.theme.CardShape
import app.seeds.ui.theme.Ink
import app.seeds.ui.theme.Pill
import app.seeds.ui.theme.SeedIcons
import app.seeds.ui.theme.SeedPalette
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType
import app.seeds.ui.theme.seedTone
import kotlinx.coroutines.launch

/**
 * The heart of Today: what you planned for *this* part of the day, big and touchable,
 * with the rest of the day as a quiet timeline underneath and tomorrow one tap away.
 */
@Composable
fun PlanHero(settings: Settings, activeSeedId: Long?, onOpenPlan: (String) -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.seeds
    val col = SeedsTheme.colors
    val scope = rememberCoroutineScope()
    val minute by rememberNow(60_000)
    val clock = remember(settings) { DayClock(settings) }
    val today = remember(minute, clock) { clock.today(minute) }
    val items by remember(today) { c.plan.observe(today.toString()) }.collectAsStateWithLifecycle(emptyList())
    val tomorrowItems by remember(today) { c.plan.observe(today.plusDays(1).toString()) }.collectAsStateWithLifecycle(emptyList())
    val seeds by c.tracking.seeds.collectAsStateWithLifecycle(emptyList())
    val slot = remember(minute) { DaySlot.now() }
    val focus = PlanFocus.of(items, slot)
    val evening = slot == DaySlot.EVENING || slot == DaySlot.NIGHT
    val done = items.count { it.done }
    var adding by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val add = {
        val t = text
        text = ""
        scope.launch { c.plan.add(today.toString(), slot, t, null) }
        Unit
    }

    SeedsCard(
        Modifier.animateContentSize().border(1.5.dp, col.accent.copy(alpha = 0.55f), CardShape),
        padding = PaddingValues(20.dp),
    ) {
        // Header: the window you're in, and how the plan is going
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(col.accent), contentAlignment = Alignment.Center) {
                Icon((focus?.slot ?: slot).icon(), null, tint = col.onAccent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (items.isEmpty()) "YOUR PLAN" else "YOUR PLAN · $done OF ${items.size} DONE",
                    style = SeedsType.overline, color = col.inkMuted,
                )
                Text(
                    when {
                        items.isEmpty() -> "What's the plan today?"
                        focus == null -> "All done for today"
                        else -> focus.label
                    },
                    style = SeedsType.title, color = col.ink,
                )
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable { onOpenPlan("today") },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.EditCalendar, "Full plan", tint = col.accent, modifier = Modifier.size(22.dp)) }
        }

        when {
            items.isEmpty() -> {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Put a few things into morning, noon, afternoon, evening and night. " +
                        "Seeds shows you the right one at the right time — here and on your home screen.",
                    style = SeedsType.body, color = col.inkMuted,
                )
                Spacer(Modifier.height(16.dp))
                PillButton("Plan today", { onOpenPlan("today") }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
            }
            focus == null -> {
                Spacer(Modifier.height(8.dp))
                Text("Everything you planned is done. Well grown. 🌱", style = SeedsType.body, color = col.inkMuted)
            }
            else -> {
                Spacer(Modifier.height(10.dp))
                focus.items.forEach { item ->
                    HeroRow(
                        item, seeds.firstOrNull { it.id == item.seedId }, growing = item.seedId != null && item.seedId == activeSeedId,
                        onToggle = { scope.launch { c.plan.toggle(item) } },
                        onStart = { id -> scope.launch { c.tracking.start(id) } },
                    )
                }
            }
        }

        // Quick add, straight into the window you're in
        if (items.isNotEmpty()) {
            AnimatedVisibility(adding) {
                OutlinedTextField(
                    text, { text = it }, placeholder = { Text("Add to ${slot.label.lowercase()}…") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = seedsFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    trailingIcon = { IconButton(onClick = { add() }) { Icon(Icons.Rounded.Check, "Add", tint = col.accent) } },
                )
            }
            if (!adding) {
                Text(
                    "+ Add to ${slot.label.lowercase()}", style = SeedsType.label, color = col.accent,
                    modifier = Modifier.padding(top = 6.dp).clip(Pill).clickable { adding = true }.padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }
        }

        // The rest of the day, as a quiet timeline
        val others = DaySlot.entries.filter { s -> s != focus?.slot && items.any { it.slot == s.ordinal } }
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(col.line))
            Spacer(Modifier.height(6.dp))
            others.forEach { s ->
                val inSlot = items.filter { it.slot == s.ordinal }
                val past = s.ordinal < slot.ordinal
                Row(
                    Modifier.fillMaxWidth().clip(Pill).clickable { onOpenPlan("today") }.padding(vertical = 7.dp)
                        .alpha(if (past) 0.55f else 1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(s.icon(), null, tint = col.inkMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(s.label, style = SeedsType.label, color = col.ink, modifier = Modifier.width(78.dp))
                    Text(
                        inSlot.joinToString(" · ") { it.title }, style = SeedsType.caption, color = col.inkMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${inSlot.count { it.done }}/${inSlot.size}", style = SeedsType.caption, color = col.inkFaint)
                }
            }
        }

        // Tomorrow — loud in the evening when it's still empty, quiet otherwise
        Spacer(Modifier.height(12.dp))
        if (evening && tomorrowItems.isEmpty()) {
            PillButton("Plan tomorrow", { onOpenPlan("tomorrow") }, Modifier.fillMaxWidth(), style = ButtonStyle.Sprout, icon = Icons.Rounded.Bedtime)
        } else {
            Row(
                Modifier.fillMaxWidth().clip(Pill).background(col.surfaceHi).clickable { onOpenPlan("tomorrow") }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Bedtime, null, tint = col.inkMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (tomorrowItems.isEmpty()) "Plan tomorrow" else "Tomorrow · ${tomorrowItems.size} planned",
                    style = SeedsType.label, color = col.ink, modifier = Modifier.weight(1f),
                )
                Text(if (tomorrowItems.isEmpty()) "Start" else "Edit", style = SeedsType.label, color = col.accent)
            }
        }
    }
}

@Composable
private fun HeroRow(item: PlanItem, seed: Seed?, growing: Boolean, onToggle: () -> Unit, onStart: (Long) -> Unit) {
    val col = SeedsTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(CircleShape)
                .background(if (item.done) col.accent else Color.Transparent)
                .border(2.dp, if (item.done) col.accent else col.inkFaint, CircleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) { if (item.done) Icon(Icons.Rounded.Check, "Done", tint = col.onAccent, modifier = Modifier.size(17.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = SeedsType.subtitle.copy(textDecoration = if (item.done) TextDecoration.LineThrough else null),
                color = if (item.done) col.inkFaint else col.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (seed != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(SeedIcons.of(seed.icon), null, tint = seedTone(seed.color), modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(seed.name, style = SeedsType.caption, color = col.inkMuted, maxLines = 1)
                }
            }
        }
        if (seed != null && !item.done) {
            Spacer(Modifier.width(8.dp))
            if (growing) {
                Text(
                    "Growing", style = SeedsType.caption, color = col.onAccent,
                    modifier = Modifier.clip(Pill).background(col.accent).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            } else {
                Row(
                    Modifier.clip(Pill).background(SeedPalette.fillColor(seed.color)).clickable { onStart(seed.id) }
                        .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Ink, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Start", style = SeedsType.label, color = Ink)
                }
            }
        }
    }
}
