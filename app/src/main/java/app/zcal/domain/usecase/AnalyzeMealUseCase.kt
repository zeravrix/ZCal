package app.zcal.domain.usecase

import app.zcal.data.ai.FoodAnalysisResult
import app.zcal.data.ai.FoodAnalyzer
import app.zcal.data.repo.MealRepository
import app.zcal.data.repo.ProfileRepository
import app.zcal.domain.model.MealAnalysis
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

/**
 * Orchestrates photo/text meal analysis through the active [FoodAnalyzer], applying
 * the user's personal calibration percentage from Settings (Section 6 Accuracy Lever #6).
 */
class AnalyzeMealUseCase @Inject constructor(
    private val foodAnalyzer: FoodAnalyzer,
    private val profileRepository: ProfileRepository,
    private val mealRepository: MealRepository
) {
    suspend fun analyzePhoto(
        imageFiles: List<File>,
        userNote: String?,
        existingAnalysisToFix: MealAnalysis? = null,
        correctionInstruction: String? = null
    ): FoodAnalysisResult {
        val profile = profileRepository.userProfileFlow.first()
        val rawResult = foodAnalyzer.analyzeMeal(
            imageFiles = imageFiles,
            userNote = userNote,
            unitSystem = profile.units,
            existingAnalysis = existingAnalysisToFix,
            correctionInstruction = correctionInstruction
        )

        return when (rawResult) {
            is FoodAnalysisResult.Success -> {
                val calibrated = applyPersonalCalibration(
                    analysis = rawResult.analysis,
                    calibrationPct = profile.personalCalibrationPct
                )
                FoodAnalysisResult.Success(
                    analysis = calibrated,
                    rawJson = rawResult.rawJson
                )
            }
            is FoodAnalysisResult.Offline -> {
                // Queue first photo as a pending offline meal (Section 6 & 11)
                imageFiles.firstOrNull()?.let { file ->
                    mealRepository.enqueuePendingOfflineScan(
                        photoPath = file.absolutePath,
                        note = userNote
                    )
                }
                rawResult
            }
            else -> rawResult
        }
    }

    private fun applyPersonalCalibration(analysis: MealAnalysis, calibrationPct: Int): MealAnalysis {
        if (calibrationPct == 0) return analysis
        val multiplier = (1.0 + (calibrationPct / 100.0)).coerceIn(0.75, 1.25)
        val calibratedItems = analysis.items.map { item ->
            item.copy(
                calories = item.calories * multiplier,
                kilojoules = item.kilojoules * multiplier,
                claMg = item.claMg * multiplier,
                proteinG = item.proteinG * multiplier,
                carbsG = item.carbsG * multiplier,
                fatG = item.fatG * multiplier,
                fiberG = item.fiberG * multiplier
            )
        }
        return analysis.copy(
            items = calibratedItems,
            caloriesLow = kotlin.math.round(analysis.caloriesLow * multiplier).toInt(),
            caloriesHigh = kotlin.math.round(analysis.caloriesHigh * multiplier).toInt()
        )
    }
}
