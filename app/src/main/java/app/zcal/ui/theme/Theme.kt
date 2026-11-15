package app.zcal.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import app.zcal.domain.model.ThemePreference

@Composable
fun ZCalTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    dynamicColorEnabled: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themePreference) {
        ThemePreference.SYSTEM -> systemDark
        ThemePreference.LIGHT -> false
        ThemePreference.DARK, ThemePreference.AMOLED -> true
    }
    val useAmoled = themePreference == ThemePreference.AMOLED

    val extendedColors = if (useDark) {
        ZCalExtendedColors(
            canvas = if (useAmoled) ZCalPalette.AmoledCanvas else ZCalPalette.DarkCanvas,
            surface = if (useAmoled) ZCalPalette.AmoledSurface else ZCalPalette.DarkSurface,
            surfaceVariant = ZCalPalette.DarkSurfaceVariant,
            outline = ZCalPalette.DarkOutline,
            textPrimary = ZCalPalette.DarkTextPrimary,
            textSecondary = ZCalPalette.DarkTextSecondary,
            primary = ZCalPalette.DarkPrimary,
            onPrimary = ZCalPalette.DarkOnPrimary,
            accent = ZCalPalette.DarkAccent,
            protein = ZCalPalette.DarkProtein, // Macro colors never change with wallpaper colors
            carbs = ZCalPalette.DarkCarbs,
            fat = ZCalPalette.DarkFat,
            error = ZCalPalette.DarkError,
            isDark = true
        )
    } else {
        ZCalExtendedColors(
            canvas = ZCalPalette.LightCanvas,
            surface = ZCalPalette.LightSurface,
            surfaceVariant = ZCalPalette.LightSurfaceVariant,
            outline = ZCalPalette.LightOutline,
            textPrimary = ZCalPalette.LightTextPrimary,
            textSecondary = ZCalPalette.LightTextSecondary,
            primary = ZCalPalette.LightPrimary,
            onPrimary = ZCalPalette.LightOnPrimary,
            accent = ZCalPalette.LightAccent,
            protein = ZCalPalette.LightProtein,
            carbs = ZCalPalette.LightCarbs,
            fat = ZCalPalette.LightFat,
            error = ZCalPalette.LightError,
            isDark = false
        )
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> darkColorScheme(
            primary = extendedColors.primary,
            onPrimary = extendedColors.onPrimary,
            background = extendedColors.canvas,
            onBackground = extendedColors.textPrimary,
            surface = extendedColors.surface,
            onSurface = extendedColors.textPrimary,
            surfaceVariant = extendedColors.surfaceVariant,
            onSurfaceVariant = extendedColors.textSecondary,
            outline = extendedColors.outline,
            error = extendedColors.error
        )
        else -> lightColorScheme(
            primary = extendedColors.primary,
            onPrimary = extendedColors.onPrimary,
            background = extendedColors.canvas,
            onBackground = extendedColors.textPrimary,
            surface = extendedColors.surface,
            onSurface = extendedColors.textPrimary,
            surfaceVariant = extendedColors.surfaceVariant,
            onSurfaceVariant = extendedColors.textSecondary,
            outline = extendedColors.outline,
            error = extendedColors.error
        )
    }

    CompositionLocalProvider(LocalZCalColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ZCalMaterialTypography,
            shapes = MaterialShapes,
            content = content
        )
    }
}
