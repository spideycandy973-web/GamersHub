package com.gamershub.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.core.FpsMeter
import com.gamershub.core.GfxEngine
import com.gamershub.core.MemoryCleaner
import com.gamershub.core.PingEngine
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
private val AX_PANEL = Color(0xF5120C0D)
private val AX_LINE = Color(0x33FFFFFF)

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
                    ghz = String.format(Locale.US, "%.2fGHz", cpu.curGhz),
                    batt = battery.first.coerceIn(0, 100),
                    clock = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
                )
            }
            tick++
            kotlinx.coroutines.delay(900)
        }
    }
    return stats
}

/**
 * AX-MODE panel — red/black angular console: tab pills, live strip, the
 * AX-MODE band, clock + battery, a 3×3 tool grid around the GAME CORNER
 * wordmark with CPU/RAM readouts, real memory actions and a REC/LIVE footer.
 *
 * Every key performs its action immediately and writes the outcome into the
 * status line, so nothing ever "does nothing" without feedback.
 */
@Composable
fun AxPanel(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stats = rememberPanelStats()
    // The panel lives at the bottom of the screen: on a landscape phone the window is
    // short, so cap the surface at 70 % of the display and let it scroll instead of
    // clipping its own top rows.
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.70f).dp
    var showStrip by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("AX-MODE ready") }
    var busy by remember { mutableStateOf(false) }
    var refreshLabel by remember { mutableStateOf("AUTO") }
    var frameBoost by remember { mutableStateOf(false) }

    fun say(text: String) {
        status = text
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(AX_PANEL)
            .border(1.dp, Color(0x33E0231F), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 6.dp)
    ) {
        // grabber (tap to close)
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .width(56.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AX_RED)
                    .clickable(onClick = onClose)
            )
        }

        // tabs + live strip
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AXTab(Icons.Filled.Tune, "CONTROLS", active = true) {
                openSheet(context, "panel")
                say("Control hub")
            }
            Spacer(Modifier.width(6.dp))
            AXTab(Icons.Filled.Info, "MONITOR", active = showStrip) {
                showStrip = !showStrip
                say(if (showStrip) "Live monitor on" else "Live monitor hidden")
            }
            Spacer(Modifier.width(10.dp))
            if (showStrip) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xCC0A0506))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatChip("FPS", stats.fps)
                    StatChip("CPU", "${stats.cpu}%")
                    StatChip("RAM", "${stats.ram}%")
                    StatChip("TEMP", stats.temp)
                    StatChip("PING", stats.ping)
                    StatChip("CLK", stats.ghz)
                }
            } else {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    QuickDot(Icons.Filled.Bolt)
                    QuickDot(Icons.Filled.Speed)
                    QuickDot(Icons.Filled.Memory)
                    QuickDot(Icons.Filled.GpsFixed)
                }
            }
        }

        // AX-MODE band
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f).height(1.dp).background(Color(0x55E0231F)))
            Box(
                Modifier
                    .padding(horizontal = 10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AX_RED)
                    .padding(horizontal = 22.dp, vertical = 3.dp)
            ) {
                Text(
                    "AX-MODE",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
            }
            Box(Modifier.weight(1f).height(1.dp).background(Color(0x55E0231F)))
        }

        // clock · battery · status
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stats.clock,
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
            Spacer(Modifier.weight(1f))
            Text(
                status,
                color = if (busy) Color(0xFFFFB4AE) else Color(0xFFFF8A80),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // ---- tool grid ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PanelKey(Icons.Filled.Autorenew, "REFRESH", refreshLabel) {
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
                PanelKey(
                    Icons.Filled.Speed,
                    "MONITOR",
                    if (MonitorOverlayService.isRunning) "ON" else "OFF"
                ) {
                    MonitorOverlayService.toggle(context)
                    say(if (MonitorOverlayService.isRunning) "Monitor hidden" else "Monitor shown")
                }
                PanelKey(Icons.Filled.Dns, "PING", "--") {
                    scope.launch {
                        busy = true
                        val ms = withContext(Dispatchers.IO) { PingEngine.probe("1.1.1.1", 53) }
                        busy = false
                        say(if (ms >= 0) "Ping $ms ms" else "No network")
                    }
                }
            }

            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("AXERON", color = Color(0x99FFFFFF), fontSize = 7.sp, letterSpacing = 2.sp)
                Text(
                    "GAME CORNER",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                HeroMark(Modifier.size(22.dp))
                Spacer(Modifier.height(4.dp))
                BarLine("CPU", stats.cpu)
                Spacer(Modifier.height(2.dp))
                BarLine("RAM", stats.ram)
            }

            Column(Modifier.weight(1f)) {
                PanelKey(
                    Icons.Filled.GpsFixed,
                    "CROSSHAIR",
                    if (CrosshairOverlayService.isRunning) "ON" else "OFF"
                ) {
                    if (CrosshairOverlayService.isRunning) CrosshairOverlayService.stop(context)
                    else CrosshairOverlayService.start(context)
                    say(if (CrosshairOverlayService.isRunning) "Crosshair off" else "Crosshair on")
                }
                PanelKey(Icons.Filled.Bolt, "FRAME", if (frameBoost) "ON" else "OFF") {
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
                PanelKey(Icons.Filled.ScreenRotation, "GYRO", "CAL") {
                    openSheet(context, "gyro")
                    say("Opening gyro calibration")
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PanelKey(Icons.Filled.ScreenLockPortrait, "ROTATE", "AUTO") {
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
                PanelKey(Icons.Filled.NotificationsOff, "DND", "SILENT") {
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
                PanelKey(Icons.Filled.BrightnessHigh, "BRIGHT", "LOCK") {
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
            }

            // ---- memory actions (real) ----
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                MemoryKey("CLEAN", Icons.Filled.CleaningServices) {
                    scope.launch {
                        busy = true
                        val report = MemoryCleaner.clean(context)
                        busy = false
                        say("Clean · ${report.freedMb} MB freed")
                    }
                }
                Spacer(Modifier.height(4.dp))
                MemoryKey("BOOST", Icons.Filled.Bolt) {
                    scope.launch {
                        busy = true
                        val report = MemoryCleaner.boost(context)
                        busy = false
                        say("Boost · ${report.freedMb} MB freed")
                    }
                }
                Spacer(Modifier.height(4.dp))
                MemoryKey("DEEP", Icons.Filled.Memory) {
                    scope.launch {
                        busy = true
                        val report = MemoryCleaner.deepClean(context)
                        busy = false
                        say("Deep clean · ${report.freedMb} MB · ${report.appsStopped} apps")
                    }
                }
            }

            Column(Modifier.weight(1f)) {
                PanelKey(
                    Icons.Filled.Adjust,
                    "MAPPING",
                    if (MappingOverlayService.isRunning) "ON" else "OFF"
                ) {
                    MappingOverlayService.toggle(context)
                    say(if (MappingOverlayService.isRunning) "Mapping off" else "Mapping on")
                }
                PanelKey(Icons.Filled.Screenshot, "SHOT", "SAVE") {
                    scope.launch {
                        busy = true
                        val shot = withContext(Dispatchers.IO) { ScreenshotEngine.capture() }
                        busy = false
                        say(if (shot != null) "Saved ${shot.name}" else "Capture needs Shizuku")
                    }
                }
                PanelKey(
                    Icons.Filled.TouchApp,
                    "MOUSE",
                    if (MouseCursorService.isRunning) "ON" else "OFF"
                ) {
                    MouseCursorService.toggle(context)
                    say(if (MouseCursorService.isRunning) "Pointer off" else "Pointer on")
                }
            }
        }

        // ---- footer: REC · wordmark · LIVE ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                FooterKey(
                    text = if (Recorder.isRecording) "● REC" else "REC",
                    active = Recorder.isRecording
                ) {
                    openSheet(context, "record")
                    say("Recording studio")
                }
            }
            HeroMark(Modifier.size(22.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
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
}

/* ------------------------------- pieces ------------------------------- */

@Composable
private fun AXTab(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) AX_RED else Color(0x22FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        AIcon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
private fun QuickDot(icon: ImageVector) {
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color(0x22FFFFFF)),
        contentAlignment = Alignment.Center
    ) {
        AIcon(icon, contentDescription = null, tint = Color(0xCCFFFFFF), modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun PanelKey(icon: ImageVector, label: String, state: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
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
            fontSize = 8.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(state, color = AX_RED, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        AIcon(icon, contentDescription = label, tint = Color(0xFFFFB4AE), modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = Color(0xFFFFD9D6), fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BarLine(label: String, pct: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(86.dp)) {
        Text(label, color = Color(0x88FFFFFF), fontSize = 6.5.sp, modifier = Modifier.width(20.dp))
        Box(
            Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x33FFFFFF))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(pct.coerceIn(0, 100) / 100f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AX_RED)
            )
        }
        Spacer(Modifier.width(3.dp))
        Text("$pct", color = Color(0xAAFFFFFF), fontSize = 6.5.sp, modifier = Modifier.width(16.dp))
    }
}

@Composable
private fun FooterKey(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) AX_RED else Color(0x22FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp)
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
