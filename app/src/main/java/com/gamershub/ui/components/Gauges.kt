package com.gamershub.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gamershub.ui.theme.LocalGlass

/**
 * Animated radial gauge (FPS / temp / CPU / RAM). Value animates with a spring
 * so the needle glides instead of jumping.
 */
@Composable
fun RadialGauge(
    label: String,
    value: Float,
    maxValue: Float,
    unit: String,
    modifier: Modifier = Modifier,
    size: Dp = 118.dp,
    color: Color? = null,
    subtitle: String? = null
) {
    val g = LocalGlass.current
    val safeValue = value.coerceIn(0f, maxValue)
    val animValue by animateFloatAsState(
        targetValue = safeValue,
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow),
        label = "gauge-$label"
    )
    val track = g.stroke.copy(alpha = 0.35f)
    val arcColor = color ?: g.accent
    val displayValue = if (value < 0) "--" else "${value.toInt()}"

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 9.dp.toPx()
            val inset = stroke * 1.6f
            val arcSize = androidx.compose.ui.geometry.Size(
                this.size.width - inset * 2, this.size.height - inset * 2
            )
            val topLeft = Offset(inset, inset)
            // track: 240° arc starting at 150°
            drawArc(
                color = track, startAngle = 150f, sweepAngle = 240f,
                useCenter = false, topLeft = topLeft, size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            val sweep = 240f * (animValue / maxValue)
            if (sweep > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(g.accent, arcColor, g.accent2)
                    ),
                    startAngle = 150f, sweepAngle = sweep,
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Text(
                displayValue,
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                color = if (value < 0) g.textDim else g.text
            )
            androidx.compose.material3.Text(
                unit, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = g.textDim
            )
        }
        androidx.compose.material3.Text(
            label.uppercase(),
            modifier = Modifier.align(Alignment.BottomCenter),
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = g.accent
        )
        if (subtitle != null) {
            androidx.compose.material3.Text(
                subtitle,
                modifier = Modifier.align(Alignment.TopCenter),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = g.textDim
            )
        }
    }
}
