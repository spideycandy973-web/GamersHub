package com.gamershub.ui.sheets

import com.gamershub.ui.components.AIcon

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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import android.widget.Toast
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.gamershub.theme.accentNow
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
/* Launch mode + slide to open                                        */
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
        // ---- game banner ----
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
        // Negative heights are not a valid measurement in Compose — lift the row
        // with an offset instead so this sheet can never fail to lay out.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 2.dp).offset(y = (-46).dp)
        ) {
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
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    version,
                    style = MaterialTheme.typography.labelLarge, color = accentNow(), fontSize = 13.sp
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Pick Start Mode", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 20.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LaunchModeCard(
                icon = Icons.Filled.Rocket, title = "Tool Shelf", cost = "FREE · panel and boosters",
                selected = gameSpace, onSelect = { gameSpace = true }, modifier = Modifier.weight(1f)
            )
            LaunchModeCard(
                icon = Icons.Filled.Android, title = "Straight In", cost = "FREE · plain start",
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
    cost: String,
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
                AIcon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
        }
        AIcon(icon, contentDescription = null, tint = if (selected) accent else GC.TextSoft, modifier = Modifier.size(34.dp))
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
        Text("Slide to Enter", style = MaterialTheme.typography.titleMedium, color = GC.TextDim, fontSize = 19.sp)
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
            AIcon(Icons.Filled.FastForward, contentDescription = "Enter", tint = Color.White, modifier = Modifier.size(26.dp))
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
                Text("App Picker", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 22.sp)
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
            AIcon(Icons.Filled.Search, contentDescription = null, tint = GC.TextDim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search apps", color = GC.TextDim, fontSize = 15.sp)
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
            FilterChip("Run", filter == AppFilter.GAMES) { filter = AppFilter.GAMES }
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
        "Modify System Preferences",
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
        "Shizuku Helper Access",
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
                        AIcon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
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
                AIcon(Icons.Filled.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Allowed ✓", style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        BigButton("Open App Detail", onOpenDetail, Modifier.fillMaxWidth(), primary = false, leadingIcon = Icons.Filled.Settings, textColor = accent)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Not now", onLater, Modifier.weight(1f), primary = false)
            if (granted) {
                BigButton("Carry on", onLater, Modifier.weight(1f))
            } else {
                BigButton("Let it", onAllow, Modifier.weight(1f))
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
    customColor: androidx.compose.ui.graphics.Color? = null,
    onCustom: (androidx.compose.ui.graphics.Color) -> Unit = {},
    iconIndex: Int,
    onIcon: (Int) -> Unit,
    onLanguage: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = LocalAccent.current
    var hue by remember {
        androidx.compose.runtime.mutableFloatStateOf(
            customColor?.let { colour ->
                val hsv = FloatArray(3)
                android.graphics.Color.RGBToHSV(
                    (colour.red * 255f).toInt().coerceIn(0, 255),
                    (colour.green * 255f).toInt().coerceIn(0, 255),
                    (colour.blue * 255f).toInt().coerceIn(0, 255),
                    hsv
                )
                hsv[0]
            } ?: 6f
        )
    }
    SheetFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(Icons.Filled.Settings, size = 50)
            Spacer(Modifier.width(12.dp))
            Text("Likes", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 25.sp)
            Spacer(Modifier.weight(1f))
            CircleIconButton(Icons.Filled.Close, onClick = onDismiss)
        }
        Spacer(Modifier.height(14.dp))
        Card2 {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Wording", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp)
                    Spacer(Modifier.height(3.dp))
                    Text("Pick your words", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 14.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GC.tagBgFor(com.gamershub.theme.LocalAccentIdx.current))
                        .clickable(onClick = onLanguage)
                        .padding(horizontal = 15.dp, vertical = 10.dp)
                ) {
                    Text("Phone Default", style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(">", style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card2 {
            Text("Colour Look", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp)
            Spacer(Modifier.height(3.dp))
            Text("Pick the app colour", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            // Six shades of red: pick the one that is easiest to read on your screen.
            GC.TONES.chunked(3).forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { tone ->
                        val i = GC.TONES.indexOf(tone)
                        ToneTile(
                            name = tone.name,
                            color = tone.color,
                            selected = accentIndex == i,
                            modifier = Modifier.weight(1f)
                        ) { onAccent(i) }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            // Readability proof: the same colour as a filled chip and as bare text.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent)
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text(
                        "Text on colour",
                        style = MaterialTheme.typography.labelLarge,
                        color = GC.inkOn(accent),
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Text beside it",
                    style = MaterialTheme.typography.labelLarge,
                    color = GC.readable(accent),
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Any shade", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 17.sp)
                    Text(
                        "Slide to tint the whole app and its text",
                        style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp
                    )
                }
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(androidx.compose.ui.graphics.Color.hsv(hue, 0.82f, 1f))
                )
            }
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.Slider(
                value = hue,
                onValueChange = { value ->
                    hue = value
                    onCustom(androidx.compose.ui.graphics.Color.hsv(value, 0.82f, 1f))
                },
                valueRange = 0f..360f,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = androidx.compose.ui.graphics.Color.hsv(hue, 0.82f, 1f),
                    activeTrackColor = androidx.compose.ui.graphics.Color.hsv(hue, 0.82f, 1f),
                    inactiveTrackColor = GC.SurfaceHi
                ),
                modifier = Modifier.fillMaxWidth().height(28.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Card2 {
            Text("Icon Look", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp)
            Spacer(Modifier.height(3.dp))
            Text("Pick the icon on your home screen", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconTile("GH", com.gamershub.R.drawable.ic_pick_gh, iconIndex == 0, Modifier.weight(1f)) { onIcon(0) }
                IconTile("Pad", com.gamershub.R.drawable.ic_pick_pad, iconIndex == 1, Modifier.weight(1f)) { onIcon(1) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconTile("Pace", com.gamershub.R.drawable.ic_pick_speed, iconIndex == 2, Modifier.weight(1f)) { onIcon(2) }
                IconTile("Mono", com.gamershub.R.drawable.ic_pick_mono, iconIndex == 3, Modifier.weight(1f)) { onIcon(3) }
            }
        }
        Spacer(Modifier.height(12.dp))
        DiagnosticsCard()
    }
}

/** Last caught exception — so a crash can be reported instead of guessed at. */
@Composable
private fun DiagnosticsCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var log by remember {
        androidx.compose.runtime.mutableStateOf(
            runCatching {
                java.io.File(context.filesDir, "crash.log").readText().takeLast(600)
            }.getOrDefault("")
        )
    }
    Card2 {
        Text("Health Look", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp)
        Spacer(Modifier.height(3.dp))
        Text(
            if (log.isBlank()) "No crash recorded — every feature is running clean"
            else "Last caught error",
            style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp
        )
        if (log.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                log.trim(),
                style = MaterialTheme.typography.labelSmall,
                color = GC.TextSoft,
                fontSize = 10.sp,
                maxLines = 6
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(
                text = "Grab log",
                onClick = {
                    runCatching {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(
                            android.content.ClipData.newPlainText("GamersHub stop log", log.ifBlank { "no crash" })
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                primary = false
            )
            BigButton(
                text = "Wipe",
                onClick = {
                    runCatching { java.io.File(context.filesDir, "crash.log").delete() }
                    log = ""
                },
                modifier = Modifier.weight(1f),
                primary = false
            )
        }
    }
}

@Composable
private fun IconTile(name: String, res: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val context = androidx.compose.ui.platform.LocalContext.current
    // Plain bitmaps only. Asking Compose to open a launcher mipmap here used to hit
    // the <adaptive-icon> XML on Android 8+ and throw, which closed the app the
    // moment this page opened.
    val art = remember(res) {
        val decoded = runCatching { android.graphics.BitmapFactory.decodeResource(context.resources, res) }
            .getOrNull()
        (decoded ?: android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888))
            .asImageBitmap()
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) GC.BrownPill else GC.Surface)
            .then(if (selected) Modifier.border(1.5.dp, accent, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp)
    ) {
        androidx.compose.foundation.Image(
            bitmap = art,
            contentDescription = name,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(13.dp))
        )
        Spacer(Modifier.height(7.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, color = if (selected) accent else GC.TextSoft, fontSize = 13.sp)
    }
}

@Composable
private fun Card2(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GC.Surface)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun ToneTile(name: String, color: Color, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) GC.BrownPill else GC.Surface)
            .then(if (selected) Modifier.border(1.5.dp, color, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp)
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center
        ) {
            if (selected) AIcon(Icons.Filled.Check, contentDescription = null, tint = GC.inkOn(color), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) GC.readable(color) else GC.TextSoft,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
    var tick by remember { mutableIntStateOf(0) }
    var sizePct by remember { mutableFloatStateOf(100f) }
    var dpi by remember {
        mutableFloatStateOf(
            runCatching { com.gamershub.core.DeviceSpoofer.panel(context).density.toFloat() }
                .getOrDefault(420f)
                .coerceIn(240f, 640f)
        )
    }
    SheetFrame(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(Icons.Filled.Palette, size = 50)
            Spacer(Modifier.width(12.dp))
            Text("Screen Tweak", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 24.sp, modifier = Modifier.weight(1f))
            CircleIconButton(Icons.Filled.Close, onClick = onDismiss)
        }
        Spacer(Modifier.height(14.dp))

        // Navigation Mode
        TuningCard(icon = Icons.Filled.Language, title = "Button Mode", desc = "Change how you move around your phone") {
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
                                    android.widget.Toast.makeText(context, "Needs Power Up — see the Power Up Spot", android.widget.Toast.LENGTH_SHORT).show()
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

        // Screen size — a real `wm size` override, with a one-tap way back.
        val panel = remember(tick) { com.gamershub.core.DeviceSpoofer.panel(context) }
        val overrideSize = remember(tick) { com.gamershub.core.DeviceSpoofer.currentOverrideSize() }
        val overrideDpi = remember(tick) {
            runCatching {
                Regex("Override density:\\s*(\\d+)")
                    .find(com.gamershub.core.shizuku.Shell.exec("wm density") ?: "")
                    ?.groupValues?.getOrNull(1)
            }.getOrNull().orEmpty()
        }
        val targetW = evenNumber(panel.width * (sizePct / 100f).toInt())
        val targetH = evenNumber(panel.height * (sizePct / 100f).toInt())

        TuningCard(icon = Icons.Filled.Apps, title = "Crisp Changer", desc = "Change screen size (Width x Height)") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("Panel ${panel.width}x${panel.height}")
                StatPill(if (overrideSize.isBlank()) "Stock size" else "Now $overrideSize")
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ValueBox("$targetW", "W", Modifier.weight(1f))
                Text("X", style = MaterialTheme.typography.titleMedium, color = GC.TextDim, fontSize = 18.sp)
                ValueBox("$targetH", "H", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            SliderRow("SIZE", sizePct, 50f..100f, "${sizePct.toInt()}%") { sizePct = it }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    BigButton(
                        text = "Okay",
                        onClick = {
                            val ok = com.gamershub.core.shizuku.Shell.ok("wm size ${targetW}x$targetH")
                            Toast.makeText(
                                context,
                                if (ok) "Screen size → ${targetW}x$targetH" else "Needs Power Up to change the size",
                                Toast.LENGTH_SHORT
                            ).show()
                            tick++
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(Modifier.weight(1f)) {
                    BigButton(
                        text = "Put back",
                        onClick = {
                            val ok = com.gamershub.core.shizuku.Shell.ok("wm size reset")
                            Toast.makeText(context, if (ok) "Back to the panel size" else "Needs Power Up", Toast.LENGTH_SHORT).show()
                            tick++
                        },
                        modifier = Modifier.fillMaxWidth(),
                        primary = false
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Density — real `wm density`, so text and layout change together.
        TuningCard(icon = Icons.Filled.Apps, title = "Width Changer", desc = "Set screen DPI and small width by hand") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("Panel ${panel.density} ppi")
                StatPill(if (overrideDpi.isBlank()) "Stock density" else "Now $overrideDpi ppi")
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ValueBox("${(minOf(panel.width, panel.height) * 160f / dpi).toInt()}", "dp", Modifier.weight(1f))
                Text("=", style = MaterialTheme.typography.titleMedium, color = GC.TextDim, fontSize = 18.sp)
                ValueBox("${dpi.toInt()}", "ppi", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            SliderRow("DENSITY", dpi, 240f..640f, "${dpi.toInt()}") { dpi = (it / 10f).toInt() * 10f }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    BigButton(
                        text = "Okay",
                        onClick = {
                            val ok = com.gamershub.core.shizuku.Shell.ok("wm density ${dpi.toInt()}")
                            Toast.makeText(
                                context,
                                if (ok) "Density → ${dpi.toInt()} ppi" else "Needs Power Up to change the density",
                                Toast.LENGTH_SHORT
                            ).show()
                            tick++
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(Modifier.weight(1f)) {
                    BigButton(
                        text = "Put back",
                        onClick = {
                            val ok = com.gamershub.core.shizuku.Shell.ok("wm density reset")
                            Toast.makeText(context, if (ok) "Back to the panel density" else "Needs Power Up", Toast.LENGTH_SHORT).show()
                            tick++
                        },
                        modifier = Modifier.fillMaxWidth(),
                        primary = false
                    )
                }
            }
        }
    }
}

/** Phone screens only render cleanly on even pixel counts. */
private fun evenNumber(value: Int): Int = (value / 2) * 2

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
    var usbNote by remember { mutableStateOf("") }
    var logOpen by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    // Keep the live shell log fresh while it is open.
    LaunchedEffect(logOpen) {
        while (logOpen) {
            kotlinx.coroutines.delay(1200)
            tick++
        }
    }
    SheetFrame(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Power Up Spot", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 24.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(GC.SurfaceHi).clickable(onClick = onDismiss).padding(11.dp)
            ) {
                AIcon(Icons.Filled.Close, contentDescription = "Shut", tint = GC.Text, modifier = Modifier.size(18.dp))
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
                Text("DEEP TIER", style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 13.sp, letterSpacing = 1.2.sp)
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
            ) { Text("Pair by Hand", style = MaterialTheme.typography.labelLarge, color = if (!quickStart) Color.White else GC.TextSoft, fontSize = 15.sp) }
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
                    Text("Shizuku Door", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp, modifier = Modifier.weight(1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (shizukuActive) accent.copy(alpha = 0.2f) else GC.Bg)
                            .clickable(onClick = onOpenShizuku)
                            .padding(horizontal = 13.dp, vertical = 7.dp)
                    ) {
                        if (shizukuActive) {
                            AIcon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
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
                    AIcon(Icons.Filled.FastForward, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shizuku Log", style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 14.sp, modifier = Modifier.weight(1f))
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
                        val lines = remember(tick) {
                            val history = runCatching { com.gamershub.core.shizuku.Shell.recent(8) }.getOrDefault(emptyList())
                            if (history.isEmpty()) {
                                listOf(
                                    "binder: " + com.gamershub.core.shizuku.Shell.statusLabel(),
                                    "error: " + com.gamershub.core.shizuku.Shell.reason()
                                )
                            } else {
                                history.reversed()
                            }
                        }
                        lines.forEach {
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
                    Text("Net Mode", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(9.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .padding(horizontal = 11.dp, vertical = 7.dp)
                ) {
                    if (adbTcp) AIcon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(12.dp))
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
                    Text("Cordless Debug", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp)
                }
                Spacer(Modifier.height(8.dp))
                StatPill(runCatching { "This phone: " + com.gamershub.core.ScreenInfo.wifiIp() }.getOrDefault("ADB Wireless Debugging"))
                Spacer(Modifier.height(10.dp))
                listOf(
                    "Open Wireless Debugging in Developer Options",
                    "Pick 'Pair device with pairing code'",
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
                    ) { Text("Likes", style = MaterialTheme.typography.labelLarge, color = GC.Text, fontSize = 15.sp) }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(accent)
                            .clickable {
                                // Real jump to the system's own wireless-debugging screen.
                                val deep = android.content.Intent("android.settings.WIRELESS_DEBUGGING")
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                val plain = android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                val opened = runCatching { context.startActivity(deep); true }
                                    .getOrElse { runCatching { context.startActivity(plain); true }.getOrDefault(false) }
                                Toast.makeText(
                                    context,
                                    if (opened) "Opened wireless debugging" else "Open Developer options by hand",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Open", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 15.sp) }
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
                    ) { Text("This phone = Brains", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 13.sp) }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x14FFFFFF))
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("This phone = Target", style = MaterialTheme.typography.labelLarge, color = GC.TextSoft, fontSize = 13.sp) }
                }
                Spacer(Modifier.height(10.dp))
                listOf(
                    "Turn on USB Debugging on the Target device (Preferences > Developer options > USB debugging)",
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
                            val found = com.gamershub.core.ScreenInfo.usbDevices(context)
                            usbNote = if (found.isEmpty()) "Nothing on the USB port right now"
                            else found.joinToString(" · ")
                            Toast.makeText(context, usbNote, Toast.LENGTH_LONG).show()
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Check USB port", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 15.sp) }
                if (usbNote.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(usbNote, color = GC.TextDim, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Game Ready                                                          */
/* ------------------------------------------------------------------ */

@Composable
fun GameReadySheet(onDone: () -> Unit, onDismiss: () -> Unit) {
    val accent = LocalAccent.current
    SheetFrame(onDismiss = onDismiss) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            IconChip(Icons.Filled.Bolt, size = 62)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Ready to Play",
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.displaySmall,
            color = GC.Text,
            fontSize = 30.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Get your device ready for your next session. Everything goes back to normal when you're done.",
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            color = GC.TextDim,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
            AIcon(Icons.Filled.Info, contentDescription = null, tint = GC.TextDim, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text("May differ per phone.", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.5.sp)
        }
        Spacer(Modifier.height(16.dp))
        SlideControl(label = "Slide to warm up", icon = Icons.Filled.Autorenew, onDone = onDone)
    }
}

/** Reusable slide-to-confirm control. */
@Composable
fun SlideControl(label: String, icon: ImageVector, onDone: () -> Unit) {
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
        Text(label, style = MaterialTheme.typography.titleMedium, color = GC.TextDim, fontSize = 19.sp)
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
                        if (offsetPx > (trackPx - 120f) * 0.65f) onDone()
                        offsetPx = 0f
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            AIcon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}
