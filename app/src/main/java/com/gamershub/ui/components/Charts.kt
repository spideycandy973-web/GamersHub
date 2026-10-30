package com.gamershub.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.gamershub.ui.theme.LocalGlass

/** Smooth area line chart for the analytics dashboards. */
@Composable
fun LineChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    minY: Float? = null,
    maxY: Float? = null,
    color: Color? = null,
    animated: Boolean = true
) {
    val g = LocalGlass.current
    val lineColor = color ?: g.accent
    val reveal by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(700),
        label = "chart-reveal"
    )

    Canvas(
        modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        if (points.size < 2) return@Canvas
        val lo = minY ?: (points.minOrNull() ?: 0f)
        val hi = maxY ?: ((points.maxOrNull() ?: 1f).coerceAtLeast(lo + 1f))
        val range = (hi - lo).takeIf { it > 0 } ?: 1f

        val stepX = size.width / (points.size - 1)
        fun y(v: Float) = size.height - ((v - lo) / range) * size.height

        // grid
        repeat(4) { i ->
            val gy = size.height * (i + 1) / 5f
            drawLine(
                g.stroke.copy(alpha = 0.25f),
                Offset(0f, gy), Offset(size.width, gy), 1f
            )
        }

        val n = if (animated) (points.size * reveal).toInt().coerceAtLeast(2) else points.size
        val path = Path()
        val fill = Path()
        for (i in 0 until n) {
            val x = i * stepX
            val yy = y(points[i])
            if (i == 0) {
                path.moveTo(x, yy)
                fill.moveTo(x, size.height)
                fill.lineTo(x, yy)
            } else {
                // quadratic smoothing
                val prevX = (i - 1) * stepX
                val prevY = y(points[i - 1])
                val cx = (prevX + x) / 2f
                path.quadraticBezierTo(prevX, prevY, cx, (prevY + yy) / 2f)
                fill.quadraticBezierTo(prevX, prevY, cx, (prevY + yy) / 2f)
            }
        }
        val lastX = (n - 1) * stepX
        path.lineTo(lastX, y(points[n - 1]))
        fill.lineTo(lastX, y(points[n - 1]))
        fill.lineTo(lastX, size.height)
        fill.close()

        drawPath(
            fill,
            Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.35f), Color.Transparent)
            )
        )
        drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx()))
    }
}

/** Tiny inline bar for lists (CPU per-core style visuals). */
@Composable
fun MiniBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val g = LocalGlass.current
    val clamped = fraction.coerceIn(0f, 1f)
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        val r = size.height / 2f
        drawRoundRect(g.stroke.copy(alpha = 0.3f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        if (clamped > 0f) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color)),
                size = androidx.compose.ui.geometry.Size(size.width * clamped, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
            )
        }
    }
}
