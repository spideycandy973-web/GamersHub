package com.gamershub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.theme.GC
import com.gamershub.theme.LocalAccent
import com.gamershub.ui.components.Card
import com.gamershub.ui.components.IconChip
import com.gamershub.ui.components.SectionLabel
import com.gamershub.ui.components.StatusPill
import com.gamershub.ui.components.Toggle

enum class LabsTab { ALL, VISUAL, CONFIGS }

/**
 * Game-Labs — Touch Settings (Magic Mapper / HW Touch), Display Settings
 * (Preset Manager / Display Tuning) and Feature Settings, all stateful
 * and animated. Matched to the reference screenshots.
 */
@Composable
fun GameLabsScreen(
    envLevel: Int,
    toggleState: (String) -> Boolean,
    onToggle: (String, Boolean) -> Unit,
    onExportPresets: () -> Unit,
    onManagePresets: () -> Unit,
    onTuneDisplay: () -> Unit,
    displayInfo: String
) {
    val accent = LocalAccent.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var tab by remember { mutableStateOf(LabsTab.ALL) }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 120.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Game-Labs", style = MaterialTheme.typography.displaySmall, color = GC.Text, fontSize = 33.sp, modifier = Modifier.weight(1f))
            StatusPill("LV.${envLevel.coerceIn(0, 3)}")
            Spacer(Modifier.width(8.dp))
            StatusPill(if (envLevel >= 2) "Advanced" else if (envLevel >= 1) "Basic" else "Inactive", orange = envLevel >= 2)
        }

        Spacer(Modifier.height(14.dp))

        Row {
            LabsChip("All", tab == LabsTab.ALL) { tab = LabsTab.ALL }
            Spacer(Modifier.width(10.dp))
            LabsChip("Visual", tab == LabsTab.VISUAL) { tab = LabsTab.VISUAL }
            Spacer(Modifier.width(10.dp))
            LabsChip("Configs", tab == LabsTab.CONFIGS) { tab = LabsTab.CONFIGS }
        }

        if (tab != LabsTab.VISUAL) {
            SectionLabel("Touch Settings")
            Card {
                FeatureRow(
                    icon = Icons.Filled.TouchApp,
                    title = "Magic Mapper Listener",
                    desc = "Enable Magic Mapper service",
                    on = toggleState("magic_mapper"),
                    onChange = { onToggle("magic_mapper", it) }
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    SmallActionButton("Show Taps", Modifier.weight(1f), active = toggleState("show_taps")) {
                        val v = !toggleState("show_taps")
                        onToggle("show_taps", v)
                        com.gamershub.feedbackOnWrite(
                            context,
                            com.gamershub.putSystemSetting(context, "show_touches", if (v) 1 else 0)
                        )
                    }
                    SmallActionButton("Show Coordinates", Modifier.weight(1f), active = toggleState("show_coords")) {
                        val v = !toggleState("show_coords")
                        onToggle("show_coords", v)
                        com.gamershub.feedbackOnWrite(
                            context,
                            com.gamershub.putSystemSetting(context, "pointer_location", if (v) 1 else 0)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(GC.Stroke))
                FeatureRow(
                    icon = Icons.Filled.OpenWith,
                    title = "HW Touch Enhancer",
                    desc = "Boost touch response via Hardware Composer",
                    on = toggleState("hw_touch"),
                    onChange = { onToggle("hw_touch", it) }
                )
            }

            SectionLabel("Display Settings")
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(Icons.Filled.Save, size = 44)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Preset Manager", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                        Text("Save or load display configurations.", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    SmallActionButton("Export", Modifier.weight(1f), onClick = onExportPresets)
                    SmallActionButton("Manage", Modifier.weight(1f), primary = true, onClick = onManagePresets)
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(GC.Stroke))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    IconChip(Icons.Filled.Gamepad, size = 44)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Display Tuning", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp)
                        Text(displayInfo, style = MaterialTheme.typography.labelMedium, color = accent, fontSize = 13.sp)
                    }
                    SmallActionButton("Tune", Modifier.width(92.dp), primary = true, onClick = onTuneDisplay)
                }
            }
        }

        if (tab != LabsTab.CONFIGS) {
            SectionLabel("Feature Settings")
            Card {
                FeatureRow(
                    icon = Icons.Filled.Bolt,
                    title = "Game Ready",
                    desc = "System health & stability index",
                    on = toggleState("game_ready"),
                    onChange = { onToggle("game_ready", it) }
                )
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(GC.Stroke))
                FeatureRow(
                    icon = Icons.Filled.Dns,
                    title = "DNS & Latency Tuner",
                    desc = "Keep connection warm & reduce latency spikes",
                    on = toggleState("dns"),
                    onChange = { onToggle("dns", it) }
                )
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(GC.Stroke))
                FeatureRow(
                    icon = Icons.Filled.GraphicEq,
                    title = "Session Monitor",
                    desc = "Track device performance while you play",
                    on = toggleState("session"),
                    onChange = { onToggle("session", it) }
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    desc: String,
    on: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        IconChip(icon, size = 44)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 17.sp)
            Spacer(Modifier.height(2.dp))
            Text(desc, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Toggle(on = on, onCheckedChange = onChange)
    }
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
    val bg by androidx.compose.animation.animateColorAsState(
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

@Composable
private fun LabsChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent else GC.SurfaceHi)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else GC.TextSoft, fontSize = 15.sp)
    }
}
