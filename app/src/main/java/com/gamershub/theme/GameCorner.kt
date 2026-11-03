@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.gamershub.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gamershub.R

/**
 * Game Corner design system — warm near-black surfaces, vivid orange,
 * chunky rounded display type. Original implementation matched to the
 * reference screenshots.
 */
object GC {
    val Bg = Color(0xFF0A0A0B)
    val Surface = Color(0xFF16110D)         // warm dark card
    val SurfaceHi = Color(0xFF211812)       // lifted warm card
    val BrownPill = Color(0xFF3B2417)       // selected pill / icon chip bg
    val TagBg = Color(0xFF38200E)           // small tag pill bg
    val Stroke = Color(0xFF2C231C)
    val Text = Color(0xFFF2F2F2)
    val TextDim = Color(0xFF8E8E90)
    val TextSoft = Color(0xFFB9B4B0)

    val Orange = Color(0xFFF2681E)          // primary accent
    val OrangeDeep = Color(0xFFE05A14)
    val Beige = Color(0xFFC9B8A8)           // hero watermark tone

    val OrangeBrush = Brush.horizontalGradient(listOf(Orange, OrangeDeep))
    val HeroBrush = Brush.horizontalGradient(
        listOf(Color(0xFF3A2A1E), Color(0xFF2B1F17), Color(0xFF241A13))
    )

    /** Chunky rounded display font (open-source Fredoka, variable). */
    val Display = FontFamily(
        Font(
            R.font.fredoka, weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500))
        ),
        Font(
            R.font.fredoka, weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600))
        ),
        Font(
            R.font.fredoka, weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700))
        )
    )
}

@Composable
fun GameCornerTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = GC.Orange,
        onPrimary = Color.White,
        secondary = GC.OrangeDeep,
        background = GC.Bg,
        onBackground = GC.Text,
        surface = GC.Surface,
        onSurface = GC.Text,
        surfaceVariant = GC.SurfaceHi,
        onSurfaceVariant = GC.TextDim,
        outline = GC.Stroke,
        error = GC.Orange
    )
    val type = Typography(
        displayLarge = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Bold),
        displayMedium = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Bold),
        displaySmall = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Bold),
        headlineLarge = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Bold),
        headlineMedium = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Bold),
        headlineSmall = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Medium)
    )
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
