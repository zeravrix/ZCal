package app.zcal

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.zcal.data.cache.FoodHistoryCacheManager
import app.zcal.worker.DailyCompletionWorker
import app.zcal.worker.MonthlyCacheCleanupWorker
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ZCalApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var foodHistoryCacheManager: FoodHistoryCacheManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        // 1. Schedule 4:00 a.m. automatic day-completion worker
        DailyCompletionWorker.schedulePeriodic(this)
        // 2. Schedule Monthly Food Image & Detail Cache Cleanup Worker + run startup check
        MonthlyCacheCleanupWorker.schedulePeriodic(this)
        appScope.launch {
            runCatching { foodHistoryCacheManager.purgeExpiredMonthlyCache() }
        }
    }
}
