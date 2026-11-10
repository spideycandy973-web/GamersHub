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
    /* ---- Pre-glass base restored: warm near-black charcoal, solid surfaces ---- */
    val Bg = Color(0xFF100F0D)
    /** Kept for the retired glass backdrop. */
    val BgTop = Color(0xFF171512)
    val Surface = Color(0xFF1C1917)
    val SurfaceHi = Color(0xFF262220)
    val BrownPill = Color(0xFF35291F)
    val TagBg = Color(0xFF332012)
    val Stroke = Color(0xFF332E2A)
    val Text = Color(0xFFF0EFED)
    val TextDim = Color(0xFF948B84)
    val TextSoft = Color(0xFFB5ACA6)

    /* ---- Glass layer tokens (retired, kept so old references compile) ---- */
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
    /** Light red — the app's default accent, used here as the console accent too. */
    val Signal = Color(0xFFFF8A80)
    val SignalDeep = Color(0xFFB3352F)
    val SignalGlow = Color(0xFFFFB4B0)

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

    /** Light red — the default look of the whole app. */
    val LightRed = Color(0xFFFF8A80)
    /** Deeper stop that pairs with [LightRed] in gradients and rims. */
    val LightRedDeep = Color(0xFFFF5A5A)

    /** Sampled from the launcher emblem: ember red core, orange edge, violet circuitry. */
    val IconRed = Color(0xFFF6231C)
    val IconAmber = Color(0xFFFF7A18)
    val IconViolet = Color(0xFF8A5CFF)

    /** Set by [GameCornerTheme] when the user picks a colour on the slider (index 4). */
    @Volatile
    var custom: Color? = null

    /**
     * The red family. Six tones from pale to deep, plus a warm ember, so there is
     * always one that reads clearly as text on the warm black and still works as a
     * filled button under dark ink. Index order matches the swatches in Preferences.
     */
    data class Tone(val name: String, val color: Color, val pair: Color, val bed: Color)

    val TONES: List<Tone> = listOf(
        Tone("Light red", Color(0xFFFF8A80), Color(0xFFFF5A5A), Color(0xFF3E1818)),
        Tone("Rose red", Color(0xFFFF6B6B), Color(0xFFFF3B3B), Color(0xFF3D1416)),
        Tone("True red", Color(0xFFFF4D4D), Color(0xFFD32F2F), Color(0xFF3C1010)),
        Tone("Signal red", Color(0xFFFF2D2D), Color(0xFFB71C1C), Color(0xFF3A0D0D)),
        Tone("Deep red", Color(0xFFE53935), Color(0xFF9E2020), Color(0xFF360F0E)),
        Tone("Ember", Color(0xFFFF7A18), Color(0xFFD84315), Color(0xFF3A2410))
    )

    /** Index that means "whatever the slider made". */
    const val CUSTOM = 6

    fun toneAt(index: Int): Tone = TONES.getOrElse(index) { TONES[0] }

    fun accentFor(index: Int): Color = when {
        index == CUSTOM -> custom ?: TONES[0].color
        else -> toneAt(index).color
    }

    /** Darker bed for a custom accent — mixed into the warm surface, not a new hue. */
    fun customBed(): Color {
        val c = custom ?: TONES[0].color
        val t = 0.22f
        return Color(
            red = Surface.red * (1 - t) + c.red * t,
            green = Surface.green * (1 - t) + c.green * t,
            blue = Surface.blue * (1 - t) + c.blue * t,
            alpha = 1f
        )
    }

    /** Second stop of the accent gradient (used for rims and highlights). */
    fun accentPairFor(index: Int): Color = when {
        index == CUSTOM -> custom ?: TONES[0].pair
        else -> toneAt(index).pair
    }

    fun tagBgFor(index: Int): Color = when {
        index == CUSTOM -> customBed()
        else -> toneAt(index).bed
    }

    fun brownFor(index: Int): Color = when {
        index == CUSTOM -> customBed()
        else -> toneAt(index).bed
    }

    /**
     * Readable ink for a filled accent chip: dark on every bright tone, white only if
     * the accent is unusually dark (a deep slider pick).
     */
    fun inkOn(accent: Color): Color {
        val lum = 0.2126f * accent.red + 0.7152f * accent.green + 0.0722f * accent.blue
        return if (lum > 0.42f) Color(0xFF17100A) else Color(0xFFFDF7F3)
    }

    /** Accent nudged towards white, for small labels that must stay legible. */
    fun readable(accent: Color): Color {
        val lum = 0.2126f * accent.red + 0.7152f * accent.green + 0.0722f * accent.blue
        if (lum >= 0.34f) return accent
        val t = 0.34f
        return Color(
            red = accent.red * (1 - t) + t,
            green = accent.green * (1 - t) + t,
            blue = accent.blue * (1 - t) + t,
            alpha = 1f
        )
    }

    val HeroBrush = Brush.horizontalGradient(
        listOf(Color(0xFF3A2A1E), Color(0xFF2B1F17), Color(0xFF241A13))
    )

    /**
     * One family everywhere: Oxanium (variable). Squared, technical, game-console
     * letterforms — deliberately not the neutral grotesque the UI used before.
     */
    private fun outfit(weight: Int, w: FontWeight) = Font(
        R.font.oxanium,
        weight = w,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight))
    )

    val Display = FontFamily(
        outfit(500, FontWeight.Medium),
        outfit(600, FontWeight.SemiBold),
        outfit(700, FontWeight.Bold),
        outfit(800, FontWeight.Black)
    )

    val Body = FontFamily(
        outfit(400, FontWeight.Normal),
        outfit(500, FontWeight.Medium),
        outfit(600, FontWeight.SemiBold),
        outfit(700, FontWeight.Bold)
    )
}

/** Live accent color (Settings ▸ Theme Color). */
val LocalAccent = staticCompositionLocalOf { GC.LightRed }
val LocalAccentIdx = staticCompositionLocalOf { 0 }

@Composable
fun GameCornerTheme(
    accentIndex: Int = 0,
    customAccent: Color? = null,
    content: @Composable () -> Unit
) {
    // Publishing the slider colour here keeps every helper that only knows an index
    // (tag beds, icon chips, gradients) in step with the chosen hue.
    GC.custom = customAccent
    val accent = customAccent ?: GC.accentFor(accentIndex)
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
        LocalAccentIdx provides (if (customAccent != null) GC.CUSTOM else accentIndex)
    ) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}
