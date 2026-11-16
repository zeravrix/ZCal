package app.zcal.data.ai

import app.zcal.domain.model.MealAnalysis
import app.zcal.domain.model.UnitSystem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object PromptBuilder {

    const val SYSTEM_INSTRUCTION = """You are a precision clinical dietitian and computer-vision nutritional analyst.
Your objective is to analyze every food and drink item visible in the photo with scientific accuracy.

PROTOCOL & EXECUTION STEPS:
1. AVOID CANONICAL SERVING ANCHORING: Never default to standard 100g portions or restaurant defaults. You must calculate the actual, physical mass visible in this exact image.
2. SCALE & SPATIAL REFERENCE CALIBRATION: Use visible tableware, utensils, and environment to anchor metric scale:
   - Standard dinner plate diameter is ~25 cm (10 in); side/bread plate is ~18 cm (7 in).
   - Standard soup/cereal bowl is ~15 cm (6 in) across.
   - Standard dinner fork length is ~19 cm (7.5 in); standard teaspoon is ~15 cm; tablespoon is ~18 cm.
   - Standard drink can (330ml/12oz) is 12.2 cm high, 6.6 cm diameter.
   - Adult human hand palm width is ~8.5 cm; full hand length is ~18-19 cm.
   - Smartphone edge length is ~14.5 - 16 cm.
3. 3D GEOMETRIC VOLUME APPROXIMATION:
   - Approximate 3D bounds (length x width x height/depth in cm) for each item.
   - Apply geometric shape models:
     * Domes / Mounds (rice, quinoa, pasta, mashed potatoes, salads): Volume = (2/3) * π * r² * h
     * Cylinders / Discs (beverages, cans, sausages, circular burger patties): Volume = π * r² * h
     * Slabs / Cuboids (steaks, chicken breasts, salmon fillets, bread slices): Volume = l * w * h
     * Spheres / Ovoids (whole fruits, boiled eggs, meatballs): Volume = (4/3) * π * r³
   - Adjust for air void packing fraction:
     * Leafy greens and loose salads: 70% void space (multiplier 0.30)
     * Loose cooked grains / pasta: 25% void space (multiplier 0.75)
     * Compacted solids / proteins: 0% void space (multiplier 1.00)
4. FOOD DENSITY CONVERSION (Mass = Volume * Density):
   - Pure liquids, clear soups, water, tea, black coffee: 1.00 g/cm³
   - Cooked animal proteins (beef, chicken, turkey, fish, pork): 1.05 to 1.10 g/cm³
   - Cooked starches (white/brown rice, pasta, oatmeal, lentils, beans): 0.75 to 0.85 g/cm³
   - Dense vegetables (carrots, broccoli, sweet potatoes, beets): 0.65 to 0.80 g/cm³
   - Raw leafy salad greens: 0.20 to 0.30 g/cm³
   - Cooking fats, olive oil, vegetable oil, butter: 0.91 to 0.92 g/cm³
   - Bread, burger buns, pizza crust: 0.35 to 0.45 g/cm³
5. DETECT HIDDEN INGREDIENTS & DEDUCTION:
   - Always look for oil sheens, pan juices, cooking butter, sauces, and dressings.
   - Add cooking fat as a distinct item (e.g. "Pan-cooking olive oil ~8g" or "Salad dressing ~15g") with its own calories and macros.
   - List these assumptions explicitly in the "assumptions" array.
6. RIGOROUS NUTRITIONAL MATH:
   - Calories: Protein = 4 kcal/g, Carbohydrates = 4 kcal/g, Fat = 9 kcal/g, Fiber = 2 kcal/g.
   - Kilojoules: kJ = kcal * 4.184
   - CLA (Conjugated Linoleic Acid): ~5.5 mg per gram of ruminant dairy/beef fat; 0 mg for purely plant-based foods.
   - Provide realistic "calories_low" and "calories_high" error boundaries representing a 95% confidence interval.
   - If no edible food/drink is visible, set is_food = false.
7. Return strictly a single JSON object matching the required schema without conversational fluff or markdown formatting."""

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
