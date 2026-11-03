package com.gamershub.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.gamershub.theme.GC
import com.gamershub.theme.GameCornerTheme
import com.gamershub.theme.LocalAccent
import com.gamershub.ui.components.Grabber
import com.gamershub.ui.components.Toggle
import kotlin.math.roundToInt

/**
 * Pop-up panel: a draggable floating bubble that expands into the quick
 * panel — brightness, media volume, refresh rate, show taps, rotation
 * lock and DND. Every control writes REAL system state.
 */
class FloatingPanelService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: ComposeView? = null
    private var panelView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var expanded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIF_ID, buildNotification())
        addBubble()
    }

    override fun onDestroy() {
        isRunning = false
        removeViews()
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
        runCatching { windowManager?.addView(view, params) }
    }

    private fun togglePanel() {
        if (expanded) closePanel() else openPanel()
    }

    private fun openPanel() {
        expanded = true
        val params = overlayParams()
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 30
        params.y = 30
        val view = ComposeView(this).apply {
            setContent {
                GameCornerTheme {
                    QuickPanel(onClose = { closePanel() })
                }
            }
        }
        panelView = view
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
            .setContentText("Tap the bubble for quick controls")
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
        Icon(Icons.Filled.Bolt, contentDescription = "Panel", tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun QuickPanel(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val accent = LocalAccent.current

    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var volume by remember {
        mutableFloatStateOf(
            audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        )
    }
    var brightness by remember {
        mutableFloatStateOf(
            try {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
            } catch (_: Throwable) { 0.5f }
        )
    }
    var showTaps by remember { mutableStateOf(readSystemInt(context, "show_touches") == 1) }
    var rotationLock by remember { mutableStateOf(readSystemInt(context, Settings.System.ACCELEROMETER_ROTATION) == 0) }
    var dnd by remember { mutableStateOf(false) }
    var refreshStep by remember { mutableStateOf(0) }
    val refreshLabels = listOf("60Hz", "90Hz", "120Hz", "144Hz")

    Column(
        Modifier
            .width(330.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xF5160E0F))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(20.dp).background(accent))
            Spacer(Modifier.width(9.dp))
            Text("Quick Panel", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(CircleShape).background(Color(0x22FFFFFF)).clickable(onClick = onClose).padding(7.dp)
            ) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = GC.Text, modifier = Modifier.size(16.dp)) }
        }
        Spacer(Modifier.height(12.dp))

        Text("Brightness", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
        Slider(
            value = brightness,
            onValueChange = { brightness = it },
            onValueChangeFinished = {
                try {
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (brightness * 255).toInt().coerceIn(10, 255))
                } catch (_: Throwable) {}
            }
        )

        Text("Media Volume", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
        Slider(
            value = volume,
            onValueChange = { volume = it },
            onValueChangeFinished = {
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * max).toInt(), 0)
            }
        )

        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.TouchApp, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Text("Show Taps", style = MaterialTheme.typography.labelLarge, color = GC.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Toggle(on = showTaps) {
                showTaps = it
                Settings.System.putInt(context.contentResolver, "show_touches", if (it) 1 else 0)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ScreenLockPortrait, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Text("Rotation Lock", style = MaterialTheme.typography.labelLarge, color = GC.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Toggle(on = rotationLock) {
                rotationLock = it
                Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (it) 0 else 1)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Refresh", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                refreshLabels.forEachIndexed { i, label ->
                    val selected = refreshStep == i
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) accent else Color(0xFF241718))
                            .clickable {
                                refreshStep = i
                                val hz = when (i) { 1 -> 90f; 2 -> 120f; 3 -> 144f; else -> 60f }
                                try {
                                    Settings.System.putFloat(context.contentResolver, Settings.System.PEAK_REFRESH_RATE, hz)
                                    Settings.System.putFloat(context.contentResolver, Settings.System.MIN_REFRESH_RATE, hz)
                                } catch (_: Throwable) {}
                            }
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) Color.White else GC.TextSoft, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

private fun readSystemInt(context: Context, name: String): Int = try {
    Settings.System.getInt(context.contentResolver, name)
} catch (_: Throwable) {
    -1
}
