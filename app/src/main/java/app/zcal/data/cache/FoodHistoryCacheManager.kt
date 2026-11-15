package app.zcal.data.cache

import android.content.Context
import app.zcal.data.local.FoodHistoryCacheEntity
import app.zcal.data.local.ZCalDao
import app.zcal.domain.model.CachedFoodHistoryItem
import app.zcal.domain.model.Meal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the 30-Day / Monthly Food Image & Detail History Cache:
 * - Saves every snapped meal image (`filesDir/meals_cache/YYYY-MM/`) and its detailed
 *   breakdown (`kcal`, `kJ`, `proteinG`, `fatG`, `carbsG`, `claMg`, item summary) into `food_history_cache`.
 * - Automatically purges/empties cached food images and details from previous months or older than 30 days
 *   so device storage stays fast and clean while keeping aggregated `day_summary` charts intact.
 */
@Singleton
class FoodHistoryCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ZCalDao
) {
    companion object {
        private const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L
    }

    val cachedHistoryFlow: Flow<List<CachedFoodHistoryItem>> =
        dao.observeFoodHistoryCache().map { list ->
            list.map { entity ->
                CachedFoodHistoryItem(
                    id = entity.id,
                    mealId = entity.mealId,
                    yearMonthBucket = entity.yearMonthBucket,
                    localDate = entity.localDate,
                    capturedAtMillis = entity.capturedAtMillis,
                    mealName = entity.mealName,
                    photoPath = entity.photoPath,
                    caloriesKcal = entity.caloriesKcal,
                    energyKj = entity.energyKj,
                    proteinG = entity.proteinG,
                    fatG = entity.fatG,
                    carbsG = entity.carbsG,
                    claMg = entity.claMg,
                    detailsSummary = entity.detailsSummary
                )
            }
        }

    suspend fun recordMealInMonthlyCache(mealId: Long, meal: Meal) {
        val currentBucket = runCatching { YearMonth.parse(meal.localDate.take(7)).toString() }
            .getOrElse { YearMonth.now().toString() } // bucket by the meal's own month, not "today"
        val details = if (meal.items.isNotEmpty()) {
            meal.items.joinToString(" • ") {
                "${it.name} (${it.grams.toInt()}g: ${it.calories} kcal / ${it.kilojoules} kJ)"
            }
        } else {
            "${meal.totalCalories} kcal (${meal.totalKilojoules} kJ)"
        }

        dao.replaceHistoryCacheEntry(
            FoodHistoryCacheEntity(
                mealId = mealId,
                yearMonthBucket = currentBucket,
                localDate = meal.localDate,
                capturedAtMillis = meal.loggedAtEpochMillis,
                mealName = meal.name,
                photoPath = meal.photoPath ?: "",
                caloriesKcal = meal.totalCalories,
                energyKj = meal.totalKilojoules,
                proteinG = meal.proteinG,
                fatG = meal.fatG,
                carbsG = meal.carbsG,
                claMg = meal.claMg,
                detailsSummary = details
            )
        )
    }

    /**
     * Automatically empties all food images and detail cache records from previous months
     * or older than 30 days. Called on app launch and by [app.zcal.worker.MonthlyCacheCleanupWorker].
     */
    suspend fun purgeExpiredMonthlyCache(): Int {
        val currentBucket = YearMonth.now().toString()
        val cutoffMillis = System.currentTimeMillis() - THIRTY_DAYS_MS
        val cutoffDate = LocalDate.now().minusDays(30).toString()

        val expiredEntries = dao.getExpiredFoodHistoryCache(currentBucket, cutoffMillis)
        var deletedCount = 0

        expiredEntries.forEach { entry ->
            if (entry.photoPath.isNotBlank()) {
                runCatching { File(entry.photoPath).delete() }
            }
            // The meal row stays (it feeds the log and charts) but must not point at a deleted file.
            dao.nullifyMealPhotoPath(entry.mealId)
            deletedCount++
        }

        if (expiredEntries.isNotEmpty()) {
            dao.deleteFoodHistoryCacheByIds(expiredEntries.map { it.id })
        }

        // Sweep orphans in older month folders, but only files past the TTL: a photo queued for an offline
        // scan from late last month must survive until the worker has analysed it.
        val cacheRoot = File(context.filesDir, "meals_cache")
        if (cacheRoot.exists()) {
            cacheRoot.listFiles()?.forEach { dir ->
                if (dir.isDirectory && dir.name != currentBucket) {
                    dir.listFiles()?.forEach { f ->
                        val stillQueued = dao.countPendingScansForPhoto(f.absolutePath) > 0
                        if (!stillQueued && f.lastModified() < cutoffMillis) runCatching { f.delete() }
                    }
                    if (dir.listFiles().isNullOrEmpty()) dir.delete()
                }
            }
        }

        dao.nullifyExpiredMealPhotoPaths(cutoffDate)
        return deletedCount
    }

    /**
     * Immediately empties the entire food image & detail cache on user demand.
     */
    suspend fun emptyEntireCacheNow() {
        val cacheRoot = File(context.filesDir, "meals_cache")
        if (cacheRoot.exists()) {
            cacheRoot.deleteRecursively()
        }
        val legacyDir = File(context.filesDir, "meals")
        if (legacyDir.exists()) {
            legacyDir.deleteRecursively()
        }
        dao.clearAllFoodHistoryCache()
    }

    fun getCacheSizeKb(): Long {
        val cacheRoot = File(context.filesDir, "meals_cache")
        if (!cacheRoot.exists()) return 0L
        return cacheRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024L
    }

    fun daysUntilNextMonthlyAutoEmpty(): Long {
        val today = LocalDate.now()
        val firstOfNextMonth = YearMonth.from(today).plusMonths(1).atDay(1)
        return ChronoUnit.DAYS.between(today, firstOfNextMonth).coerceAtLeast(1L)
    }
}
