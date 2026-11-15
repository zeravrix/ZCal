package app.zcal.domain.usecase

import app.zcal.domain.model.ActivityLevel
import app.zcal.domain.model.BiologicalSex
import app.zcal.domain.model.EnergyConversion
import app.zcal.domain.model.GoalType
import app.zcal.domain.model.Goals
import app.zcal.domain.model.PeriodTargetBreakdown
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Generates exact Daily (1D), Weekly (7D), and Monthly (30D) targets for:
 * - Calories (kcal) & Kilojoules (kJ)
 * - Protein (g), Fat (g), Carbs (g), CLA (mg), and Fiber (g)
 */
class ComputeGoalsUseCase @Inject constructor() {

    operator fun invoke(
        sex: BiologicalSex,
        birthYear: Int,
        heightCm: Double,
        weightKg: Double,
        activityLevel: ActivityLevel,
        goalType: GoalType,
        weeklyChangeKg: Double,
        proteinPerKg: Double = 1.8,
        fatCalorieShare: Double = 0.25,
        currentYear: Int = LocalDate.now().year
    ): Goals {
        val age = (currentYear - birthYear).coerceIn(14, 100)

        // 1. BMR (Mifflin-St Jeor)
        val sexOffset = when (sex) {
            BiologicalSex.MALE -> 5.0
            BiologicalSex.FEMALE -> -161.0
            BiologicalSex.OTHER -> -78.0
        }
        val bmr = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age + sexOffset
        val tdee = bmr * activityLevel.factor

        val signedWeeklyChange = when (goalType) {
            GoalType.LOSE -> -kotlin.math.abs(weeklyChangeKg)
            GoalType.MAINTAIN -> 0.0
            GoalType.GAIN -> kotlin.math.abs(weeklyChangeKg)
        }

        val dailyDelta = (signedWeeklyChange * 7700.0) / 7.0
        val rawTargetCalories = (tdee + dailyDelta).roundToInt()

        val minCalorieFloor = when (sex) {
            BiologicalSex.MALE -> 1500
            BiologicalSex.FEMALE -> 1200
            BiologicalSex.OTHER -> 1350
        }
        val floorWarning = if (rawTargetCalories < minCalorieFloor) {
            "Calculated target ($rawTargetCalories kcal) is below the recommended daily floor of $minCalorieFloor kcal. Consider a gentler weekly pace."
        } else null

        val maxSafeLossKg = (weightKg * 0.01).coerceAtMost(1.0)
        val paceWarning = if (goalType == GoalType.LOSE && kotlin.math.abs(signedWeeklyChange) > maxSafeLossKg) {
            "Losing more than ${String.format(java.util.Locale.US, "%.1f", maxSafeLossKg)} kg/week increases lean muscle loss risk."
        } else null

        val clampedCalories = rawTargetCalories.coerceIn(minCalorieFloor, 6000)
        val dailyKj = EnergyConversion.kcalToKj(clampedCalories)

        val proteinG = (weightKg * proteinPerKg).roundToInt().coerceAtLeast(50)
        val proteinCalories = proteinG * 4

        val minFatG = (weightKg * 0.6).roundToInt()
        val fatG = ((clampedCalories * fatCalorieShare) / 9.0).roundToInt().coerceAtLeast(minFatG)
        val fatCalories = fatG * 9

        val remainingCalories = (clampedCalories - proteinCalories - fatCalories).coerceAtLeast(120)
        val carbsG = (remainingCalories / 4.0).roundToInt()

        // Optimal CLA (Conjugated Linoleic Acid) target: ~42 mg per kg body weight (typically 3,000 - 3,400 mg/day)
        val claMg = (weightKg * 42.0).roundToInt().coerceIn(2000, 4500)
        val fiberG = ((clampedCalories / 1000.0) * 14.0).roundToInt().coerceIn(20, 50)

        fun buildPeriod(label: String, days: Int) = PeriodTargetBreakdown(
            periodLabel = label,
            daysCount = days,
            caloriesKcal = clampedCalories * days,
            energyKj = dailyKj * days,
            proteinG = proteinG * days,
            carbsG = carbsG * days,
            fatG = fatG * days,
            claMg = claMg * days,
            fiberG = fiberG * days
        )

        return Goals(
            bmrKcal = bmr.roundToInt(),
            bmrKj = EnergyConversion.kcalToKj(bmr),
            tdeeKcal = tdee.roundToInt(),
            tdeeKj = EnergyConversion.kcalToKj(tdee),
            dailyCalories = clampedCalories,
            dailyKilojoules = dailyKj,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            claMg = claMg,
            fiberG = fiberG,
            dailyTargets = buildPeriod("Daily Plan (1 Day)", 1),
            weeklyTargets = buildPeriod("Weekly Plan (7 Days)", 7),
            monthlyTargets = buildPeriod("Monthly Plan (30 Days)", 30),
            belowClinicalFloorWarning = floorWarning,
            rapidPaceWarning = paceWarning
        )
    }
}
