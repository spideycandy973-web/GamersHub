package com.gamershub.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gamershub.ui.theme.LocalGlass

/**
 * Liquid-glass design system.
 *
 * Frosted translucent panels + hairline edges + soft depth shadows.
 * No neon, no glow — restraint is the aesthetic. On Android 12+ the window
 * behind the app is blurred (see MainActivity) for true glass depth.
 */

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 22.dp,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(corner)
    val interaction = remember { MutableInteractionSource() }
    val pressed = if (onClick != null) interaction.collectIsPressedAsState().value else false
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
        label = "card-press"
    )
    val elevation by animateDpAsState(
        if (pressed) 1.dp else 5.dp, tween(180), label = "card-elev"
    )

    // Content lives in a COLUMN: card children stack vertically like a normal
    // list. (A Box host would pile every child on top of the previous one.)
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(elevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.10f))
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (highlight) g.glassHi else g.glass,
                        g.glass.copy(alpha = g.glass.alpha * 0.75f)
                    )
                )
            )
            .border(width = 1.dp, color = g.stroke, shape = shape)
            .let { m ->
                if (onClick != null) m.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ) else m
            }
            .padding(16.dp)
    ) {
        content()
    }
}

/** Solid-feeling liquid button with a soft press animation. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    content: (@Composable RowScope.() -> Unit)? = null
) {
    val g = LocalGlass.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
        label = "btn-scale"
    )
    val alpha by animateFloatAsState(if (enabled) 1f else 0.45f, label = "btn-alpha")

    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .shadow(if (primary) 6.dp else 2.dp, shape, ambientColor = Color.Black.copy(alpha = 0.12f))
            .clip(shape)
            .then(
                if (primary) Modifier.background(g.accent)
                else Modifier.background(Brush.verticalGradient(listOf(g.glassHi, g.glass)))
            )
            .border(1.dp, if (primary) Color.Transparent else g.stroke, shape)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (primary) Color.White else g.text
            )
            content?.invoke(this)
        }
    }
}

/** Custom animated switch with a sliding frosted knob. */
@Composable
fun GlassSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit
) {
    val g = LocalGlass.current
    val knobOffset by animateDpAsState(
        if (checked) 22.dp else 0.dp,
        spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
        label = "switch-knob"
    )
    val trackColor by animateColorAsState(
        if (checked) g.accent else g.glassHi, tween(220), label = "switch-track"
    )
    Box(
        modifier = modifier
            .width(52.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(trackColor)
            .border(1.dp, g.stroke, RoundedCornerShape(15.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .padding(start = knobOffset)
                .height(24.dp)
                .width(24.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** A labelled switch row for settings lists. */
@Composable
fun GlassSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val g = LocalGlass.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = g.text
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = g.textDim
                )
            }
        }
        GlassSwitch(checked) { onCheckedChange(it) }
    }
}

/** Small selectable chip (used for fps / resolution pickers). */
@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val g = LocalGlass.current
    val bg by animateColorAsState(
        if (selected) g.accent else g.glassHi, tween(200), label = "chip-bg"
    )
    val fg by animateColorAsState(
        if (selected) Color.White else g.text, tween(200), label = "chip-fg"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else g.stroke, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = fg
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    Text(
        text.uppercase(),
        modifier = modifier.padding(vertical = 10.dp),
        style = MaterialTheme.typography.labelLarge,
        color = g.textDim
    )
}

/** Hairline divider. */
@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(g.stroke)
    )
}
