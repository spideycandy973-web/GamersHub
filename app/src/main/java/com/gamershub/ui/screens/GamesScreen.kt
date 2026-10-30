package com.gamershub.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamershub.core.launcher.GameEntry
import com.gamershub.core.profiles.GameProfile
import com.gamershub.core.spoof.DeviceSpoofer
import com.gamershub.di.AppContainer
import com.gamershub.ui.components.GlassCard
import com.gamershub.ui.components.GlassChip
import com.gamershub.ui.components.GlassSwitchRow
import com.gamershub.ui.components.SectionTitle
import com.gamershub.ui.theme.GhColors
import com.gamershub.ui.theme.LocalGlass
import kotlinx.coroutines.launch

@Composable
fun GamesScreen(container: AppContainer) {
    val g = LocalGlass.current
    val games by container.games.games.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<GameEntry?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) { container.games.refresh() }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Game Launcher",
            style = MaterialTheme.typography.headlineLarge,
            color = g.text, modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            "${games.size} games · tap to launch · ⚙ binds a per-game profile",
            style = MaterialTheme.typography.labelSmall, color = g.textDim
        )

        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(games, key = { it.packageName }) { game ->
                GameTile(
                    game = game,
                    onLaunch = { container.games.launch(game.packageName) },
                    onEdit = { editing = game }
                )
            }
        }
    }

    editing?.let { game ->
        ProfileEditorSheet(
            container = container,
            game = game,
            onDismiss = {
                editing = null
                scope.launch { container.games.refresh() }
            }
        )
    }
}

@Composable
private fun GameTile(game: GameEntry, onLaunch: () -> Unit, onEdit: () -> Unit) {
    val g = LocalGlass.current
    GlassCard(onClick = onLaunch, corner = 18.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                val bitmap = remember(game.packageName) { game.icon?.toImageBitmap() }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap, contentDescription = game.label,
                        modifier = Modifier.size(52.dp), contentScale = ContentScale.Crop
                    )
                } else {
                    Text("🎮", style = MaterialTheme.typography.headlineMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                game.label,
                style = MaterialTheme.typography.labelSmall,
                color = g.text, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                if (game.hasProfile) "● profile" else "· · ·",
                style = MaterialTheme.typography.labelSmall,
                color = if (game.hasProfile) g.accent else g.textDim
            )
            Spacer(Modifier.height(4.dp))
            GlassChip("⚙", selected = false, onClick = onEdit)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditorSheet(container: AppContainer, game: GameEntry, onDismiss: () -> Unit) {
    val g = LocalGlass.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val existing = container.profiles.profileFor(game.packageName)
    var profile by remember { mutableStateOf(existing.copy(label = game.label)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = g.bg2
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
                .verticalScroll(rememberScrollStateCompat())
        ) {
            Text(
                "Profile: ${game.label}",
                style = MaterialTheme.typography.headlineMedium, color = g.text
            )
            Text(
                game.packageName,
                style = MaterialTheme.typography.labelSmall, color = g.textDim
            )
            Spacer(Modifier.height(14.dp))

            GlassSwitchRow(
                "Auto-boost on launch",
                "GamersHub engine applies this profile when the game opens",
                profile.boostEnabled
            ) { profile = profile.copy(boostEnabled = it) }

            Spacer(Modifier.height(12.dp))
            Text("Refresh lock", style = MaterialTheme.typography.titleMedium, color = g.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60, 90, 120, 144).forEach { hz ->
                    GlassChip("$hz", profile.refreshRate == hz) {
                        profile = profile.copy(refreshRate = hz)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Resolution scale: ${(profile.resolutionScale * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium, color = g.text
            )
            Slider(
                value = profile.resolutionScale,
                onValueChange = {
                    profile = profile.copy(resolutionScale = (it * 20f).toInt() / 20f)
                },
                valueRange = 0.7f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = g.accent, activeTrackColor = g.accent,
                    inactiveTrackColor = g.stroke
                )
            )

            Spacer(Modifier.height(12.dp))
            Text("Thermal profile", style = MaterialTheme.typography.titleMedium, color = g.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("COOL", "BALANCED", "BEAST", "EXTREME").forEach { tp ->
                    GlassChip(tp.lowercase().replaceFirstChar { it.uppercase() }, profile.thermalProfile == tp) {
                        profile = profile.copy(thermalProfile = tp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Spoof as", style = MaterialTheme.typography.titleMedium, color = g.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip("None", profile.spoofPresetId == null) {
                    profile = profile.copy(spoofPresetId = null)
                }
                DeviceSpoofer.PRESETS.take(3).forEach { p ->
                    GlassChip(
                        p.displayName.substringBefore(" ").ifBlank { p.brand },
                        profile.spoofPresetId == p.id
                    ) { profile = profile.copy(spoofPresetId = p.id) }
                }
            }

            Spacer(Modifier.height(12.dp))
            GlassSwitchRow(
                "Show FPS HUD", "Floating telemetry while this game runs",
                profile.showHud
            ) { profile = profile.copy(showHud = it) }
            Spacer(Modifier.height(8.dp))
            GlassSwitchRow(
                "Kill background on launch", "", profile.killBackground
            ) { profile = profile.copy(killBackground = it) }
            Spacer(Modifier.height(8.dp))
            GlassSwitchRow(
                "Block notifications", "", profile.blockNotifications
            ) { profile = profile.copy(blockNotifications = it) }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                com.gamershub.ui.components.GlassButton(
                    "SAVE PROFILE",
                    onClick = {
                        container.profiles.upsert(profile)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
                if (container.profiles.profiles.value.containsKey(game.packageName)) {
                    com.gamershub.ui.components.GlassButton(
                        "DELETE", primary = false,
                        onClick = {
                            container.profiles.remove(game.packageName)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberScrollStateCompat() = androidx.compose.foundation.rememberScrollState()

private fun Drawable.toImageBitmap(): ImageBitmap? {
    return try {
        if (this is BitmapDrawable && bitmap != null) return bitmap.asImageBitmap()
        val bmp = Bitmap.createBitmap(
            maxOf(1, intrinsicWidth), maxOf(1, intrinsicHeight), Bitmap.Config.ARGB_8888
        )
        val canvas = AndroidCanvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bmp.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}
