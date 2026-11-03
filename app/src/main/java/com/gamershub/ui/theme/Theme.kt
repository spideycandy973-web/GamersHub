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
// GamersHub design tokens — pure liquid glass.
//
// The look: frosted translucent panels floating over soft depth gradients,
// hairline edges, restrained accents. No neon, no glow, no cyber.
// ---------------------------------------------------------------------------

object GhColors {
    // ---- Dark (game-corner black) ----
    val DarkBg = Color(0xFF07080C)
    val DarkBg2 = Color(0xFF0B0E14)
    val DarkSurface = Color(0xFF12151D)
    val DarkGlass = Color(0x0FFFFFFF)      // 6% frosted white
    val DarkGlassHi = Color(0x1AFFFFFF)    // 10% highlight layer
    val DarkStroke = Color(0x26FFFFFF)     // hairline edge
    val DarkText = Color(0xFFF2F4F8)
    val DarkTextDim = Color(0x99A8AEB9)

    // ---- Light (soft porcelain glass) ----
    val LightBg = Color(0xFFEEF1F6)
    val LightBg2 = Color(0xFFE3E8F0)
    val LightSurface = Color(0xFFF8FAFC)
    val LightGlass = Color(0xB8FFFFFF)     // 72% frosted white
    val LightGlassHi = Color(0xE6FFFFFF)
    val LightStroke = Color(0x1F1B2430)    // hairline edge
    val LightText = Color(0xFF141821)
    val LightTextDim = Color(0x994A5261)

    // ---- Restrained liquid accents (dark-bg versions) ----
    val Cyber = Color(0xFF6E9BD2)          // ink blue (primary accent)
    val Violet = Color(0xFF8E86C4)         // mist violet (secondary)
    val Magenta = Color(0xFFC4899F)        // dusty rose (tertiary)
    val Lime = Color(0xFF7FB69E)           // sage
    val Amber = Color(0xFFFF7A2F)          // flame orange
    val Red = Color(0xFFFF2E43)            // corner red (vivid)
    val Green = Color(0xFF6FBF97)          // soft green

    // ---- Light-bg accent variants (slightly deeper for contrast) ----
    val CyberLight = Color(0xFF3E6B9E)
    val VioletLight = Color(0xFF6A62A6)
    val MagentaLight = Color(0xFFA4637B)
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
        fps < 0f -> textDim
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
        Brush.verticalGradient(listOf(GhColors.Cyber, GhColors.Violet))
    )
}

/** Accent palettes — all muted liquid tones. */
fun glassColors(dark: Boolean, accentKey: String = "CRIMSON"): GlassThemeColors {
    val (a1, a2) = when (accentKey.uppercase()) {
        "TEAL" -> GhColors.Lime to GhColors.Cyber
        "PLUM" -> GhColors.Violet to GhColors.Magenta
        "ROSE" -> GhColors.Magenta to GhColors.Amber
        "CRIMSON" -> GhColors.Red to GhColors.Amber
        else -> GhColors.Cyber to GhColors.Violet // GRAPHITE
    }
    return if (dark) {
        GlassThemeColors(
            GhColors.DarkBg, GhColors.DarkBg2, GhColors.DarkGlass, GhColors.DarkGlassHi,
            GhColors.DarkStroke, GhColors.DarkText, GhColors.DarkTextDim,
            a1, a2, Brush.verticalGradient(listOf(a1, a2))
        )
    } else {
        GlassThemeColors(
            GhColors.LightBg, GhColors.LightBg2, GhColors.LightGlass, GhColors.LightGlassHi,
            GhColors.LightStroke, GhColors.LightText, GhColors.LightTextDim,
            if (accentKey == "GRAPHITE") GhColors.CyberLight else a1,
            if (accentKey == "GRAPHITE") GhColors.VioletLight else a2,
            Brush.verticalGradient(
                listOf(
                    if (accentKey == "GRAPHITE") GhColors.CyberLight else a1,
                    if (accentKey == "GRAPHITE") GhColors.VioletLight else a2
                )
            )
        )
    }
}

private val GhTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 29.sp, lineHeight = 35.sp, letterSpacing = 0.2.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp, lineHeight = 29.sp, letterSpacing = 0.1.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp, letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp, letterSpacing = 0.2.sp
    )
)

@Composable
fun GamersHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: String = "CRIMSON",
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
            onPrimary = Color(0xFF0B0D12)
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
