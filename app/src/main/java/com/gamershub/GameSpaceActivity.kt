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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.gamershub.data.AppRepo
import com.gamershub.data.InstalledApp
import com.gamershub.data.DeviceStats
import com.gamershub.overlay.FloatingPanelService
import com.gamershub.theme.GC
import com.gamershub.theme.GameCornerTheme
import com.gamershub.theme.LocalAccent
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
            GameCornerTheme(repo.accentIndex()) {
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
    // Every selected app, each with its real launcher icon. The app we were
    // started with is added to the selection if it is not there yet, so its icon
    // and name always resolve.
    var library by remember { mutableStateOf(repo.addedApps()) }
    var currentPkg by remember { mutableStateOf(pkg.ifBlank { library.firstOrNull()?.pkg ?: "" }) }
    LaunchedEffect(pkg) {
        if (pkg.isNotBlank() && !repo.isAdded(pkg)) repo.setAdded(pkg, true)
        library = repo.addedApps()
        if (currentPkg.isBlank()) currentPkg = pkg.ifBlank { library.firstOrNull()?.pkg ?: "" }
    }
    val current = library.firstOrNull { it.pkg == currentPkg }
    val currentIcon = remember(currentPkg, library) { current?.icon ?: repo.iconFor(currentPkg) }
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
        while (true) { value = DeviceStats.mem(context); delay(2000) }
    }
    val cpu by produceState(initialValue = DeviceStats.cpu()) {
        while (true) { value = DeviceStats.cpu(); delay(2000) }
    }
    val battery by produceState(initialValue = DeviceStats.battery(context)) {
        while (true) { value = DeviceStats.battery(context); delay(5000) }
    }
    val ping by produceState(initialValue = -1.0) {
        while (true) {
            value = withContext(Dispatchers.IO) { DeviceStats.pingExact() }
            delay(5000)
        }
    }
    var clock by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) { clock = currentTime(); delay(1000) }
    }

    val freeMb = (mem.totalMb - mem.usedMb).coerceAtLeast(0)
    val level = envLevelOf(context)
    val perform = performScore(cpu.pct, mem.pct, battery.second)

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(GC.Void, GC.VoidMid, GC.Void)))) {
        HexBackdrop(Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            GsHeader(
                freeTens = (freeMb / 10).toInt(),
                totalTens = (mem.totalMb / 10).toInt(),
                battery = battery.first,
                clock = clock,
                level = level,
                tab = when {
                    showControl -> 1
                    showTexture -> 2
                    showApps -> 3
                    else -> 0
                },
                onTab = { index ->
                    when (index) {
                        1 -> { showControl = true; showTexture = false; showApps = false }
                        2 -> { showTexture = true; showControl = false; showApps = false }
                        3 -> { showApps = true; showControl = false; showTexture = false }
                        else -> { showControl = false; showTexture = false; showApps = false }
                    }
                },
                onClose = onClose
            )

            Row(Modifier.weight(1f).padding(top = 12.dp)) {
                // ---- left: app cards + launch ----
                Column(Modifier.weight(1.62f)) {
                    val others = library.filter { it.pkg != currentPkg }
                    Row(Modifier.weight(1f)) {
                        AppCard(
                            entry = current,
                            name = appName,
                            version = appVersion,
                            minutes = repo.playMinutes(currentPkg),
                            primary = true,
                            selected = true,
                            emptyLabel = if (library.isEmpty()) "ADD APPS" else "SELECT AN APP",
                            onSelect = { if (library.isEmpty()) showApps = true },
                            onFilter = { showTexture = true; showControl = false; showApps = false },
                            modifier = Modifier.weight(1.42f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppCard(
                                entry = others.getOrNull(0),
                                name = others.getOrNull(0)?.name ?: "EMPTY SLOT",
                                version = versionOf(context, others.getOrNull(0)?.pkg),
                                minutes = repo.playMinutes(others.getOrNull(0)?.pkg ?: ""),
                                primary = false,
                                selected = false,
                                emptyLabel = "TAP + TO PICK",
                                onSelect = {
                                    val p = others.getOrNull(0)?.pkg
                                    if (p != null) currentPkg = p else showApps = true
                                },
                                onFilter = null,
                                modifier = Modifier.weight(1f)
                            )
                            AppCard(
                                entry = others.getOrNull(1),
                                name = others.getOrNull(1)?.name ?: "EMPTY SLOT",
                                version = versionOf(context, others.getOrNull(1)?.pkg),
                                minutes = repo.playMinutes(others.getOrNull(1)?.pkg ?: ""),
                                primary = false,
                                selected = false,
                                emptyLabel = "TAP + TO PICK",
                                onSelect = {
                                    val p = others.getOrNull(1)?.pkg
                                    if (p != null) currentPkg = p else showApps = true
                                },
                                onFilter = null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        appName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = GC.InkText,
                        fontSize = 27.sp,
                        letterSpacing = 0.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        currentPkg.ifBlank { "no package" },
                        style = MaterialTheme.typography.labelMedium,
                        color = GC.InkDim,
                        fontSize = 11.sp,
                        letterSpacing = 0.6.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LaunchBar(
                            freeMb = freeMb,
                            enabled = currentPkg.isNotBlank(),
                            onLaunch = { if (currentPkg.isNotBlank()) onLaunch(currentPkg) },
                            onBoost = { showControl = true }
                        )
                        Spacer(Modifier.width(10.dp))
                        GridButton { showApps = true }
                        Spacer(Modifier.weight(1f))
                        PerformBlock(score = perform)
                    }
                }

                Spacer(Modifier.width(16.dp))

                // ---- right: watermark + stat wall ----
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Text(
                        "${repo.userId()} · ${android.os.Process.myPid()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0x3DFFFFFF),
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatBox(
                            title = "PING INTERVAL",
                            value = if (ping >= 0) String.format("%.1f ms", ping) else "-- ms",
                            good = ping in 0.0..80.0,
                            modifier = Modifier.weight(1f)
                        ) {
                            TickRow(filled = if (ping >= 0) (10 - (ping / 12).toInt()).coerceIn(0, 10) else 0, segments = 10)
                        }
                        StatBox(
                            title = "TEMPERATURE",
                            value = String.format("%.1f°C", battery.second.coerceAtLeast(0.0)),
                            good = battery.second in 0.0..45.0,
                            modifier = Modifier.weight(1f)
                        ) {
                            TempBar(celsius = battery.second)
                        }
                        CpuCard(
                            ghz = cpu.curGhz,
                            loadPct = cpu.pct,
                            modifier = Modifier.weight(1.06f).height(148.dp)
                        )
                    }
                }
            }
        }

        // ---- panels ----
        if (showControl) {
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
}

/* ------------------------------------------------------------------ */
/* header                                                              */
/* ------------------------------------------------------------------ */

/** Slanted hex-ish tile used by the four navigation chevrons. */
private val ChevShape = GenericShape { size, _ ->
    val cut = size.height * 0.34f
    moveTo(cut, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - cut, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
private fun GsHeader(
    freeTens: Int,
    totalTens: Int,
    battery: Int,
    clock: String,
    level: Int,
    tab: Int,
    onTab: (Int) -> Unit,
    onClose: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        HeroMark(Modifier.size(44.dp))
        Spacer(Modifier.width(9.dp))
        Column {
            Text("GAME", style = MaterialTheme.typography.labelMedium, color = GC.InkSoft, fontSize = 10.sp, letterSpacing = 3.sp)
            Text("HUB", style = MaterialTheme.typography.titleLarge, color = GC.InkText, fontSize = 25.sp, letterSpacing = 0.5.sp)
        }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.width(1.dp).height(32.dp).background(Color(0x26FFFFFF)))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                "LVL $level/3",
                style = MaterialTheme.typography.labelLarge,
                color = GC.InkText,
                fontSize = 12.sp,
                letterSpacing = 1.6.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier
                            .width(22.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i < level) GC.Signal else Color(0x33FFFFFF))
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            ChevronTab(Icons.Filled.Bolt, tab == 0) { onTab(0) }
            ChevronTab(Icons.Filled.Speed, tab == 1) { onTab(1) }
            ChevronTab(Icons.Filled.Tune, tab == 2) { onTab(2) }
            ChevronTab(Icons.Filled.Apps, tab == 3) { onTab(3) }
        }

        Spacer(Modifier.weight(1f))

        // energy / memory chip
        Row(verticalAlignment = Alignment.CenterVertically) {
            AIcon(Icons.Filled.Bolt, contentDescription = "Free memory", tint = GC.Signal, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                "$freeTens",
                style = MaterialTheme.typography.titleMedium,
                color = GC.InkText,
                fontSize = 16.sp
            )
            Spacer(Modifier.width(3.dp))
            Text(
                "/ $totalTens",
                style = MaterialTheme.typography.labelMedium,
                color = GC.InkDim,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.width(1.dp).height(30.dp).background(Color(0x26FFFFFF)))
        Spacer(Modifier.width(16.dp))

        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BatteryGlyph(pct = battery)
                Spacer(Modifier.width(7.dp))
                Text(
                    "${battery.coerceAtLeast(0)}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = GC.InkText,
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(clock, style = MaterialTheme.typography.labelLarge, color = GC.InkSoft, fontSize = 14.sp)
        }
        Spacer(Modifier.width(16.dp))
        Box(
            Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            AIcon(Icons.Filled.PowerSettingsNew, contentDescription = "Close Game Space", tint = GC.InkText, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun ChevronTab(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .width(70.dp)
            .height(46.dp)
            .clip(ChevShape)
            .background(if (selected) GC.Signal else Color(0xFF141827))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AIcon(
            icon,
            contentDescription = null,
            tint = if (selected) Color.White else GC.InkSoft,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun BatteryGlyph(pct: Int) {
    Canvas(Modifier.width(28.dp).height(14.dp)) {
        val body = size.width - 3.dp.toPx()
        val radius = 3.dp.toPx()
        val fill = (pct.coerceIn(0, 100) / 100f)
        drawRoundRect(
            color = GC.Signal.copy(alpha = 0.35f),
            size = androidx.compose.ui.geometry.Size(body, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius)
        )
        drawRoundRect(
            color = GC.Signal,
            size = androidx.compose.ui.geometry.Size((body - 2.dp.toPx()) * fill, size.height - 2.dp.toPx()),
            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius)
        )
        drawRoundRect(
            color = GC.Signal.copy(alpha = 0.7f),
            size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height * 0.42f),
            topLeft = Offset(body + 1.dp.toPx(), size.height * 0.29f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
        )
    }
}

/* ------------------------------------------------------------------ */
/* app card + launch block                                             */
/* ------------------------------------------------------------------ */

@Composable
private fun AppCard(
    entry: InstalledApp?,
    name: String,
    version: String,
    minutes: Long,
    primary: Boolean,
    selected: Boolean,
    emptyLabel: String,
    onSelect: () -> Unit,
    onFilter: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(if (primary) 22.dp else 18.dp)
    Box(
        modifier
            .clip(shape)
            .background(GC.InkPanel)
            .then(
                if (selected && primary) Modifier.border(1.5.dp, GC.Signal.copy(alpha = 0.85f), shape)
                else Modifier.border(1.dp, Color(0x14FFFFFF), shape)
            )
            .clickable(onClick = onSelect)
    ) {
        if (entry != null) {
            // Icon blown up + blurred + tinted = a per-app wash so every card looks
            // like a game still instead of a flat rectangle.
            Image(
                painter = androidx.compose.ui.graphics.painter.BitmapPainter(entry.icon),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = 2.6f, scaleY = 2.6f, alpha = 0.85f)
                    .then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(24.dp) else Modifier)
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x33000000), Color(0x99101220), Color(0xF2060810))
                        )
                    )
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF141828), Color(0xFF0B0D16)))
                    )
            )
        }

        if (onFilter != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC0A0C16))
                    .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(12.dp))
                    .clickable(onClick = onFilter),
                contentAlignment = Alignment.Center
            ) {
                AIcon(Icons.Filled.Tune, contentDescription = "Texture filtering", tint = GC.InkText, modifier = Modifier.size(19.dp))
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 14.dp, bottom = 13.dp)
                .fillMaxWidth()
        ) {
            Box(
                Modifier
                    .size(if (primary) 58.dp else 40.dp)
                    .clip(RoundedCornerShape(if (primary) 17.dp else 12.dp))
                    .background(Color(0x1FFFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                if (entry != null) {
                    Image(
                        painter = androidx.compose.ui.graphics.painter.BitmapPainter(entry.icon),
                        contentDescription = name,
                        modifier = Modifier
                            .size(if (primary) 54.dp else 38.dp)
                            .clip(RoundedCornerShape(if (primary) 15.dp else 11.dp))
                    )
                } else {
                    AIcon(Icons.Filled.Apps, contentDescription = null, tint = GC.InkDim, modifier = Modifier.size(if (primary) 26.dp else 18.dp))
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (entry != null) name else emptyLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (entry != null) Color.White else GC.InkSoft,
                    fontSize = if (primary) 20.sp else 13.sp,
                    letterSpacing = 0.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (entry != null) "${formatMinutes(minutes)} · v$version" else "tap to choose",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (entry != null) Color(0xCCFFFFFF) else GC.InkDim,
                    fontSize = if (primary) 12.sp else 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun LaunchBar(freeMb: Long, enabled: Boolean, onLaunch: () -> Unit, onBoost: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .height(54.dp)
            .clip(shape)
            .background(
                if (enabled) Brush.horizontalGradient(listOf(GC.Signal, GC.SignalDeep))
                else Brush.horizontalGradient(listOf(Color(0xFF2A2E42), Color(0xFF1B1E2C)))
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxHeight()
                .clickable(enabled = enabled, onClick = onLaunch)
                .padding(horizontal = 24.dp)
        ) {
            Text(
                "LAUNCH",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontSize = 18.sp,
                letterSpacing = 2.4.sp
            )
        }
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 12.dp).background(Color(0x40FFFFFF)))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxHeight()
                .clickable(onClick = onBoost)
                .padding(horizontal = 14.dp)
        ) {
            AIcon(Icons.Filled.Bolt, contentDescription = "Memory boost", tint = Color.White, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                "${(freeMb / 100).coerceAtLeast(0)}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

/** The 2x2 dot grid button that opens the app picker, drawn (not an icon) so it matches the reference. */
@Composable
private fun GridButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(GC.InkPanelHi)
            .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(2) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(GC.InkText)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformBlock(score: Int) {
    Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$score%",
                style = MaterialTheme.typography.headlineSmall,
                color = GC.InkText,
                fontSize = 25.sp
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "PERFORM",
                style = MaterialTheme.typography.labelMedium,
                color = GC.InkDim,
                fontSize = 11.sp,
                letterSpacing = 1.8.sp
            )
        }
        Spacer(Modifier.height(7.dp))
        TickRow(filled = score / 10, segments = 10)
    }
}

@Composable
private fun TickRow(filled: Int, segments: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(segments) { i ->
            Box(
                Modifier
                    .width(13.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (i < filled) GC.Signal else Color(0x33FFFFFF))
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
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GC.InkPanel)
            .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = GC.InkDim,
            fontSize = 9.sp,
            letterSpacing = 1.9.sp,
            maxLines = 1
        )
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = GC.InkText,
                fontSize = 20.sp,
                maxLines = 1
            )
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (good) Color(0xFF39D98A) else GC.Signal)
            )
        }
        Spacer(Modifier.height(10.dp))
        footer()
    }
}

/** Thin temperature rail with a moving marker, like the reference panel. */
@Composable
private fun TempBar(celsius: Double) {
    val pct = ((celsius.coerceIn(0.0, 80.0)) / 80.0).toFloat()
    Box(Modifier.fillMaxWidth().height(8.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height
            drawRoundRect(
                color = Color(0xFF232841),
                size = androidx.compose.ui.geometry.Size(size.width, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2)
            )
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color(0xFF39D98A), Color(0xFFF2C94C), GC.Signal)),
                size = androidx.compose.ui.geometry.Size(size.width * pct, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2)
            )
            val x = (size.width * pct).coerceIn(0f, size.width)
            drawCircle(Color.White, radius = h * 0.75f, center = Offset(x, h / 2))
        }
    }
}

@Composable
private fun CpuCard(ghz: Double, loadPct: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GC.InkPanel)
            .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
            .padding(6.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2 + 4.dp.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(
                size.width - inset * 2,
                size.height - inset * 2 + 26.dp.toPx()
            )
            drawArc(
                color = Color(0xFF232841),
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(GC.Signal, GC.SignalGlow, GC.Signal)),
                startAngle = 140f,
                sweepAngle = 260f * (loadPct.coerceIn(0, 100) / 100f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "CPU SPEED",
                style = MaterialTheme.typography.labelMedium,
                color = GC.InkDim,
                fontSize = 9.sp,
                letterSpacing = 1.6.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                String.format("%.2f", ghz),
                style = MaterialTheme.typography.headlineMedium,
                color = GC.InkText,
                fontSize = 23.sp
            )
            Text("GHz · $loadPct% load", style = MaterialTheme.typography.labelMedium, color = GC.InkSoft, fontSize = 9.sp)
        }
    }
}

/* ------------------------------------------------------------------ */
/* helpers                                                             */
/* ------------------------------------------------------------------ */

private fun formatMinutes(minutes: Long): String {
    if (minutes <= 0) return "new"
    if (minutes < 60) return "${minutes}m"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0L) "${h}h" else "${h}h ${m}m"
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
                Box(Modifier.width(3.dp).height(20.dp).background(GC.Signal))
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
                    AIcon(Icons.Filled.Close, contentDescription = "Close", tint = GC.InkText, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ToolTile(
                    icon = Icons.Filled.Videocam,
                    category = "CAPTURE",
                    title = if (Recorder.isRecording) "Stop recording" else "Screen record",
                    active = Recorder.isRecording,
                    modifier = Modifier.weight(1f)
                ) {
                    if (Recorder.isRecording) RecorderService.stop(context)
                    else ProjectionActivity.launch(context, ProjectionActivity.MODE_RECORD)
                }
                ToolTile(
                    icon = Icons.Filled.Podcasts,
                    category = "LIVE",
                    title = if (Stream.isStreaming) "Stop stream" else "Go Live (RTMP)",
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
                    title = if (panelRunning) "Pop-up panel on" else "Pop-up panel off",
                    active = panelRunning,
                    modifier = Modifier.weight(1f)
                ) { onOpenPanel() }
                ToolTile(
                    icon = Icons.Filled.Memory,
                    category = "MEMORY",
                    title = "Boost RAM",
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
                    title = "Deep clean",
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
                    title = "Texture · shadow",
                    active = false,
                    modifier = Modifier.weight(1f)
                ) { onTextureSettings() }
                ToolTile(
                    icon = Icons.Filled.TouchApp,
                    category = "CONFIG",
                    title = "Touch settings",
                    active = false,
                    modifier = Modifier.weight(1f)
                ) { onTouchSettings() }
                ToolTile(
                    icon = Icons.Filled.Apps,
                    category = "APPS",
                    title = "My apps",
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
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) GC.Signal.copy(alpha = 0.16f) else GC.InkPanelHi)
            .border(
                1.dp,
                if (active) GC.Signal.copy(alpha = 0.7f) else Color(0x12FFFFFF),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 12.dp)
    ) {
        AIcon(icon, contentDescription = null, tint = if (active) GC.Signal else GC.InkSoft, modifier = Modifier.size(19.dp))
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
    val accent = LocalAccent.current
    var sharp by remember { mutableStateOf(repo.gfx(pkg, "sharp", false)) }
    var hard by remember { mutableStateOf(repo.gfx(pkg, "hard", false)) }
    var aniso by remember { mutableStateOf(repo.gfx(pkg, "aniso", false)) }
    var angle by remember { mutableStateOf(repo.gfx(pkg, "angle", false)) }
    var res by remember { mutableStateOf(repo.gfxRes(pkg)) }
    var preset by remember { mutableStateOf(repo.gfxPreset(pkg)) }

    fun applyPreset(i: Int) {
        preset = i
        when (i) {
            1 -> { sharp = true; hard = false; aniso = false; res = 1 }
            2 -> { sharp = true; hard = true; aniso = true; res = 2 }
            3 -> { sharp = false; hard = false; aniso = true; res = 3 }
            else -> { sharp = false; hard = false; aniso = false; res = 0 }
        }
    }

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
                .width(560.dp)
                .clip(CutCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomEnd = 6.dp, bottomStart = 22.dp))
                .background(Color(0xF5170E0F))
                .clickable(enabled = false) {}
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF241718)), contentAlignment = Alignment.Center) {
                    AIcon(Icons.Filled.Memory, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("GRAPHICS · $appName", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 10.sp, letterSpacing = 1.2.sp)
                    Text("Texture Filtering", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 21.sp)
                    Text(
                        if (sharp || hard || aniso || res != 0 || angle) "Active" else "Not active",
                        style = MaterialTheme.typography.labelMedium, color = if (sharp || hard || aniso || res != 0 || angle) accent else GC.TextDim, fontSize = 12.sp
                    )
                }
                Box(
                    Modifier.clip(CutCornerShape(4.dp)).background(Color(0x22FFFFFF)).clickable(onClick = onDismiss).padding(10.dp)
                ) { AIcon(Icons.Filled.Close, contentDescription = "Close", tint = GC.Text, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1B1213))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                GfxRow("ANGLE renderer", "Runs this game on ANGLE (OpenGL ES over Vulkan).", angle) {
                    angle = it; repo.setGfx(pkg, "angle", it)
                }
                Divider()
                SectionBar("QUICK PRESETS")
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    PresetChip("Original", preset == 0) { applyPreset(0) }
                    PresetChip("Pixel", preset == 1) { applyPreset(1) }
                    PresetChip("Retro", preset == 2) { applyPreset(2) }
                    PresetChip("Lite", preset == 3) { applyPreset(3) }
                }
                Text(
                    when (preset) {
                        1 -> "Sharp blocky textures at half resolution."
                        2 -> "Banded detail, soft anisotropy, quarter resolution."
                        3 -> "Lighter GPU load at eighth resolution."
                        else -> "The game's own filtering."
                    },
                    style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp
                )
                Divider()
                SectionBar("FINE TUNING")
                GfxRow("Sharp pixels", "Textures are not smoothed, giving a blocky look up close.", sharp) {
                    sharp = it; repo.setGfx(pkg, "sharp", it)
                }
                Divider()
                GfxRow("Hard detail steps", "Visible bands where texture detail changes in the distance.", hard) {
                    hard = it; repo.setGfx(pkg, "hard", it)
                }
                Divider()
                GfxRow("Anisotropic off", "Angled ground and roads look softer. Slightly lighter on the GPU.", aniso) {
                    aniso = it; repo.setGfx(pkg, "aniso", it)
                }
                Divider()
                Text("Texture resolution", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val labels = listOf("Full", "½", "¼", "⅛", "1/16")
                    labels.forEachIndexed { i, label ->
                        PresetChip(label, res == i) {
                            res = i; repo.setGfxRes(pkg, i)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Saved automatically. Applied every time you launch the game from here.",
                style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp
            )
        }
    }
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

@Composable
private fun HexBackdrop(modifier: Modifier) {
    Canvas(modifier) {
        // pink bloom top-left, exactly where the reference art puts its light
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color(0x40FF2C5E), Color(0x00000000)),
                center = Offset(size.width * 0.22f, size.height * 0.06f),
                radius = size.width * 0.55f
            )
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color(0x2600E0FF), Color(0x00000000)),
                center = Offset(size.width * 0.92f, size.height * 0.9f),
                radius = size.width * 0.5f
            )
        )

        // hex lattice
        val r = 56f
        val hexW = r * sqrt(3f)
        val path = Path()
        var row = 0
        var y = -r
        while (y < size.height + r) {
            var x = if (row % 2 == 0) 0f else hexW / 2f
            while (x < size.width + hexW) {
                path.reset()
                for (i in 0..5) {
                    val a = (Math.PI / 3.0 * i - Math.PI / 6.0).toFloat()
                    val px = x + r * cos(a)
                    val py = y + r * sin(a)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                drawPath(path, Color(0x0BFFFFFF), style = Stroke(1f))
                x += hexW
            }
            y += r * 1.5f
            row++
        }

        // sparse signal dots
        val step = 74f
        var dy = 0f
        var drow = 0
        while (dy < size.height) {
            var dx = if (drow % 2 == 0) 0f else step / 2f
            var col = 0
            while (dx < size.width) {
                val lit = (drow * 7 + col * 3) % 11 == 0
                drawCircle(
                    if (lit) Color(0x66FF2C5E) else Color(0x14FFFFFF),
                    radius = if (lit) 2.4f else 1.5f,
                    center = Offset(dx, dy)
                )
                dx += step
                col++
            }
            dy += step * 0.8f
            drow++
        }
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
        "Grant 'Modify System Settings' to GamersHub — opening Settings…",
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
    SidePanel(title = "Touch Settings", subtitle = "CONFIGS", active = magic || taps || coords || hw, onDismiss = onDismiss) {
        GfxRow("Magic Mapper Listener", "Enable Magic Mapper service", magic) {
            magic = it; repo.setToggle("magic_mapper", it)
        }
        Divider()
        GfxRow("Show Taps", "Draw a visible marker where you touch.", taps) {
            val ok = putSystemSetting(context, "show_touches", if (it) 1 else 0)
            if (ok) { taps = it; repo.setToggle("show_taps", it) }
            feedbackOnWrite(context, ok)
        }
        Divider()
        GfxRow("Show Coordinates", "Show pointer position overlay while touching.", coords) {
            val ok = putSystemSetting(context, "pointer_location", if (it) 1 else 0)
            if (ok) { coords = it; repo.setToggle("show_coords", it) }
            feedbackOnWrite(context, ok)
        }
        Divider()
        GfxRow("HW Touch Enhancer", "Boost touch response via Hardware Composer", hw) {
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
    SidePanel(title = "Display Settings", subtitle = "CONFIGS", active = true, onDismiss = onDismiss) {
        Text("Refresh Rate", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("60Hz", "90Hz", "120Hz", "144Hz").forEachIndexed { i, label ->
                PresetChip(label, refreshStep == i) {
                    val prev = refreshStep
                    refreshStep = i
                    val hz = when (i) { 1 -> 90f; 2 -> 120f; 3 -> 144f; else -> 60f }
                    val ok = try {
                        Settings.System.putFloat(context.contentResolver, "peak_refresh_rate", hz) &&
                            Settings.System.putFloat(context.contentResolver, "min_refresh_rate", hz)
                    } catch (_: Throwable) { false }
                    if (!ok) refreshStep = prev
                    feedbackOnWrite(context, ok)
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
                ) { AIcon(Icons.Filled.Close, contentDescription = "Close", tint = GC.Text, modifier = Modifier.size(18.dp)) }
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
                    Text("MY APPS", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
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
                    AIcon(Icons.Filled.Close, contentDescription = "Close", tint = GC.Text, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            if (library.isEmpty()) {
                Text(
                    "No apps selected yet. Tap ADD APPS to choose the apps you want in this Game Space.",
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
                    AIcon(Icons.Filled.Apps, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ADD APPS", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 13.sp, letterSpacing = 1.sp)
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
