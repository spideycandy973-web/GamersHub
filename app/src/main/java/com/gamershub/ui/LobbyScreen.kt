package com.gamershub.ui

import com.gamershub.ui.components.AIcon

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.data.InstalledApp
import com.gamershub.theme.GC
import com.gamershub.theme.LocalAccent
import com.gamershub.ui.components.CircleIconButton
import com.gamershub.ui.components.HeroMark
import com.gamershub.ui.components.IconChip

/** Lobby — hero card, game library, rows / empty state. Fit-safe on all screens. */
@Composable
fun LobbyScreen(
    cpuPct: Int,
    library: List<InstalledApp>,
    version: String,
    playMinutes: (String) -> Long,
    libraryCount: Int,
    onSelectApps: () -> Unit,
    onAddGames: () -> Unit,
    onGameTap: (InstalledApp) -> Unit,
    onGameSpace: () -> Unit
) {
    val accent = LocalAccent.current
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 120.dp)
    ) {
        // ---- Header ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Lobby", style = MaterialTheme.typography.displaySmall, color = GC.Text, fontSize = 33.sp)
                Text(
                    com.gamershub.ui.components.Brand.BYLINE,
                    style = MaterialTheme.typography.labelMedium,
                    color = GC.TextDim,
                    fontSize = 13.sp,
                    letterSpacing = 0.6.sp
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GC.SurfaceHi)
                    .padding(horizontal = 13.dp, vertical = 8.dp)
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(8.dp))
                Text("Local", style = MaterialTheme.typography.labelLarge, color = GC.TextSoft, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- Hero card ----
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(GC.HeroBrush)
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(com.gamershub.R.drawable.axgc_new),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
                    .size(170.dp, 120.dp)
            )
            Column {
                Column(Modifier.padding(start = 18.dp, top = 16.dp, bottom = 12.dp)) {
                    Text("GAME", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp, letterSpacing = 2.sp)
                    Text("CORNER", style = MaterialTheme.typography.headlineLarge, color = GC.Text, fontSize = 30.sp)
                    Text("V2.5.3-25300", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                ) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(GC.SurfaceHi), contentAlignment = Alignment.Center) {
                        AIcon(Icons.Filled.Apps, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("My Apps", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$libraryCount", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp)
                            Text(" added", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(accent)
                                    .clickable(onClick = onSelectApps)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    "SELECT",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = GC.OnAccent,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(15.dp))
                            .background(GC.SurfaceHi)
                            .clickable(onClick = onGameSpace)
                            .padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp)
                    ) {
                        Column {
                            Text("Game", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
                            Text("Space", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        AIcon(Icons.Filled.Rocket, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- Library header ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Game Library", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp)
            Spacer(Modifier.width(8.dp))
            Text("${library.size} / 10", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GC.SurfaceHi)
                    .clickable(onClick = onAddGames)
                    .padding(start = 15.dp, end = 7.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Text("Add Games", style = MaterialTheme.typography.labelMedium, color = GC.TextSoft, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(25.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    AIcon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (library.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(GC.Surface)
                    .padding(vertical = 26.dp, horizontal = 18.dp)
            ) {
                IconChip(Icons.Filled.Gamepad, size = 64)
                Spacer(Modifier.height(13.dp))
                Text("No Games / Apps Detected!", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 20.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Add prioritized applications to start Axeron Space.",
                    style = MaterialTheme.typography.labelMedium,
                    color = GC.TextDim,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(alpha = 0.85f))
                        .clickable(onClick = onAddGames)
                        .padding(horizontal = 22.dp, vertical = 11.dp)
                ) {
                    AIcon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Add Games", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 15.sp)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                library.forEach { app ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(GC.Surface)
                            .clickable { onGameTap(app) }
                            .padding(12.dp)
                    ) {
                        Image(
                            painter = androidx.compose.ui.graphics.painter.BitmapPainter(app.icon),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(15.dp))
                        )
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.name, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(13.dp).clip(CircleShape).border(2.dp, GC.TextDim, CircleShape))
                                Spacer(Modifier.width(5.dp))
                                Text("${playMinutes(app.pkg)}m", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
                            }
                        }
                        CircleIconButton(Icons.Filled.ChevronRight, onClick = { onGameTap(app) })
                    }
                }
            }
        }
    }
}
