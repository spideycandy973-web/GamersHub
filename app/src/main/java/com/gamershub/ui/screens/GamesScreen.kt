package com.gamershub.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamershub.core.launcher.GameEntry
import com.gamershub.di.AppContainer
import com.gamershub.ui.components.GlassButton
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
    val scope = rememberCoroutineScope()
    val games by container.games.games.collectAsStateWithLifecycle()
    val allApps by container.games.allApps.collectAsStateWithLifecycle()
    val pinned by container.games.pinned.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<GameEntry?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { container.games.refresh() }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Game Launcher", style = MaterialTheme.typography.headlineLarge, color = g.text)
                Text(
                    "${games.size} apps · tap to launch · ⚙ binds a profile",
                    style = MaterialTheme.typography.labelSmall, color = g.textDim
                )
            }
            GlassChip("+ Add app", selected = true) {
                scope.launch { container.games.refresh() }
                showPicker = true
            }
        }

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
        ProfileEditorSheet(container = container, game = game, onDismiss = {
            editing = null
            scope.launch { container.games.refresh() }
        })
    }

    if (showPicker) {
        AppPickerSheet(
            apps = allApps,
            pinned = pinned,
            onToggle = { pkg ->
                val next = if (pkg in pinned) pinned - pkg else pinned + pkg
                container.games.setPinned(next)
            },
            onDismiss = { showPicker = false }
        )
    }
}

/** App selector: every launchable app, searchable, pinnable into the library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
    apps: List<GameEntry>,
    pinned: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val g = LocalGlass.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = g.bg2) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
                .height(560.dp)
        ) {
            Text("App selector", style = MaterialTheme.typography.headlineMedium, color = g.text)
            Text(
                "Add ANY app or game to your library — not just what Android calls a game.",
                style = MaterialTheme.typography.labelSmall, color = g.textDim
            )
            Spacer(Modifier.height(12.dp))
            GlassTextField(query, { query = it }, "Search apps…")
            Spacer(Modifier.height(10.dp))
            val filtered = apps.filter {
                query.isBlank() || it.label.contains(query, true) ||
                        it.packageName.contains(query, true)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    val isPinned = app.packageName in pinned
                    GlassCard(
                        corner = 16.dp,
                        highlight = isPinned,
                        onClick = { onToggle(app.packageName) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val bmp = remember(app.packageName) { app.icon?.toImageBitmap() }
                            if (bmp != null) {
                                Image(
                                    bmp, app.label,
                                    Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text("◻", style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = g.text, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    if (isPinned) "In library ✓" else if (app.isGame) "Game" else "App",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isPinned) g.accent else g.textDim
                                )
                            }
                        }
                    }
                }
            }
        }
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
                    Text("◻", style = MaterialTheme.typography.headlineMedium, color = g.textDim)
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
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Profile: ${game.label}",
                style = MaterialTheme.typography.headlineMedium, color = g.text
            )
            Text(game.packageName, style = MaterialTheme.typography.labelSmall, color = g.textDim)
            Spacer(Modifier.height(14.dp))

            GlassSwitchRow(
                "Auto-boost on launch",
                "Applies this profile when the app opens",
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
                    GlassChip(
                        tp.lowercase().replaceFirstChar { it.uppercase() },
                        profile.thermalProfile == tp
                    ) { profile = profile.copy(thermalProfile = tp) }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Spoof as", style = MaterialTheme.typography.titleMedium, color = g.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip("None", profile.spoofPresetId == null) {
                    profile = profile.copy(spoofPresetId = null)
                }
                com.gamershub.core.spoof.DeviceSpoofer.PRESETS.take(3).forEach { p ->
                    GlassChip(
                        p.displayName.substringBefore(" ").ifBlank { p.brand },
                        profile.spoofPresetId == p.id
                    ) { profile = profile.copy(spoofPresetId = p.id) }
                }
            }

            Spacer(Modifier.height(12.dp))
            GlassSwitchRow("Show FPS HUD", "Floating telemetry while this app runs", profile.showHud) {
                profile = profile.copy(showHud = it)
            }
            Spacer(Modifier.height(8.dp))
            GlassSwitchRow("Kill background on launch", "", profile.killBackground) {
                profile = profile.copy(killBackground = it)
            }
            Spacer(Modifier.height(8.dp))
            GlassSwitchRow("Block notifications", "", profile.blockNotifications) {
                profile = profile.copy(blockNotifications = it)
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton(
                    "Save profile",
                    onClick = {
                        container.profiles.upsert(profile)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
                if (container.profiles.profiles.value.containsKey(game.packageName)) {
                    GlassButton(
                        "Delete", primary = false,
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

private fun Drawable.toImageBitmap(): ImageBitmap? {
    return try {
        if (this is BitmapDrawable && bitmap != null) return bitmap.asImageBitmap()
        val bmp = Bitmap.createBitmap(
            maxOf(1, intrinsicWidth), maxOf(1, intrinsicHeight), Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bmp.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}
