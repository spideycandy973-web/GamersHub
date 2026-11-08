@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.gamershub.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.gamershub.R

/** Game Corner design system — warm near-black, accent-driven, chunky display type. */
object GC {
    /* ---- Liquid-glass base: deep ink, cool light, no warm browns ---- */
    val Bg = Color(0xFF0B0806)
    /** Top of the ambient backdrop gradient. */
    val BgTop = Color(0xFF1A1108)
    val Surface = Color(0xC41B1511)
    val SurfaceHi = Color(0xD6241C16)
    val BrownPill = Color(0xFF241C16)
    val TagBg = Color(0xFF2B211A)
    val Stroke = Color(0xFF3A2E25)
    val Text = Color(0xFFF7F1E9)
    val TextDim = Color(0xFFA2948A)
    val TextSoft = Color(0xFFC9BCB0)

    /* ---- Glass layers: warm smoke, used by Modifier.liquidGlass ---- */
    val GlassHi = Color(0x1FFFF0DC)
    val GlassLo = Color(0x09FFF0DC)
    val GlassEdgeHi = Color(0x5CFFE7CE)
    val GlassEdgeLo = Color(0x0DFFFFFF)
    /** Text/icon colour on top of a filled accent. */
    val OnAccent = Color(0xFF17100A)

    /* ---- Game Space (in-game hub) palette — sampled from the reference console ---- */
    val Void = Color(0xFF090607)
    val VoidMid = Color(0xFF120A0B)
    val InkPanel = Color(0xCC120C0D)
    val InkPanelHi = Color(0xFF1C1315)
    val InkStroke = Color(0x26FFFFFF)
    val InkText = Color(0xFFF6F3F2)
    val InkDim = Color(0xFF9C9294)
    val InkSoft = Color(0xFFC8BEBF)
    /** Reference signal red — the console accent. */
    val Signal = Color(0xFFFF1717)
    val SignalDeep = Color(0xFFC00B0B)
    val SignalGlow = Color(0xFFFF5C5C)

    /* ---- Accents.  Index 0 is the default: "Molten Gold", the same amber the
       launcher gem is built from.  It splits into ember, exactly like the icon. ---- */
    val Gold = Color(0xFFFFB03A)
    val Ember = Color(0xFFFF6A2A)
    val Crimson = Color(0xFFFF3B30)
    val Jade = Color(0xFF2FD8A0)
    /** Kept for legacy references. */
    val Iris = Gold
    val Aqua = Color(0xFF5CC8FF)
    val Mint = Jade
    val Rose = Crimson
    val Orange = Color(0xFFE25E20)
    val OceanBlue = Color(0xFF5CC8FF)
    val NeonTosca = Jade
    val CrimsonRed = Crimson
    val Beige = Color(0xFFC9BCB0)

    fun accentFor(index: Int): Color = when (index) {
        1 -> Ember
        2 -> Crimson
        3 -> Jade
        else -> Gold
    }

    /** Second stop of the accent gradient (used for glass rims and highlights). */
    fun accentPairFor(index: Int): Color = when (index) {
        1 -> Gold
        2 -> Ember
        3 -> Color(0xFF7CFFC9)
        else -> Ember
    }

    fun tagBgFor(index: Int): Color = when (index) {
        1 -> Color(0xFF3A2413)
        2 -> Color(0xFF3A1511)
        3 -> Color(0xFF10382B)
        else -> TagBg
    }

    fun brownFor(index: Int): Color = when (index) {
        1 -> Color(0xFF3D2713)
        2 -> Color(0xFF3D1713)
        3 -> Color(0xFF123B2D)
        else -> BrownPill
    }

    val HeroBrush = Brush.horizontalGradient(
        listOf(Color(0x38FF9A3C), Color(0x20C2160A), Color(0x0AFFF0DC))
    )

    /**
     * One family, everywhere: Archivo (variable, wght + wdth).
     * Display runs expanded and heavy, body runs normal width — same voice, two registers.
     */
    private fun archivo(weight: Int, widthPct: Float, w: FontWeight) = Font(
        R.font.archivo,
        weight = w,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.width(widthPct)
        )
    )

    val Display = FontFamily(
        archivo(500, 112f, FontWeight.Medium),
        archivo(600, 112f, FontWeight.SemiBold),
        archivo(700, 112f, FontWeight.Bold),
        archivo(800, 112f, FontWeight.Black)
    )

    val Body = FontFamily(
        archivo(400, 100f, FontWeight.Normal),
        archivo(500, 100f, FontWeight.Medium),
        archivo(600, 100f, FontWeight.SemiBold),
        archivo(700, 100f, FontWeight.Bold)
    )
}

/** Live accent color (Settings ▸ Theme Color). */
val LocalAccent = staticCompositionLocalOf { GC.Gold }
val LocalAccentIdx = staticCompositionLocalOf { 0 }

@Composable
fun GameCornerTheme(
    accentIndex: Int = 0,
    content: @Composable () -> Unit
) {
    val accent = GC.accentFor(accentIndex)
    val scheme = darkColorScheme(
        primary = accent, onPrimary = Color.White,
        secondary = accent, background = GC.Bg, onBackground = GC.Text,
        surface = GC.Surface, onSurface = GC.Text,
        surfaceVariant = GC.SurfaceHi, onSurfaceVariant = GC.TextDim,
        outline = GC.Stroke, error = GC.CrimsonRed
    )
    val type = Typography(
        displayLarge = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Black),
        displayMedium = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Black),
        displaySmall = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Bold),
        headlineLarge = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Bold),
        headlineMedium = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Bold),
        headlineSmall = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontFamily = GC.Body, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontFamily = GC.Body, fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(fontFamily = GC.Body, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontFamily = GC.Body, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontFamily = GC.Body, fontWeight = FontWeight.Medium)
    )
    androidx.compose.runtime.CompositionLocalProvider(
        LocalAccent provides accent,
        LocalAccentIdx provides accentIndex
    ) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}
