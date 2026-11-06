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
    val Bg = Color(0xFF0A0A0B)
    val Surface = Color(0xFF16110D)
    val SurfaceHi = Color(0xFF211812)
    val BrownPill = Color(0xFF3B2417)
    val TagBg = Color(0xFF38200E)
    val Stroke = Color(0xFF2C231C)
    val Text = Color(0xFFF2F2F2)
    val TextDim = Color(0xFF8E8E90)
    val TextSoft = Color(0xFFB9B4B0)

    val Orange = Color(0xFFF2681E)
    val OceanBlue = Color(0xFF2FA3E0)
    val NeonTosca = Color(0xFF1FE0C0)
    val CrimsonRed = Color(0xFFE23645)
    val Beige = Color(0xFFC9B8A8)

    fun accentFor(index: Int): Color = when (index) {
        1 -> OceanBlue
        2 -> NeonTosca
        3 -> CrimsonRed
        else -> Orange
    }

    fun tagBgFor(index: Int): Color = when (index) {
        1 -> Color(0xFF122A3A)
        2 -> Color(0xFF0E322C)
        3 -> Color(0xFF3A1216)
        else -> TagBg
    }

    fun brownFor(index: Int): Color = when (index) {
        1 -> Color(0xFF17303F)
        2 -> Color(0xFF123A34)
        3 -> Color(0xFF3A181D)
        else -> BrownPill
    }

    val HeroBrush = Brush.horizontalGradient(
        listOf(Color(0xFF3A2A1E), Color(0xFF2B1F17), Color(0xFF241A13))
    )

    /**
     * Display face — Orbitron variable font (geometric, squared, arcade/tech look).
     * Deliberately NOT a Roboto look-alike: the app needs its own voice.
     */
    val Display = FontFamily(
        Font(R.font.orbitron, weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.orbitron, weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.orbitron, weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700))),
        Font(R.font.orbitron, weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(800))),
        Font(R.font.orbitron, weight = FontWeight.Black,
            variationSettings = FontVariation.Settings(FontVariation.weight(900)))
    )

    /** Body face — Chakra Petch, a semi-condensed techno sans that reads cleanly at 11–15sp. */
    val Body = FontFamily(
        Font(R.font.chakra_regular, weight = FontWeight.Normal),
        Font(R.font.chakra_medium, weight = FontWeight.Medium),
        Font(R.font.chakra_semibold, weight = FontWeight.SemiBold),
        Font(R.font.chakra_bold, weight = FontWeight.Bold)
    )
}

/** Live accent color (Settings ▸ Theme Color). */
val LocalAccent = staticCompositionLocalOf { GC.Orange }
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
