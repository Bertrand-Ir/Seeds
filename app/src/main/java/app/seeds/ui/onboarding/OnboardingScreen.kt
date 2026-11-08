package app.seeds.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.seeds.data.db.Period
import app.seeds.data.db.Seed
import app.seeds.data.prefs.Settings
import app.seeds.data.repo.Defaults
import app.seeds.domain.Fmt
import app.seeds.notify.Alarms
import app.seeds.seeds
import app.seeds.ui.Perms
import app.seeds.ui.Restricted
import app.seeds.ui.RestrictedHelp
import app.seeds.ui.components.ButtonStyle
import app.seeds.ui.components.PillButton
import app.seeds.ui.components.SeedGlyph
import app.seeds.ui.components.SeedMark
import app.seeds.ui.components.SettingRow
import app.seeds.ui.components.TimeDialog
import app.seeds.ui.components.Wordmark
import app.seeds.ui.money.seedsFieldColors
import app.seeds.ui.rememberResumeTick
import app.seeds.ui.theme.Dimens
import app.seeds.ui.theme.InnerShape
import app.seeds.ui.theme.Pill
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType
import app.seeds.ui.you.PermRow
import kotlinx.coroutines.launch

private const val PAGES = 5

@Composable
fun OnboardingScreen(initial: Settings) {
    val ctx = LocalContext.current
    val c = ctx.seeds
    val scope = rememberCoroutineScope()
    var page by remember { mutableIntStateOf(0) }
    var s by remember { mutableStateOf(initial) }
    val picks = remember { mutableStateListOf<Seed>().apply { addAll(Defaults.seedIdeas.take(4)) } }

    fun finish() = scope.launch {
        c.settings.update { s.copy(onboarded = true) }
        c.tracking.plantInitial(picks.toList())
        Alarms.scheduleAll(ctx, c.settings.current())
        if (Perms.sms(ctx)) c.money.importInbox(ctx, s.momoSenders)
    }

    Column(Modifier.fillMaxSize().background(SeedsTheme.colors.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        if (page > 0) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(PAGES - 1) { i ->
                    Box(Modifier.weight(1f).height(4.dp).clip(Pill).background(if (i < page) SeedsTheme.colors.accent else SeedsTheme.colors.line))
                }
            }
        }
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(320)) { it / 4 * dir } + fadeIn(tween(320))) togetherWith (slideOutHorizontally(tween(260)) { -it / 4 * dir } + fadeOut(tween(200)))
            },
            modifier = Modifier.weight(1f),
            label = "onboarding",
        ) { p ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
                when (p) {
                    0 -> Welcome()
                    1 -> NamePage(s.name) { s = s.copy(name = it) }
                    2 -> RhythmPage(s) { s = it }
                    3 -> SeedsPage(picks)
                    else -> PermissionsPage()
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(Dimens.gutter), verticalAlignment = Alignment.CenterVertically) {
            if (page > 0) {
                PillButton("Back", { page-- }, style = ButtonStyle.Ghost)
                Spacer(Modifier.width(12.dp))
            }
            PillButton(
                when (page) { 0 -> "Plant my first seeds"; PAGES - 1 -> "Start growing"; else -> "Continue" },
                { if (page < PAGES - 1) page++ else finish() },
                Modifier.weight(1f),
                style = if (page == 0) ButtonStyle.Sprout else ButtonStyle.Primary,
                enabled = page != 3 || picks.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun ColumnScope.Welcome() {
    val scale = remember { Animatable(0.4f) }
    val rot = remember { Animatable(-40f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
        rot.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessVeryLow))
    }
    Spacer(Modifier.height(80.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(150.dp).clip(CircleShape).background(SeedsTheme.colors.accentSoft), contentAlignment = Alignment.Center) {
            SeedMark(84.dp, SeedsTheme.colors.accent, Modifier.scale(scale.value).rotate(rot.value))
        }
    }
    Spacer(Modifier.height(40.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Wordmark(34.dp) }
    Spacer(Modifier.height(24.dp))
    Text("Small seeds, every day.", style = SeedsType.headline, color = SeedsTheme.colors.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    Text(
        "Plan your non-negotiables. Track what you actually do with your hours and your money. See the truth — then grow anyway.",
        style = SeedsType.body, color = SeedsTheme.colors.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Heading(title: String, body: String) {
    Spacer(Modifier.height(12.dp))
    Text(title, style = SeedsType.headline, color = SeedsTheme.colors.ink)
    Spacer(Modifier.height(8.dp))
    Text(body, style = SeedsType.body, color = SeedsTheme.colors.inkMuted)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun NamePage(name: String, onChange: (String) -> Unit) {
    Heading("What should Seeds call you?", "Just for greetings. Everything you put in Seeds stays on this phone.")
    OutlinedTextField(name, onChange, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = seedsFieldColors(), textStyle = SeedsType.title)
}

@Composable
private fun RhythmPage(s: Settings, onChange: (Settings) -> Unit) {
    var pick by remember { mutableStateOf<String?>(null) }
    Heading("Your daily rhythm", "Your day runs from waking to sleep. Seeds sends your plan in the morning, your report before bed, and guards your work hours.")
    SettingRow("I wake up at", Fmt.clockOfDay(s.wakeMin), Icons.Rounded.WbSunny, onClick = { pick = "wake" })
    SettingRow("I go to sleep at", Fmt.clockOfDay(s.sleepMin), Icons.Rounded.Bedtime, onClick = { pick = "sleep" })
    SettingRow("Work starts", Fmt.clockOfDay(s.workStartMin), Icons.Rounded.Work, onClick = { pick = "ws" })
    SettingRow("Work ends", Fmt.clockOfDay(s.workEndMin), Icons.Rounded.Work, onClick = { pick = "we" })
    val awake = Math.floorMod(s.sleepMin - s.wakeMin, 1440)
    Spacer(Modifier.height(16.dp))
    Text("That's ${Fmt.minutes(awake)} awake every day. Let's spend them on purpose.", style = SeedsType.bodyStrong, color = SeedsTheme.colors.accent)
    when (pick) {
        "wake" -> TimeDialog("Wake up", s.wakeMin, { pick = null }) { onChange(s.copy(wakeMin = it)); pick = null }
        "sleep" -> TimeDialog("Sleep", s.sleepMin, { pick = null }) { onChange(s.copy(sleepMin = it)); pick = null }
        "ws" -> TimeDialog("Work starts", s.workStartMin, { pick = null }) { onChange(s.copy(workStartMin = it)); pick = null }
        "we" -> TimeDialog("Work ends", s.workEndMin, { pick = null }) { onChange(s.copy(workEndMin = it)); pick = null }
    }
}

@Composable
private fun SeedsPage(picks: MutableList<Seed>) {
    Heading("Choose your seeds", "Your non-negotiables and the time each deserves. Tap to choose, adjust the time. You can rename or add more later.")
    Defaults.seedIdeas.forEach { idea ->
        val idx = picks.indexOfFirst { it.name == idea.name }
        val chosen = idx >= 0
        val seed = if (chosen) picks[idx] else idea
        val step = when (seed.period) { Period.DAILY -> 15; Period.WEEKLY -> 60; Period.MONTHLY -> 60 }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(InnerShape)
                .background(if (chosen) SeedsTheme.colors.accentSoft else SeedsTheme.colors.surface)
                .clickable { if (chosen) picks.removeAt(idx) else picks.add(idea) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeedGlyph(seed.icon, seed.color, if (chosen) 1f else 0f, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(seed.name, style = SeedsType.bodyStrong, color = SeedsTheme.colors.ink)
                Text(
                    Fmt.minutes(seed.targetMinutes) + " " + when (seed.period) { Period.DAILY -> "a day"; Period.WEEKLY -> "a week"; Period.MONTHLY -> "a month" },
                    style = SeedsType.caption, color = SeedsTheme.colors.inkMuted,
                )
            }
            if (chosen) {
                MiniStep(Icons.Rounded.Remove) { picks[idx] = seed.copy(targetMinutes = (seed.targetMinutes - step).coerceAtLeast(step)) }
                Spacer(Modifier.width(6.dp))
                MiniStep(Icons.Rounded.Add) { picks[idx] = seed.copy(targetMinutes = seed.targetMinutes + step) }
            }
        }
    }
    val dailyTotal = picks.filter { it.period == Period.DAILY }.sumOf { it.targetMinutes }
    Spacer(Modifier.height(14.dp))
    Text("Daily seeds: ${Fmt.minutes(dailyTotal)} a day.", style = SeedsType.bodyStrong, color = SeedsTheme.colors.ink)
    Text(
        if (dailyTotal > 10 * 60) "That's a lot. Fewer seeds, fully grown, beat many half-grown." else "Ambitious, but honest. Good.",
        style = SeedsType.caption, color = SeedsTheme.colors.inkMuted,
    )
}

@Composable
private fun MiniStep(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(SeedsTheme.colors.surface).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = SeedsTheme.colors.ink, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PermissionsPage() {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    var refresh by remember { mutableIntStateOf(0) }
    val notif = remember(tick, refresh) { Perms.notifications(ctx) }
    val usage = remember(tick, refresh) { Perms.usage(ctx) }
    val sms = remember(tick, refresh) { Perms.sms(ctx) }
    val battery = remember(tick, refresh) { Perms.battery(ctx) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    var help by remember { mutableStateOf<Restricted?>(null) }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        refresh++
        if (!res.values.all { it }) help = Restricted.SMS
    }
    val askSms = { smsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)) }

    Heading("Let Seeds help", "Each permission powers one promise. Everything is processed on your phone — nothing is uploaded.")
    PermRow("Notifications", "Check-ins, morning plan, nightly report", Icons.Rounded.Notifications, notif) {
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    PermRow("Usage access", "Notices when work turns into scrolling", Icons.Rounded.QueryStats, usage) { help = Restricted.USAGE }
    PermRow("SMS", "Reads MoMo messages to track money", Icons.Rounded.Sms, sms) { askSms() }
    help?.let { kind ->
        RestrictedHelp(kind, onRetry = { if (kind == Restricted.SMS) askSms() else Perms.openUsageSettings(ctx) }, onDismiss = { help = null })
    }
    PermRow("Unrestricted battery", "So reminders arrive on time", Icons.Rounded.BatteryChargingFull, battery) { Perms.requestBattery(ctx) }
    Spacer(Modifier.height(12.dp))
    Text("You can change any of this later in You → Permissions.", style = SeedsType.caption, color = SeedsTheme.colors.inkFaint)
}
