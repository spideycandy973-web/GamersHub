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
    val Bg = Color(0xFF06070C)
    /** Top of the ambient backdrop gradient. */
    val BgTop = Color(0xFF0C1220)
    val Surface = Color(0xC4111624)
    val SurfaceHi = Color(0xD6161C2C)
    val BrownPill = Color(0xFF161C2A)
    val TagBg = Color(0xFF181F30)
    val Stroke = Color(0xFF252B3A)
    val Text = Color(0xFFEDF1FA)
    val TextDim = Color(0xFF8A93A6)
    val TextSoft = Color(0xFFB7BFD0)

    /* ---- Glass layers: translucent whites, used by Modifier.liquidGlass ---- */
    val GlassHi = Color(0x1FFFFFFF)
    val GlassLo = Color(0x0AFFFFFF)
    val GlassEdgeHi = Color(0x52FFFFFF)
    val GlassEdgeLo = Color(0x0FFFFFFF)

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

    /* ---- Accents.  Index 0 is the default and is now "Iris": a liquid
       violet that refracts into cyan, matching the launcher mark. ---- */
    val Iris = Color(0xFF7C8CFF)
    val Aqua = Color(0xFF4FD8FF)
    val Mint = Color(0xFF6EE7B7)
    val Rose = Color(0xFFFF7A9C)
    /** Kept for legacy surfaces (Game Space console keeps its own red). */
    val Orange = Color(0xFFE25E20)
    val OceanBlue = Color(0xFF4FD8FF)
    val NeonTosca = Color(0xFF6EE7B7)
    val CrimsonRed = Color(0xFFFF7A9C)
    val Beige = Color(0xFFB7BFD0)

    fun accentFor(index: Int): Color = when (index) {
        1 -> Aqua
        2 -> Mint
        3 -> Rose
        else -> Iris
    }

    /** Second stop of the accent gradient (used for glass rims and highlights). */
    fun accentPairFor(index: Int): Color = when (index) {
        1 -> Color(0xFF7C8CFF)
        2 -> Color(0xFF4FD8FF)
        3 -> Color(0xFFFFB27A)
        else -> Color(0xFF4FD8FF)
    }

    fun tagBgFor(index: Int): Color = when (index) {
        1 -> Color(0xFF0E2433)
        2 -> Color(0xFF0E2C24)
        3 -> Color(0xFF301626)
        else -> TagBg
    }

    fun brownFor(index: Int): Color = when (index) {
        1 -> Color(0xFF152A3C)
        2 -> Color(0xFF12332D)
        3 -> Color(0xFF331A28)
        else -> BrownPill
    }

    val HeroBrush = Brush.horizontalGradient(
        listOf(Color(0x1F7C8CFF), Color(0x144FD8FF), Color(0x0AFFFFFF))
    )

    /**
     * Display face — Sora (variable). Geometric grotesk with a single-storey g,
     * tall x-height and squared shoulders: the closest match to the reference
     * console lettering, and nothing like a stock Android face.
     */
    val Display = FontFamily(
        Font(R.font.sora, weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.sora, weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.sora, weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.sora, weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700))),
        Font(R.font.sora, weight = FontWeight.Black,
            variationSettings = FontVariation.Settings(FontVariation.weight(800)))
    )

    /** Body face — Plus Jakarta Sans (variable): humanist-geometric, crisp at 9–13sp. */
    val Body = FontFamily(
        Font(R.font.jakarta, weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.jakarta, weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.jakarta, weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.jakarta, weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700)))
    )
}

/** Live accent color (Settings ▸ Theme Color). */
val LocalAccent = staticCompositionLocalOf { GC.Iris }
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
