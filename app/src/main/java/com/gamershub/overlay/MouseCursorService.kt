package com.gamershub.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.gamershub.core.shizuku.Shell
import com.gamershub.theme.GameCornerTheme

/**
 * Mouse cursor — a real virtual pointer for touch-only games.
 *
 * A pointer overlay is moved by dragging the round trackpad (sensitivity comes
 * from prefs) and a tap on the trackpad injects a real touch at the pointer
 * position through Shizuku. Double-tap the trackpad to close.
 */
class MouseCursorService : Service(), androidx.lifecycle.LifecycleOwner,
    androidx.savedstate.SavedStateRegistryOwner {

    private val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this)
    private val savedStateRegistryController =
        androidx.savedstate.SavedStateRegistryController.create(this)

    override val lifecycle: androidx.lifecycle.Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: androidx.savedstate.SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var cursorView: ComposeView? = null
    private var padView: ComposeView? = null
    private var cursorParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= 34)
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        androidx.core.app.ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        if (!android.provider.Settings.canDrawOverlays(this)) {
            android.widget.Toast.makeText(
                this,
                "Allow display over other apps for GamersHub",
                android.widget.Toast.LENGTH_LONG
            ).show()
            isRunning = false
            stopSelf()
            return
        }

        val metrics = resources.displayMetrics
        val prefs = getSharedPreferences("gamershub", MODE_PRIVATE)
        val sens = prefs.getFloat("mouse_sens", 1.4f).coerceIn(0.3f, 3.5f)

        val cp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.widthPixels / 2
            y = metrics.heightPixels / 2
        }
        cursorParams = cp

        val cursor = ComposeView(this).apply {
            setContent {
                GameCornerTheme {
                    Canvas(Modifier.size(26.dp)) {
                        val w = size.width
                        val h = size.height
                        val p = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(0f, h * 0.74f)
                            lineTo(w * 0.24f, h * 0.57f)
                            lineTo(w * 0.38f, h * 0.95f)
                            lineTo(w * 0.53f, h * 0.87f)
                            lineTo(w * 0.39f, h * 0.51f)
                            lineTo(w * 0.66f, h * 0.51f)
                            close()
                        }
                        drawPath(p, Color(0xFFFFFFFF))
                        drawPath(p, Color(0xFF101010), style = Stroke(width = 2.2f))
                        drawCircle(
                            color = Color(0x55FFFFFF),
                            radius = 2.5f,
                            center = Offset(w * 0.5f, h * 0.5f)
                        )
                    }
                }
            }
        }
        cursorView = cursor
        cursor.setViewTreeLifecycleOwner(this)
        cursor.setViewTreeSavedStateRegistryOwner(this)
        runCatching { windowManager?.addView(cursor, cp) }

        val padParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.widthPixels - 190
            y = metrics.heightPixels - 320
        }

        val pad = ComposeView(this).apply {
            setContent {
                GameCornerTheme {
                    var coords by remember { mutableStateOf("${metrics.widthPixels / 2}, ${metrics.heightPixels / 2}") }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(Color(0x3310D0C0))
                                .border(1.5.dp, Color(0xCC10D0C0), CircleShape)
                                .pointerInput(sens) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        val p = cursorParams
                                        if (p != null) {
                                            p.x = (p.x + (drag.x * sens)).toInt().coerceIn(0, metrics.widthPixels)
                                            p.y = (p.y + (drag.y * sens)).toInt().coerceIn(0, metrics.heightPixels)
                                            coords = "${p.x}, ${p.y}"
                                            runCatching { cursorView?.let { c -> windowManager?.updateViewLayout(c, p) } }
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            val p = cursorParams
                                            if (p != null) Shell.ok("input tap ${p.x + 8} ${p.y + 12}")
                                        },
                                        onDoubleTap = { stopSelf() }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("◎", color = Color(0xFF10D0C0), fontSize = 22.sp)
                        }
                        Text(
                            coords,
                            color = Color(0xCCFFFFFF),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(top = 3.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xCC100B0C))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
        padView = pad
        pad.setViewTreeLifecycleOwner(this)
        pad.setViewTreeSavedStateRegistryOwner(this)
        runCatching { windowManager?.addView(pad, padParams) }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    override fun onDestroy() {
        isRunning = false
        runCatching { cursorView?.let { windowManager?.removeView(it) } }
        runCatching { padView?.let { windowManager?.removeView(it) } }
        cursorView = null
        padView = null
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel("cursor", "Mouse cursor", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            android.app.Notification.Builder(this, "cursor")
        else @Suppress("DEPRECATION") android.app.Notification.Builder(this)
        return b.setContentTitle("Mouse cursor active")
            .setContentText("GamersHub virtual pointer")
            .setSmallIcon(com.gamershub.R.drawable.ic_stat_gh)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 55

        private val runningState = androidx.compose.runtime.mutableStateOf(false)

        var isRunning: Boolean
            get() = runningState.value
            private set(value) {
                runningState.value = value
            }

        fun start(context: Context) {
            if (isRunning) return
            val intent = Intent(context, MouseCursorService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MouseCursorService::class.java))
        }

        fun toggle(context: Context) {
            if (isRunning) stop(context) else start(context)
        }

        fun sens(context: Context): Float =
            context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
                .getFloat("mouse_sens", 1.4f)

        fun setSens(context: Context, value: Float) {
            context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
                .edit().putFloat("mouse_sens", value.coerceIn(0.3f, 3.5f)).apply()
        }

        /** True when a physical mouse or touchpad is attached. */
        fun hasMouse(): Boolean = try {
            android.view.InputDevice.getDeviceIds().any { id ->
                val device = android.view.InputDevice.getDevice(id)
                device != null && (
                    device.sources and android.view.InputDevice.SOURCE_MOUSE ==
                        android.view.InputDevice.SOURCE_MOUSE
                    )
            }
        } catch (_: Throwable) {
            false
        }
    }
}
