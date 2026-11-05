package com.gamershub.overlay

import com.gamershub.ui.components.AIcon

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.gamershub.data.DeviceStats
import com.gamershub.theme.GC
import com.gamershub.theme.GameCornerTheme
import com.gamershub.theme.LocalAccent
import com.gamershub.ui.components.HeroMark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pop-up panel — a pixel copy of the AX-MODE Game Corner overlay:
 * red/black angular panel with the icon rows, the AX-MODE band with
 * real clock + battery, a live CPU chevron column on the left and a
 * live RAM chevron column on the right, and the info stats strip.
 * Every control writes REAL system state where the platform allows.
 */
class FloatingPanelService : Service(), androidx.lifecycle.LifecycleOwner,
    androidx.savedstate.SavedStateRegistryOwner {

    private val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this)
    private val savedStateRegistryController =
        androidx.savedstate.SavedStateRegistryController.create(this)

    override val lifecycle: androidx.lifecycle.Lifecycle
        get() = lifecycleRegistry
    override val savedStateRegistry: androidx.savedstate.SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var bubbleView: ComposeView? = null
    private var panelView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var expanded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= 34) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        androidx.core.app.ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        addBubble()
    }

    override fun onDestroy() {
        isRunning = false
        removeViews()
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun removeViews() {
        runCatching { bubbleView?.let { windowManager?.removeView(it) } }
        runCatching { panelView?.let { windowManager?.removeView(it) } }
        bubbleView = null
        panelView = null
    }

    private fun overlayParams(width: Int = WindowManager.LayoutParams.WRAP_CONTENT): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 400
        }

    private fun addBubble() {
        val params = overlayParams()
        bubbleParams = params
        val view = ComposeView(this).apply {
            setContent {
                GameCornerTheme {
                    Bubble(
                        onTap = { togglePanel() },
                        onDrag = { dx, dy ->
                            params.x += dx.toInt()
                            params.y += dy.toInt()
                            runCatching { windowManager?.updateViewLayout(this, params) }
                        }
                    )
                }
            }
        }
        bubbleView = view
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        runCatching { windowManager?.addView(view, params) }
    }

    private fun togglePanel() {
        if (expanded) closePanel() else openPanel()
    }

    private fun openPanel() {
        expanded = true
        val params = overlayParams(WindowManager.LayoutParams.MATCH_PARENT)
        params.gravity = Gravity.BOTTOM or Gravity.START
        params.x = 0
        params.y = 0
        val view = ComposeView(this).apply {
            setContent {
                GameCornerTheme {
                    AxModePanel(onClose = { closePanel() })
                }
            }
        }
        panelView = view
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        runCatching { windowManager?.addView(view, params) }
    }

    private fun closePanel() {
        expanded = false
        panelView?.let { runCatching { windowManager?.removeView(it) } }
        panelView = null
    }

    private fun buildNotification(): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel("panel", "Pop-up Panel", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, "panel")
        } else {
            @Suppress("DEPRECATION") android.app.Notification.Builder(this)
        }
        return builder
            .setContentTitle("GamersHub panel active")
            .setContentText("Tap the bubble for the AX-MODE panel")
            .setSmallIcon(com.gamershub.R.drawable.ic_stat_gh)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 51
        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            if (isRunning) return
            val intent = Intent(context, FloatingPanelService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingPanelService::class.java))
        }
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun Bubble(onTap: () -> Unit, onDrag: (Float, Float) -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(accent)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        HeroMark(Modifier.size(34.dp))
    }
}

/* ------------------------------------------------------------------ */

/** Live device sample for the panel. */
private data class LiveStats(
    val cpu: Int = 0,
    val ram: Int = 0,
    val tempC: Int = 0,
    val ping: Int = -1,
    val ghz: String = "0.00",
    val batt: Int = 50,
    val clock: String = "--:--"
)

@Composable
private fun rememberLiveStats(): LiveStats {
    val context = androidx.compose.ui.platform.LocalContext.current
    val stats by produceState(initialValue = LiveStats()) {
        var tick = 0
        while (true) {
            val cpu = DeviceStats.cpu()
            val mem = DeviceStats.mem(context)
            val batt = runCatching {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            }.getOrDefault(50)
            val temp = DeviceStats.battery(context).second
            val ping = if (tick % 6 == 0) DeviceStats.pingMs() else value.ping
            value = LiveStats(
                cpu = cpu.pct.coerceIn(0, 100),
                ram = mem.pct.coerceIn(0, 100),
                tempC = temp.toInt(),
                ping = ping,
                ghz = String.format(Locale.US, "%.2f", cpu.curGhz),
                batt = if (batt in 0..100) batt else 50,
                clock = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
            )
            tick++
            kotlinx.coroutines.delay(800)
        }
    }
    return stats
}


/**
 * The AX-MODE panel — exact copy of the reference screenshots:
 * tab pills + live stats strip / quick tabs, the AX-MODE band with
 * clock + battery, the icon rows around the GAME CORNER wordmark,
 * live CPU (left) and RAM (right) chevron bars, home/logo/gear row.
 */
@Composable
private fun AxModePanel(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // The original AX-MODE panel is always red, independent of the app theme.
    val accent = Color(0xFFE23A3A)
    val stats by produceState(initialValue = LiveStats()) {
        while (true) {
            runCatching {
                val cpu = DeviceStats.cpu()
                val mem = DeviceStats.mem(context)
                val b = DeviceStats.battery(context)
                value = LiveStats(
                    cpu = cpu.pct.coerceIn(0, 100),
                    ram = mem.pct.coerceIn(0, 100),
                    tempC = b.second.toInt(),
                    ping = DeviceStats.pingMs(),
                    ghz = String.format(java.util.Locale.US, "%.2f", cpu.curGhz),
                    batt = b.first.coerceIn(0, 100),
                    clock = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US).format(java.util.Date())
                )
            }
            kotlinx.coroutines.delay(900)
        }
    }
    var showInfo by remember { mutableStateOf(true) }
    var showTaps by remember { mutableStateOf(false) }
    var refreshLabel by remember { mutableStateOf("AUTO") }

    Box(
        Modifier
            .fillMaxWidth()
            .background(Color(0x66000000))
            .clickable(enabled = false) {}
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            LiveBar("CPU", stats.cpu, Color(0xFFFF2D2D), right = true, modifier = Modifier.width(64.dp))
            Column(Modifier.weight(1f)) {
                // ---- tabs + stats strip / quick tabs ----
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .clip(CutCornerShape(topStart = 2.dp, topEnd = 10.dp, bottomEnd = 2.dp, bottomStart = 10.dp))
                            .background(if (!showInfo) Color(0xFFD31F26) else Color(0x33FFFFFF))
                            .clickable { showInfo = false }
                            .padding(horizontal = 13.dp, vertical = 7.dp)
                    ) {
                        AIcon(Icons.Filled.SportsEsports, contentDescription = "Controls", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(if (showInfo) Color(0xFFD31F26) else Color(0x33FFFFFF))
                            .clickable { showInfo = true }
                            .padding(8.dp)
                    ) {
                        AIcon(Icons.Filled.Info, contentDescription = "Monitor", tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    if (showInfo) {
                        Row(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xE6160A0B))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatMini("FPS", "0")
                            StatMini("CPU", "${stats.cpu}%")
                            StatMini("TEMP", "${stats.tempC}°C")
                            StatMini("PING", if (stats.ping >= 0) "${stats.ping}ms" else "--")
                            StatMini("GPU·CPU", "${stats.ghz}GHz")
                        }
                    } else {
                        Row(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            QuickTab(Icons.Filled.Diamond)
                            QuickTab(Icons.Filled.Speed)
                            QuickTab(Icons.Filled.BatteryFull)
                            QuickTab(Icons.Filled.ManageAccounts)
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // ---- handle + AX-MODE band ----
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                            .background(Color(0xFFB31217))
                            .clickable(onClick = onClose)
                            .padding(horizontal = 18.dp, vertical = 2.dp)
                    ) {
                        AIcon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.62f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFB31217))
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("AX-MODE", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 15.sp, letterSpacing = 3.sp)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(14.dp))
                    Text("PM " + stats.clock, style = MaterialTheme.typography.labelMedium, color = Color(0xCCFFFFFF), fontSize = 11.sp)
                    Spacer(Modifier.width(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(10) { i ->
                            Box(
                                Modifier.width(8.dp).height(10.dp)
                                    .background(if (i < stats.batt / 10) Color(0xFFE8E3DD) else Color(0x33FFFFFF))
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("${stats.batt}%", style = MaterialTheme.typography.labelMedium, color = Color(0xCCFFFFFF), fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text("9:07", style = MaterialTheme.typography.labelMedium, color = Color(0x66FFFFFF), fontSize = 11.sp)
                    Spacer(Modifier.width(14.dp))
                }

                // ---- icon rows around the wordmark ----
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        GridButton(Icons.Filled.Autorenew, "Refresh Rate", refreshLabel) {
                            refreshLabel = when (refreshLabel) {
                                "AUTO" -> "60Hz"; "60Hz" -> "90Hz"; "90Hz" -> "120Hz"; "120Hz" -> "144Hz"; else -> "AUTO"
                            }
                            val hz = when (refreshLabel) { "60Hz" -> 60f; "90Hz" -> 90f; "120Hz" -> 120f; "144Hz" -> 144f; else -> 120f }
                            val ok = try {
                                Settings.System.putFloat(context.contentResolver, "peak_refresh_rate", hz) &&
                                    Settings.System.putFloat(context.contentResolver, "min_refresh_rate", if (refreshLabel == "AUTO") 60f else hz)
                            } catch (_: Throwable) { false }
                            com.gamershub.feedbackOnWrite(context, ok)
                        }
                        GridButton(Icons.Filled.Info, "Information Monitor", if (MonitorOverlayService.isRunning) "ON" else "OFF") {
                            MonitorOverlayService.toggle(context)
                            openSheet(context, "monitor")
                        }
                        GridButton(Icons.Filled.Dns, "Ping Optimizer", "LATENCY") {
                            openSheet(context, "ping")
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        GridButton(Icons.Filled.GpsFixed, "Crosshair", if (CrosshairOverlayService.isRunning) "ON" else "OFF") {
                            if (CrosshairOverlayService.isRunning) CrosshairOverlayService.stop(context)
                            else CrosshairOverlayService.start(context)
                        }
                        GridButton(Icons.Filled.Speed, "Quick Boost", "FREE") {
                            val freed = com.gamershub.boostNow(context)
                            android.widget.Toast.makeText(context, "Boosted — $freed MB freed", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        GridButton(Icons.Filled.ScreenRotation, "Gyro Calibration", "CALIBRATE") {
                            openSheet(context, "gyro")
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        GridButton(Icons.Filled.ScreenLockPortrait, "Rotation Lock", "LOCK") {
                            val cur = try { Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) } catch (_: Throwable) { 1 }
                            com.gamershub.feedbackOnWrite(context, com.gamershub.putSystemSetting(context, Settings.System.ACCELEROMETER_ROTATION, if (cur == 1) 0 else 1))
                        }
                        GridButton(Icons.Filled.NotificationsOff, "Do Not Disturb", "DND") {
                            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            val ok = try { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY); true } catch (_: Throwable) { false }
                            if (!ok) {
                                android.widget.Toast.makeText(context, "Grant Do Not Disturb access to GamersHub", android.widget.Toast.LENGTH_SHORT).show()
                                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        }
                        GridButton(Icons.Filled.BrightnessHigh, "Lock Brightness", "LOCK") {
                            val cur = try { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) } catch (_: Throwable) { 128 }
                            com.gamershub.feedbackOnWrite(
                                context,
                                com.gamershub.putSystemSetting(context, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) &&
                                    com.gamershub.putSystemSetting(context, Settings.System.SCREEN_BRIGHTNESS, cur)
                            )
                        }
                    }
                    // center wordmark between the rows
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("AXERON", style = MaterialTheme.typography.labelMedium, color = Color(0x88FFFFFF), fontSize = 8.sp, letterSpacing = 3.sp)
                        Text("GAME CORNER", style = MaterialTheme.typography.titleLarge, color = Color.White, fontSize = 17.sp, fontFamily = GC.Display)
                        Spacer(Modifier.height(2.dp))
                        HeroMark(Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        GridButton(Icons.Filled.TouchApp, "Mapping Keys", if (MappingOverlayService.isRunning) "ON" else "OFF") {
                            MappingOverlayService.toggle(context)
                            openSheet(context, "mapping")
                        }
                        GridButton(Icons.Filled.Screenshot, "Screenshot", "CAPTURE") {
                            val shot = com.gamershub.core.ScreenshotEngine.capture()
                            android.widget.Toast.makeText(
                                context,
                                if (shot != null) "Saved ${shot.name}" else "Grant Shizuku to capture",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        GridButton(Icons.Filled.Adjust, "Mouse Cursor", if (MouseCursorService.isRunning) "ON" else "OFF") {
                            MouseCursorService.toggle(context)
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // ---- bottom row: REC | wordmark | LIVE ----
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (com.gamershub.core.capture.Recorder.isRecording) Color(0xFFD31F26) else Color(0x33FFFFFF))
                                .clickable { openSheet(context, "record") }
                                .padding(horizontal = 13.dp, vertical = 6.dp)
                        ) {
                            Text(
                                if (com.gamershub.core.capture.Recorder.isRecording) "● REC" else "REC",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    HeroMark(Modifier.size(24.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (com.gamershub.core.capture.Stream.isStreaming) Color(0xFFD31F26) else Color(0x33FFFFFF))
                                .clickable { openSheet(context, "live") }
                                .padding(horizontal = 13.dp, vertical = 6.dp)
                        ) {
                            Text(
                                if (com.gamershub.core.capture.Stream.isStreaming) "● LIVE" else "LIVE",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            LiveBar("RAM", stats.ram, Color(0xFFE0359B), right = false, modifier = Modifier.width(64.dp))
        }

    }
}

@Composable
private fun QuickTab(icon: ImageVector) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color(0x22FFFFFF)),
        contentAlignment = Alignment.Center
    ) {
        AIcon(icon, contentDescription = null, tint = Color(0xCCFFFFFF), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun StatMini(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 12.sp)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0x99FFFFFF), fontSize = 8.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun GridButton(icon: ImageVector, label: String, sub: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier.size(33.dp).clip(CircleShape).background(Color(0xFF8C1013)),
            contentAlignment = Alignment.Center
        ) { AIcon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(7.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White, fontSize = 10.5.sp, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.labelMedium, color = Color(0x99FFFFFF), fontSize = 8.5.sp, maxLines = 1)
        }
    }
}

/** Animated CPU / RAM chevron column — labels at the bottom like the reference. */
@Composable
private fun LiveBar(label: String, pct: Int, color: Color, right: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 4.dp),
        horizontalAlignment = if (right) Alignment.Start else Alignment.End
    ) {
        val filled = pct / 10
        repeat(10) { i ->
            val alpha by animateFloatAsState(if (i < filled) 1f else 0f, label = "bar$i")
            val col by animateColorAsState(if (i < filled) color else color.copy(alpha = 0.18f), label = "barc$i")
            Canvas(Modifier.fillMaxWidth().height(13.dp)) {
                val w = size.width
                val h = size.height
                val path = if (right) {
                    Path().apply {
                        moveTo(w, h / 2); lineTo(w * 0.55f, 0f); lineTo(0f, 0f)
                        lineTo(w * 0.35f, h / 2); lineTo(0f, h); lineTo(w * 0.55f, h); close()
                    }
                } else {
                    Path().apply {
                        moveTo(0f, h / 2); lineTo(w * 0.45f, 0f); lineTo(w, 0f)
                        lineTo(w * 0.65f, h / 2); lineTo(w, h); lineTo(w * 0.45f, h); close()
                    }
                }
                drawPath(path, col.copy(alpha = 0.18f + 0.82f * alpha))
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontSize = 15.sp, letterSpacing = 1.sp)
    }
}

/** Opens a tool sheet in the main app from the floating panel. */
private fun openSheet(context: Context, key: String) {
    runCatching {
        context.startActivity(
            Intent(context, com.gamershub.MainActivity::class.java)
                .putExtra(com.gamershub.MainActivity.EXTRA_OPEN, key)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }
}
