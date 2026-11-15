package app.zcal.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Section 12 Page 23 Typography Scale with `tnum` (tabular figures) enabled on all styles
 * so animated calorie counters and macro grams never jitter horizontally.
 */
private const val TABULAR_NUMS = "tnum"

object ZCalTypeScale {
    // Numeral: 48 / 52 sp, weight 700 (Ring center)
    val Numeral = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        fontFeatureSettings = TABULAR_NUMS
    )

    // Display: 40 / 44 sp, weight 700 (Review total, onboarding results)
    val Display = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        fontFeatureSettings = TABULAR_NUMS
    )

    // Headline: 28 / 34 sp, weight 600 (Screen titles)
    val Headline = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontFeatureSettings = TABULAR_NUMS
    )

    // Title: 20 / 26 sp, weight 600 (Card titles, meal names)
    val Title = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontFeatureSettings = TABULAR_NUMS
    )

    // Body: 16 / 24 sp, weight 400 (Default text)
    val Body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontFeatureSettings = TABULAR_NUMS
    )

    // Body small: 14 / 20 sp, weight 400 (Secondary text)
    val BodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = TABULAR_NUMS
    )

    // Label: 12 / 16 sp, weight 500 (Chips, captions, axis labels)
    val Label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = TABULAR_NUMS
    )
}

val ZCalMaterialTypography = Typography(
    displayLarge = ZCalTypeScale.Numeral,
    displayMedium = ZCalTypeScale.Display,
    headlineMedium = ZCalTypeScale.Headline,
    titleLarge = ZCalTypeScale.Title,
    bodyLarge = ZCalTypeScale.Body,
    bodyMedium = ZCalTypeScale.BodySmall,
    labelMedium = ZCalTypeScale.Label
)
