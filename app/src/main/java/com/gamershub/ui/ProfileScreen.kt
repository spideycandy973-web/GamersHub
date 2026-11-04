package com.gamershub.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.gamershub.ui.components.CircleIconButton
import com.gamershub.ui.components.GaugeRing
import com.gamershub.ui.components.IconChip
import com.gamershub.ui.components.SegmentedBar
import com.gamershub.ui.components.SolidBar
import com.gamershub.ui.components.StatusPill
import java.util.Locale

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
    onSettings: () -> Unit,
    onBuyRenew: () -> Unit
) {
    val accent = LocalAccent.current
    Column(Modifier.fillMaxSize().background(GC.Bg)) {
        // ---- Fixed opaque header ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(GC.Bg)
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text("Profile", style = MaterialTheme.typography.displaySmall, color = GC.Text, fontSize = 33.sp, fontFamily = GC.Display, modifier = Modifier.weight(1f))
            StatusPill(version, orange = false)
            Spacer(Modifier.width(8.dp))
            CircleIconButton(Icons.Filled.Settings, onClick = onSettings)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 120.dp)
        ) {
            // ---- Account card ----
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(GC.TagBg).padding(horizontal = 11.dp, vertical = 6.dp)
                    ) {
                        Text("👤 FREE", style = MaterialTheme.typography.labelMedium, color = accent, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(uid, style = MaterialTheme.typography.headlineMedium, color = GC.Text, fontSize = 26.sp, fontFamily = GC.Display)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = GC.TextDim, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(accent)
                            .clickable(onClick = onBuyRenew)
                            .padding(horizontal = 20.dp, vertical = 11.dp)
                    ) {
                        Text("BUY / RENEW", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Valid Until: Lifetime", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
            }

            Spacer(Modifier.height(12.dp))

            // ---- Privilege Service ----
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(Icons.Filled.Shield, size = 44)
                    Spacer(Modifier.width(11.dp))
                    Text("Privilege Service", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(GC.TagBg).clickable(onClick = onPrivilege).padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("● Advanced Tier", style = MaterialTheme.typography.labelMedium, color = accent, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(13.dp))
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Environment Level", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$envLevel", style = MaterialTheme.typography.headlineMedium, color = accent, fontSize = 27.sp)
                            Text(" / 3", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(7.dp))
                        SegmentedBar(pct = envLevel * 100 / 3, segments = 3, height = 5, modifier = Modifier.width(110.dp))
                    }
                    Box(Modifier.width(1.dp).height(64.dp).background(Color(0x14FFFFFF)))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Environment Score", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$envScore", style = MaterialTheme.typography.headlineMedium, color = accent, fontSize = 27.sp)
                            Text(" / 100", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(7.dp))
                        SolidBar(pct = envScore, modifier = Modifier.width(120.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AccessTime, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(sessionTime, style = MaterialTheme.typography.labelMedium, color = accent, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "PID ${android.os.Process.myPid()} · UID ${android.os.Process.myUid()} · v1.3.1",
                        style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("Real-time Monitoring", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp)
            Spacer(Modifier.height(10.dp))

            MonitoringGrid(cpu, mem, storage)

            Spacer(Modifier.height(12.dp))

            // ---- Network ----
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(Icons.Filled.Dns, size = 38)
                    Spacer(Modifier.width(10.dp))
                    Text("Network", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 17.sp, modifier = Modifier.weight(1f))
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
                Spacer(Modifier.height(9.dp))
                SegmentedBar(pct = if (ping in 0..200) 100 - ping / 2 else 0, segments = 5, height = 5)
            }

            Spacer(Modifier.height(14.dp))
            Text("Support Us!", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp)
            Spacer(Modifier.height(10.dp))
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(Icons.Filled.Campaign, size = 46)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Join Official Channel", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 16.sp)
                        Text(
                            "Join official channels to keep track of updates, important information, and official announcements directly.",
                            style = MaterialTheme.typography.labelMedium, color = GC.TextSoft, fontSize = 12.sp, lineHeight = 18.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    CircleIconButton(Icons.Filled.Chat, onClick = { })
                    Spacer(Modifier.width(6.dp))
                    CircleIconButton(Icons.Filled.MusicNote, onClick = { })
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: ""}",
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun MonitoringGrid(cpu: DeviceStats.Cpu, mem: DeviceStats.Mem, storage: DeviceStats.Storage) {
    val accent = LocalAccent.current
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
            GaugeRing(pct = cpu.pct, big = String.format(Locale.US, "%.2f", cpu.curGhz), small = "GHz", diameter = 132)
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Max ${String.format(Locale.US, "%.2f", cpu.maxGhz)} GHz", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
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
