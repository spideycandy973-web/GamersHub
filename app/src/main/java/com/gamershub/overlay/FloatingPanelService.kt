package com.gamershub.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.gamershub.feedbackOnWrite
import com.gamershub.putSystemSetting
import com.gamershub.theme.GC
import com.gamershub.theme.GameCornerTheme
import com.gamershub.theme.LocalAccent
import com.gamershub.ui.components.BrandMark

/** AX-MODE pop-up: draggable bubble → red/black panel with live CPU/RAM bars. */
class FloatingPanelService : Service(), androidx.lifecycle.LifecycleOwner,
    androidx.savedstate.SavedStateRegistryOwner {

    private val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this)
    private val savedStateRegistryController =
        androidx.savedstate.SavedStateRegistryController.create(this)

    override val lifecycle: androidx.lifecycle.Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: androidx.savedstate.SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var bubbleView: ComposeView? = null
    private var panelView: ComposeView? = null
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 400 }

    private fun addBubble() {
        val params = overlayParams()
        val view = ComposeView(this).apply {
            val self = this
            setContent {
                GameCornerTheme {
                    val accent = LocalAccent.current
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(accent)
                            .pointerInput(Unit) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    params.x += drag.x.toInt()
                                    params.y += drag.y.toInt()
                                    runCatching { windowManager?.updateViewLayout(self, params) }
                                }
                            }
                            .clickable { if (expanded) closePanel() else openPanel() },
                        contentAlignment = Alignment.Center
                    ) {
                        BrandMark(Modifier.size(32.dp), tint = Color.White)
                    }
                }
            }
        }
        bubbleView = view
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        runCatching { windowManager?.addView(view, params) }
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
            manager.createNotificationChannel(NotificationChannel("panel", "Pop-up Panel", NotificationManager.IMPORTANCE_LOW))
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, "panel")
        } else {
            @Suppress("DEPRECATION") android.app.Notification.Builder(this)
        }
        return builder
            .setContentTitle("GamersHub panel active")
            .setContentText("Tap the bubble for the AX-MODE panel")
            .setSmallIcon(com.gamershub.R.drawable.intro_creature)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingPanelService::class.java))
        }
    }
}

/* ------------------------------------------------------------------ */

private data class LiveStats(val cpu: Int = 0, val ram: Int = 0, val batt: Int = 50, val clock: String = "--:--")

@Composable
private fun AxModePanel(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val accent = LocalAccent.current
    val stats by produceState(initialValue = LiveStats()) {
        while (true) {
            val cpu = DeviceStats.cpu()
            val mem = DeviceStats.mem(context)
            val b = DeviceStats.battery(context)
            value = LiveStats(cpu.pct.coerceIn(0, 100), mem.pct.coerceIn(0, 100), b.first, java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US).format(java.util.Date()))
            kotlinx.coroutines.delay(800)
        }
    }
    var showTaps by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color(0x66000000))
            .clickable(enabled = false) {}
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            LiveBar("CPU", stats.cpu, Color(0xFFFF2D2D), right = true, modifier = Modifier.width(62.dp))
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .background(Color(0xFFB31217))
                            .clickable(onClick = onClose)
                            .padding(horizontal = 24.dp, vertical = 4.dp)
                    ) {
                        Text("AX-MODE", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 14.sp, letterSpacing = 3.sp)
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(CutCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                        .background(Color(0xE622090B))
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(12.dp))
                    Text("PM " + stats.clock, style = MaterialTheme.typography.labelMedium, color = Color(0xCCFFFFFF), fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${stats.batt}%", style = MaterialTheme.typography.labelMedium, color = Color(0xCCFFFFFF), fontSize = 11.sp)
                    Spacer(Modifier.width(12.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        GridButton(Icons.Filled.BrightnessHigh, "Lock Brightness", "LOCK") {
                            val cur = try { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) } catch (_: Throwable) { 128 }
                            feedbackOnWrite(context, putSystemSetting(context, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) && putSystemSetting(context, Settings.System.SCREEN_BRIGHTNESS, cur))
                        }
                        GridButton(Icons.Filled.ScreenLockPortrait, "Rotation Lock", "LOCK") {
                            val cur = try { Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) } catch (_: Throwable) { 1 }
                            feedbackOnWrite(context, putSystemSetting(context, Settings.System.ACCELEROMETER_ROTATION, if (cur == 1) 0 else 1))
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("GAMERSHUB", style = MaterialTheme.typography.labelMedium, color = Color(0x88FFFFFF), fontSize = 8.sp, letterSpacing = 3.sp)
                        Text("GAME CORNER", style = MaterialTheme.typography.titleLarge, color = Color.White, fontSize = 16.sp, fontFamily = GC.Display)
                        Spacer(Modifier.height(2.dp))
                        BrandMark(Modifier.size(20.dp), tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        GridButton(Icons.Filled.TouchApp, "Touch Enhancer", if (showTaps) "ON" else "OFF") {
                            showTaps = !showTaps
                            feedbackOnWrite(context, putSystemSetting(context, "show_touches", if (showTaps) 1 else 0))
                        }
                        GridButton(Icons.Filled.Adjust, "Show Taps", "TAP") {
                            val cur = try { Settings.System.getInt(context.contentResolver, "show_touches") } catch (_: Throwable) { 0 }
                            feedbackOnWrite(context, putSystemSetting(context, "show_touches", if (cur == 1) 0 else 1))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Home, contentDescription = "Home", tint = Color(0x88FFFFFF), modifier = Modifier.size(17.dp))
                    }
                    BrandMark(Modifier.size(22.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color(0x88FFFFFF), modifier = Modifier.size(17.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            LiveBar("RAM", stats.ram, Color(0xFFE0359B), right = false, modifier = Modifier.width(62.dp))
        }
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
            Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF8C1013)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(7.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White, fontSize = 10.sp, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.labelMedium, color = Color(0x99FFFFFF), fontSize = 8.sp, maxLines = 1)
        }
    }
}

/** Animated CPU / RAM chevron column. */
@Composable
private fun LiveBar(label: String, pct: Int, color: Color, right: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = if (right) Alignment.Start else Alignment.End) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontSize = 14.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        val filled = pct / 10
        repeat(10) { i ->
            val alpha by animateFloatAsState(if (i < filled) 1f else 0f, label = "bar$i")
            val col by animateColorAsState(if (i < filled) color else color.copy(alpha = 0.18f), label = "barc$i")
            Canvas(Modifier.fillMaxWidth().height(12.dp)) {
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
        Spacer(Modifier.height(2.dp))
        Text("$pct%", style = MaterialTheme.typography.labelMedium, color = color, fontSize = 11.sp)
    }
}
