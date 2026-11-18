package app.seeds.ui.drift

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.seeds.data.prefs.Settings
import app.seeds.domain.DayClock
import app.seeds.domain.Fmt
import app.seeds.domain.Quotes
import app.seeds.seeds
import app.seeds.tracking.AppLabels
import app.seeds.tracking.DriftActions
import app.seeds.tracking.DriftState
import app.seeds.tracking.Effects
import app.seeds.ui.components.ButtonStyle
import app.seeds.ui.components.PillButton
import app.seeds.ui.components.SeedMark
import app.seeds.ui.theme.InnerShape
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType
import kotlinx.coroutines.launch

/**
 * The drift check: a full-screen pause between you and the feed. Not a notification you can
 * swipe away without thinking — a moment to choose.
 */
class DriftActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val pkg = DriftState.currentPkg
        val since = DriftState.currentSince.takeIf { it > 0 } ?: System.currentTimeMillis()
        val app = pkg?.let { AppLabels.label(this, it) } ?: "your phone"
        setContent {
            SeedsTheme(Settings.THEME_DARK) {
                DriftScreen(app, since, onFinish = { finish() })
            }
        }
    }

    companion object {
        fun intent(ctx: Context): Intent = Intent(ctx, DriftActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** Full screen right away when Android allows it ("Display over other apps"). */
        fun tryShow(ctx: Context): Boolean {
            if (!AndroidSettings.canDrawOverlays(ctx)) return false
            return runCatching { ctx.startActivity(intent(ctx)) }.isSuccess
        }
    }
}

private enum class Step { ASK, QUOTE }

@Composable
private fun DriftScreen(app: String, since: Long, onFinish: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val c = ctx.seeds
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.ASK) }
    var driftToday by remember { mutableLongStateOf(0L) }
    var resumeName by remember { mutableStateOf<String?>(null) }
    var resumeId by remember { mutableStateOf<Long?>(null) }
    var context by remember { mutableStateOf("It's work hours.") }
    val minutes = ((System.currentTimeMillis() - since) / 60_000).coerceAtLeast(1)

    LaunchedEffect(Unit) {
        val o = Effects.overview(ctx)
        driftToday = o.driftMs
        val seed = o.activeSeed ?: DriftState.resumeSeedId?.let { c.tracking.seed(it) }
        resumeId = seed?.id
        resumeName = seed?.name
        context = if (o.activeSeed != null) "You were growing ${o.activeSeed.name}." else "It's work hours."
    }

    BackHandler { DriftActions.closing(ctx); onFinish() }

    val sc = SeedsTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2A1512), sc.bg, sc.bg))),
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { (fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 8 }) togetherWith fadeOut(tween(200)) },
            label = "drift",
        ) { s ->
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            ) {
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SeedMark(18.dp, sc.drift)
                    Spacer(Modifier.width(8.dp))
                    Text("DRIFT CHECK", style = SeedsType.overline, color = sc.drift)
                }
                when (s) {
                    Step.ASK -> Ask(app, minutes, context, driftToday,
                        onDrifting = { scope.launch { DriftActions.drifting(ctx); step = Step.QUOTE } },
                        onResearch = { DriftActions.research(ctx); onFinish() },
                        onBreak = { scope.launch { DriftActions.breakTime(ctx); onFinish() } },
                        onClosing = { DriftActions.closing(ctx); DriftActions.goHome(ctx); onFinish() },
                    )
                    Step.QUOTE -> QuoteStep(app, driftToday + minutes * 60_000, resumeName,
                        onBack = { scope.launch { DriftActions.backTo(ctx, resumeId); onFinish() } },
                        onMore = { DriftState.snoozeUntil = System.currentTimeMillis() + 5 * 60_000L; onFinish() },
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ColumnScope.Ask(
    app: String, minutes: Long, context: String, driftToday: Long,
    onDrifting: () -> Unit, onResearch: () -> Unit, onBreak: () -> Unit, onClosing: () -> Unit,
) {
    val sc = SeedsTheme.colors
    Spacer(Modifier.height(48.dp))
    Text("$minutes min", style = SeedsType.display.copy(fontSize = 72.sp, lineHeight = 76.sp), color = sc.drift)
    Text("on $app", style = SeedsType.headline, color = sc.ink)
    Spacer(Modifier.height(12.dp))
    Text(context + if (driftToday > 0) " You've drifted ${Fmt.duration(driftToday)} today." else "", style = SeedsType.body, color = sc.inkMuted)
    Spacer(Modifier.height(36.dp))
    Text("What's happening?", style = SeedsType.title, color = sc.ink)
    Spacer(Modifier.height(14.dp))
    Option(Icons.Rounded.Waves, "I'm drifting", "Be honest. That's how it changes.", sc.drift, onDrifting)
    Option(Icons.Rounded.Search, "Research for work", "I'll leave you alone for 30 minutes.", sc.accent, onResearch)
    Option(Icons.Rounded.Coffee, "Taking a break", "Counted as rest, not drift.", Color(0xFF8CCBFF), onBreak)
    Option(Icons.AutoMirrored.Rounded.ExitToApp, "Closing it now", "Back to what matters.", sc.ink, onClosing)
}

@Composable
private fun Option(icon: ImageVector, title: String, sub: String, tint: Color, onClick: () -> Unit) {
    val sc = SeedsTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(InnerShape).background(sc.surface).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = SeedsType.subtitle, color = sc.ink)
            Text(sub, style = SeedsType.caption, color = sc.inkMuted)
        }
    }
}

@Composable
private fun ColumnScope.QuoteStep(app: String, driftToday: Long, resumeName: String?, onBack: () -> Unit, onMore: () -> Unit) {
    val sc = SeedsTheme.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val quote = remember {
        val day = runCatching { java.time.LocalDate.now() }.getOrDefault(java.time.LocalDate.now())
        Quotes.pick(day, DriftState.driftsToday)
    }
    Spacer(Modifier.height(56.dp))
    Icon(Icons.Rounded.FormatQuote, null, tint = sc.sprout, modifier = Modifier.size(48.dp))
    Spacer(Modifier.height(8.dp))
    Text(
        quote.text,
        style = TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 40.sp),
        color = sc.ink,
    )
    Spacer(Modifier.height(16.dp))
    Text("— ${quote.author}", style = SeedsType.bodyStrong, color = sc.inkMuted)
    Spacer(Modifier.height(40.dp))
    Box(Modifier.fillMaxWidth().clip(InnerShape).background(sc.surface).padding(16.dp)) {
        Column {
            Text("DRIFTED TODAY", style = SeedsType.overline, color = sc.inkMuted)
            Text(Fmt.duration(driftToday), style = SeedsType.headline, color = sc.drift)
            Text("Life is short. You can still win this day.", style = SeedsType.caption, color = sc.inkMuted)
        }
    }
    Spacer(Modifier.height(24.dp))
    PillButton(if (resumeName != null) "Close $app · back to $resumeName" else "Close $app", onBack, Modifier.fillMaxWidth(), style = ButtonStyle.Sprout)
    Spacer(Modifier.height(10.dp))
    PillButton("5 more minutes", onMore, Modifier.fillMaxWidth(), style = ButtonStyle.Ghost)
}
