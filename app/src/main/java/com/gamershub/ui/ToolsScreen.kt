package com.gamershub.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.core.MemoryCleaner
import com.gamershub.core.PingEngine
import com.gamershub.core.ScreenshotEngine
import com.gamershub.overlay.CrosshairOverlayService
import com.gamershub.overlay.FloatingPanelService
import com.gamershub.overlay.MappingOverlayService
import com.gamershub.overlay.MonitorOverlayService
import com.gamershub.overlay.MouseCursorService
import com.gamershub.theme.GC
import com.gamershub.theme.LocalAccent
import com.gamershub.ui.components.AIcon
import com.gamershub.ui.components.StatusPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateColorAsState

/**
 * TOOLS — the complete feature surface of the clone in one place.
 *
 * Every row is wired to a real service or a real measurement; nothing here is
 * a placeholder. Overlay rows toggle the running service, everything else opens
 * its configuration sheet.
 */
@Composable
fun ToolsScreen(
    accentIdx: Int,
    onOpenSheet: (String) -> Unit,
    onOpenGameSpace: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    var lastClean by remember { androidx.compose.runtime.mutableStateOf("") }

    val live by produceState(initialValue = mapOf<String, Boolean>()) {
        while (true) {
            value = mapOf(
                "panel" to FloatingPanelService.isRunning,
                "monitor" to MonitorOverlayService.isRunning,
                "crosshair" to CrosshairOverlayService.isRunning,
                "mapping" to MappingOverlayService.isRunning,
                "mouse" to MouseCursorService.isRunning,
                "shizuku" to com.gamershub.core.shizuku.Shell.available(),
                "overlay" to android.provider.Settings.canDrawOverlays(context),
                "mouseDevice" to MouseCursorService.hasMouse(),
                "recording" to com.gamershub.core.capture.Recorder.isRecording,
                "live" to com.gamershub.core.capture.Stream.isStreaming
            )
            delay(900)
        }
    }

    // Direct reads of the observable service state: the switch flips the moment
    // the service starts or stops (no stale "ON" labels).
    fun on(key: String): Boolean = when (key) {
        "panel" -> FloatingPanelService.isRunning
        "monitor" -> MonitorOverlayService.isRunning
        "crosshair" -> CrosshairOverlayService.isRunning
        "mapping" -> MappingOverlayService.isRunning
        "mouse" -> MouseCursorService.isRunning
        "recording" -> com.gamershub.core.capture.Recorder.isRecording
        "live" -> com.gamershub.core.capture.Stream.isStreaming
        "shizuku" -> com.gamershub.core.shizuku.Shell.connected()
        "overlay" -> android.provider.Settings.canDrawOverlays(context)
        "mouseDevice" -> MouseCursorService.hasMouse()
        else -> live[key] == true
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(GC.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(54.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    "TOOLS",
                    style = MaterialTheme.typography.displaySmall,
                    color = GC.Text,
                    fontSize = 36.sp
                )
                Text(
                    "Every GamersHub feature · live",
                    style = MaterialTheme.typography.labelMedium,
                    color = GC.TextDim,
                    fontSize = 13.sp
                )
            }
            StatusPill("● " + com.gamershub.core.shizuku.Shell.statusLabel())
        }
        Spacer(Modifier.height(14.dp))

        if (!on("overlay")) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x33E23A3A))
                    .clickable {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                    .padding(13.dp)
            ) {
                Text(
                    "Display-over-apps is off — every overlay needs it. Tap to grant.",
                    color = Color(0xFFFFB4AE),
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        SectionHead("OVERLAYS")
        if (on("panel") || on("monitor") || on("crosshair") || on("mapping") || on("mouse")) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33E23A3A))
                    .clickable {
                        FloatingPanelService.stop(context)
                        MonitorOverlayService.stop(context)
                        CrosshairOverlayService.stop(context)
                        MappingOverlayService.stop(context)
                        MouseCursorService.stop(context)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text("STOP ALL OVERLAYS", color = Color(0xFFFFB4AE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        ToolRow(
            icon = Icons.Filled.Widgets,
            title = "Floating Panel",
            subtitle = "GamersHub pop-up panel + bubble",
            active = on("panel"),
            accent = accent,
            onToggle = { if (on("panel")) FloatingPanelService.stop(context) else FloatingPanelService.start(context) },
            onConfig = { onOpenSheet("panel") }
        )
        ToolRow(
            icon = Icons.Filled.Speed,
            title = "Information Monitor",
            subtitle = "FPS · CPU · GPU · RAM · TEMP · PING",
            active = on("monitor"),
            accent = accent,
            onToggle = { MonitorOverlayService.toggle(context) },
            onConfig = { onOpenSheet("monitor") }
        )
        ToolRow(
            icon = Icons.Filled.GpsFixed,
            title = "Crosshair",
            subtitle = "7 styles · colour · opacity · rotation",
            active = on("crosshair"),
            accent = accent,
            onToggle = { if (on("crosshair")) CrosshairOverlayService.stop(context) else CrosshairOverlayService.start(context) },
            onConfig = { onOpenSheet("crosshair") }
        )
        ToolRow(
            icon = Icons.Filled.Adjust,
            title = "Mapping Editor",
            subtitle = "Macro keys injected with shell identity",
            active = on("mapping"),
            accent = accent,
            onToggle = { MappingOverlayService.toggle(context) },
            onConfig = { onOpenSheet("mapping") }
        )
        ToolRow(
            icon = Icons.Filled.TouchApp,
            title = "Mouse Cursor",
            subtitle = if (on("mouseDevice")) "Pointer · mouse connected" else "Pointer · virtual trackpad",
            active = on("mouse"),
            accent = accent,
            onToggle = { MouseCursorService.toggle(context) },
            onConfig = { onOpenSheet("mouse") }
        )

        SectionHead("STUDIO")
        ToolRow(
            icon = Icons.Filled.Videocam,
            title = if (on("recording")) "Recording…" else "Screen Recorder",
            subtitle = "MP4 · H.264 · up to 4K · mic optional",
            active = on("recording"),
            accent = accent,
            onToggle = null,
            onConfig = { onOpenSheet("record") },
            configLabel = "OPEN"
        )
        ToolRow(
            icon = Icons.Filled.Podcasts,
            title = if (on("live")) "Live now" else "Go Live",
            subtitle = "Real RTMP publish to YouTube / Twitch / any endpoint",
            active = on("live"),
            accent = accent,
            onToggle = null,
            onConfig = { onOpenSheet("live") },
            configLabel = "OPEN"
        )
        ToolRow(
            icon = Icons.Filled.AutoAwesome,
            title = "Graphics Enhancer",
            subtitle = "Render scale · Vulkan renderer · texture & shadow CVars",
            active = false,
            accent = accent,
            onToggle = null,
            onConfig = { onOpenSheet("graphics") }
        )
        ToolRow(
            icon = Icons.Filled.Smartphone,
            title = "Device Spoofing",
            subtitle = "Flagship presets · display, name, identity props",
            active = false,
            accent = accent,
            onToggle = null,
            onConfig = { onOpenSheet("spoof") }
        )

        SectionHead("NETWORK")
        ToolRow(
            icon = Icons.Filled.CenterFocusStrong,
            title = "Ping Optimizer",
            subtitle = "TCP latency · jitter · private DNS",
            active = false,
            accent = accent,
            onToggle = null,
            onConfig = { onOpenSheet("ping") }
        )

        SectionHead("PERFORMANCE")
        SectionHead("MEMORY${if (lastClean.isEmpty()) "" else " · $lastClean"}")

        ToolRow(
            icon = Icons.Filled.CleaningServices,
            title = "Clean Memory",
            subtitle = "Reclaim cached processes · trim app caches",
            active = false,
            accent = accent,
            onToggle = null,
            onConfig = {
                scope.launch {
                    val report = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MemoryCleaner.clean(context) }
                    lastClean = "${report.freedMb} MB free"
                    Toast.makeText(context, "Clean — ${report.freedMb} MB freed", Toast.LENGTH_SHORT).show()
                }
            },
            configLabel = "CLEAN"
        )
        ToolRow(
            icon = Icons.Filled.Bolt,
            title = "Boost Memory",
            subtitle = "Clean + drop page cache · instant free RAM",
            active = false,
            accent = accent,
            onToggle = null,
            onConfig = {
                scope.launch {
                    val report = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MemoryCleaner.boost(context) }
                    lastClean = "${report.freedMb} MB free"
                    Toast.makeText(context, "Boost — ${report.freedMb} MB freed", Toast.LENGTH_SHORT).show()
                }
            },
            configLabel = "BOOST"
        )
        ToolRow(
            icon = Icons.Filled.DeleteSweep,
            title = "Deep Clean",
            subtitle = "Force-stop background apps · trim caches · drop cache",
            active = false,
            accent = accent,
            onToggle = null,
            onConfig = {
                scope.launch {
                    val report = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MemoryCleaner.deepClean(context) }
                    lastClean = "${report.freedMb} MB · ${report.appsStopped} apps"
                    Toast.makeText(
                        context,
                        "Deep clean — ${report.freedMb} MB freed, ${report.appsStopped} apps stopped",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            configLabel = "DEEP"
        )
        ToolRow(
            icon = Icons.Filled.ScreenRotation,
            title = "Gyro Calibration",
            subtitle = "Bias capture · sensitivity · live readout",
            active = false,
            accent = accent,
            onToggle = null,
            onConfig = { onOpenSheet("gyro") }
        )
        ToolRow(
            icon = Icons.Filled.Tune,
            title = "Display Tuning",
            subtitle = "Resolution · DPI · refresh rate",
            active = false,
            accent = accent,
            onToggle = null,
            onConfig = { onOpenSheet("display") }
        )

        SectionHead("CAPTURE & SPACE")
        ToolRow(
            icon = Icons.Filled.Screenshot,
            title = "Screenshot",
            subtitle = ScreenshotEngine.DIR,
            active = false,
            accent = accent,
            onToggle = null,
            onConfig = {
                val shot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ScreenshotEngine.capture() }
                Toast.makeText(
                    context,
                    if (shot != null) "Saved ${shot.name}" else "Grant Shizuku to capture",
                    Toast.LENGTH_SHORT
                ).show()
                onOpenSheet("screenshot")
            },
            configLabel = "CAPTURE"
        )
        ToolRow(
            icon = Icons.Filled.Widgets,
            title = "Game Space",
            subtitle = "Landscape in-game hub",
            active = false,
            accent = accent,
            onToggle = null,
            onConfig = onOpenGameSpace,
            configLabel = "OPEN"
        )
        ToolRow(
            icon = Icons.Filled.Tune,
            title = "Presets",
            subtitle = "Imported display profiles",
            active = false,
            accent = accent,
            onToggle = null,
            onConfig = { onOpenSheet("presets") }
        )
        ToolRow(
            icon = Icons.Filled.Bolt,
            title = "Activation",
            subtitle = if (on("shizuku")) "Shizuku connected" else "Connect Shizuku for full power",
            active = on("shizuku"),
            accent = accent,
            onToggle = null,
            onConfig = { onOpenSheet("activation") },
            configLabel = "STATUS"
        )

        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun SectionHead(text: String) {
    Text(
        text,
        color = GC.TextDim,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun ToolRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    accent: Color,
    onToggle: (() -> Unit)?,
    onConfig: () -> Unit,
    configLabel: String = "SET"
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(GC.Surface)
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (active) accent.copy(alpha = 0.22f) else GC.SurfaceHi),
            contentAlignment = Alignment.Center
        ) {
            AIcon(icon, contentDescription = title, tint = if (active) accent else GC.TextSoft, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = GC.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = GC.TextDim, fontSize = 11.sp, maxLines = 1)
        }
        if (onToggle != null) {
            val tgInter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val tgPressed by tgInter.collectIsPressedAsState()
            val tgBg by androidx.compose.animation.animateColorAsState(
                if (active) accent else GC.SurfaceHi,
                androidx.compose.animation.core.tween(200), label = "toggleBg"
            )
            val tgScale by androidx.compose.animation.core.animateFloatAsState(
                if (tgPressed) 0.90f else 1f,
                androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 900f), label = "toggleScale"
            )
            Box(
                Modifier
                    .graphicsLayer { scaleX = tgScale; scaleY = tgScale }
                    .clip(RoundedCornerShape(8.dp))
                    .background(tgBg)
                    .clickable(tgInter, indication = null, onClick = onToggle)
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) {
                Text(
                    if (active) "ON" else "OFF",
                    color = if (active) GC.OnAccent else GC.TextSoft,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(GC.SurfaceHi)
                .clickable(onClick = onConfig)
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Text(configLabel, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Small helper used by the settings sheet to show the live DNS state. */
fun currentDnsLabel(): String = try {
    val mode = PingEngine.currentPrivateDns()
    when (mode) {
        "hostname" -> "PRIVATE DNS"
        "opportunistic" -> "AUTO DNS"
        else -> "SYSTEM DNS"
    }
} catch (_: Throwable) {
    "SYSTEM DNS"
}
