package com.gamershub

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.data.AppRepo
import com.gamershub.data.DeviceStats
import com.gamershub.overlay.FloatingPanelService
import com.gamershub.theme.GC
import com.gamershub.theme.GameCornerTheme
import com.gamershub.theme.LocalAccent
import com.gamershub.ui.components.BrandMark
import com.gamershub.ui.components.GaugeRing
import com.gamershub.ui.components.SegmentedBar
import com.gamershub.ui.components.Toggle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GameSpaceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: ""
        val repo = AppRepo(applicationContext)
        setContent {
            GameCornerTheme(repo.accentIndex()) {
                var showIntro by remember { mutableStateOf(true) }
                Box(Modifier.fillMaxSize()) {
                    GameSpaceScreen(pkg = pkg, repo = repo, onClose = { finish() }, onLaunch = { p -> repo.launch(p) })
                    if (showIntro) {
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(1900)
                            showIntro = false
                        }
                        Image(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.intro_creature),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clickable { showIntro = false }
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_PKG = "pkg"
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun GameSpaceScreen(pkg: String, repo: AppRepo, onClose: () -> Unit, onLaunch: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val accent = LocalAccent.current
    var showControl by remember { mutableStateOf(false) }
    var showTexture by remember { mutableStateOf(false) }
    var showTouch by remember { mutableStateOf(false) }
    var showDisplay by remember { mutableStateOf(false) }
    val cpu by androidx.compose.runtime.produceState(initialValue = DeviceStats.cpu()) {
        while (true) {
            value = DeviceStats.cpu()
            kotlinx.coroutines.delay(1500)
        }
    }
    val battery by androidx.compose.runtime.produceState(initialValue = DeviceStats.battery(context)) {
        while (true) {
            value = DeviceStats.battery(context)
            kotlinx.coroutines.delay(15000)
        }
    }
    val clock = SimpleDateFormat("hh:mm a", Locale.US).format(Date())

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF241012), Color(0xFF120A0B))))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ---- Top bar ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(Modifier.size(42.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("GAME", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 9.sp, letterSpacing = 2.sp)
                    Text("CORNER", style = MaterialTheme.typography.titleLarge, color = GC.Text, fontSize = 19.sp, fontFamily = GC.Display)
                }
                Spacer(Modifier.width(14.dp))
                Box(Modifier.width(1.dp).height(30.dp).background(Color(0x33FFFFFF)))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("LVL ${envLevelOf(context)}/3", style = MaterialTheme.typography.labelLarge, color = GC.Text, fontSize = 12.sp)
                    Spacer(Modifier.height(3.dp))
                    SegmentedBar(pct = envLevelOf(context) * 100 / 3, segments = 3, height = 4, modifier = Modifier.width(64.dp))
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    ChevronTab(Icons.Filled.Bolt, selected = !showControl && !showTexture && !showTouch && !showDisplay) {
                        showControl = false; showTexture = false; showTouch = false; showDisplay = false
                    }
                    ChevronTab(Icons.Filled.GraphicEq, selected = showControl) {
                        showControl = true; showTexture = false; showTouch = false; showDisplay = false
                    }
                    ChevronTab(Icons.Filled.Memory, selected = showTexture) {
                        showTexture = true; showControl = false; showTouch = false; showDisplay = false
                    }
                    ChevronTab(Icons.Filled.Apps, selected = false) {
                        if (FloatingPanelService.isRunning) FloatingPanelService.stop(context)
                        else FloatingPanelService.start(context)
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${repo.energy()} / 560", style = MaterialTheme.typography.labelMedium, color = GC.Text, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${battery.first.coerceAtLeast(0)}%", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 11.sp)
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.width(30.dp).height(7.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF))) {
                            Box(Modifier.fillMaxWidth((battery.first.coerceIn(0, 100)) / 100f).height(7.dp).background(accent))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(clock, style = MaterialTheme.typography.labelMedium, color = GC.Text, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color(0x22FFFFFF)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Close", tint = GC.Text, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---- Main row ----
            Row(Modifier.weight(1f)) {
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(CutCornerShape(topStart = 4.dp, topEnd = 22.dp, bottomEnd = 4.dp, bottomStart = 22.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF3A1215), Color(0xFF1E0C0E))))
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .size(38.dp)
                                .clip(CutCornerShape(topStart = 4.dp, topEnd = 12.dp, bottomEnd = 4.dp, bottomStart = 12.dp))
                                .background(Color(0xCC150A0C))
                                .clickable { showTexture = true; showControl = false; showTouch = false; showDisplay = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = "Graphics", tint = accent, modifier = Modifier.size(19.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(pkg.substringAfterLast('.'), style = MaterialTheme.typography.headlineMedium, color = GC.Text, fontSize = 22.sp, fontFamily = GC.Display)
                    Text(pkg, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(CutCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomEnd = 2.dp, bottomStart = 12.dp))
                                .background(Color(0xFFD31F26))
                                .clickable { onLaunch(pkg) }
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text("LAUNCH", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 16.sp, letterSpacing = 2.sp)
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.width(1.dp).height(15.dp).background(Color(0x44FFFFFF)))
                            Spacer(Modifier.width(10.dp))
                            Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("80", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 15.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(CutCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomEnd = 2.dp, bottomStart = 12.dp))
                                .background(Color(0x22FFFFFF))
                                .clickable { showControl = true; showTexture = false; showTouch = false; showDisplay = false }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Apps, contentDescription = "Control Center", tint = GC.Text, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("${cpu.pct.coerceIn(0, 100)}", style = MaterialTheme.typography.headlineMedium, color = accent, fontSize = 22.sp)
                                Text("%  PERFORM", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 10.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            SegmentedBar(pct = cpu.pct.coerceIn(0, 100), segments = 10, height = 5, modifier = Modifier.width(130.dp))
                        }
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(
                    Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "${repo.userId()}-14781",
                        style = MaterialTheme.typography.labelMedium, color = Color(0x66FFFFFF), fontSize = 11.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("PING INTERVAL", "${DeviceStats.pingMs()} ms", Modifier.width(150.dp))
                        StatCard("TEMPERATURE", "${battery.second.toInt()}°C", Modifier.width(150.dp))
                        Column(
                            Modifier
                                .width(150.dp)
                                .clip(CutCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 4.dp, bottomStart = 14.dp))
                                .background(Color(0xE61A1214))
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("CPU SPEED", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 9.sp, letterSpacing = 1.5.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("${String.format(Locale.US, "%.2f", cpu.curGhz)} GHz", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 15.sp)
                            Spacer(Modifier.height(6.dp))
                            GaugeRing(pct = cpu.pct, big = String.format(Locale.US, "%.2f", cpu.curGhz), small = "GHz", diameter = 76)
                        }
                    }
                }
            }
        }

        // ---- Overlays ----
        AnimatedVisibility(
            visible = showControl,
            enter = slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(260)),
            exit = slideOutHorizontally(tween(200)) { -it / 3 } + fadeOut(tween(180))
        ) {
            ControlCenter(
                onDismiss = { showControl = false },
                onTouchSettings = { showControl = false; showTouch = true },
                onDisplaySettings = { showControl = false; showDisplay = true }
            )
        }
        AnimatedVisibility(
            visible = showTexture,
            enter = slideInHorizontally(tween(320)) { it } + fadeIn(tween(280)),
            exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(200))
        ) {
            SidePanel("Texture Filtering", "GRAPHICS", true, { showTexture = false }) {
                Text("ANGLE renderer", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 16.sp)
                Text(
                    "Runs this game on ANGLE (OpenGL ES over Vulkan). Required for texture filtering.",
                    style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 12.sp, lineHeight = 17.sp
                )
            }
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

internal fun envLevelOf(context: Context): Int {
    var level = 0
    if (android.provider.Settings.canDrawOverlays(context)) level++
    if (android.provider.Settings.System.canWrite(context)) level++
    level++
    return level
}

@Composable
private fun ChevronTab(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .padding(horizontal = 1.dp)
            .clip(CutCornerShape(topStart = 2.dp, topEnd = 10.dp, bottomEnd = 2.dp, bottomStart = 10.dp))
            .background(if (selected) accent else Color(0x22FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Color.White else GC.TextSoft, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(CutCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 4.dp, bottomStart = 14.dp))
            .background(Color(0xE61A1214))
            .padding(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 9.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 17.sp)
        Spacer(Modifier.height(7.dp))
        SegmentedBar(pct = 62, segments = 6, height = 4)
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun ControlCenter(onDismiss: () -> Unit, onTouchSettings: () -> Unit, onDisplaySettings: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp)
                .clip(CutCornerShape(topStart = 22.dp, topEnd = 6.dp, bottomEnd = 22.dp, bottomStart = 6.dp))
                .background(Color(0xF51A1214))
                .clickable(enabled = false) {}
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(22.dp).background(accent))
                Spacer(Modifier.width(10.dp))
                Text("Control Center", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 21.sp)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(CutCornerShape(4.dp)).background(Color(0x22FFFFFF)).clickable(onClick = onDismiss).padding(10.dp)
                ) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = GC.Text, modifier = Modifier.size(17.dp)) }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlCard(Icons.Filled.Dns, "VISUAL", "DNS & Latency Tuner", showState = true, on = false, onToggle = {}, modifier = Modifier.weight(1f))
                ControlCard(Icons.Filled.GraphicEq, "VISUAL", "Session Monitor", showState = true, on = false, onToggle = {}, modifier = Modifier.weight(1f))
                ControlCard(Icons.Filled.TouchApp, "CONFIGS", "Touch Settings", showState = false, on = false, onToggle = { onTouchSettings() }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlCard(Icons.Filled.Tune, "CONFIGS", "Display Settings", showState = false, on = false, onToggle = { onDisplaySettings() }, modifier = Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ControlCard(
    icon: ImageVector,
    tag: String,
    title: String,
    showState: Boolean,
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccent.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF221315))
            .clickable(onClick = onToggle)
            .padding(13.dp)
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = GC.Text, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(tag, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 9.sp, letterSpacing = 1.5.sp)
            Text(title, style = MaterialTheme.typography.labelMedium, color = GC.Text, fontSize = 13.sp, maxLines = 1)
        }
        if (showState) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x33FFFFFF))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(if (on) "ON" else "OFF", style = MaterialTheme.typography.labelMedium, color = GC.TextSoft, fontSize = 10.sp)
            }
        }
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun SidePanel(
    title: String,
    subtitle: String,
    active: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
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
                    Text(if (active) "Active" else "Not active", style = MaterialTheme.typography.labelMedium, color = if (active) accent else GC.TextDim, fontSize = 12.sp)
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
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
private fun GfxRow(title: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 15.sp)
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 11.5.sp, lineHeight = 16.sp)
        }
        Toggle(on = on, onCheckedChange = onChange)
    }
}

@Composable
private fun TouchPanel(repo: AppRepo, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var taps by remember { mutableStateOf(repo.toggle("show_taps")) }
    var coords by remember { mutableStateOf(repo.toggle("show_coords")) }
    var hw by remember { mutableStateOf(repo.toggle("hw_touch")) }
    SidePanel(title = "Touch Settings", subtitle = "CONFIGS", active = taps || coords || hw, onDismiss = onDismiss) {
        GfxRow("Show Taps", "Draw a visible marker where you touch.", taps) {
            val ok = putSystemSetting(context, "show_touches", if (it) 1 else 0)
            if (ok) { taps = it; repo.setToggle("show_taps", it) }
            feedbackOnWrite(context, ok)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
        GfxRow("Show Coordinates", "Show pointer position overlay while touching.", coords) {
            val ok = putSystemSetting(context, "pointer_location", if (it) 1 else 0)
            if (ok) { coords = it; repo.setToggle("show_coords", it) }
            feedbackOnWrite(context, ok)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
        GfxRow("HW Touch Enhancer", "Boost touch response via Hardware Composer", hw) {
            hw = it; repo.setToggle("hw_touch", it)
        }
    }
}

@Composable
private fun DisplayPanel(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var refreshStep by remember { mutableStateOf(0) }
    SidePanel(title = "Display Settings", subtitle = "CONFIGS", active = true, onDismiss = onDismiss) {
        Text("Refresh Rate", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("60Hz", "90Hz", "120Hz", "144Hz").forEachIndexed { i, label ->
                val selected = refreshStep == i
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) LocalAccent.current else Color(0xFF241718))
                        .clickable {
                            val prev = refreshStep
                            refreshStep = i
                            val hz = when (i) { 1 -> 90f; 2 -> 120f; 3 -> 144f; else -> 60f }
                            val ok = try {
                                android.provider.Settings.System.putFloat(context.contentResolver, "peak_refresh_rate", hz) &&
                                    android.provider.Settings.System.putFloat(context.contentResolver, "min_refresh_rate", hz)
                            } catch (_: Throwable) { false }
                            if (!ok) refreshStep = prev
                            feedbackOnWrite(context, ok)
                        }
                        .padding(horizontal = 13.dp, vertical = 9.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) Color.White else GC.TextSoft, fontSize = 12.sp)
                }
            }
        }
    }
}
