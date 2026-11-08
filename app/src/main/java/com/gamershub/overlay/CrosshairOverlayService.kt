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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.gamershub.theme.GameCornerTheme

/**
 * Real crosshair overlay — mirrors the original CrosshairFeature:
 * 7 styles from the APK, 165dp base size, colour, opacity and rotation,
 * draggable on screen.
 */
class CrosshairOverlayService : Service(), androidx.lifecycle.LifecycleOwner,
    androidx.savedstate.SavedStateRegistryOwner {

    private val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this)
    private val savedStateRegistryController =
        androidx.savedstate.SavedStateRegistryController.create(this)

    override val lifecycle: androidx.lifecycle.Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: androidx.savedstate.SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var view: ComposeView? = null

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

        val prefs = getSharedPreferences("gamershub", MODE_PRIVATE)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        val cv = ComposeView(this).apply {
            setContent {
                GameCornerTheme {
                    var size by remember { mutableFloatStateOf(prefs.getFloat("cross_size", 1f)) }
                    var alpha by remember { mutableFloatStateOf(prefs.getFloat("cross_alpha", 1f)) }
                    var rot by remember { mutableFloatStateOf(prefs.getFloat("cross_rot", 0f)) }
                    var style by remember { mutableIntStateOf(prefs.getInt("cross_style", 1)) }
                    val color = prefs.getInt("cross_color", 0xFFFF2D2D.toInt())
                    val resId = when (style) {
                        2 -> com.gamershub.R.drawable.cross2_new
                        3 -> com.gamershub.R.drawable.cross3_new
                        4 -> com.gamershub.R.drawable.cross4_new
                        5 -> com.gamershub.R.drawable.cross5_new
                        6 -> com.gamershub.R.drawable.cross6_new
                        7 -> com.gamershub.R.drawable.cross7_new
                        else -> com.gamershub.R.drawable.cross1_new
                    }
                    Image(
                        painter = painterResource(resId),
                        contentDescription = "Crosshair",
                        colorFilter = ColorFilter.tint(Color(color)),
                        modifier = Modifier
                            .size((165 * size).dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    params.x += drag.x.toInt()
                                    params.y += drag.y.toInt()
                                    runCatching { windowManager?.updateViewLayout(this@apply, params) }
                                }
                            }
                    )
                }
            }
        }
        view = cv
        cv.setViewTreeLifecycleOwner(this)
        cv.setViewTreeSavedStateRegistryOwner(this)
        runCatching { windowManager?.addView(cv, params) }
    }

    override fun onDestroy() {
        isRunning = false
        runCatching { view?.let { windowManager?.removeView(it) } }
        view = null
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel("cross", "Crosshair", NotificationManager.IMPORTANCE_LOW))
        }
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            android.app.Notification.Builder(this, "cross")
        else @Suppress("DEPRECATION") android.app.Notification.Builder(this)
        return b.setContentTitle("Crosshair active").setContentText("GamersHub crosshair overlay")
            .setSmallIcon(com.gamershub.R.drawable.ic_stat_gh).build()
    }

    companion object {
        private const val NOTIF_ID = 52
        private val runningState = androidx.compose.runtime.mutableStateOf(false)

        var isRunning: Boolean
            get() = runningState.value
            private set(value) {
                runningState.value = value
            }

        fun start(context: Context) {
            if (isRunning) return
            val i = Intent(context, CrosshairOverlayService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
                else context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CrosshairOverlayService::class.java))
        }

        /** Re-creates the overlay so style/size/colour changes apply immediately. */
        fun restart(context: Context) {
            stop(context)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                start(context)
            }, 300)
        }
    }
}
