package com.gamershub.overlay

import android.content.Context
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.core.DeviceSpoofer
import com.gamershub.core.FpsMeter
import com.gamershub.core.GfxEngine
import com.gamershub.core.GyroConfig
import com.gamershub.core.GyroEngine
import com.gamershub.core.MemoryCleaner
import com.gamershub.core.PingEngine
import com.gamershub.core.PingResult
import com.gamershub.core.ScreenshotEngine
import com.gamershub.core.capture.ProjectionActivity
import com.gamershub.core.capture.Recorder
import com.gamershub.core.capture.RecorderService
import com.gamershub.core.capture.Stream
import com.gamershub.core.capture.StreamService
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

/* ------------------------------------------------------------------ */
/* palette                                                             */
/* ------------------------------------------------------------------ */

private val AX_RED = Color(0xFFFF2C5E)
private val AX_DEEP = Color(0xF70A0C15)
private val AX_ARM = Color(0xFF1A1F32)
private val AX_EDGE = Color(0x66FF2C5E)
private val AX_TXT = Color(0xFFEDEFF7)
private val AX_DIM = Color(0x99AEB4CC)

private const val PREFS = "gamershub"

/* ------------------------------------------------------------------ */
/* state                                                               */
/* ------------------------------------------------------------------ */

private enum class AxPage(val title: String) {
    HOME("AX-MODE"),
    MENU("ALL FEATURES"),
    GFX("GRAPHICS UNLOCK"),
    DISPLAY("DISPLAY"),
    PERF("MEMORY & PERFORMANCE"),
    NET("NETWORK / DNS"),
    SPOOF("DEVICE SPOOF"),
    REC("RECORD & LIVE"),
    OVERLAY("OVERLAYS"),
    GYRO("GYRO CALIBRATION"),
    INFO("LIVE MONITOR")
}

private class AxState {
    var page by mutableStateOf(AxPage.HOME)
    var status by mutableStateOf("AX-MODE ready")
    var busy by mutableStateOf(false)

    var texture by mutableStateOf("HIGH")
    var shadow by mutableStateOf("HIGH")
    var effects by mutableStateOf("HIGH")
    var antiAliasing by mutableStateOf("HIGH")
    var sharpness by mutableStateOf(60)
    var resolution by mutableStateOf(100)
    var fpsLimit by mutableStateOf(0)

    var vulkan by mutableStateOf(false)
    var noVsync by mutableStateOf(false)
    var gameDriver by mutableStateOf(false)
    var frameBoost by mutableStateOf(false)

    var refresh by mutableStateOf("AUTO")
    var renderScale by mutableStateOf(100)

    var showTaps by mutableStateOf(false)
    var spoofIndex by mutableStateOf(0)
    var bitrateMbps by mutableStateOf(12)
    var netResults by mutableStateOf(emptyList<PingResult>())
    var gyro by mutableStateOf(GyroConfig())
}

@Suppress("unused")
private class AxApi(
    val context: Context,
    val state: AxState,
    val say: (String) -> Unit,
    val work: (suspend () -> Unit) -> Unit,
    val nav: (AxPage) -> Unit,
    val close: () -> Unit
)

/* ------------------------------------------------------------------ */
/* live stats                                                          */
/* ------------------------------------------------------------------ */

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

/** Polls a value that needs a shell round-trip, off the composition thread. */
@Composable
private fun <T> rememberLive(initial: T, periodMs: Long = 3000, block: () -> T): T {
    val state by produceState(initialValue = initial) {
        while (true) {
            value = runCatching { block() }.getOrDefault(value)
            kotlinx.coroutines.delay(periodMs)
        }
    }
    return state
}

/* ------------------------------------------------------------------ */
/* panel                                                               */
/* ------------------------------------------------------------------ */

@Composable
fun AxPanel(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stats = rememberPanelStats()
    val st = remember { AxState() }
    val startedAt = remember { System.currentTimeMillis() }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp

    remember {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        st.texture = prefs.getString("ax_texture", "HIGH") ?: "HIGH"
        st.shadow = prefs.getString("ax_shadow", "HIGH") ?: "HIGH"
        st.effects = prefs.getString("ax_effects", "HIGH") ?: "HIGH"
        st.antiAliasing = prefs.getString("ax_aa", "HIGH") ?: "HIGH"
        st.sharpness = prefs.getInt("ax_sharp", 60)
        st.resolution = prefs.getInt("ax_res", 100)
        st.fpsLimit = prefs.getInt("ax_fps", 0)
        st.vulkan = prefs.getBoolean("ax_vulkan", false)
        st.noVsync = prefs.getBoolean("ax_vsync", false)
        st.gameDriver = prefs.getBoolean("ax_driver", false)
        st.frameBoost = prefs.getBoolean("ax_boost", false)
        st.showTaps = prefs.getBoolean("ax_taps", false)
        st.bitrateMbps = prefs.getInt("ax_bitrate", 12)
        st.gyro = GyroEngine.load(context)
        true
    }

    val say: (String) -> Unit = { text ->
        st.status = text
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
    val api = remember {
        AxApi(
            context = context,
            state = st,
            say = say,
            work = { block -> scope.launch { st.busy = true; runCatching { block() }; st.busy = false } },
            nav = { page -> st.page = page },
            close = onClose
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
            .background(AX_DEEP)
            .border(1.dp, AX_EDGE, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .width(56.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AX_RED)
                    .clickable(onClick = onClose)
            )
        }

        if (st.page == AxPage.HOME) {
            HomePage(api, stats, startedAt)
        } else {
            PageHeader(api, st.page.title)
            when (st.page) {
                AxPage.MENU -> MenuPage(api)
                AxPage.GFX -> GfxPage(api)
                AxPage.DISPLAY -> DisplayPage(api)
                AxPage.PERF -> PerfPage(api, stats)
                AxPage.NET -> NetPage(api, stats)
                AxPage.SPOOF -> SpoofPage(api)
                AxPage.REC -> RecPage(api)
                AxPage.OVERLAY -> OverlayPage(api)
                AxPage.GYRO -> GyroPage(api)
                AxPage.INFO -> InfoPage(api, stats)
                AxPage.HOME -> Unit
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* home — the reference console layout                                 */
/* ------------------------------------------------------------------ */

@Composable
private fun HomePage(api: AxApi, stats: PanelStats, startedAt: Long) {
    val st = api.state
    val context = api.context

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TopTile(Icons.Filled.Autorenew, "Refresh\nRate", st.refresh) {
            st.refresh = cycle(listOf("AUTO", "60Hz", "90Hz", "120Hz", "144Hz"), st.refresh, 1)
            api.work {
                val hz = when (st.refresh) {
                    "60Hz" -> 60
                    "90Hz" -> 90
                    "120Hz" -> 120
                    "144Hz" -> 144
                    else -> FpsMeter.refreshRate(context)
                }
                val ok = withContext(Dispatchers.IO) { GfxEngine.setRefreshRate(hz) }
                api.say(if (ok) "Refresh → $hz Hz" else "Refresh refused: ${Shell.reason()}")
            }
        }
        TopTile(
            Icons.Filled.Info,
            "Information\nMonitor",
            if (MonitorOverlayService.isRunning) "ON" else "OFF"
        ) { api.nav(AxPage.INFO) }
        TopTile(Icons.Filled.Dns, "DNS\nTuner", "--") { api.nav(AxPage.NET) }
    }

    // Angled red beams that frame the band, like the reference console.
    Box(Modifier.fillMaxWidth().height(12.dp)) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-46).dp)
                .width(210.dp)
                .height(3.dp)
                .graphicsLayer { rotationZ = -7f }
                .background(AX_RED)
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 46.dp)
                .width(210.dp)
                .height(3.dp)
                .graphicsLayer { rotationZ = 7f }
                .background(AX_RED)
        )
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f).height(2.dp).background(Color(0x55FF2C5E)))
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
        Box(Modifier.weight(1f).height(2.dp).background(Color(0x55FF2C5E)))
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("PM " + stats.clock, color = AX_DIM, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
            repeat(10) { index ->
                Box(
                    Modifier
                        .width(6.dp)
                        .height(9.dp)
                        .background(if (index < stats.batt / 10) Color(0xFFE4E7F4) else Color(0x33FFFFFF))
                )
            }
        }
        Spacer(Modifier.width(5.dp))
        Text("${stats.batt}%", color = AX_DIM, fontSize = 10.sp)
        Spacer(Modifier.width(10.dp))
        val elapsed = System.currentTimeMillis() - startedAt
        Text(
            String.format(Locale.US, "%02d:%02d", elapsed / 60000, (elapsed / 1000) % 60),
            color = AX_DIM,
            fontSize = 10.sp
        )
        Spacer(Modifier.weight(1f))
        Text(
            st.status,
            color = if (st.busy) Color(0xFFFFB3C6) else Color(0xFFFF8FAF),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChevronGauge("CPU", stats.cpu, flip = false)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("AXERON", color = AX_DIM, fontSize = 7.sp, letterSpacing = 3.sp)
            Text(
                "GAME CORNER",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundKey(Icons.Filled.Home, "Home") { api.nav(AxPage.HOME) }
                Spacer(Modifier.width(14.dp))
                HeroMark(Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
                RoundKey(Icons.Filled.Settings, "All features") { api.nav(AxPage.MENU) }
            }
        }
        ChevronGauge("RAM", stats.ram, flip = true)
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Tile(
                Icons.Filled.GpsFixed,
                "Crosshair",
                if (CrosshairOverlayService.isRunning) "ON" else "OFF"
            ) {
                if (CrosshairOverlayService.isRunning) CrosshairOverlayService.stop(context)
                else CrosshairOverlayService.start(context)
                api.say(if (CrosshairOverlayService.isRunning) "Crosshair off" else "Crosshair on")
            }
            Tile(Icons.Filled.ScreenLockPortrait, "Rotation\nLock", "AUTO") {
                api.work {
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
                    api.say(
                        if (ok) "Rotation ${if (auto) "locked" else "auto"}"
                        else "Rotation refused: ${Shell.reason()}"
                    )
                }
            }
            Tile(
                Icons.Filled.TouchApp,
                "Touch\nEnhancer",
                if (MappingOverlayService.isRunning) "ON" else "OFF"
            ) {
                MappingOverlayService.toggle(context)
                api.say(if (MappingOverlayService.isRunning) "Touch mapping off" else "Touch mapping on")
            }
        }
        Column(Modifier.weight(1f)) {
            Tile(Icons.Filled.Bolt, "Quick\nBoost", "FREE") {
                api.work {
                    val report = MemoryCleaner.boost(context)
                    api.say(memoryText("Boost", report, context))
                }
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
                api.say(if (ok) "Do not disturb toggled" else "Grant DND access to GamersHub")
            }
            Tile(Icons.Filled.Screenshot, "Screenshot", "SAVE") {
                api.work {
                    val shot = withContext(Dispatchers.IO) { ScreenshotEngine.capture() }
                    api.say(
                        if (shot != null) "Saved ${shot.name}"
                        else "Capture refused: ${Shell.reason()}"
                    )
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Tile(Icons.Filled.ScreenRotation, "Gyro\nCalibration", if (st.gyro.calibrated) "OK" else "CAL") {
                api.nav(AxPage.GYRO)
            }
            Tile(Icons.Filled.BrightnessHigh, "Lock\nBrightness", "LOCK") {
                api.work {
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
                    api.say(
                        if (ok) "Brightness ${next * 100 / 255}%"
                        else "Brightness refused: ${Shell.reason()}"
                    )
                }
            }
            Tile(Icons.Filled.Adjust, "Show\nTaps", if (st.showTaps) "ON" else "OFF") {
                api.work {
                    val target = !st.showTaps
                    val ok = withContext(Dispatchers.IO) {
                        Shell.ok("settings put system show_touches ${if (target) 1 else 0}")
                    }
                    if (ok) {
                        st.showTaps = target
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().putBoolean("ax_taps", target).apply()
                    }
                    api.say(
                        if (ok) "Touch pointers ${if (target) "on" else "off"}"
                        else "Touch pointers refused: ${Shell.reason()}"
                    )
                }
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MemoryKey("CLEAN", Icons.Filled.CleaningServices) {
            api.work {
                val report = MemoryCleaner.clean(context)
                api.say(memoryText("Clean", report, context))
            }
        }
        MemoryKey("BOOST", Icons.Filled.Bolt) {
            api.work {
                val report = MemoryCleaner.boost(context)
                api.say(memoryText("Boost", report, context))
            }
        }
        MemoryKey("DEEP CLEAN", Icons.Filled.Memory) {
            api.work {
                val report = MemoryCleaner.deepClean(context)
                api.say(memoryText("Deep clean", report, context))
            }
        }
    }

    // ---- graphics quick row: texture · shadows · sharpness (tap to cycle) ----
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickGfx("TEXTURE", st.texture) {
            st.texture = cycle(levels, st.texture, 1)
            api.say("Texture ${st.texture} — APPLY in Graphics to write it")
        }
        QuickGfx("SHADOW", st.shadow) {
            st.shadow = cycle(levels, st.shadow, 1)
            api.say("Shadows ${st.shadow}")
        }
        QuickGfx("SHARP", "${st.sharpness}%") {
            st.sharpness = when {
                st.sharpness < 40 -> 40
                st.sharpness < 70 -> 70
                st.sharpness < 100 -> 100
                else -> 0
            }
            api.say("Sharpness ${st.sharpness}%")
        }
        QuickGfx("APPLY", "GFX") {
            api.work {
                val result = withContext(Dispatchers.IO) {
                    GfxEngine.applyToForeground(
                        context,
                        st.texture,
                        st.shadow,
                        st.effects,
                        st.antiAliasing,
                        st.resolution,
                        st.sharpness,
                        st.fpsLimit
                    )
                }
                val (pkg, applied) = result
                api.say(
                    when {
                        applied.needsShell -> "Grant Shizuku: ${Shell.reason()}"
                        pkg.isBlank() -> "Open a game first"
                        applied.written.isNotEmpty() ->
                            "${pkg.substringAfterLast('.')} · ${applied.written.size} file(s) written"
                        else -> "No writable config — system switches only"
                    }
                )
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FooterKey(if (Recorder.isRecording) "● REC" else "REC", Recorder.isRecording) {
            if (Recorder.isRecording) {
                RecorderService.stop(context)
                api.say("Recording saved")
            } else {
                ProjectionActivity.launch(context, ProjectionActivity.MODE_RECORD)
                api.say("Accept the capture prompt to record")
            }
        }
        Spacer(Modifier.weight(1f))
        FooterKey(if (st.frameBoost) "FRAME ●" else "FRAME", st.frameBoost) {
            api.work {
                val target = !st.frameBoost
                val ok = withContext(Dispatchers.IO) { GfxEngine.setFrameBoost(target) }
                if (ok) {
                    st.frameBoost = target
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean("ax_boost", target).apply()
                }
                api.say(
                    if (ok) "Frame boost ${if (target) "on" else "off"}"
                    else "Frame boost refused: ${Shell.reason()}"
                )
            }
        }
        Spacer(Modifier.weight(1f))
        FooterKey(if (Stream.isStreaming) "● LIVE" else "LIVE", Stream.isStreaming) {
            api.nav(AxPage.REC)
        }
    }
}

/* ------------------------------------------------------------------ */
/* pages                                                               */
/* ------------------------------------------------------------------ */

@Composable
private fun PageHeader(api: AxApi, title: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x22FFFFFF))
                .clickable { api.nav(AxPage.HOME) }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AIcon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("AX-MODE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Text(api.state.status, color = Color(0xFFFF8FAF), fontSize = 8.sp, maxLines = 1)
    }
}

@Composable
private fun MenuPage(api: AxApi) {
    val items = listOf(
        Triple(AxPage.GFX, Icons.Filled.Speed, "Graphics unlock · texture, shadows, AA, Vulkan"),
        Triple(AxPage.DISPLAY, Icons.Filled.Autorenew, "Refresh rate · render scale · resolution"),
        Triple(AxPage.PERF, Icons.Filled.Memory, "Clean / boost / deep clean · thermal"),
        Triple(AxPage.NET, Icons.Filled.Dns, "Ping, jitter, DNS and radio tuning"),
        Triple(AxPage.SPOOF, Icons.Filled.Settings, "Device identity + display spoof"),
        Triple(AxPage.REC, Icons.Filled.Screenshot, "Screen recording and Go Live"),
        Triple(AxPage.OVERLAY, Icons.Filled.GpsFixed, "Crosshair, HUD, touch mapping, pointer"),
        Triple(AxPage.GYRO, Icons.Filled.ScreenRotation, "Gyro bias calibration and sensitivity"),
        Triple(AxPage.INFO, Icons.Filled.Info, "Live CPU / RAM / FPS / thermal readout")
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0x1AFFFFFF))
                    .clickable { api.nav(item.first) }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                AIcon(item.second, contentDescription = item.first.title, tint = AX_RED, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.first.title, color = AX_TXT, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        item.third,
                        color = AX_DIM,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text("›", color = AX_RED, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private val levels = listOf("LOW", "MEDIUM", "HIGH", "ULTRA")
private val fpsLevels = listOf(0, 30, 60, 90, 120)

@Composable
private fun GfxPage(api: AxApi) {
    val st = api.state
    val context = api.context
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    fun save() {
        prefs.edit()
            .putString("ax_texture", st.texture)
            .putString("ax_shadow", st.shadow)
            .putString("ax_effects", st.effects)
            .putString("ax_aa", st.antiAliasing)
            .putInt("ax_res", st.resolution)
            .putInt("ax_sharp", st.sharpness)
            .putInt("ax_fps", st.fpsLimit)
            .apply()
    }

    fun pushSystem(vulkan: Boolean = st.vulkan, vsync: Boolean = st.noVsync, driver: Boolean = st.gameDriver) {
        api.work {
            val log = withContext(Dispatchers.IO) {
                GfxEngine.applySystemGfx(
                    GfxEngine.SysGfx(
                        vulkan = vulkan,
                        frameBoost = st.frameBoost,
                        noVsync = vsync,
                        gameDriver = driver,
                        gpuComposition = true
                    )
                )
            }
            api.say(log.firstOrNull() ?: "Applied")
        }
    }

    val renderer = rememberLive("--") { GfxEngine.rendererStatus() }

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        StepRow(
            label = "TEXTURE QUALITY",
            value = st.texture,
            onPlus = { st.texture = cycle(levels, st.texture, 1); save(); api.say("Texture ${st.texture}") },
            onMinus = { st.texture = cycle(levels, st.texture, -1); save(); api.say("Texture ${st.texture}") }
        )
        StepRow(
            label = "SHADOW QUALITY",
            value = st.shadow,
            onPlus = { st.shadow = cycle(levels, st.shadow, 1); save(); api.say("Shadows ${st.shadow}") },
            onMinus = { st.shadow = cycle(levels, st.shadow, -1); save(); api.say("Shadows ${st.shadow}") }
        )
        StepRow(
            label = "EFFECTS",
            value = st.effects,
            onPlus = { st.effects = cycle(levels, st.effects, 1); save() },
            onMinus = { st.effects = cycle(levels, st.effects, -1); save() }
        )
        StepRow(
            label = "ANTI-ALIASING",
            value = st.antiAliasing,
            onPlus = { st.antiAliasing = cycle(levels, st.antiAliasing, 1); save() },
            onMinus = { st.antiAliasing = cycle(levels, st.antiAliasing, -1); save() }
        )
        StepRow(
            label = "RESOLUTION",
            value = "${st.resolution}%",
            onPlus = { st.resolution = (st.resolution + 10).coerceAtMost(100); save() },
            onMinus = { st.resolution = (st.resolution - 10).coerceAtLeast(50); save() }
        )
        StepRow(
            label = "SHARPNESS",
            value = "${st.sharpness}%",
            onPlus = { st.sharpness = (st.sharpness + 10).coerceAtMost(100); save() },
            onMinus = { st.sharpness = (st.sharpness - 10).coerceAtLeast(0); save() }
        )
        StepRow(
            label = "FPS LIMIT",
            value = if (st.fpsLimit == 0) "UNLIMITED" else "${st.fpsLimit}",
            onPlus = { st.fpsLimit = nextFps(st.fpsLimit, 1); save() },
            onMinus = { st.fpsLimit = nextFps(st.fpsLimit, -1); save() }
        )

        ActionRow("APPLY TO RUNNING GAME", "Writes the game's own quality CVars", "APPLY") {
            api.work {
                val (pkg, result) = withContext(Dispatchers.IO) {
                    GfxEngine.applyToForeground(
                        context,
                        st.texture,
                        st.shadow,
                        st.effects,
                        st.antiAliasing,
                        st.resolution,
                        st.sharpness,
                        st.fpsLimit
                    )
                }
                api.say(
                    when {
                        result.needsShell -> "Grant Shizuku: ${Shell.reason()}"
                        pkg.isBlank() -> "No game detected — open a game first"
                        result.written.isNotEmpty() ->
                            "Applied to ${pkg.substringAfterLast('.')} · ${result.written.size} file(s)"
                        else -> "No writable config in ${pkg.substringAfterLast('.')} — use the system switches"
                    }
                )
            }
        }

        SectionLabel("SYSTEM-WIDE · EVERY APP")
        SwitchRow("VULKAN RENDERER", "Skia Vulkan backend (debug.hwui.renderer)", st.vulkan) {
            val target = !st.vulkan
            st.vulkan = target
            prefs.edit().putBoolean("ax_vulkan", target).apply()
            pushSystem(vulkan = target)
        }
        SwitchRow("UNCAPPED VSYNC", "debug.hwui.disable_vsync", st.noVsync) {
            val target = !st.noVsync
            st.noVsync = target
            prefs.edit().putBoolean("ax_vsync", target).apply()
            pushSystem(vsync = target)
        }
        SwitchRow("GAME DRIVER", "Updatable GPU driver for all apps", st.gameDriver) {
            val target = !st.gameDriver
            st.gameDriver = target
            prefs.edit().putBoolean("ax_driver", target).apply()
            pushSystem(driver = target)
        }
        SwitchRow("SUSTAINED FRAME PACING", "Fixed performance + GameMode", st.frameBoost) {
            api.work {
                val target = !st.frameBoost
                val ok = withContext(Dispatchers.IO) { GfxEngine.setFrameBoost(target) }
                if (ok) {
                    st.frameBoost = target
                    prefs.edit().putBoolean("ax_boost", target).apply()
                }
                api.say(if (ok) "Frame pacing ${if (target) "sustained" else "standard"}" else Shell.reason())
            }
        }
        ReadRow("RENDERER NOW", renderer)
    }
}

@Composable
private fun DisplayPage(api: AxApi) {
    val st = api.state
    val context = api.context
    val state = remember { GfxEngine.displayState(context) }
    val override = rememberLive("") { GfxEngine.currentOverride() }

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        ReadRow("SURFACE", "${state.width}x${state.height} · ${state.density}dpi")
        ReadRow("OVERRIDE", override.ifBlank { "none" })
        StepRow(
            label = "REFRESH RATE",
            value = st.refresh,
            onPlus = {
                st.refresh = cycle(listOf("AUTO", "60Hz", "90Hz", "120Hz", "144Hz"), st.refresh, 1)
                api.work {
                    val hz = when (st.refresh) {
                        "60Hz" -> 60
                        "90Hz" -> 90
                        "120Hz" -> 120
                        "144Hz" -> 144
                        else -> FpsMeter.refreshRate(context)
                    }
                    val ok = withContext(Dispatchers.IO) { GfxEngine.setRefreshRate(hz) }
                    api.say(if (ok) "Refresh → $hz Hz" else Shell.reason())
                }
            },
            onMinus = {
                st.refresh = cycle(listOf("AUTO", "60Hz", "90Hz", "120Hz", "144Hz"), st.refresh, -1)
            }
        )
        StepRow(
            label = "RENDER SCALE",
            value = "${st.renderScale}%",
            onPlus = {
                st.renderScale = (st.renderScale + 5).coerceAtMost(100)
                api.work {
                    val ok = withContext(Dispatchers.IO) { GfxEngine.setRenderScale(context, st.renderScale) }
                    api.say(if (ok) "Render scale ${st.renderScale}%" else Shell.reason())
                }
            },
            onMinus = {
                st.renderScale = (st.renderScale - 5).coerceAtLeast(50)
                api.work {
                    val ok = withContext(Dispatchers.IO) { GfxEngine.setRenderScale(context, st.renderScale) }
                    api.say(if (ok) "Render scale ${st.renderScale}%" else Shell.reason())
                }
            }
        )
        ActionRow("RESTORE STOCK SURFACE", "wm size reset + wm density reset", "RESET") {
            api.work {
                val ok = withContext(Dispatchers.IO) { GfxEngine.resetRenderScale() }
                api.say(if (ok) "Display restored" else Shell.reason())
            }
        }
    }
}

@Composable
private fun PerfPage(api: AxApi, stats: PanelStats) {
    val context = api.context
    val available = rememberLive(0L, 2500) { MemoryCleaner.availableMb(context) }
    val thermal = rememberLive(-1) { GfxEngine.thermalStatus() }

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        ReadRow("RAM USED", "${stats.ram}%")
        ReadRow("AVAILABLE", "$available MB")
        ReadRow("TEMPERATURE", stats.temp)
        ReadRow("THERMAL STATUS", if (thermal < 0) "--" else "$thermal / 6")
        ActionRow("CLEAN MEMORY", "am kill-all + pm trim-caches", "CLEAN") {
            api.work {
                val report = MemoryCleaner.clean(context)
                api.say(memoryText("Clean", report, context))
            }
        }
        ActionRow("BOOST MEMORY", "Clean + page-cache drop", "BOOST") {
            api.work {
                val report = MemoryCleaner.boost(context)
                api.say(memoryText("Boost", report, context))
            }
        }
        ActionRow("DEEP CLEAN", "Also force-stops background apps", "DEEP") {
            api.work {
                val report = MemoryCleaner.deepClean(context)
                api.say(memoryText("Deep clean", report, context))
            }
        }
    }
}

@Composable
private fun NetPage(api: AxApi, stats: PanelStats) {
    val st = api.state
    val dns = rememberLive("off") { PingEngine.currentPrivateDns().ifBlank { "off" } }

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        ReadRow("PING (8.8.8.8)", stats.ping)
        ReadRow("PRIVATE DNS", dns)
        ActionRow("MEASURE ALL TARGETS", "TCP handshake to 8 game endpoints", "RUN") {
            api.work {
                val results = withContext(Dispatchers.IO) { PingEngine.measureAll() }
                st.netResults = results
                val best = results.filter { it.latency >= 0 }.minByOrNull { it.latency }
                api.say(
                    if (best != null) "Best: ${best.target.name} ${best.latency} ms"
                    else "No network reachable"
                )
            }
        }
        st.netResults.forEach { result ->
            ReadRow(
                result.target.name,
                if (result.latency < 0) "offline"
                else "${result.latency} ms · jitter ${result.jitter} · ${result.grade}"
            )
        }
        ActionRow("APPLY CLOUDFLARE DNS", "Private DNS for every app", "APPLY") {
            api.work {
                val ok = withContext(Dispatchers.IO) {
                    PingEngine.applyPrivateDns("1dot1dot1dot1.cloudflare-dns.com")
                }
                api.say(if (ok) "Private DNS → Cloudflare" else Shell.reason())
            }
        }
        ActionRow("RESTORE DNS", "Back to the carrier resolver", "RESET") {
            api.work {
                val ok = withContext(Dispatchers.IO) { PingEngine.resetPrivateDns() }
                api.say(if (ok) "DNS restored" else Shell.reason())
            }
        }
        ActionRow("RADIO TWEAKS", "TCP buffer + wifi scan interval", "APPLY") {
            api.work {
                val ok = withContext(Dispatchers.IO) { PingEngine.applyRadioTweaks() }
                api.say(if (ok) "Radio tweaks applied" else Shell.reason())
            }
        }
        ActionRow("RESTORE RADIO", "Undo the tweaks", "RESET") {
            api.work {
                val ok = withContext(Dispatchers.IO) { PingEngine.restoreRadioTweaks() }
                api.say(if (ok) "Radio restored" else Shell.reason())
            }
        }
    }
}

@Composable
private fun SpoofPage(api: AxApi) {
    val st = api.state
    val context = api.context
    val preset = DeviceSpoofer.presets[st.spoofIndex.coerceIn(0, DeviceSpoofer.presets.lastIndex)]
    val observed = rememberLive("--", 4000) { DeviceSpoofer.observed(context) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        ReadRow("APPS SEE", observed)
        StepRow(
            label = "PRESET",
            value = preset.name,
            onPlus = {
                st.spoofIndex = (st.spoofIndex + 1) % DeviceSpoofer.presets.size
                api.say("Preset ${DeviceSpoofer.presets[st.spoofIndex].name}")
            },
            onMinus = {
                st.spoofIndex =
                    (st.spoofIndex - 1 + DeviceSpoofer.presets.size) % DeviceSpoofer.presets.size
                api.say("Preset ${DeviceSpoofer.presets[st.spoofIndex].name}")
            }
        )
        ActionRow("SPOOF DISPLAY", "${preset.width}px · ${preset.density}dpi · ${preset.refresh}Hz", "APPLY") {
            api.work {
                val lines = withContext(Dispatchers.IO) { DeviceSpoofer.applySafe(context, preset) }
                api.say(lines.firstOrNull() ?: "Display spoofed")
            }
        }
        ActionRow("SPOOF IDENTITY", "${preset.manufacturer} ${preset.model}", "APPLY") {
            api.work {
                val result = withContext(Dispatchers.IO) { DeviceSpoofer.applyIdentity(preset) }
                api.say(if (result.first) "Identity → ${preset.model}" else result.second)
            }
        }
        ActionRow("RESTORE EVERYTHING", "wm reset + original props", "RESTORE") {
            api.work {
                val lines = withContext(Dispatchers.IO) { DeviceSpoofer.restore() }
                api.say(lines.firstOrNull() ?: "Restored")
            }
        }
    }
}

@Composable
private fun RecPage(api: AxApi) {
    val st = api.state
    val context = api.context
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val url = prefs.getString("rtmp_url", "") ?: ""

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        ReadRow("RECORDER", if (Recorder.isRecording) "RECORDING" else "idle")
        ActionRow(
            if (Recorder.isRecording) "STOP RECORDING" else "START RECORDING",
            "${st.bitrateMbps} Mbps · ${FpsMeter.refreshRate(context)} fps",
            if (Recorder.isRecording) "STOP" else "START"
        ) {
            if (Recorder.isRecording) {
                RecorderService.stop(context)
                api.say("Recording saved")
            } else {
                ProjectionActivity.launch(context, ProjectionActivity.MODE_RECORD)
                api.say("Accept the capture prompt")
            }
        }
        StepRow(
            label = "BITRATE",
            value = "${st.bitrateMbps} Mbps",
            onPlus = {
                st.bitrateMbps = (st.bitrateMbps + 4).coerceAtMost(40)
                prefs.edit().putInt("ax_bitrate", st.bitrateMbps).apply()
            },
            onMinus = {
                st.bitrateMbps = (st.bitrateMbps - 4).coerceAtLeast(4)
                prefs.edit().putInt("ax_bitrate", st.bitrateMbps).apply()
            }
        )
        SectionLabel("GO LIVE")
        ReadRow("STREAM", if (Stream.isStreaming) "LIVE" else "offline")
        ReadRow("RTMP URL", url.ifBlank { "not set in app" })
        ActionRow(
            if (Stream.isStreaming) "STOP STREAM" else "GO LIVE",
            "Uses the RTMP URL saved in the app",
            if (Stream.isStreaming) "STOP" else "LIVE"
        ) {
            if (Stream.isStreaming) {
                StreamService.stop(context)
                api.say("Stream stopped")
            } else if (url.isBlank()) {
                api.say("Set the RTMP URL in the app's Live sheet first")
            } else {
                ProjectionActivity.launch(context, ProjectionActivity.MODE_LIVE, url)
                api.say("Accept the capture prompt to go live")
            }
        }
    }
}

@Composable
private fun OverlayPage(api: AxApi) {
    val context = api.context
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        SwitchRow("CROSSHAIR", "Aim dot overlay", CrosshairOverlayService.isRunning) {
            if (CrosshairOverlayService.isRunning) CrosshairOverlayService.stop(context)
            else CrosshairOverlayService.start(context)
            api.say(if (CrosshairOverlayService.isRunning) "Crosshair off" else "Crosshair on")
        }
        SwitchRow("MONITOR HUD", "CPU · RAM · FPS · battery", MonitorOverlayService.isRunning) {
            MonitorOverlayService.toggle(context)
            api.say(if (MonitorOverlayService.isRunning) "HUD hidden" else "HUD shown")
        }
        SwitchRow("TOUCH MAPPING", "On-screen key mapping", MappingOverlayService.isRunning) {
            MappingOverlayService.toggle(context)
            api.say(if (MappingOverlayService.isRunning) "Mapping off" else "Mapping on")
        }
        SwitchRow("MOUSE POINTER", "Cursor for connected mice", MouseCursorService.isRunning) {
            MouseCursorService.toggle(context)
            api.say(if (MouseCursorService.isRunning) "Pointer off" else "Pointer on")
        }
        SwitchRow("SHOW TAPS", "Developer touch pointers", api.state.showTaps) {
            api.work {
                val target = !api.state.showTaps
                val ok = withContext(Dispatchers.IO) {
                    Shell.ok("settings put system show_touches ${if (target) 1 else 0}")
                }
                if (ok) api.state.showTaps = target
                api.say(if (ok) "Touch pointers ${if (target) "on" else "off"}" else Shell.reason())
            }
        }
    }
}

@Composable
private fun GyroPage(api: AxApi) {
    val context = api.context
    var live by remember { mutableStateOf(floatArrayOf(0f, 0f, 0f)) }
    val samples = remember { mutableListOf<FloatArray>() }

    DisposableEffect(Unit) {
        val manager = GyroEngine.sensorManager(context)
        val sensor = GyroEngine.sensor(context)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                live = floatArrayOf(event.values[0], event.values[1], event.values[2])
                if (samples.size < 400) samples.add(event.values.copyOf())
            }

            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) = Unit
        }
        if (manager != null && sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { runCatching { manager?.unregisterListener(listener) } }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        ReadRow("X", String.format(Locale.US, "%.3f rad/s", live[0]))
        ReadRow("Y", String.format(Locale.US, "%.3f rad/s", live[1]))
        ReadRow("Z", String.format(Locale.US, "%.3f rad/s", live[2]))
        ReadRow("CALIBRATED", if (api.state.gyro.calibrated) "yes" else "no")
        ActionRow("CAPTURE BIAS", "Hold the phone still for a second", "CAPTURE") {
            val bias = GyroEngine.biasOf(samples.toList())
            val config = api.state.gyro.copy(
                offX = bias[0],
                offY = bias[1],
                offZ = bias[2],
                calibrated = true
            )
            GyroEngine.save(context, config)
            api.state.gyro = config
            samples.clear()
            api.say("Gyro bias captured")
        }
        StepRow(
            label = "SENSITIVITY",
            value = String.format(Locale.US, "%.2fx", api.state.gyro.sens),
            onPlus = {
                val config = api.state.gyro.copy(sens = (api.state.gyro.sens + 0.05f).coerceAtMost(3f))
                GyroEngine.save(context, config)
                api.state.gyro = config
            },
            onMinus = {
                val config = api.state.gyro.copy(sens = (api.state.gyro.sens - 0.05f).coerceAtLeast(0.2f))
                GyroEngine.save(context, config)
                api.state.gyro = config
            }
        )
        SwitchRow("INVERT Y", "Flip the vertical axis", api.state.gyro.invertY) {
            val config = api.state.gyro.copy(invertY = !api.state.gyro.invertY)
            GyroEngine.save(context, config)
            api.state.gyro = config
            api.say(if (config.invertY) "Y inverted" else "Y normal")
        }
    }
}

@Composable
private fun InfoPage(api: AxApi, stats: PanelStats) {
    val context = api.context
    val thermal = rememberLive(-1) { GfxEngine.thermalStatus() }
    val game = rememberLive("--", 4000) { FpsMeter.foregroundPackage(context) ?: "--" }

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        ReadRow("FPS", stats.fps)
        ReadRow("CPU LOAD", "${stats.cpu}%")
        ReadRow("CPU CLOCK", "${stats.ghz} GHz")
        ReadRow("RAM", "${stats.ram}%")
        ReadRow("TEMPERATURE", stats.temp)
        ReadRow("PING", stats.ping)
        ReadRow("BATTERY", "${stats.batt}%")
        ReadRow("THERMAL", if (thermal < 0) "--" else "$thermal / 6")
        ReadRow("GAME", game)
        SwitchRow("SCREEN HUD", "Floating monitor strip", MonitorOverlayService.isRunning) {
            MonitorOverlayService.toggle(context)
            api.say(if (MonitorOverlayService.isRunning) "HUD hidden" else "HUD shown")
        }
    }
}

/* ------------------------------------------------------------------ */
/* building blocks                                                     */
/* ------------------------------------------------------------------ */

/** Plain-language result of a memory action. */
private fun memoryText(label: String, report: MemoryCleaner.Report, context: Context): String {
    val freeNow = MemoryCleaner.availableMb(context)
    return if (report.freedMb > 0 || report.appsStopped > 0) {
        "$label · ${report.freedMb} MB freed · ${report.appsStopped} apps · $freeNow MB free now"
    } else {
        "$label · nothing to reclaim · $freeNow MB already free"
    }
}

private fun cycle(list: List<String>, current: String, step: Int): String {
    val index = list.indexOf(current)
    if (index < 0) return list.first()
    return list[(index + step).coerceIn(0, list.lastIndex)]
}

private fun nextFps(current: Int, step: Int): Int {
    val index = fpsLevels.indexOf(current)
    if (index < 0) return fpsLevels.first()
    return fpsLevels[(index + step).coerceIn(0, fpsLevels.lastIndex)]
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = AX_DIM,
        fontSize = 8.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun ReadRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AX_DIM, fontSize = 9.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            color = AX_TXT,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StepRow(label: String, value: String, onPlus: () -> Unit, onMinus: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AX_DIM, fontSize = 9.sp, modifier = Modifier.weight(1f))
        RoundKey(Icons.Filled.ArrowBack, "-", onMinus)
        Box(Modifier.width(86.dp), contentAlignment = Alignment.Center) {
            Text(
                value,
                color = AX_TXT,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RoundKey(Icons.Filled.ArrowBack, "+", onPlus)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, button: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x14FFFFFF))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AX_TXT, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                subtitle,
                color = AX_DIM,
                fontSize = 7.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(AX_RED)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(button, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x14FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AX_TXT, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                subtitle,
                color = AX_DIM,
                fontSize = 7.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier
                .width(42.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(50))
                .background(if (checked) AX_RED else Color(0x33FFFFFF))
                .padding(2.dp)
        ) {
            Box(
                Modifier
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .size(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
            )
        }
    }
}

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
            color = AX_TXT,
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
            color = AX_TXT,
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
            .size(22.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0x22FFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (label == "+" || label == "-") {
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
        } else {
            AIcon(
                icon,
                contentDescription = label,
                tint = Color(0xCCFFFFFF),
                modifier = Modifier.size(12.dp)
            )
        }
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
private fun QuickGfx(label: String, value: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x1FFFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, color = AX_DIM, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, color = AX_RED, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun MemoryKey(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0x33FF2C5E))
            .border(1.dp, Color(0x55FF2C5E), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        AIcon(icon, contentDescription = label, tint = Color(0xFFFFB3C6), modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = Color(0xFFFFD6E1), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
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
