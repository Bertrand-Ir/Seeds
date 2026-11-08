package app.seeds.ui.money

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seeds.data.db.Category
import app.seeds.data.db.DebtDirection
import app.seeds.data.db.Direction
import app.seeds.data.db.Txn
import app.seeds.data.prefs.Settings
import app.seeds.domain.Fmt
import app.seeds.seeds
import app.seeds.ui.Perms
import app.seeds.ui.components.ButtonStyle
import app.seeds.ui.components.ChoiceChip
import app.seeds.ui.components.EmptyState
import app.seeds.ui.components.Meter
import app.seeds.ui.components.PillButton
import app.seeds.ui.components.ScreenTitle
import app.seeds.ui.components.SectionHeader
import app.seeds.ui.components.SeedsCard
import app.seeds.ui.components.StatTile
import app.seeds.ui.rememberResumeTick
import app.seeds.ui.theme.Dimens
import app.seeds.ui.theme.InnerShape
import app.seeds.ui.theme.SeedIcons
import app.seeds.ui.theme.SeedPalette
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType
import app.seeds.ui.theme.seedTone
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MoneyScreen(settings: Settings, onOpenTxn: (Long) -> Unit, onOpenDebts: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.seeds
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val month = remember { YearMonth.now() }
    val from = remember(month) { month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val to = remember(month) { month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val monthTxnsFlow = remember(from) { c.money.range(from, to) }
    val monthTxns by monthTxnsFlow.collectAsStateWithLifecycle(emptyList())
    val recentFlow = remember { c.money.recent(80) }
    val recent by recentFlow.collectAsStateWithLifecycle(emptyList())
    val unlabeled by c.money.unlabeled.collectAsStateWithLifecycle(emptyList())
    val categories by c.money.categories.collectAsStateWithLifecycle(emptyList())
    val balance by c.money.latestBalance.collectAsStateWithLifecycle(null)
    val debts by c.money.allDebts.collectAsStateWithLifecycle(emptyList())
    val catById = remember(categories) { categories.associateBy { it.id } }
    val cur = settings.currency

    val resume = rememberResumeTick()
    val smsOk = remember(resume) { Perms.sms(ctx) }
    var importing by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<String?>(null) }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) scope.launch {
            importing = true
            val n = c.money.importInbox(ctx, settings.momoSenders)
            importResult = "Found $n transactions from the last 4 months."
            importing = false
        }
    }
    var showAdd by remember { mutableStateOf(false) }

    val spentOut = monthTxns.filter { it.direction == Direction.OUT }
    val totalOut = spentOut.sumOf { it.amount + it.fee }
    val totalIn = monthTxns.filter { it.direction == Direction.IN }.sumOf { it.amount }
    val fees = monthTxns.sumOf { it.fee }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, top = 8.dp, bottom = 140.dp),
    ) {
        item {
            ScreenTitle(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), "Money") {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(SeedsTheme.colors.accent).clickable { showAdd = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, "Add", tint = SeedsTheme.colors.onAccent) }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            SeedsCard(color = SeedsTheme.colors.sprout, padding = PaddingValues(22.dp)) {
                val ink = SeedsTheme.colors.onSprout
                Text("MOMO BALANCE", style = SeedsType.overline, color = ink.copy(alpha = 0.65f))
                Spacer(Modifier.height(6.dp))
                Text(balance?.balanceAfter?.let { Fmt.money(it, cur) } ?: "—", style = SeedsType.headline.copy(fontSize = SeedsType.display.fontSize), color = ink, maxLines = 1)
                Text(
                    balance?.let { "as of ${Fmt.relativeDay(Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate(), LocalDate.now())} ${Fmt.clock(it.timestamp)}" }
                        ?: "Appears after your next MoMo message",
                    style = SeedsType.caption, color = ink.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(18.dp))
                Row {
                    StatTileOn("In", Fmt.compactMoney(totalIn), ink, Modifier.weight(1f))
                    StatTileOn("Out", Fmt.compactMoney(totalOut), ink, Modifier.weight(1f))
                    StatTileOn("Net", (if (totalIn - totalOut >= 0) "+" else "−") + Fmt.compactMoney(kotlin.math.abs(totalIn - totalOut)), ink, Modifier.weight(1f))
                }
                if (fees > 0) {
                    Spacer(Modifier.height(10.dp))
                    Text("Fees this month: ${Fmt.money(fees, cur)}", style = SeedsType.caption, color = ink.copy(alpha = 0.7f))
                }
            }
        }

        if (!smsOk || importResult != null) {
            item {
                Spacer(Modifier.height(Dimens.gap))
                SeedsCard(color = SeedsTheme.colors.surfaceHi) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Sms, null, tint = SeedsTheme.colors.accent)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (smsOk) "MoMo is connected" else "Track MoMo automatically", style = SeedsType.bodyStrong, color = SeedsTheme.colors.ink)
                            Text(
                                importResult ?: "Seeds reads only Mobile Money messages, on your phone, and asks what each one was for.",
                                style = SeedsType.caption, color = SeedsTheme.colors.inkMuted,
                            )
                        }
                    }
                    if (!smsOk) {
                        Spacer(Modifier.height(12.dp))
                        PillButton(if (importing) "Importing…" else "Allow SMS access", {
                            smsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
                        }, Modifier.fillMaxWidth(), enabled = !importing)
                    }
                }
            }
        }

        if (unlabeled.isNotEmpty()) {
            item { SectionHeader("Needs a label · ${unlabeled.size}") }
            items(unlabeled.take(5), key = { "u${it.id}" }) { t ->
                UnlabeledCard(t, cur, categories, onLabel = { cat -> scope.launch { c.money.label(t.id, cat.id) } }, onMore = { onOpenTxn(t.id) })
            }
        }

        val byCat = spentOut.filter { it.categoryId != null }.groupBy { it.categoryId!! }
            .mapValues { (_, v) -> v.sumOf { it.amount + it.fee } }.entries.sortedByDescending { it.value }
        if (byCat.isNotEmpty()) {
            item { SectionHeader("Where it went") }
            item {
                SeedsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Donut(byCat.map { (catById[it.key]?.color ?: 7) to it.value }, Modifier.size(120.dp))
                        Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1f)) {
                            byCat.take(4).forEach { (id, amt) ->
                                val cat = catById[id]
                                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(seedTone(cat?.color ?: 7)))
                                    Spacer(Modifier.width(8.dp))
                                    Text(cat?.name ?: "Other", style = SeedsType.caption, color = SeedsTheme.colors.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${(amt * 100 / totalOut.coerceAtLeast(1))}%", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    byCat.forEach { (id, amt) ->
                        val cat = catById[id] ?: return@forEach
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(SeedIcons.of(cat.icon), null, tint = seedTone(cat.color), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(cat.name, style = SeedsType.label, color = SeedsTheme.colors.ink, modifier = Modifier.weight(1f))
                                Text(Fmt.money(amt, cur) + (cat.monthlyBudget?.let { " / ${Fmt.compactMoney(it)}" } ?: ""), style = SeedsType.label, color = SeedsTheme.colors.inkMuted)
                            }
                            cat.monthlyBudget?.let { b ->
                                Spacer(Modifier.height(6.dp))
                                Meter(amt.toFloat() / b, if (amt > b) SeedsTheme.colors.drift else seedTone(cat.color), height = 6.dp)
                            }
                        }
                    }
                }
            }
        }

        item { SectionHeader("Debts & loans", action = "Open", onAction = onOpenDebts) }
        item {
            val owedToMe = debts.filter { it.settledAt == null && it.direction == DebtDirection.OWED_TO_ME }.sumOf { it.remaining }
            val iOwe = debts.filter { it.settledAt == null && it.direction == DebtDirection.I_OWE }.sumOf { it.remaining }
            SeedsCard(onClick = onOpenDebts) {
                Row {
                    StatTile("Owed to you", Fmt.money(owedToMe, cur), Modifier.weight(1f), valueColor = SeedsTheme.colors.moneyIn)
                    StatTile("You owe", Fmt.money(iOwe, cur), Modifier.weight(1f), valueColor = if (iOwe > 0) SeedsTheme.colors.moneyOut else SeedsTheme.colors.ink)
                }
            }
        }

        item { SectionHeader("Recent") }
        if (recent.isEmpty()) {
            item { EmptyState("No transactions yet", "MoMo payments appear here automatically. Add cash spending with +.") }
        } else {
            val grouped = recent.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            grouped.forEach { (day, list) ->
                item(key = "h$day") {
                    Text(Fmt.relativeDay(day, LocalDate.now()), style = SeedsType.caption, color = SeedsTheme.colors.inkFaint, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                items(list, key = { "r${it.id}" }) { t -> TxnRow(t, catById[t.categoryId], cur) { onOpenTxn(t.id) } }
            }
        }
    }

    if (showAdd) AddTxnSheet(settings, categories) { showAdd = false }
}

@Composable
private fun StatTileOn(label: String, value: String, ink: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = SeedsType.overline, color = ink.copy(alpha = 0.6f))
        Text(value, style = SeedsType.number, color = ink, maxLines = 1)
    }
}

@Composable
private fun UnlabeledCard(t: Txn, cur: String, categories: List<Category>, onLabel: (Category) -> Unit, onMore: () -> Unit) {
    val ctx = LocalContext.current
    val suggestions = remember { mutableStateMapOf<Long, List<Category>>() }
    LaunchedEffect(t.id) { suggestions[t.id] = ctx.seeds.money.suggestions(t, 4) }
    val inbound = t.direction == Direction.IN
    SeedsCard(Modifier.padding(vertical = 4.dp), onClick = onMore, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Fmt.signedMoney(t.amount, inbound, cur), style = SeedsType.subtitle, color = if (inbound) SeedsTheme.colors.moneyIn else SeedsTheme.colors.ink)
                Text(listOfNotNull(t.counterparty, Fmt.clock(t.timestamp)).joinToString(" · "), style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
            }
            Text(if (inbound) "From what?" else "For what?", style = SeedsType.caption, color = SeedsTheme.colors.inkFaint)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (suggestions[t.id] ?: categories.filter { it.direction == t.direction }.take(4)).forEach { cat ->
                ChoiceChip(cat.name, false, { onLabel(cat) }, icon = SeedIcons.of(cat.icon))
            }
            ChoiceChip("More…", false, onMore)
        }
    }
}

@Composable
fun TxnRow(t: Txn, cat: Category?, cur: String, onClick: () -> Unit) {
    val c = SeedsTheme.colors
    val inbound = t.direction == Direction.IN
    Row(
        Modifier.fillMaxWidth().clip(InnerShape).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (cat != null) SeedPalette.fillColor(cat.color).copy(alpha = if (c.isDark) 0.18f else 0.35f) else c.surfaceHi),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SeedIcons.of(cat?.icon ?: "wallet"), null, tint = if (cat != null) seedTone(cat.color) else c.inkMuted, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(cat?.name ?: "Unlabeled", style = SeedsType.bodyStrong, color = if (cat == null) c.warn else c.ink, maxLines = 1)
            Text(listOfNotNull(t.counterparty, t.note?.takeIf { it.isNotBlank() }, t.provider).joinToString(" · "), style = SeedsType.caption, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(Fmt.signedMoney(t.amount, inbound, cur), style = SeedsType.label, color = if (inbound) c.moneyIn else c.ink)
    }
}

@Composable
private fun Donut(parts: List<Pair<Int, Long>>, modifier: Modifier) {
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    val dark = SeedsTheme.colors.isDark
    Canvas(modifier) {
        val stroke = size.minDimension * 0.16f
        val d = size.minDimension - stroke
        val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
        var start = -90f
        parts.forEach { (color, v) ->
            val sweep = 360f * v / total
            drawArc(
                androidx.compose.ui.graphics.Color(if (dark) SeedPalette.fill(color) else SeedPalette.deep(color)),
                start + 1.5f, (sweep - 3f).coerceAtLeast(0.5f), false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Butt),
            )
            start += sweep
        }
    }
}
