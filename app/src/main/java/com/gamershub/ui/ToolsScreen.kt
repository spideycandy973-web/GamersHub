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
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                "mouseDevice" to MouseCursorService.hasMouse()
            )
            delay(900)
        }
    }

    fun on(key: String): Boolean = live[key] == true

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
                    "Every AX-MODE feature · live",
                    style = MaterialTheme.typography.labelMedium,
                    color = GC.TextDim,
                    fontSize = 13.sp
                )
            }
            StatusPill(if (on("shizuku")) "● Privileged" else "● Limited")
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
        ToolRow(
            icon = Icons.Filled.Widgets,
            title = "Floating Panel",
            subtitle = "AX-MODE pop-up panel + bubble",
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
        ToolRow(
            icon = Icons.Filled.Bolt,
            title = "Quick Boost",
            subtitle = "Kill background · drop caches · free RAM",
            active = false,
            accent = accent,
            onToggle = null,
            onConfig = {
                val freed = com.gamershub.boostNow(context)
                Toast.makeText(context, "Boosted — $freed MB freed", Toast.LENGTH_SHORT).show()
            },
            configLabel = "BOOST"
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
                val shot = ScreenshotEngine.capture()
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
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) accent else GC.SurfaceHi)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) {
                Text(
                    if (active) "ON" else "OFF",
                    color = if (active) Color.White else GC.TextSoft,
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
