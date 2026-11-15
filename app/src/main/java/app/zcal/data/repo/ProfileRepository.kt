package app.zcal.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.zcal.domain.model.ActivityLevel
import app.zcal.domain.model.BiologicalSex
import app.zcal.domain.model.EnergyConversion
import app.zcal.domain.model.GoalType
import app.zcal.domain.model.ThemePreference
import app.zcal.domain.model.UnitSystem
import app.zcal.domain.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

@Singleton
class ProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_SEX = stringPreferencesKey("sex")
        private val KEY_BIRTH_YEAR = intPreferencesKey("birth_year")
        private val KEY_HEIGHT_CM = doublePreferencesKey("height_cm")
        private val KEY_WEIGHT_KG = doublePreferencesKey("weight_kg")
        private val KEY_TARGET_WEIGHT_KG = doublePreferencesKey("target_weight_kg")
        private val KEY_ACTIVITY = stringPreferencesKey("activity_level")
        private val KEY_GOAL_TYPE = stringPreferencesKey("goal_type")
        private val KEY_WEEKLY_CHANGE_KG = doublePreferencesKey("weekly_change_kg")
        private val KEY_CALORIE_GOAL = intPreferencesKey("calorie_goal")
        private val KEY_PROTEIN_GOAL = intPreferencesKey("protein_goal_g")
        private val KEY_CARBS_GOAL = intPreferencesKey("carbs_goal_g")
        private val KEY_FAT_GOAL = intPreferencesKey("fat_goal_g")
        private val KEY_CLA_GOAL = intPreferencesKey("cla_goal_mg")
        private val KEY_FIBER_GOAL = intPreferencesKey("fiber_goal_g")
        private val KEY_WATER_GOAL = intPreferencesKey("water_goal_ml")
        private val KEY_STEP_GOAL = intPreferencesKey("step_goal")
        private val KEY_UNITS = stringPreferencesKey("units")
        private val KEY_CALIBRATION_PCT = intPreferencesKey("personal_calibration_pct")
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")

        private val KEY_THEME_PREF = stringPreferencesKey("theme_preference")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color_enabled")
        private val KEY_CALM_PALETTE = booleanPreferencesKey("calm_palette_enabled")
        private val KEY_FORGIVE_ONE_DAY = booleanPreferencesKey("forgive_one_missed_day_per_week")
        private val KEY_HIDE_NUMBERS = booleanPreferencesKey("hide_numbers_and_streaks")
        private val KEY_PRIVACY_DISCLOSED = booleanPreferencesKey("privacy_disclosed_before_scan")
    }

    val userProfileFlow: Flow<UserProfile> = context.userPrefsDataStore.data.map { prefs ->
        val calGoal = prefs[KEY_CALORIE_GOAL] ?: 2130
        UserProfile(
            sex = runCatching { BiologicalSex.valueOf(prefs[KEY_SEX] ?: "MALE") }.getOrDefault(BiologicalSex.MALE),
            birthYear = prefs[KEY_BIRTH_YEAR] ?: 1998,
            heightCm = prefs[KEY_HEIGHT_CM] ?: 178.0,
            weightKg = prefs[KEY_WEIGHT_KG] ?: 75.0,
            targetWeightKg = prefs[KEY_TARGET_WEIGHT_KG] ?: 70.0,
            activityLevel = runCatching { ActivityLevel.valueOf(prefs[KEY_ACTIVITY] ?: "MODERATE") }.getOrDefault(ActivityLevel.MODERATE),
            goalType = runCatching { GoalType.valueOf(prefs[KEY_GOAL_TYPE] ?: "LOSE") }.getOrDefault(GoalType.LOSE),
            weeklyChangeKg = prefs[KEY_WEEKLY_CHANGE_KG] ?: -0.5,
            calorieGoal = calGoal,
            kilojouleGoal = EnergyConversion.kcalToKj(calGoal),
            proteinGoalG = prefs[KEY_PROTEIN_GOAL] ?: 135,
            carbsGoalG = prefs[KEY_CARBS_GOAL] ?: 264,
            fatGoalG = prefs[KEY_FAT_GOAL] ?: 59,
            claGoalMg = prefs[KEY_CLA_GOAL] ?: 3200,
            fiberGoalG = prefs[KEY_FIBER_GOAL] ?: 30,
            waterGoalMl = prefs[KEY_WATER_GOAL] ?: 2500,
            stepGoal = prefs[KEY_STEP_GOAL] ?: 10000,
            units = runCatching { UnitSystem.valueOf(prefs[KEY_UNITS] ?: "METRIC") }.getOrDefault(UnitSystem.METRIC),
            personalCalibrationPct = prefs[KEY_CALIBRATION_PCT] ?: 0,
            onboardingCompleted = prefs[KEY_ONBOARDING_DONE] ?: false
        )
    }

    val themePreferenceFlow: Flow<ThemePreference> = context.userPrefsDataStore.data.map { prefs ->
        runCatching { ThemePreference.valueOf(prefs[KEY_THEME_PREF] ?: "SYSTEM") }.getOrDefault(ThemePreference.SYSTEM)
    }

    val dynamicColorFlow: Flow<Boolean> = context.userPrefsDataStore.data.map { prefs ->
        prefs[KEY_DYNAMIC_COLOR] ?: false
    }

    val calmPaletteFlow: Flow<Boolean> = context.userPrefsDataStore.data.map { prefs ->
        prefs[KEY_CALM_PALETTE] ?: false
    }

    val forgiveOneDayFlow: Flow<Boolean> = context.userPrefsDataStore.data.map { prefs ->
        prefs[KEY_FORGIVE_ONE_DAY] ?: false
    }

    val hideNumbersFlow: Flow<Boolean> = context.userPrefsDataStore.data.map { prefs ->
        prefs[KEY_HIDE_NUMBERS] ?: false
    }

    val privacyDisclosedFlow: Flow<Boolean> = context.userPrefsDataStore.data.map { prefs ->
        prefs[KEY_PRIVACY_DISCLOSED] ?: false
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_SEX] = profile.sex.name
            prefs[KEY_BIRTH_YEAR] = profile.birthYear
            prefs[KEY_HEIGHT_CM] = profile.heightCm
            prefs[KEY_WEIGHT_KG] = profile.weightKg
            prefs[KEY_TARGET_WEIGHT_KG] = profile.targetWeightKg
            prefs[KEY_ACTIVITY] = profile.activityLevel.name
            prefs[KEY_GOAL_TYPE] = profile.goalType.name
            prefs[KEY_WEEKLY_CHANGE_KG] = profile.weeklyChangeKg
            prefs[KEY_CALORIE_GOAL] = profile.calorieGoal
            prefs[KEY_PROTEIN_GOAL] = profile.proteinGoalG
            prefs[KEY_CARBS_GOAL] = profile.carbsGoalG
            prefs[KEY_FAT_GOAL] = profile.fatGoalG
            prefs[KEY_CLA_GOAL] = profile.claGoalMg
            prefs[KEY_FIBER_GOAL] = profile.fiberGoalG
            prefs[KEY_WATER_GOAL] = profile.waterGoalMl
            prefs[KEY_STEP_GOAL] = profile.stepGoal
            prefs[KEY_UNITS] = profile.units.name
            prefs[KEY_CALIBRATION_PCT] = profile.personalCalibrationPct
            prefs[KEY_ONBOARDING_DONE] = profile.onboardingCompleted
        }
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.userPrefsDataStore.edit { it[KEY_THEME_PREF] = theme.name }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setCalmPaletteEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_CALM_PALETTE] = enabled }
    }

    suspend fun setForgiveOneDay(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_FORGIVE_ONE_DAY] = enabled }
    }

    suspend fun setHideNumbers(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_HIDE_NUMBERS] = enabled }
    }

    suspend fun setPrivacyDisclosed(disclosed: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_PRIVACY_DISCLOSED] = disclosed }
    }
}
