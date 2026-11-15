package app.zcal.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.zcal.data.local.ZCalDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Section 11 Page 20 Honesty Rule:
 * "A day counts toward goal days only when it is complete. ZCal marks yesterday complete
 * at 4 a.m. if it has two or more meals, and the user can mark or unmark any day."
 */
@HiltWorker
class DailyCompletionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dao: ZCalDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val summary = dao.getDaySummaryOnce(yesterday)
        if (summary != null && summary.mealCount >= 2 && !summary.complete) {
            dao.upsertDaySummary(summary.copy(complete = true, updatedAt = System.currentTimeMillis()))
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "zcal_daily_4am_completion_worker"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyCompletionWorker>(12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
