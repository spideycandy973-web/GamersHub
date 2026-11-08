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
 * Minimal liquid glass.
 *
 * There is no real backdrop blur available to a normal Android window, so the glass is built
 * the way it actually works on this platform: a translucent vertical fill, a light-catching
 * top edge, a subtle inner specular line and a very low-alpha accent tint. The ambient colour
 * behind it comes from [LiquidBackdrop], so the panels genuinely pick up the background hue.
 */

/** Frosted surface. [tint] adds accent colour, [radiusDp] the corner. */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    accent: Color = Color.Transparent,
    strong: Boolean = false
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (strong) 0.15f else 0.10f),
                Color.White.copy(alpha = if (strong) 0.05f else 0.03f)
            )
        )
    )
    .background(
        Brush.verticalGradient(
            listOf(accent.copy(alpha = if (strong) 0.20f else 0.12f), Color.Transparent)
        )
    )
    .border(
        1.dp,
        Brush.verticalGradient(listOf(GC.GlassEdgeHi, GC.GlassEdgeLo)),
        shape
    )

/** A glass panel with the specular line drawn on top. */
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
                // top specular: a hairline that fades out to the right, like light on glass
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.55f), Color.Transparent)
                    ),
                    start = Offset(size.width * 0.06f, 1f),
                    end = Offset(size.width * 0.62f, 1f),
                    strokeWidth = 2f
                )
            },
        content = content
    )
}

/** Deep ink backdrop with two slow ambient colour fields behind everything. */
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
            val r1 = w.coerceAtLeast(h) * 0.75f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(w * 0.16f, h * 0.06f),
                    radius = r1
                ),
                radius = r1,
                center = Offset(w * 0.16f, h * 0.06f)
            )
            val r2 = w.coerceAtLeast(h) * 0.65f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF4FD8FF).copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(w * 0.92f, h * 0.72f),
                    radius = r2
                ),
                radius = r2,
                center = Offset(w * 0.92f, h * 0.72f)
            )
            val r3 = w * 0.5f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF9A6A).copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(w * 0.55f, h * 0.95f),
                    radius = r3
                ),
                radius = r3,
                center = Offset(w * 0.55f, h * 0.95f)
            )
        }
        content()
    }
}
