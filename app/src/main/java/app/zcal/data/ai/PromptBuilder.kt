package app.zcal.data.ai

import app.zcal.domain.model.MealAnalysis
import app.zcal.domain.model.UnitSystem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object PromptBuilder {

    const val SYSTEM_INSTRUCTION = """You are a careful nutrition estimator. Identify every food and drink in the photo. For each item estimate the cooked weight in grams using visible cues such as plate size, cutlery, hands and packaging, then give calories (kcal), kilojoules (kJ = kcal * 4.184), protein (g), carbs (g), fat (g), conjugated linoleic acid / healthy fatty acids cla_mg (mg), and dietary fiber (g) for that weight. Add hidden contributors you assume, such as cooking oil, butter, sauce or dressing, as their own items and list them under assumptions. If the user note adds facts, trust the note over the photo. If unsure, lower the confidence and widen the calorie range. If no food is visible, set is_food to false. Return only JSON that matches the schema."""

    /**
     * OpenAI-compatible providers only receive `response_format: json_object` (no schema),
     * so the exact field names must be spelled out or the model invents its own.
     */
    const val JSON_SHAPE_HINT = """Return a single JSON object with exactly these keys: {"is_food": boolean, "meal_name": string, "calories_low": integer, "calories_high": integer, "assumptions": [string], "items": [{"name": string, "grams": number, "calories": number, "kilojoules": number, "protein_g": number, "carbs_g": number, "fat_g": number, "fiber_g": number, "cla_mg": number, "confidence": "high"|"medium"|"low"}]}. No markdown, no prose."""

    private val compactJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun buildUserPrompt(
        userNote: String?,
        unitSystem: UnitSystem,
        existingAnalysis: MealAnalysis? = null,
        correctionInstruction: String? = null
    ): String = buildString {
        appendLine("User preferred display units: ${unitSystem.name.lowercase()} (output schema weights in grams, calories in kcal, kilojoules in kJ, and cla_mg in milligrams).")
        if (!userNote.isNullOrBlank()) {
            appendLine("User context note (trust this over visual ambiguity): \"${userNote.trim()}\"")
        }
        if (existingAnalysis != null && !correctionInstruction.isNullOrBlank()) {
            appendLine("Previous analysis JSON:")
            appendLine(compactJson.encodeToString(existingAnalysis))
            appendLine("User correction request (update items and totals accordingly): \"${correctionInstruction.trim()}\"")
        }
        appendLine("Respond strictly with valid JSON matching the schema.")
    }
}
