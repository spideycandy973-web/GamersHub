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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamershub.core.boost.ThermalProfile
import com.gamershub.di.AppContainer
import com.gamershub.ui.components.GlassCard
import com.gamershub.ui.components.LineChart
import com.gamershub.ui.components.MiniBar
import com.gamershub.ui.components.SectionTitle
import com.gamershub.ui.theme.GhColors
import com.gamershub.ui.theme.LocalGlass
import kotlinx.coroutines.launch

@Composable
fun ThermalScreen(container: AppContainer) {
    val g = LocalGlass.current
    val scope = rememberCoroutineScope()

    val active by container.thermal.activeProfile.collectAsStateWithLifecycle()
    val stats by container.monitor.stats.collectAsStateWithLifecycle()
    val history by container.analytics.history.collectAsStateWithLifecycle()
    val shellReady by container.shizuku.shellReady.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Thermal Control",
            style = MaterialTheme.typography.headlineLarge,
            color = g.text, modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            "Anti-throttling profiles with a live governor that protects hardware",
            style = MaterialTheme.typography.labelSmall, color = g.textDim
        )

        // current temps
        SectionTitle("RIGHT NOW")
        GlassCard {
            TempRow("SoC / CPU", stats.cpuTempC, g)
            TempRow("GPU", stats.gpuTempC, g)
            TempRow("Battery", stats.batteryTempC, g)
            if (stats.thermalStatus >= 0) {
                Text(
                    "System thermal status: ${stats.thermalStatus}",
                    style = MaterialTheme.typography.labelSmall, color = g.textDim,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // temp history
        if (history.size > 8) {
            SectionTitle("BATTERY TEMP · 10 MIN")
            GlassCard {
                LineChart(
                    points = history.map { if (it.batteryTempC > 0) it.batteryTempC else 0f },
                    minY = 20f, maxY = 50f, color = GhColors.Magenta
                )
            }
        }

        SectionTitle("PROFILES")
        ThermalProfile.entries.forEach { p ->
            val selected = p == active
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                highlight = selected,
                onClick = {
                    scope.launch {
                        container.thermal.setProfile(p)
                        if (shellReady) container.thermal.enforceNow()
                    }
                }
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        when (p) {
                            ThermalProfile.COOL -> "❄️"
                            ThermalProfile.BALANCED -> "🍃"
                            ThermalProfile.BEAST -> "🔥"
                            ThermalProfile.EXTREME -> "☢️"
                        },
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium, color = g.text)
                        Text(
                            when (p) {
                                ThermalProfile.COOL ->
                                    "Natural throttling + 60 Hz cap. Coolest, longest sessions."
                                ThermalProfile.BALANCED ->
                                    "Soft override (LIGHT) + 90 Hz. Everyday gaming."
                                ThermalProfile.BEAST ->
                                    "Anti-throttle override + perf mode + max refresh."
                                ThermalProfile.EXTREME ->
                                    "BEAST with 45°C governor target. Use with cooling."
                            },
                            style = MaterialTheme.typography.labelSmall, color = g.textDim
                        )
                        Text(
                            "Governor target: ${p.targetBatteryTempC}°C battery",
                            style = MaterialTheme.typography.labelSmall, color = g.accent
                        )
                    }
                    if (selected) Text("●", color = g.accent)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        GlassCard {
            Text("How it works", style = MaterialTheme.typography.titleMedium, color = g.text)
            Text(
                "• cmd thermalservice override-status tells apps the device is cool, so games " +
                        "and capture pipelines never self-limit.\n" +
                        "• The governor watches battery skin temperature and briefly eases the " +
                        "refresh-rate lock before the SoC would hard-throttle, then restores " +
                        "full power — smooth FPS without heat spikes.\n" +
                        "• Kernel-level frequency locking would require root and is intentionally " +
                        "not faked.",
                style = MaterialTheme.typography.labelSmall, color = g.textDim,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun TempRow(label: String, tempC: Float, g: com.gamershub.ui.theme.GlassThemeColors) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = g.textDim,
                modifier = Modifier.weight(1f))
            Text(
                if (tempC <= 0f) "--" else "%.1f°C".format(tempC),
                style = MaterialTheme.typography.titleMedium,
                color = g.tempColor(tempC)
            )
        }
        MiniBar(
            fraction = if (tempC <= 0f) 0f else tempC / 55f,
            color = g.tempColor(tempC)
        )
    }
}
