package com.gamershub.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.theme.GC
import com.gamershub.theme.LocalAccent

/**
 * Animated squircle toggle. The knob slides with a spring, the track
 * cross-fades — driven purely by [on], so callers must pass state that
 * actually changes (see RootShell/GameLabs state maps).
 */
@Composable
fun Toggle(on: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    val track by animateColorAsState(
        if (on) accent else Color(0xFF2B2420), tween(220), label = "track"
    )
    val knob by animateColorAsState(
        if (on) Color.White else Color(0xFF7E7A76), tween(220), label = "knob"
    )
    val shift by animateDpAsState(
        if (on) 26.dp else 3.dp, spring(stiffness = Spring.StiffnessMediumLow), label = "shift"
    )
    Box(
        modifier
            .size(width = 54.dp, height = 31.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(track)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!on) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = shift)
                .size(25.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(knob)
        )
    }
}

/** Small accent tag pill: "● REQUIRED · APP DETECTION". */
@Composable
fun TagPill(text: String, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val idx = com.gamershub.theme.LocalAccentIdx.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(GC.tagBgFor(idx))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = accent, fontSize = 12.sp)
    }
}

/** Dark rounded-square icon chip. */
@Composable
fun IconChip(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Int = 52,
    tint: Color? = null,
    bg: Color? = null
) {
    val accent = LocalAccent.current
    val idx = com.gamershub.theme.LocalAccentIdx.current
    Box(
        modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.30f).dp))
            .background(bg ?: GC.brownFor(idx)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint ?: accent, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 14.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        color = GC.TextSoft,
        fontSize = 18.sp
    )
}

/** Big rounded action button with press animation. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    leadingIcon: ImageVector? = null,
    textColor: Color? = null
) {
    val accent = LocalAccent.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f, spring(Spring.DampingRatioNoBouncy), label = "press"
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(if (primary) accent else GC.SurfaceHi)
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon, contentDescription = null,
                    tint = textColor ?: if (primary) Color.White else accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(9.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 16.sp,
                color = textColor ?: if (primary) Color.White else GC.Text
            )
        }
    }
}

/** Status pill (e.g. "● Not Activated", "Good", "Advanced"). */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, orange: Boolean = true) {
    val accent = LocalAccent.current
    val idx = com.gamershub.theme.LocalAccentIdx.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (orange) GC.tagBgFor(idx) else GC.SurfaceHi)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (orange) accent else GC.TextDim))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 13.sp,
            color = if (orange) accent else GC.TextDim
        )
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(Spring.DampingRatioNoBouncy), label = "cbtn")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(44.dp)
            .clip(CircleShape)
            .background(GC.SurfaceHi)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = GC.TextSoft, modifier = Modifier.size(21.dp))
    }
}

/** Circular progress gauge. */
@Composable
fun GaugeRing(pct: Int, big: String, small: String, modifier: Modifier = Modifier, diameter: Int = 150) {
    val accent = LocalAccent.current
    Box(modifier.size(diameter.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter.dp)) {
            val stroke = 13.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Color(0xFF2E2119), startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = accent, startAngle = -90f,
                sweepAngle = 360f * pct.coerceIn(0, 100) / 100f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(big, style = MaterialTheme.typography.headlineMedium, color = GC.Text, fontSize = 27.sp)
            Text(small, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
        }
    }
}

@Composable
fun SegmentedBar(pct: Int, segments: Int, modifier: Modifier = Modifier, height: Int = 8) {
    val accent = LocalAccent.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        val filled = (segments * pct.coerceIn(0, 100)) / 100
        repeat(segments) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(height.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (i < filled) accent else Color(0xFF2E2119))
            )
        }
    }
}

@Composable
fun SolidBar(pct: Int, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Box(
        modifier
            .fillMaxWidth()
            .height(9.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF2E2119))
    ) {
        Box(
            Modifier
                .fillMaxWidth(pct.coerceIn(0, 100) / 100f)
                .height(9.dp)
                .clip(RoundedCornerShape(50))
                .background(accent)
        )
    }
}

@Composable
fun Grabber(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(vertical = 8.dp)
            .width(44.dp)
            .height(5.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF4A4340))
    )
}

/** Original angular hero emblem. */
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
            style = Stroke(width = h * 0.055f, cap = StrokeCap.Round, join = StrokeJoin.Round)
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

/** Section card used across screens — compact, wrap-friendly. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GC.Surface)
            .padding(15.dp),
        content = content
    )
}
