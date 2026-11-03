package com.gamershub.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Game Corner design system — original implementation.
 *
 * Deep game-corner black, vivid corner red, flame accents and a
 * cut-corner geometry (diagonal corners top-start / bottom-end) that
 * gives the whole app its "red corner" identity.
 */
object GC {
    val Bg = Color(0xFF07080C)
    val Bg2 = Color(0xFF0B0E14)
    val Surface = Color(0xFF12151D)
    val SurfaceHi = Color(0xFF1A1F29)
    val Stroke = Color(0x29FFFFFF)
    val Text = Color(0xFFF2F4F8)
    val TextDim = Color(0x99A8AEB9)

    val Red = Color(0xFFFF2E43)      // corner red
    val Flame = Color(0xFFFF7A2F)    // flame orange

    val RedBrush = Brush.horizontalGradient(listOf(Red, Flame))
    val DarkGlassBrush = Brush.verticalGradient(listOf(SurfaceHi, Surface))
}

/** Signature cut-corner surface shape. */
val CutCard = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
val CutChip = CutCornerShape(topStart = 9.dp, bottomEnd = 9.dp)
val CutPill = CutCornerShape(topStart = 11.dp, bottomEnd = 11.dp)

@Composable
fun GameCornerTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = GC.Red,
        onPrimary = Color.White,
        secondary = GC.Flame,
        onSecondary = Color.White,
        background = GC.Bg,
        onBackground = GC.Text,
        surface = GC.Surface,
        onSurface = GC.Text,
        surfaceVariant = GC.SurfaceHi,
        onSurfaceVariant = GC.TextDim,
        outline = GC.Stroke,
        error = GC.Red
    )
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}
