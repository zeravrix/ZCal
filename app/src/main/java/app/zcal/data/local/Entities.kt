package app.zcal.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "meal_entry",
    indices = [
        Index(value = ["localDate"]),
        Index(value = ["mealType"])
    ]
)
data class MealEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val loggedAt: Long,
    val localDate: String,
    val mealType: String,
    val name: String,
    val photoPath: String?,
    val source: String,
    val totalCalories: Int,
    val totalKilojoules: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val claMg: Double,
    val fiberG: Double,
    val confidence: String,
    val caloriesLow: Int,
    val caloriesHigh: Int,
    val note: String?,
    val assumptionsJson: String?,
    val rawJson: String?
)

@Entity(
    tableName = "food_item",
    foreignKeys = [
        ForeignKey(
            entity = MealEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["mealId"]),
        Index(value = ["name"])
    ]
)
data class FoodItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mealId: Long,
    val name: String,
    val grams: Double,
    val calories: Int,
    val kilojoules: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val claMg: Double,
    val fiberG: Double,
    val confidence: String
)

@Entity(tableName = "day_summary")
data class DaySummaryEntity(
    @PrimaryKey val localDate: String,
    val kcal: Int,
    val kj: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val claMg: Double,
    val fiberG: Double,
    val mealCount: Int,
    val complete: Boolean,
    val updatedAt: Long
)

/**
 * Monthly Auto-Emptying Food Image & Detail History Cache Table:
 * Stores the cached image path and full nutrient breakdown (`kcal`, `kJ`, `proteinG`, `fatG`,
 * `carbsG`, `claMg`, item summary) tagged with `yearMonthBucket` (`YYYY-MM`).
 * Automatically emptied every month when the calendar month rolls over or after 30 days.
 */
@Entity(
    tableName = "food_history_cache",
    indices = [
        Index(value = ["yearMonthBucket"]),
        Index(value = ["capturedAtMillis"]),
        Index(value = ["mealId"])
    ]
)
data class FoodHistoryCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mealId: Long,
    val yearMonthBucket: String, // e.g. "2026-11"
    val localDate: String,
    val capturedAtMillis: Long,
    val mealName: String,
    val photoPath: String,
    val caloriesKcal: Int,
    val energyKj: Int,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    val claMg: Double,
    val detailsSummary: String
)

@Entity(
    tableName = "weight_entry",
    indices = [Index(value = ["localDate"], unique = true)]
)
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val localDate: String,
    val weightKg: Double
)

@Entity(
    tableName = "water_entry",
    indices = [Index(value = ["localDate"], unique = true)]
)
data class WaterEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val localDate: String,
    val ml: Int
)

@Entity(tableName = "saved_meal")
data class SavedMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val itemsJson: String,
    val totalCalories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val useCount: Int = 1,
    val lastUsedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "barcode_cache")
data class BarcodeCacheEntity(
    @PrimaryKey val barcode: String,
    val foodItemJson: String,
    val cachedAt: Long
)

@Entity(tableName = "pending_scan")
data class PendingScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val photoPath: String,
    val note: String?,
    val localDate: String,
    val createdAt: Long
)
