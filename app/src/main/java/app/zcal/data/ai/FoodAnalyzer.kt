package app.zcal.data.ai

import app.zcal.domain.model.MealAnalysis
import app.zcal.domain.model.UnitSystem
import java.io.File

/**
 * Section 6 Error & Result taxonomy:
 * - Every error state maps directly to a clear user recovery action (Retry, Key screen, or Manual Entry).
 */
sealed class FoodAnalysisResult {
    data class Success(val analysis: MealAnalysis, val rawJson: String) : FoodAnalysisResult()
    data object NotFood : FoodAnalysisResult()
    data class InvalidKey(val message: String) : FoodAnalysisResult()
    data class RateLimited(val retryAfterSeconds: Int = 60) : FoodAnalysisResult()
    data class Offline(val savedPendingPhotoPath: String?) : FoodAnalysisResult()
    data class Timeout(val message: String) : FoodAnalysisResult()
    data class MalformedResponse(val message: String) : FoodAnalysisResult()
    data class UnknownError(val message: String) : FoodAnalysisResult()
}

/** Models a key can call: [recommended] is ranked best-for-food-photos first; [others] is everything else usable. */
data class ModelChoices(val recommended: List<String>, val others: List<String>)

sealed class KeyVerificationResult {
    data class Valid(val latencyMs: Long, val modelUsed: String) : KeyVerificationResult()
    data class Invalid(val errorMessage: String) : KeyVerificationResult()
}

/**
 * Section 5 & 6 Core Abstraction:
 * Putting every AI provider behind [FoodAnalyzer] keeps the rest of the app 100% agnostic
 * of whether the user chose Google Gemini, OpenAI, OpenRouter, or a local Ollama server.
 */
interface FoodAnalyzer {
    suspend fun analyzeMeal(
        imageFiles: List<File>,
        userNote: String?,
        unitSystem: UnitSystem,
        existingAnalysis: MealAnalysis? = null,
        correctionInstruction: String? = null
    ): FoodAnalysisResult

    suspend fun verifyApiKey(
        candidateKey: String,
        modelId: String,
        baseUrl: String
    ): KeyVerificationResult

    /** Lists the models the key can use. A blank [candidateKey] means "use the stored key". Null if unreachable. */
    suspend fun listAvailableModels(candidateKey: String, baseUrl: String): ModelChoices?
}
