package com.gamershub.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * RomKit — the instrument-panel design system.
 *
 * Tokens only carry values; nothing here knows about features. Palette entries are the
 * single source of colour truth, hairlines replace elevation, and monospace carries every
 * number, unit, status word and label.
 */
object RomKit {

    /* ---- colour ------------------------------------------------------------- */
    val Ink = Color(0xFF05060A)
    val Panel = Color(0xFF0D1017)
    val Panel2 = Color(0xFF141926)
    val Stroke = Color(0xFF232B3D)
    val Divider = Color(0xFF1A2030)

    val TextHi = Color(0xFFF2F5FA)
    val TextLo = Color(0xFF8A94A8)
    val TextMid = Color(0xFFB6BFD0)

    val Ember = Color(0xFFFF6900)
    val EmberHi = Color(0xFFFF8A3D)
    val EmberDeep = Color(0xFFD95300)

    val Cyan = Color(0xFF35E1F2)
    val Good = Color(0xFF38E08B)
    val Warn = Color(0xFFFFC857)
    val Bad = Color(0xFFFF5C6C)

    val OnEmber = Color(0xFF0A0A0A)

    /* ---- type --------------------------------------------------------------- */
    val Mono = FontFamily.Monospace

    val Display = TextStyle(
        fontFamily = GC.Display, fontWeight = FontWeight.Black, fontSize = 30.sp,
        letterSpacing = (-0.5).sp
    )
    val Headline = TextStyle(
        fontFamily = GC.Display, fontWeight = FontWeight.Bold, fontSize = 20.sp,
        letterSpacing = (-0.2).sp
    )
    val Title = TextStyle(
        fontFamily = GC.Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp
    )
    val Body = TextStyle(
        fontFamily = GC.Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp
    )
    val BodySmall = TextStyle(
        fontFamily = GC.Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp
    )
    val Caption = TextStyle(
        fontFamily = GC.Body, fontWeight = FontWeight.Normal, fontSize = 11.sp
    )

    val Label = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 10.sp,
        letterSpacing = 1.sp, textAlign = TextAlign.Start
    )
    val LabelSm = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 9.5.sp, letterSpacing = 0.4.sp
    )
    val Counter = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp
    )
    val Log = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 14.sp
    )
    val MonoValue = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp
    )

    /* ---- shape / space ------------------------------------------------------ */
    val R18 = RoundedCornerShape(18.dp)
    val R16 = RoundedCornerShape(16.dp)
    val R12 = RoundedCornerShape(12.dp)
    val R10 = RoundedCornerShape(10.dp)
    val RPill = RoundedCornerShape(50)

    val PanelPadding = 16.dp
    val PanelGap = 10.dp
    val ScreenGutter = 18.dp

    /* ---- motion ------------------------------------------------------------- */
    const val Micro = 180
    const val Standard = 300
    const val Emphasized = 420

    fun emberBrush() = Brush.horizontalGradient(listOf(Ember, EmberHi))
}

/** The one easing curve in the app. */
val RomKitEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Panel: 18 dp radius, PANEL fill, 1 dp hairline. The only depth model in the app. */
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RomKit.R18)
            .background(RomKit.Panel)
            .border(1.dp, accent?.copy(alpha = 0.45f) ?: RomKit.Stroke, RomKit.R18)
            .padding(RomKit.PanelPadding),
        content = content
    )
}

/** Section label: monospace, uppercase, 10 sp, +1 tracking. 6 dp before content. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, tint: Color = RomKit.TextLo) {
    Text(
        text = text.uppercase(),
        style = RomKit.Label,
        color = tint,
        modifier = modifier.padding(bottom = 6.dp)
    )
}

/** Key / value row: 118 dp key column, monospace value, 3 dp rhythm. */
@Composable
fun KeyValueRow(key: String, value: String, valueColor: Color = RomKit.TextHi) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(key, style = RomKit.MonoValue, color = RomKit.TextLo, modifier = Modifier.width(118.dp))
        Text(
            if (value.isBlank()) "-" else value,
            style = RomKit.MonoValue,
            color = valueColor,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Status chip: full radius, 1 dp border at 55%, monospace label in its own colour. */
@Composable
fun RkPill(
    text: String,
    color: Color = RomKit.Cyan,
    modifier: Modifier = Modifier,
    solid: Boolean = false
) {
    Box(
        modifier
            .clip(RomKit.RPill)
            .background(if (solid) color.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, color.copy(alpha = 0.55f), RomKit.RPill)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text.uppercase(), style = RomKit.Label.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), color = color)
    }
}

/** 6-7 dp state dot. */
@Composable
fun RkDot(color: Color, modifier: Modifier = Modifier, size: Int = 7) {
    Box(modifier.size(size.dp).clip(CircleShape).background(color))
}

/** The single primary action: ember gradient, black 800 label, 56 dp tall. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RomKit.R16)
            .background(if (enabled) RomKit.emberBrush() else Brush.horizontalGradient(
                listOf(RomKit.Stroke, RomKit.Stroke)
            ))
            .then(
                if (enabled) Modifier.clickable(interaction, indication = null, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = RomKit.Display.copy(fontSize = 16.sp, letterSpacing = 0.3.sp),
            color = if (enabled) RomKit.OnEmber else RomKit.TextLo.copy(alpha = if (pressed) 1f else 0.8f)
        )
    }
}

/** Ghost button: hairlines only, tinted variant takes the accent on border + label. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = RomKit.TextHi,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .clip(RomKit.R12)
            .border(1.dp, tint.copy(alpha = if (enabled) 0.40f else GC.Stroke.alpha), RomKit.R12)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = RomKit.Title.copy(fontSize = 13.sp), color = tint)
    }
}

/** Truth line: fixed 58 dp mono tag in a semantic colour, then the plain sentence. */
@Composable
fun TruthLine(tag: String, text: String, color: Color = RomKit.TextLo) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            tag.uppercase(),
            style = RomKit.Label.copy(fontWeight = FontWeight.Black, fontSize = 10.sp),
            color = color,
            modifier = Modifier.width(58.dp)
        )
        Text(text, style = RomKit.Caption.copy(fontSize = 11.sp, lineHeight = 15.sp), color = RomKit.TextLo)
    }
}

/** Step row: 30 dp ring + title over subtitle + trailing pill. */
@Composable
fun RkStepRow(
    number: Int,
    title: String,
    subtitle: String,
    state: Color,
    pill: String,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RomKit.R12)
            .background(if (active) RomKit.Panel2 else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = if (active) 10.dp else 0.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .border(1.5.dp, state, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number.toString(), style = RomKit.Label.copy(fontSize = 11.sp), color = state)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = RomKit.Title.copy(fontSize = 14.sp), color = RomKit.TextHi)
            Text(subtitle, style = RomKit.Caption.copy(fontSize = 11.sp), color = RomKit.TextLo)
        }
        if (pill.isNotBlank()) RkPill(pill, state)
    }
}

/** Log line: mono 10 sp, timestamp at 50% alpha, colour carries the level. */
@Composable
fun RkLogLine(stamp: String, message: String, color: Color = RomKit.TextLo, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(stamp, style = RomKit.Log.copy(fontSize = 9.sp), color = RomKit.TextLo.copy(alpha = 0.5f))
        Spacer(Modifier.width(6.dp))
        Text(
            message,
            style = if (bold) RomKit.Log.copy(fontWeight = FontWeight.Bold) else RomKit.Log,
            color = color
        )
    }
}

/** Collapsible card: whole header row tappable, "+" / "-" glyph, 300 ms open. */
@Composable
fun RkToggleCard(
    title: String,
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    PanelCard(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = RomKit.Title, color = RomKit.TextHi, modifier = Modifier.weight(1f))
            Text(if (open) "-" else "+", style = RomKit.Title.copy(fontSize = 16.sp), color = RomKit.TextLo)
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** Horizontal row of equal-width ghost buttons. */
@Composable
fun GhostRow(vararg items: Pair<String, () -> Unit>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, action) ->
            GhostButton(label, action, Modifier.weight(1f))
        }
    }
}

/** Row scope helper kept private-free: renders children with 8 dp gaps. */
@Composable
fun GhostRowContent(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
