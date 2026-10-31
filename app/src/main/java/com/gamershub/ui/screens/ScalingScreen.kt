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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamershub.core.scaling.LosslessScalingEngine
import com.gamershub.di.AppContainer
import com.gamershub.ui.components.GlassButton
import com.gamershub.ui.components.GlassCard
import com.gamershub.ui.components.GlassChip
import com.gamershub.ui.components.GlassSwitchRow
import com.gamershub.ui.components.SectionTitle
import com.gamershub.ui.theme.GhColors
import com.gamershub.ui.theme.LocalGlass
import kotlinx.coroutines.launch

/**
 * Lossless Scaling + Frame Generation.
 *
 * Display upscaling is done on the system display surface (Shizuku `wm size`):
 * the game renders at a smaller resolution, the hardware compositor upscales to
 * the panel in real time — pixel-perfect when the scale is an integer divisor.
 * Frame generation doubles output frames via real GPU motion interpolation in
 * the capture pipeline, and drives hardware MEMC where the SoC exposes it.
 */
@Composable
fun ScalingScreen(container: AppContainer) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val scaleState by container.scaling.state.collectAsStateWithLifecycle()
    val memc by container.frameGen.memc.collectAsStateWithLifecycle()
    val memcOn by container.frameGen.enabled.collectAsStateWithLifecycle()
    val fgDisplay by container.settings.frameGenDisplay.collectAsStateWithLifecycle(false)
    val fgCapture by container.settings.frameGenCapture.collectAsStateWithLifecycle(false)
    val shellReady by container.shizuku.shellReady.collectAsStateWithLifecycle()

    var customPct by remember { mutableIntStateOf(85) }
    var refreshPick by remember { mutableIntStateOf(0) }

    LaunchedEffect(shellReady) {
        if (shellReady) {
            container.scaling.probe()
            container.frameGen.probe()
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
            "Lossless Scaling",
            style = MaterialTheme.typography.headlineLarge,
            color = g.text, modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            "Real-time upscaling on the system display surface. The game renders " +
                    "fewer pixels, the hardware compositor scales them up — zero added lag.",
            style = MaterialTheme.typography.labelSmall, color = g.textDim
        )

        // ---------------------------------------------------- panel info
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Panel ${scaleState.panelW}×${scaleState.panelH}",
                        style = MaterialTheme.typography.titleMedium, color = g.text
                    )
                    Text(
                        if (scaleState.active)
                            "Rendering ${scaleState.renderW}×${scaleState.renderH}"
                        else "Rendering at native resolution",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (scaleState.active) g.accent else g.textDim
                    )
                }
                val integer = scaleState.renderW > 0 &&
                        scaleState.panelW % scaleState.renderW == 0 &&
                        scaleState.panelH % scaleState.renderH == 0
                if (scaleState.active) {
                    Text(
                        if (integer) "INTEGER" else "SCALED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (integer) GhColors.Green else GhColors.Amber
                    )
                }
            }
        }

        // ---------------------------------------------------- scale modes
        SectionTitle("RENDER SCALE")
        GlassCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip("Native", scaleState.mode == LosslessScalingEngine.ScaleMode.NATIVE) {
                    if (!com.gamershub.ui.requireEngine(container, context)) return@GlassChip
                    scope.launch {
                        val out = container.scaling.apply(LosslessScalingEngine.ScaleMode.NATIVE)
                        Toast.makeText(context, out.lastOrNull() ?: "Reset", Toast.LENGTH_SHORT).show()
                    }
                }
                GlassChip("¾", scaleState.mode == LosslessScalingEngine.ScaleMode.THREE_QUARTERS) {
                    if (!com.gamershub.ui.requireEngine(container, context)) return@GlassChip
                    scope.launch {
                        val out = container.scaling.apply(
                            LosslessScalingEngine.ScaleMode.THREE_QUARTERS, refreshHz = refreshPick
                        )
                        Toast.makeText(context, out.firstOrNull() ?: "Applied", Toast.LENGTH_SHORT).show()
                    }
                }
                GlassChip("⅔", scaleState.mode == LosslessScalingEngine.ScaleMode.TWO_THIRDS) {
                    if (!com.gamershub.ui.requireEngine(container, context)) return@GlassChip
                    scope.launch {
                        val out = container.scaling.apply(
                            LosslessScalingEngine.ScaleMode.TWO_THIRDS, refreshHz = refreshPick
                        )
                        Toast.makeText(context, out.firstOrNull() ?: "Applied", Toast.LENGTH_SHORT).show()
                    }
                }
                GlassChip("½", scaleState.mode == LosslessScalingEngine.ScaleMode.HALF) {
                    if (!com.gamershub.ui.requireEngine(container, context)) return@GlassChip
                    scope.launch {
                        val out = container.scaling.apply(
                            LosslessScalingEngine.ScaleMode.HALF, refreshHz = refreshPick
                        )
                        Toast.makeText(context, out.firstOrNull() ?: "Applied", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Quality — supersample down (crisper '4K/8K class' image):",
                style = MaterialTheme.typography.labelSmall, color = g.textDim
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip("1.25×", scaleState.mode == LosslessScalingEngine.ScaleMode.SUPER_125) {
                    if (!com.gamershub.ui.requireEngine(container, context)) return@GlassChip
                    scope.launch {
                        val out = container.scaling.apply(
                            LosslessScalingEngine.ScaleMode.SUPER_125, refreshHz = refreshPick
                        )
                        Toast.makeText(context, out.lastOrNull() ?: "Applied", Toast.LENGTH_LONG).show()
                    }
                }
                GlassChip("1.5×", scaleState.mode == LosslessScalingEngine.ScaleMode.SUPER_150) {
                    if (!com.gamershub.ui.requireEngine(container, context)) return@GlassChip
                    scope.launch {
                        val out = container.scaling.apply(
                            LosslessScalingEngine.ScaleMode.SUPER_150, refreshHz = refreshPick
                        )
                        Toast.makeText(context, out.lastOrNull() ?: "Applied", Toast.LENGTH_LONG).show()
                    }
                }
                GlassChip("2× (8K)", scaleState.mode == LosslessScalingEngine.ScaleMode.SUPER_200) {
                    if (!com.gamershub.ui.requireEngine(container, context)) return@GlassChip
                    scope.launch {
                        val out = container.scaling.apply(
                            LosslessScalingEngine.ScaleMode.SUPER_200, refreshHz = refreshPick
                        )
                        Toast.makeText(context, out.lastOrNull() ?: "Applied", Toast.LENGTH_LONG).show()
                    }
                }
            }
            Text(
                "Above native: renders sharper than the panel, downsamples in hardware — " +
                        "real full-screen anti-aliasing. Costs GPU power.",
                style = MaterialTheme.typography.labelSmall, color = g.textDim,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Custom: $customPct%  (40–200 · under 100 = FPS, over 100 = quality)",
                style = MaterialTheme.typography.titleMedium, color = g.text
            )
            Slider(
                value = customPct.toFloat(),
                onValueChange = { customPct = it.toInt() },
                valueRange = 40f..200f,
                colors = SliderDefaults.colors(
                    thumbColor = g.accent, activeTrackColor = g.accent, inactiveTrackColor = g.stroke
                )
            )
            GlassButton("Apply custom scale", primary = false, onClick = {
                if (!com.gamershub.ui.requireEngine(container, context)) return@GlassButton
                scope.launch {
                    val out = container.scaling.apply(
                        LosslessScalingEngine.ScaleMode.CUSTOM, customPct, refreshHz = refreshPick
                    )
                    Toast.makeText(context, out.firstOrNull() ?: "Applied", Toast.LENGTH_SHORT).show()
                }
            })
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60, 90, 120, 144).forEach { hz ->
                    GlassChip("$hz Hz", refreshPick == hz) {
                        refreshPick = if (refreshPick == hz) 0 else hz
                    }
                }
            }
            Text(
                "Refresh lock (0 = keep current). Applied together with the scale.",
                style = MaterialTheme.typography.labelSmall, color = g.textDim
            )
            Spacer(Modifier.height(10.dp))
            GlassButton("Restore display", onClick = {
                scope.launch {
                    val out = container.scaling.restore()
                    Toast.makeText(context, out.lastOrNull() ?: "Restored", Toast.LENGTH_SHORT).show()
                }
            }, modifier = Modifier.fillMaxWidth())
        }

        // ---------------------------------------------------- frame gen
        SectionTitle("FRAME GENERATION")
        GlassCard {
            Text(
                memc.detail,
                style = MaterialTheme.typography.labelSmall,
                color = if (memc.available) GhColors.Green else g.textDim
            )
            Spacer(Modifier.height(10.dp))
            GlassSwitchRow(
                "Hardware MEMC",
                "Drives the display's motion-interpolation hardware (when present)",
                memcOn
            ) { on ->
                if (!com.gamershub.ui.requireEngine(container, context)) return@GlassSwitchRow
                scope.launch {
                    val out = container.frameGen.setEnabled(on)
                    Toast.makeText(context, out.lastOrNull() ?: "Done", Toast.LENGTH_SHORT).show()
                }
            }
            Spacer(Modifier.height(8.dp))
            GlassSwitchRow(
                "Display frame pacing",
                "Stops the panel duplicating frames — GameMode vote + refresh lock",
                fgDisplay
            ) { on ->
                if (!com.gamershub.ui.requireEngine(container, context)) return@GlassSwitchRow
                scope.launch {
                    container.settings.setFrameGenDisplay(on)
                    if (on) {
                        val out = container.frameGen.applyPacing(120)
                        Toast.makeText(context, out.lastOrNull() ?: "Done", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            GlassSwitchRow(
                "Capture frame generation",
                "Real motion-interpolated frames at 2× rate in recordings & streams",
                fgCapture
            ) { on ->
                scope.launch {
                    container.settings.setFrameGenCapture(on)
                    container.settings.setRecFrameGen(on)
                }
            }
            Spacer(Modifier.height(10.dp))
            GlassCard(corner = 16.dp) {
                Text(
                    "Capture FrameGen runs a GPU block-matching motion estimator and " +
                            "synthesises genuine mid-frames between every captured pair — the " +
                            "output video is 2× its real frame rate with actual new content, " +
                            "not duplicated frames. If a GPU can't run it, capture silently " +
                            "falls back to zero-copy and nothing breaks.",
                    style = MaterialTheme.typography.labelSmall, color = g.textDim
                )
            }
        }

        Spacer(Modifier.height(110.dp))
    }
}
