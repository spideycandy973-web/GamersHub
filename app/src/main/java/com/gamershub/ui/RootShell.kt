package com.gamershub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import com.gamershub.theme.GC

/**
 * App shell: the glowing brand strip on top (live stats + engine switch land
 * here as the rebuild progresses) and the current screen below.
 */
@Composable
fun RootShell() {
    Column(
        Modifier
            .fillMaxSize()
            .background(GC.Bg)
    ) {
        BrandStrip()
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            HomeScreen()
        }
    }
}

@Composable
private fun BrandStrip() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(GC.Bg2)
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            "◆ GAMERSHUB",
            style = MaterialTheme.typography.titleMedium.copy(
                shadow = Shadow(color = GC.Red.copy(alpha = 0.8f), blurRadius = 26f)
            ),
            color = GC.Red
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "GAME CORNER",
            style = MaterialTheme.typography.labelMedium,
            color = GC.TextDim
        )
        Spacer(Modifier.weight(1f))
        Text(
            "REBUILDING…",
            style = MaterialTheme.typography.labelMedium,
            color = GC.Flame
        )
    }
}
