package app.zcal.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Section 12 Visual Design System Color Tokens (Light, Dark & Pure Black AMOLED)
 * Primary Brand Color: #23212C (Luxury Slate Obsidian)
 * All contrast ratios audited against WCAG 2.1 AA/AAA:
 * - Light primary (#23212C) on White: 15.6:1 (AAA)
 * - Dark primary (#D6D0EA) on Dark Surface (#23212C): 10.4:1 (AAA)
 * - TextPrimary on Canvas/Surface: >13.5:1 (AAA)
 * - Macro colors are fixed semantic accents (Protein, Carbs, Fat) and never overridden.
 */
object ZCalPalette {
    // Brand Token
    val BrandObsidian = Color(0xFF23212C)

    // Light Mode Tokens
    val LightCanvas = Color(0xFFF7F8F5)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceElevated = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFEEF1EC)
    val LightOutline = Color(0xFFDDE2DA)
    val LightTextPrimary = Color(0xFF101512)
    val LightTextSecondary = Color(0xFF5B655E)
    val LightPrimary = Color(0xFF23212C)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightAccent = Color(0xFF7C5CFC) // Vibrant Electric Violet
    val LightOnAccent = Color(0xFFFFFFFF)
    val LightSecondary = Color(0xFF10B981) // Fresh Emerald
    val LightOnSecondary = Color(0xFFFFFFFF)
    val LightAccentGlow = Color(0x337C5CFC)
    val LightProtein = Color(0xFFE5533D)
    val LightCarbs = Color(0xFFC27C0E)
    val LightFat = Color(0xFF3B7BF2)
    val LightError = Color(0xFFC62828)

    // Dark Mode Tokens (Obsidian Slate Theme)
    val DarkCanvas = Color(0xFF16151F)
    val AmoledCanvas = Color(0xFF000000) // Section 12 Page 25 pure black AMOLED option
    val DarkSurface = Color(0xFF23212C)
    val DarkSurfaceElevated = Color(0xFF2C283A)
    val AmoledSurface = Color(0xFF0A090F)
    val AmoledSurfaceElevated = Color(0xFF14121D)
    val DarkSurfaceVariant = Color(0xFF2E2B3A)
    val DarkOutline = Color(0xFF3E3A4D)
    val DarkTextPrimary = Color(0xFFF0EEF5)
    val DarkTextSecondary = Color(0xFFA6A2B6)
    val DarkPrimary = Color(0xFFD6D0EA)
    val DarkOnPrimary = Color(0xFF23212C)
    val DarkAccent = Color(0xFFA78BFA) // Luminous Violet
    val DarkOnAccent = Color(0xFF16151F)
    val DarkSecondary = Color(0xFF34D399) // Luminous Mint Emerald
    val DarkOnSecondary = Color(0xFF064E3B)
    val DarkAccentGlow = Color(0x4DA78BFA)
    val DarkProtein = Color(0xFFFF7A66)
    val DarkCarbs = Color(0xFFF0B04A)
    val DarkFat = Color(0xFF7BA7FF)
    val DarkError = Color(0xFFFF8A80)

    // Calendar Heat Map Tints (Section 11 Page 20: Default vs. Calm Palette)
    val CalOnTargetTintLight = Color(0xFFD6D2E6)
    val CalCloseTintLight = Color(0xFFFCEBC8)
    val CalOffTintLight = Color(0xFFFADAD4)
    val CalCalmHighLight = Color(0xFFDED9EE)
    val CalCalmMedLight = Color(0xFFEFEBF8)

    val CalOnTargetTintDark = Color(0xFF353047)
    val CalCloseTintDark = Color(0xFF3D2E14)
    val CalOffTintDark = Color(0xFF3E1F1B)
    val CalCalmHighDark = Color(0xFF322D44)
    val CalCalmMedDark = Color(0xFF282436)
}

@Immutable
data class ZCalExtendedColors(
    val canvas: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val onAccent: Color,
    val secondary: Color,
    val onSecondary: Color,
    val accentGlow: Color,
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    val error: Color,
    val isDark: Boolean
)

val LocalZCalColors = staticCompositionLocalOf {
    ZCalExtendedColors(
        canvas = ZCalPalette.LightCanvas,
        surface = ZCalPalette.LightSurface,
        surfaceElevated = ZCalPalette.LightSurfaceElevated,
        surfaceVariant = ZCalPalette.LightSurfaceVariant,
        outline = ZCalPalette.LightOutline,
        textPrimary = ZCalPalette.LightTextPrimary,
        textSecondary = ZCalPalette.LightTextSecondary,
        primary = ZCalPalette.LightPrimary,
        onPrimary = ZCalPalette.LightOnPrimary,
        accent = ZCalPalette.LightAccent,
        onAccent = ZCalPalette.LightOnAccent,
        secondary = ZCalPalette.LightSecondary,
        onSecondary = ZCalPalette.LightOnSecondary,
        accentGlow = ZCalPalette.LightAccentGlow,
        protein = ZCalPalette.LightProtein,
        carbs = ZCalPalette.LightCarbs,
        fat = ZCalPalette.LightFat,
        error = ZCalPalette.LightError,
        isDark = false
    )
}
