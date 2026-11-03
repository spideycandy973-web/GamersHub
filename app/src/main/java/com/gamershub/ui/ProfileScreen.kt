package com.gamershub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.data.DeviceStats
import com.gamershub.theme.GC
import com.gamershub.ui.components.BigButton
import com.gamershub.ui.components.CircleIconButton
import com.gamershub.ui.components.GaugeRing
import com.gamershub.ui.components.IconChip
import com.gamershub.ui.components.SectionLabel
import com.gamershub.ui.components.SegmentedBar
import com.gamershub.ui.components.SolidBar
import com.gamershub.ui.components.StatusPill

/** Profile — account card, privilege service, live monitoring, support. */
@Composable
fun ProfileScreen(
    uid: String,
    version: String,
    premium: Boolean,
    cpu: DeviceStats.Cpu,
    mem: DeviceStats.Mem,
    storage: DeviceStats.Storage,
    ping: Int,
    envLevel: Int,
    envScore: Int,
    onBuyRenew: () -> Unit,
    onPrivilege: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(top = 18.dp, bottom = 130.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Profile", style = MaterialTheme.typography.displaySmall, color = GC.Text, fontSize = 38.sp)
            Spacer(Modifier.weight(1f))
            StatusPill(version, orange = false)
            Spacer(Modifier.width(10.dp))
            CircleIconButton(Icons.Filled.Settings, onClick = onSettings)
        }

        Spacer(Modifier.height(18.dp))

        // ---- Account card ----
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(GC.HeroBrush)
                .padding(22.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GC.TagBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = GC.Orange, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (premium) "PREMIUM" else "FREE",
                    style = MaterialTheme.typography.labelMedium, color = GC.Orange, fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(uid, style = MaterialTheme.typography.headlineLarge, color = GC.Text, fontSize = 32.sp)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy ID", tint = GC.TextDim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GC.Orange)
                        .clickable(onClick = onBuyRenew)
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Text("BUY / RENEW", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (premium) "Valid Until: Lifetime" else "Valid Until: Lifetime (Free tier)",
                style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 15.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        // ---- Privilege service ----
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(GC.Surface)
                .padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(Icons.Filled.Shield, size = 48)
                Spacer(Modifier.width(14.dp))
                Text("Privilege Service", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 21.sp)
                Spacer(Modifier.weight(1f))
                StatusPill(if (envLevel >= 3) "Active" else "Not Activated")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Campaign, contentDescription = null, tint = Color.Transparent, modifier = Modifier.size(0.dp))
                Icon(
                    Icons.Filled.Settings, contentDescription = null, tint = Color.Transparent, modifier = Modifier.size(0.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(GC.Stroke))
            Spacer(Modifier.height(16.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Environment Level", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$envLevel", style = MaterialTheme.typography.headlineLarge, color = GC.Orange, fontSize = 32.sp)
                        Text(" / 3", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    SegmentedBar(pct = envLevel * 100 / 3, segments = 3, height = 8)
                }
                Box(Modifier.width(1.dp).height(90.dp).background(GC.Stroke))
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text("Environment Score", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$envScore", style = MaterialTheme.typography.headlineLarge, color = GC.Orange, fontSize = 32.sp)
                        Text(" / 100", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    SolidBar(pct = envScore)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GC.SurfaceHi)
                    .clickable(onClick = onPrivilege)
                    .padding(14.dp)
            ) {
                Text(
                    "Grant overlay · system settings · usage access to raise your level",
                    style = MaterialTheme.typography.labelMedium, color = GC.TextSoft, fontSize = 14.sp
                )
                Spacer(Modifier.weight(1f))
                Text("Setup ▸", style = MaterialTheme.typography.labelMedium, color = GC.Orange, fontSize = 14.sp)
            }
        }

        SectionLabel("Real-time Monitoring")

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // CPU card
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(GC.Surface)
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CPU Speed", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                    Spacer(Modifier.weight(1f))
                    IconChip(Icons.Filled.Speed, size = 40)
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    GaugeRing(
                        pct = cpu.pct,
                        big = String.format("%.2f", cpu.curGhz),
                        small = "GHz"
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Max ${String.format("%.2f", cpu.maxGhz)} GHz",
                        style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 15.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text("${cpu.pct}%", style = MaterialTheme.typography.labelLarge, color = GC.Orange, fontSize = 17.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.weight(1f)) {
                // RAM card
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(GC.Surface)
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("RAM Usage", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                        Spacer(Modifier.weight(1f))
                        IconChip(Icons.Filled.Storage, size = 40)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${mem.pct}", style = MaterialTheme.typography.headlineLarge, color = GC.Orange, fontSize = 34.sp)
                        Text(" / 100", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 15.sp)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Total ${mem.totalMb / 1024} GB",
                            style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 15.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    SegmentedBar(pct = mem.pct, segments = 8)
                }
                // Storage card
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(GC.Surface)
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Storage", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                        Spacer(Modifier.weight(1f))
                        IconChip(Icons.Filled.Storage, size = 40, bg = GC.SurfaceHi)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${storage.pct}", style = MaterialTheme.typography.headlineLarge, color = GC.Orange, fontSize = 34.sp)
                        Text(" / 100", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 15.sp)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Total ${storage.totalGb} GB",
                            style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 15.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    SolidBar(pct = storage.pct)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- Network card ----
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(GC.Surface)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(Icons.Filled.Wifi, size = 40)
                Spacer(Modifier.width(12.dp))
                Text("Network", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
                StatusPill(if (ping in 0..120) "Good" else if (ping > 0) "Slow" else "Offline")
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                if (ping >= 0) {
                    Text("$ping", style = MaterialTheme.typography.headlineLarge, color = GC.Orange, fontSize = 32.sp)
                    Text(" ms", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 15.sp)
                } else {
                    Text("--", style = MaterialTheme.typography.headlineLarge, color = GC.TextDim, fontSize = 32.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("8.8.8.8", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 16.sp)
            }
            Spacer(Modifier.height(10.dp))
            SegmentedBar(pct = if (ping in 0..120) 75 else if (ping > 0) 35 else 0, segments = 4, height = 12)
        }

        SectionLabel("Support Us!")

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(GC.HeroBrush)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(Icons.Filled.Campaign, size = 54)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Join Official Channel", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 21.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Join official channels to keep track of updates, important information, and official announcements directly.",
                        style = MaterialTheme.typography.labelMedium, color = GC.TextSoft, fontSize = 15.sp, lineHeight = 22.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                CircleIconButton(Icons.Filled.Chat, onClick = { })
                Spacer(Modifier.width(10.dp))
                CircleIconButton(Icons.Filled.MusicNote, onClick = { })
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            deviceLine(),
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium,
            color = GC.TextDim,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(10.dp))
    }
}

private fun deviceLine(): String {
    val abis = try {
        android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    } catch (_: Throwable) {
        "unknown"
    }
    return "${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} · $abis"
}
