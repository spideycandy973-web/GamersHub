package com.gamershub.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.theme.GC
import com.gamershub.theme.LocalAccent
import kotlin.math.roundToInt

/** Squircle switch with spring knob + animated track. */
@Composable
fun Toggle(on: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    val track by animateColorAsState(if (on) accent else Color(0xFF4A4441), label = "track")
    val knob by animateDpAsState(if (on) 26.dp else 3.dp, spring(dampingRatio = 0.7f), label = "knob")
    Box(
        modifier
            .width(52.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(track)
            .clickable { onCheckedChange(!on) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = knob)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (on) Color.White else Color(0xFFB9B2AC))
        )
    }
}

/** Standard rounded card with content slot. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GC.Surface)
            .padding(16.dp),
        content = content
    )
}

/** Big pill button. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    leadingIcon: ImageVector? = null,
    textColor: Color = Color.White
) {
    val accent = LocalAccent.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (primary) accent else GC.SurfaceHi)
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = textColor, fontSize = 15.sp)
    }
}

@Composable
fun StatusPill(text: String, orange: Boolean = true) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (orange) GC.TagBg else GC.SurfaceHi)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (orange) accent else GC.TextSoft, fontSize = 12.sp)
    }
}

@Composable
fun TagPill(text: String) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(GC.TagBg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text("● $text", style = MaterialTheme.typography.labelMedium, color = accent, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun IconChip(icon: ImageVector, size: Int = 44, bg: Color = GC.BrownPill) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(size / 3.2f))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(GC.SurfaceHi)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = GC.Text, modifier = Modifier.size(19.dp))
    }
}

/** Circular gauge whose text scales with the ring. */
@Composable
fun GaugeRing(pct: Int, big: String, small: String, modifier: Modifier = Modifier, diameter: Int = 150) {
    val accent = LocalAccent.current
    val bigSp = (diameter / 5).coerceIn(13, 28)
    val smallSp = (diameter / 11).coerceIn(9, 13)
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
            Text(big, style = MaterialTheme.typography.headlineMedium, color = GC.Text, fontSize = bigSp.sp)
            Text(small, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = smallSp.sp)
        }
    }
}

@Composable
fun SegmentedBar(pct: Int, segments: Int = 10, height: Int = 7, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
        repeat(segments) { i ->
            val on = i < (pct * segments / 100f).toInt() + if (pct > 0) 1 else 0
            Box(
                Modifier
                    .weight(1f)
                    .height(height.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (on && i < pct * segments / 100f + 1) accent else accent.copy(alpha = 0.18f))
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
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(accent.copy(alpha = 0.18f))
    ) {
        Box(Modifier.fillMaxWidth(pct.coerceIn(0, 100) / 100f).height(8.dp).background(accent))
    }
}

/** Slide-to-confirm control (Slide to Open / Slide to get ready). */
@Composable
fun SlideControl(label: String, icon: ImageVector, onDone: () -> Unit) {
    val accent = LocalAccent.current
    var trackPx by remember { mutableStateOf(0f) }
    var offsetPx by remember { mutableStateOf(0f) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GC.SurfaceHi)
            .onSizeChanged { trackPx = it.width.toFloat() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = GC.TextDim, fontSize = 19.sp)
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .padding(5.dp)
                .width(104.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(accent)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        offsetPx = (offsetPx + delta).coerceIn(0f, (trackPx - 120f).coerceAtLeast(0f))
                    },
                    onDragStopped = {
                        if (offsetPx > (trackPx - 120f) * 0.65f) onDone()
                        offsetPx = 0f
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

/** The angular brand mark (pentagon + bolt), drawn with Canvas. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, tint: Color = Color(0xFFC9B3A4)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, 0f)
            lineTo(w, h * 0.36f)
            lineTo(w * 0.82f, h)
            lineTo(w * 0.18f, h)
            lineTo(0f, h * 0.36f)
            close()
        }
        drawPath(path, tint.copy(alpha = 0.35f), style = Stroke(w * 0.055f))
        val bolt = Path().apply {
            moveTo(w * 0.56f, h * 0.16f)
            lineTo(w * 0.36f, h * 0.55f)
            lineTo(w * 0.50f, h * 0.55f)
            lineTo(w * 0.42f, h * 0.86f)
            lineTo(w * 0.68f, h * 0.44f)
            lineTo(w * 0.52f, h * 0.44f)
            close()
        }
        drawPath(bolt, tint)
    }
}

/** Octagon-ish chip used for nav + angular buttons. */
@Composable
fun CutButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bg: Color = GC.SurfaceHi,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .clip(CutCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomEnd = 2.dp, bottomStart = 12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun CheckMark(checked: Boolean) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (checked) accent else GC.SurfaceHi),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
