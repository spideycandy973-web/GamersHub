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
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.gamershub.core.MonitorPrefs
import com.gamershub.data.DeviceStats
import androidx.compose.foundation.border
import com.gamershub.theme.RomKit
import com.gamershub.theme.GC
import com.gamershub.theme.LocalAccent
import com.gamershub.theme.accentNow
import com.gamershub.ui.components.Card
import com.gamershub.ui.components.IconChip
import com.gamershub.ui.components.Toggle
import java.util.Locale
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer

/** Counts the supplied booleans without short-circuiting. */
private fun list0(vararg flags: Boolean): List<Boolean> = flags.toList()

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

    // Read the two system switches themselves, so the buttons can never lie.
    val tapsOn by produceState(initialValue = false) {
        while (true) {
            value = runCatching {
                android.provider.Settings.System.getInt(context.contentResolver, "show_touches") == 1
            }.getOrDefault(false)
            kotlinx.coroutines.delay(1500)
        }
    }
    val pointersOn by produceState(initialValue = false) {
        while (true) {
            value = runCatching {
                android.provider.Settings.System.getInt(context.contentResolver, "pointer_location") == 1
            }.getOrDefault(false)
            kotlinx.coroutines.delay(1500)
        }
    }

    // Real read-out of the touch panel, refreshed when the tab is opened.
    val touchRate by produceState(initialValue = "checking…") {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.gamershub.core.TouchProbe.summary()
        }
    }

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
            Text("Lab Bench", style = MaterialTheme.typography.displaySmall, color = GC.Text, fontSize = 33.sp)
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RomKit.R12)
                    .background(RomKit.Panel2)
                    .border(1.dp, RomKit.Stroke, RomKit.R12)
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) {
                AIcon(Icons.Filled.Bolt, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("LV.$envLevel", style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(5.dp))
                Text("Pro", style = MaterialTheme.typography.labelLarge, color = GC.TextSoft, fontSize = 13.sp)
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
                    val labBg by androidx.compose.animation.animateColorAsState(
                        if (selected) accent else GC.SurfaceHi,
                        androidx.compose.animation.core.tween(200), label = "labBg"
                    )
                    val labInter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val labPressed by labInter.collectIsPressedAsState()
                    val labScale by androidx.compose.animation.core.animateFloatAsState(
                        if (labPressed) 0.93f else 1f,
                        androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 900f), label = "labScale"
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .graphicsLayer { scaleX = labScale; scaleY = labScale }
                            .clip(RoundedCornerShape(50))
                            .background(labBg)
                            .clickable(labInter, indication = null) { tab = t }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        AIcon(
                            when (t) {
                                LabsTab.ALL -> Icons.Filled.Extension
                                LabsTab.VISUAL -> Icons.Filled.GraphicEq
                                LabsTab.CONFIGS -> Icons.Filled.Settings
                            },
                            contentDescription = null,
                            tint = if (selected) GC.OnAccent else GC.TextSoft,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            t.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) GC.OnAccent else GC.TextSoft,
                            fontSize = 15.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            // ---- Stop All Features: only while something is actually running ----
            val stopKeys = listOf("game_ready", "dns", "session", "magic_mapper", "hw_touch", "show_taps", "show_coords")
            val runningServices = list0(
                com.gamershub.overlay.FloatingPanelService.isRunning,
                com.gamershub.overlay.MonitorOverlayService.isRunning,
                com.gamershub.overlay.CrosshairOverlayService.isRunning,
                com.gamershub.overlay.MappingOverlayService.isRunning,
                com.gamershub.overlay.MouseCursorService.isRunning,
                com.gamershub.core.capture.Recorder.isRecording,
                com.gamershub.core.capture.Stream.isStreaming,
                com.gamershub.core.upscale.UpscaleMirror.isRunning
            ).count { it }
            val activeCount = runningServices + stopKeys.count { toggleState(it) }
            if (tab == LabsTab.ALL && activeCount > 0) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconChip(Icons.Filled.Dialpad, size = 46)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Shut It All Down", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp)
                            Text(
                                if (runningServices > 0) "$activeCount active now · $runningServices service(s) running"
                                else "$activeCount feature(s) active — turn everything off",
                                style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 13.sp, lineHeight = 18.sp
                            )
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF5A1618))
                                .clickable {
                                    stopKeys.forEach { k -> if (toggleState(k)) onToggle(k, false) }
                                    runCatching { com.gamershub.overlay.FloatingPanelService.stop(context) }
                                    runCatching { com.gamershub.overlay.MonitorOverlayService.stop(context) }
                                    runCatching { com.gamershub.overlay.CrosshairOverlayService.stop(context) }
                                    runCatching { com.gamershub.overlay.MappingOverlayService.stop(context) }
                                    runCatching { com.gamershub.overlay.MouseCursorService.stop(context) }
                                    runCatching { com.gamershub.core.capture.RecorderService.stop(context) }
                                    runCatching { com.gamershub.core.capture.StreamService.stop(context) }
                                    runCatching { com.gamershub.core.upscale.UpscaleMirror.stop(context) }
                                }
                                .padding(horizontal = 22.dp, vertical = 11.dp)
                        ) {
                            Text("End", style = MaterialTheme.typography.labelLarge, color = accentNow(), fontSize = 15.sp)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // ---- Touch Settings ----
            if (tab != LabsTab.VISUAL) {
                SectionLabel("Tap Likes")
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconChip(Icons.Filled.TouchApp, size = 46)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Magic Pad Mapper", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                            Text(
                                if (toggleState("magic_mapper")) "Mapping overlay running" else "Turn on the mapping overlay",
                                style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 13.sp
                            )
                        }
                        Toggle(on = toggleState("magic_mapper")) { onToggle("magic_mapper", it) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmallActionButton("Show Taps", Modifier.weight(1f), active = tapsOn) {
                            val v = !tapsOn
                            val ok = com.gamershub.putSystemSetting(context, "show_touches", if (v) 1 else 0)
                            if (ok) onToggle("show_taps", v)
                            com.gamershub.feedbackOnWrite(context, ok)
                        }
                        SmallActionButton("Show Pointers", Modifier.weight(1f), active = pointersOn) {
                            val v = !pointersOn
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
                            Text("Tap Speed", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                            Text(
                                touchRate,
                                style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 13.sp, lineHeight = 18.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // ---- Display Settings ----
            if (tab != LabsTab.VISUAL) {
                SectionLabel("Screen Likes")
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconChip(Icons.Filled.Dns, size = 46)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Setup Saver", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                            Text("Keep or load screen looks.", style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmallActionButton("Export", Modifier.weight(1f), onClick = onExportPresets)
                        SmallActionButton("Handle", Modifier.weight(1f), primary = true, onClick = onManagePresets)
                    }
                    Divider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconChip(Icons.Filled.VideogameAsset, size = 46)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Screen Tweak", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                            Text(displayInfo, style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 14.sp)
                        }
                        SmallActionButton("Tune", Modifier.width(88.dp), primary = true, onClick = onTuneDisplay)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // ---- Feature Settings ----
            if (tab != LabsTab.CONFIGS) {
                SectionLabel("Feature Likes")
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
                            Text("Ready to Play", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                            Text("Phone health and steady score", style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 13.sp)
                        }
                        Text("OPEN", color = accent, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    Divider()
                    DnsTunerCard(onToggle)
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
private fun DnsTunerCard(onToggle: (String, Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    // What the phone is really using, not what we last wrote.
    val liveMode by produceState(initialValue = "") {
        while (true) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { com.gamershub.core.DnsEngine.current() }.getOrDefault("")
            }
            kotlinx.coroutines.delay(4000)
        }
    }
    val dnsOn = liveMode.startsWith("private dns")
    val hosts = listOf("dns.google", "one.one.one.one", "doh.opendns.com", "automatic")
    val names = listOf("Google", "Cloudflare", "OpenDNS", "Automatic")

    fun applyProvider(index: Int) {
        provider = index
        scope.launch {
            val ok = withContext(Dispatchers.IO) { com.gamershub.core.DnsEngine.apply(index) }
            Toast.makeText(
                context,
                if (ok) "Private DNS: ${names[index]}" else "Needs Shizuku to change DNS",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val latency by produceState(initialValue = -1) {
        while (true) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                DeviceStats.pingMs(hosts[provider])
            }
            kotlinx.coroutines.delay(3000)
        }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(Icons.Filled.Dns, size = 46)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Net Delay Fixer", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                Text(
                    "Keep connection warm & reduce latency spikes",
                    style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 13.sp, lineHeight = 18.sp
                )
            }
            Toggle(on = dnsOn) {
                expanded = it
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        if (it) com.gamershub.core.DnsEngine.apply(provider) else com.gamershub.core.DnsEngine.off()
                    }
                    Toast.makeText(
                        context,
                        when {
                            ok && it -> "Private DNS set to ${names[provider]}"
                            ok -> "Private DNS back to the phone's default"
                            else -> "Needs Shizuku — grant it in Activate to change DNS"
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                    if (ok) onToggle("dns", it)
                }
            }
        }
        if (liveMode.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("Now: $liveMode", color = GC.TextDim, fontSize = 11.5.sp)
        }
        if (dnsOn && expanded) {
            Spacer(Modifier.height(10.dp))
            // provider row + latency pill
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RomKit.R12)
                        .background(RomKit.Panel2)
                        .border(1.dp, RomKit.Stroke, RomKit.R12)
                        .clickable { applyProvider((provider + 1) % 4) }
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
                    .clip(RomKit.R12)
                    .background(RomKit.Panel2)
                    .border(1.dp, RomKit.Stroke, RomKit.R12)
                    .padding(6.dp)
            ) {
                names.forEachIndexed { i, n ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { applyProvider(i) }
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
    // The field chips write straight into the floating watcher's own list, so what you
    // tick here is exactly what the overlay draws.
    var picked by remember { mutableStateOf(MonitorPrefs.fields(context)) }
    val live by produceState(initialValue = listOf("--", "--", "--", "--")) {
        while (true) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val cpu = DeviceStats.cpu()
                val mem = DeviceStats.mem(context)
                val batt = DeviceStats.battery(context)
                val fps = com.gamershub.core.FpsMeter.liveFps(context)
                listOf("${cpu.pct}%", "${mem.pct}%", "${batt.first}%", if (fps > 0) fps.toString() else "--")
            }
            kotlinx.coroutines.delay(1500)
        }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(Icons.Filled.GraphicEq, size = 46)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Run Watcher", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                Text(
                    "Track device performance while you play",
                    style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 13.sp, lineHeight = 18.sp
                )
            }
            Toggle(on = on) {
                if (it && !android.provider.Settings.canDrawOverlays(context)) {
                    Toast.makeText(context, "Allow draw-over-apps first", Toast.LENGTH_SHORT).show()
                    return@Toggle
                }
                onToggle("session", it)
                runCatching {
                    if (it) com.gamershub.overlay.MonitorOverlayService.start(context)
                    else com.gamershub.overlay.MonitorOverlayService.stop(context)
                }
            }
        }
        if (on) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RomKit.R12)
                    .background(RomKit.Panel2)
                    .border(1.dp, RomKit.Stroke, RomKit.R12)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StripStat("CPU", live[0])
                StripStat("RAM", live[1])
                StripStat("BAT", live[2])
                StripStat("FPS", live[3])
                StripStat("CLK", java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date()))
            }
            Spacer(Modifier.height(10.dp))
            MonitorPrefs.ORDER.forEach { field ->
                val checked = picked.contains(field)
                val label = when (field) {
                    "fps" -> "Frames"
                    "cpu" -> "CPU Use"
                    "gpu" -> "GPU Use"
                    "ram" -> "Memory"
                    "temp" -> "Heat"
                    "batt" -> "Cell"
                    "ping" -> "Net Wait"
                    "hz" -> "Screen HZ"
                    "clk" -> "Clock"
                    else -> field.uppercase()
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (checked) accentNow().copy(alpha = 0.14f) else GC.SurfaceHi)
                        .clickable {
                            val ok = MonitorPrefs.toggleField(context, field)
                            if (ok) picked = MonitorPrefs.fields(context)
                            else Toast.makeText(context, "Keep at least one field", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(label, color = if (checked) accentNow() else GC.TextSoft, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(if (checked) "ON" else "OFF", color = if (checked) accentNow() else GC.TextDim, fontSize = 11.sp)
                }
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
                    .clip(RomKit.R12)
                    .background(RomKit.Panel2)
                    .border(1.dp, RomKit.Stroke, RomKit.R12)
            ) {
                Box(
                    Modifier
                        .background(if (checked) accent else Color.Transparent)
                        .clickable { onToggle(key, true) }
                        .padding(horizontal = 13.dp, vertical = 7.dp)
                ) { Text("In use", style = MaterialTheme.typography.labelMedium, color = if (checked) Color.White else GC.TextSoft, fontSize = 12.sp) }
                Box(
                    Modifier
                        .background(if (!checked) accent else Color.Transparent)
                        .clickable { onToggle(key, false) }
                        .padding(horizontal = 13.dp, vertical = 7.dp)
                ) { Text("Free", style = MaterialTheme.typography.labelMedium, color = if (!checked) Color.White else GC.TextSoft, fontSize = 12.sp) }
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
