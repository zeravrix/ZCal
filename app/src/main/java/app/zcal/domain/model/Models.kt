package app.zcal.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

enum class BiologicalSex { MALE, FEMALE, OTHER }

enum class ActivityLevel(val factor: Double, val label: String, val description: String) {
    SEDENTARY(1.2, "Sedentary", "Desk job, little or no exercise"),
    LIGHT(1.375, "Lightly Active", "Light exercise 1–3 days/week"),
    MODERATE(1.55, "Moderately Active", "Moderate exercise 3–5 days/week"),
    ACTIVE(1.725, "Very Active", "Hard exercise 6–7 days/week"),
    EXTRA_ACTIVE(1.9, "Extra Active", "Physical job or 2x/day training")
}

enum class GoalType(val label: String) {
    LOSE("Lose weight"),
    MAINTAIN("Maintain weight"),
    GAIN("Build muscle / gain")
}

enum class UnitSystem { METRIC, IMPERIAL }

enum class MealType(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack");

    companion object {
        fun fromHourOfDay(hour: Int): MealType = when (hour) {
            in 5..10 -> BREAKFAST
            in 11..14 -> LUNCH
            in 17..21 -> DINNER
            else -> SNACK
        }
    }
}

enum class MealSource { PHOTO, BARCODE, TEXT, MANUAL }

@Serializable
enum class ConfidenceLevel {
    @SerialName("high") HIGH,
    @SerialName("medium") MEDIUM,
    @SerialName("low") LOW;

    val rangeFraction: Double
        get() = when (this) {
            HIGH -> 0.08
            MEDIUM -> 0.18
            LOW -> 0.30
        }
}

enum class AiProviderType(
    val displayName: String,
    /** "auto" = the app lists the models this key can use and picks the best one for food photos. */
    val defaultModel: String,
    /** Used only if the model list cannot be fetched or nothing in it looks suitable. */
    val fallbackModel: String,
    val defaultBaseUrl: String
) {
    GEMINI(
        displayName = "Google Gemini (AI Studio)",
        defaultModel = "auto",
        fallbackModel = "gemini-2.5-flash",
        defaultBaseUrl = "https://generativelanguage.googleapis.com"
    ),
    OPENAI_COMPATIBLE(
        displayName = "OpenAI / OpenRouter / Local Ollama",
        defaultModel = "auto",
        fallbackModel = "gpt-4o-mini",
        defaultBaseUrl = "https://api.openai.com/v1"
    )
}

enum class ThemePreference { SYSTEM, LIGHT, DARK, AMOLED }

object EnergyConversion {
    const val KCAL_TO_KJ = 4.184
    fun kcalToKj(kcal: Int): Int = (kcal * KCAL_TO_KJ).roundToInt()
    fun kcalToKj(kcal: Double): Int = (kcal * KCAL_TO_KJ).roundToInt()
}

data class PeriodTargetBreakdown(
    val periodLabel: String, // "Daily (1D)", "Weekly (7D)", "Monthly (30D)"
    val daysCount: Int,
    val caloriesKcal: Int,
    val energyKj: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val claMg: Int,
    val fiberG: Int
)

data class UserProfile(
    val sex: BiologicalSex = BiologicalSex.MALE,
    val birthYear: Int = 1998,
    val birthMonth: Int = 1,
    val birthDay: Int = 1,
    val heightCm: Double = 178.0,
    val weightKg: Double = 75.0,
    val targetWeightKg: Double = 70.0,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val goalType: GoalType = GoalType.LOSE,
    val weeklyChangeKg: Double = -0.5,
    val calorieGoal: Int = 2130,
    val kilojouleGoal: Int = 8912,
    val proteinGoalG: Int = 135,
    val carbsGoalG: Int = 264,
    val fatGoalG: Int = 59,
    val claGoalMg: Int = 3200, // Daily Conjugated Linoleic Acid (CLA) target in mg (3.2 g/day)
    val fiberGoalG: Int = 30,
    val waterGoalMl: Int = 2500,
    val stepGoal: Int = 10000,
    val units: UnitSystem = UnitSystem.METRIC,
    val personalCalibrationPct: Int = 0,
    val onboardingCompleted: Boolean = false
) {
    fun computeCurrentAge(now: java.time.LocalDate = java.time.LocalDate.now()): Int {
        val safeMonth = birthMonth.coerceIn(1, 12)
        val maxDays = try {
            java.time.YearMonth.of(birthYear, safeMonth).lengthOfMonth()
        } catch (_: Exception) { 31 }
        val safeDay = birthDay.coerceIn(1, maxDays)
        val birthDate = try {
            java.time.LocalDate.of(birthYear, safeMonth, safeDay)
        } catch (_: Exception) {
            java.time.LocalDate.of(birthYear, 1, 1)
        }
        return java.time.Period.between(birthDate, now).years.coerceAtLeast(1)
    }
}

data class Goals(
    val bmrKcal: Int,
    val bmrKj: Int,
    val tdeeKcal: Int,
    val tdeeKj: Int,
    val dailyCalories: Int,
    val dailyKilojoules: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val claMg: Int,
    val fiberG: Int,
    val dailyTargets: PeriodTargetBreakdown,
    val weeklyTargets: PeriodTargetBreakdown,
    val monthlyTargets: PeriodTargetBreakdown,
    val belowClinicalFloorWarning: String? = null,
    val rapidPaceWarning: String? = null
)

@Serializable
data class FoodItem(
    val id: Long = 0L,
    val mealId: Long = 0L,
    val name: String,
    val grams: Double,
    val calories: Int,
    val kilojoules: Int = EnergyConversion.kcalToKj(calories),
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val claMg: Double = 0.0, // Conjugated Linoleic Acid in mg
    val fiberG: Double = 0.0,
    val confidence: ConfidenceLevel = ConfidenceLevel.MEDIUM
) {
    fun rescaleToGrams(newGrams: Double): FoodItem {
        val safeNewGrams = newGrams.coerceIn(0.0, 5000.0)
        if (grams <= 0.0) return copy(grams = safeNewGrams)
        val ratio = safeNewGrams / grams
        val newCal = kotlin.math.round(calories * ratio).toInt().coerceAtLeast(0)
        return copy(
            grams = kotlin.math.round(safeNewGrams * 10.0) / 10.0,
            calories = newCal,
            kilojoules = EnergyConversion.kcalToKj(newCal),
            proteinG = kotlin.math.round(proteinG * ratio * 10.0) / 10.0,
            carbsG = kotlin.math.round(carbsG * ratio * 10.0) / 10.0,
            fatG = kotlin.math.round(fatG * ratio * 10.0) / 10.0,
            claMg = kotlin.math.round(claMg * ratio * 10.0) / 10.0,
            fiberG = kotlin.math.round(fiberG * ratio * 10.0) / 10.0
        )
    }
}

data class Meal(
    val id: Long = 0L,
    val loggedAtEpochMillis: Long,
    val localDate: String,
    val mealType: MealType,
    val name: String,
    val photoPath: String? = null,
    val source: MealSource = MealSource.PHOTO,
    val totalCalories: Int,
    val totalKilojoules: Int = EnergyConversion.kcalToKj(totalCalories),
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val claMg: Double = 0.0,
    val fiberG: Double = 0.0,
    val confidence: ConfidenceLevel = ConfidenceLevel.MEDIUM,
    val caloriesLow: Int,
    val caloriesHigh: Int,
    val note: String? = null,
    val assumptions: List<String> = emptyList(),
    val rawJson: String? = null,
    val items: List<FoodItem> = emptyList()
)

@Serializable
data class MealAnalysis(
    @SerialName("is_food") val isFood: Boolean = true,
    @SerialName("meal_name") val mealName: String = "",
    @SerialName("items") val items: List<AnalyzedFoodItemDto> = emptyList(),
    @SerialName("calories_low") val caloriesLow: Int = 0,
    @SerialName("calories_high") val caloriesHigh: Int = 0,
    @SerialName("assumptions") val assumptions: List<String> = emptyList()
)

@Serializable
data class AnalyzedFoodItemDto(
    @SerialName("name") val name: String,
    @SerialName("grams") val grams: Double,
    @SerialName("calories") val calories: Double,
    @SerialName("kilojoules") val kilojoules: Double = 0.0,
    @SerialName("protein_g") val proteinG: Double,
    @SerialName("carbs_g") val carbsG: Double,
    @SerialName("fat_g") val fatG: Double,
    @SerialName("cla_mg") val claMg: Double = 0.0,
    @SerialName("fiber_g") val fiberG: Double = 0.0,
    @SerialName("confidence") val confidence: ConfidenceLevel = ConfidenceLevel.MEDIUM
)

data class DaySummary(
    val localDate: String,
    val kcal: Int,
    val kj: Int = EnergyConversion.kcalToKj(kcal),
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val claMg: Double = 0.0,
    val fiberG: Double,
    val mealCount: Int,
    val complete: Boolean,
    val updatedAt: Long
)

data class CachedFoodHistoryItem(
    val id: Long = 0L,
    val mealId: Long,
    val yearMonthBucket: String, // "YYYY-MM" — automatically purged when month rolls over
    val localDate: String,
    val capturedAtMillis: Long,
    val mealName: String,
    val photoPath: String,
    val caloriesKcal: Int,
    val energyKj: Int,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    val claMg: Double,
    val detailsSummary: String
)

data class DailyHealthSteps(
    val steps: Int,
    val stepGoal: Int = 10000,
    val activeBurnKcal: Int,
    val activeBurnKj: Int,
    val sourceLabel: String // "Health Connect" or "On-Device Step Sensor"
)

data class WeightEntry(
    val id: Long = 0L,
    val localDate: String,
    val weightKg: Double
)

data class WeightPointWithTrend(
    val localDate: String,
    val rawWeightKg: Double,
    val ewmaWeightKg: Double
)
