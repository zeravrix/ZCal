package app.zcal

import app.zcal.data.ai.ResponseValidator
import app.zcal.domain.model.ActivityLevel
import app.zcal.domain.model.BiologicalSex
import app.zcal.domain.model.EnergyConversion
import app.zcal.domain.model.GoalType
import app.zcal.data.ai.ModelSelector
import app.zcal.data.ai.PromptBuilder
import app.zcal.domain.model.AiProviderType
import app.zcal.domain.model.UnitSystem
import app.zcal.domain.usecase.AdaptiveTdeeUseCase
import app.zcal.domain.usecase.ComputeGoalsUseCase
import app.zcal.domain.usecase.DailyScoreUseCase
import app.zcal.util.DateHelpers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ZCalUnitTests {

    private val computeGoals = ComputeGoalsUseCase()
    private val dailyScore = DailyScoreUseCase()

    @Test
    fun `test Day Week and Month Goal Generator with kcal kJ Protein Fat Carbs and CLA`() {
        val goals = computeGoals(
            sex = BiologicalSex.MALE,
            birthYear = 2026 - 28,
            heightCm = 178.0,
            weightKg = 75.0,
            activityLevel = ActivityLevel.MODERATE,
            goalType = GoalType.LOSE,
            weeklyChangeKg = 0.5,
            currentYear = 2026
        )

        assertEquals(1728, goals.bmrKcal)
        assertEquals(2678, goals.tdeeKcal)
        assertEquals(EnergyConversion.kcalToKj(goals.dailyCalories), goals.dailyKilojoules)

        // Verify Weekly (7x) and Monthly (30x) generated targets
        assertEquals(goals.dailyCalories * 7, goals.weeklyTargets.caloriesKcal)
        assertEquals(goals.dailyCalories * 30, goals.monthlyTargets.caloriesKcal)
        assertEquals(goals.proteinG * 7, goals.weeklyTargets.proteinG)
        assertEquals(goals.proteinG * 30, goals.monthlyTargets.proteinG)
        assertTrue(goals.claMg >= 2000)
        assertEquals(goals.claMg * 30, goals.monthlyTargets.claMg)
    }

    @Test
    fun `test ResponseValidator reconciles Atwater macros and populates kJ and CLA`() {
        val sampleJson = """
            {
              "is_food": true,
              "meal_name": "Grilled Salmon & Rice",
              "items": [
                {
                  "name": "Grilled salmon and rice",
                  "grams": 320,
                  "calories": 900,
                  "protein_g": 30,
                  "carbs_g": 40,
                  "fat_g": 10,
                  "fiber_g": 0,
                  "confidence": "high"
                }
              ],
              "calories_low": 0,
              "calories_high": 0,
              "assumptions": ["Assumed 1 tsp olive oil"]
            }
        """

        val res = ResponseValidator.parseAndValidate(sampleJson)
        assertTrue(res is ResponseValidator.ValidationResult.Valid)
        val valid = res as ResponseValidator.ValidationResult.Valid
        val item = valid.analysis.items.first()
        assertEquals(370.0, item.calories, 1.0)
        assertEquals(EnergyConversion.kcalToKj(370).toDouble(), item.kilojoules, 2.0)
        assertTrue(item.claMg > 0.0)
    }

    @Test
    fun `test DailyScoreUseCase awards 10 points when on target`() {
        val score = dailyScore(
            eatenCalories = 2100,
            goalCalories = 2130,
            eatenProteinG = 140.0,
            goalProteinG = 135,
            eatenCarbsG = 260.0,
            goalCarbsG = 264,
            eatenFatG = 58.0,
            goalFatG = 59
        )
        assertEquals(10.0, score.totalScore, 0.01)
    }

    @Test
    fun `PromptBuilder quotes the user note and correction safely`() {
        val prompt = PromptBuilder.buildUserPrompt(userNote = "no oil, 2 eggs", unitSystem = UnitSystem.METRIC)
        assertTrue(prompt.contains("\"no oil, 2 eggs\""))
    }

    @Test
    fun `ResponseValidator returns NotFood when is_food is false`() {
        val res = ResponseValidator.parseAndValidate(
            """{"is_food": false, "meal_name": "", "items": [], "calories_low": 0, "calories_high": 0, "assumptions": []}"""
        )
        assertTrue(res is ResponseValidator.ValidationResult.NotFood)
    }

    @Test
    fun `ResponseValidator tolerates markdown fences around JSON`() {
        val res = ResponseValidator.parseAndValidate(
            "```json\n{\"is_food\":true,\"meal_name\":\"Apple\",\"items\":[{\"name\":\"Apple\",\"grams\":180,\"calories\":95,\"protein_g\":0.5,\"carbs_g\":25,\"fat_g\":0.3,\"confidence\":\"high\"}],\"calories_low\":0,\"calories_high\":0,\"assumptions\":[]}\n```"
        )
        assertTrue(res is ResponseValidator.ValidationResult.Valid)
    }

    @Test
    fun `ModelSelector prefers Flash over Pro and Lite and the newest stable generation`() {
        val ids = listOf(
            "gemini-3.8-flash", "gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash",
            "gemini-2.5-flash-image", "gemini-2.5-flash-preview-tts", "text-embedding-004", "gemma-3-27b-it"
        )
        assertEquals("gemini-3.8-flash", ModelSelector.pickBest(AiProviderType.GEMINI, ids, allowPreview = false))
    }

    @Test
    fun `ModelSelector ranks gemini-4-hydrogen at the top of workhorse models`() {
        val ids = listOf("gemini-3.8-flash", "gemini-4-hydrogen", "gemini-2.5-flash")
        assertEquals("gemini-4-hydrogen", ModelSelector.pickBest(AiProviderType.GEMINI, ids, allowPreview = false))
    }

    @Test
    fun `AiProviderType Gemini defaults to gemini-3_8-flash fallback`() {
        assertEquals("gemini-3.8-flash", AiProviderType.GEMINI.fallbackModel)
    }

    @Test
    fun `ModelSelector uses a newer preview only when previews are allowed`() {
        val ids = listOf("gemini-2.5-flash", "gemini-3-flash-preview", "gemini-3-pro-preview")
        assertEquals("gemini-2.5-flash", ModelSelector.pickBest(AiProviderType.GEMINI, ids, allowPreview = false))
        assertEquals("gemini-3-flash-preview", ModelSelector.pickBest(AiProviderType.GEMINI, ids, allowPreview = true))
    }

    @Test
    fun `ModelSelector falls back to a preview rather than no model, and to null for unknown lists`() {
        assertEquals("gemini-3-flash-preview", ModelSelector.pickBest(AiProviderType.GEMINI, listOf("gemini-3-flash-preview"), false))
        assertEquals(null, ModelSelector.pickBest(AiProviderType.GEMINI, listOf("some-text-model"), true))
    }

    @Test
    fun `ModelSelector handles OpenAI-compatible lists and local vision models`() {
        val openAi = listOf("gpt-4o", "gpt-4o-mini", "gpt-4.1-mini", "gpt-4o-audio-preview", "whisper-1")
        assertEquals("gpt-4.1-mini", ModelSelector.pickBest(AiProviderType.OPENAI_COMPATIBLE, openAi, false))
        val router = listOf("openai/gpt-4o-mini", "google/gemini-2.5-flash")
        assertEquals("google/gemini-2.5-flash", ModelSelector.pickBest(AiProviderType.OPENAI_COMPATIBLE, router, false))
        val ollama = listOf("llama3.2:3b", "llava:7b", "qwen2.5vl:7b")
        assertEquals("qwen2.5vl:7b", ModelSelector.pickBest(AiProviderType.OPENAI_COMPATIBLE, ollama, false))
    }

    @Test
    fun `Adaptive TDEE refuses sparse data`() {
        val uc = AdaptiveTdeeUseCase()
        assertEquals(null, uc.evaluateAdaptiveAdjustment(emptyList(), emptyList(), 2500))
    }

    @Test
    fun `Streak calculation with 1 missed day forgiveness`() {
        val today = LocalDate.of(2026, 11, 14)
        val logged = setOf("2026-11-14", "2026-11-13", "2026-11-11", "2026-11-10")
        assertEquals(2, DateHelpers.computeCurrentStreak(logged, today, forgiveOneMissedDayPerWeek = false))
        assertEquals(4, DateHelpers.computeCurrentStreak(logged, today, forgiveOneMissedDayPerWeek = true))
    }
}
