package com.gamershub.ui

import com.gamershub.ui.components.AIcon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.data.DeviceStats
import com.gamershub.theme.GC
import com.gamershub.theme.LocalAccent
import com.gamershub.ui.components.Card
import com.gamershub.ui.components.CircleIconButton
import com.gamershub.ui.components.GaugeRing
import com.gamershub.ui.components.IconChip
import com.gamershub.ui.components.SectionLabel
import com.gamershub.ui.components.SegmentedBar
import com.gamershub.ui.components.SolidBar
import com.gamershub.ui.components.StatusPill

/** Profile — account, privilege service (real), live monitoring (real), support. */
@Composable
fun ProfileScreen(
    uid: String,
    version: String,
    sessionTime: String,
    cpu: DeviceStats.Cpu,
    mem: DeviceStats.Mem,
    storage: DeviceStats.Storage,
    ping: Int,
    envLevel: Int,
    envScore: Int,
    onPrivilege: () -> Unit,
    onSettings: () -> Unit
) {
    val accent = LocalAccent.current
    Column(Modifier.fillMaxSize().background(GC.Bg)) {
        // ---- Fixed opaque header: scrolling content can never overlap it ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f)
                .background(GC.Bg)
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Profile", style = MaterialTheme.typography.displaySmall, color = GC.Text, fontSize = 33.sp,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AIcon(Icons.Filled.AccessTime, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(sessionTime, style = MaterialTheme.typography.labelMedium, color = accent, fontSize = 13.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(version, orange = false)
            Spacer(Modifier.width(8.dp))
            CircleIconButton(Icons.Filled.Settings, onClick = onSettings)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 10.dp)
                .padding(bottom = 120.dp)
        ) {
        Text(
            "PID ${android.os.Process.myPid()} · UID ${android.os.Process.myUid()} · v1.3.1",
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium,
            color = GC.TextDim,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(10.dp))

        // ---- Environment level / score (never overlaps the header) ----
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(GC.Surface)
                    .padding(14.dp)
            ) {
                Text("Environment Level", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 11.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$envLevel", style = MaterialTheme.typography.headlineMedium, color = accent, fontSize = 26.sp)
                    Text(" / 3", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { index ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (index < envLevel) accent else Color(0xFF2E2119))
                        )
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(GC.Surface)
                    .padding(14.dp)
            ) {
                Text("Environment Score", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 11.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$envScore", style = MaterialTheme.typography.headlineMedium, color = accent, fontSize = 26.sp)
                    Text(" / 100", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                SolidBar(envScore)
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- Account card ----
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(GC.HeroBrush)
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GC.TagBg)
                    .padding(horizontal = 11.dp, vertical = 5.dp)
            ) {
                AIcon(Icons.Filled.Shield, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(7.dp))
                Text("FREE", style = MaterialTheme.typography.labelMedium, color = accent, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(uid, style = MaterialTheme.typography.headlineLarge, color = GC.Text, fontSize = 28.sp)
                Spacer(Modifier.width(10.dp))
                AIcon(Icons.Filled.ContentCopy, contentDescription = "Copy ID", tint = GC.TextDim, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(5.dp))
            Text("Local profile · no cloud sync", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
        }

        Spacer(Modifier.height(12.dp))

        // ---- Privilege service ----
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(Icons.Filled.Shield, size = 44)
                Spacer(Modifier.width(12.dp))
                Text("Privilege Service", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp, modifier = Modifier.weight(1f))
                StatusPill(if (envLevel >= 2) "Advanced Tier" else if (envLevel >= 1) "Basic Tier" else "Not Activated")
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(GC.Stroke))
            Spacer(Modifier.height(13.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Environment Level", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$envLevel", style = MaterialTheme.typography.headlineLarge, color = accent, fontSize = 28.sp)
                        Text(" / 3", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    SegmentedBar(pct = envLevel * 100 / 3, segments = 3, height = 7)
                }
                Box(Modifier.width(1.dp).height(78.dp).background(GC.Stroke))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Environment Score", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$envScore", style = MaterialTheme.typography.headlineLarge, color = accent, fontSize = 28.sp)
                        Text(" / 100", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    SolidBar(pct = envScore)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(GC.SurfaceHi)
                    .clickable(onClick = onPrivilege)
                    .padding(12.dp)
            ) {
                Text(
                    "Grant overlay · system settings · usage access",
                    style = MaterialTheme.typography.labelMedium, color = GC.TextSoft, fontSize = 12.5.sp,
                    modifier = Modifier.weight(1f)
                )
                Text("Setup ▸", style = MaterialTheme.typography.labelMedium, color = accent, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AIcon(Icons.Filled.AccessTime, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text(sessionTime, style = MaterialTheme.typography.labelMedium, color = accent, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "PID ${android.os.Process.myPid()} · UID ${android.os.Process.myUid()} · v1.3.1",
                    style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 11.sp
                )
            }
        }

        SectionLabel("Real-time Monitoring")

        MonitoringGrid(cpu = cpu, mem = mem, storage = storage)

        Spacer(Modifier.height(12.dp))

        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(Icons.Filled.Wifi, size = 36)
                Spacer(Modifier.width(10.dp))
                Text("Network", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                StatusPill(if (ping in 0..120) "Good" else if (ping > 0) "Slow" else "Offline")
            }
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                if (ping >= 0) {
                    Text("$ping", style = MaterialTheme.typography.headlineLarge, color = accent, fontSize = 28.sp)
                    Text(" ms", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
                } else {
                    Text("--", style = MaterialTheme.typography.headlineLarge, color = GC.TextDim, fontSize = 28.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("8.8.8.8", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            SegmentedBar(pct = if (ping in 0..120) 75 else if (ping > 0) 35 else 0, segments = 4, height = 10)
        }

        SectionLabel("Support Us!")

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(GC.HeroBrush)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(Icons.Filled.Campaign, size = 48)
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("Join Official Channel", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Join official channels to keep track of updates, important information, and official announcements directly.",
                        style = MaterialTheme.typography.labelMedium, color = GC.TextSoft, fontSize = 13.sp, lineHeight = 19.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                CircleIconButton(Icons.Filled.Chat, onClick = { })
                Spacer(Modifier.width(8.dp))
                CircleIconButton(Icons.Filled.MusicNote, onClick = { })
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(deviceLine(), Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
        }
    }
}

private fun deviceLine(): String {
    val abis = try { android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown" } catch (_: Throwable) { "unknown" }
    return "${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} · $abis"
}

/**
 * Adaptive monitoring grid — two columns when there is room, single
 * stacked column on narrow screens so text can never overlap.
 */
@Composable
private fun MonitoringGrid(cpu: DeviceStats.Cpu, mem: DeviceStats.Mem, storage: DeviceStats.Storage) {
    androidx.compose.foundation.layout.BoxWithConstraints {
        if (maxWidth < 400.dp) {
            CpuCard(cpu, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            RamCard(mem, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            StorageCard(storage, Modifier.fillMaxWidth())
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CpuCard(cpu, Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    RamCard(mem, Modifier.fillMaxWidth())
                    StorageCard(storage, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun CpuCard(cpu: DeviceStats.Cpu, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CPU Speed", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            IconChip(Icons.Filled.Speed, size = 36)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GaugeRing(pct = cpu.pct, big = String.format("%.2f", cpu.curGhz), small = "GHz", diameter = 132)
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Max ${String.format("%.2f", cpu.maxGhz)} GHz", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("${cpu.pct}%", style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 14.sp)
        }
    }
}

@Composable
private fun RamCard(mem: DeviceStats.Mem, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("RAM Usage", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            IconChip(Icons.Filled.Storage, size = 36)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${mem.pct}", style = MaterialTheme.typography.headlineLarge, color = accent, fontSize = 28.sp)
            Text(" / 100", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.height(3.dp))
        Text("Total ${mem.totalMb / 1024} GB", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        SegmentedBar(pct = mem.pct, segments = 8)
    }
}

@Composable
private fun StorageCard(storage: DeviceStats.Storage, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Storage", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            IconChip(Icons.Filled.Storage, size = 36, bg = GC.SurfaceHi)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${storage.pct}", style = MaterialTheme.typography.headlineLarge, color = accent, fontSize = 28.sp)
            Text(" / 100", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.height(3.dp))
        Text("Total ${storage.totalGb} GB", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        SolidBar(pct = storage.pct)
    }
}
