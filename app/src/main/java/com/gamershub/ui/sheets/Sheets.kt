package com.gamershub.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.data.InstalledApp
import com.gamershub.theme.GC
import com.gamershub.ui.components.BigButton
import com.gamershub.ui.components.Grabber
import com.gamershub.ui.components.IconChip
import com.gamershub.ui.components.StatusPill
import com.gamershub.ui.components.TagPill
import com.gamershub.ui.components.Toggle
import kotlin.math.roundToInt

/** Sheet overlay frame: scrim + rounded-top card with grabber. */
@Composable
fun SheetFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(GC.Surface)
                .clickable(enabled = false) {}
                .padding(horizontal = 22.dp)
                .padding(bottom = 26.dp)
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Grabber() }
            content()
        }
    }
}

/* ------------------------------------------------------------------ */
/* Low Energy gate                                                      */
/* ------------------------------------------------------------------ */
@Composable
fun LowEnergySheet(onWatchAd: () -> Unit, onBuyPremium: () -> Unit, onDismiss: () -> Unit) {
    SheetFrame(onDismiss) {
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            IconChip(Icons.Filled.Bolt, size = 84)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Low Energy",
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineMedium,
            color = GC.Text,
            fontSize = 32.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "You need at least 80 Energy to open Space. Watch an ad to get 120 Energy instantly!",
            style = MaterialTheme.typography.bodyLarge,
            color = GC.TextSoft,
            fontSize = 16.sp,
            lineHeight = 24.sp
        )
        Spacer(Modifier.height(26.dp))
        BigButton("Watch Ad [+120 Energy]", onWatchAd, Modifier.fillMaxWidth(), leadingIcon = Icons.Filled.Bolt)
        Spacer(Modifier.height(10.dp))
        Text(
            "Only if the ad is shown",
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium,
            color = GC.TextDim,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(14.dp))
        BigButton(
            "Buy Premium [Optional]", onBuyPremium, Modifier.fillMaxWidth(),
            primary = false, leadingIcon = Icons.Filled.Bolt, textColor = GC.Orange
        )
        Spacer(Modifier.height(12.dp))
        BigButton("Maybe Later", onDismiss, Modifier.fillMaxWidth(), primary = false)
    }
}

/* ------------------------------------------------------------------ */
/* Launch mode + slide to open                                          */
/* ------------------------------------------------------------------ */
@Composable
fun LaunchModeSheet(
    app: InstalledApp,
    version: String,
    playMinutes: Long,
    energy: Int,
    onLaunch: (gameSpace: Boolean) -> Unit,
    onLowEnergy: () -> Unit,
    onDismiss: () -> Unit
) {
    var gameSpace by remember { mutableStateOf(true) }
    SheetFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(GC.SurfaceHi),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    androidx.compose.ui.graphics.painter.BitmapPainter(app.icon),
                    contentDescription = null,
                    modifier = Modifier.size(58.dp).clip(RoundedCornerShape(16.dp))
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 21.sp)
                Text(app.pkg, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Row {
                    StatusPill("◌ ${playMinutes}m", orange = false)
                    Spacer(Modifier.width(8.dp))
                    StatusPill("🎮 App", orange = false)
                }
            }
            Text(version, style = MaterialTheme.typography.labelMedium, color = GC.Orange, fontSize = 14.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text("Select Launch Mode", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 22.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LaunchModeCard(
                icon = Icons.Filled.Rocket, title = "Game Space", cost = "⚡ 80 Energy",
                selected = gameSpace, onSelect = { gameSpace = true }, modifier = Modifier.weight(1f)
            )
            LaunchModeCard(
                icon = Icons.Filled.Android, title = "Direct Open", cost = "⚡ 40 Energy",
                selected = !gameSpace, onSelect = { gameSpace = false }, modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(20.dp))
        SlideToOpen {
            val cost = if (gameSpace) 80 else 40
            if (energy >= cost) onLaunch(gameSpace) else onLowEnergy()
        }
    }
}

@Composable
private fun LaunchModeCard(
    icon: ImageVector,
    title: String,
    cost: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color(0xFF3A2214) else GC.SurfaceHi)
            .then(
                if (selected) Modifier.border(2.dp, GC.Orange, RoundedCornerShape(18.dp))
                else Modifier
            )
            .clickable(onClick = onSelect)
            .padding(vertical = 20.dp)
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = GC.Orange, modifier = Modifier.size(20.dp))
            }
        }
        Icon(icon, contentDescription = null, tint = if (selected) GC.Orange else GC.TextSoft, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) GC.Orange else GC.Text,
            fontSize = 19.sp
        )
        Spacer(Modifier.height(8.dp))
        StatusPill(cost, orange = selected)
    }
}

@Composable
private fun SlideToOpen(onOpen: () -> Unit) {
    var trackPx by remember { mutableStateOf(0f) }
    var offsetPx by remember { mutableStateOf(0f) }
    val handlePx = 116.dp
    Box(
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(GC.SurfaceHi)
            .onSizeChanged { trackPx = it.width.toFloat() },
        contentAlignment = Alignment.Center
    ) {
        Text("Slide to Open", style = MaterialTheme.typography.titleMedium, color = GC.TextDim, fontSize = 21.sp)
        Box(
            Modifier
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .padding(6.dp)
                .width(handlePx)
                .height(56.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(GC.Orange)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        offsetPx = (offsetPx + delta).coerceIn(0f, trackPx - 240f)
                    },
                    onDragStopped = {
                        if (offsetPx > (trackPx - 260f) * 0.7f) onOpen()
                        offsetPx = 0f
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.FastForward, contentDescription = "Open", tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

/* ------------------------------------------------------------------ */
/* App selector                                                         */
/* ------------------------------------------------------------------ */
enum class AppFilter { ALL, GAMES, APPS, ADDED, NOT_ADDED }

@Composable
fun AppSelectorSheet(
    apps: List<InstalledApp>,
    added: Set<String>,
    isGame: (String) -> Boolean,
    playMinutes: (String) -> Long,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppFilter.ALL) }
    val shown = apps.filter { app ->
        val matches = app.name.contains(query, true) || app.pkg.contains(query, true)
        val byKind = when (filter) {
            AppFilter.ALL -> true
            AppFilter.GAMES -> isGame(app.pkg)
            AppFilter.APPS -> !isGame(app.pkg)
            AppFilter.ADDED -> added.contains(app.pkg)
            AppFilter.NOT_ADDED -> !added.contains(app.pkg)
        }
        matches && byKind
    }
    SheetFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(Icons.Filled.Apps, size = 56)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("App Selector", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 25.sp)
                Text("${added.size} Added", style = MaterialTheme.typography.labelMedium, color = GC.Orange, fontSize = 15.sp)
            }
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(GC.SurfaceHi)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = GC.TextSoft, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GC.SurfaceHi)
                .padding(horizontal = 16.dp, vertical = 15.dp)
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = GC.TextDim, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Search Apps", color = GC.TextDim, fontSize = 17.sp)
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = GC.Text, fontSize = 17.sp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            FilterChip("All", filter == AppFilter.ALL) { filter = AppFilter.ALL }
            Spacer(Modifier.width(10.dp))
            FilterChip("Games", filter == AppFilter.GAMES) { filter = AppFilter.GAMES }
            Spacer(Modifier.width(10.dp))
            FilterChip("Apps", filter == AppFilter.APPS) { filter = AppFilter.APPS }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(1.dp).height(24.dp).background(GC.Stroke))
            Spacer(Modifier.width(12.dp))
            FilterChip("Added", filter == AppFilter.ADDED) { filter = AppFilter.ADDED }
            Spacer(Modifier.width(10.dp))
            FilterChip("Not Added", filter == AppFilter.NOT_ADDED) { filter = AppFilter.NOT_ADDED }
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(520.dp)
        ) {
            items(shown, key = { it.pkg }) { app ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(GC.SurfaceHi)
                        .padding(14.dp)
                ) {
                    Icon(
                        androidx.compose.ui.graphics.painter.BitmapPainter(app.icon),
                        contentDescription = null,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            app.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = GC.Text,
                            fontSize = 19.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Play time: ${playMinutes(app.pkg)}m",
                            style = MaterialTheme.typography.labelMedium,
                            color = GC.TextDim,
                            fontSize = 15.sp
                        )
                    }
                    Toggle(on = added.contains(app.pkg)) { onToggle(app.pkg, it) }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) GC.Orange else GC.SurfaceHi)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else GC.TextSoft,
            fontSize = 15.sp
        )
    }
}

/* ------------------------------------------------------------------ */
/* Permission sheets                                                    */
/* ------------------------------------------------------------------ */
enum class PermKind { NOTIFICATION, OVERLAY, WRITE_SETTINGS, USAGE, SHIZUKU }

data class PermConfig(
    val kind: PermKind,
    val icon: ImageVector,
    val tag: String,
    val title: String,
    val desc: String,
    val checks: List<String>,
    val info: String? = null,
    val openDetail: Boolean = false
)

val PERM_CONFIGS = listOf(
    PermConfig(
        PermKind.NOTIFICATION, Icons.Filled.Notifications, "REQUIRED · STATUS UPDATES",
        "Notification Permission",
        "Required to display active service notifications and real-time connection progress.",
        listOf(
            "Shows foreground service status while active",
            "Provides interactive wireless pairing notifications"
        )
    ),
    PermConfig(
        PermKind.OVERLAY, Icons.Filled.Apps, "REQUIRED · FLOATING WIDGET",
        "Display Over Other Apps",
        "Allows the app to display a compact status indicator and quick-access panel while using other applications.",
        listOf(
            "Displays the floating quick-settings panel when enabled",
            "Shows real-time system metrics on screen"
        )
    ),
    PermConfig(
        PermKind.WRITE_SETTINGS, Icons.Filled.Tune, "REQUIRED · SYSTEM PREFERENCES",
        "Modify System Settings",
        "Required to adjust display and system preferences according to your saved profiles.",
        listOf(
            "Manages screen brightness and display parameters",
            "Restores default device settings automatically when finished"
        )
    ),
    PermConfig(
        PermKind.USAGE, Icons.Filled.PieChart, "REQUIRED · APP DETECTION",
        "Usage Data Access",
        "Required to identify the active foreground application and record session duration for your selected apps.",
        listOf(
            "Detects when prioritized applications are launched",
            "Tracks session duration & applies saved preferences"
        ),
        info = "If the toggle is disabled on Android 13+, open App Info ▸ top-right menu ▸ Allow restricted settings.",
        openDetail = true
    ),
    PermConfig(
        PermKind.SHIZUKU, Icons.Filled.Shield, "OPTIONAL · ADVANCED SERVICE",
        "Shizuku Service Access",
        "Allows the app to connect with the active Shizuku service on your device for system-level configuration management.",
        listOf(
            "Connects to the privileged service automatically",
            "Enables advanced device & display configuration options"
        )
    )
)

@Composable
fun PermissionSheet(
    config: PermConfig,
    onAllow: () -> Unit,
    onLater: () -> Unit,
    onOpenDetail: () -> Unit
) {
    SheetFrame(onDismiss = onLater) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(config.icon, size = 56)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                TagPill(config.tag)
                Spacer(Modifier.height(6.dp))
                Text(config.title, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 25.sp)
            }
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(GC.SurfaceHi)
                    .clickable(onClick = onLater),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = GC.TextSoft, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(GC.SurfaceHi)
                .padding(18.dp)
        ) {
            Text(config.desc, style = MaterialTheme.typography.bodyLarge, color = GC.TextSoft, fontSize = 16.sp, lineHeight = 24.sp)
            Spacer(Modifier.height(16.dp))
            config.checks.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(GC.BrownPill),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = GC.Orange, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(line, style = MaterialTheme.typography.bodyLarge, color = GC.Text, fontSize = 15.5.sp, lineHeight = 22.sp)
                }
            }
            if (config.info != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF241C15))
                        .padding(12.dp)
                ) {
                    Text("ⓘ  " + config.info, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
        if (config.openDetail) {
            Spacer(Modifier.height(16.dp))
            BigButton(
                "Open App Detail", onOpenDetail, Modifier.fillMaxWidth(),
                primary = false, leadingIcon = Icons.Filled.Settings, textColor = GC.Orange
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BigButton("Later", onLater, Modifier.weight(1f), primary = false)
                BigButton("Allow", onAllow, Modifier.weight(1f))
            }
        } else {
            Spacer(Modifier.height(22.dp))
            if (config.kind == PermKind.NOTIFICATION) {
                BigButton("Allow", onAllow, Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BigButton("Later", onLater, Modifier.weight(1f), primary = false)
                    BigButton("Allow", onAllow, Modifier.weight(1f))
                }
            }
        }
    }
}
