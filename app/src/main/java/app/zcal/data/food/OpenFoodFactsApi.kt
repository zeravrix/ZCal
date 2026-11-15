package app.zcal.data.food

import app.zcal.domain.model.ConfidenceLevel
import app.zcal.domain.model.FoodItem
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Section 10 Open Food Facts Compliance:
 * - Data is licensed under ODbL (attributed in Settings > About).
 * - Sends mandatory custom User-Agent header: `ZCal/1.0.0 (opensource@zcal.app)`.
 * - Extracts per-serving or per-100g nutriments and marks confidence as HIGH (known label).
 */
@Singleton
class OpenFoodFactsApi @Inject constructor(
    private val httpClient: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        private const val USER_AGENT = "ZCal/1.0.0 (https://github.com/U1M/ZCal)"
    }

    suspend fun lookupBarcode(barcode: String): FoodItem? {
        val cleanCode = barcode.filter { it.isDigit() }
        if (cleanCode.length < 8) return null

        return try {
            val url = "https://world.openfoodfacts.org/api/v2/product/$cleanCode.json" +
                "?fields=product_name,brands,serving_quantity,nutriments"
            val response = httpClient.get(url) {
                header("User-Agent", USER_AGENT)
            }
            if (response.status.value !in 200..299) return null

            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val status = root["status"]?.jsonPrimitive?.intOrNull ?: 0
            if (status != 1) return null

            val product = root["product"]?.jsonObject ?: return null
            val rawName = product["product_name"]?.jsonPrimitive?.content?.trim().orEmpty()
            val brand = product["brands"]?.jsonPrimitive?.content?.split(",")?.firstOrNull()?.trim().orEmpty()
            val displayName = when {
                rawName.isNotEmpty() && brand.isNotEmpty() -> "$rawName ($brand)"
                rawName.isNotEmpty() -> rawName
                else -> "Packaged Food ($cleanCode)"
            }

            val servingGrams = product["serving_quantity"]?.jsonPrimitive?.doubleOrNull
                ?.takeIf { it in 5.0..1000.0 } ?: 100.0
            val ratio = servingGrams / 100.0

            val nutriments = product["nutriments"]?.jsonObject ?: return null
            val kcal100g = nutriments["energy-kcal_100g"]?.jsonPrimitive?.doubleOrNull
                ?: nutriments["energy-kcal"]?.jsonPrimitive?.doubleOrNull
                ?: nutriments["energy_100g"]?.jsonPrimitive?.doubleOrNull?.div(4.184) // kJ-only labels
                ?: 0.0
            val protein100g = nutriments["proteins_100g"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val carbs100g = nutriments["carbohydrates_100g"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val fat100g = nutriments["fat_100g"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val fiber100g = nutriments["fiber_100g"]?.jsonPrimitive?.doubleOrNull ?: 0.0

            FoodItem(
                name = displayName,
                grams = servingGrams,
                calories = (kcal100g * ratio).roundToInt(),
                proteinG = ((protein100g * ratio) * 10.0).roundToInt() / 10.0,
                carbsG = ((carbs100g * ratio) * 10.0).roundToInt() / 10.0,
                fatG = ((fat100g * ratio) * 10.0).roundToInt() / 10.0,
                fiberG = ((fiber100g * ratio) * 10.0).roundToInt() / 10.0,
                confidence = ConfidenceLevel.HIGH
            )
        } catch (e: Exception) {
            null
        }
    }
}
