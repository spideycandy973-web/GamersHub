package com.gamershub

import com.gamershub.ui.components.AIcon

import android.os.BatteryManager
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.gamershub.core.capture.ProjectionActivity
import com.gamershub.core.capture.Recorder
import com.gamershub.core.capture.RecorderService
import com.gamershub.core.capture.Stream
import com.gamershub.core.capture.StreamService
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.ShaderBrush
import com.gamershub.data.AppRepo
import com.gamershub.data.InstalledApp
import com.gamershub.data.DeviceStats
import com.gamershub.overlay.FloatingPanelService
import com.gamershub.theme.GC
import com.gamershub.theme.GameCornerTheme
import com.gamershub.theme.LocalAccent
import com.gamershub.theme.accentNow
import com.gamershub.theme.accentPairNow
import com.gamershub.ui.components.GaugeRing
import com.gamershub.ui.components.HeroMark
import com.gamershub.ui.components.SegmentedBar
import com.gamershub.ui.components.Toggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Game Space — the landscape in-game hub: game card + LAUNCH, live stats
 * (ping / temperature / CPU), Control Center and per-game Texture Filtering.
 */
class GameSpaceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep edge-to-edge but DO NOT zero the insets — the top bar must
        // never slide under the status bar (that was the overlap bug).
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: ""
        val repo = AppRepo(applicationContext)
        setContent {
            val customArgb = runCatching { repo.customAccent() }.getOrDefault(0)
            val customTint = if (customArgb != 0) androidx.compose.ui.graphics.Color(customArgb) else null
            GameCornerTheme(if (customTint != null) com.gamershub.theme.GC.CUSTOM else repo.accentIndex(), customTint) {
                var showIntro by remember { mutableStateOf(true) }
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    GameSpaceScreen(
                        pkg = pkg,
                        repo = repo,
                        onClose = { finish() },
                        onLaunch = { p ->
                            repo.launch(p)
                        }
                    )
                    if (showIntro) {
                        com.gamershub.ui.IntroAnimation(onDone = { showIntro = false })
                    }
                }
            }
        }
    }

    /**
     * Leaving the hub must not leave the display scaled: a `wm size` override that
     * outlives the session is what makes the whole phone look odd afterwards.
     */
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            val prefs = getSharedPreferences("gamershub", android.content.Context.MODE_PRIVATE)
            if (prefs.getInt("ax_lossless", 100) in 40..99) {
                // Keep the render scale alive when the user goes straight back into the
                // game that was running underneath; restore the panel otherwise.
                val target = intent?.getStringExtra(EXTRA_PKG) ?: ""
                val front = runCatching {
                    com.gamershub.core.FpsMeter.foregroundPackageDeep(applicationContext)
                }.getOrNull()
                val stillPlaying = target.isNotBlank() && front == target
                if (!stillPlaying) {
                    runCatching { com.gamershub.core.GfxEngine.resetRenderScale() }
                    prefs.edit().putInt("ax_lossless", 100).apply()
                }
            }
            // A spoofed Bluetooth/USB name must not outlive the session.
            runCatching {
                if (com.gamershub.core.DeviceSpoofer.nameSpoofActive(applicationContext)) {
                    com.gamershub.core.DeviceSpoofer.restoreDeviceName(applicationContext)
                }
            }
        }
    }

    companion object {
        const val EXTRA_PKG = "pkg"
    }
}

@Composable
private fun GameSpaceScreen(
    pkg: String,
    repo: AppRepo,
    onClose: () -> Unit,
    onLaunch: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var library by remember { mutableStateOf(repo.addedApps()) }
    var currentPkg by remember { mutableStateOf(pkg.ifBlank { library.firstOrNull()?.pkg ?: "" }) }
    LaunchedEffect(pkg) {
        if (pkg.isNotBlank() && !repo.isAdded(pkg)) repo.setAdded(pkg, true)
        library = repo.addedApps()
        if (currentPkg.isBlank()) currentPkg = pkg.ifBlank { library.firstOrNull()?.pkg ?: "" }
    }
    val current = library.firstOrNull { it.pkg == currentPkg }
    val currentIcon = remember(currentPkg, library) { current?.icon ?: repo.iconFor(currentPkg) }
    // Real captured frame of the app, polled until the shell capture lands.
    val art by produceState<ImageBitmap?>(initialValue = null, currentPkg) {
        value = withContext(Dispatchers.IO) { com.gamershub.data.ArtStore.load(context, currentPkg) }
        var tries = 0
        while (tries < 40) {
            delay(1500)
            val shot = withContext(Dispatchers.IO) { com.gamershub.data.ArtStore.load(context, currentPkg) }
            if (shot != null) { value = shot; break }
            tries++
        }
    }
    val appName = current?.name ?: currentPkg.ifBlank { "No app selected" }
    val appVersion = remember(currentPkg) {
        try {
            context.packageManager.getPackageInfo(currentPkg, 0).versionName ?: "?"
        } catch (_: Throwable) {
            "?"
        }
    }
    var showControl by remember { mutableStateOf(false) }
    var showTexture by remember { mutableStateOf(false) }
    var showTouch by remember { mutableStateOf(false) }
    var showDisplay by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }
    var panelRunning by remember { mutableStateOf(FloatingPanelService.isRunning) }

    // ---- live device numbers (all real) ----
    val mem by produceState(initialValue = DeviceStats.mem(context)) {
        while (true) { value = withContext(Dispatchers.IO) { DeviceStats.mem(context) }; delay(2000) }
    }
    val cpu by produceState(initialValue = DeviceStats.cpu()) {
        while (true) { value = withContext(Dispatchers.IO) { DeviceStats.cpu() }; delay(2000) }
    }
    val battery by produceState(initialValue = DeviceStats.battery(context)) {
        while (true) { value = withContext(Dispatchers.IO) { DeviceStats.battery(context) }; delay(5000) }
    }
    val ping by produceState(initialValue = -1.0) {
        while (true) {
            value = withContext(Dispatchers.IO) { DeviceStats.pingExact() }
            delay(5000)
        }
    }

    val freeMb = (mem.totalMb - mem.usedMb).coerceAtLeast(0)
    val level = envLevelOf(context)
    val perform = performScore(cpu.pct, mem.pct, battery.second)
    val others = library.filter { it.pkg != currentPkg }

    Box(Modifier.fillMaxSize().background(GC.Void)) {
        GsBackdrop(currentIcon, Modifier.fillMaxSize())
        // safe-area aware canvas: the status bar can never sit on top of the console
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val w = maxWidth
            val h = maxHeight

            // ---------- top bar ----------
            GsMark(Modifier.offset(x = w * 0.048f, y = h * 0.068f).size(h * 0.120f))
            Column(Modifier.offset(x = w * 0.048f + h * 0.130f, y = h * 0.072f)) {
                Text(
                    "GAME",
                    style = MaterialTheme.typography.labelMedium,
                    color = GC.InkText.copy(alpha = 0.9f),
                    fontSize = (h.value * 0.020f).sp,
                    letterSpacing = (h.value * 0.008f).sp
                )
                Text(
                    "HUB",
                    style = MaterialTheme.typography.titleLarge,
                    color = GC.InkText,
                    fontSize = (h.value * 0.055f).sp,
                    letterSpacing = (h.value * 0.002f).sp
                )
            }

            // divider between the lockup and the level block, like the reference
            Box(
                Modifier
                    .offset(x = w * 0.216f, y = h * 0.072f)
                    .width(1.dp)
                    .height(h * 0.098f)
                    .background(Color(0x1FFFFFFF))
            )

            Column(Modifier.offset(x = w * 0.232f, y = h * 0.080f)) {
                Text(
                    "LVL ${level.coerceAtLeast(2).coerceAtMost(3)}/3",
                    style = MaterialTheme.typography.labelLarge,
                    color = GC.InkText,
                    fontSize = (h.value * 0.029f).sp,
                    letterSpacing = (h.value * 0.004f).sp
                )
                Spacer(Modifier.height(h * 0.020f))
                Row(horizontalArrangement = Arrangement.spacedBy(w * 0.006f)) {
                    repeat(3) { i ->
                        Box(
                            Modifier
                                .width(w * 0.016f)
                                .height(h * 0.011f)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (i < level.coerceAtLeast(2)) accentNow() else Color(0x33FFFFFF))
                        )
                    }
                }
            }

            // chevron tabs
            val tabW = w * 0.050f
            val tabH = h * 0.100f
            val tabY = h * 0.075f
            val tabX = w * 0.396f
            val tabs = listOf(Icons.Filled.Bolt, Icons.Filled.Speed, Icons.Filled.Tune, Icons.Filled.Apps)
            val activeTab = when {
                showControl -> 1
                showTexture -> 2
                showApps -> 3
                else -> 0
            }
            tabs.forEachIndexed { i, icon ->
                val tabBg by animateColorAsState(
                    if (i == activeTab) accentNow() else Color(0xFF150E0F),
                    tween(220), label = "tabBg$i"
                )
                val tabTint by animateColorAsState(
                    if (i == activeTab) Color.White else GC.InkSoft,
                    tween(220), label = "tabTint$i"
                )
                val tabInteraction = remember { MutableInteractionSource() }
                val tabPressed by tabInteraction.collectIsPressedAsState()
                val tabScale by animateFloatAsState(
                    if (tabPressed) 0.90f else 1f,
                    spring(dampingRatio = 0.45f, stiffness = 900f), label = "tabScale$i"
                )
                Box(
                    Modifier
                        .offset(x = tabX + (tabW + w * 0.003f) * i, y = tabY)
                        .width(tabW)
                        .height(tabH)
                        .graphicsLayer { scaleX = tabScale; scaleY = tabScale }
                        .clip(ChevShape)
                        .background(tabBg)
                        .clickable(tabInteraction, indication = null) {
                            when (i) {
                                1 -> { showControl = true; showTexture = false; showApps = false }
                                2 -> { showTexture = true; showControl = false; showApps = false }
                                3 -> { showApps = true; showControl = false; showTexture = false }
                                else -> { showControl = false; showTexture = false; showApps = false }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AIcon(
                        icon,
                        contentDescription = null,
                        tint = tabTint,
                        modifier = Modifier.size(h * 0.036f)
                    )
                }
            }

            // energy (free / total RAM in tens of MB)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.offset(x = w * 0.738f, y = h * 0.082f)
            ) {
                AIcon(Icons.Filled.Bolt, contentDescription = "Free room", tint = accentNow(), modifier = Modifier.size(h * 0.032f))
                Spacer(Modifier.width(w * 0.004f))
                Text(
                    "${(freeMb / 10)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = GC.InkText,
                    fontSize = (h.value * 0.030f).sp
                )
                Spacer(Modifier.width(w * 0.003f))
                Text(
                    "/ ${(mem.totalMb / 10)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = GC.InkDim,
                    fontSize = (h.value * 0.025f).sp
                )
            }

            // battery + clock
            Column(Modifier.offset(x = w * 0.827f, y = h * 0.080f), horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BatteryGlyph(pct = battery.first, w = w * 0.028f, h = h * 0.022f)
                    Spacer(Modifier.width(w * 0.006f))
                    Text(
                        "${battery.first.coerceAtLeast(0)}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = GC.InkText,
                        fontSize = (h.value * 0.028f).sp
                    )
                }
                Spacer(Modifier.height(h * 0.012f))
                LiveClock(fontSize = (h.value * 0.026f).sp)
            }

            // power
            Box(
                Modifier
                    .offset(x = w * 0.906f, y = h * 0.088f)
                    .size(h * 0.060f)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                AIcon(
                    Icons.Filled.PowerSettingsNew,
                    contentDescription = "Shut tool shelf",
                    tint = GC.InkText,
                    modifier = Modifier.size(h * 0.048f)
                )
            }

            // ---------- app strip: every selected app, always visible ----------
            AppStrip(
                library = library,
                currentPkg = currentPkg,
                onSelect = { currentPkg = it },
                onAdd = { showApps = true },
                modifier = Modifier
                    .offset(x = w * 0.045f, y = h * 0.184f)
                    .size(width = w * 0.910f, height = h * 0.132f),
                iconSize = h * 0.082f,
                nameSize = (h.value * 0.027f).sp
            )

            // ---------- featured card with a real captured frame ----------
            // Switching apps now slides the card in the direction you swiped, so the
            // change of selection is visible instead of the picture just swapping.
            AnimatedContent(
                targetState = currentPkg,
                transitionSpec = {
                    val from = library.indexOfFirst { it.pkg == initialState }
                    val to = library.indexOfFirst { it.pkg == targetState }
                    val forward = to >= from
                    (
                        slideInHorizontally(tween(300)) { width -> if (forward) width / 3 else -width / 3 } +
                            fadeIn(tween(220)) +
                            androidx.compose.animation.scaleIn(tween(300), initialScale = 0.96f)
                        ).togetherWith(
                        slideOutHorizontally(tween(200)) { width -> if (forward) -width / 4 else width / 4 } +
                            fadeOut(tween(160))
                    )
                },
                label = "featuredApp",
                modifier = Modifier
                    .offset(x = w * 0.045f, y = h * 0.340f)
                    .size(width = w * 0.356f, height = h * 0.348f)
                    .swipeToSwitchApp(library, currentPkg) { currentPkg = it }
            ) { shownPkg ->
                val shown = library.firstOrNull { it.pkg == shownPkg }
                AppCard(
                    entry = shown,
                    art = if (shownPkg == currentPkg) art else null,
                    name = shown?.name ?: shownPkg.ifBlank { "No app selected" },
                    version = appVersion,
                    minutes = repo.playMinutes(shownPkg),
                    selected = true,
                    primary = true,
                    emptyLabel = if (library.isEmpty()) "PICK APPS" else "SELECT AN APP",
                    onSelect = { if (library.isEmpty()) showApps = true },
                    onFilter = { showTexture = true; showControl = false; showApps = false },
                    onCapture = {
                        if (currentPkg.isNotBlank()) {
                            onLaunch(currentPkg)
                            com.gamershub.data.ArtStore.captureLater(context, currentPkg, 9000L)
                        }
                    },
                    artSize = h * 0.115f,
                    nameSize = (h.value * 0.031f).sp,
                    metaSize = (h.value * 0.019f).sp,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ---------- live app facts, 2x2 ----------
            AppFacts(
                context = context,
                pkg = currentPkg,
                minutes = repo.playMinutes(currentPkg),
                version = appVersion,
                modifier = Modifier
                    .offset(x = w * 0.417f, y = h * 0.340f)
                    .size(width = w * 0.288f, height = h * 0.348f),
                titleSize = (h.value * 0.016f).sp,
                valueSize = (h.value * 0.030f).sp,
                noteSize = (h.value * 0.014f).sp
            )

            // ---------- name / package ----------
            Text(
                appName,
                style = MaterialTheme.typography.headlineMedium,
                color = GC.InkText,
                fontSize = (h.value * 0.048f).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .offset(x = w * 0.045f, y = h * 0.720f)
                    .width(w * 0.30f)
            )
            Text(
                currentPkg.ifBlank { "no package" },
                style = MaterialTheme.typography.labelMedium,
                color = GC.InkDim,
                fontSize = (h.value * 0.020f).sp,
                letterSpacing = (h.value * 0.0015f).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .offset(x = w * 0.045f + h * 0.008f, y = h * 0.786f)
                    .width(w * 0.28f)
            )

            // ---------- launch row ----------
            LaunchBar(
                enabled = currentPkg.isNotBlank(),
                onLaunch = {
                    if (currentPkg.isNotBlank()) {
                        onLaunch(currentPkg)
                        com.gamershub.data.ArtStore.captureLater(context, currentPkg, 9000L)
                    }
                },
                onBoost = { showControl = true },
                freeMb = freeMb,
                width = w * 0.189f,
                height = h * 0.091f,
                textSize = (h.value * 0.038f).sp,
                modifier = Modifier.offset(x = w * 0.045f, y = h * 0.853f)
            )
            GridButton(
                onClick = { showApps = true },
                size = h * 0.091f,
                modifier = Modifier.offset(x = w * 0.237f, y = h * 0.853f)
            )
            PerformBlock(
                score = perform,
                valueSize = (h.value * 0.040f).sp,
                labelSize = (h.value * 0.019f).sp,
                tickW = w * 0.0094f,
                modifier = Modifier
                    .offset(x = w * 0.286f, y = h * 0.858f)
                    .width(w * 0.124f)
            )

            // ---------- stat wall ----------
            StatBox(
                title = "PING GAP",
                value = if (ping >= 0) String.format("%.1f ms", ping) else "-- ms",
                good = ping in 0.0..80.0,
                titleSize = (h.value * 0.017f).sp,
                valueSize = (h.value * 0.031f).sp,
                modifier = Modifier
                    .offset(x = w * 0.577f, y = h * 0.764f)
                    .size(width = w * 0.122f, height = h * 0.115f)
            ) {
                TickRow(filled = if (ping >= 0) (10 - (ping / 12).toInt()).coerceIn(0, 10) else 0, segments = 10, tickW = w * 0.007f)
            }
            StatBox(
                title = "HEAT",
                value = String.format("%.1f°C", battery.second.coerceAtLeast(0.0)),
                good = battery.second in 0.0..45.0,
                titleSize = (h.value * 0.017f).sp,
                valueSize = (h.value * 0.031f).sp,
                modifier = Modifier
                    .offset(x = w * 0.708f, y = h * 0.764f)
                    .size(width = w * 0.119f, height = h * 0.115f)
            ) {
                TempBar(celsius = battery.second, height = h * 0.014f)
            }
            CpuCard(
                ghz = cpu.curGhz,
                loadPct = cpu.pct,
                titleSize = (h.value * 0.017f).sp,
                valueSize = (h.value * 0.033f).sp,
                modifier = Modifier
                    .offset(x = w * 0.843f, y = h * 0.735f)
                    .size(width = w * 0.112f, height = h * 0.173f)
            )
        }
    }

    // ---- panels ----
    AnimatedVisibility(
        visible = showControl,
        enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 5 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(220)) { it / 6 }
    ) {
        ControlCenter(
            onDismiss = { showControl = false },
            onTouchSettings = { showControl = false; showTouch = true },
            onDisplaySettings = { showControl = false; showDisplay = true },
            onTextureSettings = { showControl = false; showTexture = true },
            onPickApps = { showControl = false; showApps = true },
            onOpenPanel = {
                panelRunning = !panelRunning
                if (panelRunning) FloatingPanelService.start(context) else FloatingPanelService.stop(context)
            },
            panelRunning = panelRunning
        )
    }

    AnimatedVisibility(
        visible = showApps,
        enter = slideInHorizontally(tween(320)) { it } + fadeIn(tween(280)),
        exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(200))
    ) {
        AppsPanel(
            library = library,
            currentPkg = currentPkg,
            onSelect = { currentPkg = it },
            onLaunch = { p -> onLaunch(p) },
            onPickApps = {
                showApps = false
                runCatching {
                    context.startActivity(
                        Intent(context, com.gamershub.MainActivity::class.java)
                            .putExtra(com.gamershub.MainActivity.EXTRA_OPEN, "apps#" + System.currentTimeMillis())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
            onDismiss = { showApps = false }
        )
    }

    AnimatedVisibility(
        visible = showTexture,
        enter = slideInHorizontally(tween(320)) { it } + fadeIn(tween(280)),
        exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(200))
    ) {
        TexturePanel(pkg = currentPkg, repo = repo, appName = appName, onDismiss = { showTexture = false })
    }

    AnimatedVisibility(
        visible = showTouch,
        enter = slideInHorizontally(tween(320)) { it } + fadeIn(tween(280)),
        exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(200))
    ) {
        TouchPanel(repo = repo, onDismiss = { showTouch = false })
    }
    AnimatedVisibility(
        visible = showDisplay,
        enter = slideInHorizontally(tween(320)) { it } + fadeIn(tween(280)),
        exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(200))
    ) {
        DisplayPanel(onDismiss = { showDisplay = false })
    }
}

/* ------------------------------------------------------------------ */
/* backdrop + header pieces                                            */
/* ------------------------------------------------------------------ */

/** The reference screen is tinted by the selected app's own artwork. */
@Composable
private fun GsBackdrop(icon: ImageBitmap?, modifier: Modifier) {
    // Pre-blurred once per icon: a 4x-scaled full-screen RenderEffect blur was re-rasterising
    // on every stats tick, which is what made the hub feel heavy.
    val blurred = remember(icon) { icon?.let { blurBitmap(it, 0.75f) } }
    val dots = remember { dotPattern() }
    Box(modifier) {
        if (blurred != null) {
            Image(
                painter = androidx.compose.ui.graphics.painter.BitmapPainter(blurred),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            GC.Void.copy(alpha = 0.62f),
                            GC.Void.copy(alpha = 0.80f),
                            GC.Void.copy(alpha = 0.92f)
                        )
                    )
                )
        )
        Canvas(Modifier.fillMaxSize()) { drawRect(dots) }
    }
}

/** Cheap software blur: downscale hard, upscale back with bilinear filtering. */
private fun blurBitmap(src: ImageBitmap, alpha: Float): ImageBitmap = runCatching {
    val bmp = src.asAndroidBitmap()
    val w = 40
    val h = (40f * bmp.height / bmp.width).coerceAtLeast(1f).toInt()
    val small = android.graphics.Bitmap.createScaledBitmap(bmp, w, h, true)
    val big = android.graphics.Bitmap.createScaledBitmap(small, 900, (900f * bmp.height / bmp.width).coerceAtLeast(1f).toInt(), true)
    big.asImageBitmap()
}.getOrNull() ?: src

/** One 128 px tile of the console dot matrix, repeated by a shader. */
private fun dotPattern(): Brush {
    val n = 128
    val bmp = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.argb(0x14, 255, 255, 255)
    canvas.drawCircle(n * 0.12f, n * 0.18f, 1.5f, paint)
    canvas.drawCircle(n * 0.62f, n * 0.56f, 1.3f, paint)
    paint.color = android.graphics.Color.argb(0x44, 255, 0x17, 0x17)
    canvas.drawCircle(n * 0.86f, n * 0.90f, 2.2f, paint)
    paint.color = android.graphics.Color.argb(0x12, 255, 255, 255)
    canvas.drawCircle(n * 0.34f, n * 0.86f, 1.2f, paint)
    return ShaderBrush(
        androidx.compose.ui.graphics.ImageShader(
            bmp.asImageBitmap(),
            androidx.compose.ui.graphics.TileMode.Repeated,
            androidx.compose.ui.graphics.TileMode.Repeated
        )
    )
}

/** Clock lives in its own composable so the whole hub is not rebuilt every second. */
@Composable
private fun LiveClock(fontSize: androidx.compose.ui.unit.TextUnit) {
    var clock by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = currentTime()
            delay(1000)
        }
    }
    Text(clock, style = MaterialTheme.typography.labelLarge, color = GC.InkSoft, fontSize = fontSize)
}

/** Slanted tile used by the four navigation chevrons. */
private val ChevShape = GenericShape { size, _ ->
    val cut = size.height * 0.30f
    moveTo(cut, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - cut, size.height)
    lineTo(0f, size.height)
    close()
}

/** Solid angular emblem, matching the chunky reference logo. */
@Composable
private fun GsMark(modifier: Modifier) {
    val accent = accentNow()
    val accentPair = accentPairNow()
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val outline = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.50f, h * 0.02f)
            lineTo(w * 0.97f, h * 0.29f)
            lineTo(w * 0.86f, h * 0.94f)
            lineTo(w * 0.14f, h * 0.94f)
            lineTo(w * 0.03f, h * 0.29f)
            close()
        }
        drawPath(outline, Color(0xFF241516))
        drawPath(
            outline,
            Brush.linearGradient(listOf(accent, accentPair)),
            style = Stroke(width = h * 0.075f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        )
        val bolt = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.58f, h * 0.18f)
            lineTo(w * 0.33f, h * 0.55f)
            lineTo(w * 0.49f, h * 0.55f)
            lineTo(w * 0.40f, h * 0.83f)
            lineTo(w * 0.70f, h * 0.45f)
            lineTo(w * 0.53f, h * 0.45f)
            close()
        }
        drawPath(bolt, Color(0xFFFFFFFF))
    }
}

@Composable
private fun BatteryGlyph(pct: Int, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp) {
    val accent = accentNow()
    Canvas(Modifier.width(w).height(h)) {
        val body = size.width - 3.dp.toPx()
        val radius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
        drawRoundRect(
            color = Color(0x40FFFFFF),
            size = androidx.compose.ui.geometry.Size(body, size.height),
            cornerRadius = radius
        )
        drawRoundRect(
            color = accent,
            size = androidx.compose.ui.geometry.Size(
                (body - 2.dp.toPx()) * (pct.coerceIn(0, 100) / 100f),
                size.height - 2.dp.toPx()
            ),
            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
            cornerRadius = radius
        )
        drawRoundRect(
            color = accent,
            size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height * 0.40f),
            topLeft = Offset(body + 1.dp.toPx(), size.height * 0.30f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
        )
    }
}

/* ------------------------------------------------------------------ */
/* app cards + launch block                                            */
/* ------------------------------------------------------------------ */

@Composable
private fun AppCard(
    entry: InstalledApp?,
    name: String,
    version: String,
    minutes: Long,
    selected: Boolean,
    primary: Boolean,
    emptyLabel: String,
    onSelect: () -> Unit,
    onFilter: (() -> Unit)?,
    artSize: androidx.compose.ui.unit.Dp,
    nameSize: androidx.compose.ui.unit.TextUnit,
    metaSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    art: ImageBitmap? = null,
    onCapture: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(artSize * 0.20f)
    Box(
        modifier
            .clip(shape)
            .background(GC.InkPanel)
            .then(
                if (selected) Modifier.border(1.dp, accentNow().copy(alpha = 0.55f), shape)
                else Modifier.border(1.dp, Color(0x12FFFFFF), shape)
            )
            .clickable(onClick = onSelect)
    ) {
        if (art != null) {
            // A real frame captured from the app itself — cross-faded so switching apps is smooth.
            androidx.compose.animation.Crossfade(
                targetState = art,
                animationSpec = tween(320),
                label = "art"
            ) { frame ->
                Image(
                    painter = androidx.compose.ui.graphics.painter.BitmapPainter(frame),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (entry != null) {
            Image(
                painter = androidx.compose.ui.graphics.painter.BitmapPainter(entry.icon),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = 3.4f, scaleY = 3.4f, alpha = 0.82f)
                    .then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(26.dp) else Modifier)
            )
            // no capture yet: the icon still carries the card, softly
            Image(
                painter = androidx.compose.ui.graphics.painter.BitmapPainter(entry.icon),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight(0.62f)
                    .fillMaxWidth(0.62f)
                    .graphicsLayer(rotationZ = -8f, alpha = 0.55f)
                    .then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(10.dp) else Modifier)
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x59000000), Color(0x33000000), Color(0xE60A0708))
                        )
                    )
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF1A1113), Color(0xFF0C0809))))
            )
        }

        if (onCapture != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(artSize * 0.16f)
                    .size(artSize * 0.55f)
                    .clip(RoundedCornerShape(artSize * 0.14f))
                    .background(Color(0xCC0A0708))
                    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(artSize * 0.14f))
                    .clickable(onClick = onCapture),
                contentAlignment = Alignment.Center
            ) {
                AIcon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = if (art != null) "Snap the card again" else "Snap this app",
                    tint = if (art != null) GC.InkText else accentNow(),
                    modifier = Modifier.size(artSize * 0.30f)
                )
            }
        }
        if (onFilter != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = artSize * 0.16f,
                        end = if (onCapture != null) artSize * 0.78f else artSize * 0.16f
                    )
                    .size(artSize * 0.55f)
                    .clip(RoundedCornerShape(artSize * 0.14f))
                    .background(Color(0xCC0A0708))
                    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(artSize * 0.14f))
                    .clickable(onClick = onFilter),
                contentAlignment = Alignment.Center
            ) {
                AIcon(Icons.Filled.Tune, contentDescription = "Surface smooth", tint = GC.InkText, modifier = Modifier.size(artSize * 0.30f))
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = artSize * 0.22f, end = artSize * 0.22f, bottom = artSize * 0.20f)
                .fillMaxWidth()
        ) {
            Box(
                Modifier
                    .size(artSize)
                    .clip(RoundedCornerShape(artSize * 0.26f))
                    .background(Color(0x1FFFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                if (entry != null) {
                    Image(
                        painter = androidx.compose.ui.graphics.painter.BitmapPainter(entry.icon),
                        contentDescription = name,
                        modifier = Modifier
                            .size(artSize * 0.94f)
                            .clip(RoundedCornerShape(artSize * 0.23f))
                    )
                } else {
                    AIcon(Icons.Filled.Apps, contentDescription = null, tint = GC.InkDim, modifier = Modifier.size(artSize * 0.45f))
                }
            }
            Spacer(Modifier.width(artSize * 0.20f))
            Column(Modifier.weight(1f)) {
                Text(
                    if (entry != null) name else emptyLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (entry != null) Color.White else GC.InkSoft,
                    fontSize = nameSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (entry != null) "${formatMinutes(minutes)} · v$version" else "tap to choose",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (entry != null) Color(0xCCFFFFFF) else GC.InkDim,
                    fontSize = metaSize,
                    maxLines = 1
                )
            }
        }
    }
}

/** Every selected app, in one swipeable strip — nothing can be hidden. */
@Composable
private fun AppStrip(
    library: List<InstalledApp>,
    currentPkg: String,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp,
    nameSize: androidx.compose.ui.unit.TextUnit
) {
    val listState = rememberLazyListState()
    val activeIndex = library.indexOfFirst { it.pkg == currentPkg }

    // Keep the chosen app in view, so a long library never hides the selection.
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) runCatching { listState.animateScrollToItem(activeIndex) }
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(iconSize * 0.18f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(library.size) { index ->
            val entry = library[index]
            val active = entry.pkg == currentPkg
            val stripBg by animateColorAsState(
                if (active) accentNow().copy(alpha = 0.20f) else Color(0x14FFFFFF),
                tween(220), label = "stripBg"
            )
            val stripBorder by animateColorAsState(
                if (active) accentNow().copy(alpha = 0.80f) else Color(0x10FFFFFF),
                tween(220), label = "stripBorder"
            )
            val stripInter = remember { MutableInteractionSource() }
            val stripPressed by stripInter.collectIsPressedAsState()
            val pressScale by animateFloatAsState(
                if (stripPressed) 0.94f else 1f,
                spring(dampingRatio = 0.5f, stiffness = 900f), label = "stripScale"
            )
            // The chosen app breathes a little larger, so the change of selection is visible.
            val activeScale by animateFloatAsState(
                if (active) 1.06f else 1f,
                spring(dampingRatio = 0.55f, stiffness = 420f), label = "stripActive"
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(iconSize * 1.55f)
                    .graphicsLayer {
                        val s = pressScale * activeScale
                        scaleX = s
                        scaleY = s
                    }
                    .clip(RoundedCornerShape(iconSize * 0.42f))
                    .background(stripBg)
                    .border(1.dp, stripBorder, RoundedCornerShape(iconSize * 0.42f))
                    .clickable(stripInter, indication = null) { onSelect(entry.pkg) }
                    .padding(end = iconSize * 0.40f)
            ) {
                Image(
                    painter = androidx.compose.ui.graphics.painter.BitmapPainter(entry.icon),
                    contentDescription = entry.name,
                    modifier = Modifier
                        .padding(iconSize * 0.11f)
                        .size(iconSize)
                        .clip(RoundedCornerShape(iconSize * 0.28f))
                )
                Spacer(Modifier.width(iconSize * 0.24f))
                Text(
                    entry.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) Color.White else GC.InkSoft,
                    fontSize = nameSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        item {
            if (library.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(iconSize * 1.55f)
                        .clip(RoundedCornerShape(iconSize * 0.42f))
                        .background(Color(0x14FFFFFF))
                        .clickable(onClick = onAdd)
                        .padding(horizontal = iconSize * 0.35f)
                ) {
                    AIcon(Icons.Filled.Add, contentDescription = null, tint = accentNow(), modifier = Modifier.size(iconSize * 0.7f))
                    Spacer(Modifier.width(iconSize * 0.2f))
                    Text("PICK APPS", style = MaterialTheme.typography.labelLarge, color = GC.InkText, fontSize = nameSize)
                }
            } else {
                Box(
                    Modifier
                        .size(iconSize * 1.55f)
                        .clip(RoundedCornerShape(iconSize * 0.42f))
                        .background(Color(0x14FFFFFF))
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center
                ) {
                    AIcon(Icons.Filled.Add, contentDescription = "Pick apps", tint = GC.InkText, modifier = Modifier.size(iconSize * 0.65f))
                }
            }
        }
    }
}

/** Real, readable facts about the featured app (size, install date, play time). */
@Composable
private fun AppFacts(
    context: android.content.Context,
    pkg: String,
    minutes: Long,
    version: String,
    modifier: Modifier = Modifier,
    titleSize: androidx.compose.ui.unit.TextUnit,
    valueSize: androidx.compose.ui.unit.TextUnit,
    noteSize: androidx.compose.ui.unit.TextUnit
) {
    val info = remember(pkg) {
        var sizeMb = -1L
        var installed = 0L
        var targetSdk = 0
        try {
            val pm = context.packageManager
            val pi = pm.getPackageInfo(pkg, 0)
            installed = pi.firstInstallTime
            targetSdk = pi.applicationInfo?.targetSdkVersion ?: 0
            sizeMb = java.io.File(pi.applicationInfo?.sourceDir ?: "").length() / (1024 * 1024)
        } catch (_: Throwable) {
        }
        Triple(sizeMb, installed, targetSdk)
    }
    val installText = remember(info.second) {
        if (info.second <= 0L) "--"
        else java.text.SimpleDateFormat("dd MMM", java.util.Locale.US).format(java.util.Date(info.second))
    }
    val session = formatMinutes(minutes)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(titleSize.value.dp * 0.9f)) {
        Row(horizontalArrangement = Arrangement.spacedBy(titleSize.value.dp * 0.9f)) {
            FactTile("SESSION", session, "played in Play Hub", Modifier.weight(1f), titleSize, valueSize, noteSize)
            FactTile("VERSION", "v$version", "from package manager", Modifier.weight(1f), titleSize, valueSize, noteSize)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(titleSize.value.dp * 0.9f)) {
            FactTile(
                "APK SIZE",
                if (info.first > 0) "${info.first} MB" else "--",
                "on disk right now",
                Modifier.weight(1f), titleSize, valueSize, noteSize
            )
            FactTile("INSTALLED", installText, "targetSdk ${info.third}", Modifier.weight(1f), titleSize, valueSize, noteSize)
        }
    }
}

@Composable
private fun FactTile(
    title: String,
    value: String,
    note: String,
    modifier: Modifier = Modifier,
    titleSize: androidx.compose.ui.unit.TextUnit,
    valueSize: androidx.compose.ui.unit.TextUnit,
    noteSize: androidx.compose.ui.unit.TextUnit
) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xA6100809))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = GC.InkDim,
            fontSize = titleSize,
            letterSpacing = (titleSize.value * 0.22f).sp,
            maxLines = 1,
            softWrap = false
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = GC.InkText,
            fontSize = valueSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            note,
            style = MaterialTheme.typography.labelMedium,
            color = GC.InkDim,
            fontSize = noteSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyCard(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xAA110B0C))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AIcon(Icons.Filled.Apps, contentDescription = null, tint = GC.InkDim, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = GC.InkSoft,
            fontSize = 12.sp,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun LaunchBar(
    enabled: Boolean,
    onLaunch: () -> Unit,
    onBoost: () -> Unit,
    freeMb: Long,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    textSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    val launchInter = remember { MutableInteractionSource() }
    val launchPressed by launchInter.collectIsPressedAsState()
    val launchScale by animateFloatAsState(
        if (launchPressed) 0.955f else 1f,
        spring(dampingRatio = 0.5f, stiffness = 900f), label = "launchScale"
    )
    Row(
        modifier
            .width(width)
            .height(height)
            .graphicsLayer { scaleX = launchScale; scaleY = launchScale }
            .clip(RoundedCornerShape(height * 0.16f))
            .background(
                if (enabled) Brush.horizontalGradient(listOf(accentNow(), accentPairNow()))
                else Brush.horizontalGradient(listOf(Color(0xFF2A1E1F), Color(0xFF180F10)))
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxHeight()
                .clickable(launchInter, indication = null, enabled = enabled, onClick = onLaunch)
                .padding(horizontal = height * 0.30f)
        ) {
            Text(
                "LAUNCH",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontSize = textSize,
                letterSpacing = (textSize.value * 0.16f).sp
            )
        }
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .padding(vertical = height * 0.24f)
                .background(Color(0x55FFFFFF))
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxHeight()
                .clickable(onClick = onBoost)
                .padding(horizontal = height * 0.18f)
        ) {
            AIcon(Icons.Filled.Bolt, contentDescription = "Memory boost", tint = Color.White, modifier = Modifier.size(height * 0.34f))
            Spacer(Modifier.width(width * 0.012f))
            Text(
                "${(freeMb / 100).coerceAtLeast(0)}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontSize = (textSize.value * 0.82f).sp
            )
        }
    }
}

/** 2x2 dot grid button — opens the app picker. */
@Composable
private fun GridButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val gridInter = remember { MutableInteractionSource() }
    val gridPressed by gridInter.collectIsPressedAsState()
    val gridScale by animateFloatAsState(
        if (gridPressed) 0.92f else 1f,
        spring(dampingRatio = 0.5f, stiffness = 900f), label = "gridScale"
    )
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = gridScale; scaleY = gridScale }
            .clip(RoundedCornerShape(size * 0.16f))
            .background(Color(0xFF1B1113))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(size * 0.16f))
            .clickable(gridInter, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(size * 0.09f)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(size * 0.09f)) {
                    repeat(2) {
                        Box(
                            Modifier
                                .size(size * 0.11f)
                                .clip(RoundedCornerShape(size * 0.03f))
                                .background(GC.InkText)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformBlock(
    score: Int,
    valueSize: androidx.compose.ui.unit.TextUnit,
    labelSize: androidx.compose.ui.unit.TextUnit,
    tickW: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$score%",
                style = MaterialTheme.typography.headlineSmall,
                color = GC.InkText,
                fontSize = valueSize
            )
            Spacer(Modifier.width(tickW * 0.4f))
            Text(
                "PERFORM",
                style = MaterialTheme.typography.labelMedium,
                color = GC.InkDim,
                fontSize = labelSize,
                letterSpacing = (labelSize.value * 0.14f).sp
            )
        }
        Spacer(Modifier.height(labelSize.value.dp * 0.8f))
        TickRow(filled = score / 10, segments = 10, tickW = tickW)
    }
}

@Composable
private fun TickRow(filled: Int, segments: Int, tickW: androidx.compose.ui.unit.Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(tickW * 0.28f)) {
        repeat(segments) { i ->
            Box(
                Modifier
                    .width(tickW)
                    .height(tickW * 0.28f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i < filled) accentNow() else Color(0x33FFFFFF))
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* stat wall                                                           */
/* ------------------------------------------------------------------ */

@Composable
private fun StatBox(
    title: String,
    value: String,
    good: Boolean,
    titleSize: androidx.compose.ui.unit.TextUnit,
    valueSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xB3100809))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = GC.InkDim,
            fontSize = titleSize,
            letterSpacing = (titleSize.value * 0.22f).sp,
            maxLines = 1,
            softWrap = false
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = GC.InkText,
                fontSize = valueSize,
                maxLines = 1,
                softWrap = false
            )
            Spacer(Modifier.width(5.dp))
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (good) Color(0xFF39D98A) else accentNow())
            )
        }
        footer()
    }
}

@Composable
private fun TempBar(celsius: Double, height: androidx.compose.ui.unit.Dp) {
    val pct = ((celsius.coerceIn(0.0, 80.0)) / 80.0).toFloat()
    val accent = accentNow()
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val h = size.height
        drawRoundRect(
            color = Color(0xFF2A2426),
            size = androidx.compose.ui.geometry.Size(size.width, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2)
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color(0xFF6BD34F), Color(0xFFF2C94C), accent)),
            size = androidx.compose.ui.geometry.Size(size.width * pct, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2)
        )
        val x = (size.width * pct).coerceIn(0f, size.width)
        drawCircle(Color.White, radius = h * 0.65f, center = Offset(x, h / 2))
    }
}

@Composable
private fun CpuCard(
    ghz: Double,
    loadPct: Int,
    titleSize: androidx.compose.ui.unit.TextUnit,
    valueSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xB3100809))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(10.dp))
    ) {
        // label
        Text(
            "CPU SPEED",
            style = MaterialTheme.typography.labelMedium,
            color = GC.InkDim,
            fontSize = titleSize,
            letterSpacing = (titleSize.value * 0.12f).sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 7.dp)
        )

        val accent = accentNow()
        val accentPair = accentPairNow()
        Canvas(Modifier.fillMaxSize().padding(top = titleSize.value.dp * 1.6f)) {
            val stroke = size.minDimension * 0.085f
            val radius = (size.minDimension / 2f) - stroke
            val arcCentre = Offset(size.width / 2f, size.height * 0.52f)
            val rect = androidx.compose.ui.geometry.Rect(
                center = arcCentre,
                radius = radius
            )
            drawArc(
                color = Color(0xFF2E2326),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(accentPair, accent, Color(0xFFFFFFFF), accent)
                ),
                startAngle = 135f,
                sweepAngle = 270f * (loadPct.coerceIn(0, 100) / 100f),
                useCenter = false,
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }

        Column(
            Modifier.align(Alignment.Center).padding(top = titleSize.value.dp * 1.2f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                String.format(java.util.Locale.US, "%.2f", ghz),
                style = MaterialTheme.typography.headlineMedium,
                color = GC.InkText,
                fontSize = valueSize
            )
            Text(
                "GHz",
                style = MaterialTheme.typography.labelMedium,
                color = GC.InkSoft,
                fontSize = (valueSize.value * 0.40f).sp
            )
        }

        Text(
            "$loadPct%",
            style = MaterialTheme.typography.labelMedium,
            color = accentNow(),
            fontSize = titleSize,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp)
        )
    }
}

/* ------------------------------------------------------------------ */
/* helpers                                                             */
/* ------------------------------------------------------------------ */

private fun formatMinutes(minutes: Long): String {
    if (minutes <= 0) return "0m"
    if (minutes < 60) return "${minutes}m"
    val hrs = minutes / 60
    val mins = minutes % 60
    return if (mins == 0L) "${hrs}h" else "${hrs}h ${mins}m"
}

private fun versionOf(context: android.content.Context, pkg: String?): String {
    if (pkg.isNullOrBlank()) return "?"
    return try {
        context.packageManager.getPackageInfo(pkg, 0).versionName ?: "?"
    } catch (_: Throwable) {
        "?"
    }
}

/** 0..100 health score from live CPU load, memory pressure and battery temperature. */
private fun performScore(cpuPct: Int, memPct: Int, temp: Double): Int {
    val cpuPart = (100 - cpuPct.coerceIn(0, 100)) * 0.55
    val memPart = (100 - memPct.coerceIn(0, 100)) * 0.30
    val tempScore = ((60.0 - temp.coerceIn(0.0, 60.0)) / 60.0) * 15.0
    return (cpuPart + memPart + tempScore).toInt().coerceIn(0, 100)
}

@Composable
private fun ControlCenter(
    onDismiss: () -> Unit,
    onTouchSettings: () -> Unit,
    onDisplaySettings: () -> Unit,
    onTextureSettings: () -> Unit,
    onPickApps: () -> Unit,
    onOpenPanel: () -> Unit,
    panelRunning: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("gamershub", android.content.Context.MODE_PRIVATE) }
    var memNote by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x9904050A))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(GC.InkPanel.copy(alpha = 0.98f))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(22.dp))
                .clickable(enabled = false) {}
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(20.dp).background(accentNow()))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "CONTROL CENTER",
                        style = MaterialTheme.typography.titleMedium,
                        color = GC.InkText,
                        fontSize = 17.sp,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        if (memNote.isBlank()) "8 live tools — every tile does something real"
                        else memNote,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (memNote.isBlank()) GC.InkDim else Color(0xFF39D98A),
                        fontSize = 11.sp
                    )
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    AIcon(Icons.Filled.Close, contentDescription = "Shut", tint = GC.InkText, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ToolTile(
                    icon = Icons.Filled.Videocam,
                    category = "CAPTURE",
                    title = if (Recorder.isRecording) "End clip" else "Screen clip",
                    active = Recorder.isRecording,
                    modifier = Modifier.weight(1f)
                ) {
                    if (Recorder.isRecording) RecorderService.stop(context)
                    else ProjectionActivity.launch(context, ProjectionActivity.MODE_RECORD)
                }
                ToolTile(
                    icon = Icons.Filled.Podcasts,
                    category = "LIVE",
                    title = if (Stream.isStreaming) "End air" else "Go On Air (RTMP)",
                    active = Stream.isStreaming,
                    modifier = Modifier.weight(1f)
                ) {
                    if (Stream.isStreaming) {
                        StreamService.stop(context)
                    } else {
                        val url = prefs.getString("rtmp_url", "") ?: ""
                        if (url.isBlank()) {
                            onDismiss()
                            runCatching {
                                context.startActivity(
                                    Intent(context, com.gamershub.MainActivity::class.java)
                                        .putExtra(com.gamershub.MainActivity.EXTRA_OPEN, "live")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        } else {
                            ProjectionActivity.launch(context, ProjectionActivity.MODE_LIVE, url)
                        }
                    }
                }
                ToolTile(
                    icon = Icons.Filled.Bolt,
                    category = "OVERLAY",
                    title = if (panelRunning) "Panel on" else "Panel off",
                    active = panelRunning,
                    modifier = Modifier.weight(1f)
                ) { onOpenPanel() }
                ToolTile(
                    icon = Icons.Filled.Memory,
                    category = "MEMORY",
                    title = "Quick Ram",
                    active = false,
                    modifier = Modifier.weight(1f)
                ) {
                    if (working) return@ToolTile
                    working = true
                    scope.launch {
                        val report = withContext(Dispatchers.IO) { com.gamershub.core.MemoryCleaner.boost(context) }
                        memNote = com.gamershub.core.MemoryCleaner.describe(report)
                        working = false
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ToolTile(
                    icon = Icons.Filled.CleaningServices,
                    category = "MEMORY",
                    title = "Deep wipe",
                    active = false,
                    modifier = Modifier.weight(1f)
                ) {
                    if (working) return@ToolTile
                    working = true
                    scope.launch {
                        val report = withContext(Dispatchers.IO) { com.gamershub.core.MemoryCleaner.deepClean(context) }
                        memNote = com.gamershub.core.MemoryCleaner.describe(report)
                        working = false
                    }
                }
                ToolTile(
                    icon = Icons.Filled.Tune,
                    category = "GRAPHICS",
                    title = "Surface · shade",
                    active = false,
                    modifier = Modifier.weight(1f)
                ) { onTextureSettings() }
                ToolTile(
                    icon = Icons.Filled.TouchApp,
                    category = "CONFIG",
                    title = "Tap settings",
                    active = false,
                    modifier = Modifier.weight(1f)
                ) { onTouchSettings() }
                ToolTile(
                    icon = Icons.Filled.Apps,
                    category = "APPS",
                    title = "My picks",
                    active = false,
                    modifier = Modifier.weight(1f)
                ) { onPickApps() }
            }
        }
    }
}

@Composable
private fun ToolTile(
    icon: ImageVector,
    category: String,
    title: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tileBg by animateColorAsState(
        if (active) accentNow().copy(alpha = 0.16f) else GC.InkPanelHi, tween(200), label = "tileBg"
    )
    val tileEdge by animateColorAsState(
        if (active) accentNow().copy(alpha = 0.70f) else Color(0x12FFFFFF), tween(200), label = "tileEdge"
    )
    val tileTint by animateColorAsState(
        if (active) accentNow() else GC.InkSoft, tween(200), label = "tileTint"
    )
    val tileInter = remember { MutableInteractionSource() }
    val tilePressed by tileInter.collectIsPressedAsState()
    val tileScale by animateFloatAsState(
        if (tilePressed) 0.94f else 1f,
        spring(dampingRatio = 0.5f, stiffness = 900f), label = "tileScale"
    )
    Column(
        modifier
            .graphicsLayer { scaleX = tileScale; scaleY = tileScale }
            .clip(RoundedCornerShape(14.dp))
            .background(tileBg)
            .border(1.dp, tileEdge, RoundedCornerShape(14.dp))
            .clickable(tileInter, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 12.dp)
    ) {
        AIcon(icon, contentDescription = null, tint = tileTint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(9.dp))
        Text(category, style = MaterialTheme.typography.labelMedium, color = GC.InkDim, fontSize = 8.sp, letterSpacing = 1.4.sp)
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = GC.InkText,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}


/** Per-game Texture Filtering panel (right side). */
@Composable
private fun TexturePanel(pkg: String, repo: AppRepo, appName: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("gamershub", android.content.Context.MODE_PRIVATE) }
    val engine = remember { com.gamershub.core.RenderEngine }
    // signed offsets: 0 = the game's own setting, negative below it, positive above it
    var texture by remember { mutableStateOf(prefs.getInt("ax_tex_off", 0)) }
    var shadow by remember { mutableStateOf(prefs.getInt("ax_shd_off", 0)) }
    var sharp by remember { mutableStateOf(prefs.getInt("ax_sharp", 0)) }
    var renderScale by remember { mutableStateOf(prefs.getInt("ax_res", 100)) }
    var lossless by remember { mutableStateOf(prefs.getInt("ax_lossless", 100)) }
    var fps by remember { mutableStateOf(prefs.getInt("ax_fps", 0)) }
    var mode by remember { mutableStateOf(prefs.getString("ax_gpu_mode", "standard") ?: "standard") }
    var angle by remember { mutableStateOf(prefs.getBoolean("ax_angle", false)) }
    var report by remember { mutableStateOf("") }
    var deviceNow by remember { mutableStateOf("--") }
    var working by remember { mutableStateOf(false) }

    fun persist() = prefs.edit()
        .putInt("ax_tex_off", texture)
        .putInt("ax_shd_off", shadow)
        .putInt("ax_sharp", sharp)
        .putInt("ax_res", renderScale)
        .putInt("ax_lossless", lossless)
        .putInt("ax_fps", fps)
        .putString("ax_gpu_mode", mode)
        .putBoolean("ax_angle", angle)
        .apply()

    fun apply() {
        persist()
        if (working) return
        working = true
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                engine.applyKnobs(
                    context = context,
                    pkg = pkg,
                    knobs = com.gamershub.core.GfxEngine.Knobs(
                        sharpen = sharp,
                        texture = texture,
                        shadow = shadow,
                        renderScale = renderScale,
                        fps = fps
                    ),
                    mode = mode,
                    angle = angle,
                    losslessPct = lossless
                )
            }
            report = r.details()
            deviceNow = r.verified
            working = false
            android.widget.Toast.makeText(context, r.summary(), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(pkg) {
        deviceNow = withContext(Dispatchers.IO) { engine.verify(pkg) }
    }

    val levels = listOf("LOW", "MEDIUM", "HIGH", "ULTRA")
    fun cycleLevel(cur: String, step: Int): String {
        val i = levels.indexOf(cur).let { if (it < 0) 2 else it }
        return levels[(i + step + levels.size) % levels.size]
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(14.dp)
                .width(560.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(GC.InkPanel)
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(22.dp))
                .clickable(enabled = false) {}
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "GPU PIPELINE · ${appName.uppercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = GC.InkDim,
                        fontSize = 10.sp,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        "Runs real commands",
                        style = MaterialTheme.typography.titleMedium,
                        color = GC.InkText,
                        fontSize = 20.sp
                    )
                    Text(
                        if (engine.shellReady()) engine.statusLine() else "Shizuku needed for real changes",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (engine.shellReady()) accentNow() else GC.InkDim,
                        fontSize = 11.sp
                    )
                }
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color(0x1AFFFFFF)).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) { AIcon(Icons.Filled.Close, contentDescription = "Shut", tint = GC.InkText, modifier = Modifier.size(16.dp)) }
            }
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x66000000))
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Stepper(
                    "NO-LOSS SCALE", if (lossless >= 100) "OFF" else "$lossless%",
                    onMinus = {
                    lossless = (lossless - 10).coerceAtLeast(50); apply()
                    },
                    onPlus = {
                    lossless = (lossless + 10).coerceAtMost(100); apply()
                    }
                )
                Divider()
                Stepper(
                    "RENDER SCALE (THIS APP)", "$renderScale%",
                    onMinus = {
                    renderScale = (renderScale - 10).coerceAtLeast(50); apply()
                    },
                    onPlus = {
                    renderScale = (renderScale + 10).coerceAtMost(100); apply()
                    }
                )
                Divider()
                Stepper(
                    "FPS CAP", if (fps == 0) "UNLIMITED" else "$fps",
                    onMinus = {
                    fps = fpsOf(fps, -1); apply()
                    },
                    onPlus = {
                    fps = fpsOf(fps, 1); apply()
                    }
                )
                Divider()
                Stepper(
                    "CHIP MODE", mode.uppercase(),
                    onMinus = {
                    mode = when (mode) { "standard" -> "battery"; "battery" -> "performance"; else -> "standard" }; apply()
                    },
                    onPlus = {
                    mode = when (mode) { "standard" -> "performance"; "performance" -> "battery"; else -> "standard" }; apply()
                    }
                )
                Divider()
                Stepper(
                    "TEXTURE", signedOffset(texture),
                    onMinus = {
                    texture = (texture - 1).coerceAtLeast(-3); apply()
                    },
                    onPlus = {
                    texture = (texture + 1).coerceAtMost(3); apply()
                    }
                )
                Divider()
                Stepper(
                    "SHADOW", signedOffset(shadow),
                    onMinus = {
                    shadow = (shadow - 1).coerceAtLeast(-3); apply()
                    },
                    onPlus = {
                    shadow = (shadow + 1).coerceAtMost(3); apply()
                    }
                )
                Divider()
                Stepper(
                    "CRISP", signedOffset(sharp),
                    onMinus = {
                    sharp = (sharp - 10).coerceAtLeast(-100); apply()
                    },
                    onPlus = {
                    sharp = (sharp + 10).coerceAtMost(100); apply()
                    }
                )
                Divider()
                GfxRow("ANGLE GL DRIVER", "Swap the GL driver for this app.", angle) {
                    angle = it; apply()
                }
                Divider()
                Text(
                    "DEVICE NOW: $deviceNow",
                    style = MaterialTheme.typography.labelMedium,
                    color = GC.InkSoft,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
                if (report.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        report,
                        style = MaterialTheme.typography.labelMedium,
                        color = GC.InkDim,
                        fontSize = 9.sp,
                        lineHeight = 13.sp
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // Explicit RUN and STOP — both always visible.
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentNow())
                        .clickable {
                            com.gamershub.core.upscale.UpscaleMirror.sharpen = sharp / 100f
                            apply()
                            runCatching { com.gamershub.core.upscale.UpscaleMirror.start(context) }
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("START DRAW", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 13.sp, letterSpacing = 1.2.sp)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x1AFFFFFF))
                        .clickable {
                            runCatching { com.gamershub.core.upscale.UpscaleMirror.stop(context) }
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { engine.restore(context, pkg) }
                                report = r.details()
                                deviceNow = r.verified
                                lossless = 100
                            }
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("HALT DRAW", style = MaterialTheme.typography.labelLarge, color = GC.InkText, fontSize = 13.sp, letterSpacing = 1.2.sp)
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentNow())
                        .clickable { apply() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("USE NOW", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 13.sp, letterSpacing = 1.4.sp)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x1AFFFFFF))
                        .clickable {
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { engine.restore(context, pkg) }
                                report = r.details()
                                deviceNow = r.verified
                                lossless = 100
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("PUT BACK", style = MaterialTheme.typography.labelLarge, color = GC.InkText, fontSize = 13.sp, letterSpacing = 1.2.sp)
                }
            }
        }
    }
}

/**
 * Swipe left / right anywhere on the featured card to move the selection through the
 * library. The strip stays scrollable; this is the gesture you asked for.
 */
private fun Modifier.swipeToSwitchApp(
    library: List<com.gamershub.data.InstalledApp>,
    currentPkg: String,
    onSwitch: (String) -> Unit
): Modifier = this.pointerInput(currentPkg, library) {
    if (library.size < 2) return@pointerInput
    var travelled = 0f
    detectHorizontalDragGestures(
        onDragStart = { travelled = 0f },
        onDragEnd = {
            val index = library.indexOfFirst { it.pkg == currentPkg }
            if (index >= 0 && kotlin.math.abs(travelled) > 55f) {
                val step = if (travelled < 0) 1 else -1
                val next = ((index + step) % library.size + library.size) % library.size
                onSwitch(library[next].pkg)
            }
            travelled = 0f
        },
        onDragCancel = { travelled = 0f }
    ) { change, dragAmount ->
        travelled += dragAmount
        change.consume()
    }
}

/** 0 renders as DEFAULT; anything else shows its sign — offsets, not absolute levels. */
private fun signedOffset(value: Int): String = when {
    value == 0 -> "DEFAULT"
    value > 0 -> "+$value"
    else -> "$value"
}

/** Small +/- stepper used by the GPU pipeline panel. */
@Composable
private fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = GC.InkSoft,
            fontSize = 11.sp,
            letterSpacing = 1.1.sp,
            modifier = Modifier.weight(1f)
        )
        KeyBox("−", onMinus)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = GC.InkText,
            fontSize = 13.sp,
            modifier = Modifier.width(96.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        KeyBox("+", onPlus)
    }
}

@Composable
private fun KeyBox(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x1FFFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = GC.InkText, fontSize = 16.sp, style = MaterialTheme.typography.labelLarge)
    }
}

private fun fpsOf(current: Int, step: Int): Int {
    val steps = listOf(0, 30, 45, 60, 90, 120, 144)
    val i = steps.indexOf(current).let { if (it < 0) 3 else it }
    return steps[(i + step).coerceIn(0, steps.size - 1)]
}

@Composable
private fun GfxRow(title: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 9.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 16.sp)
            Spacer(Modifier.height(2.dp))
            Text(desc, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Toggle(on = on, onCheckedChange = onChange)
    }
}

@Composable
private fun SectionBar(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Box(Modifier.width(3.dp).height(16.dp).background(LocalAccent.current))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = GC.TextSoft, fontSize = 12.sp, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x22FFFFFF)))
}

@Composable
private fun PresetChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent else Color(0xFF241718))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else GC.TextSoft, fontSize = 13.sp)
    }
}

private fun currentTime(): String {
    val cal = Calendar.getInstance()
    val h = cal.get(Calendar.HOUR)
    val m = cal.get(Calendar.MINUTE)
    val ampm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    return String.format("%d:%02d %s", if (h == 0) 12 else h, m, ampm)
}

private fun envLevelOf(context: android.content.Context): Int {
    var level = 0
    if (Settings.canDrawOverlays(context)) level++
    if (Settings.System.canWrite(context)) level++
    return level.coerceAtMost(3)
}

/** Shizuku-first system setting write (shell identity beats every restriction). */
internal fun putSystemSetting(context: Context, name: String, value: Int): Boolean {
    com.gamershub.core.shizuku.Shell.ensure(context)
    if (com.gamershub.core.shizuku.Shell.ok("settings put system '$name' '$value'")) return true
    return try {
        android.provider.Settings.System.putInt(context.contentResolver, name, value)
    } catch (_: Throwable) {
        false
    }
}

internal fun putGlobalSetting(context: Context, name: String, value: String): Boolean {
    com.gamershub.core.shizuku.Shell.ensure(context)
    return com.gamershub.core.shizuku.Shell.ok("settings put global '$name' '$value'") ||
        try {
            android.provider.Settings.Global.putString(context.contentResolver, name, value)
        } catch (_: Throwable) { false }
}

internal fun putSecureSetting(context: Context, name: String, value: Int): Boolean {
    com.gamershub.core.shizuku.Shell.ensure(context)
    return com.gamershub.core.shizuku.Shell.ok("settings put secure '$name' '$value'") ||
        try {
            android.provider.Settings.Secure.putInt(context.contentResolver, name, value)
        } catch (_: Throwable) { false }
}

/** Raw shell command (Shizuku when available, app-side fallback). */
internal fun shellExec(context: Context, command: String): String {
    com.gamershub.core.shizuku.Shell.ensure(context)
    return com.gamershub.core.shizuku.Shell.exec(command) ?: com.gamershub.core.shizuku.Shell.appExec(command)
}

/** Real game booster: kill background apps + drop caches. Returns MB freed. */
internal fun boostNow(context: Context): Int {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val before = com.gamershub.data.DeviceStats.mem(context).let { it.totalMb - it.usedMb }
    com.gamershub.core.shizuku.Shell.ensure(context)
    runCatching {
        am.runningAppProcesses?.forEach { p ->
            if (p.processName != context.packageName) {
                com.gamershub.core.shizuku.Shell.ok("am kill ${p.processName}")
                am.killBackgroundProcesses(p.processName)
            }
        }
    }
    com.gamershub.core.shizuku.Shell.ok("echo 3 > /proc/sys/vm/drop_caches")
    val after = com.gamershub.data.DeviceStats.mem(context).let { it.totalMb - it.usedMb }
    return (after - before).coerceAtLeast(0L).toInt()
}

internal fun feedbackOnWrite(context: Context, ok: Boolean) {
    if (ok) return
    // Never fail silently: tell the user exactly what to do and take them
    // to the real grant page for THIS app.
    android.widget.Toast.makeText(
        context,
        "Grant 'Modify System Preferences' to GamersHub — opening Preferences…",
        android.widget.Toast.LENGTH_LONG
    ).show()
    val opened = runCatching {
        context.startActivity(
            android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS,
                android.net.Uri.parse("package:${context.packageName}")
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
    if (!opened) {
        runCatching {
            context.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${context.packageName}")
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/** Touch Settings side panel — real system writes for taps / coordinates. */
@Composable
private fun TouchPanel(repo: AppRepo, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var magic by remember { mutableStateOf(repo.toggle("magic_mapper")) }
    var taps by remember { mutableStateOf(repo.toggle("show_taps")) }
    var coords by remember { mutableStateOf(repo.toggle("show_coords")) }
    var hw by remember { mutableStateOf(repo.toggle("hw_touch")) }
    SidePanel(title = "Tap Likes", subtitle = "SETUPS", active = magic || taps || coords || hw, onDismiss = onDismiss) {
        GfxRow("Magic Pad Mapper", "Turn on the pad mapper", magic) {
            magic = it; repo.setToggle("magic_mapper", it)
        }
        Divider()
        GfxRow("Show Taps", "Draw a visible marker where you touch.", taps) {
            val ok = putSystemSetting(context, "show_touches", if (it) 1 else 0)
            if (ok) { taps = it; repo.setToggle("show_taps", it) }
            feedbackOnWrite(context, ok)
        }
        Divider()
        GfxRow("Show Pointers", "Show pointer position overlay while touching.", coords) {
            val ok = putSystemSetting(context, "pointer_location", if (it) 1 else 0)
            if (ok) { coords = it; repo.setToggle("show_coords", it) }
            feedbackOnWrite(context, ok)
        }
        Divider()
        GfxRow("HW Touch Booster", "Speed touch response via Hardware Composer", hw) {
            hw = it; repo.setToggle("hw_touch", it)
        }
    }
}

/** Display Settings side panel — real refresh-rate and rotation control. */
@Composable
private fun DisplayPanel(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var refreshStep by remember { mutableStateOf(0) }
    var autoRotate by remember {
        mutableStateOf(
            try {
                Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1
            } catch (_: Throwable) { true }
        )
    }
    SidePanel(title = "Screen Likes", subtitle = "SETUPS", active = true, onDismiss = onDismiss) {
        Text("Screen Pace", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("60Hz", "90Hz", "120Hz", "144Hz").forEachIndexed { i, label ->
                PresetChip(label, refreshStep == i) {
                    val prev = refreshStep
                    refreshStep = i
                    val hz = when (i) { 1 -> 90f; 2 -> 120f; 3 -> 144f; else -> 60f }
                    // real write through the shell (float settings + MIUI key), verified
                    val ok = com.gamershub.core.GfxEngine.setRefreshRate(hz.toInt())
                    if (!ok) {
                        refreshStep = prev
                        android.widget.Toast.makeText(
                            context,
                            com.gamershub.core.GfxEngine.refreshSummary(context),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "Refresh → ${hz.toInt()} Hz · " + com.gamershub.core.GfxEngine.refreshSummary(context),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        Divider()
        GfxRow("Auto-rotate", "Rotate the screen with the device orientation.", autoRotate) {
            val ok = putSystemSetting(context, Settings.System.ACCELEROMETER_ROTATION, if (it) 1 else 0)
            if (ok) autoRotate = it
            feedbackOnWrite(context, ok)
        }
        Divider()
        val dm = context.resources.displayMetrics
        Text(
            "Panel: ${dm.widthPixels}x${dm.heightPixels} · ${dm.densityDpi}dp",
            style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp
        )
    }
}

/** Shared right-side panel frame (same look as Texture Filtering). */
@Composable
private fun SidePanel(
    title: String,
    subtitle: String,
    active: Boolean,
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x55000000))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(14.dp)
                .width(520.dp)
                .clip(CutCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomEnd = 6.dp, bottomStart = 22.dp))
                .background(Color(0xF5170E0F))
                .clickable(enabled = false) {}
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 10.sp, letterSpacing = 1.2.sp)
                    Text(title, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 21.sp)
                    Text(
                        if (active) "Active" else "Not active",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) accent else GC.TextDim, fontSize = 12.sp
                    )
                }
                Box(
                    Modifier.clip(CutCornerShape(4.dp)).background(Color(0x22FFFFFF)).clickable(onClick = onDismiss).padding(10.dp)
                ) { AIcon(Icons.Filled.Close, contentDescription = "Shut", tint = GC.Text, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1B1213))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                content = content
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* app strip + app picker                                              */
/* ------------------------------------------------------------------ */

@Composable
private fun AppChip(entry: InstalledApp, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.20f) else Color(0x14FFFFFF))
            .then(
                if (selected) Modifier.border(1.dp, accent, RoundedCornerShape(12.dp)) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp)
    ) {
        Image(
            painter = androidx.compose.ui.graphics.painter.BitmapPainter(entry.icon),
            contentDescription = entry.name,
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
        )
        Spacer(Modifier.height(3.dp))
        Text(
            entry.name.take(9),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) accent else GC.TextSoft,
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AppsPanel(
    library: List<InstalledApp>,
    currentPkg: String,
    onSelect: (String) -> Unit,
    onLaunch: (String) -> Unit,
    onPickApps: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 460.dp)
                .clip(CutCornerShape(topStart = 6.dp, topEnd = 24.dp, bottomEnd = 6.dp, bottomStart = 24.dp))
                .background(Color(0xF0120709))
                .border(1.dp, Color(0x44FFFFFF), CutCornerShape(topStart = 6.dp, topEnd = 24.dp, bottomEnd = 6.dp, bottomStart = 24.dp))
                .clickable { }
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AIcon(Icons.Filled.Apps, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("MY PICKS", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                    Text(
                        "${library.size} selected · tap to choose, LAUNCH to open",
                        style = MaterialTheme.typography.labelMedium,
                        color = GC.TextDim,
                        fontSize = 11.sp
                    )
                }
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Color(0x22FFFFFF)).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    AIcon(Icons.Filled.Close, contentDescription = "Shut", tint = GC.Text, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            if (library.isEmpty()) {
                Text(
                    "No apps selected yet. Tap ADD APPS to choose the apps you want in this Play Hub.",
                    style = MaterialTheme.typography.labelLarge,
                    color = GC.TextSoft,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(accent)
                        .clickable(onClick = onPickApps)
                        .padding(horizontal = 18.dp, vertical = 11.dp)
                ) {
                    AIcon(Icons.Filled.Apps, contentDescription = null, tint = Color(0xFF17100A), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("PICK APPS", style = MaterialTheme.typography.labelLarge, color = Color(0xFF17100A), fontSize = 13.sp, letterSpacing = 1.sp)
                }
            } else {
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    library.chunked(4).forEach { rowApps ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowApps.forEach { entry ->
                                val selected = entry.pkg == currentPkg
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(if (selected) accent.copy(alpha = 0.18f) else Color(0x14FFFFFF))
                                        .then(
                                            if (selected) Modifier.border(1.dp, accent, RoundedCornerShape(13.dp))
                                            else Modifier
                                        )
                                        .clickable { onSelect(entry.pkg) }
                                        .padding(vertical = 9.dp)
                                ) {
                                    Image(
                                        painter = androidx.compose.ui.graphics.painter.BitmapPainter(entry.icon),
                                        contentDescription = entry.name,
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp))
                                    )
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        entry.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) accent else GC.Text,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            repeat(4 - rowApps.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CutCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomEnd = 2.dp, bottomStart = 12.dp))
                    .background(accent)
                    .clickable { if (currentPkg.isNotBlank()) onLaunch(currentPkg) }
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "LAUNCH ${library.firstOrNull { it.pkg == currentPkg }?.name ?: ""}".trim(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontSize = 15.sp,
                    letterSpacing = 2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
