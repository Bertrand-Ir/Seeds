package app.seeds.ui.today

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.seeds.data.db.Seed
import app.seeds.data.db.SessionKind
import app.seeds.data.prefs.Settings
import app.seeds.domain.Fmt
import app.seeds.seeds
import app.seeds.ui.components.ChoiceChip
import app.seeds.ui.components.PillButton
import app.seeds.ui.components.SeedsCard
import app.seeds.ui.components.SeedsSheet
import app.seeds.ui.components.SettingRow
import app.seeds.ui.components.TimeDialog
import app.seeds.ui.theme.SeedIcons
import app.seeds.ui.theme.SeedPalette
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/** Forgot to start the timer? Log it honestly after the fact. */
@Composable
fun LogTimeSheet(settings: Settings, seeds: List<Seed>, preselect: Long?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val nowMin = LocalDateTime.now().let { it.hour * 60 + it.minute }
    var seedId by remember { mutableStateOf(preselect ?: seeds.firstOrNull()?.id) }
    var kind by remember { mutableStateOf(SessionKind.SEED) }
    var startMin by remember { mutableIntStateOf(Math.floorMod(nowMin - 30, 1440)) }
    var endMin by remember { mutableIntStateOf(nowMin) }
    var picking by remember { mutableStateOf<String?>(null) }

    SeedsSheet(onDismiss) {
        Text("Log time", style = SeedsType.title, color = SeedsTheme.colors.ink)
        Text("For time you forgot to track. Honest beats perfect.", style = SeedsType.body, color = SeedsTheme.colors.inkMuted)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            seeds.forEach { s ->
                ChoiceChip(s.name, kind == SessionKind.SEED && seedId == s.id, { kind = SessionKind.SEED; seedId = s.id }, icon = SeedIcons.of(s.icon), tint = SeedPalette.fillColor(s.color))
            }
            ChoiceChip("Break", kind == SessionKind.BREAK, { kind = SessionKind.BREAK })
            ChoiceChip("Drift", kind == SessionKind.DRIFT, { kind = SessionKind.DRIFT })
        }
        Spacer(Modifier.height(16.dp))
        SeedsCard {
            SettingRow("From", Fmt.clockOfDay(startMin), onClick = { picking = "start" })
            SettingRow("To", Fmt.clockOfDay(endMin), onClick = { picking = "end" })
            val dur = Math.floorMod(endMin - startMin, 1440)
            Text("Duration: ${Fmt.minutes(dur)}", style = SeedsType.label, color = SeedsTheme.colors.inkMuted)
        }
        Spacer(Modifier.height(20.dp))
        PillButton("Save", {
            scope.launch {
                val zone = ZoneId.systemDefault()
                val now = LocalDateTime.now()
                var end = now.toLocalDate().atStartOfDay().plusMinutes(endMin.toLong())
                if (end.isAfter(now)) end = end.minusDays(1)
                val start = end.minusMinutes(Math.floorMod(endMin - startMin, 1440).toLong())
                ctx.seeds.tracking.logManual(
                    if (kind == SessionKind.SEED) seedId else null, kind,
                    start.atZone(zone).toInstant().toEpochMilli(), end.atZone(zone).toInstant().toEpochMilli(),
                )
                onDismiss()
            }
        }, Modifier.fillMaxWidth(), enabled = kind != SessionKind.SEED || seedId != null)
        Spacer(Modifier.width(1.dp))
    }

    when (picking) {
        "start" -> TimeDialog("From", startMin, { picking = null }) { startMin = it; picking = null }
        "end" -> TimeDialog("To", endMin, { picking = null }) { endMin = it; picking = null }
    }
}
