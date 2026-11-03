package com.gamershub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gamershub.ui.theme.LocalGlass

/**
 * Before → after video preview for a visual option.
 *
 * The clip runs the SAME filter math that the option applies to your display
 * on real test footage: 0:00–0:03 BEFORE, 0:03–0:06 AFTER. Honest by design —
 * no staged screenshots, the effect itself is what you see.
 */
@Composable
fun EffectPreview(rawRes: Int, label: String) {
    val g = LocalGlass.current
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        GlassChip(if (open) "Hide preview" else "▶ Preview: $label", open) { open = !open }
        AnimatedVisibility(open) {
            Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            android.widget.VideoView(ctx).apply {
                                setVideoPath("android.resource://" + ctx.packageName + "/" + rawRes)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "0:00–0:03 BEFORE  ·  0:03–0:06 AFTER — rendered with the same " +
                            "filter this option applies, on test footage. On your display " +
                            "the effect runs live.",
                    style = MaterialTheme.typography.labelSmall,
                    color = g.textDim,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}
