package com.gamershub.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamershub.BuildConfig
import com.gamershub.core.settings.GestureAction
import com.gamershub.core.settings.ThemeMode
import com.gamershub.di.AppContainer
import com.gamershub.ui.components.GlassButton
import com.gamershub.ui.components.GlassCard
import com.gamershub.ui.components.GlassChip
import com.gamershub.ui.components.GlassSwitchRow
import com.gamershub.ui.components.SectionTitle
import com.gamershub.ui.theme.LocalGlass
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(container: AppContainer) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = g.text, modifier = Modifier.padding(top = 20.dp)
        )

        // ------------------------------------------------- master switch
        SectionTitle("MASTER ENGINE SWITCH")
        GlassCard(highlight = true) {
            val engineOn by container.engine.enabled.collectAsStateWithLifecycle()
            GlassSwitchRow(
                "Engine active",
                if (engineOn) "All engines live — boost, scaling, unlock, enhancer, widget"
                else "Everything is OFF and restored — the phone is back to stock",
                engineOn
            ) { on ->
                scope.launch {
                    val log = container.engine.setEnabled(on)
                    Toast.makeText(context, log.lastOrNull() ?: "", Toast.LENGTH_LONG).show()
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Turning this OFF stops every engine immediately: display, refresh " +
                        "rate, thermal, spoofing, frame generation and game configs are " +
                        "all restored to stock.",
                style = MaterialTheme.typography.labelSmall, color = g.textDim
            )
        }

        // ------------------------------------------------------- appearance
        SectionTitle("APPEARANCE")
        GlassCard {
            val themeMode by container.settings.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val accent by container.settings.accent.collectAsStateWithLifecycle("CRIMSON")
            Text("Theme", style = MaterialTheme.typography.titleMedium, color = g.text)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    GlassChip(
                        mode.name.lowercase().replaceFirstChar { it.uppercase() },
                        themeMode == mode
                    ) { scope.launch { container.settings.setThemeMode(mode) } }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Accent", style = MaterialTheme.typography.titleMedium, color = g.text)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("CRIMSON", "GRAPHITE", "TEAL", "PLUM", "ROSE").forEach { a ->
                    GlassChip(
                        a.lowercase().replaceFirstChar { it.uppercase() },
                        accent == a
                    ) { scope.launch { container.settings.setAccent(a) } }
                }
            }
        }

        // --------------------------------------------------- widget/gestures
        SectionTitle("FLOATING WIDGET & GESTURES")
        GlassCard {
            val bubble by container.settings.overlayBubble.collectAsStateWithLifecycle(true)
            val hud by container.settings.overlayHud.collectAsStateWithLifecycle(true)
            val chatOv by container.settings.overlayChat.collectAsStateWithLifecycle(true)
            val dbl by container.settings.gestureDoubleTap.collectAsStateWithLifecycle(GestureAction.TOGGLE_RECORD)
            val lng by container.settings.gestureTripleTap.collectAsStateWithLifecycle(GestureAction.TOGGLE_BOOST)

            GlassSwitchRow("Bubble", "Draggable quick bubble", bubble) {
                scope.launch { container.settings.setOverlayBubble(it) }
            }
            Spacer(Modifier.height(8.dp))
            GlassSwitchRow("FPS HUD", "Live telemetry pinned to the corner", hud) {
                scope.launch { container.settings.setOverlayHud(it) }
            }
            Spacer(Modifier.height(8.dp))
            GlassSwitchRow("Chat overlay", "Viewer chat over the game while live", chatOv) {
                scope.launch { container.settings.setOverlayChat(it) }
            }
            Spacer(Modifier.height(12.dp))
            Text("Double-tap bubble", style = MaterialTheme.typography.labelSmall, color = g.textDim)
            Spacer(Modifier.height(6.dp))
            ActionChips(dbl) { scope.launch { container.settings.setGestureDoubleTap(it) } }
            Spacer(Modifier.height(10.dp))
            Text("Long-press bubble", style = MaterialTheme.typography.labelSmall, color = g.textDim)
            Spacer(Modifier.height(6.dp))
            ActionChips(lng) { scope.launch { container.settings.setGestureTripleTap(it) } }

            Spacer(Modifier.height(12.dp))
            if (!Settings.canDrawOverlays(context)) {
                GlassButton("Grant overlay permission", onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                })
            }
        }

        // ---------------------------------------------------------- shizuku
        SectionTitle("SHIZUKU / WIRELESS DEBUGGING")
        GlassCard {
            val status by container.shizuku.status.collectAsStateWithLifecycle()
            val detail by container.shizuku.statusDetail.collectAsStateWithLifecycle()
            Text(
                "Status: ${status.name}",
                style = MaterialTheme.typography.titleMedium, color = g.accent
            )
            Text(detail, style = MaterialTheme.typography.labelSmall, color = g.textDim)
            Text(
                "1. Install Shizuku from Play Store/GitHub.\n" +
                        "2. Android 11+: enable Wireless Debugging in Developer Options, open " +
                        "Shizuku → \"Start via wireless debugging\" and pair.\n" +
                        "3. Or connect a PC: adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh\n" +
                        "4. Root devices: Shizuku starts itself (Sui).\n" +
                        "5. Come back and Allow GamersHub.",
                style = MaterialTheme.typography.labelSmall, color = g.textDim,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip("Request permission", selected = true) {
                    container.shizuku.requestPermission()
                }
                GlassChip("Reconnect", selected = false) {
                    container.shizuku.reconnect()
                    Toast.makeText(context, "Rebinding Shizuku shell…", Toast.LENGTH_SHORT).show()
                }
                GlassChip("Open Shizuku", selected = false) {
                    val i = context.packageManager
                        .getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    if (i != null) context.startActivity(i)
                    else Toast.makeText(context, "Shizuku not installed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // ---------------------------------------------------------- misc
        SectionTitle("ENGINE")
        GlassCard {
            val autoBoost by container.settings.autoBoost.collectAsStateWithLifecycle(true)
            val keepOn by container.settings.keepScreenOn.collectAsStateWithLifecycle(true)
            val boot by container.settings.startOnBoot.collectAsStateWithLifecycle(false)
            GlassSwitchRow(
                "Auto-boost games", "Apply game profiles automatically", autoBoost
            ) { scope.launch { container.settings.setAutoBoost(it) } }
            Spacer(Modifier.height(8.dp))
            GlassSwitchRow("Keep screen on", "While boosting", keepOn) {
                scope.launch { container.settings.setKeepScreenOn(it) }
            }
            Spacer(Modifier.height(8.dp))
            GlassSwitchRow("Start on boot", "Restart engine after reboot", boot) {
                scope.launch { container.settings.setStartOnBoot(it) }
            }
        }

        Spacer(Modifier.height(16.dp))
        GlassCard {
            Text(
                "GamersHub ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleMedium, color = g.text
            )
            Text(
                "FPS boost · game unlock · lossless scaling · frame generation · " +
                        "4K capture · multi-platform live — all through Shizuku, no root.",
                style = MaterialTheme.typography.labelSmall, color = g.textDim
            )
        }

        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun ActionChips(current: GestureAction, onPick: (GestureAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassChip("None", current == GestureAction.NONE) { onPick(GestureAction.NONE) }
        GlassChip("Record", current == GestureAction.TOGGLE_RECORD) {
            onPick(GestureAction.TOGGLE_RECORD)
        }
        GlassChip("Boost", current == GestureAction.TOGGLE_BOOST) {
            onPick(GestureAction.TOGGLE_BOOST)
        }
        GlassChip("Panel", current == GestureAction.OPEN_PANEL) { onPick(GestureAction.OPEN_PANEL) }
    }
}
