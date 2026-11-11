package com.gamershub.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Brush
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
import com.gamershub.theme.accentNow
import com.gamershub.theme.accentPairNow
import com.gamershub.theme.GC
import com.gamershub.theme.RomKit
import com.gamershub.theme.LocalAccent

/**
 * Animated icon — every icon in the app gently breathes (scale + glow),
 * so the whole UI feels alive.
 */
@Composable
fun AIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = androidx.compose.material3.LocalContentColor.current
) {
    val transition = rememberInfiniteTransition(label = "aicon")
    val breathe by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    val glow by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    androidx.compose.material3.Icon(
        imageVector,
        contentDescription = contentDescription,
        tint = tint.copy(alpha = tint.alpha * glow),
        modifier = modifier.graphicsLayer { scaleX = breathe; scaleY = breathe }
    )
}

/**
 * Animated squircle toggle. The knob slides with a spring, the track
 * cross-fades — driven purely by [on], so callers must pass state that
 * actually changes (see RootShell/GameLabs state maps).
 */
@Composable
fun Toggle(on: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    val track by animateColorAsState(
        if (on) accent else RomKit.Panel2, tween(RomKit.Micro), label = "track"
    )
    val knob by animateColorAsState(
        if (on) RomKit.OnEmber else RomKit.TextLo, tween(RomKit.Micro), label = "knob"
    )
    val shift by animateDpAsState(
        if (on) 26.dp else 3.dp, tween(RomKit.Micro), label = "shift"
    )
    Box(
        modifier
            .size(width = 54.dp, height = 31.dp)
            .clip(RomKit.RPill)
            .background(track)
            .border(1.dp, if (on) accent.copy(alpha = 0.55f) else RomKit.Stroke, RomKit.RPill)
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

/** Status chip: full radius, 1 dp border at 55%, monospace label in its own colour. */
@Composable
fun TagPill(text: String, modifier: Modifier = Modifier) {
    val accent = accentNow()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RomKit.RPill)
            .border(1.dp, accent.copy(alpha = 0.55f), RomKit.RPill)
            .background(accent.copy(alpha = 0.16f), RomKit.RPill)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(6.dp))
        Text(
            text.uppercase(),
            style = RomKit.Label.copy(fontSize = 11.sp, letterSpacing = 0.6.sp),
            color = accent
        )
    }
}

/** Icon tile: PANEL2 square, hairline, accent glyph. */
@Composable
fun IconChip(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Int = 52,
    tint: Color? = null,
    bg: Color? = null
) {
    val accent = accentNow()
    Box(
        modifier
            .size(size.dp)
            .clip(RomKit.R12)
            .background(bg ?: RomKit.Panel2)
            .border(1.dp, RomKit.Stroke, RomKit.R12),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, contentDescription = null, tint = tint ?: accent,
            modifier = Modifier.size((size * 0.50f).dp)
        )
    }
}

/** Section label: monospace 10 sp, +1 tracking, uppercase, TEXT-LO. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(top = 14.dp, bottom = 6.dp),
        style = RomKit.Label,
        color = RomKit.TextLo
    )
}

/** The one primary action on a screen: ember gradient, black 800 label, 56 dp. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    leadingIcon: ImageVector? = null,
    textColor: Color? = null
) {
    val accent = accentNow()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        if (pressed) 0.86f else 1f, tween(RomKit.Micro), label = "press"
    )
    Box(
        modifier
            .clip(RomKit.R16)
            .then(
                if (primary) Modifier.background(RomKit.emberBrush())
                else Modifier
                    .background(Color.Transparent)
                    .border(1.dp, accent.copy(alpha = 0.40f), RomKit.R16)
            )
            .clickable(interaction, indication = null, onClick = onClick)
            .height(56.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer { this.alpha = alpha }
        ) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon, contentDescription = null,
                    tint = textColor ?: if (primary) RomKit.OnEmber else accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(9.dp))
            }
            Text(
                text.uppercase(),
                style = RomKit.Display.copy(fontSize = 16.sp, letterSpacing = 0.3.sp),
                color = textColor ?: if (primary) RomKit.OnEmber else accent
            )
        }
    }
}

/** Status pill (e.g. "● Not Activated", "Good", "Pro"). */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, orange: Boolean = true) {
    val accent = accentNow()
    val color = if (orange) accent else RomKit.TextLo
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RomKit.RPill)
            .border(1.dp, color.copy(alpha = 0.55f), RomKit.RPill)
            .background(if (orange) color.copy(alpha = 0.16f) else Color.Transparent, RomKit.RPill)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            text.uppercase(),
            style = RomKit.Label.copy(fontSize = 11.sp, letterSpacing = 0.6.sp),
            color = color
        )
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(RomKit.Panel2)
            .border(1.dp, RomKit.Stroke, CircleShape)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = RomKit.TextHi, modifier = Modifier.size(20.dp))
    }
}

/** Circular progress gauge — hairline track, accent arc, monospace numbers. */
@Composable
fun GaugeRing(pct: Int, big: String, small: String, modifier: Modifier = Modifier, diameter: Int = 150) {
    val accent = accentNow()
    val bigSp = (diameter / 5).coerceIn(13, 28)
    val smallSp = (diameter / 12).coerceIn(9, 12)
    val animatedPct by animateFloatAsState(pct.coerceIn(0, 100).toFloat(), label = "gauge")
    Box(modifier.size(diameter.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter.dp)) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = RomKit.Panel2, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = accent, startAngle = -90f,
                sweepAngle = 360f * animatedPct / 100f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                big,
                style = RomKit.Counter.copy(fontSize = bigSp.sp),
                color = RomKit.TextHi
            )
            Text(
                small.uppercase(),
                style = RomKit.Label.copy(fontSize = smallSp.sp),
                color = RomKit.TextLo
            )
        }
    }
}

@Composable
fun SegmentedBar(pct: Int, segments: Int, modifier: Modifier = Modifier, height: Int = 8) {
    val accent = accentNow()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val filled = (segments * pct.coerceIn(0, 100)) / 100
        repeat(segments) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < filled) accent else RomKit.Panel2)
            )
        }
    }
}

@Composable
fun SolidBar(pct: Int, modifier: Modifier = Modifier) {
    val accent = accentNow()
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(RomKit.Panel2)
    ) {
        Box(
            Modifier
                .fillMaxWidth(pct.coerceIn(0, 100) / 100f)
                .height(6.dp)
                .clip(RoundedCornerShape(2.dp))
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
            .clip(RomKit.RPill)
            .background(RomKit.Stroke)
    )
}

/**
 * GamersHub mark — restored to the v6.8.2 hero: a custom GH ligature drawn as strokes,
 * where the G's spur and the H's crossbar are one continuous bar.
 */
@Composable
fun HeroMark(modifier: Modifier = Modifier) {
    val accent = accentNow()
    val accent2 = accentPairNow()
    Canvas(modifier) {
        val k = minOf(size.width, size.height) / 1024f
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        val stroke = Stroke(width = 100f * k, cap = StrokeCap.Round)
        val brush = Brush.linearGradient(
            colors = listOf(accent, accent2),
            start = p(74f, 238f),
            end = p(956f, 786f)
        )
        drawArc(
            brush = brush,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = p(348f - 174f, 512f - 174f),
            size = Size(348f * k, 348f * k),
            style = stroke
        )
        drawLine(brush, p(622f, 338f), p(622f, 686f), strokeWidth = 100f * k, cap = StrokeCap.Round)
        drawLine(brush, p(884f, 338f), p(884f, 686f), strokeWidth = 100f * k, cap = StrokeCap.Round)
        drawLine(brush, p(384f, 512f), p(884f, 512f), strokeWidth = 100f * k, cap = StrokeCap.Round)
    }
}

/** Panel card: 18 dp radius, PANEL fill, 1 dp hairline, 16 dp padding. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RomKit.R18)
            .background(RomKit.Panel)
            .border(1.dp, RomKit.Stroke, RomKit.R18)
            .padding(RomKit.PanelPadding),
        content = content
    )
}
