package app.seeds.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seeds.data.db.Debt
import app.seeds.data.db.DebtDirection
import app.seeds.data.prefs.Settings
import app.seeds.domain.Fmt
import app.seeds.seeds
import app.seeds.ui.components.ButtonStyle
import app.seeds.ui.components.ChoiceChip
import app.seeds.ui.components.EmptyState
import app.seeds.ui.components.Meter
import app.seeds.ui.components.PillButton
import app.seeds.ui.components.ScreenTitle
import app.seeds.ui.components.SectionHeader
import app.seeds.ui.components.SeedsCard
import app.seeds.ui.components.SeedsSheet
import app.seeds.ui.theme.Dimens
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType
import kotlinx.coroutines.launch

@Composable
fun DebtsScreen(settings: Settings, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.seeds
    val scope = rememberCoroutineScope()
    val debts by c.money.allDebts.collectAsStateWithLifecycle(emptyList())
    var adding by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf<Debt?>(null) }
    val cur = settings.currency

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, bottom = 60.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = SeedsTheme.colors.ink) }
            }
            ScreenTitle("Credit & debit", "Debts & loans") {
                PillButton("Add", { adding = true }, icon = Icons.Rounded.Add, height = 44.dp)
            }
            Text(
                "Label a MoMo payment as “Lent to someone” or “Borrowed” and it shows up here. Repayments reduce it automatically.",
                style = SeedsType.caption, color = SeedsTheme.colors.inkMuted, modifier = Modifier.padding(top = 6.dp),
            )
        }
        val open = debts.filter { it.settledAt == null }
        val owed = open.filter { it.direction == DebtDirection.OWED_TO_ME }
        val owe = open.filter { it.direction == DebtDirection.I_OWE }
        item { SectionHeader("Owed to you · ${Fmt.money(owed.sumOf { it.remaining }, cur)}") }
        if (owed.isEmpty()) item { Text("Nobody owes you right now.", style = SeedsType.body, color = SeedsTheme.colors.inkFaint) }
        items(owed, key = { "o${it.id}" }) { d -> DebtCard(d, cur, onPay = { paying = d }) }
        item { SectionHeader("You owe · ${Fmt.money(owe.sumOf { it.remaining }, cur)}") }
        if (owe.isEmpty()) item { Text("You don't owe anyone. Freedom.", style = SeedsType.body, color = SeedsTheme.colors.inkFaint) }
        items(owe, key = { "i${it.id}" }) { d -> DebtCard(d, cur, onPay = { paying = d }) }
        val settled = debts.filter { it.settledAt != null }
        if (settled.isNotEmpty()) {
            item { SectionHeader("Settled") }
            items(settled, key = { "s${it.id}" }) { d -> DebtCard(d, cur, onPay = null, onDelete = { scope.launch { c.money.deleteDebt(d) } }) }
        }
        if (debts.isEmpty()) item { EmptyState("Clean slate", "Track money you lent or borrowed so nothing is forgotten.") }
    }

    if (adding) AddDebtSheet(cur) { adding = false }
    paying?.let { d ->
        var amount by remember(d.id) { mutableStateOf(d.remaining.toString()) }
        AlertDialog(
            onDismissRequest = { paying = null },
            containerColor = SeedsTheme.colors.bg,
            title = { Text(if (d.direction == DebtDirection.OWED_TO_ME) "${d.person} paid you" else "You paid ${d.person}", style = SeedsType.title) },
            text = {
                OutlinedTextField(amount, { v -> amount = v.filter { it.isDigit() } }, label = { Text("Amount ($cur)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = seedsFieldColors())
            },
            confirmButton = {
                TextButton(onClick = { amount.toLongOrNull()?.let { a -> scope.launch { c.money.settle(d, a) } }; paying = null }) {
                    Text("Record", color = SeedsTheme.colors.accent, style = SeedsType.bodyStrong)
                }
            },
            dismissButton = {
                TextButton(onClick = { scope.launch { c.money.deleteDebt(d) }; paying = null }) { Text("Delete", color = SeedsTheme.colors.drift, style = SeedsType.bodyStrong) }
            },
        )
    }
}

@Composable
private fun DebtCard(d: Debt, cur: String, onPay: (() -> Unit)?, onDelete: (() -> Unit)? = null) {
    val tone = if (d.direction == DebtDirection.OWED_TO_ME) SeedsTheme.colors.moneyIn else SeedsTheme.colors.moneyOut
    SeedsCard(Modifier.padding(vertical = 4.dp), onClick = onPay ?: onDelete, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.person, style = SeedsType.bodyStrong, color = SeedsTheme.colors.ink)
                Text(
                    "since ${Fmt.dayShort(java.time.Instant.ofEpochMilli(d.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate())}" + (d.note?.let { " · $it" } ?: ""),
                    style = SeedsType.caption, color = SeedsTheme.colors.inkMuted,
                )
            }
            Text(Fmt.money(if (d.settledAt == null) d.remaining else d.amount, cur), style = SeedsType.subtitle, color = if (d.settledAt == null) tone else SeedsTheme.colors.inkFaint)
        }
        if (d.paid > 0 && d.settledAt == null) {
            Spacer(Modifier.height(10.dp))
            Meter(d.paid.toFloat() / d.amount, tone, height = 6.dp)
            Spacer(Modifier.height(4.dp))
            Text("${Fmt.money(d.paid, cur)} of ${Fmt.money(d.amount, cur)} paid back", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
        }
    }
}

@Composable
private fun AddDebtSheet(cur: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dir by remember { mutableStateOf(DebtDirection.OWED_TO_ME) }
    var person by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    SeedsSheet(onDismiss) {
        Text("Add a debt", style = SeedsType.title, color = SeedsTheme.colors.ink)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("They owe me", dir == DebtDirection.OWED_TO_ME, { dir = DebtDirection.OWED_TO_ME })
            ChoiceChip("I owe them", dir == DebtDirection.I_OWE, { dir = DebtDirection.I_OWE })
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(person, { person = it }, label = { Text("Who") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = seedsFieldColors())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(amount, { v -> amount = v.filter { it.isDigit() } }, label = { Text("Amount ($cur)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), colors = seedsFieldColors())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = seedsFieldColors())
        Spacer(Modifier.height(20.dp))
        PillButton("Save", {
            val a = amount.toLongOrNull() ?: return@PillButton
            scope.launch { ctx.seeds.money.saveDebt(Debt(person = person.trim(), amount = a, direction = dir, note = note.ifBlank { null })); onDismiss() }
        }, Modifier.fillMaxWidth(), enabled = person.isNotBlank() && (amount.toLongOrNull() ?: 0) > 0, style = ButtonStyle.Primary)
    }
}
