package app.zcal.domain.usecase

import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Implements Section 8 / Page 15 Daily Score (0 to 10):
 * - Labelled as a "Daily Score", never a medical health rating.
 * - Calories within 10% of goal earn up to 5 points, scaling linearly down to 0 at 40% off.
 * - Protein earns 3 points times the share of goal reached, capped at 1.0 (3 pts max).
 * - Fat and carbs each within 20% of target earn 1 point (up to 2 pts total).
 */
class DailyScoreUseCase @Inject constructor() {

    data class ScoreBreakdown(
        val totalScore: Double, // 0.0 to 10.0
        val displayScore: Int,  // 0 to 10
        val caloriePoints: Double,
        val proteinPoints: Double,
        val carbsPoints: Double,
        val fatPoints: Double,
        val summaryLabel: String
    )

    operator fun invoke(
        eatenCalories: Int,
        goalCalories: Int,
        eatenProteinG: Double,
        goalProteinG: Int,
        eatenCarbsG: Double,
        goalCarbsG: Int,
        eatenFatG: Double,
        goalFatG: Int
    ): ScoreBreakdown {
        if (eatenCalories <= 0 || goalCalories <= 0) {
            return ScoreBreakdown(
                totalScore = 0.0,
                displayScore = 0,
                caloriePoints = 0.0,
                proteinPoints = 0.0,
                carbsPoints = 0.0,
                fatPoints = 0.0,
                summaryLabel = "Log a meal to see today's balance"
            )
        }

        // 1. Calorie score (0..5 points)
        val calErrorRatio = abs(eatenCalories - goalCalories).toDouble() / goalCalories.toDouble()
        val caloriePoints = when {
            calErrorRatio <= 0.10 -> 5.0
            calErrorRatio >= 0.40 -> 0.0
            else -> 5.0 * (1.0 - (calErrorRatio - 0.10) / 0.30)
        }

        // 2. Protein score (0..3 points)
        val proteinShare = if (goalProteinG > 0) {
            (eatenProteinG / goalProteinG.toDouble()).coerceIn(0.0, 1.0)
        } else 1.0
        val proteinPoints = 3.0 * proteinShare

        // 3. Carbs score (0..1 point) - full point within 20%, partial down to 40%
        val carbsError = if (goalCarbsG > 0) abs(eatenCarbsG - goalCarbsG) / goalCarbsG else 0.0
        val carbsPoints = when {
            carbsError <= 0.20 -> 1.0
            carbsError >= 0.40 -> 0.0
            else -> 1.0 - (carbsError - 0.20) / 0.20
        }

        // 4. Fat score (0..1 point) - full point within 20%, partial down to 40%
        val fatError = if (goalFatG > 0) abs(eatenFatG - goalFatG) / goalFatG else 0.0
        val fatPoints = when {
            fatError <= 0.20 -> 1.0
            fatError >= 0.40 -> 0.0
            else -> 1.0 - (fatError - 0.20) / 0.20
        }

        val rawTotal = (caloriePoints + proteinPoints + carbsPoints + fatPoints).coerceIn(0.0, 10.0)
        val roundedOneDec = (rawTotal * 10.0).roundToInt() / 10.0
        val displayInt = rawTotal.roundToInt()

        val label = when {
            roundedOneDec >= 8.5 -> "Right on target today"
            roundedOneDec >= 6.5 -> "Solid macro balance"
            roundedOneDec >= 4.0 -> "Building momentum"
            else -> "Day in progress"
        }

        return ScoreBreakdown(
            totalScore = roundedOneDec,
            displayScore = displayInt,
            caloriePoints = caloriePoints,
            proteinPoints = proteinPoints,
            carbsPoints = carbsPoints,
            fatPoints = fatPoints,
            summaryLabel = label
        )
    }
}
