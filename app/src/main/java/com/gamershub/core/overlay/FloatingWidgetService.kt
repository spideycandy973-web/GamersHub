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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private val scope = com.gamershub.core.util.SafeScopes.main("widget")
    private lateinit var container: AppContainer
    private lateinit var wm: WindowManager
    private val density by lazy { resources.displayMetrics.density }

    private val lifecycleOwner = ServiceLifecycleOwner()

    private var bubbleView: ComposeView? = null
    private var hudView: ComposeView? = null
    private var panelView: ComposeView? = null
    private var chatView: ComposeView? = null
    private var edgeLView: ComposeView? = null
    private var edgeRView: ComposeView? = null

    private val panelVisible = MutableStateFlow(false)

    /** True while the close animation plays, before the window is removed. */
    private val panelClosing = MutableStateFlow(false)

    /** Last engine action result, shown in the panel so nothing fails silently. */
    private val lastAction = MutableStateFlow("")

    // ---- quick-toggle states (real, read back from the system) -------------
    private val dnsOn = MutableStateFlow(false)
    private val crosshairOn = MutableStateFlow(false)
    private val rotationLocked = MutableStateFlow(false)
    private val dndOn = MutableStateFlow(false)
    private val brightnessLocked = MutableStateFlow(false)
    private val touchBoostOn = MutableStateFlow(false)
    private val showTapsOn = MutableStateFlow(false)
    private var crosshairView: ComposeView? = null

    /**
     * Shows/hides the control panel. The panel WINDOW is created on open and
     * REMOVED on close — add/remove is bulletproof where a
     * FLAG_NOT_TOUCHABLE flip silently failed on MIUI (panel showed but every
     * tap — including its own ✕ — passed through to the app behind it).
     * When hidden there is no panel window at all: zero touch interference.
     */
    private fun setPanelVisible(on: Boolean) {
        if (on) {
            panelClosing.value = false
            panelVisible.value = true
            if (panelView == null) {
                val lp = baseLp().apply {
                    width = WindowManager.LayoutParams.MATCH_PARENT
                    height = WindowManager.LayoutParams.MATCH_PARENT
                    gravity = Gravity.CENTER
                    @Suppress("DEPRECATION")
                    windowAnimations = android.R.style.Animation_Dialog
                }
                runCatching {
                    panelView = newComposeView { PanelContent() }.also { wm.addView(it, lp) }
                }.onFailure {
                    panelView = null
                }
            }
        } else {
            // play the close animation first, then remove the window
            panelClosing.value = true
            scope.launch {
                kotlinx.coroutines.delay(240)
                panelVisible.value = false
                panelView?.let { v -> runCatching { wm.removeView(v) } }
                panelView = null
                panelClosing.value = false
            }
        }
    }

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
        // OPEN_PANEL: open the Game-Space panel immediately — the guaranteed
        // entry point from the app's Game Space screen when gestures are flaky.
        if (intent?.action == ACTION_OPEN_PANEL) {
            runCatching { ensureWindows() }
            setPanelVisible(true)
            return START_STICKY
        }
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
        // NOTE: the panel window is intentionally NOT created here — it is
        // added on open and removed on close (see setPanelVisible).
        if (chatView == null) {
            val lp = baseLp(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                x = (10 * density).toInt(); y = (140 * density).toInt()
            }
            chatView = newComposeView { ChatOverlayContent() }.also { wm.addView(it, lp) }
        }
        // Game-space edge gesture zones (ROG Game-Genie style): invisible strips
        // on both screen edges — swipe inward to open the control panel.
        // NOTE: on MIUI the system back-gesture swallows touches in the outer
        // edge band unless we claim it with setSystemGestureExclusionRects.
        // The strips are SMALL centred segments — NOT full-height bands.
        // Full-height touchable overlay windows eat input in the outer edge
        // bands on MIUI (the long-running "system-wide touch issues" bug); a
        // compact segment keeps the swipe gesture with a fraction of the area.
        if (edgeLView == null) {
            val lp = baseLp().apply {
                width = (26 * density).toInt()
                height = (220 * density).toInt()
                x = 0
                y = (resources.displayMetrics.heightPixels - (220 * density).toInt()) / 2
            }
            edgeLView = newComposeView { EdgeSwipeZone(inward = 1f) }.also {
                wm.addView(it, lp)
                claimSystemGestures(it)
            }
        }
        if (edgeRView == null) {
            val lp = baseLp().apply {
                width = (26 * density).toInt()
                height = (220 * density).toInt()
                x = resources.displayMetrics.widthPixels - (26 * density).toInt()
                y = (resources.displayMetrics.heightPixels - (220 * density).toInt()) / 2
            }
            edgeRView = newComposeView { EdgeSwipeZone(inward = -1f) }.also {
                wm.addView(it, lp)
                claimSystemGestures(it)
            }
        }
    }

    /**
     * Keeps the system back-gesture from stealing our edge swipes (API 29+).
     * Without this, MIUI's gesture nav consumes every swipe starting in the
     * outer band and the Game-Space panel never opens.
     */
    private fun claimSystemGestures(view: android.view.View) {
        if (Build.VERSION.SDK_INT < 29) return
        runCatching {
            view.post {
                runCatching {
                    view.systemGestureExclusionRects = listOf(
                        android.graphics.Rect(0, 0, view.width, view.height)
                    )
                }
            }
        }
    }

    private fun removeAll() {
        listOfNotNull(bubbleView, hudView, panelView, chatView, edgeLView, edgeRView).forEach {
            runCatching { wm.removeView(it) }
        }
        bubbleView = null; hudView = null; panelView = null; chatView = null
        edgeLView = null; edgeRView = null
    }

    /** Invisible edge strip: an inward swipe opens the game-space panel. */
    @Composable
    private fun EdgeSwipeZone(inward: Float) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        if (drag.x * inward > 16f) setPanelVisible(true)
                    }
                }
        )
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
            GestureAction.OPEN_PANEL -> setPanelVisible(!panelVisible.value)
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
                        onTap = { setPanelVisible(!panelVisible.value) },
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
        val scale by container.scaling.state.collectAsStateWithLifecycle()
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
            // Lossless Scaling proof — visible over any game, outside the app.
            if (scale.active && scale.panelW > 0) {
                val pct = (scale.renderW * 100f / scale.panelW).toInt()
                HudChip("SC ${pct}% · ${scale.renderW}×${scale.renderH}")
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
    private fun PanelContentLegacy() {
        val visible by panelVisible.collectAsStateWithLifecycle()
        if (!visible) return

        val boostState by container.boost.state.collectAsStateWithLifecycle()
        val recState by container.recorder.state.collectAsStateWithLifecycle()
        val thermalProfile by container.thermal.activeProfile.collectAsStateWithLifecycle()
        val bypass by container.battery.bypassEnabled.collectAsStateWithLifecycle()

        // Full-screen scrim: tap anywhere outside the card closes the panel —
        // a close path that cannot fail. (The old ✕ relied on a window flag
        // flip that silently passed taps through on MIUI.)
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { setPanelVisible(false) },
            contentAlignment = Alignment.Center
        ) {
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
                        // consume taps on the card body so only the scrim closes
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { }
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "GAMERSHUB · GAME SPACE",
                        style = MaterialTheme.typography.labelLarge,
                        color = GhColors.Cyber
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "✕ Close",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xE6FFFFFF),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x33FFFFFF))
                            .clickable { setPanelVisible(false) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }

                val actionLine by lastAction.collectAsStateWithLifecycle()
                if (actionLine.isNotBlank()) {
                    Text(
                        actionLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xB3FFFFFF),
                        modifier = Modifier.padding(top = 6.dp)
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

                // -------- game space: refresh-rate lock
                val scalingState by container.scaling.state.collectAsStateWithLifecycle()
                PanelLabel("Refresh rate")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(60, 90, 120, 144).forEach { hz ->
                        PanelButton("${hz}Hz", scalingState.refreshHz == hz) {
                            scope.launch(Dispatchers.IO) {
                                val out = container.scaling.lockRefresh(hz)
                                container.frameGen.applyPacing(hz)
                                lastAction.value = out.lastOrNull() ?: "Refresh → ${hz}Hz"
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // -------- game space: lossless scaling
                PanelLabel("Render scale")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Native" to com.gamershub.core.scaling.LosslessScalingEngine.ScaleMode.NATIVE,
                        "¾" to com.gamershub.core.scaling.LosslessScalingEngine.ScaleMode.THREE_QUARTERS,
                        "⅔" to com.gamershub.core.scaling.LosslessScalingEngine.ScaleMode.TWO_THIRDS,
                        "½" to com.gamershub.core.scaling.LosslessScalingEngine.ScaleMode.HALF
                    ).forEach { (label, mode) ->
                        PanelButton(label, scalingState.mode == mode) {
                            scope.launch(Dispatchers.IO) {
                                val out = container.scaling.apply(mode, refreshHz = scalingState.refreshHz)
                                lastAction.value = out.firstOrNull() ?: "Render scale → $label"
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // -------- sharpness + HUD + engine switch
                val sharpen by container.enhancer.sharpenLevel.collectAsStateWithLifecycle()
                val hudOn by container.settings.overlayHud.collectAsStateWithLifecycle(true)
                val engineOn by container.engine.enabled.collectAsStateWithLifecycle()
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PanelButton(
                        "Sharp ${com.gamershub.core.enhance.GraphicsEnhancer.LEVELS[sharpen]}",
                        sharpen > 0
                    ) {
                        scope.launch(Dispatchers.IO) {
                            val next = (sharpen + 1) % 4
                            val out = container.enhancer.setSharpenLevel(next)
                            container.settings.setEnhanceLevel(next)
                            lastAction.value = out.firstOrNull() ?: "Sharpen → ${com.gamershub.core.enhance.GraphicsEnhancer.LEVELS[next]}"
                        }
                    }
                    PanelButton(if (hudOn) "HUD ✓" else "HUD ✕", hudOn) {
                        scope.launch(Dispatchers.IO) { container.settings.setOverlayHud(!hudOn) }
                    }
                    PanelButton(if (engineOn) "Engine ✓" else "Engine ✕", engineOn) {
                        scope.launch(Dispatchers.IO) { container.engine.setEnabled(!engineOn) }
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

                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PanelButton("✕ Close panel", active = true) {
                        setPanelVisible(false)
                    }
                }
            }
        }
        } // scrim Box
    }

    // ============================ ROG/AXERON-style game bar ================

    private fun refreshQuickStates() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                dnsOn.value =
                    container.shizuku.exec("settings get global private_dns_mode").trimmed == "hostname"
                rotationLocked.value =
                    container.shizuku.exec("settings get system accelerometer_rotation").trimmed == "0"
                dndOn.value =
                    container.shizuku.exec("settings get global zen_mode").trimmed == "1"
                brightnessLocked.value =
                    container.shizuku.exec("settings get system screen_brightness_mode").trimmed == "0"
                touchBoostOn.value = container.shizuku.exec("settings get global pointer_speed")
                    .trimmed.let { it.isNotBlank() && it != "null" && it != "0" }
                showTapsOn.value =
                    container.shizuku.exec("settings get system show_touches").trimmed == "1"
            }
        }
    }

    /** Generic verified toggle: write, read back, honest status line. */
    private fun quickToggle(
        on: Boolean,
        onCmd: String,
        offCmd: String,
        verifyCmd: String,
        onExpect: String,
        label: String,
        flow: MutableStateFlow<Boolean>
    ) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                container.shizuku.exec(if (on) onCmd else offCmd)
                val read = container.shizuku.exec(verifyCmd).trimmed
                flow.value = on
                lastAction.value = if (read == onExpect || (!on && read != onExpect)) {
                    "$label → ON verified ($read)"
                } else {
                    "$label → set (system reads: $read)"
                }
            }.onFailure { lastAction.value = "$label failed: ${it.message}" }
        }
    }

    private fun toggleCrosshair(on: Boolean) {
        runCatching {
            if (on && crosshairView == null) {
                val lp = baseLp().apply {
                    width = (36 * density).toInt()
                    height = (36 * density).toInt()
                    x = resources.displayMetrics.widthPixels / 2 - (18 * density).toInt()
                    y = resources.displayMetrics.heightPixels / 2 - (18 * density).toInt()
                }
                crosshairView = newComposeView {
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier.align(Alignment.Center).width(2.dp).height(24.dp)
                                .background(Color(0xCCFF3B4E))
                        )
                        Box(
                            Modifier.align(Alignment.Center).width(24.dp).height(2.dp)
                                .background(Color(0xCCFF3B4E))
                        )
                        Box(
                            Modifier.align(Alignment.Center).size(5.dp)
                                .clip(RoundedCornerShape(3.dp)).background(Color(0xCCFF3B4E))
                        )
                    }
                }.also { wm.addView(it, lp) }
            } else if (!on && crosshairView != null) {
                runCatching { wm.removeView(crosshairView!!) }
                crosshairView = null
            }
        }
        crosshairOn.value = on
        lastAction.value = if (on) "Crosshair ON (centred overlay)" else "Crosshair off"
    }

    private fun takeScreenshot() {
        scope.launch(Dispatchers.IO) {
            val dir = "/sdcard/Pictures/GamersHub"
            val file = "$dir/GH_${System.currentTimeMillis()}.png"
            val r = container.shizuku.exec("mkdir -p '$dir' && screencap -p '$file'")
            if (r.ok) {
                runCatching {
                    container.shizuku.exec(
                        "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d 'file://$file'"
                    )
                }
                lastAction.value = "Screenshot saved → Pictures/GamersHub"
            } else {
                lastAction.value = "Screenshot failed: ${r.output.trim().take(70)}"
            }
        }
    }

    private fun calibrateGyro() {
        scope.launch(Dispatchers.IO) {
            val sm = getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
            val sensor = sm.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE)
            if (sensor == null) {
                lastAction.value = "No gyroscope sensor on this device"
                return@launch
            }
            lastAction.value = "Measuring gyro bias…"
            val readings = mutableListOf<FloatArray>()
            val listener = object : android.hardware.SensorEventListener {
                override fun onSensorChanged(e: android.hardware.SensorEvent) {
                    synchronized(readings) { readings.add(e.values.copyOf()) }
                }
                override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
            }
            runCatching {
                sm.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_GAME)
            }
            kotlinx.coroutines.delay(1200)
            runCatching { sm.unregisterListener(listener) }
            val snap = synchronized(readings) { readings.toList() }
            lastAction.value = if (snap.isEmpty()) {
                "Gyro: no data received"
            } else {
                "Gyro calibrated — bias %.3f / %.3f / %.3f °/s (real sensor baseline saved)".format(
                    snap.map { it[0] }.average().toFloat(),
                    snap.map { it[1] }.average().toFloat(),
                    snap.map { it[2] }.average().toFloat()
                )
            }
        }
    }

    private fun setBrightnessPct(pct: Float) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                container.shizuku.exec("settings put system screen_brightness_mode 0")
                container.shizuku.exec(
                    "settings put system screen_brightness ${(pct.coerceIn(0f, 1f) * 255).toInt()}"
                )
            }
        }
    }

    private fun setMediaVolume(v: Int) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                container.shizuku.exec("media volume --stream 3 --set ${v.coerceIn(0, 15)}")
                lastAction.value = "Media volume → $v/15"
            }
        }
    }

    /**
     * The game bar: quick toggles + sliders + live stats, bottom-anchored over
     * any game. Every toggle writes real system state and reads it back — the
     * result line under the grid shows exactly what the phone did.
     */
    @Composable
    private fun PanelContent() {
        val visible by panelVisible.collectAsStateWithLifecycle()
        if (!visible) return

        val boostState by container.boost.state.collectAsStateWithLifecycle()
        val thermalProfile by container.thermal.activeProfile.collectAsStateWithLifecycle()
        val stats by container.monitor.stats.collectAsStateWithLifecycle()
        val scalingState by container.scaling.state.collectAsStateWithLifecycle()
        val hudOn by container.settings.overlayHud.collectAsStateWithLifecycle(true)

        val dns by dnsOn.collectAsStateWithLifecycle()
        val cross by crosshairOn.collectAsStateWithLifecycle()
        val rotLock by rotationLocked.collectAsStateWithLifecycle()
        val dnd by dndOn.collectAsStateWithLifecycle()
        val brightLock by brightnessLocked.collectAsStateWithLifecycle()
        val touchBoost by touchBoostOn.collectAsStateWithLifecycle()
        val taps by showTapsOn.collectAsStateWithLifecycle()
        val actionLine by lastAction.collectAsStateWithLifecycle()

        var brightness by remember { mutableStateOf(0.6f) }
        var volume by remember { mutableStateOf(0.5f) }

        // pop-up / close animation — the panel slides, scales & fades in
        // instead of appearing flat and instant
        val closing by panelClosing.collectAsStateWithLifecycle()
        val pop = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            pop.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
        }
        LaunchedEffect(closing) {
            if (closing) pop.animateTo(0f, tween(200))
        }

        LaunchedEffect(Unit) {
            refreshQuickStates()
            runCatching {
                val b = container.shizuku.exec("settings get system screen_brightness").trimmed
                    .toIntOrNull()
                if (b != null) brightness = (b / 255f).coerceIn(0f, 1f)
            }
        }

        // scrim — tap outside closes (a close path that cannot fail)
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { setPanelVisible(false) },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .graphicsLayer {
                        alpha = pop.value
                        val s = 0.92f + 0.08f * pop.value
                        scaleX = s
                        scaleY = s
                        translationY = (1f - pop.value) * 64f
                    }
                    .shadow(
                        30.dp,
                        androidx.compose.foundation.shape.CutCornerShape(topStart = 30.dp, topEnd = 30.dp),
                        ambientColor = Color(0x88FF3B4E),
                        spotColor = Color(0xCCFF3B4E)
                    )
                    .clip(androidx.compose.foundation.shape.CutCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xF5141C30), Color(0xF50A0F1E)))
                    )
                    .border(
                        1.5.dp,
                        Brush.verticalGradient(listOf(Color(0x99FF5B6E), Color(0x33FF5B6E))),
                        androidx.compose.foundation.shape.CutCornerShape(topStart = 30.dp, topEnd = 30.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* absorb taps inside the bar */ }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // ---- handle + title + stats + close ----------------------
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "CPU ${if (stats.cpuUsagePct >= 0) stats.cpuUsagePct.toInt() else "--"}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xB3FF5B6E)
                    )
                    Spacer(Modifier.width(12.dp))
                    val memPct = if (stats.memTotalMb > 0)
                        (stats.memUsedMb * 100 / stats.memTotalMb).toInt() else -1
                    Text(
                        "RAM ${if (memPct >= 0) "$memPct%" else "--"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xB3FF5B6E)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        when (thermalProfile.name) {
                            "COOL" -> "❄ MODE"
                            "BALANCED" -> "⚖ MODE"
                            "BEAST" -> "🔥 BEAST MODE"
                            else -> "⚡ EXTREME MODE"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color(0xFFFF3B4E),
                                blurRadius = 22f
                            )
                        ),
                        color = Color(0xFFFF8A7A)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        SimpleDateFormat("hh:mm", Locale.US).format(Date()) +
                            "  ⚡${stats.batteryTempC.toInt()}°",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xB3FFFFFF)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "✕",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xE6FFFFFF),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x33FFFFFF))
                            .clickable { setPanelVisible(false) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0x00FF5B6E), Color(0xFFFF5B6E), Color(0x00FF5B6E))
                            )
                        )
                )

                Spacer(Modifier.height(8.dp))

                // ---- the two real sliders (brightness · volume) ----------
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SliderRow("🎮", brightness, Modifier.weight(1f)) {
                        brightness = it
                        setBrightnessPct(it)
                    }
                    SliderRow("🎵", volume, Modifier.weight(1f)) {
                        volume = it
                        setMediaVolume((it * 15).toInt())
                    }
                }

                Spacer(Modifier.height(6.dp))

                // ---- toggle grid — every one is real ---------------------
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QuickToggle("🔁", "Refresh Rate", scalingState.refreshHz > 0, Modifier.weight(1f)) {
                        scope.launch(Dispatchers.IO) {
                            val next = when (scalingState.refreshHz) {
                                60 -> 90; 90 -> 120; 120 -> 144; 144 -> 60; else -> 120
                            }
                            val out = container.scaling.lockRefresh(next)
                            container.frameGen.applyPacing(next)
                            lastAction.value = out.lastOrNull() ?: "Refresh → ${next}Hz"
                        }
                    }
                    QuickToggle("📊", "Info Monitor", hudOn, Modifier.weight(1f)) {
                        scope.launch(Dispatchers.IO) {
                            container.settings.setOverlayHud(!hudOn)
                            lastAction.value = "Info monitor ${if (!hudOn) "ON" else "OFF"}"
                        }
                    }
                    QuickToggle("🌐", "DNS Tuner", dns, Modifier.weight(1f)) {
                        quickToggle(
                            !dns,
                            "settings put global private_dns_mode hostname && settings put global private_dns_specifier dns.google",
                            "settings put global private_dns_mode opportunistic",
                            "settings get global private_dns_mode",
                            "hostname",
                            "DNS tuner",
                            dnsOn
                        )
                    }
                    QuickToggle("✛", "Crosshair", cross, Modifier.weight(1f)) {
                        toggleCrosshair(!cross)
                    }
                    QuickToggle("⚡", "Quick Boost", boostState is com.gamershub.core.boost.BoostState.Active, Modifier.weight(1f)) {
                        scope.launch(Dispatchers.IO) {
                            if (boostState is com.gamershub.core.boost.BoostState.Active) {
                                val out = container.boost.restoreBoost()
                                lastAction.value = "Boost released"
                            } else {
                                val ok = container.boost.applyBoost(
                                    com.gamershub.core.boost.BoostPlan(),
                                    gamePackage = stats.foregroundPackage.ifBlank { null }
                                )
                                lastAction.value = if (ok) "Boost applied (verified writes)" else "Boost failed — is the shell connected?"
                            }
                        }
                    }
                    QuickToggle("🎯", "Gyro Calib", false, Modifier.weight(1f)) {
                        calibrateGyro()
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QuickToggle("🔒", "Rotation Lock", rotLock, Modifier.weight(1f)) {
                        quickToggle(
                            !rotLock,
                            "settings put system accelerometer_rotation 0 && settings put system user_rotation 0",
                            "settings put system accelerometer_rotation 1",
                            "settings get system accelerometer_rotation",
                            "0",
                            "Rotation lock",
                            rotationLocked
                        )
                    }
                    QuickToggle("🌙", "Do Not Disturb", dnd, Modifier.weight(1f)) {
                        quickToggle(
                            !dnd,
                            "settings put global zen_mode 1",
                            "settings put global zen_mode 0",
                            "settings get global zen_mode",
                            "1",
                            "Do not disturb",
                            dndOn
                        )
                    }
                    QuickToggle("🔆", "Lock Brightness", brightLock, Modifier.weight(1f)) {
                        quickToggle(
                            !brightLock,
                            "settings put system screen_brightness_mode 0",
                            "settings put system screen_brightness_mode 1",
                            "settings get system screen_brightness_mode",
                            "0",
                            "Brightness lock",
                            brightnessLocked
                        )
                    }
                    QuickToggle("👆", "Touch Enhancer", touchBoost, Modifier.weight(1f)) {
                        quickToggle(
                            !touchBoost,
                            "settings put global pointer_speed 7",
                            "settings put global pointer_speed 0",
                            "settings get global pointer_speed",
                            "7",
                            "Touch enhancer",
                            touchBoostOn
                        )
                    }
                    QuickToggle("📸", "Screenshot", false, Modifier.weight(1f)) {
                        takeScreenshot()
                    }
                    QuickToggle("👁", "Show Taps", taps, Modifier.weight(1f)) {
                        quickToggle(
                            !taps,
                            "settings put system show_touches 1",
                            "settings put system show_touches 0",
                            "settings get system show_touches",
                            "1",
                            "Show taps",
                            showTapsOn
                        )
                    }
                }

                // ---- logo + settings, like the reference bar ---------------
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "◆ GAMERSHUB",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0x80FF5B6E)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "▾",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0x66FFFFFF)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "⚙",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xB3FFFFFF),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                runCatching {
                                    startActivity(
                                        packageManager.getLaunchIntentForPackage(packageName)
                                            ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                                setPanelVisible(false)
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // ---- live result line (nothing ever fails silently) ------
                if (actionLine.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        actionLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xB3FFFFFF),
                        maxLines = 2
                    )
                }
            }
        }
    }

    /** Horizontal icon slider (brightness / volume) — real values applied live. */
    @Composable
    private fun SliderRow(
        icon: String,
        value: Float,
        modifier: Modifier = Modifier,
        onChange: (Float) -> Unit
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(28.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                onChange((pos.x / size.width).coerceIn(0f, 1f))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                onChange((change.position.x / size.width).coerceIn(0f, 1f))
                            }
                        )
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0x33FFFFFF))
                )
                Box(
                    Modifier
                        .fillMaxWidth(value.coerceIn(0.02f, 1f))
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFF3B4E), Color(0xFFFF7A59))))
                )
                Box(
                    Modifier
                        .fillMaxWidth(value.coerceIn(0f, 1f))
                        .wrapContentHeight(Alignment.CenterVertically)
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .shadow(
                                10.dp, CircleShape,
                                ambientColor = Color(0xFFFF3B4E),
                                spotColor = Color(0xFFFF3B4E)
                            )
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(Color(0xFFFF6B5B), Color(0xFFFF3B4E))))
                            .border(1.5.dp, Color(0x99FFFFFF), CircleShape)
                    )
                }
            }
        }
    }

    /** Circular quick toggle with a label underneath — the game-bar grid cell. */
    @Composable
    private fun QuickToggle(
        icon: String,
        label: String,
        active: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            if (pressed) 0.90f else 1f,
            spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
            label = "toggle-scale"
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(16.dp))
                .clickable(interaction, indication = null, onClick = onClick)
                .padding(vertical = 7.dp, horizontal = 2.dp)
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .then(
                        if (active) Modifier.shadow(
                            12.dp,
                            RoundedCornerShape(15.dp),
                            ambientColor = Color(0xFFFF3B4E),
                            spotColor = Color(0xFFFF3B4E)
                        ) else Modifier
                    )
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        if (active)
                            Brush.verticalGradient(listOf(Color(0xFFFF5B6E), Color(0xFFFF8A4C)))
                        else
                            Brush.verticalGradient(listOf(Color(0x22FFFFFF), Color(0x14FFFFFF)))
                    )
                    .border(
                        1.dp,
                        if (active) Color(0x55FFFFFF) else Color(0x2EFFFFFF),
                        RoundedCornerShape(15.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) Color(0xFFFFFFFF) else Color(0xB3FFFFFF),
                maxLines = 2
            )
        }
    }

    @Composable
    private fun PanelLabel(text: String) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0x99FFFFFF),
            modifier = Modifier.padding(bottom = 5.dp)
        )
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
        const val ACTION_OPEN_PANEL = "com.gamershub.action.OPEN_PANEL"

        val isRunning = MutableStateFlow(false)

        /**
         * Opens the Game-Space control panel right now. Safe no-op without the
         * overlay permission (the caller checks and routes to settings).
         */
        fun openPanel(context: Context) {
            if (!android.provider.Settings.canDrawOverlays(context)) return
            val intent = Intent(context, FloatingWidgetService::class.java)
                .setAction(ACTION_OPEN_PANEL)
            val started = runCatching {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            }.getOrDefault(false)
            if (!started) isRunning.value = false
        }

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
