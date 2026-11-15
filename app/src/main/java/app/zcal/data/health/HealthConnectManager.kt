package app.zcal.data.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import app.zcal.domain.model.DailyHealthSteps
import app.zcal.domain.model.EnergyConversion
import app.zcal.domain.model.Meal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Connects ZCal with Android Health Connect (`androidx.health.connect.client`) AND provides
 * a live on-device Hardware Step Counter Sensor (`Sensor.TYPE_STEP_COUNTER`) fallback so the
 * user always sees their live steps and active calories/kilojoules burned on the Camera HUD
 * and Dashboard.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class)
    )

    fun isHealthConnectAvailable(): Boolean {
        val status = HealthConnectClient.getSdkStatus(context)
        return status == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasAllPermissions(): Boolean {
        if (!isHealthConnectAvailable()) return false
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            granted.containsAll(requiredPermissions)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reads today's steps and active burn from Health Connect if available & permitted,
     * or falls back to the hardware step sensor.
     */
    suspend fun readTodayStepsAndBurn(stepGoal: Int = 10000): DailyHealthSteps {
        if (isHealthConnectAvailable()) {
            try {
                val client = HealthConnectClient.getOrCreate(context)
                val granted = client.permissionController.getGrantedPermissions()
                if (granted.contains(HealthPermission.getReadPermission(StepsRecord::class))) {
                    val zone = ZoneId.systemDefault()
                    val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()
                    val now = Instant.now()
                    val response = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(
                                StepsRecord.COUNT_TOTAL,
                                ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
                            ),
                            timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                        )
                    )
                    val steps = (response[StepsRecord.COUNT_TOTAL] ?: 0L).toInt()
                    val activeKcal = response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.roundToInt()
                        ?: (steps * 0.04).roundToInt()
                    return DailyHealthSteps(
                        steps = steps,
                        stepGoal = stepGoal,
                        activeBurnKcal = activeKcal,
                        activeBurnKj = EnergyConversion.kcalToKj(activeKcal),
                        sourceLabel = "Health Connect"
                    )
                }
            } catch (_: Exception) {
                // Fall through to hardware sensor
            }
        }
        return DailyHealthSteps(
            steps = latestHardwareSteps,
            stepGoal = stepGoal,
            activeBurnKcal = (latestHardwareSteps * 0.04).roundToInt(),
            activeBurnKj = EnergyConversion.kcalToKj((latestHardwareSteps * 0.04).roundToInt()),
            sourceLabel = "Device Step Sensor"
        )
    }

    @Volatile
    private var latestHardwareSteps: Int = 0
    private var baselineSensorSteps: Int? = null

    fun observeLiveHardwareSteps(): Flow<Int> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensorManager == null || stepSensor == null) {
            trySend(latestHardwareSteps)
            awaitClose {}
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val rawTotal = event?.values?.firstOrNull()?.toInt() ?: return
                val base = baselineSensorSteps ?: rawTotal.also { baselineSensorSteps = it }
                val sessionSteps = (rawTotal - base).coerceAtLeast(0)
                latestHardwareSteps = sessionSteps
                trySend(sessionSteps)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    /**
     * Writes a saved meal's nutrition breakdown (kcal, protein, carbs, fat, fiber) to Health Connect.
     */
    suspend fun writeMealToHealthConnect(meal: Meal) {
        if (!isHealthConnectAvailable()) return
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.contains(HealthPermission.getWritePermission(NutritionRecord::class))) return

            val startInstant = Instant.ofEpochMilli(meal.loggedAtEpochMillis)
            val endInstant = startInstant.plusSeconds(900)
            val offset = ZoneId.systemDefault().rules.getOffset(startInstant)

            val record = NutritionRecord(
                // Same clientRecordId + higher version = Health Connect updates in place instead of duplicating.
                metadata = Metadata(
                    clientRecordId = "zcal-meal-${meal.id}",
                    clientRecordVersion = System.currentTimeMillis()
                ),
                startTime = startInstant,
                startZoneOffset = offset,
                endTime = endInstant,
                endZoneOffset = offset,
                name = meal.name,
                energy = Energy.kilocalories(meal.totalCalories.toDouble()),
                protein = Mass.grams(meal.proteinG),
                totalCarbohydrate = Mass.grams(meal.carbsG),
                totalFat = Mass.grams(meal.fatG),
                dietaryFiber = Mass.grams(meal.fiberG)
            )
            client.insertRecords(listOf(record))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Ignore write errors if Health Connect is locked
        }
    }

    suspend fun writeWeightToHealthConnect(weightKg: Double) {
        if (!isHealthConnectAvailable()) return
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.contains(HealthPermission.getWritePermission(WeightRecord::class))) return

            val now = Instant.now()
            val offset = ZoneId.systemDefault().rules.getOffset(now)
            val record = WeightRecord(
                metadata = Metadata(
                    clientRecordId = "zcal-weight-${java.time.LocalDate.now()}",
                    clientRecordVersion = System.currentTimeMillis()
                ),
                time = now,
                zoneOffset = offset,
                weight = Mass.kilograms(weightKg)
            )
            client.insertRecords(listOf(record))
        } catch (_: Exception) {
        }
    }
}
