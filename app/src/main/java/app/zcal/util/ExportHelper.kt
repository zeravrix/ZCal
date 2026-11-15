package app.zcal.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.zcal.domain.model.Meal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Data ownership: exports the user's full meal and food item history as CSV or JSON
 * and opens the Android Share Sheet.
 */
object ExportHelper {

    private val prettyJson = Json { prettyPrint = true; encodeDefaults = true }

    /** RFC 4180 quoting, plus a leading apostrophe so spreadsheets never run `=`/`+`/`-`/`@` cells as formulas. */
    private fun csvCell(raw: String): String {
        val guarded = if (raw.isNotEmpty() && raw[0] in "=+-@\t\r") "'" + raw else raw
        return "\"" + guarded.replace("\"", "\"\"") + "\""
    }

    fun exportMealsToCsvFile(context: Context, meals: List<Meal>): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val csvFile = File(exportDir, "zcal_nutrition_log_${DateHelpers.todayLocalDateString()}.csv")
        csvFile.bufferedWriter().use { writer ->
            writer.write("Date,TimeEpochMs,MealType,MealName,Source,TotalCalories,TotalKilojoules,ProteinG,CarbsG,FatG,FiberG,ClaMg,Confidence,ItemsSummary\n")
            meals.forEach { meal ->
                val itemsSummary = meal.items.joinToString("; ") { "${it.name} (${it.grams}g: ${it.calories}kcal)" }
                writer.write(
                    listOf(
                        meal.localDate,
                        meal.loggedAtEpochMillis.toString(),
                        meal.mealType.name,
                        csvCell(meal.name),
                        meal.source.name,
                        meal.totalCalories.toString(),
                        meal.totalKilojoules.toString(),
                        meal.proteinG.toString(),
                        meal.carbsG.toString(),
                        meal.fatG.toString(),
                        meal.fiberG.toString(),
                        meal.claMg.toString(),
                        meal.confidence.name,
                        csvCell(itemsSummary)
                    ).joinToString(",") + "\n"
                )
            }
        }
        return csvFile
    }

    /** Full backup: every meal with its items (the previous version exported items only and lost the meals). */
    fun exportMealsToJsonFile(context: Context, meals: List<Meal>): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val jsonFile = File(exportDir, "zcal_backup_${DateHelpers.todayLocalDateString()}.json")
        val root = buildJsonObject {
            put("app", "ZCal")
            put("exportVersion", 1)
            put("meals", buildJsonArray {
                meals.forEach { meal ->
                    add(buildJsonObject {
                        put("localDate", meal.localDate)
                        put("loggedAtEpochMillis", meal.loggedAtEpochMillis)
                        put("mealType", meal.mealType.name)
                        put("name", meal.name)
                        put("source", meal.source.name)
                        put("totalCalories", meal.totalCalories)
                        put("totalKilojoules", meal.totalKilojoules)
                        put("proteinG", meal.proteinG)
                        put("carbsG", meal.carbsG)
                        put("fatG", meal.fatG)
                        put("fiberG", meal.fiberG)
                        put("claMg", meal.claMg)
                        put("confidence", meal.confidence.name)
                        put("caloriesLow", meal.caloriesLow)
                        put("caloriesHigh", meal.caloriesHigh)
                        meal.note?.let { put("note", it) }
                        put("assumptions", JsonArray(meal.assumptions.map { JsonPrimitive(it) }))
                        put("items", buildJsonArray {
                            meal.items.forEach { item ->
                                add(buildJsonObject {
                                    put("name", item.name)
                                    put("grams", item.grams)
                                    put("calories", item.calories)
                                    put("kilojoules", item.kilojoules)
                                    put("proteinG", item.proteinG)
                                    put("carbsG", item.carbsG)
                                    put("fatG", item.fatG)
                                    put("fiberG", item.fiberG)
                                    put("claMg", item.claMg)
                                    put("confidence", item.confidence.name)
                                })
                            }
                        })
                    })
                }
            })
        }
        jsonFile.writeText(prettyJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), root))
        return jsonFile
    }

    fun shareExportFile(context: Context, file: File, mimeType: String = "text/csv") {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "Export ZCal Nutrition Data").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
