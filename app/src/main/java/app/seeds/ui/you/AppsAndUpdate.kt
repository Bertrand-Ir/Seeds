package app.seeds.ui.you

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seeds.data.prefs.Settings
import app.seeds.seeds
import app.seeds.ui.components.PillButton
import app.seeds.ui.components.ScreenTitle
import app.seeds.ui.components.SeedsCard
import app.seeds.ui.components.SeedsSheet
import app.seeds.ui.money.seedsFieldColors
import app.seeds.ui.theme.Dimens
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType
import app.seeds.update.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AppInfo(val pkg: String, val label: String, val icon: ImageBitmap?)

@Composable
fun AppsPickerScreen(settings: Settings, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<AppInfo>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != ctx.packageName }
                .map { pkg ->
                    val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                    AppInfo(
                        pkg,
                        info?.let { pm.getApplicationLabel(it).toString() } ?: pkg,
                        runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
                    )
                }
                .sortedWith(compareByDescending<AppInfo> { it.pkg in settings.distractingApps }.thenBy { it.label.lowercase() })
        }
    }
    val selected = settings.distractingApps

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, bottom = 40.dp)) {
        item {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = SeedsTheme.colors.ink) }
            ScreenTitle("Drift guard", "Distracting apps")
            Text("Long stints in these apps during work hours trigger a gentle check-in.", style = SeedsType.body, color = SeedsTheme.colors.inkMuted)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(query, { query = it }, label = { Text("Search apps") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = seedsFieldColors())
            Spacer(Modifier.height(8.dp))
        }
        val list = apps
        if (list == null) item { Text("Loading apps…", style = SeedsType.body, color = SeedsTheme.colors.inkMuted) }
        else items(list.filter { query.isBlank() || it.label.contains(query, true) }, key = { it.pkg }) { app ->
            val on = app.pkg in selected
            Row(
                Modifier.fillMaxWidth().clickable {
                    scope.launch { ctx.seeds.settings.update { s -> s.copy(distractingApps = if (on) s.distractingApps - app.pkg else s.distractingApps + app.pkg) } }
                }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (app.icon != null) Image(app.icon, null, Modifier.size(36.dp)) else Spacer(Modifier.size(36.dp))
                Spacer(Modifier.width(14.dp))
                Text(app.label, style = SeedsType.bodyStrong, color = SeedsTheme.colors.ink, modifier = Modifier.weight(1f))
                Checkbox(on, null, colors = CheckboxDefaults.colors(checkedColor = SeedsTheme.colors.accent, checkmarkColor = SeedsTheme.colors.onAccent, uncheckedColor = SeedsTheme.colors.inkFaint))
            }
        }
    }
}

@Composable
fun UpdateSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val updates = ctx.seeds.updates
    val scope = rememberCoroutineScope()
    val release by updates.available.collectAsStateWithLifecycle()
    val download by updates.download.collectAsStateWithLifecycle()
    var checking by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        updates.check(force = true).onFailure { error = it.message }
        checking = false
    }

    SeedsSheet(onDismiss) {
        Text("Updates", style = SeedsType.title, color = SeedsTheme.colors.ink)
        Text("You have Seeds ${updates.currentVersion}.", style = SeedsType.body, color = SeedsTheme.colors.inkMuted)
        Spacer(Modifier.height(16.dp))
        val r = release
        when {
            checking -> Text("Checking GitHub…", style = SeedsType.body, color = SeedsTheme.colors.inkMuted)
            error != null -> Text(error!!, style = SeedsType.body, color = SeedsTheme.colors.drift)
            r == null -> Text("You're on the latest version. 🌱", style = SeedsType.bodyStrong, color = SeedsTheme.colors.ink)
            else -> {
                SeedsCard(color = SeedsTheme.colors.surface) {
                    Text(r.title, style = SeedsType.subtitle, color = SeedsTheme.colors.ink)
                    Spacer(Modifier.height(8.dp))
                    Text(r.notes.ifBlank { "Improvements and fixes." }.take(1200), style = SeedsType.body, color = SeedsTheme.colors.inkMuted)
                }
                Spacer(Modifier.height(16.dp))
                when (val d = download) {
                    is DownloadState.Running -> {
                        app.seeds.ui.components.Meter(d.progress, SeedsTheme.colors.accent)
                        Spacer(Modifier.height(8.dp))
                        Text("Downloading… ${(d.progress * 100).toInt()}%", style = SeedsType.caption, color = SeedsTheme.colors.inkMuted)
                    }
                    else -> {
                        if (d is DownloadState.Failed) Text(d.message, style = SeedsType.caption, color = SeedsTheme.colors.drift)
                        PillButton(if (d is DownloadState.Ready) "Install ${r.version}" else "Download & install", {
                            scope.launch { updates.downloadAndInstall(ctx, r) }
                        }, Modifier.fillMaxWidth())
                        if (!updates.canInstall(ctx)) {
                            Text("Android will ask you to allow installs from Seeds the first time.", style = SeedsType.caption, color = SeedsTheme.colors.inkFaint, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "View on GitHub", style = SeedsType.label, color = SeedsTheme.colors.accent,
                    modifier = Modifier.padding(vertical = 8.dp).clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.pageUrl))) },
                )
            }
        }
    }
}
