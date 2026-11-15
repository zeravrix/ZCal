package app.zcal.data.repo

import app.zcal.data.cache.FoodHistoryCacheManager
import app.zcal.data.food.OpenFoodFactsApi
import app.zcal.data.health.HealthConnectManager
import app.zcal.data.local.BarcodeCacheEntity
import app.zcal.data.local.DaySummaryEntity
import app.zcal.data.local.FoodItemEntity
import app.zcal.data.local.MealEntryEntity
import app.zcal.data.local.MealTypeDistributionRow
import app.zcal.data.local.MealWithItemsRelation
import app.zcal.data.local.PendingScanEntity
import app.zcal.data.local.SavedMealEntity
import app.zcal.data.local.TopFoodRow
import app.zcal.data.local.WaterEntryEntity
import app.zcal.data.local.WeekdayAverageRow
import app.zcal.data.local.ZCalDao
import app.zcal.domain.model.ConfidenceLevel
import app.zcal.domain.model.DaySummary
import app.zcal.domain.model.EnergyConversion
import app.zcal.domain.model.FoodItem
import app.zcal.domain.model.Meal
import app.zcal.domain.model.MealSource
import app.zcal.domain.model.MealType
import app.zcal.util.DateHelpers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.content.Context
import androidx.glance.appwidget.updateAll
import app.zcal.widget.ZCalGlanceWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class MealRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ZCalDao,
    private val openFoodFactsApi: OpenFoodFactsApi,
    private val foodHistoryCacheManager: FoodHistoryCacheManager,
    private val healthConnectManager: HealthConnectManager
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun observeMealsForDate(localDate: String): Flow<List<Meal>> =
        dao.observeMealsForDate(localDate).map { list -> list.map { it.toDomain() } }

    fun observeDaySummary(localDate: String): Flow<DaySummary?> =
        dao.observeDaySummary(localDate).map { it?.toDomain() }

    fun observeDaySummariesInRange(start: String, end: String): Flow<List<DaySummary>> =
        dao.observeDaySummariesInRange(start, end).map { list -> list.map { it.toDomain() } }

    fun observeWeekdayPattern(start: String, end: String): Flow<List<WeekdayAverageRow>> =
        dao.observeWeekdayPattern(start, end)

    fun observeTopFoodsByCalories(start: String, end: String): Flow<List<TopFoodRow>> =
        dao.observeTopFoodsByCalories(start, end)

    fun observeTopFoodsByProtein(start: String, end: String): Flow<List<TopFoodRow>> =
        dao.observeTopFoodsByProtein(start, end)

    fun observeMealTypeDistribution(start: String, end: String): Flow<List<MealTypeDistributionRow>> =
        dao.observeMealTypeDistribution(start, end)

    fun observeWaterForDate(localDate: String): Flow<Int> =
        dao.observeWaterForDate(localDate).map { it?.ml ?: 0 }

    fun observeSavedMeals(): Flow<List<SavedMealEntity>> = dao.observeSavedMeals()

    fun observePendingScans(): Flow<List<PendingScanEntity>> = dao.observePendingScans()

    suspend fun getMealById(mealId: Long): Meal? =
        dao.getMealWithItemsById(mealId)?.toDomain()

    suspend fun saveMeal(meal: Meal): Long {
        val recomputedCalories = meal.items.sumOf { it.calories }.takeIf { meal.items.isNotEmpty() } ?: meal.totalCalories
        val recomputedKj = EnergyConversion.kcalToKj(recomputedCalories)
        val recomputedProtein = meal.items.sumOf { it.proteinG }.takeIf { meal.items.isNotEmpty() } ?: meal.proteinG
        val recomputedCarbs = meal.items.sumOf { it.carbsG }.takeIf { meal.items.isNotEmpty() } ?: meal.carbsG
        val recomputedFat = meal.items.sumOf { it.fatG }.takeIf { meal.items.isNotEmpty() } ?: meal.fatG
        val recomputedCla = meal.items.sumOf { it.claMg }.takeIf { meal.items.isNotEmpty() }
            ?: meal.claMg.takeIf { it > 0.0 }
            ?: (recomputedFat * 6.5)
        val recomputedFiber = meal.items.sumOf { it.fiberG }.takeIf { meal.items.isNotEmpty() } ?: meal.fiberG

        val entity = MealEntryEntity(
            id = meal.id,
            loggedAt = meal.loggedAtEpochMillis,
            localDate = meal.localDate,
            mealType = meal.mealType.name,
            name = meal.name.trim().ifEmpty { "Meal" },
            photoPath = meal.photoPath,
            source = meal.source.name,
            totalCalories = recomputedCalories,
            totalKilojoules = recomputedKj,
            proteinG = (recomputedProtein * 10.0).roundToInt() / 10.0,
            carbsG = (recomputedCarbs * 10.0).roundToInt() / 10.0,
            fatG = (recomputedFat * 10.0).roundToInt() / 10.0,
            claMg = (recomputedCla * 10.0).roundToInt() / 10.0,
            fiberG = (recomputedFiber * 10.0).roundToInt() / 10.0,
            confidence = meal.confidence.name,
            caloriesLow = meal.caloriesLow.coerceAtMost(recomputedCalories),
            caloriesHigh = meal.caloriesHigh.coerceAtLeast(recomputedCalories),
            note = meal.note,
            assumptionsJson = json.encodeToString(meal.assumptions),
            rawJson = meal.rawJson
        )

        val itemEntities = meal.items.map { item ->
            FoodItemEntity(
                id = item.id,
                mealId = meal.id,
                name = item.name,
                grams = item.grams,
                calories = item.calories,
                kilojoules = EnergyConversion.kcalToKj(item.calories),
                proteinG = item.proteinG,
                carbsG = item.carbsG,
                fatG = item.fatG,
                claMg = if (item.claMg > 0.0) item.claMg else (item.fatG * 6.5 * 10.0).roundToInt() / 10.0,
                fiberG = item.fiberG,
                confidence = item.confidence.name
            )
        }

        val savedId = dao.saveMealWithItemsTransaction(entity, itemEntities)
        val updatedMeal = meal.copy(
            id = savedId,
            totalCalories = recomputedCalories,
            totalKilojoules = recomputedKj,
            proteinG = entity.proteinG,
            carbsG = entity.carbsG,
            fatG = entity.fatG,
            claMg = entity.claMg,
            fiberG = entity.fiberG
        )

        // Record in 30-day Monthly Food Image & Detail History Cache + Health Connect
        // Side effects must never fail a save that already succeeded in Room.
        sideEffect { foodHistoryCacheManager.recordMealInMonthlyCache(savedId, updatedMeal) }
        sideEffect { healthConnectManager.writeMealToHealthConnect(updatedMeal) }
        sideEffect { ZCalGlanceWidget().updateAll(context) }
        return savedId
    }

    private suspend inline fun sideEffect(block: () -> Unit) {
        try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
    }

    suspend fun deleteMeal(meal: Meal) {
        val photo = dao.getMealPhotoPath(meal.id)
        dao.deleteMealTransaction(meal.id, meal.localDate)
        if (!photo.isNullOrBlank()) runCatching { File(photo).delete() } // no orphaned photos
        sideEffect { ZCalGlanceWidget().updateAll(context) }
    }

    suspend fun toggleDayComplete(localDate: String, complete: Boolean) {
        dao.refreshDaySummaryForDate(localDate, markCompleteOverride = complete)
    }

    suspend fun addWaterMl(localDate: String, deltaMl: Int, currentMl: Int) {
        val updated = (currentMl + deltaMl).coerceIn(0, 10000)
        dao.upsertWater(WaterEntryEntity(localDate = localDate, ml = updated))
    }

    suspend fun lookupBarcodeCached(barcode: String): FoodItem? {
        val clean = barcode.filter { it.isDigit() }
        val cached = dao.getCachedBarcode(clean)
        if (cached != null) {
            runCatching { json.decodeFromString<FoodItem>(cached.foodItemJson) }.getOrNull()?.let {
                return it
            }
        }
        val remote = openFoodFactsApi.lookupBarcode(clean) ?: return null
        dao.upsertBarcodeCache(
            BarcodeCacheEntity(
                barcode = clean,
                foodItemJson = json.encodeToString(remote),
                cachedAt = System.currentTimeMillis()
            )
        )
        return remote
    }

    suspend fun enqueuePendingOfflineScan(photoPath: String, note: String?) {
        if (dao.countPendingScansForPhoto(photoPath) > 0) return // Retry taps must not queue the same photo twice
        dao.insertPendingScan(
            PendingScanEntity(
                photoPath = photoPath,
                note = note,
                localDate = DateHelpers.todayLocalDateString(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getAllMealsForExport(): List<Meal> =
        dao.getAllMealsWithItems().map { it.toDomain() }

    suspend fun resetAllUserData() {
        dao.clearAllMeals()
        dao.clearAllDaySummaries()
        dao.clearAllWeights()
        dao.clearAllWater()
        dao.clearAllPendingScans()
        dao.clearAllSavedMeals()
        dao.clearAllBarcodeCache()
        foodHistoryCacheManager.emptyEntireCacheNow()
        sideEffect { ZCalGlanceWidget().updateAll(context) }
    }

    private fun MealWithItemsRelation.toDomain(): Meal {
        val assumptionsList = meal.assumptionsJson?.let {
            runCatching { json.decodeFromString<List<String>>(it) }.getOrNull()
        } ?: emptyList()

        return Meal(
            id = meal.id,
            loggedAtEpochMillis = meal.loggedAt,
            localDate = meal.localDate,
            mealType = runCatching { MealType.valueOf(meal.mealType) }.getOrDefault(MealType.LUNCH),
            name = meal.name,
            photoPath = meal.photoPath,
            source = runCatching { MealSource.valueOf(meal.source) }.getOrDefault(MealSource.PHOTO),
            totalCalories = meal.totalCalories,
            totalKilojoules = meal.totalKilojoules,
            proteinG = meal.proteinG,
            carbsG = meal.carbsG,
            fatG = meal.fatG,
            claMg = meal.claMg,
            fiberG = meal.fiberG,
            confidence = runCatching { ConfidenceLevel.valueOf(meal.confidence) }.getOrDefault(ConfidenceLevel.MEDIUM),
            caloriesLow = meal.caloriesLow,
            caloriesHigh = meal.caloriesHigh,
            note = meal.note,
            assumptions = assumptionsList,
            rawJson = meal.rawJson,
            items = items.map { item ->
                FoodItem(
                    id = item.id,
                    mealId = item.mealId,
                    name = item.name,
                    grams = item.grams,
                    calories = item.calories,
                    kilojoules = item.kilojoules,
                    proteinG = item.proteinG,
                    carbsG = item.carbsG,
                    fatG = item.fatG,
                    claMg = item.claMg,
                    fiberG = item.fiberG,
                    confidence = runCatching { ConfidenceLevel.valueOf(item.confidence) }.getOrDefault(ConfidenceLevel.MEDIUM)
                )
            }
        )
    }

    private fun DaySummaryEntity.toDomain() = DaySummary(
        localDate = localDate,
        kcal = kcal,
        kj = kj,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        claMg = claMg,
        fiberG = fiberG,
        mealCount = mealCount,
        complete = complete,
        updatedAt = updatedAt
    )
}
