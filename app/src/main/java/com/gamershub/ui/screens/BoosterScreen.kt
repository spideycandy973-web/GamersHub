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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamershub.core.boost.BoostPlan
import com.gamershub.core.boost.BoostState
import com.gamershub.di.AppContainer
import com.gamershub.ui.components.GlassButton
import com.gamershub.ui.components.GlassCard
import com.gamershub.ui.components.GlassChip
import com.gamershub.ui.components.GlassSwitchRow
import com.gamershub.ui.components.SectionTitle
import com.gamershub.ui.theme.LocalGlass
import kotlinx.coroutines.launch

@Composable
fun BoosterScreen(container: AppContainer) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val state by container.boost.state.collectAsStateWithLifecycle()
    val log by container.boost.log.collectAsStateWithLifecycle()
    val shellReady by container.shizuku.shellReady.collectAsStateWithLifecycle()

    var refresh by remember { mutableStateOf(120) }
    var scale by remember { mutableStateOf(1.0f) }
    var killBg by remember { mutableStateOf(true) }
    var blockNotif by remember { mutableStateOf(true) }
    var tweaks by remember { mutableStateOf(true) }

    val active = state as? BoostState.Active

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Boost Engine",
            style = MaterialTheme.typography.headlineLarge,
            color = g.text,
            modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            if (active != null) "Active${active.gamePackage?.let { " · $it" } ?: " (global)"}"
            else "Idle — configure and apply",
            style = MaterialTheme.typography.labelSmall, color = g.textDim
        )

        SectionTitle("PERFORMANCE PLAN")
        GlassCard {
            Text("Refresh-rate lock", style = MaterialTheme.typography.titleMedium, color = g.text)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60, 90, 120, 144).forEach { hz ->
                    GlassChip("$hz", refresh == hz) { refresh = hz }
                }
            }
            Text(
                "Hz — panels lower than the pick will fall back to their native max.",
                style = MaterialTheme.typography.labelSmall, color = g.textDim,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "Render resolution scale: ${(scale * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium, color = g.text
            )
            Slider(
                value = scale,
                onValueChange = { scale = (it * 20f).toInt() / 20f },
                valueRange = 0.7f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = g.accent,
                    activeTrackColor = g.accent,
                    inactiveTrackColor = g.stroke
                )
            )
            Text(
                "100% = native. Lower = more FPS (GPU-bound games). Restored after boost.",
                style = MaterialTheme.typography.labelSmall, color = g.textDim
            )

            Spacer(Modifier.height(14.dp))
            GlassSwitchRow(
                "Kill background apps", "Free RAM before gaming (am kill-all)",
                killBg
            ) { killBg = it }
            Spacer(Modifier.height(10.dp))
            GlassSwitchRow(
                "Block notifications", "Gaming DND while boost is active",
                blockNotif
            ) { blockNotif = it }
            Spacer(Modifier.height(10.dp))
            GlassSwitchRow(
                "Smoothness tweaks",
                "0.5x animator scales + HWUI renderer hint",
                tweaks
            ) { tweaks = it }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton(
                text = if (active == null) "APPLY BOOST" else "RESTORE",
                onClick = {
                    if (!shellReady) {
                        Toast.makeText(context, "Shizuku shell not ready", Toast.LENGTH_SHORT).show()
                        return@GlassButton
                    }
                    scope.launch {
                        if (active == null) {
                            container.boost.applyBoost(
                                BoostPlan(
                                    refreshRateHz = refresh,
                                    resolutionScale = scale,
                                    killBackground = killBg,
                                    blockNotifications = blockNotif,
                                    speedTweaks = tweaks
                                )
                            )
                        } else container.boost.restoreBoost()
                    }
                },
                modifier = Modifier.weight(1f)
            )
            GlassButton(
                text = "AUTO ENGINE",
                primary = false,
                onClick = {
                    com.gamershub.core.service.GamersHubCoreService.start(context)
                    Toast.makeText(
                        context, "Auto-boost engine started — game profiles applied automatically",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.weight(1f)
            )
        }

        SectionTitle("ENGINE LOG")
        GlassCard {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                items(log.takeLast(60).reversed()) { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.labelSmall,
                        color = g.textDim,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(110.dp))
    }
}
