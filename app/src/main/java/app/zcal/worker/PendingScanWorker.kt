package app.zcal.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.zcal.data.ai.FoodAnalysisResult
import app.zcal.data.ai.FoodAnalyzer
import app.zcal.data.local.ZCalDao
import app.zcal.data.repo.MealRepository
import app.zcal.data.repo.ProfileRepository
import app.zcal.domain.model.FoodItem
import app.zcal.domain.model.Meal
import app.zcal.domain.model.MealSource
import app.zcal.domain.model.MealType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Section 6 & 11 Offline Scan Queue Worker:
 * When the user snaps a meal while offline, ZCal stores it in `pending_scan` and schedules
 * [PendingScanWorker] with `NetworkType.CONNECTED`. As soon as connectivity returns, it
 * analyzes the queued photo and adds the meal to the original `localDate`.
 */
@HiltWorker
class PendingScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dao: ZCalDao,
    private val foodAnalyzer: FoodAnalyzer,
    private val mealRepository: MealRepository,
    private val profileRepository: ProfileRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val pendingList = dao.getPendingScansOnce()
        if (pendingList.isEmpty()) return Result.success()

        val profile = profileRepository.userProfileFlow.first()

        for (pending in pendingList) {
            val file = File(pending.photoPath)
            if (!file.exists()) {
                dao.deletePendingScan(pending.id)
                continue
            }

            when (val res = foodAnalyzer.analyzeMeal(listOf(file), pending.note, profile.units)) {
                is FoodAnalysisResult.Success -> {
                    val analysis = calibrate(res.analysis, profile.personalCalibrationPct)
                    val items = analysis.items.map { dto ->
                        FoodItem(
                            name = dto.name,
                            grams = dto.grams,
                            calories = dto.calories.roundToInt(),
                            proteinG = dto.proteinG,
                            carbsG = dto.carbsG,
                            fatG = dto.fatG,
                            fiberG = dto.fiberG,
                            confidence = dto.confidence
                        )
                    }
                    val hour = Instant.ofEpochMilli(pending.createdAt)
                        .atZone(ZoneId.systemDefault()).hour
                    val meal = Meal(
                        loggedAtEpochMillis = pending.createdAt,
                        localDate = pending.localDate,
                        mealType = MealType.fromHourOfDay(hour),
                        name = analysis.mealName,
                        photoPath = pending.photoPath,
                        source = MealSource.PHOTO,
                        totalCalories = items.sumOf { it.calories },
                        proteinG = items.sumOf { it.proteinG },
                        carbsG = items.sumOf { it.carbsG },
                        fatG = items.sumOf { it.fatG },
                        fiberG = items.sumOf { it.fiberG },
                        caloriesLow = analysis.caloriesLow,
                        caloriesHigh = analysis.caloriesHigh,
                        note = pending.note,
                        assumptions = analysis.assumptions,
                        rawJson = res.rawJson,
                        items = items
                    )
                    mealRepository.saveMeal(meal)
                    dao.deletePendingScan(pending.id)
                }
                is FoodAnalysisResult.RateLimited, is FoodAnalysisResult.Offline, is FoodAnalysisResult.Timeout -> {
                    return if (runAttemptCount < 5) Result.retry() else Result.failure()
                }
                is FoodAnalysisResult.InvalidKey -> {
                    // Keep the queued photo: the user just needs to fix the key in Settings; the next scan re-triggers us.
                    return Result.failure()
                }
                else -> {
                    // Unrecoverable (e.g. not food), remove from queue so it doesn't loop forever
                    dao.deletePendingScan(pending.id)
                }
            }
        }
        return Result.success()
    }

    private fun calibrate(a: app.zcal.domain.model.MealAnalysis, pct: Int): app.zcal.domain.model.MealAnalysis {
        if (pct == 0) return a
        val m = (1.0 + pct / 100.0).coerceIn(0.75, 1.25)
        return a.copy(
            items = a.items.map {
                it.copy(
                    calories = it.calories * m, kilojoules = it.kilojoules * m, claMg = it.claMg * m,
                    proteinG = it.proteinG * m, carbsG = it.carbsG * m, fatG = it.fatG * m, fiberG = it.fiberG * m
                )
            },
            caloriesLow = kotlin.math.round(a.caloriesLow * m).toInt(),
            caloriesHigh = kotlin.math.round(a.caloriesHigh * m).toInt()
        )
    }

    companion object {
        private const val WORK_NAME = "zcal_pending_offline_scan_worker"

        fun enqueueWhenOnline(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<PendingScanWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
