package app.seeds.ui.you

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seeds.data.Backup
import app.seeds.data.db.Period
import app.seeds.data.prefs.Settings
import app.seeds.domain.Fmt
import app.seeds.notify.Alarms
import app.seeds.seeds
import app.seeds.tracking.Effects
import app.seeds.ui.Perms
import app.seeds.ui.components.ButtonStyle
import app.seeds.ui.components.ChoiceChip
import app.seeds.ui.components.PillButton
import app.seeds.ui.components.ScreenTitle
import app.seeds.ui.components.SectionHeader
import app.seeds.ui.components.SeedGlyph
import app.seeds.ui.components.SeedsCard
import app.seeds.ui.components.SettingRow
import app.seeds.ui.components.SwitchRow
import app.seeds.ui.components.TimeDialog
import app.seeds.ui.components.Wordmark
import app.seeds.ui.money.seedsFieldColors
import app.seeds.ui.rememberResumeTick
import app.seeds.ui.theme.Dimens
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
fun YouScreen(
    settings: Settings,
    onEditSeed: (Long) -> Unit,
    onPickApps: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenReview: () -> Unit,
) {
    val ctx = LocalContext.current
    val c = ctx.seeds
    val scope = rememberCoroutineScope()
    val seeds by c.tracking.allSeeds.collectAsStateWithLifecycle(emptyList())
    var showArchived by remember { mutableStateOf(false) }
    var timePick by remember { mutableStateOf<String?>(null) }
    val resume = rememberResumeTick()

    fun update(t: (Settings) -> Settings) = scope.launch {
        c.settings.update(t)
        val s = c.settings.current()
        Alarms.scheduleAll(ctx, s)
        Effects.refresh(ctx)
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) scope.launch {
            val n = c.money.importInbox(ctx, settings.momoSenders)
            Toast.makeText(ctx, "Imported $n transactions", Toast.LENGTH_SHORT).show()
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val json = Backup.export(c.db)
            withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
            Toast.makeText(ctx, "Backup saved", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = runCatching {
                val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } } ?: error("Empty file")
                Backup.import(c.db, text)
                Effects.refresh(ctx)
            }
            Toast.makeText(ctx, r.fold({ "Backup restored" }, { it.message ?: "Couldn't restore" }), Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, top = 8.dp, bottom = 140.dp),
    ) {
        item {
            ScreenTitle(null, if (settings.name.isBlank()) "You" else settings.name)
            Spacer(Modifier.height(4.dp))
            Text("Your seeds, your rhythm, your rules.", style = SeedsType.body, color = SeedsTheme.colors.inkMuted)
        }

        // --- Seeds ---------------------------------------------------------------------------------
        item { SectionHeader("Your seeds", action = if (showArchived) "Hide archived" else "Archived", onAction = { showArchived = !showArchived }) }
        item {
            SeedsCard(padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                seeds.filter { showArchived || !it.archived }.forEach { s ->
                    SettingRow(
                        s.name + if (s.archived) " (archived)" else "",
                        "${Fmt.minutes(s.targetMinutes)} " + when (s.period) { Period.DAILY -> "a day"; Period.WEEKLY -> "a week"; Period.MONTHLY -> "a month" } +
                            if (s.focus) " · drift-guarded" else "",
                        onClick = { onEditSeed(s.id) },
                        trailing = null,
                    )
                }
                Spacer(Modifier.height(8.dp))
                PillButton("Plant a new seed", { onEditSeed(0) }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, icon = Icons.Rounded.Add, height = 46.dp)
                Spacer(Modifier.height(8.dp))
            }
        }

        // --- Rhythm --------------------------------------------------------------------------------
        item { SectionHeader("Daily rhythm") }
        item {
            SeedsCard(padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                SettingRow("Wake up", Fmt.clockOfDay(settings.wakeMin), Icons.Rounded.WbSunny, onClick = { timePick = "wake" })
                SettingRow("Sleep", "${Fmt.clockOfDay(settings.sleepMin)} · report arrives ${Alarms.NIGHT_LEAD_MIN} min before", Icons.Rounded.Bedtime, onClick = { timePick = "sleep" })
                SettingRow("Work starts", Fmt.clockOfDay(settings.workStartMin), Icons.Rounded.Work, onClick = { timePick = "workStart" })
                SettingRow("Work ends", Fmt.clockOfDay(settings.workEndMin), Icons.Rounded.Work, onClick = { timePick = "workEnd" })
                Text("Work days", style = SeedsType.label, color = SeedsTheme.colors.inkMuted, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_LETTERS.forEachIndexed { i, l ->
                        val on = settings.workDays and (1 shl i) != 0
                        ChoiceChip(l, on, { update { it.copy(workDays = it.workDays xor (1 shl i)) } })
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        // --- Honest tracking ---------------------------------------------------------------------------
        item { SectionHeader("Honest tracking") }
        item {
            SeedsCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Check in every", style = SeedsType.bodyStrong, color = SeedsTheme.colors.ink)
                Text("Seeds asks if you're still on the same thing.", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(20, 30, 45, 60, 90, 0).forEach { m ->
                        ChoiceChip(if (m == 0) "Never" else Fmt.minutes(m), settings.checkInMin == m, { update { it.copy(checkInMin = m) } })
                    }
                }
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    "Pause if I don't answer",
                    "After ${settings.graceMin} min of silence, stop counting at the check-in time. You can restore it.",
                    settings.autoTrim,
                ) { v -> update { it.copy(autoTrim = v) } }
            }
        }

        // --- Drift guard ------------------------------------------------------------------------------
        item { SectionHeader("Drift guard") }
        item {
            val usageOk = remember(resume) { Perms.usage(ctx) }
            SeedsCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                SwitchRow("Watch for drift", "During work hours and focus seeds, ask when a distracting app takes over.", settings.driftEnabled) { v ->
                    update { it.copy(driftEnabled = v) }
                }
                Text("Ask after", style = SeedsType.label, color = SeedsTheme.colors.inkMuted, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 10, 15).forEach { m -> ChoiceChip("$m min", settings.driftThresholdMin == m, { update { it.copy(driftThresholdMin = m) } }) }
                }
                Spacer(Modifier.height(6.dp))
                SettingRow("Distracting apps", "${settings.distractingApps.size} apps watched", Icons.Rounded.Apps, onClick = onPickApps)
                if (!usageOk) SettingRow("Usage access needed", "Without it Seeds can't see which app is open.", Icons.Rounded.ErrorOutline, onClick = { Perms.openUsageSettings(ctx) })
            }
        }

        // --- Money --------------------------------------------------------------------------------------
        item { SectionHeader("Money") }
        item {
            var senders by remember(settings.momoSenders) { mutableStateOf(settings.momoSenders.joinToString(", ")) }
            var currency by remember(settings.currency) { mutableStateOf(settings.currency) }
            val smsOk = remember(resume) { Perms.sms(ctx) }
            SeedsCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                SwitchRow("Track Mobile Money", "Ask what each MoMo payment was for.", settings.moneyEnabled) { v -> update { it.copy(moneyEnabled = v) } }
                SettingRow(
                    if (smsOk) "Import past MoMo messages" else "Allow SMS access",
                    if (smsOk) "Backfill the last 4 months. Duplicates are skipped." else "Needed to read MoMo confirmations.",
                    Icons.Rounded.Sms,
                    onClick = { smsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)) },
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(senders, { senders = it }, label = { Text("MoMo sender names") }, modifier = Modifier.fillMaxWidth(), colors = seedsFieldColors(),
                    supportingText = { Text("Comma-separated. Matches the SMS sender, e.g. M-Money.") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(currency, { currency = it.uppercase().take(4) }, label = { Text("Currency") }, singleLine = true, modifier = Modifier.width(140.dp), colors = seedsFieldColors())
                    Spacer(Modifier.weight(1f))
                    PillButton("Save", {
                        update { s -> s.copy(momoSenders = senders.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(), currency = currency.ifBlank { "RWF" }) }
                        Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
                    }, style = ButtonStyle.Secondary, height = 44.dp)
                }
            }
        }

        // --- Rituals ----------------------------------------------------------------------------------
        item { SectionHeader("Rituals") }
        item {
            SeedsCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                SwitchRow("Morning plan", "At ${Fmt.clockOfDay(settings.wakeMin)}: today's seeds and where to start.", settings.morningBrief) { v -> update { it.copy(morningBrief = v) } }
                SwitchRow("Evening report", "Before bed: your score, the truth, a moment to reflect.", settings.nightReview) { v -> update { it.copy(nightReview = v) } }
                SettingRow("Open today's review", null, onClick = onOpenReview)
            }
        }

        // --- Permissions --------------------------------------------------------------------------------
        item { SectionHeader("Permissions") }
        item {
            val r = resume
            val notif = remember(r) { Perms.notifications(ctx) }
            val usage = remember(r) { Perms.usage(ctx) }
            val sms = remember(r) { Perms.sms(ctx) }
            val battery = remember(r) { Perms.battery(ctx) }
            SeedsCard(padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                PermRow("Notifications", "Check-ins, drift, money, reports", Icons.Rounded.Notifications, notif) {
                    if (android.os.Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else Perms.openAppSettings(ctx)
                }
                PermRow("Usage access", "Detects scrolling during work", Icons.Rounded.QueryStats, usage) { Perms.openUsageSettings(ctx) }
                PermRow("SMS", "Reads Mobile Money messages", Icons.Rounded.Sms, sms) {
                    smsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
                }
                PermRow("Unrestricted battery", "Keeps timers and check-ins reliable", Icons.Rounded.BatteryChargingFull, battery) { Perms.requestBattery(ctx) }
                Text(
                    "If Android blocks SMS access: App info → ⋮ → Allow restricted settings.",
                    style = SeedsType.caption, color = SeedsTheme.colors.inkFaint, modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        // --- Data ----------------------------------------------------------------------------------------
        item { SectionHeader("Your data") }
        item {
            SeedsCard(padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Everything stays on this phone. Back it up now and then.", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted, modifier = Modifier.padding(vertical = 8.dp))
                SettingRow("Export backup", "Save a .json file", Icons.Rounded.Upload, onClick = { exportLauncher.launch("seeds-backup-${java.time.LocalDate.now()}.json") })
                SettingRow("Restore backup", "Replaces what's on this phone", Icons.Rounded.Download, onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) })
            }
        }

        // --- Updates & appearance -------------------------------------------------------------------------
        item { SectionHeader("App") }
        item {
            var repo by remember(settings.githubRepo) { mutableStateOf(settings.githubRepo) }
            SeedsCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Theme", style = SeedsType.label, color = SeedsTheme.colors.inkMuted, modifier = Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("System", settings.theme == Settings.THEME_SYSTEM, { update { it.copy(theme = Settings.THEME_SYSTEM) } })
                    ChoiceChip("Light", settings.theme == Settings.THEME_LIGHT, { update { it.copy(theme = Settings.THEME_LIGHT) } })
                    ChoiceChip("Dark", settings.theme == Settings.THEME_DARK, { update { it.copy(theme = Settings.THEME_DARK) } })
                }
                Spacer(Modifier.height(10.dp))
                SettingRow("Check for updates", "Version ${c.updates.currentVersion}", Icons.Rounded.SystemUpdate, onClick = onOpenUpdate)
                SwitchRow("Notify me about updates", "Checks GitHub a few times a day.", settings.autoUpdate) { v -> update { it.copy(autoUpdate = v) } }
                OutlinedTextField(repo, { repo = it.trim() }, label = { Text("GitHub repository") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = seedsFieldColors(),
                    supportingText = { Text("owner/repo that publishes Seeds releases") })
                if (repo != settings.githubRepo) {
                    PillButton("Save repository", { update { it.copy(githubRepo = repo) } }, style = ButtonStyle.Secondary, height = 44.dp)
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Wordmark(20.dp, SeedsTheme.colors.inkFaint)
                Spacer(Modifier.height(6.dp))
                Text("Small seeds, every day.", style = SeedsType.caption, color = SeedsTheme.colors.inkFaint)
            }
        }
    }

    when (timePick) {
        "wake" -> TimeDialog("Wake up", settings.wakeMin, { timePick = null }) { m -> update { it.copy(wakeMin = m) }; timePick = null }
        "sleep" -> TimeDialog("Sleep", settings.sleepMin, { timePick = null }) { m -> update { it.copy(sleepMin = m) }; timePick = null }
        "workStart" -> TimeDialog("Work starts", settings.workStartMin, { timePick = null }) { m -> update { it.copy(workStartMin = m) }; timePick = null }
        "workEnd" -> TimeDialog("Work ends", settings.workEndMin, { timePick = null }) { m -> update { it.copy(workEndMin = m) }; timePick = null }
    }
}

@Composable
fun PermRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, granted: Boolean, onFix: () -> Unit) {
    SettingRow(title, subtitle, icon, onClick = if (granted) null else onFix) {
        if (granted) Icon(Icons.Rounded.CheckCircle, "Granted", tint = SeedsTheme.colors.accent)
        else Text("Allow", style = SeedsType.label, color = SeedsTheme.colors.accent)
    }
}
