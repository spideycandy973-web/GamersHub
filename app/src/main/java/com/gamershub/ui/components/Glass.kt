package com.gamershub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.gamershub.theme.GC
import com.gamershub.theme.LocalAccent

/**
 * Warm liquid glass.
 *
 * Android gives no real backdrop blur to a normal window, so the glass is built the way it
 * actually reads on the platform: smoked translucency over an ember-black backdrop, a lit top
 * edge, a hairline specular line, a warm rim and a low-alpha accent wash that picks up the
 * panel's own colour.
 */

/** Frosted surface. [tint] adds accent colour, [shape] the corner. */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    accent: Color = Color.Transparent,
    strong: Boolean = false
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (strong) 0.11f else 0.07f),
                Color(0.36f, 0.27f, 0.21f, if (strong) 0.10f else 0.06f)
            )
        )
    )
    .background(
        Brush.verticalGradient(
            listOf(accent.copy(alpha = if (strong) 0.17f else 0.10f), Color.Transparent)
        )
    )
    .border(
        1.dp,
        Brush.verticalGradient(listOf(GC.GlassEdgeHi, GC.GlassEdgeLo)),
        shape
    )

/** Glass panel with the specular line drawn on top — use where the surface needs a lift. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    strong: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val accent = LocalAccent.current
    Box(
        modifier
            .liquidGlass(shape = shape, accent = accent, strong = strong)
            .drawWithContent {
                drawContent()
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFFFFE8C6).copy(alpha = 0.55f), Color.Transparent)
                    ),
                    start = Offset(size.width * 0.06f, 1f),
                    end = Offset(size.width * 0.60f, 1f),
                    strokeWidth = 2f
                )
            },
        content = content
    )
}

/** Ember-black backdrop with slow warm colour fields behind everything. */
@Composable
fun LiquidBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val accent = LocalAccent.current
    Box(modifier.background(Brush.verticalGradient(listOf(GC.BgTop, GC.Bg)))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val r1 = w.coerceAtLeast(h) * 0.78f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(w * 0.18f, h * 0.04f),
                    radius = r1
                ),
                radius = r1,
                center = Offset(w * 0.18f, h * 0.04f)
            )
            val r2 = w.coerceAtLeast(h) * 0.70f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFC2160A).copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(w * 0.95f, h * 0.62f),
                    radius = r2
                ),
                radius = r2,
                center = Offset(w * 0.95f, h * 0.62f)
            )
            val r3 = w * 0.55f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF8A3C).copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(w * 0.45f, h * 0.98f),
                    radius = r3
                ),
                radius = r3,
                center = Offset(w * 0.45f, h * 0.98f)
            )
        }
        content()
    }
}
