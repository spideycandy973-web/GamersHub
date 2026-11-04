package com.gamershub.ui

import com.gamershub.ui.components.AIcon

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.data.DeviceStats
import com.gamershub.theme.GC
import com.gamershub.theme.LocalAccent
import com.gamershub.ui.components.Card
import com.gamershub.ui.components.IconChip
import com.gamershub.ui.components.Toggle
import java.util.Locale

private enum class LabsTab(val label: String) { ALL("All"), VISUAL("Visual"), CONFIGS("Configs") }

@Composable
fun GameLabsScreen(
    envLevel: Int,
    toggleState: (String) -> Boolean,
    onToggle: (String, Boolean) -> Unit,
    onExportPresets: () -> Unit,
    onManagePresets: () -> Unit,
    onTuneDisplay: () -> Unit,
    onGameReady: () -> Unit = {},
    displayInfo: String
) {
    val accent = LocalAccent.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var tab by remember { mutableStateOf(LabsTab.ALL) }

    Column(Modifier.fillMaxSize().background(GC.Bg)) {
        // ---- Fixed opaque header: content can never collide with it ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(GC.Bg)
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text("Game-Labs", style = MaterialTheme.typography.displaySmall, color = GC.Text, fontSize = 33.sp)
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GC.SurfaceHi)
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) {
                AIcon(Icons.Filled.Bolt, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("LV.$envLevel", style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(5.dp))
                Text("Advanced", style = MaterialTheme.typography.labelLarge, color = GC.TextSoft, fontSize = 13.sp)
            }
        }

        // ---- Everything below scrolls under the header, clipped ----
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 120.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabsTab.entries.forEach { t ->
                    val selected = tab == t
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) accent else GC.SurfaceHi)
                            .clickable { tab = t }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        AIcon(
                            when (t) {
                                LabsTab.ALL -> Icons.Filled.Extension
                                LabsTab.VISUAL -> Icons.Filled.GraphicEq
                                LabsTab.CONFIGS -> Icons.Filled.Settings
                            },
                            contentDescription = null,
                            tint = if (selected) Color.White else GC.TextSoft,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            t.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White else GC.TextSoft,
                            fontSize = 15.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            // ---- Stop All Features (All tab) ----
            if (tab == LabsTab.ALL) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconChip(Icons.Filled.Dialpad, size = 46)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Stop All Features", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp)
                            Text(
                                "All active features will be closed immediately",
                                style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 13.sp, lineHeight = 18.sp
                            )
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF5A1618))
                                .clickable {
                                    listOf("game_ready", "dns", "session", "magic_mapper", "hw_touch", "show_taps", "show_coords")
                                        .forEach { k -> if (toggleState(k)) onToggle(k, false) }
                                }
                                .padding(horizontal = 22.dp, vertical = 11.dp)
                        ) {
                            Text("Stop", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFF8A80), fontSize = 15.sp)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // ---- Touch Settings ----
            if (tab != LabsTab.VISUAL) {
                SectionLabel("Touch Settings")
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconChip(Icons.Filled.TouchApp, size = 46)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Magic Mapper Listener", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                            Text(
                                if (toggleState("magic_mapper")) "Monitoring 1 device(s)" else "Enable Magic Mapper service",
                                style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 13.sp
                            )
                        }
                        Toggle(on = toggleState("magic_mapper")) { onToggle("magic_mapper", it) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmallActionButton("Show Taps", Modifier.weight(1f), active = toggleState("show_taps")) {
                            val v = !toggleState("show_taps")
                            val ok = com.gamershub.putSystemSetting(context, "show_touches", if (v) 1 else 0)
                            if (ok) onToggle("show_taps", v)
                            com.gamershub.feedbackOnWrite(context, ok)
                        }
                        SmallActionButton("Show Coordinates", Modifier.weight(1f), active = toggleState("show_coords")) {
                            val v = !toggleState("show_coords")
                            val ok = com.gamershub.putSystemSetting(context, "pointer_location", if (v) 1 else 0)
                            if (ok) onToggle("show_coords", v)
                            com.gamershub.feedbackOnWrite(context, ok)
                        }
                    }
                    Divider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconChip(Icons.Filled.TouchApp, size = 46)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("HW Touch Enhancer", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                            Text(
                                "Boost touch response via Hardware Composer",
                                style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 13.sp, lineHeight = 18.sp
                            )
                        }
                        Toggle(on = toggleState("hw_touch")) { onToggle("hw_touch", it) }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // ---- Display Settings ----
            if (tab != LabsTab.VISUAL) {
                SectionLabel("Display Settings")
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconChip(Icons.Filled.Dns, size = 46)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Preset Manager", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                            Text("Save or load display configurations.", style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmallActionButton("Export", Modifier.weight(1f), onClick = onExportPresets)
                        SmallActionButton("Manage", Modifier.weight(1f), primary = true, onClick = onManagePresets)
                    }
                    Divider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconChip(Icons.Filled.VideogameAsset, size = 46)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Display Tuning", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                            Text(displayInfo, style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 14.sp)
                        }
                        SmallActionButton("Tune", Modifier.width(88.dp), primary = true, onClick = onTuneDisplay)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // ---- Feature Settings ----
            if (tab != LabsTab.CONFIGS) {
                SectionLabel("Feature Settings")
                Card {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onGameReady)
                    ) {
                        IconChip(Icons.Filled.Bolt, size = 46)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Game Ready", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                            Text("System health & stability index", style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 13.sp)
                        }
                        Toggle(on = toggleState("game_ready")) {
                            onToggle("game_ready", it)
                            if (it) onGameReady()
                        }
                    }
                    Divider()
                    DnsTunerCard(toggleState("dns"), onToggle)
                    Divider()
                    SessionMonitorCard(toggleState("session"), toggleState, onToggle)
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun DnsTunerCard(on: Boolean, onToggle: (String, Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf(0) }
    val hosts = listOf("8.8.8.8", "1.1.1.1", "208.67.222.222", "8.8.8.8")
    val names = listOf("Google", "Cloudflare", "OpenDNS", "Custom")
    val latency by produceState(initialValue = -1) {
        while (true) {
            value = DeviceStats.pingMs(hosts[provider])
            kotlinx.coroutines.delay(3000)
        }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(Icons.Filled.Dns, size = 46)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("DNS & Latency Tuner", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                Text(
                    "Keep connection warm & reduce latency spikes",
                    style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 13.sp, lineHeight = 18.sp
                )
            }
            Toggle(on = on) {
                onToggle("dns", it)
                expanded = it
            }
        }
        if (on && expanded) {
            Spacer(Modifier.height(10.dp))
            // provider row + latency pill
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GC.SurfaceHi)
                        .clickable { provider = (provider + 1) % 4 }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(names[provider], style = MaterialTheme.typography.labelLarge, color = GC.Text, fontSize = 15.sp)
                        Spacer(Modifier.weight(1f))
                        Text(hosts[provider], style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("⌃", style = MaterialTheme.typography.labelLarge, color = GC.TextDim, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF31401A))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        if (latency >= 0) "${latency}ms" else "--",
                        style = MaterialTheme.typography.labelLarge, color = Color(0xFFC6E24A), fontSize = 15.sp
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GC.SurfaceHi)
                    .padding(6.dp)
            ) {
                names.forEachIndexed { i, n ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { provider = i }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(n, style = MaterialTheme.typography.labelLarge, color = if (i == provider) GC.Text else GC.TextSoft, fontSize = 14.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(hosts[i], style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionMonitorCard(on: Boolean, toggleState: (String) -> Boolean, onToggle: (String, Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val live by produceState(initialValue = Triple("0", "0", "--")) {
        while (true) {
            val cpu = DeviceStats.cpu()
            val mem = DeviceStats.mem(context)
            val batt = DeviceStats.battery(context)
            value = Triple(
                cpu.pct.toString(),
                mem.pct.toString(),
                batt.first.toString()
            )
            kotlinx.coroutines.delay(1500)
        }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(Icons.Filled.GraphicEq, size = 46)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Session Monitor", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                Text(
                    "Track device performance while you play",
                    style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 13.sp, lineHeight = 18.sp
                )
            }
            Toggle(on = on) {
                onToggle("session", it)
                expanded = it
            }
        }
        if (on && expanded) {
            Spacer(Modifier.height(10.dp))
            // live monitor strip
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(GC.SurfaceHi)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StripStat("CPU", "${live.first}%")
                StripStat("RAM", "${live.second}%")
                StripStat("BAT", "${live.third}%")
                StripStat("FPS", "0")
                StripStat("CLK", java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date()))
            }
            Spacer(Modifier.height(10.dp))
            val checks = listOf(
                Triple("sm_cpu", "CPU Usage", Icons.Filled.Speed),
                Triple("sm_gpu", "GPU Usage", Icons.Filled.Memory),
                Triple("sm_batt", "Battery Level", Icons.Filled.Bolt),
                Triple("sm_batt_temp", "Battery Temperature", Icons.Filled.Storage),
                Triple("sm_fps", "Frame Rate (FPS)", Icons.Filled.GraphicEq),
                Triple("sm_clock", "Clock", Icons.Filled.Dns)
            )
            CheckRow("RAM Used/Available", "ram_mode", toggleState, onToggle, ramToggle = true)
            checks.forEach { (key, label, icon) ->
                CheckRow(label, key, toggleState, onToggle, icon = icon)
            }
        }
    }
}

@Composable
private fun CheckRow(
    label: String,
    key: String,
    toggleState: (String) -> Boolean,
    onToggle: (String, Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    ramToggle: Boolean = false
) {
    val accent = LocalAccent.current
    val checked = toggleState(key)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = GC.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (ramToggle) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(GC.SurfaceHi)
            ) {
                Box(
                    Modifier
                        .background(if (checked) accent else Color.Transparent)
                        .clickable { onToggle(key, true) }
                        .padding(horizontal = 13.dp, vertical = 7.dp)
                ) { Text("Used", style = MaterialTheme.typography.labelMedium, color = if (checked) Color.White else GC.TextSoft, fontSize = 12.sp) }
                Box(
                    Modifier
                        .background(if (!checked) accent else Color.Transparent)
                        .clickable { onToggle(key, false) }
                        .padding(horizontal = 13.dp, vertical = 7.dp)
                ) { Text("Available", style = MaterialTheme.typography.labelMedium, color = if (!checked) Color.White else GC.TextSoft, fontSize = 12.sp) }
            }
        } else {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (checked) accent else GC.SurfaceHi)
                    .clickable { onToggle(key, !checked) },
                contentAlignment = Alignment.Center
            ) {
                if (checked) AIcon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun StripStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelLarge, color = GC.Text, fontSize = 13.sp)
        Text(label, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun FeatureRow(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    on: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconChip(icon, size = 46)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Toggle(on = on, onCheckedChange = onChange)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = GC.Text,
        fontSize = 17.sp,
        modifier = Modifier.padding(bottom = 10.dp, top = 2.dp)
    )
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .height(1.dp)
            .background(Color(0x14FFFFFF))
    )
}

@Composable
private fun SmallActionButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val accent = LocalAccent.current
    val bg by animateColorAsState(
        when {
            primary -> accent
            active -> accent.copy(alpha = 0.28f)
            else -> GC.SurfaceHi
        },
        label = "sbtn"
    )
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) Color.White else if (active) accent else GC.Text,
            fontSize = 14.sp
        )
    }
}
