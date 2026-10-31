package com.gamershub.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamershub.core.record.RecorderConfig
import com.gamershub.core.record.RecorderController
import com.gamershub.di.AppContainer
import com.gamershub.ui.components.GlassButton
import com.gamershub.ui.components.GlassCard
import com.gamershub.ui.components.GlassChip
import com.gamershub.ui.components.GlassSwitchRow
import com.gamershub.ui.components.SectionTitle
import com.gamershub.ui.theme.GhColors
import com.gamershub.ui.theme.LocalGlass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RecorderScreen(container: AppContainer) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val res by container.settings.recResolution.collectAsStateWithLifecycle("1080p")
    val fps by container.settings.recFps.collectAsStateWithLifecycle(60)
    val codec by container.settings.recCodec.collectAsStateWithLifecycle("H264")
    val bitrate by container.settings.recBitrateMbps.collectAsStateWithLifecycle(0)
    val mic by container.settings.recMic.collectAsStateWithLifecycle(false)
    val state by container.recorder.state.collectAsStateWithLifecycle()

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(context, "Mic denied", Toast.LENGTH_SHORT).show()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Screen Recorder",
            style = MaterialTheme.typography.headlineLarge,
            color = g.text, modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            "Zero-copy hardware encode — the display renders straight into the codec",
            style = MaterialTheme.typography.labelSmall, color = g.textDim
        )

        // ------------------------------------------------- active session
        val activeState = state as? RecorderController.State.Active
        if (activeState != null) {
            Spacer(Modifier.height(12.dp))
            GlassCard(highlight = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⏺", style = MaterialTheme.typography.headlineMedium, color = GhColors.Red)
                    Spacer(Modifier.width(10.dp))
                    val elapsed = produceState(0L, activeState.sinceMs) {
                        while (true) {
                            value = (System.currentTimeMillis() - activeState.sinceMs) / 1000
                            delay(1000)
                        }
                    }.value
                    Column(Modifier.weight(1f)) {
                        val mm = elapsed / 60
                        val ss = elapsed % 60
                        Text(
                            "${activeState.mode} · $mm:${"%02d".format(ss)}",
                            style = MaterialTheme.typography.titleMedium, color = g.text
                        )
                        if (activeState.streamTargets.isNotEmpty()) {
                            Text(
                                "Targets: ${activeState.streamTargets.joinToString()}",
                                style = MaterialTheme.typography.labelSmall, color = g.textDim
                            )
                        }
                    }
                    GlassButton("STOP", primary = false, onClick = { container.recorder.stop() })
                }
            }
        }

        // ------------------------------------------------- config
        SectionTitle("QUALITY")
        GlassCard {
            Text("Resolution", style = MaterialTheme.typography.titleMedium, color = g.text)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("720p", "1080p", "1440p", "2160p").forEach { r ->
                    GlassChip(if (r == "2160p") "4K" else r, res == r) {
                        scope.launch { container.settings.setRecResolution(r) }
                    }
                }
            }
            Text(
                "4K requires the panel/encoder to support it — otherwise the nearest supported size is used.",
                style = MaterialTheme.typography.labelSmall, color = g.textDim,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(14.dp))
            Text("Frame rate", style = MaterialTheme.typography.titleMedium, color = g.text)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 90, 120).forEach { f ->
                    GlassChip("$f", fps == f) {
                        scope.launch { container.settings.setRecFps(f) }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("Codec", style = MaterialTheme.typography.titleMedium, color = g.text)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("H264", "HEVC").forEach { c ->
                    GlassChip(c, codec == c) {
                        scope.launch { container.settings.setRecCodec(c) }
                    }
                }
            }
            Text(
                "HEVC ~25% smaller files; streaming always uses H.264.",
                style = MaterialTheme.typography.labelSmall, color = g.textDim
            )

            Spacer(Modifier.height(14.dp))
            Text(
                if (bitrate == 0) "Bitrate: AUTO" else "Bitrate: ${bitrate} Mbps",
                style = MaterialTheme.typography.titleMedium, color = g.text
            )
            Slider(
                value = bitrate.toFloat(),
                onValueChange = { scope.launch { container.settings.setRecBitrateMbps(it.toInt()) } },
                valueRange = 0f..80f,
                colors = SliderDefaults.colors(
                    thumbColor = g.accent, activeTrackColor = g.accent, inactiveTrackColor = g.stroke
                )
            )

            GlassSwitchRow(
                "Microphone audio", "Record commentary with the game (off by default)",
                mic
            ) { on ->
                scope.launch { container.settings.setRecMic(on) }
                if (on) micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            Spacer(Modifier.height(8.dp))
            val fg by container.settings.recFrameGen.collectAsStateWithLifecycle(false)
            GlassSwitchRow(
                "Frame generation", "Real GPU-interpolated frames at 2× output rate",
                fg
            ) { on -> scope.launch { container.settings.setRecFrameGen(on) } }
        }

        Spacer(Modifier.height(16.dp))
        GlassButton(
            text = if (activeState == null) "⏺  START RECORDING" else "■  STOP",
            onClick = {
                if (activeState == null && !com.gamershub.ui.requireEngine(container, context)) {
                    return@GlassButton
                }
                scope.launch {
                    if (activeState == null) {
                        container.recorder.requestStart(RecorderConfig.Mode.RECORD)
                    } else container.recorder.stop()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))
        Text(
            "Tip: double-tap the floating bubble in-game to start/stop recording instantly.",
            style = MaterialTheme.typography.labelSmall, color = g.textDim
        )
        Spacer(Modifier.height(110.dp))
    }
}
