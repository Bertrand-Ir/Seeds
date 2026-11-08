package app.seeds.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.seeds.ui.components.ButtonStyle
import app.seeds.ui.components.PillButton
import app.seeds.ui.components.SeedsSheet
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType

enum class Restricted(val title: String, val why: String, val retryLabel: String) {
    SMS(
        "Android blocked SMS access",
        "Seeds was installed from outside the Play Store, so Android hides SMS access until you allow it once. Seeds only reads Mobile Money messages, on this phone.",
        "Try SMS access again",
    ),
    USAGE(
        "Turn on usage access",
        "Seeds needs this to notice when work time turns into scrolling. If the switch is greyed out (“Controlled by Restricted Setting”), Android wants you to allow it first.",
        "Open usage access",
    ),
}

/** Walks the user through Android's "Allow restricted settings" for sideloaded apps. */
@Composable
fun RestrictedHelp(kind: Restricted, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    SeedsSheet(onDismiss) {
        Text(kind.title, style = SeedsType.title, color = SeedsTheme.colors.ink)
        Spacer(Modifier.height(6.dp))
        Text(kind.why, style = SeedsType.body, color = SeedsTheme.colors.inkMuted)
        Spacer(Modifier.height(18.dp))
        Step(1, "Tap “Open app info” below.")
        Step(2, "Tap ⋮ in the top-right corner, then “Allow restricted settings”. Confirm with your PIN or fingerprint.")
        Step(3, "Come back and tap “${kind.retryLabel}”.")
        Spacer(Modifier.height(8.dp))
        Text(
            "Don't see “Allow restricted settings”? Try granting once first so Android records the attempt, then check again.",
            style = SeedsType.caption, color = SeedsTheme.colors.inkFaint,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Open app info", { Perms.openAppSettings(ctx) }, Modifier.weight(1f), style = ButtonStyle.Secondary)
            PillButton(kind.retryLabel.substringBefore(" again"), { onDismiss(); onRetry() }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(SeedsTheme.colors.accentSoft), contentAlignment = Alignment.Center) {
            Text("$n", style = SeedsType.label, color = SeedsTheme.colors.accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(text, style = SeedsType.body, color = SeedsTheme.colors.ink) }
    }
}
