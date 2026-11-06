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
    val accent = LocalAccent.current
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
    val currentIcon = remember(currentPkg, library) {
        current?.icon ?: repo.iconFor(currentPkg)
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

    // Live stats
    val cpu by produceState(initialValue = DeviceStats.cpu()) {
        while (true) { value = DeviceStats.cpu(); delay(2500) }
    }
    val battery by produceState(initialValue = DeviceStats.battery(context)) {
        while (true) { value = DeviceStats.battery(context); delay(5000) }
    }
    val ping by produceState(initialValue = -1) {
        while (true) { value = withContext(Dispatchers.IO) { DeviceStats.pingMs() }; delay(6000) }
    }
    var clock by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) { clock = currentTime(); delay(1000) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A0B0B), Color(0xFF120708), Color(0xFF0B0506))))
    ) {
        HexBackdrop(Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ---- Top bar ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroMark(Modifier.size(46.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("GAME", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 10.sp, letterSpacing = 2.sp)
                    Text("HUB", style = MaterialTheme.typography.titleLarge, color = GC.Text, fontSize = 22.sp)
                }
                Spacer(Modifier.width(14.dp))
                Box(Modifier.width(1.dp).height(34.dp).background(Color(0x33FFFFFF)))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("LVL ${envLevelOf(context)}/3", style = MaterialTheme.typography.labelLarge, color = GC.Text, fontSize = 13.sp)
                    Spacer(Modifier.height(3.dp))
                    SegmentedBar(pct = envLevelOf(context) * 100 / 3, segments = 3, height = 5, modifier = Modifier.width(70.dp))
                }

                Spacer(Modifier.weight(1f))

                // Chevron tabs
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    ChevronTab(Icons.Filled.Bolt, selected = !showControl && !showTexture && !showApps) {
                        showControl = false; showTexture = false; showApps = false
                    }
                    ChevronTab(Icons.Filled.GraphicEq, selected = showControl) {
                        showControl = true; showTexture = false; showApps = false
                    }
                    ChevronTab(Icons.Filled.Memory, selected = showTexture) {
                        showTexture = true; showControl = false; showApps = false
                    }
                    ChevronTab(Icons.Filled.Apps, selected = showApps) {
                        showApps = true; showControl = false; showTexture = false
                    }
                }

                Spacer(Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AIcon(Icons.Filled.Bolt, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("${cpu.pct.coerceIn(0, 100)}%", style = MaterialTheme.typography.labelLarge, color = GC.Text, fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.width(1.dp).height(16.dp).background(Color(0x33FFFFFF)))
                        Spacer(Modifier.width(6.dp))
                        Text("${clock}", style = MaterialTheme.typography.labelLarge, color = GC.Text, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${battery.first.coerceAtLeast(0)}%", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 11.sp)
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.width(34.dp).height(8.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF))) {
                            Box(Modifier.fillMaxWidth((battery.first.coerceIn(0, 100)) / 100f).height(8.dp).background(accent))
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color(0x22FFFFFF)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    AIcon(Icons.Filled.PowerSettingsNew, contentDescription = "Close", tint = GC.Text, modifier = Modifier.size(22.dp))
                }
            }

            // ---- app strip: every app you selected, real icons ----
            if (library.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    library.forEach { entry ->
                        AppChip(
                            entry = entry,
                            selected = entry.pkg == currentPkg,
                            onClick = { currentPkg = entry.pkg }
                        )
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x22FFFFFF))
                        .clickable { showApps = true }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AIcon(Icons.Filled.Apps, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "No apps selected yet — tap to pick apps",
                        style = MaterialTheme.typography.labelLarge,
                        color = GC.Text,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- Main row ----
            Row(Modifier.weight(1f)) {
                // Left: game card + launch
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(CutCornerShape(topStart = 4.dp, topEnd = 22.dp, bottomEnd = 4.dp, bottomStart = 22.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF5A1418), Color(0xFF2A0D10))))
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .size(40.dp)
                                .clip(CutCornerShape(topStart = 4.dp, topEnd = 12.dp, bottomEnd = 4.dp, bottomStart = 12.dp))
                                .background(Color(0xCC150A0C))
                                .clickable { showTexture = true; showControl = false },
                            contentAlignment = Alignment.Center
                        ) {
                            AIcon(Icons.Filled.Tune, contentDescription = "Graphics", tint = accent, modifier = Modifier.size(20.dp))
                        }
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                        ) {
                            if (currentIcon != null) {
                                Image(
                                    painter = androidx.compose.ui.graphics.painter.BitmapPainter(currentIcon),
                                    contentDescription = appName,
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(appName, style = MaterialTheme.typography.titleMedium, color = Color.White, fontSize = 20.sp)
                                Text("${repo.playMinutes(currentPkg)}m · v$appVersion", style = MaterialTheme.typography.labelMedium, color = Color(0xCCFFFFFF), fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(appName, style = MaterialTheme.typography.headlineMedium, color = GC.Text, fontSize = 26.sp)
                    Text(currentPkg, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // LAUNCH (angular)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(CutCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomEnd = 2.dp, bottomStart = 12.dp))
                                .background(accent)
                                .clickable { if (currentPkg.isNotBlank()) onLaunch(currentPkg) }
                                .padding(horizontal = 22.dp, vertical = 13.dp)
                        ) {
                            Text("LAUNCH", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 17.sp, letterSpacing = 2.sp)
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.width(1.dp).height(16.dp).background(Color(0x44FFFFFF)))
                            Spacer(Modifier.width(10.dp))
                        }
                        Box(
                            Modifier
                                .clip(CutCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomEnd = 2.dp, bottomStart = 12.dp))
                                .background(Color(0x22FFFFFF))
                                .clickable { showControl = true; showTexture = false }
                                .padding(13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AIcon(Icons.Filled.Apps, contentDescription = "Control Center", tint = GC.Text, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("${cpu.pct.coerceIn(0, 100)}", style = MaterialTheme.typography.headlineMedium, color = accent, fontSize = 24.sp)
                                Text("%  PERFORM", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(5.dp))
                            SegmentedBar(pct = cpu.pct.coerceIn(0, 100), segments = 10, height = 6, modifier = Modifier.width(150.dp))
                        }
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Right: watermark + stat cards
                Column(
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Text(
                        "${repo.userId()} · ${android.os.Process.myPid()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0x55FFFFFF),
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("PING INTERVAL", if (ping >= 0) "$ping ms" else "-- ms", Modifier.width(150.dp)) {
                            SegmentedBar(pct = if (ping in 0..120) 80 else 30, segments = 3, height = 5)
                        }
                        StatCard("TEMPERATURE", String.format("%.1f°C", battery.second.coerceAtLeast(0.0)), Modifier.width(150.dp)) {
                            SegmentedBar(pct = ((battery.second.coerceIn(0.0, 60.0)) / 60.0 * 100).toInt(), segments = 6, height = 5)
                        }
                        StatCard("CPU SPEED", String.format("%.2f GHz", cpu.curGhz), Modifier.width(160.dp)) {
                            GaugeRing(pct = cpu.pct, big = String.format("%.2f", cpu.curGhz), small = "GHz", diameter = 74)
                        }
                    }
                }
            }
        }

        // ---- Control Center overlay ----
        if (showControl) {
            ControlCenter(
                onDismiss = { showControl = false },
                onTouchSettings = { showControl = false; showTouch = true },
                onDisplaySettings = { showControl = false; showDisplay = true },
                onOpenPanel = {
                    panelRunning = !panelRunning
                    if (panelRunning) FloatingPanelService.start(context) else FloatingPanelService.stop(context)
                },
                panelRunning = panelRunning
            )
        }

        // ---- Apps panel: pick and launch any selected app ----
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

        // ---- Texture Filtering panel ----
        AnimatedVisibility(
            visible = showTexture,
            enter = slideInHorizontally(tween(320)) { it } + fadeIn(tween(280)),
            exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(200))
        ) {
            TexturePanel(pkg = currentPkg, repo = repo, appName = appName, onDismiss = { showTexture = false })
        }

        // ---- Touch / Display settings panels ----
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

@Composable
private fun ChevronTab(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .clip(CutCornerShape(topStart = 2.dp, topEnd = 10.dp, bottomEnd = 2.dp, bottomStart = 10.dp))
            .background(if (selected) accent else Color(0x22FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        AIcon(icon, contentDescription = null, tint = if (selected) Color.White else GC.TextSoft, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier, footer: @Composable () -> Unit) {
    Column(
        modifier
            .clip(CutCornerShape(topStart = 2.dp, topEnd = 14.dp, bottomEnd = 2.dp, bottomStart = 14.dp))
            .background(Color(0xCC120A0B))
            .border(1.dp, Color(0x22FFFFFF), CutCornerShape(topStart = 2.dp, topEnd = 14.dp, bottomEnd = 2.dp, bottomStart = 14.dp))
            .padding(13.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp)
        Spacer(Modifier.height(8.dp))
        footer()
    }
}

@Composable
private fun ControlCenter(
    onDismiss: () -> Unit,
    onTouchSettings: () -> Unit,
    onDisplaySettings: () -> Unit,
    onOpenPanel: () -> Unit,
    panelRunning: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { AppRepo(context) }
    var dns by remember { mutableStateOf(repo.toggle("dns")) }
    var session by remember { mutableStateOf(repo.toggle("session")) }
    var taps by remember { mutableStateOf(repo.toggle("show_taps")) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .clip(CutCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomEnd = 6.dp, bottomStart = 22.dp))
                .background(Color(0xF5160E0F))
                .clickable(enabled = false) {}
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(22.dp).background(LocalAccent.current))
                Spacer(Modifier.width(10.dp))
                Text("Control Center", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 20.sp)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(CutCornerShape(4.dp)).background(Color(0x22FFFFFF)).clickable(onClick = onDismiss).padding(10.dp)
                ) { AIcon(Icons.Filled.Close, contentDescription = "Close", tint = GC.Text, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlCard(Icons.Filled.Dns, "VISUAL", "DNS & Latency Tuner", showState = true, on = dns, onToggle = {
                    dns = it; repo.setToggle("dns", it)
                }, modifier = Modifier.weight(1f))
                ControlCard(Icons.Filled.GraphicEq, "VISUAL", "Session Monitor", showState = true, on = session, onToggle = {
                    session = it; repo.setToggle("session", it)
                }, modifier = Modifier.weight(1f))
                ControlCard(Icons.Filled.TouchApp, "CONFIGS", "Touch Settings", showState = false, on = false, onToggle = {
                    onTouchSettings()
                }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlCard(Icons.Filled.Tune, "CONFIGS", "Display Settings", showState = false, on = false, onToggle = {
                    onDisplaySettings()
                }, modifier = Modifier.weight(1f))
                ControlCard(Icons.Filled.Apps, "CONFIGS", if (panelRunning) "Pop-up Panel (ON)" else "Pop-up Panel", showState = false, on = panelRunning, onToggle = { onOpenPanel() }, modifier = Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ControlCard(
    icon: ImageVector,
    category: String,
    title: String,
    showState: Boolean,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccent.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CutCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomEnd = 2.dp, bottomStart = 12.dp))
            .background(Color(0xFF1C1213))
            .clickable { onToggle(!on) }
            .padding(13.dp)
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF241718)), contentAlignment = Alignment.Center) {
            AIcon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(category, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 9.sp, letterSpacing = 1.2.sp)
            Text(title, style = MaterialTheme.typography.labelLarge, color = GC.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showState) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF241718))
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Text(if (on) "ON" else "OFF", style = MaterialTheme.typography.labelMedium, color = if (on) accent else GC.TextDim, fontSize = 11.sp)
            }
        }
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
        val step = 46f
        val paintDot = Color(0x14FF6B4A)
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else step / 2
            while (x < size.width) {
                drawCircle(paintDot, radius = 2.2f, center = Offset(x, y))
                x += step
            }
            y += step * 0.86f
            row++
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
