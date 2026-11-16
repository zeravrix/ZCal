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

    // Light Mode Tokens (Clean Monochrome Slate)
    val LightCanvas = Color(0xFFF6F6F8)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceElevated = Color(0xFFF0F0F3)
    val LightSurfaceVariant = Color(0xFFE6E6EB)
    val LightOutline = Color(0xFFD5D5DC)
    val LightTextPrimary = Color(0xFF15141A)
    val LightTextSecondary = Color(0xFF63616E)
    val LightPrimary = Color(0xFF23212C)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightAccent = Color(0xFF32303D) // Calm Monochrome Dark Slate
    val LightOnAccent = Color(0xFFFFFFFF)
    val LightSecondary = Color(0xFF5A5868)
    val LightOnSecondary = Color(0xFFFFFFFF)
    val LightAccentGlow = Color(0x1823212C)
    val LightProtein = Color(0xFFC85A54) // Calm Muted Terracotta (Non-Neon)
    val LightCarbs = Color(0xFFB88A3B)   // Calm Warm Ochre (Non-Neon)
    val LightFat = Color(0xFF4A7AB5)     // Calm Slate Blue (Non-Neon)
    val LightError = Color(0xFFC62828)

    // Dark Mode Tokens (Obsidian Slate + Calm Off-White Monochrome Theme)
    val DarkCanvas = Color(0xFF181722)
    val AmoledCanvas = Color(0xFF000000)
    val DarkSurface = Color(0xFF23212C)
    val DarkSurfaceElevated = Color(0xFF2C2A37)
    val AmoledSurface = Color(0xFF0A090F)
    val AmoledSurfaceElevated = Color(0xFF14131A)
    val DarkSurfaceVariant = Color(0xFF353342)
    val DarkOutline = Color(0xFF3F3D4D)
    val DarkTextPrimary = Color(0xFFF2F1F5)
    val DarkTextSecondary = Color(0xFFA5A3B0)
    val DarkPrimary = Color(0xFFE6E5EA) // Calm Light White (Not pure blinding white)
    val DarkOnPrimary = Color(0xFF1E1D28)
    val DarkAccent = Color(0xFFE6E5EA)  // Calm Off-White Accent (Replacing violet)
    val DarkOnAccent = Color(0xFF1E1D28)
    val DarkSecondary = Color(0xFFD0CFD7) // Calm Light White-Grey
    val DarkOnSecondary = Color(0xFF1E1D28)
    val DarkAccentGlow = Color(0x20E6E5EA)
    val DarkProtein = Color(0xFFD6736E) // Calm Muted Terracotta (Non-Neon)
    val DarkCarbs = Color(0xFFCFA055)   // Calm Warm Ochre (Non-Neon)
    val DarkFat = Color(0xFF6B96CC)     // Calm Slate Blue (Non-Neon)
    val DarkError = Color(0xFFE57373)

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
