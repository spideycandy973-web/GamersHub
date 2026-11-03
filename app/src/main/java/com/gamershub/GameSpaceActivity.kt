package com.gamershub

import android.os.BatteryManager
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
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
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: ""
        val repo = AppRepo(applicationContext)
        setContent {
            GameCornerTheme(repo.accentIndex()) {
                GameSpaceScreen(
                    pkg = pkg,
                    repo = repo,
                    onClose = { finish() },
                    onLaunch = { p ->
                        repo.launch(p)
                    }
                )
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
    val app = remember(pkg) {
        try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            val icon: androidx.compose.ui.graphics.ImageBitmap = run {
                val d = pm.getApplicationIcon(pkg)
                val bmp = android.graphics.Bitmap.createBitmap(128, 128, android.graphics.Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(bmp)
                d.setBounds(0, 0, 128, 128); d.draw(c)
                bmp.asImageBitmap()
            }
            Triple(pm.getApplicationLabel(info).toString(), pm.getPackageInfo(pkg, 0).versionName ?: "?", icon)
        } catch (_: Throwable) {
            Triple(pkg, "?", null)
        }
    }
    var showControl by remember { mutableStateOf(false) }
    var showTexture by remember { mutableStateOf(false) }
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
                .systemBarsPadding()
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
                    ChevronTab(Icons.Filled.Bolt, selected = !showControl && !showTexture) {
                        showControl = false; showTexture = false
                    }
                    ChevronTab(Icons.Filled.GraphicEq, selected = showControl) {
                        showControl = true; showTexture = false
                    }
                    ChevronTab(Icons.Filled.Memory, selected = showTexture) {
                        showTexture = true; showControl = false
                    }
                    ChevronTab(Icons.Filled.Apps, selected = false) {
                        panelRunning = !panelRunning
                        if (panelRunning) FloatingPanelService.start(context) else FloatingPanelService.stop(context)
                    }
                }

                Spacer(Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
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
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Close", tint = GC.Text, modifier = Modifier.size(22.dp))
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
                            Icon(Icons.Filled.Tune, contentDescription = "Graphics", tint = accent, modifier = Modifier.size(20.dp))
                        }
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                        ) {
                            if (app.third != null) {
                                Icon(
                                    androidx.compose.ui.graphics.painter.BitmapPainter(app.third!!),
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(app.first, style = MaterialTheme.typography.titleMedium, color = Color.White, fontSize = 20.sp)
                                Text("0m · v${app.second}", style = MaterialTheme.typography.labelMedium, color = Color(0xCCFFFFFF), fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(app.first, style = MaterialTheme.typography.headlineMedium, color = GC.Text, fontSize = 26.sp)
                    Text(pkg, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // LAUNCH (angular)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(CutCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomEnd = 2.dp, bottomStart = 12.dp))
                                .background(accent)
                                .clickable { onLaunch(pkg) }
                                .padding(horizontal = 22.dp, vertical = 13.dp)
                        ) {
                            Text("LAUNCH", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 17.sp, letterSpacing = 2.sp)
                        }
                        Box(
                            Modifier
                                .clip(CutCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomEnd = 2.dp, bottomStart = 12.dp))
                                .background(Color(0x22FFFFFF))
                                .clickable { showControl = true; showTexture = false }
                                .padding(13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Apps, contentDescription = "Control Center", tint = GC.Text, modifier = Modifier.size(22.dp))
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
                onOpenPanel = {
                    panelRunning = !panelRunning
                    if (panelRunning) FloatingPanelService.start(context) else FloatingPanelService.stop(context)
                },
                panelRunning = panelRunning
            )
        }

        // ---- Texture Filtering panel ----
        if (showTexture) {
            TexturePanel(pkg = pkg, repo = repo, appName = app.first, onDismiss = { showTexture = false })
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
        Icon(icon, contentDescription = null, tint = if (selected) Color.White else GC.TextSoft, modifier = Modifier.size(20.dp))
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
private fun ControlCenter(onDismiss: () -> Unit, onOpenPanel: () -> Unit, panelRunning: Boolean) {
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
                ) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = GC.Text, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlCard(Icons.Filled.Dns, "VISUAL", "DNS & Latency Tuner", showState = true, on = dns, onToggle = {
                    dns = it; repo.setToggle("dns", it)
                }, modifier = Modifier.weight(1f))
                ControlCard(Icons.Filled.GraphicEq, "VISUAL", "Session Monitor", showState = true, on = session, onToggle = {
                    session = it; repo.setToggle("session", it)
                }, modifier = Modifier.weight(1f))
                ControlCard(Icons.Filled.Apps, "CONFIGS", if (panelRunning) "Pop-up Panel (ON)" else "Pop-up Panel", showState = false, on = panelRunning, onToggle = { onOpenPanel() }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlCard(Icons.Filled.Bolt, "CONFIGS", "Show Taps", showState = true, on = taps, onToggle = {
                    taps = it; repo.setToggle("show_taps", it)
                    putSystemSetting(context, "show_touches", if (it) 1 else 0)
                }, modifier = Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
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
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
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
                    Icon(Icons.Filled.Memory, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
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
                ) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = GC.Text, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1B1213))
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

internal fun putSystemSetting(context: Context, name: String, value: Int) {
    try {
        android.provider.Settings.System.putInt(context.contentResolver, name, value)
    } catch (_: Throwable) {
        // needs WRITE_SETTINGS; the permission sheet covers it
    }
}
