package com.gamershub.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamershub.core.spoof.DeviceSpoofer
import com.gamershub.core.spoof.SpoofCapability
import com.gamershub.core.unlock.GameUnlockPreset
import com.gamershub.di.AppContainer
import com.gamershub.ui.components.GlassButton
import com.gamershub.ui.components.GlassCard
import com.gamershub.ui.components.GlassChip
import com.gamershub.ui.components.SectionTitle
import com.gamershub.ui.theme.GhColors
import com.gamershub.ui.theme.LocalGlass
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Game Unlock — the screen that actually gets max graphics into games.
 *
 * Two real mechanisms, both through Shizuku without root:
 *  1. **Game config unlock** — writes the games' own saved config files
 *     (PUBG `UserCustom.ini` CVars, Delta Force `config.ini` sliders, generic
 *     UE scalability) so the hidden Ultra / 120 FPS options appear.
 *  2. **Device spoof** — display/refresh tuning (+ root identity spoof when
 *     resetprop exists) for games that also whitelist hardware.
 */
@Composable
fun UnlockScreen(container: AppContainer) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val installed by container.unlocker.installed.collectAsStateWithLifecycle()
    val report by container.unlocker.lastReport.collectAsStateWithLifecycle()
    val busy by container.unlocker.busy.collectAsStateWithLifecycle()
    val capability by container.spoofer.capability.collectAsStateWithLifecycle()
    val applied by container.spoofer.appliedPreset.collectAsStateWithLifecycle()
    val shellReady by container.shizuku.shellReady.collectAsStateWithLifecycle()

    var selectedPreset by remember { mutableStateOf<GameUnlockPreset?>(null) }

    LaunchedEffect(shellReady) {
        if (shellReady) {
            container.unlocker.refreshInstalled()
            container.spoofer.detect()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Game Unlock",
            style = MaterialTheme.typography.headlineLarge,
            color = g.text, modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            "Unlocks the hidden max-graphics / max-FPS options inside the games " +
                    "themselves — the same method root GFX tools use, without root.",
            style = MaterialTheme.typography.labelSmall, color = g.textDim
        )

        // -------------------------------------------------------- status
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (shellReady) "Shizuku shell ready" else "Shizuku shell offline",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (shellReady) GhColors.Green else GhColors.Red
                    )
                    Text(
                        "Game configs live in Android/data — the shell can write them",
                        style = MaterialTheme.typography.labelSmall, color = g.textDim
                    )
                }
                if (busy) CircularProgressIndicator(Modifier.width(22.dp).height(22.dp), strokeWidth = 2.dp)
            }
        }

        // -------------------------------------------------------- installed games
        SectionTitle("DETECTED GAMES")
        if (installed.isEmpty()) {
            GlassCard {
                Text(
                    if (shellReady) "No known games found — or use “Any app” below"
                    else "Connect Shizuku first (Dashboard → Reconnect)",
                    style = MaterialTheme.typography.labelSmall, color = g.textDim
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.height(280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(installed, key = { it.packageName }) { game ->
                    val preset = game.preset ?: return@items
                    GlassCard(corner = 18.dp) {
                        Column {
                            Text(game.label, style = MaterialTheme.typography.titleMedium, color = g.text)
                            Text(
                                preset.notes,
                                style = MaterialTheme.typography.labelSmall, color = g.textDim
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassButton("Unlock max", onClick = {
                                    if (!com.gamershub.ui.requireEngine(container, context)) return@GlassButton
                                    selectedPreset = preset
                                    scope.launch {
                                        val p = if (container.settings.enhanceGameSharp.first())
                                            com.gamershub.core.unlock.GameUnlockPresets.sharpened(preset)
                                        else preset
                                        val r = container.unlocker.unlock(p)
                                        Toast.makeText(
                                            context,
                                            if (r.ok) "Unlocked ${r.game}" else "Unlock failed — see report",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                })
                                GlassButton("Restore", primary = false, onClick = {
                                    scope.launch {
                                        val r = container.unlocker.restore(preset)
                                        Toast.makeText(
                                            context, r.lines.lastOrNull() ?: "Done", Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------- any app
        SectionTitle("ANY APP")
        GlassCard {
            Text(
                "Pick any game or app — GamersHub detects its engine and applies " +
                        "the matching unlock recipe (Unreal Engine titles are auto-detected).",
                style = MaterialTheme.typography.labelSmall, color = g.textDim
            )
            Spacer(Modifier.height(10.dp))
            val allApps by container.games.allApps.collectAsStateWithLifecycle()
            var appQuery by remember { mutableStateOf("") }
            com.gamershub.ui.screens.GlassTextField(
                value = appQuery,
                onValueChange = { appQuery = it },
                label = "Search installed apps…"
            )
            Spacer(Modifier.height(8.dp))
            val filtered = allApps.filter {
                appQuery.isBlank() || it.label.contains(appQuery, true) ||
                        it.packageName.contains(appQuery, true)
            }.take(12)
            filtered.forEach { app ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyMedium, color = g.text)
                        Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = g.textDim)
                    }
                    GlassChip("Unlock", selected = false) {
                        if (!com.gamershub.ui.requireEngine(container, context)) return@GlassChip
                        val preset = com.gamershub.core.unlock.GameUnlockPresets
                            .forAnyPackage(app.packageName, app.label)
                        scope.launch {
                            val p = if (container.settings.enhanceGameSharp.first())
                                com.gamershub.core.unlock.GameUnlockPresets.sharpened(preset)
                            else preset
                            val r = container.unlocker.unlock(p)
                            Toast.makeText(
                                context, r.lines.lastOrNull() ?: "Done", Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------- device spoof
        SectionTitle("DEVICE SPOOF (hardware whitelist)")
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (capability) {
                            SpoofCapability.FULL_ROOT -> "Full spoof (root)"
                            SpoofCapability.DISPLAY_ONLY -> "Display spoof (Shizuku)"
                            else -> "Tap detect"
                        },
                        style = MaterialTheme.typography.titleMedium, color = g.text
                    )
                    Text(
                        when (capability) {
                            SpoofCapability.FULL_ROOT ->
                                "resetprop available — model identity + display spoof"
                            SpoofCapability.DISPLAY_ONLY ->
                                "Display resolution / density / refresh spoof — unlocks HFR toggles"
                            else -> "Scans for Shizuku + root capability"
                        },
                        style = MaterialTheme.typography.labelSmall, color = g.textDim
                    )
                }
                if (capability == SpoofCapability.UNKNOWN || capability == SpoofCapability.UNAVAILABLE) {
                    GlassChip("Detect", selected = true) {
                        scope.launch { container.spoofer.detect() }
                    }
                }
            }
        }
        DeviceSpoofer.PRESETS.forEach { preset ->
            val isApplied = applied?.id == preset.id
            GlassCard(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                highlight = isApplied
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(preset.displayName, style = MaterialTheme.typography.titleMedium, color = g.text)
                        Text(
                            "${preset.displayW}×${preset.displayH} @ ${preset.maxRefreshHz}Hz · ${preset.gpu}",
                            style = MaterialTheme.typography.labelSmall, color = g.textDim
                        )
                    }
                    GlassChip(if (isApplied) "Restore" else "Apply", selected = isApplied) {
                        if (!isApplied && !com.gamershub.ui.requireEngine(container, context)) {
                            return@GlassChip
                        }
                        scope.launch {
                            if (isApplied) {
                                container.spoofer.remove()
                                Toast.makeText(context, "Device restored", Toast.LENGTH_SHORT).show()
                            } else {
                                val lines = container.spoofer.apply(preset)
                                Toast.makeText(context, lines.lastOrNull() ?: "Applied", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------- report
        report?.let { r ->
            SectionTitle("LAST REPORT — ${r.game}")
            GlassCard {
                r.lines.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (line.startsWith("FAILED") || line.startsWith("Error")) GhColors.Red
                        else if (line.startsWith("✔") || line.startsWith("Done")) GhColors.Green
                        else g.textDim,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        GlassCard {
            Text("How this works", style = MaterialTheme.typography.titleMedium, color = g.text)
            Text(
                "• Games decide which graphics options to show by reading their own " +
                        "saved configs. GamersHub edits those files through the Shizuku shell " +
                        "(the exact files root GFX tools edit) and keeps a backup of every one.\n" +
                        "• Config unlock beats the device whitelist in PUBG Mobile, Delta Force, " +
                        "Free Fire, CODM and most Unreal Engine titles.\n" +
                        "• Game updates can reset configs — run Unlock again after big patches.\n" +
                        "• Every change is reversible from the Restore button.",
                style = MaterialTheme.typography.labelSmall, color = g.textDim,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(Modifier.height(110.dp))
    }
}
