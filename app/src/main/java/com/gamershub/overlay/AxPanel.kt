package com.gamershub.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.core.FpsMeter
import com.gamershub.core.GfxEngine
import com.gamershub.core.MemoryCleaner
import com.gamershub.core.ScreenshotEngine
import com.gamershub.core.capture.Recorder
import com.gamershub.core.capture.Stream
import com.gamershub.core.shizuku.Shell
import com.gamershub.data.DeviceStats
import com.gamershub.ui.components.AIcon
import com.gamershub.ui.components.HeroMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AX_RED = Color(0xFFE0231F)
private val AX_DEEP = Color(0xF5120C0D)
private val AX_ARM = Color(0xFF2A1416)
private val AX_EDGE = Color(0x66E0231F)

/** One live sample of everything the panel shows. */
private data class PanelStats(
    val fps: String = "--",
    val cpu: Int = 0,
    val ram: Int = 0,
    val temp: String = "--",
    val ping: String = "--",
    val ghz: String = "--",
    val batt: Int = 0,
    val clock: String = "--:--"
)

@Composable
private fun rememberPanelStats(): PanelStats {
    val context = LocalContext.current
    val stats by produceState(initialValue = PanelStats()) {
        var tick = 0
        var lastFrames: Long? = null
        var lastStamp = 0L
        while (true) {
            runCatching {
                val cpu = DeviceStats.cpu()
                val mem = DeviceStats.mem(context)
                val battery = DeviceStats.battery(context)
                val sampled = if (tick % 5 == 0) DeviceStats.pingMs() else -2
                val pingText = when {
                    sampled >= 0 -> "$sampled ms"
                    sampled == -2 -> value.ping
                    else -> "--"
                }
                val pkg = FpsMeter.foregroundPackage(context)
                val frames = pkg?.let { FpsMeter.totalFrames(it) }
                val now = System.currentTimeMillis()
                var fps = value.fps
                if (frames != null && lastFrames != null && lastStamp > 0) {
                    val seconds = (now - lastStamp) / 1000.0
                    if (seconds > 0.2) {
                        val measured = ((frames - (lastFrames ?: 0L)) / seconds).toInt()
                        if (measured in 0..240) fps = measured.toString()
                    }
                }
                if (frames != null) {
                    lastFrames = frames
                    lastStamp = now
                }
                value = PanelStats(
                    fps = fps,
                    cpu = cpu.pct.coerceIn(0, 100),
                    ram = mem.pct.coerceIn(0, 100),
                    temp = if (battery.second > 0) "${battery.second.toInt()}°C" else "--",
                    ping = pingText,
                    ghz = String.format(Locale.US, "%.2f", cpu.curGhz),
                    batt = battery.first.coerceIn(0, 100),
                    clock = SimpleDateFormat("hh:mm", Locale.US).format(Date())
                )
            }
            tick++
            kotlinx.coroutines.delay(900)
        }
    }
    return stats
}

/**
 * AX-MODE panel — laid out like the reference console:
 *
 *   ┌ chevron handle ─────────────────────────────────────────┐
 *   │  Refresh Rate   Information Monitor   DNS Tuner          │
 *   │ ══════════════  A X - M O D E  ═════════════════════════ │
 *   │  PM 07:29  ▮▮▮▮▮▮▯▯▯▯ 12%   · 09:07 ·   status          │
 *   │  CPU ▰▰▰     AXERON / GAME CORNER      ▰▰▰ RAM           │
 *   │  Crosshair   Quick Boost   Gyro Calibration              │
 *   │  Rotation Lock  Do Not Disturb  Lock Brightness          │
 *   │  Touch Enhancer  Screenshot  Show Taps                   │
 *   │  CLEAN  BOOST  DEEP                                      │
 *   │  REC                                  LIVE               │
 *   └─────────────────────────────────────────────────────────┘
 *
 * Every tile performs a real action and reports the outcome in the status
 * slot, so no tap is ever silent.
 */
@Composable
fun AxPanel(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stats = rememberPanelStats()
    // Bottom-anchored overlay: cap the surface so a landscape screen can never
    // clip it, and let it scroll instead of overlapping its own rows.
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.78f).dp

    var status by remember { mutableStateOf("AX-MODE ready") }
    var busy by remember { mutableStateOf(false) }
    var refreshLabel by remember { mutableStateOf("AUTO") }
    var frameBoost by remember { mutableStateOf(false) }
    val startedAt = remember { System.currentTimeMillis() }

    fun say(text: String) {
        status = text
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun memory(label: String, block: suspend () -> MemoryCleaner.Report) {
        scope.launch {
            busy = true
            val report = block()
            busy = false
            say("$label · ${report.freedMb} MB freed")
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(AX_DEEP)
            .border(1.dp, AX_EDGE, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 6.dp)
    ) {
        // ---- chevron handle (tap to close) ----
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .width(54.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AX_RED)
                    .clickable(onClick = onClose)
            )
        }

        // ---- row 1: three quick tiles ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TopTile(Icons.Filled.Autorenew, "Refresh\nRate", refreshLabel) {
                refreshLabel = when (refreshLabel) {
                    "AUTO" -> "60Hz"
                    "60Hz" -> "90Hz"
                    "90Hz" -> "120Hz"
                    "120Hz" -> "144Hz"
                    else -> "AUTO"
                }
                scope.launch {
                    busy = true
                    val hz = when (refreshLabel) {
                        "60Hz" -> 60
                        "90Hz" -> 90
                        "120Hz" -> 120
                        "144Hz" -> 144
                        else -> FpsMeter.refreshRate(context)
                    }
                    val ok = withContext(Dispatchers.IO) { GfxEngine.setRefreshRate(hz) }
                    busy = false
                    say(if (ok) "Refresh → $hz Hz" else "Refresh needs Shizuku")
                }
            }
            TopTile(
                Icons.Filled.Info,
                "Information\nMonitor",
                if (MonitorOverlayService.isRunning) "ON" else "OFF"
            ) {
                MonitorOverlayService.toggle(context)
                say(if (MonitorOverlayService.isRunning) "Monitor hidden" else "Monitor shown")
            }
            TopTile(Icons.Filled.Dns, "DNS\nTuner", "--") {
                openSheet(context, "ping")
                say("Opening DNS / ping tuner")
            }
        }

        // ---- AX-MODE band ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(Color(0x55E0231F))
            )
            Box(
                Modifier
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(AX_RED)
                    .padding(horizontal = 26.dp, vertical = 4.dp)
            ) {
                Text(
                    "AX-MODE",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 5.sp
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(Color(0x55E0231F))
            )
        }

        // ---- clock · battery · session · status ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "PM " + stats.clock,
                color = Color(0xCCFFFFFF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                repeat(10) { index ->
                    Box(
                        Modifier
                            .width(6.dp)
                            .height(9.dp)
                            .background(if (index < stats.batt / 10) Color(0xFFE8E3DD) else Color(0x33FFFFFF))
                    )
                }
            }
            Spacer(Modifier.width(5.dp))
            Text("${stats.batt}%", color = Color(0xCCFFFFFF), fontSize = 10.sp)
            Spacer(Modifier.width(10.dp))
            val minutes = ((System.currentTimeMillis() - startedAt) / 60000).toInt()
            Text(
                String.format(Locale.US, "%02d:%02d", minutes, ((System.currentTimeMillis() - startedAt) / 1000 % 60).toInt()),
                color = Color(0x99FFFFFF),
                fontSize = 10.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                status,
                color = if (busy) Color(0xFFFFB4AE) else Color(0xFFFF8A80),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // live strip (tap the band to hide/show is not needed — it is always live)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xCC0A0506))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatChip("FPS", stats.fps)
            StatChip("CPU", "${stats.cpu}%")
            StatChip("RAM", "${stats.ram}%")
            StatChip("TEMP", stats.temp)
            StatChip("PING", stats.ping)
            StatChip("GHz", stats.ghz)
        }

        // ---- wordmark block with CPU / RAM chevron gauges ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChevronGauge("CPU", stats.cpu, flip = false)
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("AXERON", color = Color(0x99FFFFFF), fontSize = 7.sp, letterSpacing = 3.sp)
                Text(
                    "GAME CORNER",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundKey(Icons.Filled.Home, "Home") {
                        openSheet(context, "panel")
                        say("Control hub")
                    }
                    Spacer(Modifier.width(14.dp))
                    HeroMark(Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    RoundKey(Icons.Filled.Settings, "Settings") {
                        openSheet(context, "settings")
                        say("Settings")
                    }
                }
            }
            ChevronGauge("RAM", stats.ram, flip = true)
        }

        // ---- 3 × 3 grid ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            Column(Modifier.weight(1f)) {
                Tile(
                    Icons.Filled.GpsFixed,
                    "Crosshair",
                    if (CrosshairOverlayService.isRunning) "ON" else "OFF"
                ) {
                    if (CrosshairOverlayService.isRunning) CrosshairOverlayService.stop(context)
                    else CrosshairOverlayService.start(context)
                    say(if (CrosshairOverlayService.isRunning) "Crosshair off" else "Crosshair on")
                }
                Tile(Icons.Filled.ScreenLockPortrait, "Rotation\nLock", "AUTO") {
                    scope.launch {
                        busy = true
                        val auto = withContext(Dispatchers.IO) {
                            Settings.System.getInt(
                                context.contentResolver,
                                Settings.System.ACCELEROMETER_ROTATION,
                                1
                            ) == 1
                        }
                        val ok = withContext(Dispatchers.IO) {
                            Shell.ok("settings put system accelerometer_rotation ${if (auto) 0 else 1}")
                        }
                        busy = false
                        say(if (ok) "Rotation ${if (auto) "locked" else "auto"}" else "Needs Shizuku")
                    }
                }
                Tile(Icons.Filled.TouchApp, "Touch\nEnhancer", if (MappingOverlayService.isRunning) "ON" else "OFF") {
                    MappingOverlayService.toggle(context)
                    say(if (MappingOverlayService.isRunning) "Touch mapping off" else "Touch mapping on")
                }
            }
            Column(Modifier.weight(1f)) {
                Tile(Icons.Filled.Bolt, "Quick\nBoost", "FREE") {
                    memory("Boost") { MemoryCleaner.boost(context) }
                }
                Tile(Icons.Filled.NotificationsOff, "Do Not\nDisturb", "SILENT") {
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                        as android.app.NotificationManager
                    val ok = try {
                        val filter = if (manager.currentInterruptionFilter ==
                            android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                        ) android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        else android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                        manager.setInterruptionFilter(filter)
                        true
                    } catch (_: Throwable) {
                        false
                    }
                    say(if (ok) "Do not disturb toggled" else "Grant DND access")
                }
                Tile(Icons.Filled.Screenshot, "Screenshot", "SAVE") {
                    scope.launch {
                        busy = true
                        val shot = withContext(Dispatchers.IO) { ScreenshotEngine.capture() }
                        busy = false
                        say(if (shot != null) "Saved ${shot.name}" else "Capture needs Shizuku")
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Tile(Icons.Filled.ScreenRotation, "Gyro\nCalibration", "CAL") {
                    openSheet(context, "gyro")
                    say("Opening gyro calibration")
                }
                Tile(Icons.Filled.BrightnessHigh, "Lock\nBrightness", "LOCK") {
                    scope.launch {
                        busy = true
                        val now = withContext(Dispatchers.IO) {
                            try {
                                Settings.System.getInt(
                                    context.contentResolver,
                                    Settings.System.SCREEN_BRIGHTNESS
                                )
                            } catch (_: Throwable) {
                                128
                            }
                        }
                        val next = when {
                            now < 90 -> 128
                            now < 180 -> 255
                            else -> 40
                        }
                        val ok = withContext(Dispatchers.IO) {
                            Shell.ok("settings put system screen_brightness_mode 0") &&
                                Shell.ok("settings put system screen_brightness $next")
                        }
                        busy = false
                        say(if (ok) "Brightness ${next * 100 / 255}%" else "Needs Shizuku")
                    }
                }
                Tile(Icons.Filled.Adjust, "Show\nTaps", "TOUCH") {
                    scope.launch {
                        busy = true
                        val on = withContext(Dispatchers.IO) { Shell.ok("settings put system show_touches 1") }
                        busy = false
                        say(if (on) "Tap-visible pointers on" else "Needs Shizuku")
                    }
                }
            }
        }

        // ---- memory actions ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemoryKey("CLEAN", Icons.Filled.CleaningServices) {
                memory("Clean") { MemoryCleaner.clean(context) }
            }
            MemoryKey("BOOST", Icons.Filled.Bolt) {
                memory("Boost") { MemoryCleaner.boost(context) }
            }
            MemoryKey("DEEP CLEAN", Icons.Filled.Memory) {
                scope.launch {
                    busy = true
                    val report = MemoryCleaner.deepClean(context)
                    busy = false
                    say("Deep clean · ${report.freedMb} MB · ${report.appsStopped} apps")
                }
            }
        }

        // ---- footer: REC · frame boost · LIVE ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FooterKey(
                text = if (Recorder.isRecording) "● REC" else "REC",
                active = Recorder.isRecording
            ) {
                openSheet(context, "record")
                say("Recording studio")
            }
            Spacer(Modifier.weight(1f))
            FooterKey(
                text = if (frameBoost) "FRAME ●" else "FRAME",
                active = frameBoost
            ) {
                scope.launch {
                    busy = true
                    val target = !frameBoost
                    val ok = withContext(Dispatchers.IO) { GfxEngine.setFrameBoost(target) }
                    if (ok) frameBoost = target
                    busy = false
                    say(
                        if (ok) "Frame boost ${if (target) "on" else "off"}"
                        else "Frame boost needs Shizuku"
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            FooterKey(
                text = if (Stream.isStreaming) "● LIVE" else "LIVE",
                active = Stream.isStreaming
            ) {
                openSheet(context, "live")
                say("Live studio")
            }
        }
    }
}

/* ------------------------------- pieces ------------------------------- */

@Composable
private fun TopTile(icon: ImageVector, label: String, state: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(50))
                .background(AX_ARM)
                .border(1.dp, AX_EDGE, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            AIcon(icon, contentDescription = label, tint = AX_RED, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = Color(0xFFEDE7E3),
            fontSize = 9.sp,
            lineHeight = 10.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2
        )
        Text(state, color = AX_RED, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun Tile(icon: ImageVector, label: String, state: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp)
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x1FFFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            AIcon(icon, contentDescription = label, tint = Color(0xFFF2F2F2), modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = Color(0xFFEDE7E3),
            fontSize = 9.sp,
            lineHeight = 10.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2
        )
        Text(state, color = AX_RED, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun RoundKey(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0x22FFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AIcon(icon, contentDescription = label, tint = Color(0xCCFFFFFF), modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun ChevronGauge(label: String, pct: Int, flip: Boolean) {
    val filled = when {
        pct >= 75 -> 3
        pct >= 45 -> 2
        pct >= 20 -> 1
        else -> 0
    }
    Column(horizontalAlignment = if (flip) Alignment.End else Alignment.Start) {
        Text(
            label,
            color = AX_RED,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(2.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = if (flip) Alignment.End else Alignment.Start
        ) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .width((30 - index * 7).dp)
                        .height(6.dp)
                        .background(if (index < filled) AX_RED else Color(0x33FFFFFF))
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text("$pct%", color = Color(0xAAFFFFFF), fontSize = 8.sp)
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(2.dp))
        Text(label, color = Color(0x88FFFFFF), fontSize = 7.sp)
    }
}

@Composable
private fun MemoryKey(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0x26E0231F))
            .border(1.dp, Color(0x55E0231F), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        AIcon(icon, contentDescription = label, tint = Color(0xFFFFB4AE), modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = Color(0xFFFFD9D6), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FooterKey(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) AX_RED else Color(0x22FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

/** Opens a tool sheet in the main app from the overlay. */
private fun openSheet(context: Context, key: String) {
    runCatching {
        context.startActivity(
            Intent(context, com.gamershub.MainActivity::class.java)
                .putExtra(com.gamershub.MainActivity.EXTRA_OPEN, key + "#" + System.currentTimeMillis())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }
}
