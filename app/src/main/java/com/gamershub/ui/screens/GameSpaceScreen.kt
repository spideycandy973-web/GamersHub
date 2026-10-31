package com.gamershub.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamershub.core.overlay.FloatingWidgetService
import com.gamershub.di.AppContainer
import com.gamershub.ui.Routes
import com.gamershub.ui.components.GlassButton
import com.gamershub.ui.components.GlassCard
import com.gamershub.ui.components.SectionTitle
import com.gamershub.ui.theme.GhColors
import com.gamershub.ui.theme.LocalGlass

/**
 * Game Space — the ROG-style gaming overlay hub. From here the user starts
 * the floating widget and can pop the control panel open with ONE tap, so
 * the feature never depends on edge gestures alone (on some launchers the
 * system back-gesture swallows edge swipes — the widget claims that band
 * back with setSystemGestureExclusionRects, and this screen is the
 * guaranteed entry point on top of that).
 */
@Composable
fun GameSpaceScreen(container: AppContainer, onOpen: (String) -> Unit) {
    val g = LocalGlass.current
    val context = LocalContext.current

    val widgetRunning by FloatingWidgetService.isRunning.collectAsStateWithLifecycle()
    val shellReady by container.shizuku.shellReady.collectAsStateWithLifecycle()
    val engineOn by container.settings.engineEnabled.collectAsStateWithLifecycle()
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    fun overlaySettings() {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(GhColors.BgDeep)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Game Space",
            style = MaterialTheme.typography.headlineLarge,
            color = g.text, modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            "The ROG-style gaming overlay: swipe from a screen edge inward (or tap " +
                    "the bubble) to pull up the in-game control panel — boost, frame " +
                    "generation, refresh lock, recording, streaming and more, over any game.",
            style = MaterialTheme.typography.labelSmall, color = g.textDim
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill("Overlay perm", hasOverlay, Modifier.weight(1f))
            StatusPill("Widget", widgetRunning, Modifier.weight(1f))
            StatusPill("Shizuku", shellReady, Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
        if (!hasOverlay) {
            GlassCard {
                Text(
                    "Display-over-other-apps permission needed",
                    style = MaterialTheme.typography.titleMedium, color = g.text
                )
                Text(
                    "Android only lets an app draw a floating widget after you grant " +
                            "the overlay permission. Tap the button below, allow GamersHub, " +
                            "then come back.",
                    style = MaterialTheme.typography.labelSmall, color = g.textDim
                )
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    "Open overlay settings",
                    onClick = { overlaySettings() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        GlassButton(
            if (widgetRunning) "Stop Game Space widget" else "Start Game Space widget",
            onClick = {
                if (widgetRunning) {
                    FloatingWidgetService.stop(context)
                } else {
                    if (Settings.canDrawOverlays(context)) {
                        FloatingWidgetService.start(context)
                    } else {
                        overlaySettings()
                    }
                }
                hasOverlay = Settings.canDrawOverlays(context)
            },
            modifier = Modifier.fillMaxWidth(),
            primary = !widgetRunning
        )

        Spacer(Modifier.height(10.dp))
        GlassCard {
            Text(
                "Open the control panel now",
                style = MaterialTheme.typography.titleMedium, color = g.text
            )
            Text(
                "One tap pops the full Game-Space panel over anything on screen " +
                        "(tap outside it or ✕ to close). No gesture needed.",
                style = MaterialTheme.typography.labelSmall, color = g.textDim
            )
            Spacer(Modifier.height(10.dp))
            GlassButton(
                "Open control panel",
                onClick = {
                    hasOverlay = Settings.canDrawOverlays(context)
                    if (hasOverlay) {
                        if (!FloatingWidgetService.isRunning.value) {
                            FloatingWidgetService.start(context)
                        }
                        FloatingWidgetService.openPanel(context)
                    } else {
                        overlaySettings()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!engineOn) {
            Spacer(Modifier.height(10.dp))
            GlassCard {
                Text(
                    "Master switch is OFF",
                    style = MaterialTheme.typography.titleMedium, color = g.text
                )
                Text(
                    "Game Space works best with the engine armed — enable the master " +
                            "switch on Settings (or the Dashboard) so the panel's boost, " +
                            "frame generation and scaling controls take effect instantly.",
                    style = MaterialTheme.typography.labelSmall, color = g.textDim
                )
            }
        }

        SectionTitle("HOW TO REVEAL IT IN A GAME")
        GlassCard {
            GuideLine("1", "Swipe inward from the LEFT or RIGHT screen edge — the " +
                    "invisible edge strips claim that band from the system back-gesture.")
            Spacer(Modifier.height(8.dp))
            GuideLine("2", "Tap the floating bubble anywhere on screen.")
            Spacer(Modifier.height(8.dp))
            GuideLine("3", "Long-press the bubble for instant actions: boost, " +
                    "record, live, close panel — configurable in Settings → Gestures.")
            Spacer(Modifier.height(8.dp))
            GuideLine("→", "If a launcher still eats the swipe, use “Open control " +
                    "panel” above — it always works.")
        }

        SectionTitle("QUICK CONTROLS")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Render scale", onClick = { onOpen(Routes.SCALING) }, modifier = Modifier.weight(1f))
            GlassButton("Sharpening", onClick = { onOpen(Routes.ENHANCE) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Thermal", onClick = { onOpen(Routes.THERMAL) }, modifier = Modifier.weight(1f))
            GlassButton("Booster", onClick = { onOpen(Routes.BOOST) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Games", onClick = { onOpen(Routes.GAMES) }, modifier = Modifier.weight(1f))
            GlassButton("Live proof", onClick = { onOpen(Routes.PROOF) }, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusPill(label: String, on: Boolean, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    GlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (on) "●" else "○",
                style = MaterialTheme.typography.titleMedium,
                color = if (on) GhColors.Green else g.textDim
            )
            Text(
                "  $label",
                style = MaterialTheme.typography.labelSmall,
                color = if (on) g.text else g.textDim
            )
        }
    }
}

@Composable
private fun GuideLine(mark: String, text: String) {
    val g = LocalGlass.current
    Row {
        Text(
            mark,
            style = MaterialTheme.typography.titleMedium,
            color = g.accent,
            modifier = Modifier.padding(end = 10.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = g.textDim
        )
    }
}
