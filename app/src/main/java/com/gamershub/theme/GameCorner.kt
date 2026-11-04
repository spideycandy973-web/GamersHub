package com.gamershub.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.gamershub.R

/** The Game Corner palette — measured from the recording frames. */
object GC {
    val Bg = Color(0xFF0B0B0C)
    val Surface = Color(0xFF1B1917)
    val SurfaceHi = Color(0xFF272320)
    val Text = Color(0xFFF5F1EE)
    val TextSoft = Color(0xFFC9C2BC)
    val TextDim = Color(0xFF9A9490)
    val TagBg = Color(0xFF3A2214)
    val BrownPill = Color(0xFF33251B)
    val Red = Color(0xFFFF3B3B)

    val Display: FontFamily = FontFamily(Font(R.font.fredoka))

    val HeroBrush: Brush = Brush.horizontalGradient(
        listOf(Color(0xFF2A211B), Color(0xFF4A3A2E), Color(0xFF6E5744))
    )

    val Accents = listOf(
        Color(0xFFE05A2B), // orange
        Color(0xFF2196F3), // ocean blue
        Color(0xFF00E0C6), // neon tosca
        Color(0xFFE03545)  // crimson red
    )

    fun accentFor(idx: Int): Color = Accents[idx.coerceIn(0, Accents.lastIndex)]

    fun tagBgFor(idx: Int): Color = when (idx) {
        1 -> Color(0xFF14273A)
        2 -> Color(0xFF0C322E)
        3 -> Color(0xFF3A1418)
        else -> TagBg
    }
}

val LocalAccent = staticCompositionLocalOf { GC.Accents[0] }

@Composable
fun GameCornerTheme(accentIndex: Int = 0, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAccent provides GC.accentFor(accentIndex)) {
        MaterialTheme(
            typography = Typography(),
            content = content
        )
    }
}
