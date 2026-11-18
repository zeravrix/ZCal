package app.zcal.data.ai

import app.zcal.domain.model.MealAnalysis
import app.zcal.domain.model.UnitSystem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object PromptBuilder {

    const val SYSTEM_INSTRUCTION = """Food vision analyst. Strict JSON only.
1. Scale: Find any reference object in frame (exact phone/bottle/can/package model, utensil, plate, mug, hand, figurine). Recall its real size (cm), compute cm/px scale, estimate each food's 3D volume (cm³), apply void & density (g/cm³) for mass (g).
2. Items: Split foods, drinks, sauces, cooking oil. Short clean names (2-4 words).
3. Math: P*4 + netC*4 + fiber*2 + F*9 ≈ kcal (±5%); kJ = round(kcal*4.184); cla_mg = 5*ruminant_fat_g.
4. Brief assumptions (1 short phrase). No food -> is_food=false."""

    /**
     * OpenAI-compatible providers only receive `response_format: json_object` (no schema),
     * so the exact field names must be spelled out or the model invents its own.
     */
    const val JSON_SHAPE_HINT = """JSON keys: {"is_food":bool,"meal_name":str,"calories_low":int,"calories_high":int,"assumptions":[str],"items":[{"name":str,"grams":num,"calories":num,"kilojoules":num,"protein_g":num,"carbs_g":num,"fat_g":num,"fiber_g":num,"cla_mg":num,"confidence":"high"|"medium"|"low"}]}. No markdown."""

    private val compactJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun buildUserPrompt(
        userNote: String?,
        unitSystem: UnitSystem,
        existingAnalysis: MealAnalysis? = null,
        correctionInstruction: String? = null
    ): String = buildString {
        appendLine("Estimate this meal. Use any recognizable object/model in the photo for scale (cm), then compute volume and grams.")
        if (!userNote.isNullOrBlank()) {
            appendLine("User note: \"${userNote.trim()}\"")
        }
        if (existingAnalysis != null && !correctionInstruction.isNullOrBlank()) {
            appendLine("Previous JSON: ${compactJson.encodeToString(existingAnalysis)}")
            appendLine("Fix: \"${correctionInstruction.trim()}\"")
        }
        append("Units: ${unitSystem.name.lowercase()} (output g, kcal, kJ, cla_mg). JSON only.")
    }
}
