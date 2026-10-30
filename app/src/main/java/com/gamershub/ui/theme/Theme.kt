package com.gamershub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// GamersHub design tokens — neon gaming palette over glass surfaces.
// ---------------------------------------------------------------------------

object GhColors {
    // Dark
    val DarkBg = Color(0xFF070A14)
    val DarkBg2 = Color(0xFF0B1020)
    val DarkSurface = Color(0xFF121829)
    val DarkGlass = Color(0x14FFFFFF)      // 8% white glass
    val DarkGlassHi = Color(0x24FFFFFF)    // 14% white glass
    val DarkStroke = Color(0x33FFFFFF)     // 20% white border
    val DarkText = Color(0xFFF1F5FF)
    val DarkTextDim = Color(0x99E2E8F0)

    // Light
    val LightBg = Color(0xFFEFF2FB)
    val LightBg2 = Color(0xFFE4E9F7)
    val LightSurface = Color(0xFFFFFFFF)
    val LightGlass = Color(0x99FFFFFF)     // 60% white glass
    val LightGlassHi = Color(0xCFFFFFFF)
    val LightStroke = Color(0x330B1020)    // 20% ink border
    val LightText = Color(0xFF111827)
    val LightTextDim = Color(0x99111827)

    // Accents
    val Cyber = Color(0xFF22D3EE)          // electric cyan
    val Violet = Color(0xFF8B5CF6)
    val Magenta = Color(0xFFF472B6)
    val Lime = Color(0xFFA3E635)
    val Amber = Color(0xFFF59E0B)
    val Red = Color(0xFFEF4444)
    val Green = Color(0xFF34D399)

    // Light-theme variants (darker for contrast)
    val CyberLight = Color(0xFF0E7490)
    val VioletLight = Color(0xFF6D28D9)
    val MagentaLight = Color(0xFFBE185D)
}

/** Per-theme glass parameters consumed by the components layer. */
data class GlassThemeColors(
    val bg: Color,
    val bg2: Color,
    val glass: Color,
    val glassHi: Color,
    val stroke: Color,
    val text: Color,
    val textDim: Color,
    val accent: Color,
    val accent2: Color,
    val gradient: Brush
) {
    fun tempColor(tempC: Float): Color = when {
        tempC < 0f -> textDim
        tempC < 38f -> GhColors.Green
        tempC < 43f -> GhColors.Amber
        else -> GhColors.Red
    }

    fun fpsColor(fps: Float, target: Float = 60f): Color = when {
        fps < 0 -> textDim
        fps >= target * 0.95f -> GhColors.Green
        fps >= target * 0.75f -> GhColors.Amber
        else -> GhColors.Red
    }
}

val LocalGlass = staticCompositionLocalOf<GlassThemeColors> {
    GlassThemeColors(
        GhColors.DarkBg, GhColors.DarkBg2, GhColors.DarkGlass, GhColors.DarkGlassHi,
        GhColors.DarkStroke, GhColors.DarkText, GhColors.DarkTextDim,
        GhColors.Cyber, GhColors.Violet,
        Brush.linearGradient(listOf(GhColors.Cyber, GhColors.Violet, GhColors.Magenta))
    )
}

fun glassColors(dark: Boolean, accentKey: String = "CYBER"): GlassThemeColors {
    val (a1, a2) = when (accentKey.uppercase()) {
        "VIOLET" -> GhColors.Violet to GhColors.Magenta
        "MAGMA" -> GhColors.Magenta to GhColors.Amber
        "LIME" -> GhColors.Lime to GhColors.Cyber
        else -> GhColors.Cyber to GhColors.Violet
    }
    return if (dark) {
        GlassThemeColors(
            GhColors.DarkBg, GhColors.DarkBg2, GhColors.DarkGlass, GhColors.DarkGlassHi,
            GhColors.DarkStroke, GhColors.DarkText, GhColors.DarkTextDim,
            a1, a2, Brush.linearGradient(listOf(a1, a2, GhColors.Magenta))
        )
    } else {
        GlassThemeColors(
            GhColors.LightBg, GhColors.LightBg2, GhColors.LightGlass, GhColors.LightGlassHi,
            GhColors.LightStroke, GhColors.LightText, GhColors.LightTextDim,
            if (accentKey == "CYBER") GhColors.CyberLight else a1,
            if (accentKey == "CYBER") GhColors.VioletLight else a2,
            Brush.linearGradient(
                listOf(
                    if (accentKey == "CYBER") GhColors.CyberLight else a1,
                    if (accentKey == "CYBER") GhColors.VioletLight else a2
                )
            )
        )
    }
}

private val GhTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = 0.5.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = 0.3.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = 0.2.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp, letterSpacing = 0.8.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp, letterSpacing = 0.4.sp
    )
)

@Composable
fun GamersHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: String = "CYBER",
    content: @Composable () -> Unit
) {
    val glass = glassColors(darkTheme, accent)
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = glass.accent,
            secondary = glass.accent2,
            background = glass.bg,
            surface = GhColors.DarkSurface,
            onBackground = glass.text,
            onSurface = glass.text,
            onPrimary = Color(0xFF04121A)
        )
    } else {
        lightColorScheme(
            primary = glass.accent,
            secondary = glass.accent2,
            background = glass.bg,
            surface = GhColors.LightSurface,
            onBackground = glass.text,
            onSurface = glass.text,
            onPrimary = Color.White
        )
    }
    CompositionLocalProvider(LocalGlass provides glass) {
        MaterialTheme(
            colorScheme = scheme,
            typography = GhTypography,
            content = content
        )
    }
}
