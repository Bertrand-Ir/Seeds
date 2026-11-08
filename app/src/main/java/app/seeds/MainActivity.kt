package app.seeds

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.seeds.data.prefs.Settings
import app.seeds.ui.insights.GardenScreen
import app.seeds.ui.money.DebtsScreen
import app.seeds.ui.money.MoneyScreen
import app.seeds.ui.money.TxnSheet
import app.seeds.ui.onboarding.OnboardingScreen
import app.seeds.ui.review.ReviewScreen
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType
import app.seeds.ui.today.TodayScreen
import app.seeds.ui.you.AppsPickerScreen
import app.seeds.ui.you.SeedEditorScreen
import app.seeds.ui.you.UpdateSheet
import app.seeds.ui.you.YouScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        var ready = false
        splash.setKeepOnScreenCondition { !ready }
        pendingRoute = intent?.getStringExtra(EXTRA_ROUTE)

        setContent {
            val settings by seeds.settings.flow.collectAsStateWithLifecycle(null)
            val s = settings ?: return@setContent
            ready = true
            val dark = when (s.theme) { Settings.THEME_LIGHT -> false; Settings.THEME_DARK -> true; else -> androidx.compose.foundation.isSystemInDarkTheme() }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            SeedsTheme(s.theme) {
                Box(Modifier.fillMaxSize().background(SeedsTheme.colors.bg)) {
                    if (!s.onboarded) OnboardingScreen(s)
                    else SeedsNav(s, pendingRoute) { pendingRoute = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute = it }
    }

    override fun onResume() {
        super.onResume()
        val c = seeds
        c.scope.launch {
            runCatching { if (c.settings.current().autoUpdate) c.updates.check() }
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Today("Today", Icons.Rounded.WbSunny),
    Garden("Garden", Icons.Rounded.Spa),
    Money("Money", Icons.Rounded.AccountBalanceWallet),
    You("You", Icons.Rounded.Person),
}

@Composable
private fun SeedsNav(settings: Settings, route: String?, onRouteHandled: () -> Unit) {
    val nav = rememberNavController()
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var txnSheet by remember { mutableStateOf<Long?>(null) }
    var showUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(route) {
        when {
            route == null -> Unit
            route == "review" -> nav.navigate("review/today")
            route == "update" -> showUpdate = true
            route.startsWith("txn/") -> { tab = Tab.Money; txnSheet = route.removePrefix("txn/").toLongOrNull() }
        }
        if (route != null) onRouteHandled()
    }

    NavHost(nav, startDestination = "home") {
        composable("home") {
            Box(Modifier.fillMaxSize()) {
                Crossfade(tab, animationSpec = tween(220), label = "tabs") { t ->
                    when (t) {
                        Tab.Today -> TodayScreen(settings, onOpenReview = { nav.navigate("review/today") }, onPlantSeed = { nav.navigate("seed/0") }, onOpenUpdate = { showUpdate = true })
                        Tab.Garden -> GardenScreen(settings)
                        Tab.Money -> MoneyScreen(settings, onOpenTxn = { txnSheet = it }, onOpenDebts = { nav.navigate("debts") })
                        Tab.You -> YouScreen(
                            settings,
                            onEditSeed = { nav.navigate("seed/$it") },
                            onPickApps = { nav.navigate("apps") },
                            onOpenUpdate = { showUpdate = true },
                            onOpenReview = { nav.navigate("review/today") },
                        )
                    }
                }
                BottomBar(tab, Modifier.align(Alignment.BottomCenter)) { tab = it }
            }
        }
        composable("review/{date}", arguments = listOf(navArgument("date") { type = NavType.StringType })) { e ->
            val d = e.arguments?.getString("date")
            ReviewScreen(settings, d.takeIf { it != "today" }) { nav.popBackStack() }
        }
        composable("seed/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            SeedEditorScreen(e.arguments?.getLong("id") ?: 0L) { nav.popBackStack() }
        }
        composable("debts") { DebtsScreen(settings) { nav.popBackStack() } }
        composable("apps") { AppsPickerScreen(settings) { nav.popBackStack() } }
    }

    txnSheet?.let { id -> TxnSheet(id, settings) { txnSheet = null } }
    if (showUpdate) UpdateSheet { showUpdate = false }
}

@Composable
private fun BottomBar(selected: Tab, modifier: Modifier, onSelect: (Tab) -> Unit) {
    val c = SeedsTheme.colors
    Row(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .shadow(24.dp, RoundedCornerShape(32.dp), ambientColor = c.ink.copy(alpha = 0.2f), spotColor = c.ink.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(32.dp))
            .background(c.surface)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val sel = t == selected
            Row(
                Modifier
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (sel) c.accent else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(t) }
                    .padding(horizontal = if (sel) 18.dp else 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(t.icon, t.label, tint = if (sel) c.onAccent else c.inkMuted, modifier = Modifier.size(22.dp))
                if (sel) {
                    Spacer(Modifier.width(8.dp))
                    Text(t.label, style = SeedsType.label, color = c.onAccent)
                }
            }
        }
    }
}
