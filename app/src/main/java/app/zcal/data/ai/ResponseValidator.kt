package app.zcal.data.ai

import app.zcal.domain.model.AnalyzedFoodItemDto
import app.zcal.domain.model.ConfidenceLevel
import app.zcal.domain.model.EnergyConversion
import app.zcal.domain.model.MealAnalysis
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.roundToInt

object ResponseValidator {

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    sealed class ValidationResult {
        data class Valid(val analysis: MealAnalysis, val cleanedJson: String) : ValidationResult()
        data object NotFood : ValidationResult()
        data class Malformed(val reason: String) : ValidationResult()
    }

    fun parseAndValidate(rawResponseText: String): ValidationResult {
        val cleaned = extractJsonObject(rawResponseText)
        if (cleaned.isBlank()) {
            return ValidationResult.Malformed("Empty response from AI model.")
        }

        val parsed = try {
            lenientJson.decodeFromString<MealAnalysis>(cleaned)
        } catch (e: Exception) {
            return ValidationResult.Malformed("JSON parse error: ${e.message}")
        }

        if (!parsed.isFood) {
            return ValidationResult.NotFood
        }

        if (parsed.items.isEmpty()) {
            return ValidationResult.Malformed("Model returned is_food=true but items list was empty.")
        }

        val validatedItems = parsed.items.mapNotNull { item ->
            sanitizeAndReconcileItem(item)
        }

        if (validatedItems.isEmpty()) {
            return ValidationResult.Malformed("All returned items had zero or invalid weights.")
        }

        val totalKcal = validatedItems.sumOf { it.calories.roundToInt() }

        val weightedSpread = validatedItems.sumOf { item ->
            item.calories * item.confidence.rangeFraction
        }.roundToInt().coerceAtLeast(15)

        val validLow = if (parsed.caloriesLow in 1..totalKcal) {
            parsed.caloriesLow
        } else {
            (totalKcal - weightedSpread).coerceAtLeast(0)
        }

        val validHigh = if (parsed.caloriesHigh >= totalKcal && parsed.caloriesHigh > validLow) {
            parsed.caloriesHigh
        } else {
            totalKcal + weightedSpread
        }

        val normalizedMealName = parsed.mealName.trim().ifEmpty {
            validatedItems.take(2).joinToString(" & ") { it.name }
        }

        return ValidationResult.Valid(
            analysis = parsed.copy(
                mealName = normalizedMealName,
                items = validatedItems,
                caloriesLow = validLow,
                caloriesHigh = validHigh
            ),
            cleanedJson = cleaned
        )
    }

    private fun sanitizeAndReconcileItem(raw: AnalyzedFoodItemDto): AnalyzedFoodItemDto? {
        val name = raw.name.trim()
        if (name.isEmpty()) return null

        val grams = raw.grams.coerceIn(1.0, 5000.0)
        val protein = raw.proteinG.coerceIn(0.0, 500.0)
        val carbs = raw.carbsG.coerceIn(0.0, 1000.0)
        val fat = raw.fatG.coerceIn(0.0, 500.0)
        val fiber = raw.fiberG.coerceIn(0.0, carbs)

        val netCarbs = (carbs - fiber).coerceAtLeast(0.0)
        val atwaterCalories = (4.0 * protein) + (4.0 * netCarbs) + (2.0 * fiber) + (9.0 * fat)
        val reportedCalories = raw.calories.coerceIn(0.0, 8000.0)

        val reconciledCalories = if (reportedCalories <= 0.0 && atwaterCalories > 0.0) {
            atwaterCalories
        } else if (atwaterCalories > 5.0) {
            val discrepancyRatio = abs(reportedCalories - atwaterCalories) / atwaterCalories
            if (discrepancyRatio > 0.15) atwaterCalories else reportedCalories
        } else {
            reportedCalories
        }

        val finalKcal = reconciledCalories.roundToInt().toDouble()
        val finalKj = EnergyConversion.kcalToKj(finalKcal).toDouble()

        // Estimate natural CLA (mg) from fat content if the model returned 0 (~6 mg CLA per gram of fat on average)
        val reconciledClaMg = if (raw.claMg > 0.0) {
            raw.claMg.coerceIn(0.0, 10000.0)
        } else {
            (fat * 6.5 * 10.0).roundToInt() / 10.0
        }

        return raw.copy(
            name = name,
            grams = (grams * 10.0).roundToInt() / 10.0,
            calories = finalKcal,
            kilojoules = finalKj,
            proteinG = (protein * 10.0).roundToInt() / 10.0,
            carbsG = (carbs * 10.0).roundToInt() / 10.0,
            fatG = (fat * 10.0).roundToInt() / 10.0,
            claMg = reconciledClaMg,
            fiberG = (fiber * 10.0).roundToInt() / 10.0
        )
    }

    private fun extractJsonObject(raw: String): String {
        val trimmed = raw.trim()
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        return if (firstBrace >= 0 && lastBrace > firstBrace) {
            trimmed.substring(firstBrace, lastBrace + 1)
        } else {
            trimmed
        }
    }
}
