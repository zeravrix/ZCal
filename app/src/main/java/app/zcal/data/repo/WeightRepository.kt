package app.zcal.data.repo

import app.zcal.data.health.HealthConnectManager
import app.zcal.data.local.WeightEntryEntity
import app.zcal.data.local.ZCalDao
import app.zcal.domain.model.WeightEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeightRepository @Inject constructor(
    private val dao: ZCalDao,
    private val healthConnectManager: HealthConnectManager
) {
    val allWeightsFlow: Flow<List<WeightEntry>> = dao.observeAllWeights().map { list ->
        list.map { WeightEntry(id = it.id, localDate = it.localDate, weightKg = it.weightKg) }
    }

    suspend fun logWeightForDate(localDate: String, weightKg: Double) {
        val safeWeight = weightKg.coerceIn(25.0, 350.0)
        dao.upsertWeight(
            WeightEntryEntity(
                localDate = localDate,
                weightKg = safeWeight
            )
        )
        healthConnectManager.writeWeightToHealthConnect(safeWeight)
    }
}
