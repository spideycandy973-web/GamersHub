package com.gamershub.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.theme.GC

/** Rounded-square app-style toggle with a squircle knob (off: grey · on: orange). */
@Composable
fun Toggle(on: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    val track by animateColorAsState(
        if (on) GC.Orange else Color(0xFF2B2420), tween(180), label = "track"
    )
    val knob by animateColorAsState(
        if (on) Color.White else Color(0xFF7E7A76), tween(180), label = "knob"
    )
    val shift by animateDpAsState(if (on) 26.dp else 3.dp, tween(180), label = "shift")
    Box(
        modifier
            .size(width = 54.dp, height = 31.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(track)
            .clickable { onCheckedChange(!on) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .padding(start = shift)
                .size(25.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(knob)
        )
    }
}

/** Small orange-on-brown tag pill: "● REQUIRED · APP DETECTION". */
@Composable
fun TagPill(text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(GC.TagBg)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(GC.Orange))
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = GC.Orange, fontSize = 12.sp)
    }
}

/** Dark brown rounded-square icon chip (the reference's icon container). */
@Composable
fun IconChip(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Int = 52,
    tint: Color = GC.Orange,
    bg: Color = GC.BrownPill
) {
    Box(
        modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.30f).dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(vertical = 10.dp),
        style = MaterialTheme.typography.titleMedium,
        color = GC.TextSoft,
        fontSize = 19.sp
    )
}

/** Big rounded action button — orange primary or dark secondary. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    leadingIcon: ImageVector? = null,
    textColor: Color? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = if (pressed) 0.97f else 1f
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(if (primary) GC.Orange else GC.SurfaceHi)
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(vertical = 17.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon, contentDescription = null,
                    tint = textColor ?: if (primary) Color.White else GC.Orange,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 18.sp,
                color = textColor ?: if (primary) Color.White else GC.Text
            )
        }
    }
}

/** Status pill (e.g. "● Not Activated", "Good", "Inactive", "LV.0"). */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, orange: Boolean = true) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (orange) GC.TagBg else GC.SurfaceHi)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (orange) GC.Orange else GC.TextDim))
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 14.sp,
            color = if (orange) GC.Orange else GC.TextDim
        )
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(GC.SurfaceHi)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = GC.TextSoft, modifier = Modifier.size(22.dp))
    }
}

/** Circular progress gauge (CPU speed card). */
@Composable
fun GaugeRing(pct: Int, big: String, small: String, modifier: Modifier = Modifier) {
    Box(modifier.size(190.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(190.dp)) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Color(0xFF2E2119),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = GC.Orange,
                startAngle = -90f,
                sweepAngle = 360f * pct.coerceIn(0, 100) / 100f,
                useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(big, style = MaterialTheme.typography.headlineMedium, color = GC.Text, fontSize = 32.sp)
            Text(small, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 14.sp)
        }
    }
}

/** Dashed segmented bar (RAM / network style). */
@Composable
fun SegmentedBar(pct: Int, segments: Int, modifier: Modifier = Modifier, height: Int = 10) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val filled = (segments * pct.coerceIn(0, 100)) / 100
        repeat(segments) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(height.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (i < filled) GC.Orange else Color(0xFF2E2119))
            )
        }
    }
}

/** Solid rounded bar (storage style). */
@Composable
fun SolidBar(pct: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF2E2119))
    ) {
        Box(
            Modifier
                .fillMaxWidth(pct.coerceIn(0, 100) / 100f)
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(GC.Orange)
        )
    }
}

@Composable
fun Grabber(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(vertical = 10.dp)
            .width(46.dp)
            .height(5.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF4A4340))
    )
}

/**
 * Original angular hero emblem (our own geometric mark — a cut hexagon
 * ring with an inner lightning bolt).
 */
@Composable
fun HeroMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val ring = Path().apply {
            moveTo(w * 0.55f, h * 0.06f)
            lineTo(w * 0.97f, h * 0.32f)
            lineTo(w * 0.84f, h * 0.90f)
            lineTo(w * 0.26f, h * 0.94f)
            lineTo(w * 0.05f, h * 0.42f)
            close()
        }
        drawPath(
            ring, GC.Beige.copy(alpha = 0.40f),
            style = Stroke(width = h * 0.055f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        )
        val bolt = Path().apply {
            moveTo(w * 0.58f, h * 0.18f)
            lineTo(w * 0.34f, h * 0.55f)
            lineTo(w * 0.50f, h * 0.55f)
            lineTo(w * 0.40f, h * 0.82f)
            lineTo(w * 0.70f, h * 0.44f)
            lineTo(w * 0.53f, h * 0.44f)
            close()
        }
        drawPath(bolt, GC.Beige.copy(alpha = 0.55f))
    }
}
