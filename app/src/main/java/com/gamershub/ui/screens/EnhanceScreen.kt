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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamershub.di.AppContainer
import com.gamershub.ui.requireEngine
import com.gamershub.ui.components.GlassButton
import com.gamershub.ui.components.GlassCard
import com.gamershub.ui.components.GlassChip
import com.gamershub.ui.components.GlassSwitchRow
import com.gamershub.ui.components.SectionTitle
import com.gamershub.ui.theme.LocalGlass
import kotlinx.coroutines.launch

/**
 * Graphics Enhancer — real sharpness & image-quality controls.
 *
 *  - Sharpen pipeline: GPU unsharp-mask on every frame the capture / stream /
 *    frame-generation path emits (pixel-level, no fake overlays).
 *  - Force GPU rendering: the system display-pipeline switch (verified).
 *  - Vivid colours: OEM colour-mode knob when the ROM exposes it.
 *  - Sharpen game configs: AA / post-process quality overrides merged into the
 *    game unlock patches.
 */
@Composable
fun EnhanceScreen(container: AppContainer) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val level by container.enhancer.sharpenLevel.collectAsStateWithLifecycle()
    val gpu by container.enhancer.gpuRendering.collectAsStateWithLifecycle()
    val vivid by container.enhancer.vivid.collectAsStateWithLifecycle()
    val cap by container.enhancer.capability.collectAsStateWithLifecycle()
    val gameSharp by container.settings.enhanceGameSharp.collectAsStateWithLifecycle(false)
    val engineOn by container.engine.enabled.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { container.enhancer.probe() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Graphics Enhancer",
            style = MaterialTheme.typography.headlineLarge,
            color = g.text, modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            "Real sharpness & image-quality knobs — every switch is verified on " +
                    "this device before it claims to work.",
            style = MaterialTheme.typography.labelSmall, color = g.textDim
        )

        if (!engineOn) {
            Spacer(Modifier.height(10.dp))
            GlassCard {
                Text(
                    "Master engine switch is OFF",
                    style = MaterialTheme.typography.titleMedium, color = g.text
                )
                Text(
                    "Turn the engine ON (Dashboard or Settings) to use these controls.",
                    style = MaterialTheme.typography.labelSmall, color = g.textDim
                )
            }
        }

        // ------------------------------------------------ sharpen pipeline
        SectionTitle("SCREEN & CAPTURE SHARPENING — REAL DEVICE KNOBS")
        GlassCard {
            val proof by container.enhancer.sharpenProof.collectAsStateWithLifecycle()
            Text(
                "Sharpen level: ${com.gamershub.core.enhance.GraphicsEnhancer.LEVELS[level]}",
                style = MaterialTheme.typography.titleMedium, color = g.text
            )
            Text(
                "Balanced / Sharp supersample the WHOLE DISPLAY through the Lossless " +
                        "Scaling engine (the compositor downsamples → visibly crisper screen), " +
                        "drive the vendor sharpen display props this ROM exposes — each one " +
                        "read back and verified — and feed the unsharp shader into " +
                        "record / stream / frame-gen. The proof of every step is below.",
                style = MaterialTheme.typography.labelSmall, color = g.textDim,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.gamershub.core.enhance.GraphicsEnhancer.LEVELS.forEachIndexed { i, name ->
                    GlassChip(name, level == i) {
                        if (!requireEngine(container, context)) return@GlassChip
                        scope.launch {
                            container.enhancer.setSharpenLevel(i)
                            container.settings.setEnhanceLevel(i)
                        }
                    }
                }
            }
            if (proof.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                proof.forEach { line ->
                    Text(
                        "• $line",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (line.startsWith("Shizuku")) com.gamershub.ui.theme.GhColors.Amber else g.textDim
                    )
                }
            }
        }

        // ------------------------------------------------ display sharpen
        SectionTitle("DISPLAY SHARPNESS — SUPERSAMPLING")
        GlassCard {
            val scalingState by container.scaling.state.collectAsStateWithLifecycle()
            Text(
                "The real display-wide sharpening: the system renders ABOVE the " +
                        "panel resolution and the compositor supersamples down — " +
                        "full-screen anti-aliasing, every app and game gets the " +
                        "sharper '4K/8K-class' image. This is the knob you can see.",
                style = MaterialTheme.typography.labelSmall, color = g.textDim,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Off" to com.gamershub.core.scaling.LosslessScalingEngine.ScaleMode.NATIVE,
                    "1.25×" to com.gamershub.core.scaling.LosslessScalingEngine.ScaleMode.SUPER_125,
                    "1.5×" to com.gamershub.core.scaling.LosslessScalingEngine.ScaleMode.SUPER_150,
                    "2× (8K)" to com.gamershub.core.scaling.LosslessScalingEngine.ScaleMode.SUPER_200
                ).forEach { (label, mode) ->
                    GlassChip(label, scalingState.mode == mode) {
                        if (!requireEngine(container, context)) return@GlassChip
                        scope.launch {
                            val out = container.scaling.apply(mode)
                            Toast.makeText(context, out.lastOrNull() ?: "Applied", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            if (scalingState.active && scalingState.renderW > scalingState.panelW) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "LIVE: rendering ${scalingState.renderW}×${scalingState.renderH} → " +
                            "downsampled to ${scalingState.panelW}×${scalingState.panelH}",
                    style = MaterialTheme.typography.labelSmall, color = g.accent
                )
            }
        }

        // ------------------------------------------------ display knobs
        SectionTitle("DISPLAY PIPELINE")
        GlassCard {
            GlassSwitchRow(
                "Force GPU rendering",
                "Routes display composition through the GPU (the classic " +
                        "\"force GPU\" switch) — read-back verified",
                gpu
            ) { on ->
                if (!requireEngine(container, context)) return@GlassSwitchRow
                scope.launch {
                    val ok = container.enhancer.setGpuRendering(on)
                    container.settings.setEnhanceGpu(on && ok)
                    if (!ok) {
                        Toast.makeText(
                            context,
                            "This device rejected the GPU-rendering switch — reverted",
                            Toast.LENGTH_SHORT
                        ).show()
                        container.enhancer.setGpuRendering(false)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            GlassSwitchRow(
                "Vivid colours",
                "OEM colour-mode override (only when this ROM exposes it)",
                vivid
            ) { on ->
                if (!requireEngine(container, context)) return@GlassSwitchRow
                scope.launch {
                    val ok = container.enhancer.setVivid(on)
                    container.settings.setEnhanceVivid(on && ok)
                    if (!ok) {
                        Toast.makeText(
                            context,
                            "No colour-mode knob on this device",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            GlassSwitchRow(
                "Sharpen game configs",
                "Merges AA / post-process sharpening overrides into Game Unlock patches",
                gameSharp
            ) { on ->
                if (!requireEngine(container, context)) return@GlassSwitchRow
                scope.launch { container.settings.setEnhanceGameSharp(on) }
            }
        }

        // ------------------------------------------------ capability report
        SectionTitle("THIS DEVICE")
        GlassCard {
            Text(cap.detail, style = MaterialTheme.typography.labelSmall, color = g.textDim)
            if (cap.vendorKnobs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Vendor display props detected:",
                    style = MaterialTheme.typography.labelSmall, color = g.text
                )
                cap.vendorKnobs.forEach {
                    Text(
                        "· ${it.substringAfterLast('.')}",
                        style = MaterialTheme.typography.labelSmall, color = g.textDim
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            GlassButton(
                "Re-probe device",
                onClick = { scope.launch { container.enhancer.probe() } },
                primary = false
            )
        }

        Spacer(Modifier.height(110.dp))
    }
}
