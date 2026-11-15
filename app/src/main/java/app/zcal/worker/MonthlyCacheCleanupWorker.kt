package app.zcal.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.zcal.data.cache.FoodHistoryCacheManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Scheduled background worker that checks daily and automatically empties expired
 * food images and detail cache entries on monthly rollover / 30-day TTL.
 */
@HiltWorker
class MonthlyCacheCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val cacheManager: FoodHistoryCacheManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            cacheManager.purgeExpiredMonthlyCache()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "zcal_monthly_food_image_cache_cleanup"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonthlyCacheCleanupWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
