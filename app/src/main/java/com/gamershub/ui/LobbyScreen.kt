package com.gamershub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.data.InstalledApp
import com.gamershub.theme.GC
import com.gamershub.ui.components.CircleIconButton
import com.gamershub.ui.components.HeroMark
import com.gamershub.ui.components.IconChip

/**
 * Lobby — hero brand card (energy + Game Space), game library with rows
 * or the empty state. Pixel-matched to the reference.
 */
@Composable
fun LobbyScreen(
    uid: String,
    energy: Int,
    library: List<InstalledApp>,
    version: String,
    playMinutes: (String) -> Long,
    onAddGames: () -> Unit,
    onGameTap: (InstalledApp) -> Unit,
    onGameSpace: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(top = 18.dp, bottom = 130.dp)
    ) {
        // ---- Header ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Lobby", style = MaterialTheme.typography.displaySmall, color = GC.Text, fontSize = 38.sp)
                Text("By GamersHub", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 14.sp)
            }
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GC.SurfaceHi)
                    .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(GC.TagBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text("ID", style = MaterialTheme.typography.labelMedium, color = GC.Orange, fontSize = 12.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(uid, style = MaterialTheme.typography.labelLarge, color = GC.Text, fontSize = 18.sp)
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- Hero card ----
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(GC.HeroBrush)
        ) {
            HeroMark(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .size(210.dp, 150.dp)
            )
            Column {
                Column(Modifier.padding(start = 22.dp, top = 20.dp, bottom = 14.dp)) {
                    Text(
                        "GAME",
                        style = MaterialTheme.typography.labelMedium,
                        color = GC.TextDim,
                        fontSize = 15.sp,
                        letterSpacing = 2.sp
                    )
                    Text("HUB", style = MaterialTheme.typography.headlineLarge, color = GC.Text, fontSize = 34.sp)
                    Text(version, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 14.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                        .padding(horizontal = 22.dp, vertical = 14.dp)
                ) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(GC.SurfaceHi),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, tint = GC.TextSoft, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Energy", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 15.sp)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$energy", style = MaterialTheme.typography.headlineMedium, color = GC.Text, fontSize = 24.sp)
                            Text(" / 560", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 15.sp)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(GC.Orange),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add energy", tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(GC.SurfaceHi)
                            .clickable(onClick = onGameSpace)
                            .padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 10.dp)
                    ) {
                        Column {
                            Text("Game", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 14.sp)
                            Text("Space", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 23.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Filled.Rocket, contentDescription = null, tint = GC.TextDim, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- Library header ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Game Library", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 21.sp)
            Spacer(Modifier.width(10.dp))
            Text("${library.size} / 10", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GC.SurfaceHi)
                    .clickable(onClick = onAddGames)
                    .padding(start = 18.dp, end = 8.dp, top = 7.dp, bottom = 7.dp)
            ) {
                Text("Add Games", style = MaterialTheme.typography.labelMedium, color = GC.TextSoft, fontSize = 15.sp)
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(GC.Orange),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (library.isEmpty()) {
            // ---- Empty state ----
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(GC.Surface)
                    .padding(vertical = 30.dp)
            ) {
                IconChip(Icons.Filled.Gamepad, size = 72)
                Spacer(Modifier.height(16.dp))
                Text("No Games / Apps Detected!", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 23.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Add prioritized applications to start GamersHub Space.",
                    style = MaterialTheme.typography.labelMedium,
                    color = GC.TextDim,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GC.Orange.copy(alpha = 0.85f))
                        .clickable(onClick = onAddGames)
                        .padding(horizontal = 26.dp, vertical = 13.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Games", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 17.sp)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                library.forEach { app ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(GC.Surface)
                            .clickable { onGameTap(app) }
                            .padding(14.dp)
                    ) {
                        Icon(
                            androidx.compose.ui.graphics.painter.BitmapPainter(app.icon),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp))
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.name, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 21.sp)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(16.dp).clip(CircleShape).border(2.dp, GC.TextDim, CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${playMinutes(app.pkg)}m",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GC.TextDim,
                                    fontSize = 15.sp
                                )
                            }
                        }
                        CircleIconButton(Icons.Filled.ChevronRight, onClick = { onGameTap(app) })
                    }
                }
            }
        }
    }
}
