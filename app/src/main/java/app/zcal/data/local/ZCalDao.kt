package app.zcal.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class MealWithItemsRelation(
    @Embedded val meal: MealEntryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "mealId"
    )
    val items: List<FoodItemEntity>
)

data class WeekdayAverageRow(
    val weekday: Int,
    val avgKcal: Double
)

data class TopFoodRow(
    val food: String,
    val kcal: Int,
    val protein: Double,
    val times: Int
)

data class MealTypeDistributionRow(
    val mealType: String,
    val avgKcal: Double,
    val meals: Int
)

@Dao
interface ZCalDao {

    @Transaction
    @Query("SELECT * FROM meal_entry WHERE localDate = :localDate ORDER BY loggedAt DESC")
    fun observeMealsForDate(localDate: String): Flow<List<MealWithItemsRelation>>

    @Transaction
    @Query("SELECT * FROM meal_entry WHERE id = :mealId LIMIT 1")
    suspend fun getMealWithItemsById(mealId: Long): MealWithItemsRelation?

    @Transaction
    @Query("SELECT * FROM meal_entry ORDER BY loggedAt DESC")
    suspend fun getAllMealsWithItems(): List<MealWithItemsRelation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealEntry(meal: MealEntryEntity): Long

    @Update
    suspend fun updateMealEntry(meal: MealEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodItems(items: List<FoodItemEntity>)

    @Query("DELETE FROM food_item WHERE mealId = :mealId")
    suspend fun deleteFoodItemsForMeal(mealId: Long)

    @Query("DELETE FROM meal_entry WHERE id = :mealId")
    suspend fun deleteMealById(mealId: Long)

    @Query("SELECT * FROM day_summary WHERE localDate = :localDate LIMIT 1")
    fun observeDaySummary(localDate: String): Flow<DaySummaryEntity?>

    @Query("SELECT * FROM day_summary WHERE localDate = :localDate LIMIT 1")
    suspend fun getDaySummaryOnce(localDate: String): DaySummaryEntity?

    @Query(
        """
        SELECT localDate, kcal, kj, proteinG, carbsG, fatG, claMg, fiberG, mealCount, complete, updatedAt
        FROM day_summary
        WHERE localDate BETWEEN :start AND :end
        ORDER BY localDate ASC
        """
    )
    fun observeDaySummariesInRange(start: String, end: String): Flow<List<DaySummaryEntity>>

    @Query(
        """
        SELECT CAST(strftime('%w', localDate) AS INTEGER) AS weekday, AVG(kcal) AS avgKcal
        FROM day_summary
        WHERE localDate BETWEEN :start AND :end AND mealCount > 0
        GROUP BY weekday
        ORDER BY weekday ASC
        """
    )
    fun observeWeekdayPattern(start: String, end: String): Flow<List<WeekdayAverageRow>>

    @Query(
        """
        SELECT lower(fi.name) AS food, SUM(fi.calories) AS kcal, SUM(fi.proteinG) AS protein, COUNT(*) AS times
        FROM food_item fi JOIN meal_entry me ON me.id = fi.mealId
        WHERE me.localDate BETWEEN :start AND :end
        GROUP BY lower(fi.name)
        ORDER BY kcal DESC
        LIMIT 5
        """
    )
    fun observeTopFoodsByCalories(start: String, end: String): Flow<List<TopFoodRow>>

    @Query(
        """
        SELECT lower(fi.name) AS food, SUM(fi.calories) AS kcal, SUM(fi.proteinG) AS protein, COUNT(*) AS times
        FROM food_item fi JOIN meal_entry me ON me.id = fi.mealId
        WHERE me.localDate BETWEEN :start AND :end
        GROUP BY lower(fi.name)
        ORDER BY protein DESC
        LIMIT 5
        """
    )
    fun observeTopFoodsByProtein(start: String, end: String): Flow<List<TopFoodRow>>

    @Query(
        """
        SELECT mealType, AVG(totalCalories) AS avgKcal, COUNT(*) AS meals
        FROM meal_entry
        WHERE localDate BETWEEN :start AND :end
        GROUP BY mealType
        """
    )
    fun observeMealTypeDistribution(start: String, end: String): Flow<List<MealTypeDistributionRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDaySummary(summary: DaySummaryEntity)

    @Query("DELETE FROM day_summary WHERE localDate = :localDate")
    suspend fun deleteDaySummary(localDate: String)

    @Query("SELECT * FROM meal_entry WHERE localDate = :localDate")
    suspend fun getMealsForDateOnce(localDate: String): List<MealEntryEntity>

    @Transaction
    suspend fun refreshDaySummaryForDate(localDate: String, markCompleteOverride: Boolean? = null) {
        val meals = getMealsForDateOnce(localDate)
        val existing = getDaySummaryOnce(localDate)
        if (meals.isEmpty()) {
            deleteDaySummary(localDate)
            return
        }
        val kcal = meals.sumOf { it.totalCalories }
        val kj = meals.sumOf { it.totalKilojoules }
        val protein = meals.sumOf { it.proteinG }
        val carbs = meals.sumOf { it.carbsG }
        val fat = meals.sumOf { it.fatG }
        val cla = meals.sumOf { it.claMg }
        val fiber = meals.sumOf { it.fiberG }
        val count = meals.size
        // Honesty rule: a day only counts as complete once it is over (the daily worker flips it) or the
        // user marks it. Previously any day became "complete" the moment a 2nd meal was logged.
        val isPastDay = localDate < java.time.LocalDate.now().toString()
        val isComplete = markCompleteOverride ?: existing?.complete ?: (isPastDay && count >= 2)

        upsertDaySummary(
            DaySummaryEntity(
                localDate = localDate,
                kcal = kcal,
                kj = kj,
                proteinG = protein,
                carbsG = carbs,
                fatG = fat,
                claMg = cla,
                fiberG = fiber,
                mealCount = count,
                complete = isComplete,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    @Transaction
    suspend fun saveMealWithItemsTransaction(
        meal: MealEntryEntity,
        items: List<FoodItemEntity>
    ): Long {
        val mealId = if (meal.id == 0L) {
            insertMealEntry(meal)
        } else {
            updateMealEntry(meal)
            deleteFoodItemsForMeal(meal.id)
            meal.id
        }
        val boundItems = items.map { it.copy(id = 0L, mealId = mealId) }
        insertFoodItems(boundItems)
        refreshDaySummaryForDate(meal.localDate)
        return mealId
    }

    @Transaction
    suspend fun deleteMealTransaction(mealId: Long, localDate: String) {
        deleteMealById(mealId)
        deleteHistoryCacheByMealId(mealId)
        refreshDaySummaryForDate(localDate)
    }

    // ---------------------------------------------------------
    // Monthly Food Image & Detail History Cache Operations
    // ---------------------------------------------------------
    @Query("SELECT * FROM food_history_cache ORDER BY capturedAtMillis DESC")
    fun observeFoodHistoryCache(): Flow<List<FoodHistoryCacheEntity>>

    @Query("SELECT * FROM food_history_cache WHERE yearMonthBucket != :currentYearMonth OR capturedAtMillis < :cutoffMillis")
    suspend fun getExpiredFoodHistoryCache(currentYearMonth: String, cutoffMillis: Long): List<FoodHistoryCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFoodHistoryCache(entry: FoodHistoryCacheEntity)

    @Query("DELETE FROM food_history_cache WHERE id IN (:ids)")
    suspend fun deleteFoodHistoryCacheByIds(ids: List<Long>)

    @Query("DELETE FROM food_history_cache WHERE mealId = :mealId")
    suspend fun deleteHistoryCacheByMealId(mealId: Long)

    @Query("UPDATE meal_entry SET photoPath = NULL WHERE id = :mealId")
    suspend fun nullifyMealPhotoPath(mealId: Long)

    @Query("SELECT photoPath FROM meal_entry WHERE id = :mealId")
    suspend fun getMealPhotoPath(mealId: Long): String?

    @Query("SELECT COUNT(*) FROM pending_scan WHERE photoPath = :photoPath")
    suspend fun countPendingScansForPhoto(photoPath: String): Int

    @Query("DELETE FROM pending_scan")
    suspend fun clearAllPendingScans()

    @Query("DELETE FROM saved_meal")
    suspend fun clearAllSavedMeals()

    @Query("DELETE FROM barcode_cache")
    suspend fun clearAllBarcodeCache()

    @Transaction
    suspend fun replaceHistoryCacheEntry(entry: FoodHistoryCacheEntity) {
        // One cache row per meal: editing a meal used to insert a duplicate row each time.
        deleteHistoryCacheByMealId(entry.mealId)
        upsertFoodHistoryCache(entry)
    }

    @Query("DELETE FROM food_history_cache")
    suspend fun clearAllFoodHistoryCache()

    @Query(" UPDATE meal_entry SET photoPath = NULL WHERE localDate < :cutoffDate ")
    suspend fun nullifyExpiredMealPhotoPaths(cutoffDate: String)

    // ---------------------------------------------------------
    // Weight, Water, Saved Meals, Barcode Cache & Pending Scans
    // ---------------------------------------------------------
    @Query("SELECT * FROM weight_entry ORDER BY localDate ASC")
    fun observeAllWeights(): Flow<List<WeightEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeight(entry: WeightEntryEntity)

    @Query("SELECT * FROM water_entry WHERE localDate = :localDate LIMIT 1")
    fun observeWaterForDate(localDate: String): Flow<WaterEntryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWater(entry: WaterEntryEntity)

    @Query("SELECT * FROM saved_meal ORDER BY useCount DESC, lastUsedAt DESC")
    fun observeSavedMeals(): Flow<List<SavedMealEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedMeal(savedMeal: SavedMealEntity)

    @Query("SELECT * FROM barcode_cache WHERE barcode = :barcode LIMIT 1")
    suspend fun getCachedBarcode(barcode: String): BarcodeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBarcodeCache(entry: BarcodeCacheEntity)

    @Query("SELECT * FROM pending_scan ORDER BY createdAt ASC")
    fun observePendingScans(): Flow<List<PendingScanEntity>>

    @Query("SELECT * FROM pending_scan ORDER BY createdAt ASC")
    suspend fun getPendingScansOnce(): List<PendingScanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingScan(scan: PendingScanEntity): Long

    @Query("DELETE FROM pending_scan WHERE id = :id")
    suspend fun deletePendingScan(id: Long)

    @Query("DELETE FROM meal_entry")
    suspend fun clearAllMeals()

    @Query("DELETE FROM day_summary")
    suspend fun clearAllDaySummaries()

    @Query("DELETE FROM weight_entry")
    suspend fun clearAllWeights()

    @Query("DELETE FROM water_entry")
    suspend fun clearAllWater()
}
