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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
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
import com.gamershub.core.FpsMeter
import com.gamershub.core.MonitorPrefs
import com.gamershub.data.DeviceStats
import com.gamershub.theme.GameCornerTheme
import kotlinx.coroutines.delay

/**
 * Floating monitor — the real "Information Monitor".
 *
 * Every field is read from the device: FPS from the game's gfxinfo frame
 * counter, CPU from cpufreq, RAM from ActivityManager, temperature and battery
 * from the battery service, ping from a TCP probe, clock from the system.
 * Fields, opacity and scale come from MonitorPrefs.
 */
class MonitorOverlayService : Service(), androidx.lifecycle.LifecycleOwner,
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

    private data class Live(
        val fps: String = "--",
        val cpu: String = "--",
        val gpu: String = "--",
        val ram: String = "--",
        val temp: String = "--",
        val batt: String = "--",
        val ping: String = "--",
        val hz: String = "--",
        val clock: String = "--:--"
    )

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
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels - 168).coerceAtLeast(12)
            y = 150
        }

        val cv = ComposeView(this).apply {
            setContent {
                GameCornerTheme {
                    var scale by remember { mutableFloatStateOf(MonitorPrefs.scale(this@MonitorOverlayService)) }
                    val fields = remember { MonitorPrefs.fields(this@MonitorOverlayService) }
                    val live by produceState(initialValue = Live()) {
                        var lastFrames: Long? = null
                        var lastStamp = 0L
                        while (true) {
                            runCatching {
                                val ctx = this@MonitorOverlayService
                                val cpu = DeviceStats.cpu()
                                val mem = DeviceStats.mem(ctx)
                                val batt = DeviceStats.battery(ctx)
                                val hz = FpsMeter.refreshRate(ctx)
                                val pkg = FpsMeter.foregroundPackage(ctx)
                                val frames = pkg?.let { FpsMeter.totalFrames(it) }
                                val now = System.currentTimeMillis()
                                var fps = "--"
                                if (frames != null && lastFrames != null && lastStamp > 0) {
                                    val dt = (now - lastStamp) / 1000.0
                                    if (dt > 0.2) {
                                        val value = ((frames - (lastFrames ?: 0L)) / dt).toInt()
                                        if (value in 0..240) fps = value.toString()
                                    }
                                }
                                if (frames != null) {
                                    lastFrames = frames
                                    lastStamp = now
                                }
                                val ping = DeviceStats.pingMs()
                                val gpu = FpsMeter.gpuLoad()
                                value = Live(
                                    fps = fps,
                                    cpu = "${cpu.pct}%",
                                    gpu = if (gpu >= 0) "$gpu%" else "--",
                                    ram = "${mem.pct}%",
                                    temp = "${batt.second.toInt()}°",
                                    batt = "${batt.first}%",
                                    ping = if (ping >= 0) "$ping ms" else "--",
                                    hz = "$hz Hz",
                                    clock = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                                        .format(java.util.Date())
                                )
                                scale = MonitorPrefs.scale(ctx)
                            }
                            delay(1000)
                        }
                    }
                    val order = MonitorPrefs.ORDER.filter { fields.contains(it) }
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xE6100B0C))
                            .padding(horizontal = 9.dp, vertical = 7.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    val metrics = resources.displayMetrics
                                    params.x = (params.x + drag.x.toInt())
                                        .coerceIn(0, (metrics.widthPixels - 60).coerceAtLeast(0))
                                    params.y = (params.y + drag.y.toInt())
                                        .coerceIn(0, (metrics.heightPixels - 60).coerceAtLeast(0))
                                    runCatching { windowManager?.updateViewLayout(this@apply, params) }
                                }
                            }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "MONITOR",
                                color = Color(0xFFE23A3A),
                                fontSize = (9 * scale).sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .size((13 * scale).dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x33FFFFFF))
                                    .clickable { stopSelf() },
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.foundation.Image(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close monitor",
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White),
                                    modifier = Modifier.size((9 * scale).dp)
                                )
                            }
                        }
                        Spacer(Modifier.size(3.dp))
                        order.forEach { field ->
                            val label = MonitorPrefs.LABELS[field] ?: field.uppercase()
                            val raw = when (field) {
                                "fps" -> live.fps
                                "cpu" -> live.cpu
                                "gpu" -> live.gpu
                                "ram" -> live.ram
                                "temp" -> live.temp
                                "batt" -> live.batt
                                "ping" -> live.ping
                                "hz" -> live.hz
                                else -> live.clock
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.width((104 * scale).dp)
                            ) {
                                Text(
                                    label,
                                    color = Color(0x99FFFFFF),
                                    fontSize = (9 * scale).sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    raw,
                                    color = MonitorPrefs.colorFor(field),
                                    fontSize = (11 * scale).sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
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
            nm.createNotificationChannel(
                NotificationChannel("monitor", "Floating monitor", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            android.app.Notification.Builder(this, "monitor")
        else @Suppress("DEPRECATION") android.app.Notification.Builder(this)
        return b.setContentTitle("Monitor active")
            .setContentText("GamersHub live monitor")
            .setSmallIcon(com.gamershub.R.drawable.ic_stat_gh)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 53

        private val runningState = androidx.compose.runtime.mutableStateOf(false)

        var isRunning: Boolean
            get() = runningState.value
            private set(value) {
                runningState.value = value
            }

        fun start(context: Context) {
            if (isRunning) return
            val intent = Intent(context, MonitorOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorOverlayService::class.java))
        }

        fun toggle(context: Context) {
            if (isRunning) stop(context) else start(context)
        }

        fun restart(context: Context) {
            if (!isRunning) return
            stop(context)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                start(context)
            }, 350)
        }
    }
}
