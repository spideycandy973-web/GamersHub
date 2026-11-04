package com.gamershub.ui.sheets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.data.InstalledApp
import com.gamershub.theme.GC
import com.gamershub.theme.LocalAccent
import com.gamershub.ui.components.BigButton
import com.gamershub.ui.components.CheckMark
import com.gamershub.ui.components.CircleIconButton
import com.gamershub.ui.components.GaugeRing
import com.gamershub.ui.components.IconChip
import com.gamershub.ui.components.SlideControl
import com.gamershub.ui.components.StatusPill
import com.gamershub.ui.components.TagPill
import com.gamershub.ui.components.Toggle

/** Shared bottom-sheet frame. */
@Composable
fun SheetFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 780.dp)
            .background(GC.Bg)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(GC.Surface)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp, bottom = 26.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(44.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0x33FFFFFF))
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/* ------------------------------------------------------------------ */
/* Launch mode (energy economy — simulated)                            */
/* ------------------------------------------------------------------ */
@Composable
fun LaunchModeSheet(
    app: InstalledApp,
    playMinutes: Long,
    onLaunch: (gameSpace: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var gameSpace by remember { mutableStateOf(true) }
    SheetFrame(onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF132A1C), Color(0xFF0E1512))))
        ) {
            Image(
                painter = androidx.compose.ui.graphics.painter.BitmapPainter(app.icon),
                contentDescription = null,
                alpha = 0.55f,
                modifier = Modifier.align(Alignment.CenterEnd).size(120.dp)
            )
        }
        Spacer(Modifier.height(0.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(y = (-46).dp).padding(horizontal = 2.dp)) {
            Image(
                painter = androidx.compose.ui.graphics.painter.BitmapPainter(app.icon),
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(15.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.pkg, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row {
                    StatusPill("◌ ${playMinutes}m", orange = false)
                    Spacer(Modifier.width(6.dp))
                    StatusPill("🎮 Game", orange = false)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Select Launch Mode", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 20.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LaunchModeCard(Icons.Filled.Rocket, "Game Space", "⚡ 80 Energy", gameSpace, { gameSpace = true }, Modifier.weight(1f))
            LaunchModeCard(Icons.Filled.Android, "Direct Open", "⚡ 40 Energy", !gameSpace, { gameSpace = false }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        SlideControl(label = "Slide to Open", icon = Icons.Filled.FastForward, onDone = { onLaunch(gameSpace) })
    }
}

@Composable
private fun LaunchModeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    cost: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccent.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFF3A2214) else GC.SurfaceHi)
            .then(if (selected) Modifier.border(2.dp, accent, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClick = onSelect)
            .padding(vertical = 16.dp)
    ) {
        Box(Modifier.fillMaxWidth().padding(end = 8.dp), contentAlignment = Alignment.TopEnd) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Icon(icon, contentDescription = null, tint = if (selected) accent else GC.TextSoft, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) accent else GC.Text, fontSize = 17.sp)
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) accent.copy(alpha = 0.22f) else Color(0x14FFFFFF))
                .padding(horizontal = 11.dp, vertical = 5.dp)
        ) {
            Text(cost, style = MaterialTheme.typography.labelMedium, color = if (selected) accent else GC.TextDim, fontSize = 12.sp)
        }
    }
}

/* ------------------------------------------------------------------ */
/* App selector                                                        */
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
    val accent = LocalAccent.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppFilter.ALL) }
    SheetFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(Icons.Filled.Dialpad, size = 50)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("App Selector", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 22.sp)
                Text("${added.size} Added", style = MaterialTheme.typography.labelMedium, color = accent, fontSize = 13.sp)
            }
            CircleIconButton(Icons.Filled.Close, onClick = onDismiss)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(GC.SurfaceHi)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = GC.Text, fontSize = 15.sp),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            AppFilter.entries.forEach { f ->
                val selected = filter == f
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) accent else GC.SurfaceHi)
                        .clickable { filter = f }
                        .padding(horizontal = 15.dp, vertical = 9.dp)
                ) {
                    Text(
                        f.name.lowercase().replaceFirstChar { it.uppercase() }.replace("_", " "),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) Color.White else GC.TextSoft,
                        fontSize = 13.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val shown = apps.filter { a ->
            val matchQ = query.isEmpty() || a.name.contains(query, true) || a.pkg.contains(query, true)
            val matchF = when (filter) {
                AppFilter.ALL -> true
                AppFilter.GAMES -> isGame(a.pkg)
                AppFilter.APPS -> !isGame(a.pkg)
                AppFilter.ADDED -> a.pkg in added
                AppFilter.NOT_ADDED -> a.pkg !in added
            }
            matchQ && matchF
        }
        shown.forEach { a ->
            val isAdded = a.pkg in added
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isAdded) accent.copy(alpha = 0.12f) else Color.Transparent)
                    .padding(8.dp)
            ) {
                Image(
                    painter = androidx.compose.ui.graphics.painter.BitmapPainter(a.icon),
                    contentDescription = null,
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Play time: ${playMinutes(a.pkg)}m", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
                }
                Toggle(on = isAdded) { v ->
                    if (v && added.size >= 10) {
                        android.widget.Toast.makeText(context, "Library is full (10 / 10)", android.widget.Toast.LENGTH_SHORT).show()
                    } else onToggle(a.pkg, v)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Settings sheet                                                      */
/* ------------------------------------------------------------------ */
@Composable
fun SettingsSheet(accentIndex: Int, onAccent: (Int) -> Unit, onLanguage: () -> Unit, onDismiss: () -> Unit) {
    val accent = LocalAccent.current
    SheetFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(Icons.Filled.Settings, size = 50)
            Spacer(Modifier.width(12.dp))
            Text("Settings", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 24.sp, modifier = Modifier.weight(1f))
            CircleIconButton(Icons.Filled.Close, onClick = onDismiss)
        }
        Spacer(Modifier.height(14.dp))
        Card2 {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Language", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                    Text("Choose your preferred language", style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 13.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GC.BrownPill)
                        .clickable(onClick = onLanguage)
                        .padding(horizontal = 13.dp, vertical = 8.dp)
                ) {
                    Text("System Default", style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 13.sp)
                    Spacer(Modifier.width(5.dp))
                    Text("›", style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 15.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card2 {
            Text("Theme Color", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
            Spacer(Modifier.height(3.dp))
            Text("Customize app accent color", style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            val names = listOf("Orange", "Ocean Blue", "Neon Tosca", "Crimson Red")
            names.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { name ->
                        val idx = names.indexOf(name)
                        val selected = accentIndex == idx
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) GC.TagBg else GC.SurfaceHi)
                                .clickable { onAccent(idx) }
                                .padding(12.dp)
                        ) {
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(GC.accentFor(idx)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(9.dp))
                            Text(name, style = MaterialTheme.typography.labelLarge, color = if (selected) accent else GC.Text, fontSize = 13.sp)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun Card2(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GC.SurfaceHi)
            .padding(15.dp),
        content = content
    )
}

/* ------------------------------------------------------------------ */
/* Permissions                                                         */
/* ------------------------------------------------------------------ */
enum class PermKind { NOTIFICATION, OVERLAY, WRITE_SETTINGS, USAGE, SHIZUKU }

data class PermConfig(
    val kind: PermKind,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tag: String,
    val title: String,
    val desc: String,
    val checks: List<String>,
    val info: String? = null
)

val PERM_CONFIGS = listOf(
    PermConfig(PermKind.NOTIFICATION, Icons.Filled.Info, "REQUIRED · STATUS UPDATES",
        "Notification Permission",
        "Required to display active service notifications and real-time connection progress.",
        listOf("Shows foreground service status while active", "Provides interactive wireless pairing notifications")),
    PermConfig(PermKind.OVERLAY, Icons.Filled.Apps, "REQUIRED · FLOATING WIDGET",
        "Display Over Other Apps",
        "Allows the app to display a compact status indicator and quick-access panel while using other applications.",
        listOf("Displays the floating quick-settings panel when enabled", "Shows real-time system metrics on screen")),
    PermConfig(PermKind.WRITE_SETTINGS, Icons.Filled.Settings, "REQUIRED · SYSTEM PREFERENCES",
        "Modify System Settings",
        "Required to adjust display and system preferences according to your saved profiles.",
        listOf("Manages screen brightness and display parameters", "Restores default device settings automatically when finished")),
    PermConfig(PermKind.USAGE, Icons.Filled.Apps, "REQUIRED · APP DETECTION",
        "Usage Data Access",
        "Required to identify the active foreground application and record session duration for your selected apps.",
        listOf("Detects when prioritized applications are launched", "Tracks session duration & applies saved preferences"),
        info = "If the toggle is disabled on Android 13+, open App Info ▸ top-right menu ▸ Allow restricted settings."),
    PermConfig(PermKind.SHIZUKU, Icons.Filled.Shield, "OPTIONAL · ADVANCED SERVICE",
        "Shizuku Service Access",
        "Allows the app to connect with the active Shizuku service on your device for system-level configuration management.",
        listOf("Connects to the privileged service automatically", "Enables advanced device & display configuration options"))
)

@Composable
fun PermissionSheet(
    config: PermConfig,
    granted: Boolean,
    onAllow: () -> Unit,
    onLater: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val accent = LocalAccent.current
    SheetFrame(onDismiss = onLater) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(config.icon, size = 50)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                TagPill(config.tag)
                Spacer(Modifier.height(5.dp))
                Text(config.title, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CircleIconButton(Icons.Filled.Close, onClick = onLater)
        }
        Spacer(Modifier.height(12.dp))
        Card2 {
            Text(config.desc, style = MaterialTheme.typography.bodyLarge, color = GC.TextSoft, fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(12.dp))
            config.checks.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(GC.BrownPill), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(line, style = MaterialTheme.typography.bodyLarge, color = GC.Text, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            if (config.info != null) {
                Spacer(Modifier.height(8.dp))
                Text("ⓘ  " + config.info, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.5.sp, lineHeight = 18.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (granted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(GC.SurfaceHi)
                    .padding(14.dp)
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Granted ✓", style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        BigButton("Open App Detail", onOpenDetail, Modifier.fillMaxWidth(), primary = false, leadingIcon = Icons.Filled.Settings, textColor = accent)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Later", onLater, Modifier.weight(1f), primary = false, textColor = GC.TextSoft)
            BigButton("Allow", onAllow, Modifier.weight(1f), primary = true)
        }
    }
}

/* ------------------------------------------------------------------ */
/* Low Energy gate + ad reward + Game Ready                            */
/* ------------------------------------------------------------------ */
@Composable
fun LowEnergySheet(needed: Int, onWatchAd: () -> Unit, onBuyPremium: () -> Unit, onDismiss: () -> Unit) {
    val accent = LocalAccent.current
    SheetFrame(onDismiss = onDismiss) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { IconChip(Icons.Filled.Bolt, size = 62) }
        Spacer(Modifier.height(12.dp))
        Text("Low Energy", Modifier.fillMaxWidth(), style = MaterialTheme.typography.displaySmall, color = GC.Text, fontSize = 30.sp, textAlign = TextAlign.Center, fontFamily = GC.Display)
        Spacer(Modifier.height(8.dp))
        Text(
            "You need at least $needed Energy to open Space. Watch an ad to get 120 Energy instantly!",
            Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge, color = GC.TextDim,
            fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        BigButton("⚡ Watch Ad (+120 Energy)", onWatchAd, Modifier.fillMaxWidth(), primary = true, leadingIcon = Icons.Filled.Bolt)
        Spacer(Modifier.height(6.dp))
        Text("Only if the ad is shown", Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 11.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        BigButton("Buy Premium (Optional)", onBuyPremium, Modifier.fillMaxWidth(), primary = false, leadingIcon = Icons.Filled.CheckCircle, textColor = accent)
        Spacer(Modifier.height(10.dp))
        BigButton("Maybe Later", onDismiss, Modifier.fillMaxWidth(), primary = false, textColor = GC.TextSoft)
    }
}

@Composable
fun AdLoadingDialog(onFinished: (reward: Int) -> Unit) {
    val accent = LocalAccent.current
    var secondsLeft by remember { mutableIntStateOf(15) }
    var skipLeft by remember { mutableIntStateOf(3) }
    val progress by androidx.compose.animation.core.animateFloatAsState((15 - secondsLeft) / 15f, label = "adprog")
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            kotlinx.coroutines.delay(1000)
            secondsLeft--
            if (skipLeft > 0) skipLeft--
        }
        onFinished(120)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xF51A1314))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Loading ad...", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text("Stay here — you'll get your reward even if the ad doesn't load.", style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x22FFFFFF))
            ) {
                Box(Modifier.fillMaxWidth(progress).height(8.dp).background(accent))
            }
            Spacer(Modifier.height(6.dp))
            Text("${secondsLeft}s", Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp, textAlign = TextAlign.End)
            Spacer(Modifier.height(12.dp))
            RewardRow("If the ad is shown", "120 Energy", orange = true)
            RewardRow("Alternative reward", "60 Energy", orange = false)
            RewardRow("If the ad fails", "60 Energy", orange = false)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GC.SurfaceHi)
                    .clickable(enabled = skipLeft == 0) { onFinished(60) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (skipLeft > 0) "Skip ($skipLeft)" else "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (skipLeft > 0) GC.TextDim else GC.Text,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun RewardRow(label: String, value: String, orange: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = if (orange) Color(0xFFE05A2B) else GC.Text, fontSize = 14.sp)
    }
}

@Composable
fun GameReadySheet(onDone: () -> Unit, onDismiss: () -> Unit) {
    SheetFrame(onDismiss = onDismiss) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { IconChip(Icons.Filled.Bolt, size = 62) }
        Spacer(Modifier.height(12.dp))
        Text("Game Ready", Modifier.fillMaxWidth(), style = MaterialTheme.typography.displaySmall, color = GC.Text, fontSize = 30.sp, textAlign = TextAlign.Center, fontFamily = GC.Display)
        Spacer(Modifier.height(8.dp))
        Text(
            "Get your device ready for your next session. Everything goes back to normal when you're done.",
            Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge, color = GC.TextDim,
            fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(GC.SurfaceHi)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = GC.TextDim, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text("Results may vary by device.", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.5.sp)
        }
        Spacer(Modifier.height(16.dp))
        SlideControl(label = "Slide to get ready", icon = Icons.Filled.Autorenew, onDone = onDone)
    }
}

/* ------------------------------------------------------------------ */
/* Display tuning + Activation center                                  */
/* ------------------------------------------------------------------ */
@Composable
fun DisplayTuningSheet(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val accent = LocalAccent.current
    val dm = context.resources.displayMetrics
    val cfg = context.resources.configuration
    var navMode by remember {
        mutableIntStateOf(
            try { android.provider.Settings.Secure.getInt(context.contentResolver, "navigation_mode") } catch (_: Throwable) { 2 }
        )
    }
    SheetFrame(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(Icons.Filled.Palette, size = 50)
            Spacer(Modifier.width(12.dp))
            Text("Display Tuning", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 24.sp, modifier = Modifier.weight(1f))
            CircleIconButton(Icons.Filled.Close, onClick = onDismiss)
        }
        Spacer(Modifier.height(14.dp))
        Card2 {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(Icons.Filled.Language, size = 42)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Navigation Mode", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                    Text("Change how you navigate your device", style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 12.5.sp)
                }
            }
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("3-Button", "2-Button", "Gestual").forEachIndexed { i, label ->
                    val selected = navMode == i
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) accent else GC.SurfaceHi)
                            .clickable {
                                val ok = try {
                                    android.provider.Settings.Secure.putInt(context.contentResolver, "navigation_mode", i)
                                } catch (_: Throwable) { false }
                                if (ok) navMode = i
                                else android.widget.Toast.makeText(context, "Requires Activation — see Activation Center", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else GC.TextSoft, fontSize = 14.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card2 {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(Icons.Filled.Apps, size = 42)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Resolution Changer", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                    Text("Change screen resolution (Width x Height)", style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 12.5.sp)
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("Res-Ratio: 1.00"); StatPill("W-Scale: 1.00"); StatPill("H-Scale: 1.00")
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ValueBox("${dm.widthPixels}", "W", Modifier.weight(1f))
                Text("X", style = MaterialTheme.typography.titleMedium, color = GC.TextDim, fontSize = 18.sp)
                ValueBox("${dm.heightPixels}", "H", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(GC.SurfaceHi)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) { Text("Applied", style = MaterialTheme.typography.labelLarge, color = GC.TextDim, fontSize = 15.sp) }
        }
        Spacer(Modifier.height(12.dp))
        Card2 {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(Icons.Filled.Apps, size = 42)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Smallest Width Changer", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                    Text("Override screen DPI & smallest width directly", style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 12.5.sp)
                }
            }
            Spacer(Modifier.height(9.dp))
            StatPill("DPI-Proportion: 5.35%")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ValueBox("${cfg.smallestScreenWidthDp}", "dp", Modifier.weight(1f))
                Text("=", style = MaterialTheme.typography.titleMedium, color = GC.TextDim, fontSize = 18.sp)
                ValueBox("${dm.densityDpi}", "ppi", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(GC.SurfaceHi)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) { Text("Applied", style = MaterialTheme.typography.labelLarge, color = GC.TextDim, fontSize = 15.sp) }
        }
    }
}

@Composable
private fun StatPill(text: String) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.22f))
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = accent, fontSize = 11.5.sp)
    }
}

@Composable
private fun ValueBox(value: String, suffix: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x14FFFFFF))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Text(suffix, style = MaterialTheme.typography.labelLarge, color = GC.TextDim, fontSize = 14.sp)
    }
}

@Composable
fun ActivationSheet(
    envLevel: Int,
    shizukuActive: Boolean,
    onOpenShizuku: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val accent = LocalAccent.current
    var quickStart by remember { mutableStateOf(true) }
    SheetFrame(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Activation Center", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 24.sp, modifier = Modifier.weight(1f))
            CircleIconButton(Icons.Filled.Close, onClick = onDismiss)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GaugeRing(pct = envLevel * 100 / 3, big = "$envLevel", small = "/ 3", diameter = 128)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(GC.SurfaceHi).padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(8.dp))
                Text("ADVANCED TIER", style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 13.sp, letterSpacing = 1.2.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(GC.SurfaceHi)
                .padding(5.dp)
        ) {
            listOf(true to "Quick Start", false to "Manual Pairing").forEach { (qs, label) ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (quickStart == qs) accent else Color.Transparent)
                        .clickable { quickStart = qs }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = if (quickStart == qs) Color.White else GC.TextSoft, fontSize = 15.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (quickStart) {
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(Icons.Filled.Android, size = 44)
                    Spacer(Modifier.width(11.dp))
                    Text("Shizuku Access", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp, modifier = Modifier.weight(1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (shizukuActive) accent.copy(alpha = 0.2f) else GC.Bg)
                            .clickable(onClick = onOpenShizuku)
                            .padding(horizontal = 13.dp, vertical = 7.dp)
                    ) {
                        if (shizukuActive) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(if (shizukuActive) "Activated" else "Activate", style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 13.sp)
                    }
                }
            }
        } else {
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(Icons.Filled.Dns, size = 44)
                    Spacer(Modifier.width(11.dp))
                    Text("TCP/IP Mode", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp)
                }
                Spacer(Modifier.height(9.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .padding(horizontal = 11.dp, vertical = 7.dp)
                ) {
                    Text("TCP/IP active on port 5555", style = MaterialTheme.typography.labelMedium, color = accent, fontSize = 12.sp)
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    "ADB TCP/IP looks active on this device, but this app hasn't paired with it yet — it was likely turned on another way. Pair once below to trust this device.",
                    style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 12.5.sp, lineHeight = 18.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(Icons.Filled.Dns, size = 44)
                    Spacer(Modifier.width(11.dp))
                    Text("Wireless Debugging", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp)
                }
                Spacer(Modifier.height(8.dp))
                StatPill("ADB Wireless Debugging")
                Spacer(Modifier.height(10.dp))
                listOf(
                    "Open Wireless Debugging in Developer Options",
                    "Select 'Pair device with pairing code'",
                    "Come back here and start discovery"
                ).forEachIndexed { i, step ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = accent, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(step, style = MaterialTheme.typography.bodyMedium, color = GC.Text, fontSize = 13.5.sp, lineHeight = 19.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Settings", style = MaterialTheme.typography.labelLarge, color = GC.Text, fontSize = 15.sp) }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(accent)
                            .clickable {
                                android.widget.Toast.makeText(context, "Searching for pairing codes…", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Start", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 15.sp) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(Icons.Filled.Dns, size = 44)
                    Spacer(Modifier.width(11.dp))
                    Text("ADB via OTG", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp)
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(accent).padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                        Text("This device = Computer", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 13.sp)
                    }
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(Color(0x14FFFFFF)).padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                        Text("This device = Target", style = MaterialTheme.typography.labelLarge, color = GC.TextSoft, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                listOf(
                    "Enable USB Debugging on the Target device",
                    "Plug the OTG adapter into THIS device, then connect the Target",
                    "Tap Scan — approve the USB permission dialog",
                    "Tap Activate — approve the Allow USB debugging dialog"
                ).forEachIndexed { i, step ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 5.dp)) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = accent, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(step, style = MaterialTheme.typography.bodyMedium, color = GC.Text, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(accent)
                        .clickable {
                            android.widget.Toast.makeText(context, "Plug in the OTG cable to scan", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Scan USB Device", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 15.sp) }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Export / Manage presets                                             */
/* ------------------------------------------------------------------ */
@Composable
fun ExportPresetSheet(displayInfo: String, onExport: (String, String) -> Unit, onDismiss: () -> Unit) {
    val accent = LocalAccent.current
    var creator by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    SheetFrame(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(Icons.Filled.Download, size = 50)
            Spacer(Modifier.width(12.dp))
            Text("Export Preset", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 23.sp, modifier = Modifier.weight(1f))
            CircleIconButton(Icons.Filled.Close, onClick = onDismiss)
        }
        Spacer(Modifier.height(14.dp))
        Text("Creator Name", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        InputRow("e.g. FahrezONE", creator) { creator = it }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(2f)) {
                Text("Preset Name", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                InputRow("e.g. Ultrawide Pro", name) { name = it }
            }
            Column(Modifier.weight(1f)) {
                Text("Version", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                InputRow("1", "1") {}
            }
        }
        Spacer(Modifier.height(12.dp))
        Card2 {
            Row {
                Text("Preset_Name", style = MaterialTheme.typography.labelLarge, color = GC.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("Creator", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
            }
            Spacer(Modifier.height(9.dp))
            SpecRow("Resolution (WxH)", displayInfo.substringBefore(" · "), orange = false)
            SpecRow("Res-Ratio", "1.00", orange = true)
            SpecRow("Scales (W/H)", "1.00x / 1.00x", orange = true)
            SpecRow("Smallest Width", "406 dp / 480 ppi", orange = false)
            SpecRow("DPI Proportion", "5.35 %", orange = true)
        }
        Spacer(Modifier.height(14.dp))
        BigButton(
            "Export",
            { onExport(creator, name) },
            Modifier.fillMaxWidth(),
            primary = creator.isNotBlank() && name.isNotBlank(),
            textColor = if (creator.isNotBlank() && name.isNotBlank()) Color.White else GC.TextDim
        )
    }
}

@Composable
private fun InputRow(hint: String, value: String, onChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x14FFFFFF))
            .padding(horizontal = 14.dp, vertical = 15.dp)
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = GC.Text, fontSize = 15.sp),
            modifier = Modifier.fillMaxWidth()
        )
        if (value.isEmpty()) {
            Text(hint, style = MaterialTheme.typography.bodyLarge, color = GC.TextDim, fontSize = 15.sp)
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String, orange: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = if (orange) Color(0xFFE05A2B) else GC.Text, fontSize = 14.sp)
    }
}

@Composable
fun ManagePresetsSheet(presets: List<String>, onImport: () -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    val accent = LocalAccent.current
    SheetFrame(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(Icons.Filled.Palette, size = 50)
            Spacer(Modifier.width(12.dp))
            Text("Manage Presets", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 23.sp, modifier = Modifier.weight(1f))
            CircleIconButton(Icons.Filled.Close, onClick = onDismiss)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Reset All", onClear, Modifier.weight(1f), primary = false, textColor = accent)
            BigButton("Import", onImport, Modifier.weight(1f), primary = true)
        }
        Spacer(Modifier.height(14.dp))
        Text("Quick Presets", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 17.sp)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            listOf("0.8x", "0.8x", "0.7x", "0.8x").forEach {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GC.SurfaceHi)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = GC.TextSoft, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (presets.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GC.SurfaceHi)
                    .padding(22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No presets found in /Download/axgc_presets/", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        } else {
            presets.forEach {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GC.SurfaceHi)
                        .padding(13.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = GC.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
