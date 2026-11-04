package com.gamershub.ui.sheets

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.filled.Search
import java.util.Locale
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.data.InstalledApp
import com.gamershub.theme.GC
import com.gamershub.theme.LocalAccent
import com.gamershub.ui.components.BigButton
import com.gamershub.ui.components.CircleIconButton
import com.gamershub.ui.components.Grabber
import com.gamershub.ui.components.IconChip
import com.gamershub.ui.components.StatusPill
import com.gamershub.ui.components.TagPill
import com.gamershub.ui.components.Toggle
import kotlin.math.roundToInt

/** Sheet overlay frame — scrim + rounded-top card, scrollable content, fits any screen. */
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
                .heightIn(max = 760.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(GC.Surface)
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .navigationBarsPadding()
                .padding(bottom = 18.dp)
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Grabber() }
            content()
        }
    }
}

/* ------------------------------------------------------------------ */
/* Launch mode + slide to open (no energy gate — removed per request)   */
/* ------------------------------------------------------------------ */
@Composable
fun LaunchModeSheet(
    app: InstalledApp,
    version: String,
    playMinutes: Long,
    onLaunch: (gameSpace: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var gameSpace by remember { mutableStateOf(true) }
    SheetFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                            painter = androidx.compose.ui.graphics.painter.BitmapPainter(app.icon),
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(15.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.pkg, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row {
                    StatusPill("◌ ${playMinutes}m", orange = false)
                    Spacer(Modifier.width(6.dp))
                    StatusPill(version, orange = false)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Select Launch Mode", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 20.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LaunchModeCard(
                icon = Icons.Filled.Rocket, title = "Game Space",
                selected = gameSpace, onSelect = { gameSpace = true }, modifier = Modifier.weight(1f)
            )
            LaunchModeCard(
                icon = Icons.Filled.Android, title = "Direct Open",
                selected = !gameSpace, onSelect = { gameSpace = false }, modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(16.dp))
        SlideToOpen { onLaunch(gameSpace) }
    }
}

@Composable
private fun LaunchModeCard(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccent.current
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (selected) 1f else 0.97f,
        androidx.compose.animation.core.spring(
            androidx.compose.animation.core.Spring.DampingRatioNoBouncy
        ),
        label = "mode"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFF3A2214) else GC.SurfaceHi)
            .then(
                if (selected) Modifier.border(2.dp, accent, RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable(onClick = onSelect)
            .padding(vertical = 16.dp)
    ) {
        Box(Modifier.fillMaxWidth().padding(end = 8.dp), contentAlignment = Alignment.TopEnd) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
        }
        Icon(icon, contentDescription = null, tint = if (selected) accent else GC.TextSoft, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) accent else GC.Text, fontSize = 17.sp)
    }
}

@Composable
private fun SlideToOpen(onOpen: () -> Unit) {
    val accent = LocalAccent.current
    var trackPx by remember { mutableStateOf(0f) }
    var offsetPx by remember { mutableStateOf(0f) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GC.SurfaceHi)
            .onSizeChanged { trackPx = it.width.toFloat() },
        contentAlignment = Alignment.Center
    ) {
        Text("Slide to Open", style = MaterialTheme.typography.titleMedium, color = GC.TextDim, fontSize = 19.sp)
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .padding(5.dp)
                .width(104.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(accent)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        offsetPx = (offsetPx + delta).coerceIn(0f, (trackPx - 120f).coerceAtLeast(0f))
                    },
                    onDragStopped = {
                        if (offsetPx > (trackPx - 120f) * 0.65f) onOpen()
                        offsetPx = 0f
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.FastForward, contentDescription = "Open", tint = Color.White, modifier = Modifier.size(26.dp))
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
            IconChip(Icons.Filled.Apps, size = 50)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("App Selector", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 22.sp)
                Text("${added.size} Added", style = MaterialTheme.typography.labelMedium, color = LocalAccent.current, fontSize = 14.sp)
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
            Icon(Icons.Filled.Search, contentDescription = null, tint = GC.TextDim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search Apps", color = GC.TextDim, fontSize = 15.sp)
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = GC.Text, fontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            FilterChip("All", filter == AppFilter.ALL) { filter = AppFilter.ALL }
            Spacer(Modifier.width(8.dp))
            FilterChip("Games", filter == AppFilter.GAMES) { filter = AppFilter.GAMES }
            Spacer(Modifier.width(8.dp))
            FilterChip("Apps", filter == AppFilter.APPS) { filter = AppFilter.APPS }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(1.dp).height(22.dp).background(GC.Stroke))
            Spacer(Modifier.width(10.dp))
            FilterChip("Added", filter == AppFilter.ADDED) { filter = AppFilter.ADDED }
            Spacer(Modifier.width(8.dp))
            FilterChip("Not Added", filter == AppFilter.NOT_ADDED) { filter = AppFilter.NOT_ADDED }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.heightIn(max = 380.dp)
        ) {
            items(shown, key = { it.pkg }) { app ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GC.SurfaceHi)
                        .padding(12.dp)
                ) {
                    Image(
                            painter = androidx.compose.ui.graphics.painter.BitmapPainter(app.icon),
                        contentDescription = null,
                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.name, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Play time: ${playMinutes(app.pkg)}m", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
                    }
                    Toggle(on = added.contains(app.pkg)) { onToggle(app.pkg, it) }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent else GC.SurfaceHi)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) Color.White else GC.TextSoft, fontSize = 14.sp)
    }
}

/* ------------------------------------------------------------------ */
/* Permission sheets — live status + always-available app-details path  */
/* ------------------------------------------------------------------ */
enum class PermKind { NOTIFICATION, OVERLAY, WRITE_SETTINGS, USAGE, SHIZUKU }

data class PermConfig(
    val kind: PermKind,
    val icon: ImageVector,
    val tag: String,
    val title: String,
    val desc: String,
    val checks: List<String>,
    val info: String? = null
)

val PERM_CONFIGS = listOf(
    PermConfig(
        PermKind.NOTIFICATION, Icons.Filled.Notifications, "REQUIRED · STATUS UPDATES",
        "Notification Permission",
        "Required to display active service notifications and real-time connection progress.",
        listOf("Shows foreground service status while active", "Provides interactive wireless pairing notifications")
    ),
    PermConfig(
        PermKind.OVERLAY, Icons.Filled.Apps, "REQUIRED · FLOATING WIDGET",
        "Display Over Other Apps",
        "Allows the app to display a compact status indicator and quick-access panel while using other applications.",
        listOf("Displays the floating quick-settings panel when enabled", "Shows real-time system metrics on screen")
    ),
    PermConfig(
        PermKind.WRITE_SETTINGS, Icons.Filled.Tune, "REQUIRED · SYSTEM PREFERENCES",
        "Modify System Settings",
        "Required to adjust display and system preferences according to your saved profiles.",
        listOf("Manages screen brightness and display parameters", "Restores default device settings automatically when finished")
    ),
    PermConfig(
        PermKind.USAGE, Icons.Filled.PieChart, "REQUIRED · APP DETECTION",
        "Usage Data Access",
        "Required to identify the active foreground application and record session duration for your selected apps.",
        listOf("Detects when prioritized applications are launched", "Tracks session duration & applies saved preferences"),
        info = "If the toggle is disabled on Android 13+, open App Info ▸ top-right menu ▸ Allow restricted settings."
    ),
    PermConfig(
        PermKind.SHIZUKU, Icons.Filled.Shield, "OPTIONAL · ADVANCED SERVICE",
        "Shizuku Service Access",
        "Allows the app to connect with the active Shizuku service on your device for system-level configuration management.",
        listOf("Connects to the privileged service automatically", "Enables advanced device & display configuration options")
    )
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
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GC.SurfaceHi)
                .padding(15.dp)
        ) {
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
            BigButton("Later", onLater, Modifier.weight(1f), primary = false)
            if (granted) {
                BigButton("Continue", onLater, Modifier.weight(1f))
            } else {
                BigButton("Allow", onAllow, Modifier.weight(1f))
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Settings sheet — Language + Theme Color                              */
/* ------------------------------------------------------------------ */
@Composable
fun SettingsSheet(
    accentIndex: Int,
    onAccent: (Int) -> Unit,
    onLanguage: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = LocalAccent.current
    SheetFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(Icons.Filled.Settings, size = 50)
            Spacer(Modifier.width(12.dp))
            Text("Settings", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 25.sp)
            Spacer(Modifier.weight(1f))
            CircleIconButton(Icons.Filled.Close, onClick = onDismiss)
        }
        Spacer(Modifier.height(14.dp))
        Card2 {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Language", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp)
                    Spacer(Modifier.height(3.dp))
                    Text("Choose your preferred language", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 14.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GC.tagBgFor(com.gamershub.theme.LocalAccentIdx.current))
                        .clickable(onClick = onLanguage)
                        .padding(horizontal = 15.dp, vertical = 10.dp)
                ) {
                    Text("System Default", style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(">", style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card2 {
            Text("Theme Color", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp)
            Spacer(Modifier.height(3.dp))
            Text("Customize app accent color", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeColorTile("Orange", GC.Orange, accentIndex == 0, Modifier.weight(1f)) { onAccent(0) }
                ThemeColorTile("Ocean Blue", GC.OceanBlue, accentIndex == 1, Modifier.weight(1f)) { onAccent(1) }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeColorTile("Neon Tosca", GC.NeonTosca, accentIndex == 2, Modifier.weight(1f)) { onAccent(2) }
                ThemeColorTile("Crimson Red", GC.CrimsonRed, accentIndex == 3, Modifier.weight(1f)) { onAccent(3) }
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
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun ThemeColorTile(name: String, color: Color, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) GC.BrownPill else GC.Surface)
            .then(if (selected) Modifier.border(1.5.dp, color, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(13.dp)
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(11.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, color = if (selected) color else GC.TextSoft, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/* ------------------------------------------------------------------ */
/* Display Tuning — navigation mode, resolution, smallest width        */
/* ------------------------------------------------------------------ */

@Composable
fun DisplayTuningSheet(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val accent = LocalAccent.current
    val dm = context.resources.displayMetrics
    val cfg = context.resources.configuration
    var navMode by remember {
        mutableStateOf(
            try {
                android.provider.Settings.Secure.getInt(context.contentResolver, "navigation_mode")
            } catch (_: Throwable) { 2 }
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

        // Navigation Mode
        TuningCard(icon = Icons.Filled.Language, title = "Navigation Mode", desc = "Change how you navigate your device") {
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
                                else {
                                    android.widget.Toast.makeText(context, "Requires Activation — see Activation Center", android.widget.Toast.LENGTH_SHORT).show()
                                }
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

        // Resolution Changer
        val physW = dm.widthPixels
        val physH = dm.heightPixels
        TuningCard(icon = Icons.Filled.Apps, title = "Resolution Changer", desc = "Change screen resolution (Width x Height)") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("Res-Ratio: 1.00")
                StatPill("W-Scale: 1.00")
                StatPill("H-Scale: 1.00")
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ValueBox("$physW", "W", Modifier.weight(1f))
                Text("X", style = MaterialTheme.typography.titleMedium, color = GC.TextDim, fontSize = 18.sp)
                ValueBox("$physH", "H", Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Text("Width", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 11.sp)
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

        // Smallest Width Changer
        TuningCard(icon = Icons.Filled.Apps, title = "Smallest Width Changer", desc = "Override screen DPI & smallest width directly") {
            StatPill("DPI-Proportion: ${String.format(Locale.US, "%.2f", dm.densityDpi / 160f * 100f / 90f)}%")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ValueBox("${cfg.smallestScreenWidthDp}", "dp", Modifier.weight(1f))
                Text("=", style = MaterialTheme.typography.titleMedium, color = GC.TextDim, fontSize = 18.sp)
                ValueBox("${dm.densityDpi}", "ppi", Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Text("Smallest Width", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 11.sp)
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
private fun TuningCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GC.SurfaceHi)
            .padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(icon, size = 42)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                Text(desc, style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 12.5.sp, lineHeight = 17.sp)
            }
        }
        Spacer(Modifier.height(11.dp))
        content()
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

/* ------------------------------------------------------------------ */
/* Activation Center — Shizuku + pairing                              */
/* ------------------------------------------------------------------ */

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
    var logOpen by remember { mutableStateOf(false) }
    SheetFrame(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Activation Center", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 24.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(GC.SurfaceHi).clickable(onClick = onDismiss).padding(11.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = GC.Text, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            com.gamershub.ui.components.GaugeRing(pct = envLevel * 100 / 3, big = "$envLevel", small = "/ 3", diameter = 128)
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
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (quickStart) accent else Color.Transparent)
                    .clickable { quickStart = true }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("Quick Start", style = MaterialTheme.typography.labelLarge, color = if (quickStart) Color.White else GC.TextSoft, fontSize = 15.sp) }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (!quickStart) accent else Color.Transparent)
                    .clickable { quickStart = false }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("Manual Pairing", style = MaterialTheme.typography.labelLarge, color = if (!quickStart) Color.White else GC.TextSoft, fontSize = 15.sp) }
        }
        Spacer(Modifier.height(12.dp))

        if (quickStart) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(GC.SurfaceHi)
                    .padding(15.dp)
            ) {
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
                        Text(
                            if (shizukuActive) "Activated" else "Activate",
                            style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 13.sp
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { logOpen = !logOpen }
                        .padding(vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.FastForward, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shizuku Execution Log", style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(if (logOpen) "⌃" else "⌄", style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 14.sp)
                }
                if (logOpen) {
                    Spacer(Modifier.height(6.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(GC.Bg)
                            .padding(12.dp)
                    ) {
                        listOf(
                            "$ shizuku: binder alive",
                            "$ settings put system show_touches …",
                            "$ settings put system pointer_location …",
                            "$ settings put system peak_refresh_rate …"
                        ).forEach {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 11.5.sp, lineHeight = 17.sp)
                        }
                    }
                }
            }
        } else {
            // ---- TCP/IP Mode ----
            val adbTcp = try {
                android.provider.Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled") == 1
            } catch (_: Throwable) { false }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(GC.SurfaceHi)
                    .padding(15.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(Icons.Filled.Dns, size = 44)
                    Spacer(Modifier.width(11.dp))
                    Text("TCP/IP Mode", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(9.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .padding(horizontal = 11.dp, vertical = 7.dp)
                ) {
                    if (adbTcp) Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (adbTcp) "TCP/IP active on port 5555" else "TCP/IP not active",
                        style = MaterialTheme.typography.labelMedium, color = accent, fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    "ADB TCP/IP looks active on this device, but this app hasn't paired with it yet — it was likely turned on another way. Pair once below to trust this device.",
                    style = MaterialTheme.typography.bodyMedium, color = GC.TextDim, fontSize = 12.5.sp, lineHeight = 18.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            // ---- Wireless Debugging ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(GC.SurfaceHi)
                    .padding(15.dp)
            ) {
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
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).background(accent.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) { Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = accent, fontSize = 12.sp) }
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
            // ---- ADB via OTG ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(GC.SurfaceHi)
                    .padding(15.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(Icons.Filled.Dns, size = 44)
                    Spacer(Modifier.width(11.dp))
                    Text("ADB via OTG", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp)
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(accent)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("This device = Computer", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 13.sp) }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x14FFFFFF))
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("This device = Target", style = MaterialTheme.typography.labelLarge, color = GC.TextSoft, fontSize = 13.sp) }
                }
                Spacer(Modifier.height(10.dp))
                listOf(
                    "Enable USB Debugging on the Target device (Settings > Developer options > USB debugging)",
                    "Plug the OTG adapter into THIS device, then connect the Target via USB cable",
                    "Tap Scan — approve the USB permission dialog on THIS device",
                    "Tap Activate — approve the 'Allow USB debugging?' dialog on the Target"
                ).forEachIndexed { i, step ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 5.dp)) {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).background(accent.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) { Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = accent, fontSize = 12.sp) }
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
