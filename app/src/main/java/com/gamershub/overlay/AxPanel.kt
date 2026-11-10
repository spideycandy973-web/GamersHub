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
import androidx.compose.material.icons.filled.PowerSettingsNew
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.gamershub.core.DeviceSpoofer
import com.gamershub.core.FpsMeter
import com.gamershub.core.GfxEngine
import com.gamershub.core.RenderEngine
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
import com.gamershub.core.upscale.UpscaleEngine
import com.gamershub.core.upscale.UpscaleMirror
import com.gamershub.core.shizuku.Shell
import com.gamershub.data.DeviceStats
import com.gamershub.ui.components.AIcon
import com.gamershub.ui.components.HeroMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ------------------------------------------------------------------ */
/* palette                                                             */
/* ------------------------------------------------------------------ */

/* Liquid-glass panel palette — molten gold accent, smoked ember surfaces. */
private val AX_RED = Color(0xFFFFB03A)
private val AX_DEEP = Color(0xEE0E0906)
private val AX_ARM = Color(0x14FFD9B4)
private val AX_EDGE = Color(0x3DFFDFAE)
private val AX_TXT = Color(0xFFF7F1E9)
private val AX_DIM = Color(0x99A2948A)

private const val PREFS = "gamershub"

/* ------------------------------------------------------------------ */
/* state                                                               */
/* ------------------------------------------------------------------ */

private enum class AxPage(val title: String) {
    HOME("GAMERSHUB"),
    MENU("ALL FEATURES"),
    GFX("GRAPHICS UNLOCK"),
    DISPLAY("DISPLAY"),
    UPSCALE("LOSSLESS / UPSCALE"),
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
    var status by mutableStateOf("GAMERSHUB ready")
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
    var lossless by mutableStateOf(100)
    var gpuMode by mutableStateOf("standard")
    var angle by mutableStateOf(false)
    var lastReport by mutableStateOf("")

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
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { block() }.getOrDefault(value)
            }
            kotlinx.coroutines.delay(periodMs)
        }
    }
    return state
}

/* ------------------------------------------------------------------ */
/* panel                                                               */
/* ------------------------------------------------------------------ */

@Composable
fun AxPanel(onClose: () -> Unit, onQuit: () -> Unit = onClose) {
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
        st.lossless = prefs.getInt("ax_lossless", 100)
        st.gpuMode = prefs.getString("ax_gpu_mode", "standard") ?: "standard"
        st.angle = prefs.getBoolean("ax_angle", false)
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
        // grabber: tap or pull down to close
        var dragY by remember { mutableStateOf(0f) }
        Row(
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragY > 56f) onClose()
                            dragY = 0f
                        },
                        onDragCancel = { dragY = 0f }
                    ) { _, dy -> dragY = (dragY + dy).coerceAtLeast(0f) }
                }
                .clickable(onClick = onClose),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(56.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AX_RED)
            )
        }

        if (st.page == AxPage.HOME) {
            HomePage(api, stats, startedAt, onQuit)
        } else {
            PageHeader(api, st.page.title)
            when (st.page) {
                AxPage.MENU -> MenuPage(api)
                AxPage.GFX -> GfxPage(api)
                AxPage.DISPLAY -> DisplayPage(api)
                AxPage.UPSCALE -> UpscalePage(api)
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
private fun HomePage(api: AxApi, stats: PanelStats, startedAt: Long, onQuit: () -> Unit) {
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

        // clean accent divider (the old angled beams read as noise, not design)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .height(1.dp)
            .background(Color(0x24FFFFFF))
    )

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f).height(2.dp).background(Color(0x55E25E20)))
        Box(
            Modifier
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(50))
                .background(AX_RED)
                .padding(horizontal = 24.dp, vertical = 5.dp)
        ) {
            Text(
                "GAMERSHUB",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.4.sp
            )
        }
        Box(Modifier.weight(1f).height(2.dp).background(Color(0x55E25E20)))
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
                        .background(if (index < stats.batt / 10) Color(0xFFEDE7E2) else Color(0x33FFFFFF))
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
            color = if (st.busy) Color(0xFFF3B592) else Color(0xFFF09A72),
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
            Text("GAME BOOSTER", color = AX_DIM, fontSize = 7.sp, letterSpacing = 3.sp)
            Text(
                "GAMERSHUB",
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
                Spacer(Modifier.width(14.dp))
                RoundKey(Icons.Filled.PowerSettingsNew, "Close floating icon") { onQuit() }
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
            Tile(Icons.Filled.ScreenLockPortrait, "Turn\nLock", "AUTO") {
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
                    api.say(memoryText("Speed", report, context))
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
            Tile(Icons.Filled.Screenshot, "Display Grab", "SAVE") {
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
                api.say(memoryText("Sweep", report, context))
            }
        }
        MemoryKey("BOOST", Icons.Filled.Bolt) {
            api.work {
                val report = MemoryCleaner.boost(context)
                api.say(memoryText("Speed", report, context))
            }
        }
        MemoryKey("DEEP CLEAN", Icons.Filled.Memory) {
            api.work {
                val report = MemoryCleaner.deepClean(context)
                api.say(memoryText("Full clean", report, context))
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
                api.say("Capture saved")
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
                Text("GAMERSHUB", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
        Text(api.state.status, color = Color(0xFFF09A72), fontSize = 8.sp, maxLines = 1)
    }
}

@Composable
private fun MenuPage(api: AxApi) {
    val items = listOf(
        Triple(AxPage.GFX, Icons.Filled.Speed, "Graphics unlock · texture, shadows, AA, Vulkan"),
        Triple(AxPage.UPSCALE, Icons.Filled.Bolt, "Real upscaling · lossless scale · GLES shader"),
        Triple(AxPage.DISPLAY, Icons.Filled.Autorenew, "Refresh rate · render scale · resolution"),
        Triple(AxPage.PERF, Icons.Filled.Memory, "Sweep / boost / deep clean · thermal"),
        Triple(AxPage.NET, Icons.Filled.Dns, "Ping, jitter, DNS and radio tuning"),
        Triple(AxPage.SPOOF, Icons.Filled.Settings, "Phone identity + display spoof"),
        Triple(AxPage.REC, Icons.Filled.Screenshot, "Display recording and Go Live"),
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
    val targetPkg = remember {
        runCatching { FpsMeter.foregroundPackage(context) ?: GfxEngine.currentGamePackage(context) ?: "" }
            .getOrDefault("")
    }
    val pkgLabel = targetPkg.substringAfterLast('.').ifBlank { "no game in front" }

    fun save() {
        prefs.edit()
            .putString("ax_texture", st.texture)
            .putString("ax_shadow", st.shadow)
            .putString("ax_effects", st.effects)
            .putString("ax_aa", st.antiAliasing)
            .putInt("ax_res", st.resolution)
            .putInt("ax_sharp", st.sharpness)
            .putInt("ax_fps", st.fpsLimit)
            .putInt("ax_lossless", st.lossless)
            .putString("ax_gpu_mode", st.gpuMode)
            .putBoolean("ax_angle", st.angle)
            .apply()
    }

    // Every change runs the real command set and stores the proof.
    fun applyNow() {
        save()
        api.work {
            val report = withContext(Dispatchers.IO) {
                RenderEngine.apply(
                    context = context,
                    pkg = targetPkg,
                    texture = st.texture,
                    shadow = st.shadow,
                    sharpness = st.sharpness,
                    scalePct = st.resolution,
                    fps = st.fpsLimit,
                    mode = st.gpuMode,
                    angle = st.angle,
                    losslessPct = st.lossless
                )
            }
            st.lastReport = report.details()
            api.say(report.summary())
        }
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
            api.say(log.firstOrNull() ?: "Done")
        }
    }

    val renderer = rememberLive("--") { GfxEngine.rendererStatus() }
    val deviceNow = rememberLive("--") { RenderEngine.verify() }

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        ReadRow("TARGET APP", pkgLabel)
        ReadRow("ENGINE", RenderEngine.statusLine())
        ReadRow("DEVICE NOW", deviceNow)
        Spacer(Modifier.height(6.dp))

        StepRow(
            label = "LOSSLESS SCALING",
            value = if (st.lossless >= 100) "OFF" else "${st.lossless}%",
            onPlus = { st.lossless = (st.lossless + 10).coerceAtMost(100); applyNow() },
            onMinus = { st.lossless = (st.lossless - 10).coerceAtLeast(50); applyNow() }
        )
        StepRow(
            label = "RENDER SCALE",
            value = "${st.resolution}%",
            onPlus = { st.resolution = (st.resolution + 10).coerceAtMost(100); applyNow() },
            onMinus = { st.resolution = (st.resolution - 10).coerceAtLeast(50); applyNow() }
        )
        StepRow(
            label = "FPS LIMIT",
            value = if (st.fpsLimit == 0) "UNLIMITED" else "${st.fpsLimit}",
            onPlus = { st.fpsLimit = nextFps(st.fpsLimit, 1); applyNow() },
            onMinus = { st.fpsLimit = nextFps(st.fpsLimit, -1); applyNow() }
        )
        StepRow(
            label = "GPU MODE",
            value = st.gpuMode.uppercase(),
            onPlus = {
                st.gpuMode = when (st.gpuMode) {
                    "standard" -> "performance"
                    "performance" -> "battery"
                    else -> "standard"
                }
                applyNow()
            },
            onMinus = {
                st.gpuMode = when (st.gpuMode) {
                    "standard" -> "battery"
                    "battery" -> "performance"
                    else -> "standard"
                }
                applyNow()
            }
        )
        StepRow(
            label = "TEXTURE QUALITY",
            value = st.texture,
            onPlus = { st.texture = cycle(levels, st.texture, 1); applyNow() },
            onMinus = { st.texture = cycle(levels, st.texture, -1); applyNow() }
        )
        StepRow(
            label = "SHADOW QUALITY",
            value = st.shadow,
            onPlus = { st.shadow = cycle(levels, st.shadow, 1); applyNow() },
            onMinus = { st.shadow = cycle(levels, st.shadow, -1); applyNow() }
        )
        StepRow(
            label = "SHARPNESS",
            value = "${st.sharpness}%",
            onPlus = { st.sharpness = (st.sharpness + 10).coerceAtMost(100); applyNow() },
            onMinus = { st.sharpness = (st.sharpness - 10).coerceAtLeast(0); applyNow() }
        )
        SwitchRow("ANGLE GL DRIVER", "Per-app driver swap for $pkgLabel", st.angle) {
            st.angle = !st.angle
            applyNow()
        }

        ActionRow("APPLY EVERYTHING NOW", "Runs the real commands and reads them back", "APPLY") { applyNow() }
        ActionRow("RESTORE DEVICE", "wm size/density reset, GameMode reset, engine backup", "RESTORE") {
            api.work {
                val report = withContext(Dispatchers.IO) { RenderEngine.restore(context, targetPkg) }
                st.lastReport = report.details()
                st.lossless = 100
                save()
                api.say(report.summary())
            }
        }

        if (st.lastReport.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            SectionLabel("LAST APPLY · LIVE RESULT")
            Text(
                st.lastReport,
                color = AX_DIM,
                fontSize = 9.sp,
                lineHeight = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        SectionLabel("RENDERING SAFETY")
        ReadRow("STATUS", com.gamershub.core.GfxSafe.status())
        ActionRow(
            title = "REPAIR RENDERING",
            subtitle = "Clears every graphics override old builds left behind (they cause green/corrupt screens in other apps until reboot), resets display scaling and GameMode",
            button = "REPAIR"
        ) {
            api.work {
                val log = withContext(Dispatchers.IO) { com.gamershub.core.GfxSafe.repair(context, targetPkg) }
                st.lastReport = log.joinToString("\n")
                api.say(log.firstOrNull() ?: "Repaired")
            }
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
    val state = remember { runCatching { GfxEngine.displayState(context) }.getOrNull() }
    val override = rememberLive("") { GfxEngine.currentOverride() }

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        ReadRow(
            "SURFACE",
            state?.let { "${it.width}x${it.height} · ${it.density}dpi" } ?: "--"
        )
        ReadRow("OVERRIDE", override.ifBlank { "none" })
        ReadRow("REFRESH NOW", rememberLive("--", 3000) { GfxEngine.refreshSummary(context) })
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
                    api.say(
                        if (ok) "Refresh → $hz Hz · " + GfxEngine.refreshSummary(context)
                        else "Refused · " + GfxEngine.refreshSummary(context)
                    )
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
private fun UpscalePage(api: AxApi) {
    val context = api.context
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var percent by remember { mutableStateOf(prefs.getInt("ax_lossless", 100)) }
    var quality by remember {
        mutableStateOf(
            runCatching {
                UpscaleEngine.Quality.valueOf(prefs.getString("ax_up_quality", "BALANCED") ?: "BALANCED")
            }.getOrDefault(UpscaleEngine.Quality.BALANCED)
        )
    }
    var sharpen by remember { mutableStateOf(prefs.getInt("ax_up_sharp", 35)) }
    var integer by remember { mutableStateOf(prefs.getBoolean("ax_up_integer", false)) }
    var bench by remember { mutableStateOf<UpscaleEngine.Bench?>(null) }
    var measured by remember { mutableStateOf("") }
    var frameGen by remember { mutableStateOf(prefs.getBoolean("ax_up_framegen", false)) }

    val caps = remember { runCatching { UpscaleEngine.caps(context) }.getOrNull() }
    val state = rememberLive<RenderEngine.LosslessState?>(null, 2500) {
        RenderEngine.losslessState(context)
    }
    val liveFps = rememberLive("--", 2000) {
        val fps = FpsMeter.liveFps(context)
        if (fps > 0) "$fps fps · ${FpsMeter.compositorHz(context)} Hz panel" else "game not readable"
    }
    val frameTimes = rememberLive("--", 2500) {
        val hz = FpsMeter.compositorHz(context)
        val pkg = FpsMeter.foregroundPackageDeep(context) ?: ""
        val stats = FpsMeter.frameStats(pkg, hz)
        if (stats.ok) stats.line() else "no frame data"
    }
    val mirrorState = rememberLive("--", 1200) { UpscaleMirror.state() }

    fun persist() {
        prefs.edit()
            .putInt("ax_lossless", percent)
            .putString("ax_up_quality", quality.name)
            .putInt("ax_up_sharp", sharpen)
            .putBoolean("ax_up_integer", integer)
            .putBoolean("ax_up_framegen", frameGen)
            .apply()
    }

    fun applyScale(target: Int) {
        percent = target.coerceIn(40, 100)
        persist()
        api.work {
            val before = withContext(Dispatchers.IO) { FpsMeter.liveFps(context) }
            val steps = withContext(Dispatchers.IO) { RenderEngine.applyLossless(context, percent) }
            // let the compositor settle so the second sample is a real measurement
            delay(1600)
            val after = withContext(Dispatchers.IO) { FpsMeter.liveFps(context) }
            measured = if (before > 0 && after > 0) "$before → $after fps" else "fps not readable"
            val applied = steps.count { it.ok }
            api.say(
                if (percent >= 100) "Native · $measured"
                else "$applied/${steps.size} applied · $measured"
            )
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        ReadRow("GPU", caps?.renderer?.take(30) ?: "--")
        ReadRow("CAPABLE", caps?.line() ?: "--")
        ReadRow("LIVE", liveFps + if (measured.isNotBlank()) " · $measured" else "")
        ReadRow("FRAME TIME", frameTimes)
        StepRow(
            label = "LOSSLESS SCALE",
            value = if (percent >= 100) "OFF" else "$percent%",
            onPlus = { applyScale(percent + 5) },
            onMinus = { applyScale(percent - 5) }
        )
        ReadRow("PIXELS", state?.summary() ?: "reading…")
        ActionRow("NATIVE RESOLUTION", "wm size reset · back to the panel", "RESET") {
            applyScale(100)
        }
        SectionLabel("UPSCALER")
        StepRow(
            label = "PRESET",
            value = quality.label.uppercase(),
            onPlus = {
                val all = UpscaleEngine.Quality.values()
                quality = all[(quality.ordinal + 1) % all.size]
                persist()
                api.say("${quality.label} · ${quality.blurb}")
            },
            onMinus = {
                val all = UpscaleEngine.Quality.values()
                quality = all[(quality.ordinal - 1 + all.size) % all.size]
                persist()
                api.say("${quality.label} · ${quality.blurb}")
            }
        )
        StepRow(
            label = "SHARPNESS",
            value = "$sharpen%",
            onPlus = { sharpen = (sharpen + 5).coerceAtMost(100); persist() },
            onMinus = { sharpen = (sharpen - 5).coerceAtLeast(0); persist() }
        )
        SwitchRow(
            title = "INTEGER SCALE",
            subtitle = "nearest-neighbour · pixel perfect, no blur",
            checked = integer,
            onClick = {
                integer = !integer
                persist()
                api.say(if (integer) "Integer scaling · GL_NEAREST" else "Adaptive scaling · GL_LINEAR")
            }
        )
        ActionRow(
            title = "SHADER TEST",
            subtitle = bench?.let { it.line() + " · " + it.quality.label } ?: "renders real frames on this GPU",
            button = "RUN"
        ) {
            api.work {
                val metrics = context.resources.displayMetrics
                bench = withContext(Dispatchers.IO) {
                    UpscaleEngine.benchmark(
                        context,
                        metrics.widthPixels,
                        metrics.heightPixels,
                        quality
                    )
                }
                api.say(bench?.line() ?: "no measurement")
            }
        }
        SectionLabel("FRAME GENERATION")
        SwitchRow(
            title = "FRAME GENERATION",
            subtitle = "optical flow + motion-compensated synthesis · generated frames are counted separately",
            checked = frameGen,
            onClick = {
                frameGen = !frameGen
                persist()
                UpscaleMirror.frameGen = frameGen
                api.say(
                    if (frameGen) "Frame gen arms with the mirror · real frames stay real"
                    else "Frame generation off"
                )
            }
        )
        ReadRow("GENERATED", if (frameGen) "${UpscaleMirror.generated}/s synthesised" else "off")
        if (UpscaleMirror.frameGenError.isNotBlank()) {
            ReadRow("FRAMEGEN", UpscaleMirror.frameGenError)
        }
        SectionLabel("UPSCALE MIRROR")
        ReadRow("STATE", mirrorState)
        RunStopRow(
            title = "GRAPHICS ENGINE",
            subtitle = if (UpscaleMirror.supported())
                "GL upscale + sharpening on your game's frames · pick the game in the dialog"
            else UpscaleMirror.whyNot(),
            running = UpscaleMirror.isRunning,
            onRun = {
                UpscaleMirror.quality = quality
                UpscaleMirror.sharpen = sharpen / 100f
                UpscaleMirror.integer = integer
                UpscaleMirror.frameGen = frameGen
                UpscaleMirror.start(context)
                api.say(if (UpscaleMirror.supported()) "Pick the game in the capture dialog" else UpscaleMirror.whyNot())
            },
            onStop = {
                UpscaleMirror.stop(context)
                api.say("Engine stopped")
            }
        )
        SectionLabel("PROFILES")
        ActionRow(
            title = "MAX ULTRA",
            subtitle = "native resolution · ULTRA preset · sharpness 70 · ultra engine cvars · max Hz",
            button = "ONE TAP"
        ) {
            percent = 100
            quality = UpscaleEngine.Quality.ULTRA
            sharpen = 70
            integer = false
            frameGen = false
            persist()
            api.work {
                val lossless = withContext(Dispatchers.IO) { RenderEngine.applyLossless(context, 100) }
                val pkg = withContext(Dispatchers.IO) { FpsMeter.foregroundPackageDeep(context) } ?: ""
                val game = if (pkg.isNotBlank()) withContext(Dispatchers.IO) {
                    RenderEngine.apply(
                        context = context,
                        pkg = pkg,
                        texture = "ULTRA",
                        shadow = "ULTRA",
                        sharpness = 70,
                        scalePct = 100,
                        fps = 0,
                        mode = "performance",
                        losslessPct = 100
                    )
                } else null
                val hz = GfxEngine.panelRates().maxOrNull() ?: FpsMeter.refreshRate(context)
                val hzOk = withContext(Dispatchers.IO) { GfxEngine.setRefreshRate(hz) }
                api.say(
                    "MAX ULTRA · native ${lossless.count { it.ok }}/${lossless.size} · " +
                        (game?.summary() ?: "no game in front") + " · ${hz}Hz ${if (hzOk) "on" else "refused"}"
                )
            }
        }
        ActionRow(
            title = "MAX FPS",
            subtitle = "70% render scale · balanced upscale · frame generation · performance mode",
            button = "ONE TAP"
        ) {
            percent = 70
            quality = UpscaleEngine.Quality.BALANCED
            sharpen = 40
            frameGen = true
            persist()
            UpscaleMirror.frameGen = true
            api.work {
                val lossless = withContext(Dispatchers.IO) { RenderEngine.applyLossless(context, 70) }
                val pkg = withContext(Dispatchers.IO) { FpsMeter.foregroundPackageDeep(context) } ?: ""
                val game = if (pkg.isNotBlank()) withContext(Dispatchers.IO) {
                    RenderEngine.apply(
                        context = context,
                        pkg = pkg,
                        texture = "MEDIUM",
                        shadow = "MEDIUM",
                        sharpness = 40,
                        scalePct = 70,
                        fps = 0,
                        mode = "performance",
                        losslessPct = 70
                    )
                } else null
                api.say(
                    "MAX FPS · 70% ${lossless.count { it.ok }}/${lossless.size} · " +
                        (game?.summary() ?: "no game in front") + " · frame gen ready in the mirror"
                )
            }
        }
        SectionLabel("WHAT IS REAL HERE")
        ReadRow("LEVEL 1", "display size · refresh · fps · thermal — no root")
        ReadRow("LEVEL 2", "GLES upscale + adaptive sharpen · integer scale · frame gen in the mirror")
        ReadRow("LEVEL 3", "frames inside the GAME's own pipeline need a present hook (root / ROM)")
        ReadRow("NOTE", "generated frames smooth motion — they never raise the game's render fps")
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
                api.say(memoryText("Sweep", report, context))
            }
        }
        ActionRow("BOOST MEMORY", "Sweep + page-cache drop", "BOOST") {
            api.work {
                val report = MemoryCleaner.boost(context)
                api.say(memoryText("Speed", report, context))
            }
        }
        ActionRow("DEEP CLEAN", "Also force-stops background apps", "DEEP") {
            api.work {
                val report = MemoryCleaner.deepClean(context)
                api.say(memoryText("Full clean", report, context))
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

    val deviceName = rememberLive("--", 4000) {
        com.gamershub.core.shizuku.Shell.exec("settings get global device_name")?.trim().orEmpty()
            .ifBlank { "stock" }
    }
    val spoofStatus = rememberLive(DeviceSpoofer.status(context), 4000) { DeviceSpoofer.status(context) }
    val refreshLine = rememberLive("--", 4000) { GfxEngine.refreshSummary(context) }
    val rooted = rememberLive(false, 8000) { DeviceSpoofer.isRooted() }

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        ReadRow("APPS SEE", observed)
        ReadRow("DEVICE NAME", deviceName)
        // What CPU-Z / any app reads — model identity needs root, and this row says so.
        ReadRow(
            "MODEL (CPU-Z)",
            spoofStatus.model + if (!spoofStatus.model.equals(preset.model, ignoreCase = true))
                (if (rooted) "" else " · needs root") else " ✓"
        )
        ReadRow("MANUFACTURER", spoofStatus.manufacturer)
        ReadRow("REFRESH", refreshLine)
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
        ActionRow(
            "SPOOF MODEL IDENTITY",
            if (rooted) "${preset.manufacturer} ${preset.model}"
            else "Root required: Android makes ro.product.model read-only. Display, density, refresh and the device name are applied without root.",
            if (rooted) "APPLY" else "ROOT?"
        ) {
            api.work {
                val result = withContext(Dispatchers.IO) { DeviceSpoofer.applyIdentity(preset) }
                api.say(if (result.first) "Identity → ${preset.model}" else result.second)
            }
        }
        ActionRow("RESTORE EVERYTHING", "wm reset, real device name, refresh, props", "RESTORE") {
            api.work {
                val lines = withContext(Dispatchers.IO) { DeviceSpoofer.restore(context) }
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
        RunStopRow(
            title = "RECORDER",
            subtitle = "${st.bitrateMbps} Mbps · ${FpsMeter.refreshRate(context)} fps",
            running = Recorder.isRecording,
            onRun = {
                if (Recorder.isRecording) {
                    api.say("Already recording")
                } else {
                    ProjectionActivity.launch(context, ProjectionActivity.MODE_RECORD)
                    api.say("Accept the capture prompt")
                }
            },
            onStop = {
                if (Recorder.isRecording) {
                    RecorderService.stop(context)
                    api.say("Capture saved")
                } else {
                    api.say("Not recording")
                }
            }
        )
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
        RunStopRow(
            title = "STREAM",
            subtitle = "Uses the RTMP URL saved in the app",
            running = Stream.isStreaming,
            onRun = {
                if (Stream.isStreaming) {
                    api.say("Already live")
                } else if (url.isBlank()) {
                    api.say("Set the RTMP URL in the app's Live sheet first")
                } else {
                    ProjectionActivity.launch(context, ProjectionActivity.MODE_LIVE, url)
                    api.say("Accept the capture prompt to go live")
                }
            },
            onStop = {
                if (Stream.isStreaming) {
                    StreamService.stop(context)
                    api.say("Stream stopped")
                } else {
                    api.say("Not live")
                }
            }
        )
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
        ReadRow("AUTHOR", com.gamershub.ui.components.Brand.HANDLE)
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

/** Two explicit buttons — the engine can always be run or stopped, not toggled blind. */
@Composable
private fun RunStopRow(
    title: String,
    subtitle: String,
    running: Boolean,
    onRun: () -> Unit,
    onStop: () -> Unit
) {
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
            Text(
                title + if (running) "  · RUNNING" else "  · STOPPED",
                color = if (running) AX_RED else AX_TXT,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(subtitle, color = AX_DIM, fontSize = 7.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(if (running) Color(0x33FFFFFF) else AX_RED)
                .clickable(onClick = onRun)
                .padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Text("RUN", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(5.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(if (running) AX_RED else Color(0x33FFFFFF))
                .clickable(onClick = onStop)
                .padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Text("STOP", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
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
            .background(Color(0x33E25E20))
            .border(1.dp, Color(0x55E25E20), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        AIcon(icon, contentDescription = label, tint = Color(0xFFF3B592), modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = Color(0xFFFFDCC9), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
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
