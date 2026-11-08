package app.seeds.ui.you

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.seeds.data.db.Period
import app.seeds.data.db.Seed
import app.seeds.domain.Fmt
import app.seeds.seeds
import app.seeds.ui.components.ButtonStyle
import app.seeds.ui.components.ChoiceChip
import app.seeds.ui.components.PillButton
import app.seeds.ui.components.SeedGlyph
import app.seeds.ui.components.SeedsCard
import app.seeds.ui.components.SwitchRow
import app.seeds.ui.money.seedsFieldColors
import app.seeds.ui.theme.Dimens
import app.seeds.ui.theme.Ink
import app.seeds.ui.theme.SeedIcons
import app.seeds.ui.theme.SeedPalette
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeedEditorScreen(seedId: Long, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.seeds
    val scope = rememberCoroutineScope()
    var seed by remember { mutableStateOf(Seed(name = "", icon = "spa", color = 0, targetMinutes = 30, period = Period.DAILY)) }
    var loaded by remember { mutableStateOf(seedId == 0L) }
    LaunchedEffect(seedId) {
        if (seedId != 0L) c.tracking.seed(seedId)?.let { seed = it }
        loaded = true
    }
    if (!loaded) return

    val step = when (seed.period) { Period.DAILY -> 5; Period.WEEKLY -> 30; Period.MONTHLY -> 60 }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = SeedsTheme.colors.ink) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            SeedGlyph(seed.icon, seed.color, 0.7f, 64.dp)
            Spacer(Modifier.size(16.dp))
            Column {
                Text(if (seedId == 0L) "Plant a seed" else "Tend this seed", style = SeedsType.headline, color = SeedsTheme.colors.ink)
                Text("A non-negotiable you give time to.", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(seed.name, { seed = seed.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            colors = seedsFieldColors(), textStyle = SeedsType.subtitle, placeholder = { Text("e.g. Deep work, Gym, Reading") })

        Label("How often")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Period.entries.forEach { p ->
                ChoiceChip(p.name.lowercase().replaceFirstChar { it.uppercase() }, seed.period == p, {
                    val target = when (p) { Period.DAILY -> 30; Period.WEEKLY -> 180; Period.MONTHLY -> 300 }
                    seed = seed.copy(period = p, targetMinutes = if (seed.period == p) seed.targetMinutes else target)
                })
            }
        }

        Label("Time to give it " + when (seed.period) { Period.DAILY -> "each day"; Period.WEEKLY -> "each week"; Period.MONTHLY -> "each month" })
        SeedsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper(Icons.Rounded.Remove) { seed = seed.copy(targetMinutes = (seed.targetMinutes - step).coerceAtLeast(step)) }
                Text(Fmt.minutes(seed.targetMinutes), style = SeedsType.headline, color = SeedsTheme.colors.ink, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Stepper(Icons.Rounded.Add) { seed = seed.copy(targetMinutes = (seed.targetMinutes + step).coerceAtMost(24 * 60 * 31)) }
            }
            if (seed.period != Period.DAILY) {
                val days = if (seed.period == Period.WEEKLY) 7 else 30
                Spacer(Modifier.height(8.dp))
                Text("≈ ${Fmt.minutes(seed.targetMinutes / days)} a day", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted, modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }

        if (seed.period == Period.DAILY) {
            Label("On these days")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, l ->
                    ChoiceChip(l, seed.activeDays and (1 shl i) != 0, {
                        val next = seed.activeDays xor (1 shl i)
                        if (next != 0) seed = seed.copy(activeDays = next)
                    })
                }
            }
        }

        Label("Color")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(SeedPalette.size) { i ->
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(SeedPalette.fillColor(i))
                        .then(if (seed.color == i) Modifier.border(3.dp, SeedsTheme.colors.ink, CircleShape) else Modifier)
                        .clickable { seed = seed.copy(color = i) },
                    contentAlignment = Alignment.Center,
                ) { if (seed.color == i) Icon(Icons.Rounded.Check, null, tint = Ink, modifier = Modifier.size(16.dp)) }
            }
        }

        Label("Icon")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SeedIcons.seedKeys.forEach { k ->
                val sel = seed.icon == k
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(if (sel) SeedPalette.fillColor(seed.color) else SeedsTheme.colors.surface)
                        .clickable { seed = seed.copy(icon = k) },
                    contentAlignment = Alignment.Center,
                ) { Icon(SeedIcons.of(k), k, tint = if (sel) Ink else SeedsTheme.colors.inkMuted, modifier = Modifier.size(22.dp)) }
            }
        }

        Spacer(Modifier.height(12.dp))
        SwitchRow("Protect with drift guard", "While this runs, Seeds asks if you open a distracting app — even outside work hours.", seed.focus) {
            seed = seed.copy(focus = it)
        }

        Spacer(Modifier.height(24.dp))
        PillButton(if (seedId == 0L) "Plant it" else "Save", {
            scope.launch { c.tracking.saveSeed(seed.copy(name = seed.name.trim())); onDone() }
        }, Modifier.fillMaxWidth(), enabled = seed.name.isNotBlank())
        if (seedId != 0L) {
            Spacer(Modifier.height(10.dp))
            PillButton(if (seed.archived) "Bring it back" else "Archive", {
                scope.launch { c.tracking.archiveSeed(seed, !seed.archived); onDone() }
            }, Modifier.fillMaxWidth(), style = if (seed.archived) ButtonStyle.Secondary else ButtonStyle.Danger)
            Text("Archiving keeps its history in your garden.", style = SeedsType.caption, color = SeedsTheme.colors.inkFaint, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = SeedsType.label, color = SeedsTheme.colors.inkMuted, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
}

@Composable
private fun Stepper(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(SeedsTheme.colors.surfaceHi).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = SeedsTheme.colors.ink)
    }
}
