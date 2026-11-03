package com.gamershub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gamershub.core.settings.ThemeMode
import com.gamershub.core.shizuku.ShizukuManager
import com.gamershub.di.AppContainer
import com.gamershub.ui.components.GlassDivider
import com.gamershub.ui.screens.AnalyticsScreen
import com.gamershub.ui.screens.BoosterScreen
import com.gamershub.ui.screens.DashboardScreen
import com.gamershub.ui.screens.EnhanceScreen
import com.gamershub.ui.screens.GamesScreen
import com.gamershub.ui.screens.GameSpaceScreen
import com.gamershub.ui.screens.MoreScreen
import com.gamershub.ui.screens.ProofScreen
import com.gamershub.ui.screens.RecorderScreen
import com.gamershub.ui.screens.ScalingScreen
import com.gamershub.ui.screens.SettingsScreen
import com.gamershub.ui.screens.StreamScreen
import com.gamershub.ui.screens.ThermalScreen
import com.gamershub.ui.screens.UnlockScreen
import com.gamershub.ui.theme.GamersHubTheme
import com.gamershub.ui.theme.LocalGlass

object Routes {
    const val HOME = "home"
    const val BOOST = "boost"
    const val GAMES = "games"
    const val RECORD = "record"
    const val MORE = "more"
    const val THERMAL = "thermal"
    const val UNLOCK = "unlock"
    const val SCALING = "scaling"
    const val STREAM = "stream"
    const val ANALYTICS = "analytics"
    const val SETTINGS = "settings"
    const val ENHANCE = "enhance"
    const val PROOF = "proof"
    const val GAMESPACE = "gamespace"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.BOOST, "Boost", Icons.Filled.Speed),
    Tab(Routes.GAMES, "Games", Icons.Filled.SportsEsports),
    Tab(Routes.RECORD, "Record", Icons.Filled.FiberManualRecord),
    Tab(Routes.MORE, "More", Icons.Filled.GridView),
)

@Composable
fun GamersHubRoot(container: AppContainer) {
    val themeMode by container.settings.themeMode.collectAsStateWithLifecycle(ThemeMode.DARK)
    val accent by container.settings.accent.collectAsStateWithLifecycle("CRIMSON")
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    GamersHubTheme(darkTheme = dark, accent = accent) {
        val nav = rememberNavController()
        val g = LocalGlass.current

        // Real Shizuku permission flow: as soon as the app opens and the Shizuku
        // service is alive but not yet granted, show the system-level permission
        // dialog automatically — the engines only go live once it is granted.
        val shizukuStatus by container.shizuku.status.collectAsStateWithLifecycle()
        LaunchedEffect(shizukuStatus) {
            if (shizukuStatus == ShizukuManager.Status.PERMISSION_NEEDED) {
                container.shizuku.requestPermission()
            } else if (shizukuStatus == ShizukuManager.Status.READY) {
                container.shizuku.bindShell()
            }
        }

        Column(Modifier.fillMaxSize()) {
            GameCornerHeader(container)
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(g.bg2, g.bg)))
            ) {
                val backStack by nav.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route
                val showBar = currentRoute in TABS.map { it.route }

                // glass navigation rail pinned to the left edge
                if (showBar) {
                    GlassNavRail(
                        nav = nav,
                        currentRoute = currentRoute
                    )
                }
                Box(Modifier.weight(1f)) {
                    AppNavHost(nav, container)
                }
            }
        }
    }
}

/**
 * Game-corner top strip: glowing brand mark, live CPU / RAM / TEMP stats and
 * the master engine switch — the app's identity bar.
 */
@Composable
private fun GameCornerHeader(container: AppContainer) {
    val g = LocalGlass.current
    val stats by container.monitor.stats.collectAsStateWithLifecycle()
    val engineOn by container.engine.enabled.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(g.bg, g.bg2, g.bg)))
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(
            "◆ GAMERSHUB",
            style = MaterialTheme.typography.titleMedium.copy(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = g.accent.copy(alpha = 0.8f),
                    blurRadius = 24f
                )
            ),
            color = g.accent
        )
        Spacer(Modifier.width(18.dp))
        HeaderStat("CPU", if (stats.cpuUsagePct >= 0) "${stats.cpuUsagePct.toInt()}%" else "--%")
        HeaderStat(
            "RAM",
            if (stats.memTotalMb > 0) "${(stats.memUsedMb * 100 / stats.memTotalMb).toInt()}%" else "--%"
        )
        HeaderStat("TEMP", "${stats.batteryTempC.toInt()}°")
        Spacer(Modifier.weight(1f))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                .background(
                    if (engineOn) Brush.horizontalGradient(listOf(g.accent, g.accent2))
                    else Brush.verticalGradient(listOf(g.glassHi, g.glass))
                )
                .clickable {
                    scope.launch { runCatching { container.engine.setEnabled(!engineOn) } }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                if (engineOn) "ENGINE ON" else "ENGINE OFF",
                style = MaterialTheme.typography.labelLarge,
                color = if (engineOn) androidx.compose.ui.graphics.Color.White else g.textDim
            )
        }
    }
}

@Composable
private fun HeaderStat(name: String, value: String) {
    val g = LocalGlass.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.labelSmall, color = g.textDim)
        Spacer(Modifier.width(5.dp))
        Text(value, style = MaterialTheme.typography.labelSmall, color = g.accent)
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, container: AppContainer) {
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = {
            slideInHorizontally(tween(520, easing = FastOutSlowInEasing)) { it } +
                fadeIn(tween(420)) +
                scaleIn(tween(520, easing = FastOutSlowInEasing), initialScale = 0.9f)
        },
        exitTransition = {
            fadeOut(tween(240)) + scaleOut(tween(240), targetScale = 0.96f)
        },
        popEnterTransition = {
            slideInHorizontally(tween(520, easing = FastOutSlowInEasing)) { -it } +
                fadeIn(tween(420)) +
                scaleIn(tween(520, easing = FastOutSlowInEasing), initialScale = 0.9f)
        },
        popExitTransition = {
            fadeOut(tween(240)) + scaleOut(tween(240), targetScale = 0.96f)
        }
    ) {
        composable(Routes.HOME) { DashboardScreen(container, nav) }
        composable(Routes.BOOST) { BoosterScreen(container) }
        composable(Routes.GAMES) { GamesScreen(container) }
        composable(Routes.RECORD) { RecorderScreen(container) }
        composable(Routes.MORE) { MoreScreen(nav) }
        composable(Routes.THERMAL) { ThermalScreen(container) }
        composable(Routes.UNLOCK) { UnlockScreen(container) }
        composable(Routes.SCALING) { ScalingScreen(container) }
        composable(Routes.STREAM) { StreamScreen(container) }
        composable(Routes.ANALYTICS) { AnalyticsScreen(container) }
        composable(Routes.SETTINGS) { SettingsScreen(container) }
        composable(Routes.ENHANCE) { EnhanceScreen(container) }
        composable(Routes.PROOF) { ProofScreen(container) }
        composable(Routes.GAMESPACE) { GameSpaceScreen(container) { nav.navigate(it) } }
    }
}

@Composable
private fun GlassNavRail(
    nav: NavHostController,
    currentRoute: String?
) {
    val g = LocalGlass.current
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(82.dp)
            .systemBarsPadding()
            .padding(start = 8.dp, top = 10.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(g.glass)
            .border(1.dp, g.stroke, RoundedCornerShape(24.dp))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TABS.forEach { tab ->
            val selected = currentRoute == tab.route
            val tint by animateColorAsState(
                if (selected) g.accent else g.textDim, tween(220), label = "tab"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        if (!selected) {
                            nav.navigate(tab.route) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                    .padding(vertical = 5.dp)
            ) {
                Icon(
                    tab.icon, contentDescription = tab.label,
                    tint = tint, modifier = Modifier.size(22.dp)
                )
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    maxLines = 1
                )
                AnimatedVisibility(selected) {
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .size(width = 16.dp, height = 3.dp)
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(g.accent, g.accent2)))
                    )
                }
            }
        }
    }
}
