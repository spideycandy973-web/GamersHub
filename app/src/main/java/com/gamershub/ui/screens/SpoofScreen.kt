package com.gamershub.ui.screens

import android.widget.Toast
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamershub.core.spoof.DeviceSpoofer
import com.gamershub.core.spoof.SpoofCapability
import com.gamershub.core.spoof.SpoofPreset
import com.gamershub.di.AppContainer
import com.gamershub.ui.components.GlassButton
import com.gamershub.ui.components.GlassCard
import com.gamershub.ui.components.GlassChip
import com.gamershub.ui.components.SectionTitle
import com.gamershub.ui.theme.LocalGlass
import kotlinx.coroutines.launch

@Composable
fun SpoofScreen(container: AppContainer) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val capability by container.spoofer.capability.collectAsStateWithLifecycle()
    val applied by container.spoofer.appliedPreset.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Device Spoof",
            style = MaterialTheme.typography.headlineLarge,
            color = g.text, modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            "Make games detect a flagship gaming phone to unlock max graphics",
            style = MaterialTheme.typography.labelSmall, color = g.textDim
        )

        SectionTitle("CAPABILITY")
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (capability) {
                            SpoofCapability.FULL_ROOT -> "FULL SPOOF (root)"
                            SpoofCapability.DISPLAY_ONLY -> "DISPLAY SPOOF"
                            SpoofCapability.UNAVAILABLE -> "UNAVAILABLE"
                            SpoofCapability.UNKNOWN -> "Tap detect"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = when (capability) {
                            SpoofCapability.FULL_ROOT -> g.accent
                            SpoofCapability.DISPLAY_ONLY -> g.text
                            else -> g.textDim
                        }
                    )
                    Text(
                        when (capability) {
                            SpoofCapability.FULL_ROOT ->
                                "resetprop found — full model identity + display spoofing"
                            SpoofCapability.DISPLAY_ONLY ->
                                "Shizuku (shell) — spoofs display resolution/density/refresh, " +
                                        "which unlocks HFR modes in most games. Model-identity spoof needs root."
                            SpoofCapability.UNAVAILABLE ->
                                "Shizuku not ready — connect it from the Dashboard"
                            SpoofCapability.UNKNOWN ->
                                "Scan for Shizuku + root capabilities"
                        },
                        style = MaterialTheme.typography.labelSmall, color = g.textDim
                    )
                }
                if (capability == SpoofCapability.UNKNOWN) {
                    GlassChip("Detect", selected = true) {
                        scope.launch { container.spoofer.detect() }
                    }
                }
            }
        }

        SectionTitle("FLAGSHIP PRESETS")
        DeviceSpoofer.PRESETS.forEach { preset ->
            val isApplied = applied?.id == preset.id
            SpoofCard(
                preset = preset,
                applied = isApplied,
                onApply = {
                    scope.launch {
                        val lines = container.spoofer.apply(preset)
                        Toast.makeText(
                            context, lines.lastOrNull() ?: "Applied", Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onRemove = {
                    scope.launch {
                        container.spoofer.remove()
                        Toast.makeText(context, "Device identity restored", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        GlassCard {
            Text("Honest notes", style = MaterialTheme.typography.titleMedium, color = g.text)
            Text(
                "• Display + refresh spoofing work on every Shizuku device and are what " +
                        "unlock 90/120/144 FPS toggles in most titles.\n" +
                        "• Rewriting ro.product.* needs root (Magisk resetprop). When present, " +
                        "GamersHub uses it automatically.\n" +
                        "• Some games whitelist devices server-side — results vary by title.",
                style = MaterialTheme.typography.labelSmall, color = g.textDim,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun SpoofCard(
    preset: SpoofPreset,
    applied: Boolean,
    onApply: () -> Unit,
    onRemove: () -> Unit
) {
    val g = LocalGlass.current
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        highlight = applied
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(preset.displayName, style = MaterialTheme.typography.titleMedium, color = g.text)
                Text(
                    "${preset.displayW}×${preset.displayH} @ ${preset.maxRefreshHz}Hz · " +
                            "dpi ${preset.displayDensity} · ${preset.gpu}",
                    style = MaterialTheme.typography.labelSmall, color = g.textDim
                )
                Text(
                    "${preset.brand} · ${preset.model}",
                    style = MaterialTheme.typography.labelSmall, color = g.accent
                )
            }
            GlassChip(
                text = if (applied) "Restore" else "Apply",
                selected = applied,
                onClick = if (applied) onRemove else onApply
            )
        }
    }
}
