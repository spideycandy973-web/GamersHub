package com.gamershub.ui.screens

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamershub.di.AppContainer
import com.gamershub.ui.components.GlassCard
import com.gamershub.ui.components.LineChart
import com.gamershub.ui.components.SectionTitle
import com.gamershub.ui.theme.GhColors
import com.gamershub.ui.theme.LocalGlass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AnalyticsScreen(container: AppContainer) {
    val g = LocalGlass.current
    val history by container.analytics.history.collectAsStateWithLifecycle()
    val sessions by container.analytics.sessions.collectAsStateWithLifecycle()
    val totalStreamSec by container.settings.totalStreamSeconds.collectAsStateWithLifecycle(0)
    val totalRec by container.settings.totalRecordings.collectAsStateWithLifecycle(0)
    val stats by container.monitor.stats.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Analytics",
            style = MaterialTheme.typography.headlineLarge,
            color = g.text, modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            "Real-time thermals & performance, plus your channel counters",
            style = MaterialTheme.typography.labelSmall, color = g.textDim
        )

        // counters
        SectionTitle("ALL-TIME")
        Row {
            GlassCard(Modifier.weight(1f)) {
                Text(
                    "%dh %02dm".format(totalStreamSec / 3600, (totalStreamSec % 3600) / 60),
                    style = MaterialTheme.typography.headlineMedium, color = g.text
                )
                Text("streamed", style = MaterialTheme.typography.labelSmall, color = g.textDim)
            }
            Spacer(Modifier.width(12.dp))
            GlassCard(Modifier.weight(1f)) {
                Text(
                    "$totalRec",
                    style = MaterialTheme.typography.headlineMedium, color = g.text
                )
                Text("recordings", style = MaterialTheme.typography.labelSmall, color = g.textDim)
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Row {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (stats.gpuModel.isNotBlank()) stats.gpuModel else "GPU unknown",
                        style = MaterialTheme.typography.titleMedium, color = g.text
                    )
                    Text("renderer", style = MaterialTheme.typography.labelSmall, color = g.textDim)
                }
                Column {
                    Text(
                        "${stats.refreshRateHz} Hz",
                        style = MaterialTheme.typography.titleMedium, color = g.accent
                    )
                    Text("panel", style = MaterialTheme.typography.labelSmall, color = g.textDim)
                }
            }
        }

        // charts
        SectionTitle("FPS · LAST 10 MIN")
        GlassCard {
            val pts = history.map { if (it.fps >= 0) it.fps else 0f }
            if (pts.size > 5) {
                LineChart(pts, maxY = maxOf(120f, pts.maxOrNull() ?: 60f), color = GhColors.Green)
            } else {
                Text(
                    "Collecting samples… launch a game with Shizuku connected",
                    style = MaterialTheme.typography.labelSmall, color = g.textDim
                )
            }
        }

        SectionTitle("THERMALS · LAST 10 MIN")
        GlassCard {
            val temps = history.map { if (it.batteryTempC > 0) it.batteryTempC else 0f }
            if (temps.size > 5) {
                LineChart(temps, minY = 15f, maxY = 50f, color = GhColors.Magenta)
            } else {
                Text("Collecting samples…", style = MaterialTheme.typography.labelSmall, color = g.textDim)
            }
        }

        SectionTitle("CAPTURE SESSIONS")
        if (sessions.isEmpty()) {
            GlassCard {
                Text(
                    "No sessions yet. Record or stream and the stats land here.",
                    style = MaterialTheme.typography.labelSmall, color = g.textDim
                )
            }
        } else {
            val fmt = SimpleDateFormat("MMM d · HH:mm", Locale.US)
            sessions.takeLast(10).reversed().forEach { s ->
                GlassCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${s.mode} · ${fmt.format(Date(s.startedAt))}",
                                style = MaterialTheme.typography.titleMedium, color = g.text
                            )
                            Text(
                                "${(s.endedAt - s.startedAt) / 1000}s · avg ${"%.0f".format(s.avgFps)} fps · " +
                                        "max ${"%.0f".format(s.maxFps)} fps",
                                style = MaterialTheme.typography.labelSmall, color = g.textDim
                            )
                        }
                        Text(
                            "🔥 ${"%.0f".format(s.maxBatteryTempC)}°",
                            style = MaterialTheme.typography.labelSmall, color = g.accent
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(110.dp))
    }
}
