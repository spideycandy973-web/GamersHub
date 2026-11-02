package com.gamershub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.gamershub.ui.Routes
import com.gamershub.ui.theme.GlassThemeColors
import com.gamershub.ui.theme.LocalGlass

/**
 * The "More" tab — every tool on ONE screen. Compact horizontal cards so the
 * whole list fits the landscape display. Every surface, border and text colour
 * comes from the theme (LocalGlass) so dark mode and light mode are both
 * first-class — the old hardcoded light palette made dark mode unreadable.
 */
@Composable
fun MoreScreen(nav: NavHostController) {
    val g = LocalGlass.current
    val items = listOf(
        Triple("🎮", "Game Space", "ROG-style in-game overlay panel") to Routes.GAMESPACE,
        Triple("🔓", "Game Unlock", "Max graphics + FPS in your games") to Routes.UNLOCK,
        Triple("🎚", "Lossless Scaling", "Upscaling + real frame generation") to Routes.SCALING,
        Triple("✨", "Graphics Enhancer", "Sharpness, GPU pipeline & colours") to Routes.ENHANCE,
        Triple("🔬", "Live Proof", "Real commands & outputs — see it working") to Routes.PROOF,
        Triple("🌡", "Thermal Control", "Anti-throttle profiles & governor") to Routes.THERMAL,
        Triple("📡", "Live Streaming", "YouTube · Facebook · Instagram + chat") to Routes.STREAM,
        Triple("📊", "Analytics", "FPS, thermals & session history") to Routes.ANALYTICS,
        Triple("⚙️", "Settings", "Theme, gestures, Shizuku, engine") to Routes.SETTINGS,
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        Text(
            "More",
            style = MaterialTheme.typography.headlineLarge,
            color = g.text,
            modifier = Modifier.padding(top = 16.dp)
        )
        Spacer(Modifier.height(10.dp))

        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { (info, route) ->
                    val (emoji, title, sub) = info
                    CompactToolCard(g, emoji, title, sub, Modifier.weight(1f)) {
                        nav.navigate(route)
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * One compact tool row-card: icon tile + title + subtitle, ~84dp tall.
 * All colours are theme-driven (works in dark AND light); the icon tile
 * follows the active accent; the card scales on press like every other card.
 */
@Composable
private fun CompactToolCard(
    g: GlassThemeColors,
    emoji: String,
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
        label = "tool-press"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(vertical = 5.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(4.dp, RoundedCornerShape(18.dp), clip = false, ambientColor = g.accent.copy(alpha = 0.18f))
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(listOf(g.glassHi, g.glass))
            )
            .border(1.dp, g.stroke, RoundedCornerShape(18.dp))
            .clickable(interaction, indication = null) { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            g.accent.copy(alpha = 0.24f),
                            g.accent2.copy(alpha = 0.10f)
                        )
                    )
                )
                .border(1.dp, g.accent.copy(alpha = 0.35f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = g.text)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = g.textDim)
        }
    }
}
