package com.gamershub.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.gamershub.ui.Routes
import com.gamershub.ui.components.GlassCard
import com.gamershub.ui.theme.LocalGlass

@Composable
fun MoreScreen(nav: NavHostController) {
    val g = LocalGlass.current

        val items = listOf(
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
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "More",
            style = MaterialTheme.typography.headlineLarge,
            color = g.text, modifier = Modifier.padding(top = 18.dp)
        )
        Spacer(Modifier.height(14.dp))

        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { (info, route) ->
                    val (emoji, title, sub) = info
                    GlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 6.dp),
                        onClick = { nav.navigate(route) }
                    ) {
                        Text(emoji, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(title, style = MaterialTheme.typography.titleMedium, color = g.text)
                        Text(sub, style = MaterialTheme.typography.labelSmall, color = g.textDim)
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(110.dp))
    }
}
