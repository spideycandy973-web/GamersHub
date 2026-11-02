package com.gamershub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.gamershub.ui.Routes
import com.gamershub.ui.theme.LocalGlass

/**
 * The "More" tab — every tool on ONE screen. Compact horizontal cards so the
 * whole list fits the landscape display without cutting rows off (the user:
 * "it's not perfectly fit in screen").
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
                    CompactToolCard(emoji, title, sub, g.text, g.textDim, Modifier.weight(1f)) {
                        nav.navigate(route)
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** One compact tool row-card: icon tile + title + subtitle, ~84dp tall. */
@Composable
private fun CompactToolCard(
    emoji: String,
    title: String,
    sub: String,
    titleColor: Color,
    subColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xE6FFFFFF), Color(0xB3FFFFFF)))
            )
            .border(1.dp, Color(0x2E2A3A4D), RoundedCornerShape(18.dp))
            .clickable(interaction, indication = null) { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0x14FF3B4E), Color(0x0AFF3B4E)))
                )
                .border(1.dp, Color(0x22FF3B4E), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = subColor)
        }
    }
}
