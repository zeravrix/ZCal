package app.zcal.domain.usecase

import app.zcal.domain.model.DaySummary
import app.zcal.domain.model.WeightEntry
import app.zcal.domain.model.WeightPointWithTrend
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Optimization (Section 3 & 6):
 * Computes 7-day EWMA smoothed weight trend and estimates actual empirical TDEE
 * from logged calories vs. smoothed weight change over 14+ logged days to catch
 * systematic under-logging or metabolic adaptation.
 */
class AdaptiveTdeeUseCase @Inject constructor() {

    data class AdaptiveRecommendation(
        val empiricalTdeeKcal: Int,
        val formulaTdeeKcal: Int,
        val suggestedCalorieAdjustment: Int,
        val confidenceDays: Int,
        val explanation: String
    )

    fun computeSmoothedWeightSeries(entries: List<WeightEntry>): List<WeightPointWithTrend> {
        val sorted = entries.sortedBy { it.localDate }
        if (sorted.isEmpty()) return emptyList()
        val alpha = 0.25 // 7-day smoothing equivalent
        var ewma = sorted.first().weightKg
        return sorted.map { entry ->
            ewma = alpha * entry.weightKg + (1.0 - alpha) * ewma
            WeightPointWithTrend(
                localDate = entry.localDate,
                rawWeightKg = entry.weightKg,
                ewmaWeightKg = (ewma * 100.0).roundToInt() / 100.0
            )
        }
    }

    fun evaluateAdaptiveAdjustment(
        daySummaries: List<DaySummary>,
        weightEntries: List<WeightEntry>,
        formulaTdeeKcal: Int
    ): AdaptiveRecommendation? {
        val smoothedWeights = computeSmoothedWeightSeries(weightEntries)
        if (smoothedWeights.size < 4) return null

        // The weight change and the food log must cover the SAME calendar window. The old version divided the
        // weight change by the number of logged days, which overstates the daily balance whenever days are skipped.
        val firstWeightDate = smoothedWeights.first().localDate
        val lastWeightDate = smoothedWeights.last().localDate
        val spanDays = (java.time.temporal.ChronoUnit.DAYS.between(
            java.time.LocalDate.parse(firstWeightDate), java.time.LocalDate.parse(lastWeightDate)
        )).toInt()
        val completeDays = daySummaries
            .filter { it.complete && it.mealCount > 0 && it.localDate in firstWeightDate..lastWeightDate }
            .sortedBy { it.localDate }
        // Need two weeks of calendar time AND dense logging inside it, otherwise the estimate is noise.
        if (spanDays < 14 || completeDays.size < 10 || completeDays.size < spanDays * 0.6) return null

        val avgLoggedKcal = completeDays.map { it.kcal }.average()
        val firstWeight = smoothedWeights.first().ewmaWeightKg
        val lastWeight = smoothedWeights.last().ewmaWeightKg
        val weightDeltaKg = lastWeight - firstWeight

        // Energy balance: TDEE = AvgIntake - (DeltaWeightKg * 7700 / spanDays)
        val dailyEnergyBalanceDelta = (weightDeltaKg * 7700.0) / spanDays.toDouble()
        val empiricalTdee = (avgLoggedKcal - dailyEnergyBalanceDelta).roundToInt().coerceIn(1100, 5500)
        val diff = (empiricalTdee - formulaTdeeKcal).coerceIn(-400, 400)

        return AdaptiveRecommendation(
            empiricalTdeeKcal = empiricalTdee,
            formulaTdeeKcal = formulaTdeeKcal,
            suggestedCalorieAdjustment = diff,
            confidenceDays = completeDays.size,
            explanation = "Based on ${completeDays.size} complete days and your smoothed weight trend (${String.format(java.util.Locale.US, "%+.2f", weightDeltaKg)} kg), your empirical maintenance is ~$empiricalTdee kcal/day."
        )
    }
}
