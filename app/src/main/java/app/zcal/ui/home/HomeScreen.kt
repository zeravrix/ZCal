package app.zcal.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.ui.res.painterResource
import app.zcal.R
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.zcal.data.health.HealthConnectManager
import app.zcal.data.repo.MealRepository
import app.zcal.data.repo.ProfileRepository
import app.zcal.domain.model.ConfidenceLevel
import app.zcal.domain.model.DailyHealthSteps
import app.zcal.domain.model.DaySummary
import app.zcal.domain.model.EnergyConversion
import app.zcal.domain.model.FoodItem
import app.zcal.domain.model.Meal
import app.zcal.domain.model.MealSource
import app.zcal.domain.model.MealType
import app.zcal.domain.model.UserProfile
import app.zcal.domain.usecase.DailyScoreUseCase
import app.zcal.ui.components.CalorieRingCard
import app.zcal.ui.components.MacroRowCards
import app.zcal.ui.components.MealRowCard
import app.zcal.ui.components.ZCalPrimaryButton
import app.zcal.ui.components.ZCalSecondaryButton
import app.zcal.ui.localization.LocalZCalStrings
import app.zcal.ui.theme.LocalZCalColors
import app.zcal.ui.theme.ZCalShapes
import app.zcal.ui.theme.ZCalTypeScale
import app.zcal.util.DateHelpers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val profileRepository: ProfileRepository,
    private val dailyScoreUseCase: DailyScoreUseCase,
    val healthConnectManager: HealthConnectManager
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(DateHelpers.todayLocalDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    val userProfile: StateFlow<UserProfile> = profileRepository.userProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    val hideNumbers: StateFlow<Boolean> = profileRepository.hideNumbersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val mealsForSelectedDate: StateFlow<List<Meal>> = _selectedDate
        .flatMapLatest { date -> mealRepository.observeMealsForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val daySummary: StateFlow<DaySummary?> = _selectedDate
        .flatMapLatest { date -> mealRepository.observeDaySummary(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val waterMl: StateFlow<Int> = _selectedDate
        .flatMapLatest { date -> mealRepository.observeWaterForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingScansCount: StateFlow<Int> = mealRepository.observePendingScans()
        .combine(_selectedDate) { list, _ -> list.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _healthSteps = MutableStateFlow(
        DailyHealthSteps(steps = 0, stepGoal = 10000, activeBurnKcal = 0, activeBurnKj = 0, sourceLabel = "Step Sensor")
    )
    val healthSteps: StateFlow<DailyHealthSteps> = _healthSteps.asStateFlow()

    init {
        refreshHealthConnectSteps()
        viewModelScope.launch {
            healthConnectManager.observeLiveHardwareSteps().collect { sensorSteps ->
                if (_healthSteps.value.sourceLabel != "Health Connect" || sensorSteps > _healthSteps.value.steps) {
                    val kcal = (sensorSteps * 0.04).roundToInt()
                    _healthSteps.value = DailyHealthSteps(
                        steps = sensorSteps,
                        stepGoal = userProfile.value.stepGoal,
                        activeBurnKcal = kcal,
                        activeBurnKj = EnergyConversion.kcalToKj(kcal),
                        sourceLabel = "Device Step Sensor"
                    )
                }
            }
        }
    }

    fun refreshHealthConnectSteps() {
        viewModelScope.launch {
            _healthSteps.value = healthConnectManager.readTodayStepsAndBurn(userProfile.value.stepGoal)
        }
    }

    fun selectDate(dateStr: String) {
        _selectedDate.value = dateStr
    }

    fun computeDailyScore(summary: DaySummary?, profile: UserProfile): DailyScoreUseCase.ScoreBreakdown {
        return dailyScoreUseCase(
            eatenCalories = summary?.kcal ?: 0,
            goalCalories = profile.calorieGoal,
            eatenProteinG = summary?.proteinG ?: 0.0,
            goalProteinG = profile.proteinGoalG,
            eatenCarbsG = summary?.carbsG ?: 0.0,
            goalCarbsG = profile.carbsGoalG,
            eatenFatG = summary?.fatG ?: 0.0,
            goalFatG = profile.fatGoalG
        )
    }

    fun addWater(deltaMl: Int) {
        viewModelScope.launch {
            mealRepository.addWaterMl(_selectedDate.value, deltaMl, waterMl.value)
        }
    }

    fun saveManualMeal(
        name: String,
        calories: Int,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
        mealType: MealType
    ) {
        viewModelScope.launch {
            val item = FoodItem(
                name = name.ifBlank { "Manual Entry" },
                grams = 100.0,
                calories = calories,
                kilojoules = EnergyConversion.kcalToKj(calories),
                proteinG = proteinG,
                carbsG = carbsG,
                fatG = fatG,
                claMg = (fatG * 6.5 * 10.0).roundToInt() / 10.0,
                confidence = ConfidenceLevel.HIGH
            )
            val meal = Meal(
                loggedAtEpochMillis = System.currentTimeMillis(),
                localDate = _selectedDate.value,
                mealType = mealType,
                name = item.name,
                source = MealSource.MANUAL,
                totalCalories = calories,
                totalKilojoules = item.kilojoules,
                proteinG = proteinG,
                carbsG = carbsG,
                fatG = fatG,
                claMg = item.claMg,
                confidence = ConfidenceLevel.HIGH,
                caloriesLow = calories,
                caloriesHigh = calories,
                items = listOf(item)
            )
            mealRepository.saveMeal(meal)
        }
    }

    fun deleteMeal(meal: Meal) {
        viewModelScope.launch {
            mealRepository.deleteMeal(meal)
        }
    }

    fun restoreDeletedMeal(meal: Meal) {
        viewModelScope.launch {
            mealRepository.saveMeal(meal.copy(id = 0L))
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenMealForEdit: (Long) -> Unit,
    onLaunchCamera: () -> Unit
) {
    val colors = LocalZCalColors.current
    val strings = LocalZCalStrings.current
    val selectedDate by viewModel.selectedDate.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    val hideNumbers by viewModel.hideNumbers.collectAsState()
    val meals by viewModel.mealsForSelectedDate.collectAsState()
    val summary by viewModel.daySummary.collectAsState()
    val waterMl by viewModel.waterMl.collectAsState()
    val pendingScansCount by viewModel.pendingScansCount.collectAsState()
    val healthSteps by viewModel.healthSteps.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showManualEntrySheet by remember { mutableStateOf(false) }

    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) {
        viewModel.refreshHealthConnectSteps()
    }

    LaunchedEffect(Unit) {
        viewModel.refreshHealthConnectSteps()
    }

    val dailyScore = remember(summary, profile) {
        viewModel.computeDailyScore(summary, profile)
    }

    val weekDates = remember { DateHelpers.currentWeekDates() }

    Scaffold(
        containerColor = colors.canvas,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo_transparent),
                            contentDescription = "ZCal Logo",
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(strings.dashboard, style = ZCalTypeScale.Headline, color = colors.textPrimary)
                            Text(
                                DateHelpers.formatFriendlyDate(selectedDate),
                                style = ZCalTypeScale.BodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { showManualEntrySheet = true },
                        shape = ZCalShapes.Pill
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text(strings.manualEntry, modifier = Modifier.padding(start = 4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    weekDates.forEach { date ->
                        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        val isSelected = dateStr == selectedDate
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 3.dp)
                                .clickable { viewModel.selectDate(dateStr) },
                            shape = ZCalShapes.Chip,
                            color = if (isSelected) colors.accent else colors.surfaceElevated,
                            shadowElevation = if (isSelected) 3.dp else 1.dp,
                            border = BorderStroke(1.dp, if (isSelected) colors.accent else colors.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = date.dayOfWeek.name.take(1),
                                    style = ZCalTypeScale.Label,
                                    color = if (isSelected) colors.onAccent else colors.textSecondary
                                )
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = ZCalTypeScale.BodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) colors.onAccent else colors.textPrimary
                                )
                            }
                        }
                    }
                }
            }

            if (pendingScansCount > 0) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ZCalShapes.Card,
                        color = colors.surfaceVariant,
                        border = BorderStroke(1.dp, colors.accent)
                    ) {
                        Text(
                            text = "$pendingScansCount offline meal photo(s) queued for automatic AI analysis.",
                            style = ZCalTypeScale.BodySmall,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // Calorie Ring + Kilojoules (kJ) & CLA Summary
            item {
                CalorieRingCard(
                    eatenCalories = summary?.kcal ?: 0,
                    goalCalories = profile.calorieGoal,
                    hideNumbers = hideNumbers
                )
                Spacer(modifier = Modifier.height(8.dp))
                val eatenKj = summary?.kj ?: 0
                val eatenCla = (summary?.claMg ?: 0.0).roundToInt()
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ZCalShapes.Card,
                    color = colors.surfaceElevated,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Energy: %,d / %,d kJ".format(eatenKj, profile.kilojouleGoal),
                            style = ZCalTypeScale.BodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary
                        )
                        Text(
                            text = "CLA: %,d / %,d mg".format(eatenCla, profile.claGoalMg),
                            style = ZCalTypeScale.BodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.accent
                        )
                    }
                }
            }

            // Three Macro Cards (P, C, F)
            item {
                MacroRowCards(
                    proteinEatenG = summary?.proteinG ?: 0.0,
                    proteinGoalG = profile.proteinGoalG,
                    carbsEatenG = summary?.carbsG ?: 0.0,
                    carbsGoalG = profile.carbsGoalG,
                    fatEatenG = summary?.fatG ?: 0.0,
                    fatGoalG = profile.fatGoalG,
                    hideNumbers = hideNumbers
                )
            }

            // Health Connect Steps & Active Burn Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ZCalShapes.Card,
                    color = colors.surfaceElevated,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, contentDescription = null, tint = colors.accent)
                                Text("Health App Steps (${healthSteps.sourceLabel})", style = ZCalTypeScale.Label, color = colors.textSecondary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "%,d / %,d steps".format(healthSteps.steps, healthSteps.stepGoal),
                                style = ZCalTypeScale.Title,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Active burn: ${healthSteps.activeBurnKcal} kcal (${healthSteps.activeBurnKj} kJ)",
                                style = ZCalTypeScale.Label,
                                color = colors.accent
                            )
                        }

                        if (viewModel.healthConnectManager.isHealthConnectAvailable()) {
                            OutlinedButton(
                                onClick = {
                                    healthConnectPermissionLauncher.launch(viewModel.healthConnectManager.requiredPermissions)
                                },
                                shape = ZCalShapes.Pill
                            ) {
                                Text("Sync Health")
                            }
                        }
                    }
                }
            }

            // Daily Score & Water Tracker Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = ZCalShapes.Card,
                        color = colors.surfaceElevated,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.7f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = colors.accent
                                )
                                Text(strings.dailyScore, style = ZCalTypeScale.Label, color = colors.textSecondary)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (hideNumbers) "•• / 10" else "${dailyScore.totalScore} / 10",
                                style = ZCalTypeScale.Title,
                                color = colors.textPrimary
                            )
                            Text(
                                text = dailyScore.summaryLabel,
                                style = ZCalTypeScale.Label,
                                color = colors.textSecondary
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = ZCalShapes.Card,
                        color = colors.surfaceElevated,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.7f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LocalDrink,
                                    contentDescription = null,
                                    tint = colors.fat
                                )
                                Text(strings.hydration, style = ZCalTypeScale.Label, color = colors.textSecondary)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "$waterMl / ${profile.waterGoalMl} ml",
                                style = ZCalTypeScale.Title,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "+250 ml glass",
                                style = ZCalTypeScale.Label.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.accent,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable { viewModel.addWater(250) }
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "${strings.loggedMeals} (${meals.size})",
                    style = ZCalTypeScale.Title,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (meals.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ZCalShapes.Card,
                        color = colors.surfaceElevated,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.7f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = strings.noMealsYet,
                                style = ZCalTypeScale.Body,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            ZCalPrimaryButton(
                                text = strings.openCamera,
                                onClick = onLaunchCamera,
                                modifier = Modifier.fillMaxWidth(0.85f)
                            )
                        }
                    }
                }
            } else {
                items(meals, key = { it.id }) { meal ->
                    MealRowCard(
                        meal = meal,
                        hideNumbers = hideNumbers,
                        onClick = { onOpenMealForEdit(meal.id) },
                        onDelete = {
                            viewModel.deleteMeal(meal)
                            scope.launch {
                                val res = snackbarHostState.showSnackbar(
                                    message = "Deleted ${meal.name}",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (res == SnackbarResult.ActionPerformed) {
                                    viewModel.restoreDeletedMeal(meal)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showManualEntrySheet) {
        ManualMealEntryBottomSheet(
            onDismiss = { showManualEntrySheet = false },
            onSave = { name, kcal, p, c, f, type ->
                viewModel.saveManualMeal(name, kcal, p, c, f, type)
                showManualEntrySheet = false
            }
        )
    }
}

@Composable
private fun ManualMealEntryBottomSheet(
    onDismiss: () -> Unit,
    onSave: (String, Int, Double, Double, Double, MealType) -> Unit
) {
    val colors = LocalZCalColors.current
    val strings = LocalZCalStrings.current
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var mealType by remember {
        mutableStateOf(MealType.fromHourOfDay(LocalTime.now().hour))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = ZCalShapes.SheetTop,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Log Known Meal Manually", style = ZCalTypeScale.Title, color = colors.textPrimary)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealType.entries.forEach { mt ->
                    FilterChip(
                        selected = mealType == mt,
                        onClick = { mealType = mt },
                        label = { Text(mt.label) }
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(strings.foodName) },
                shape = ZCalShapes.Input,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = calories,
                onValueChange = { calories = it },
                label = { Text("Calories (kcal)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = ZCalShapes.Input,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = protein,
                    onValueChange = { protein = it },
                    label = { Text("Protein (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = ZCalShapes.Input,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = carbs,
                    onValueChange = { carbs = it },
                    label = { Text("Carbs (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = ZCalShapes.Input,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = fat,
                    onValueChange = { fat = it },
                    label = { Text("Fat (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = ZCalShapes.Input,
                    modifier = Modifier.weight(1f)
                )
            }

            ZCalPrimaryButton(
                text = strings.save,
                onClick = {
                    onSave(
                        name.trim().ifEmpty { "Manual Meal" },
                        calories.toIntOrNull() ?: 0,
                        protein.toDoubleOrNull() ?: 0.0,
                        carbs.toDoubleOrNull() ?: 0.0,
                        fat.toDoubleOrNull() ?: 0.0,
                        mealType
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
