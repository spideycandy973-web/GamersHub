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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.gamershub.core.MacroKey
import com.gamershub.core.MappingStore
import com.gamershub.core.shizuku.Shell
import com.gamershub.theme.GameCornerTheme

/**
 * Mapping overlay — the real mapping editor.
 *
 * Every macro key is its own tiny overlay window (so the game underneath keeps
 * receiving touches everywhere else). Drag a key in EDIT mode to place it, tap
 * it in PLAY mode and the compiled `input` command is executed with shell
 * identity through Shizuku — the tap actually lands in the game.
 */
class MappingOverlayService : Service(), androidx.lifecycle.LifecycleOwner,
    androidx.savedstate.SavedStateRegistryOwner {

    private val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this)
    private val savedStateRegistryController =
        androidx.savedstate.SavedStateRegistryController.create(this)

    override val lifecycle: androidx.lifecycle.Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: androidx.savedstate.SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private val views = mutableListOf<ComposeView>()

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
        MappingStore.ensureSeeded(this)
        buildKeys()
        buildBar()
    }

    private fun overlayParams(flags: Int = 0): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or flags,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun buildKeys() {
        val metrics = resources.displayMetrics
        val keys = MappingStore.load(this)
        keys.forEach { key ->
            val params = overlayParams().apply {
                x = (key.x * metrics.widthPixels).toInt()
                y = (key.y * metrics.heightPixels).toInt()
            }
            val cv = ComposeView(this)
            cv.setContent {
                GameCornerTheme {
                    var edit by remember { mutableStateOf(MappingStore.editMode(this@MappingOverlayService)) }
                    var armed by remember { mutableStateOf(false) }
                    val accent = Color(0xFFE23A3A)
                    Box(
                        Modifier
                            .size(key.size.dp)
                            .clip(CircleShape)
                            .background(if (edit) Color(0x66E23A3A) else Color(0x33E23A3A))
                            .border(
                                width = 1.5.dp,
                                color = if (armed) Color.White else accent.copy(alpha = 0.85f),
                                shape = CircleShape
                            )
                            .pointerInput(edit) {
                                if (edit) {
                                    detectDragGestures(
                                        onDragEnd = {
                                            val m = resources.displayMetrics
                                            MappingStore.move(
                                                this@MappingOverlayService,
                                                key.id,
                                                (params.x.toFloat() / m.widthPixels).coerceIn(0f, 1f),
                                                (params.y.toFloat() / m.heightPixels).coerceIn(0f, 1f)
                                            )
                                        }
                                    ) { change, drag ->
                                        change.consume()
                                        params.x += drag.x.toInt()
                                        params.y += drag.y.toInt()
                                        runCatching { windowManager?.updateViewLayout(cv, params) }
                                    }
                                } else {
                                    detectTapGestures(
                                        onPress = { armed = true },
                                        onTap = {
                                            armed = false
                                            MappingStore.execute(this@MappingOverlayService, key)
                                        }
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                key.label,
                                color = Color.White,
                                fontSize = (key.size / 5).sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (edit) {
                                Text("MOVE", color = Color(0xCCFFFFFF), fontSize = 7.sp, letterSpacing = 1.sp)
                            }
                        }
                    }
                }
            }
            cv.setViewTreeLifecycleOwner(this)
            cv.setViewTreeSavedStateRegistryOwner(this)
            runCatching { windowManager?.addView(cv, params) }
            views.add(cv)
        }
    }

    private fun buildBar() {
        val params = overlayParams().apply {
            x = 24
            y = 120
        }
        val cv = ComposeView(this)
        cv.setContent {
            GameCornerTheme {
                var edit by remember { mutableStateOf(MappingStore.editMode(this@MappingOverlayService)) }
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xE6100B0C))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        "MAPPING",
                        color = Color(0xFFE23A3A),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                    Text(
                        if (edit) "EDIT" else "PLAY",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (edit) Color(0xFFB31217) else Color(0x33FFFFFF))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    edit = !edit
                                    MappingStore.setEditMode(this@MappingOverlayService, edit)
                                    restart(this@MappingOverlayService)
                                }
                            }
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                    Text(
                        "✕",
                        color = Color(0xCCFFFFFF),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .pointerInput(Unit) { detectTapGestures { stopSelf() } }
                    )
                }
            }
        }
        cv.setViewTreeLifecycleOwner(this)
        cv.setViewTreeSavedStateRegistryOwner(this)
        runCatching { windowManager?.addView(cv, params) }
        views.add(cv)
    }

    override fun onDestroy() {
        isRunning = false
        views.forEach { runCatching { windowManager?.removeView(it) } }
        views.clear()
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel("mapping", "Mapping overlay", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            android.app.Notification.Builder(this, "mapping")
        else @Suppress("DEPRECATION") android.app.Notification.Builder(this)
        return b.setContentTitle("Mapping active")
            .setContentText("GamersHub mapping overlay")
            .setSmallIcon(com.gamershub.R.drawable.ic_stat_gh)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 54

        private val runningState = androidx.compose.runtime.mutableStateOf(false)

        var isRunning: Boolean
            get() = runningState.value
            private set(value) {
                runningState.value = value
            }

        fun start(context: Context) {
            if (isRunning) return
            val intent = Intent(context, MappingOverlayService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            }.onFailure {
                com.gamershub.GamersHubApp.toast("Mapping couldn't start — check overlay/notification permission")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MappingOverlayService::class.java))
        }

        fun restart(context: Context) {
            stop(context)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                start(context)
            }, 350)
        }

        fun toggle(context: Context) {
            if (isRunning) stop(context) else start(context)
        }

        /** Injection self-test used by the mapping sheet. */
        fun testInject(context: Context): Boolean {
            val metrics = context.resources.displayMetrics
            return Shell.ok("input tap ${metrics.widthPixels / 2} ${metrics.heightPixels / 2}")
        }
    }
}
