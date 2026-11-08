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
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.SportsEsports
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
 * Pop-up panel — the GamersHub overlay:
 * warm-black angular panel with the icon rows, the GAMERSHUB band with
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
    private var panelView: android.view.View? = null
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
        val attached = runCatching { windowManager?.addView(view, params) }.isSuccess
        if (!attached) {
            android.widget.Toast.makeText(
                this,
                "Panel could not attach — check display-over-apps",
                android.widget.Toast.LENGTH_LONG
            ).show()
            isRunning = false
            stopSelf()
        }
    }

    private fun togglePanel() {
        if (expanded) closePanel() else openPanel()
    }

    private fun openPanel() {
        if (panelView != null) return
        val params = overlayParams(WindowManager.LayoutParams.MATCH_PARENT)
        // Taps anywhere outside the sheet come back as ACTION_OUTSIDE and close it —
        // no need to hunt for the floating bubble again.
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        params.gravity = Gravity.BOTTOM or Gravity.START
        params.x = 0
        params.y = 0
        val content = ComposeView(this).apply {
            setContent {
                GameCornerTheme {
                    AxPanel(onClose = { closePanel() })
                }
            }
        }
        // ComposeView is final, so the outside-tap handler lives on a thin host.
        val view = object : android.widget.FrameLayout(this) {
            override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
                if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                    closePanel()
                    return true
                }
                return super.dispatchTouchEvent(event)
            }
        }
        view.addView(content, android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        content.setViewTreeLifecycleOwner(this)
        content.setViewTreeSavedStateRegistryOwner(this)
        val attached = runCatching { windowManager?.addView(view, params) }.isSuccess
        if (!attached) {
            android.widget.Toast.makeText(
                this,
                "Panel could not attach — check display-over-apps",
                android.widget.Toast.LENGTH_LONG
            ).show()
            expanded = false
            return
        }
        expanded = true
        panelView = view
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
            .setContentText("Tap the bubble for the GamersHub panel")
            .setSmallIcon(com.gamershub.R.drawable.ic_stat_gh)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 51
        private val runningState = androidx.compose.runtime.mutableStateOf(false)

        var isRunning: Boolean
            get() = runningState.value
            private set(value) {
                runningState.value = value
            }

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
            .border(1.dp, Color(0x4DFFFFFF), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        // Gamepad bubble (the reference uses a controller mark, not a logo).
        AIcon(
            Icons.Filled.SportsEsports,
            contentDescription = "GamersHub panel",
            tint = Color(0xFF17100A),
            modifier = Modifier.size(30.dp)
        )
    }
}

/* ------------------------------------------------------------------ */
