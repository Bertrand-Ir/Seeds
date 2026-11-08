package app.seeds.ui.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seeds.data.db.Category
import app.seeds.data.db.Direction
import app.seeds.data.db.Txn
import app.seeds.data.prefs.Settings
import app.seeds.domain.Fmt
import app.seeds.seeds
import app.seeds.ui.components.ButtonStyle
import app.seeds.ui.components.ChoiceChip
import app.seeds.ui.components.PillButton
import app.seeds.ui.components.SeedsSheet
import app.seeds.ui.theme.SeedIcons
import app.seeds.ui.theme.SeedPalette
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType
import kotlinx.coroutines.launch

@Composable
fun seedsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SeedsTheme.colors.accent,
    unfocusedBorderColor = SeedsTheme.colors.line,
    focusedContainerColor = SeedsTheme.colors.surface,
    unfocusedContainerColor = SeedsTheme.colors.surface,
    cursorColor = SeedsTheme.colors.accent,
    focusedLabelColor = SeedsTheme.colors.accent,
    unfocusedLabelColor = SeedsTheme.colors.inkMuted,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(categories: List<Category>, direction: Direction, selected: Long?, onPick: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.filter { it.direction == direction }.forEach { cat ->
            ChoiceChip(cat.name, selected == cat.id, { onPick(cat.id) }, icon = SeedIcons.of(cat.icon), tint = SeedPalette.fillColor(cat.color))
        }
    }
}

/** Label (or correct) one transaction. */
@Composable
fun TxnSheet(txnId: Long, settings: Settings, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.seeds
    val scope = rememberCoroutineScope()
    val categories by c.money.categories.collectAsStateWithLifecycle(emptyList())
    var txn by remember { mutableStateOf<Txn?>(null) }
    var cat by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var person by remember { mutableStateOf("") }
    var showRaw by remember { mutableStateOf(false) }
    LaunchedEffect(txnId) {
        c.money.txn(txnId)?.let { t -> txn = t; cat = t.categoryId; note = t.note.orEmpty(); person = t.counterparty.orEmpty() }
    }
    val t = txn ?: return
    val inbound = t.direction == Direction.IN
    val selectedCat = categories.firstOrNull { it.id == cat }

    SeedsSheet(onDismiss) {
        Text(Fmt.signedMoney(t.amount, inbound, settings.currency), style = SeedsType.headline, color = if (inbound) SeedsTheme.colors.moneyIn else SeedsTheme.colors.ink)
        Text(
            listOfNotNull(t.counterparty, "${Fmt.dayShort(java.time.Instant.ofEpochMilli(t.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate())} ${Fmt.clock(t.timestamp)}", t.provider).joinToString(" · "),
            style = SeedsType.caption, color = SeedsTheme.colors.inkMuted,
        )
        if (t.fee > 0 || t.balanceAfter != null) {
            Text(
                listOfNotNull(t.fee.takeIf { it > 0 }?.let { "Fee ${Fmt.money(it, settings.currency)}" }, t.balanceAfter?.let { "Balance after ${Fmt.money(it, settings.currency)}" }).joinToString(" · "),
                style = SeedsType.caption, color = SeedsTheme.colors.inkMuted,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(if (inbound) "Where did it come from?" else "What was it for?", style = SeedsType.subtitle, color = SeedsTheme.colors.ink)
        Spacer(Modifier.height(10.dp))
        CategoryPicker(categories, t.direction, cat) { cat = it }
        Spacer(Modifier.height(16.dp))
        if (selectedCat?.special != null) {
            OutlinedTextField(person, { person = it }, label = { Text("Who?") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = seedsFieldColors())
            Text(
                when (selectedCat.special) {
                    Category.Special.LOAN_GIVEN -> "Seeds will remember they owe you this."
                    Category.Special.LOAN_TAKEN -> "Seeds will remember you owe this."
                    else -> "Seeds will reduce the matching debt."
                },
                style = SeedsType.caption, color = SeedsTheme.colors.inkMuted,
            )
            Spacer(Modifier.height(10.dp))
        }
        OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = seedsFieldColors())
        if (t.raw != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (showRaw) t.raw else "Show original message", style = SeedsType.caption, color = SeedsTheme.colors.inkFaint,
                modifier = Modifier.fillMaxWidth().clickable { showRaw = !showRaw },
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Delete", { scope.launch { c.money.deleteTxn(t); onDismiss() } }, style = ButtonStyle.Danger)
            PillButton("Save", {
                scope.launch {
                    val id = cat
                    if (id != null) c.money.label(t.id, id, note.ifBlank { null }, person.ifBlank { null })
                    else c.money.updateTxn(t.copy(note = note.ifBlank { null }))
                    app.seeds.notify.Notifier.cancel(ctx, app.seeds.notify.Notifier.moneyId(t.id))
                    onDismiss()
                }
            }, Modifier.weight(1f))
        }
    }
}

@Composable
fun AddTxnSheet(settings: Settings, categories: List<Category>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var direction by remember { mutableStateOf(Direction.OUT) }
    var amount by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<Long?>(null) }
    var who by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    SeedsSheet(onDismiss) {
        Text("Add a transaction", style = SeedsType.title, color = SeedsTheme.colors.ink)
        Text("Cash, bank or anything Seeds didn't catch.", style = SeedsType.body, color = SeedsTheme.colors.inkMuted)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Money out", direction == Direction.OUT, { direction = Direction.OUT; cat = null })
            ChoiceChip("Money in", direction == Direction.IN, { direction = Direction.IN; cat = null })
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            amount, { v -> amount = v.filter { it.isDigit() } }, label = { Text("Amount (${settings.currency})") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), colors = seedsFieldColors(),
            textStyle = SeedsType.title,
        )
        Spacer(Modifier.height(14.dp))
        CategoryPicker(categories, direction, cat) { cat = it }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(who, { who = it }, label = { Text(if (direction == Direction.OUT) "Paid to (optional)" else "From (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = seedsFieldColors())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = seedsFieldColors())
        Spacer(Modifier.height(20.dp))
        PillButton("Save", {
            val a = amount.toLongOrNull() ?: return@PillButton
            scope.launch {
                ctx.seeds.money.addManual(a, direction, cat, note.ifBlank { null }, who, System.currentTimeMillis())
                onDismiss()
            }
        }, Modifier.fillMaxWidth(), enabled = (amount.toLongOrNull() ?: 0) > 0)
    }
}
