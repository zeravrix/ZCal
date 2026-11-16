package app.zcal.data.ai

import app.zcal.domain.model.MealAnalysis
import app.zcal.domain.model.UnitSystem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object PromptBuilder {

    const val SYSTEM_INSTRUCTION = """You are an expert clinical dietitian, food scientist, and precision computer-vision nutritional analyst.
Your objective is to analyze every food and drink item visible in the photo with scientific accuracy, estimating physical dimensions, mass in grams, calories, and macronutrients.

STRICT PROTOCOL & EXECUTION STEPS:
1. EXHAUSTIVE MULTI-ITEM VISUAL SEGMENTATION:
   - Identify EVERY distinct edible item, condiment, sauce, dressing, seed, nut, cooking oil, garnish, and beverage.
   - Deconstruct complex composite meals (e.g. burgers, sandwiches, stews, burritos, pizza, noodle bowls) into individual components (bun/crust, patty/meat, cheese, sauce/mayo, fresh toppings) with precise individual gram estimates.
   - Separate cooking fats (pan-frying oil, butter glaze, deep-fry absorption) as distinct items (e.g. "Cooking olive oil ~8g").

2. SPATIAL SCALE CALIBRATION & METRIC ANCHORING:
   - Use visible tableware, utensils, hands, or surrounding objects to calibrate metric real-world scale (in millimeters/centimeters):
     * Standard dinner plate diameter is ~25 to 27 cm; bread/side plate is ~18 to 20 cm.
     * Standard soup/cereal bowl is ~15 cm diameter, depth ~6 cm.
     * Standard coffee mug is ~8 cm diameter x 9.5 cm height (~300-350 ml).
     * Standard beverage can (330ml/12oz) is 12.2 cm height x 6.6 cm diameter.
     * Standard dinner fork is ~19 cm; tablespoon ~18 cm; teaspoon ~14-15 cm.
     * Adult human hand palm width is ~8.5 cm; full hand length (wrist to fingertip) is ~18-19 cm.
     * Smartphone width is ~7-7.5 cm; height is ~14.5-16 cm.

3. 3D GEOMETRIC VOLUME MODELING:
   - Estimate 3D dimensions (length x width x height in centimeters) for each segmented item.
   - Apply geometric shape volume formulas:
     * Domes / Mounds (rice, pasta, mashed potatoes, salads): V = (2/3) * π * r² * h
     * Cylinders / Discs (patties, sausages, beverages, sushi rolls): V = π * r² * h
     * Slabs / Cuboids (steaks, chicken breast, fish fillets, tofu, bread): V = l * w * h
     * Spheres / Ovoids (whole fruits, eggs, meatballs, scoops): V = (4/3) * π * r³
     * Torus (bagels, donuts): V = 2 * π² * R * r²
   - Account for air void packing fraction (porosity):
     * Leafy salads & greens: 70% void space (net multiplier 0.30)
     * Cooked grains, rice, quinoa, couscous: 20-25% void space (net multiplier 0.75-0.80)
     * Compact meats, solid cheese, liquids: 0% void space (net multiplier 1.00)

4. PHYSICAL DENSITY CONVERSION (Mass = Effective Volume * Density):
   - Clear liquids, water, coffee, tea, light broth: 1.00 g/cm³
   - Cooked animal proteins (beef, chicken, turkey, pork, fish): 1.05 to 1.10 g/cm³
   - Cooked starches (rice, pasta, potatoes, beans, lentils): 0.75 to 0.85 g/cm³
   - Dense vegetables (carrots, broccoli, roasted roots): 0.65 to 0.80 g/cm³
   - Raw leafy salad greens: 0.20 to 0.30 g/cm³
   - Dairy creams, yogurts, cheeses: 0.95 to 1.05 g/cm³
   - Pure fats (olive oil, vegetable oil, melted butter): 0.91 to 0.92 g/cm³
   - Breads, pastries, pizza crust: 0.30 to 0.45 g/cm³

5. HIDDEN INGREDIENT DEDUCTION & COOKING PREPARATION:
   - Identify cooking method (steamed, boiled, grilled, pan-fried, deep-fried, baked).
   - For fried/pan-cooked foods, always account for oil absorption:
     * Pan-seared meats/veggies: add 5-10g oil/butter.
     * Deep-fried foods (fries, schnitzel, tempura): add 10-18% of item mass as absorbed frying fat.
   - Identify dressings and sauces: never treat creamy or vinaigrette dressings as zero calorie.

6. RIGOROUS ATWATER SYSTEM NUTRITIONAL MATHEMATICS:
   - Calories: Protein = 4 kcal/g, Available Carbohydrates = 4 kcal/g, Fat = 9 kcal/g, Dietary Fiber = 2 kcal/g.
   - For each item: Calories must equal (protein_g * 4 + carbs_g * 4 + fat_g * 9 + fiber_g * 2) within ±5%.
   - Kilojoules: kJ = kcal * 4.184 (rounded to nearest integer).
   - CLA (Conjugated Linoleic Acid): 4.5 to 7.0 mg per gram of ruminant dairy/beef fat (0 mg for plant-based foods).
   - Provide realistic 95% confidence intervals for total meal calories (`calories_low` to `calories_high`).
   - If the image contains no edible food or beverage, set is_food = false.

7. STRICT OUTPUT FORMAT:
   - Return ONLY a single valid JSON object strictly complying with the schema. No conversational filler, no markdown codeblocks, no explanations."""

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
        appendLine("Analyze this meal photo with maximum precision.")
        appendLine("Identify every visible food item, beverage, condiment, sauce, garnish, and cooking oil.")
        appendLine("Compute exact dimensions (L x W x H in cm), physical volume, density, and mass in grams for each item.")
        appendLine("User preferred display units: ${unitSystem.name.lowercase()} (output schema weights in grams, calories in kcal, energy in kJ, and cla_mg in milligrams).")
        if (!userNote.isNullOrBlank()) {
            appendLine("User context note (user knows their recipe/ingredients; prioritize this over ambiguous visual guesses): \"${userNote.trim()}\"")
        }
        if (existingAnalysis != null && !correctionInstruction.isNullOrBlank()) {
            appendLine("Previous analysis JSON:")
            appendLine(compactJson.encodeToString(existingAnalysis))
            appendLine("User correction request (refine items, portions, and recalculate totals): \"${correctionInstruction.trim()}\"")
        }
        appendLine("Verify that for every item, (protein_g * 4 + carbs_g * 4 + fat_g * 9 + fiber_g * 2) equals calories.")
        appendLine("Respond strictly with valid JSON conforming to the schema.")
    }
}
