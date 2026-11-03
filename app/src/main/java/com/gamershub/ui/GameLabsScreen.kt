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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
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
import com.gamershub.ui.components.IconChip
import com.gamershub.ui.components.SectionLabel
import com.gamershub.ui.components.StatusPill
import com.gamershub.ui.components.Toggle

enum class LabsTab { ALL, VISUAL, CONFIGS }

/** Game-Labs — feature toggles under All / Visual / Configs. */
@Composable
fun GameLabsScreen(
    activeCount: Int,
    toggleState: (String) -> Boolean,
    onToggle: (String, Boolean) -> Unit
) {
    var tab by remember { mutableStateOf(LabsTab.ALL) }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(top = 18.dp, bottom = 130.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Game-Labs", style = MaterialTheme.typography.displaySmall, color = GC.Text, fontSize = 38.sp)
            Spacer(Modifier.weight(1f))
            StatusPill("LV.${if (activeCount >= 2) 1 else 0}")
            Spacer(Modifier.width(10.dp))
            StatusPill(if (activeCount > 0) "Active" else "Inactive", orange = activeCount > 0)
        }

        Spacer(Modifier.height(18.dp))

        Row {
            LabsChip("All", tab == LabsTab.ALL) { tab = LabsTab.ALL }
            Spacer(Modifier.width(12.dp))
            LabsChip("Visual", tab == LabsTab.VISUAL) { tab = LabsTab.VISUAL }
            Spacer(Modifier.width(12.dp))
            LabsChip("Configs", tab == LabsTab.CONFIGS) { tab = LabsTab.CONFIGS }
        }

        SectionLabel("Feature Settings")

        val features = when (tab) {
            LabsTab.ALL -> listOf(
                Feature("dns", Icons.Filled.Dns, "DNS & Latency Tuner", "Keep connection warm & reduce latency spikes"),
                Feature("session", Icons.Filled.GraphicEq, "Session Monitor", "Track device performance while you play")
            )
            LabsTab.VISUAL -> listOf(
                Feature("quality", Icons.Filled.Palette, "3D Quality Pipeline", "Boost shadows & texture detail in games"),
                Feature("clarity", Icons.Filled.Tune, "Color & Clarity", "Vivid colors and sharpened frames")
            )
            LabsTab.CONFIGS -> listOf(
                Feature("dns", Icons.Filled.Dns, "DNS & Latency Tuner", "Keep connection warm & reduce latency spikes"),
                Feature("touch", Icons.Filled.Shield, "Touch Profile", "Tune touch response for fast taps")
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(GC.Surface)
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            features.forEachIndexed { i, f ->
                if (i > 0) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(GC.Stroke))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 18.dp)) {
                    IconChip(f.icon, size = 48)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.title, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 20.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            f.desc,
                            style = MaterialTheme.typography.labelMedium,
                            color = GC.TextDim,
                            fontSize = 15.sp,
                            lineHeight = 21.sp
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Toggle(on = toggleState(f.key)) { onToggle(f.key, it) }
                }
            }
        }
    }
}

private data class Feature(val key: String, val icon: ImageVector, val title: String, val desc: String)

@Composable
private fun LabsChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) GC.Orange else GC.SurfaceHi)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else GC.TextSoft,
            fontSize = 16.sp
        )
    }
}
