package com.gamershub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gamershub.theme.CutCard
import com.gamershub.theme.CutChip
import com.gamershub.theme.GC

/**
 * Home screen skeleton — the genre frame (performance modes, games grid,
 * feature tiles) is in place; each screen gets matched pixel-by-pixel to
 * the reference screenshots as they arrive.
 */
@Composable
fun HomeScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 16.dp)
    ) {
        // ---- Performance modes ----
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeTile("❄", "COOL", selected = false)
            ModeTile("◆", "BALANCED", selected = true)
            ModeTile("🔥", "BEAST", selected = false)
            ModeTile("🚀", "EXTREME", selected = false)
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("GAMES")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GameTile("＋", "ADD GAME")
            GameTile("PUBG", "TAP TO BOOST")
            GameTile("DELTA", "TAP TO BOOST")
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("TOOLS")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolTile("🎮", "GAME BAR")
            ToolTile("🖥", "3D QUALITY")
            ToolTile("🎬", "RECORDER")
            ToolTile("🛡", "BOOST")
        }

        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(GC.DarkGlassBrush, CutCard)
                .border(1.dp, GC.Stroke, CutCard)
                .padding(18.dp)
        ) {
            Column {
                Text(
                    "The app is being rebuilt from scratch to match Red Corner.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = GC.Text
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Reference screenshots from you + the in-game panel guide every screen. " +
                        "Boost, unlock, recorder and the floating panel come back one by one — real and working.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GC.TextDim
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(18.dp)
                .height(3.dp)
                .background(GC.RedBrush)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = GC.Text,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun ModeTile(emoji: String, label: String, selected: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(120.dp)
            .then(
                if (selected) Modifier.background(GC.Red.copy(alpha = 0.18f), CutChip)
                else Modifier.background(GC.DarkGlassBrush, CutChip)
            )
            .border(1.dp, if (selected) GC.Red else GC.Stroke, CutChip)
            .padding(vertical = 14.dp)
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) GC.Red else GC.TextDim
        )
    }
}

@Composable
private fun GameTile(name: String, sub: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(150.dp)
            .height(110.dp)
            .background(GC.DarkGlassBrush, CutCard)
            .border(1.dp, GC.Stroke, CutCard)
            .padding(10.dp)
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium, color = GC.Text)
        Spacer(Modifier.height(4.dp))
        Text(sub, style = MaterialTheme.typography.labelSmall, color = GC.TextDim)
    }
}

@Composable
private fun ToolTile(emoji: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(110.dp)
            .background(GC.DarkGlassBrush, CutChip)
            .border(1.dp, GC.Stroke, CutChip)
            .padding(vertical = 12.dp)
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = GC.TextDim)
    }
}
