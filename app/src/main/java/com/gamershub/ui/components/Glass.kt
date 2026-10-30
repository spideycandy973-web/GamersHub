package com.gamershub.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Frosted-glass design system.
 *
 * The "glass" look = translucent panel + hairline gradient border + soft shadow.
 * On Android 12+ real window blur is enabled on top of this (see MainActivity).
 */

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(corner)
    val interaction = remember { MutableInteractionSource() }
    val pressed = if (onClick != null) interaction.collectIsPressedAsState().value else false
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "card-press"
    )
    val elevation by animateDpAsState(
        if (pressed) 1.dp else 6.dp, tween(180), label = "card-elev"
    )

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(elevation, shape, ambientColor = g.accent.copy(alpha = 0.15f))
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (highlight) g.glassHi else g.glass,
                        g.glass
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(g.stroke, g.stroke.copy(alpha = 0.05f))
                ),
                shape = shape
            )
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

/** Button with gradient fill, glow and buttery press animation. */
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
        if (pressed) 0.94f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label = "btn-scale"
    )
    val bgAlpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "btn-alpha")

    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = bgAlpha }
            .shadow(if (primary) 10.dp else 2.dp, shape, spotColor = g.accent.copy(alpha = 0.5f))
            .clip(shape)
            .background(
                if (primary) Brush.horizontalGradient(listOf(g.accent, g.accent2))
                else Brush.verticalGradient(listOf(g.glassHi, g.glass))
            )
            .border(1.dp, if (primary) Color.Transparent else g.stroke, shape)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Text(
                text = text,
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                color = if (primary) Color(0xFF04121A) else g.text
            )
            content?.invoke(this)
        }
    }
}

/** Custom animated switch with sliding glow knob. */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val g = LocalGlass.current
    val knobOffset by animateDpAsState(
        if (checked) 22.dp else 0.dp,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumHigh),
        label = "switch-knob"
    )
    val trackColor by animateColorAsState(
        if (checked) g.accent else g.glass,
        tween(220), label = "switch-track"
    )
    val trackStroke by animateColorAsState(
        if (checked) g.accent2.copy(alpha = 0.8f) else g.stroke,
        tween(220), label = "switch-stroke"
    )
    Box(
        modifier = modifier
            .width(52.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(trackColor)
            .border(1.dp, trackStroke, RoundedCornerShape(15.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .padding(start = knobOffset)
                .height(24.dp)
                .width(24.dp)
                .shadow(if (checked) 6.dp else 0.dp, CircleShape, spotColor = g.accent)
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
            androidx.compose.material3.Text(
                title,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = g.text
            )
            if (subtitle != null) {
                androidx.compose.material3.Text(
                    subtitle,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = g.textDim
                )
            }
        }
        GlassSwitch(checked, onCheckedChange)
    }
}

/** Small selectable chip (used for fps / resolution pickers). */
@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val g = LocalGlass.current
    val bg by animateColorAsState(
        if (selected) g.accent else g.glass, tween(200), label = "chip-bg"
    )
    val fg by animateColorAsState(
        if (selected) Color(0xFF04121A) else g.text, tween(200), label = "chip-fg"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else g.stroke, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        androidx.compose.material3.Text(
            text,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            color = fg
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    androidx.compose.material3.Text(
        text.uppercase(),
        modifier = modifier.padding(vertical = 10.dp),
        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        color = g.accent
    )
}

/** Gradient divider line. */
@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, g.stroke, Color.Transparent)
                )
            )
    )
}
