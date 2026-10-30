package com.gamershub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gamershub.core.settings.ThemeMode
import com.gamershub.di.AppContainer
import com.gamershub.ui.components.GlassDivider
import com.gamershub.ui.screens.AnalyticsScreen
import com.gamershub.ui.screens.BoosterScreen
import com.gamershub.ui.screens.DashboardScreen
import com.gamershub.ui.screens.GamesScreen
import com.gamershub.ui.screens.MoreScreen
import com.gamershub.ui.screens.RecorderScreen
import com.gamershub.ui.screens.SettingsScreen
import com.gamershub.ui.screens.SpoofScreen
import com.gamershub.ui.screens.StreamScreen
import com.gamershub.ui.screens.ThermalScreen
import com.gamershub.ui.theme.GamersHubTheme
import com.gamershub.ui.theme.LocalGlass

object Routes {
    const val HOME = "home"
    const val BOOST = "boost"
    const val GAMES = "games"
    const val RECORD = "record"
    const val MORE = "more"
    const val THERMAL = "thermal"
    const val SPOOF = "spoof"
    const val STREAM = "stream"
    const val ANALYTICS = "analytics"
    const val SETTINGS = "settings"
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
    val themeMode by container.settings.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
    val accent by container.settings.accent.collectAsStateWithLifecycle("CYBER")
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    GamersHubTheme(darkTheme = dark, accent = accent) {
        val nav = rememberNavController()
        val g = LocalGlass.current

        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(g.bg2, g.bg)))
        ) {
            AppNavHost(nav, container)

            // Floating glass bottom bar
            val backStack by nav.currentBackStackEntryAsState()
            val currentRoute = backStack?.destination?.route
            val showBar = currentRoute in TABS.map { it.route }
            if (showBar) {
                GlassBottomBar(
                    nav = nav,
                    currentRoute = currentRoute,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, container: AppContainer) {
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = {
            slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(320))
        },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = {
            slideInHorizontally(tween(320)) { -it / 3 } + fadeIn(tween(320))
        },
        popExitTransition = { fadeOut(tween(200)) }
    ) {
        composable(Routes.HOME) { DashboardScreen(container, nav) }
        composable(Routes.BOOST) { BoosterScreen(container) }
        composable(Routes.GAMES) { GamesScreen(container) }
        composable(Routes.RECORD) { RecorderScreen(container) }
        composable(Routes.MORE) { MoreScreen(nav) }
        composable(Routes.THERMAL) { ThermalScreen(container) }
        composable(Routes.SPOOF) { SpoofScreen(container) }
        composable(Routes.STREAM) { StreamScreen(container) }
        composable(Routes.ANALYTICS) { AnalyticsScreen(container) }
        composable(Routes.SETTINGS) { SettingsScreen(container) }
    }
}

@Composable
private fun GlassBottomBar(
    nav: NavHostController,
    currentRoute: String?,
    modifier: Modifier = Modifier
) {
    val g = LocalGlass.current
    Box(modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(g.glass)
                .border(1.dp, g.stroke, RoundedCornerShape(24.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TABS.forEach { tab ->
                val selected = currentRoute == tab.route
                val tint by animateColorAsState(
                    if (selected) g.accent else g.textDim, tween(220), label = "tab"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
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
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        tab.icon, contentDescription = tab.label,
                        tint = tint, modifier = Modifier.size(22.dp)
                    )
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint
                    )
                    AnimatedVisibility(selected) {
                        Box(
                            Modifier
                                .padding(top = 2.dp)
                                .size(width = 14.dp, height = 3.dp)
                                .clip(CircleShape)
                                .background(Brush.horizontalGradient(listOf(g.accent, g.accent2)))
                        )
                    }
                }
            }
        }
    }
}
