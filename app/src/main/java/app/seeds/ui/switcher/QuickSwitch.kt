package app.seeds.ui.switcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seeds.data.db.SessionKind
import app.seeds.data.prefs.Settings
import app.seeds.domain.Engine
import app.seeds.domain.Fmt
import app.seeds.domain.SeedStat
import app.seeds.seeds
import app.seeds.ui.components.SeedGlyph
import app.seeds.ui.components.SeedsSheet
import app.seeds.ui.components.rememberNow
import app.seeds.ui.rememberDayData
import app.seeds.ui.theme.InnerShape
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType
import kotlinx.coroutines.launch

/** "What are you doing now?" — one tap to switch. Used in-app and from the widget/notifications. */
@Composable
fun ColumnScope.QuickSwitchContent(settings: Settings, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.seeds
    val scope = rememberCoroutineScope()
    val data = rememberDayData(settings)
    val now by rememberNow(30_000)
    val o = Engine.overview(data.clock, data.seeds, data.sessions, now)
    val active = o.active

    Text("What are you growing now?", style = SeedsType.title, color = SeedsTheme.colors.ink)
    Spacer(Modifier.height(4.dp))
    Text(
        if (active != null) "Switching ends the current one at ${Fmt.clock(System.currentTimeMillis())}." else "Pick one and the timer starts.",
        style = SeedsType.body, color = SeedsTheme.colors.inkMuted,
    )
    Spacer(Modifier.height(16.dp))

    val stats: List<SeedStat> = (o.daily.filter { it.appliesToday } + o.weekly + o.monthly + o.daily.filter { !it.appliesToday })
    Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { st ->
                    val isActive = active?.seedId == st.seed.id
                    SwitchTile(
                        modifier = Modifier.weight(1f),
                        selected = isActive,
                        leading = { SeedGlyph(st.seed.icon, st.seed.color, st.progress, 40.dp) },
                        title = st.seed.name,
                        subtitle = when {
                            isActive -> "Growing now"
                            st.grown -> "Grown · ${Fmt.duration(st.doneMs)}"
                            else -> "${Fmt.duration(st.remainingMs)} to go"
                        },
                    ) {
                        scope.launch { c.tracking.start(st.seed.id); onDone() }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SwitchTile(
                Modifier.weight(1f), active?.kind == SessionKind.BREAK,
                leading = { RoundIcon(Icons.Rounded.Coffee, SeedsTheme.colors.inkMuted) },
                title = "Break", subtitle = "Rest on purpose",
            ) { scope.launch { c.tracking.start(null, SessionKind.BREAK); onDone() } }
            SwitchTile(
                Modifier.weight(1f), active?.kind == SessionKind.DRIFT,
                leading = { RoundIcon(Icons.Rounded.Waves, SeedsTheme.colors.drift) },
                title = "Drifting", subtitle = "Be honest with yourself",
            ) { scope.launch { c.tracking.start(null, SessionKind.DRIFT); onDone() } }
        }
        if (active != null) {
            SwitchTile(
                Modifier.fillMaxWidth(), false,
                leading = { RoundIcon(Icons.Rounded.Stop, SeedsTheme.colors.ink) },
                title = "Stop tracking", subtitle = "Nothing for now",
            ) { scope.launch { c.tracking.stop(); onDone() } }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, tint: Color) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SwitchTile(
    modifier: Modifier,
    selected: Boolean,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val c = SeedsTheme.colors
    Row(
        modifier
            .clip(InnerShape)
            .background(if (selected) c.accentSoft else c.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = SeedsType.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = SeedsType.caption, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Translucent activity so the widget and notifications can open just the switcher sheet. */
class QuickSwitchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by seeds.settings.flow.collectAsStateWithLifecycle(null)
            val s = settings ?: return@setContent
            SeedsTheme(s.theme) {
                Box(Modifier.fillMaxSize())
                SeedsSheet(onDismiss = { finish() }) {
                    QuickSwitchContent(s) { finish() }
                }
            }
        }
    }
}
