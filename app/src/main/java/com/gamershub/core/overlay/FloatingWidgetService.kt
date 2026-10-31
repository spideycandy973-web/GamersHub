package com.gamershub.core.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.gamershub.GamersHubApp
import com.gamershub.MainActivity
import com.gamershub.core.boost.BoostState
import com.gamershub.core.notifications.NotificationHelper
import com.gamershub.core.record.RecorderController
import com.gamershub.core.settings.GestureAction
import com.gamershub.di.AppContainer
import com.gamershub.ui.theme.GhColors
import com.gamershub.ui.theme.GamersHubTheme
import com.gamershub.core.boost.ThermalProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The in-game floating companion:
 *  - **Bubble** — draggable, tap = open control panel, double-tap / long-press =
 *    configurable gestures (record toggle, boost toggle…)
 *  - **HUD** — live FPS / temps / CPU bars pinned to a corner
 *  - **Panel** — glass pop-up with every control, usable without leaving the game
 *  - **Chat** — streamer chat overlay with viewer messages
 *
 * Everything renders through one ComposeView-per-window hosted in WindowManager,
 * so there is no Activity and virtually zero overhead.
 */
class FloatingWidgetService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var container: AppContainer
    private lateinit var wm: WindowManager
    private val density by lazy { resources.displayMetrics.density }

    private val lifecycleOwner = ServiceLifecycleOwner()

    private var bubbleView: ComposeView? = null
    private var hudView: ComposeView? = null
    private var panelView: ComposeView? = null
    private var chatView: ComposeView? = null

    private val panelVisible = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()
        container = GamersHubApp.container(application as GamersHubApp)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        lifecycleOwner.onServiceCreate()

        val notif = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_CORE)
            .setContentTitle("GamersHub widget")
            .setContentText("Floating tools active")
            .setSmallIcon(com.gamershub.R.drawable.ic_stat_gh)
            .setSilent(true)
            .build()
        runCatching {
            ServiceCompat.startForeground(
                this, ID, notif,
                if (Build.VERSION.SDK_INT >= 34)
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            )
        }.onFailure {
            // If the platform refuses the FGS, never crash the app — just stop.
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleOwner.onServiceStart()
        runCatching { ensureWindows() }
            .onFailure {
                removeAll()
                stopSelf()
            }
        return START_STICKY
    }

    // ------------------------------------------------------------- windows

    private fun baseLp(flagsExtra: Int = 0): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or flagsExtra
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            flags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun newComposeView(content: @Composable () -> Unit): ComposeView {
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                GamersHubTheme(darkTheme = true, content = content)
            }
        }
    }

    private fun ensureWindows() {
        // addView on an overlay window without SYSTEM_ALERT_WINDOW throws
        // BadTokenException and used to take the whole app down with it.
        if (!android.provider.Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        if (bubbleView == null) {
            val lp = baseLp().apply {
                x = (16 * density).toInt()
                y = (220 * density).toInt()
            }
            bubbleView = newComposeView { BubbleContent(lp) }.also {
                wm.addView(it, lp)
            }
        }
        if (hudView == null) {
            val lp = baseLp(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE).apply {
                x = (12 * density).toInt(); y = (12 * density).toInt()
            }
            hudView = newComposeView { HudContent() }.also { wm.addView(it, lp) }
        }
        if (panelView == null) {
            val lp = baseLp().apply {
                gravity = Gravity.CENTER
                if (Build.VERSION.SDK_INT >= 31) {
                    flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    blurBehindRadius = 24
                }
            }
            panelView = newComposeView { PanelContent() }.also { wm.addView(it, lp) }
        }
        if (chatView == null) {
            val lp = baseLp(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                x = (10 * density).toInt(); y = (140 * density).toInt()
            }
            chatView = newComposeView { ChatOverlayContent() }.also { wm.addView(it, lp) }
        }
    }

    private fun removeAll() {
        listOfNotNull(bubbleView, hudView, panelView, chatView).forEach {
            runCatching { wm.removeView(it) }
        }
        bubbleView = null; hudView = null; panelView = null; chatView = null
    }

    // ---------------------------------------------------------------- bubble

    private fun runGesture(action: GestureAction) {
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.TOGGLE_RECORD ->
                scope.launch(Dispatchers.IO) { container.recorder.toggleRecording() }
            GestureAction.TOGGLE_BOOST ->
                scope.launch(Dispatchers.IO) {
                    container.boost.toggleBoost(container.boost.defaultPlan())
                }
            GestureAction.OPEN_PANEL -> panelVisible.value = !panelVisible.value
        }
    }

    @Composable
    private fun BubbleContent(lp: WindowManager.LayoutParams) {
        val enabled by container.settings.overlayBubble.collectAsStateWithLifecycle(true)
        val doubleTap by container.settings.gestureDoubleTap.collectAsStateWithLifecycle(
            GestureAction.TOGGLE_RECORD
        )
        val longPress by container.settings.gestureTripleTap.collectAsStateWithLifecycle(
            GestureAction.TOGGLE_BOOST
        )
        val recState by container.recorder.state.collectAsStateWithLifecycle()
        val boostState by container.boost.state.collectAsStateWithLifecycle()
        val recording = recState is RecorderController.State.Active
        val boosting = boostState is BoostState.Active

        if (!enabled) return

        Box(
            modifier = Modifier
                .size(58.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { panelVisible.value = !panelVisible.value },
                        onDoubleTap = { runGesture(doubleTap) },
                        onLongPress = { runGesture(longPress) },
                        onPress = {}
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        lp.x += drag.x.toInt()
                        lp.y += drag.y.toInt()
                        val v = bubbleView
                        if (v != null) runCatching { wm.updateViewLayout(v, lp) }
                    }
                }
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        if (recording) listOf(GhColors.Red, GhColors.Magenta)
                        else if (boosting) listOf(GhColors.Cyber, GhColors.Violet)
                        else listOf(Color(0xFF1E293B), Color(0xFF0B1020))
                    )
                )
                .border(
                    2.dp,
                    Brush.linearGradient(listOf(GhColors.Cyber, GhColors.Violet)),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "GH",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }

    // ------------------------------------------------------------------- HUD

    @Composable
    private fun HudContent() {
        val enabled by container.settings.overlayHud.collectAsStateWithLifecycle(true)
        val stats by container.monitor.stats.collectAsStateWithLifecycle()
        if (!enabled) return

        Column(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x990B1020))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (stats.fps >= 0) "${stats.fps.toInt()}" else "--",
                    style = MaterialTheme.typography.headlineMedium,
                    color = fpsHudColor(stats.fps)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "FPS", style = MaterialTheme.typography.labelSmall,
                    color = Color(0x99FFFFFF)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HudChip("⚡ ${stats.refreshRateHz}Hz")
                if (stats.batteryTempC > 0) {
                    HudChip("🔋 ${"%.0f".format(stats.batteryTempC)}°")
                }
                if (stats.cpuTempC > 0) {
                    HudChip("🧊 ${"%.0f".format(stats.cpuTempC)}°")
                }
                if (stats.cpuUsagePct >= 0) {
                    HudChip("CPU ${stats.cpuUsagePct.toInt()}%")
                }
            }
        }
    }

    private fun fpsHudColor(fps: Float): Color = when {
        fps < 0 -> Color(0x99FFFFFF)
        fps >= 55 -> GhColors.Green
        fps >= 35 -> GhColors.Amber
        else -> GhColors.Red
    }

    @Composable
    private fun HudChip(text: String) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color(0xCCFFFFFF))
    }

    // ----------------------------------------------------------------- panel

    @Composable
    private fun PanelContent() {
        val visible by panelVisible.collectAsStateWithLifecycle()
        if (!visible) return

        val boostState by container.boost.state.collectAsStateWithLifecycle()
        val recState by container.recorder.state.collectAsStateWithLifecycle()
        val thermalProfile by container.thermal.activeProfile.collectAsStateWithLifecycle()
        val bypass by container.battery.bypassEnabled.collectAsStateWithLifecycle()

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                    scaleIn(spring(stiffness = Spring.StiffnessMedium), initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f)
        ) {
            Column(
                Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xD90D1322))
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(Color(0x66FFFFFF), Color(0x14FFFFFF))
                        ),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "GAMERSHUB",
                        style = MaterialTheme.typography.labelLarge,
                        color = GhColors.Cyber
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "✕",
                        color = Color(0x99FFFFFF),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { panelVisible.value = false }
                            .padding(6.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))

                // Boost + Record + Stream trio
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PanelButton(
                        "⚡ Boost",
                        boostState is BoostState.Active
                    ) {
                        scope.launch(Dispatchers.IO) {
                            container.boost.toggleBoost(container.boost.defaultPlan())
                        }
                    }
                    PanelButton(
                        "⏺ Rec",
                        recState is RecorderController.State.Active &&
                                (recState as? RecorderController.State.Active)?.mode !=
                                com.gamershub.core.record.RecorderConfig.Mode.STREAM
                    ) {
                        scope.launch(Dispatchers.IO) { container.recorder.toggleRecording() }
                    }
                    PanelButton(
                        "📡 Live",
                        recState is RecorderController.State.Active &&
                                (recState as? RecorderController.State.Active)?.mode !=
                                com.gamershub.core.record.RecorderConfig.Mode.RECORD
                    ) {
                        scope.launch(Dispatchers.IO) {
                            if (recState is RecorderController.State.Active) container.recorder.stop()
                            else container.recorder.requestStart(
                                com.gamershub.core.record.RecorderConfig.Mode.STREAM
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Thermal profile cycle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Thermal", style = MaterialTheme.typography.labelSmall,
                        color = Color(0x99FFFFFF)
                    )
                    Spacer(Modifier.weight(1f))
                    PanelButton(thermalProfile.name, active = false) {
                        scope.launch(Dispatchers.IO) {
                            val all = ThermalProfile.entries
                            val next = all[(all.indexOf(thermalProfile) + 1) % all.size]
                            container.thermal.setProfile(next)
                            container.thermal.enforceNow()
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Bypass charging", style = MaterialTheme.typography.labelSmall,
                        color = Color(0x99FFFFFF)
                    )
                    Spacer(Modifier.weight(1f))
                    PanelButton(
                        if (bypass == true) "ON" else "OFF",
                        active = bypass == true
                    ) {
                        scope.launch(Dispatchers.IO) {
                            container.battery.setBypassCharging(bypass != true)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "App", style = MaterialTheme.typography.labelSmall,
                        color = Color(0x99FFFFFF)
                    )
                    Spacer(Modifier.weight(1f))
                    PanelButton("Open GamersHub", active = false) {
                        startActivity(
                            Intent(this@FloatingWidgetService, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun PanelButton(
        text: String,
        active: Boolean,
        onClick: () -> Unit
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (active)
                        Brush.horizontalGradient(listOf(GhColors.Cyber, GhColors.Violet))
                    else
                        Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF)))
                )
                .border(
                    1.dp,
                    if (active) Color.Transparent else Color(0x33FFFFFF),
                    RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) Color(0xFF04121A) else Color(0xE6FFFFFF)
            )
        }
    }

    // ------------------------------------------------------------------ chat

    @Composable
    private fun ChatOverlayContent() {
        val chatEnabled by container.settings.overlayChat.collectAsStateWithLifecycle(true)
        val chatActive by container.chat.active.collectAsStateWithLifecycle()
        val messages by container.chat.messages.collectAsStateWithLifecycle()

        if (!chatEnabled || !chatActive || messages.isEmpty()) return

        Column(
            Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x990B1020))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            Text(
                "LIVE CHAT", style = MaterialTheme.typography.labelSmall,
                color = GhColors.Magenta
            )
            Spacer(Modifier.height(6.dp))
            messages.takeLast(8).forEach { msg ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(
                        "${platformBadge(msg.platform)} ${msg.author}: ",
                        style = MaterialTheme.typography.labelSmall,
                        color = platformColor(msg.platform)
                    )
                    Text(
                        msg.text.take(120),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xE6FFFFFF)
                    )
                }
            }
        }
    }

    private fun platformBadge(id: String) = when (id) {
        "youtube" -> "▶"
        "facebook" -> "ⓕ"
        "instagram" -> "◎"
        else -> "💬"
    }

    private fun platformColor(id: String) = when (id) {
        "youtube" -> Color(0xFFFF5252)
        "facebook" -> Color(0xFF579BFF)
        "instagram" -> Color(0xFFE178D9)
        else -> GhColors.Cyber
    }

    // ------------------------------------------------------------------ misc

    override fun onDestroy() {
        removeAll()
        lifecycleOwner.onServiceDestroy()
        scope.cancel()
        isRunning.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ID = 48152

        val isRunning = MutableStateFlow(false)

        fun start(context: Context) {
            if (isRunning.value) return
            if (!android.provider.Settings.canDrawOverlays(context)) {
                isRunning.value = false
                return
            }
            isRunning.value = true
            val started = runCatching {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(
                        Intent(context, FloatingWidgetService::class.java)
                    )
                } else {
                    context.startService(Intent(context, FloatingWidgetService::class.java))
                }
                true
            }.getOrDefault(false)
            // Android 12+ blocks FGS starts from the background — never crash.
            if (!started) isRunning.value = false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingWidgetService::class.java))
            isRunning.value = false
        }
    }
}
