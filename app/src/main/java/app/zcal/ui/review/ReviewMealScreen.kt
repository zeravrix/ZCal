package app.zcal.ui.review

import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Replay
import app.zcal.ui.localization.LocalZCalStrings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import app.zcal.data.ai.FoodAnalysisResult
import app.zcal.data.repo.MealRepository
import app.zcal.data.repo.ProfileRepository
import app.zcal.domain.model.AnalyzedFoodItemDto
import app.zcal.domain.model.ConfidenceLevel
import app.zcal.domain.model.EnergyConversion
import app.zcal.domain.model.FoodItem
import app.zcal.domain.model.Meal
import app.zcal.domain.model.MealAnalysis
import app.zcal.domain.model.MealSource
import app.zcal.domain.model.MealType
import app.zcal.domain.usecase.AnalyzeMealUseCase
import app.zcal.ui.components.ConfidenceChip
import app.zcal.ui.components.ZCalPrimaryButton
import app.zcal.ui.components.ZCalSecondaryButton
import app.zcal.ui.theme.LocalZCalColors
import app.zcal.ui.theme.ZCalShapes
import app.zcal.ui.theme.ZCalTypeScale
import app.zcal.util.DateHelpers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime
import javax.inject.Inject
import kotlin.math.roundToInt

sealed class CaptureWorkflowState {
    data object Idle : CaptureWorkflowState()
    data class Analyzing(val photoFile: File?) : CaptureWorkflowState()
    data class Error(
        val title: String,
        val message: String,
        val isKeyError: Boolean = false,
        val isOfflineQueued: Boolean = false
    ) : CaptureWorkflowState()
    data object ReadyForReview : CaptureWorkflowState()
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val analyzeMealUseCase: AnalyzeMealUseCase,
    private val mealRepository: MealRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _workflowState = MutableStateFlow<CaptureWorkflowState>(CaptureWorkflowState.Idle)
    val workflowState: StateFlow<CaptureWorkflowState> = _workflowState.asStateFlow()

    val privacyDisclosed: StateFlow<Boolean> = profileRepository.privacyDisclosedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _draftMeal = MutableStateFlow(createEmptyDraftMeal())
    val draftMeal: StateFlow<Meal> = _draftMeal.asStateFlow()

    private var lastPhotoFile: File? = null
    private var lastUserNote: String? = null

    fun acknowledgePrivacyDisclosure() {
        viewModelScope.launch {
            profileRepository.setPrivacyDisclosed(true)
        }
    }

    fun resetToIdle() {
        _workflowState.value = CaptureWorkflowState.Idle
    }

    fun analyzePhotoFile(file: File, userNote: String?) {
        lastPhotoFile = file
        lastUserNote = userNote
        viewModelScope.launch {
            _workflowState.value = CaptureWorkflowState.Analyzing(file)
            handleAnalysisResult(
                result = analyzeMealUseCase.analyzePhoto(listOf(file), userNote),
                photoPath = file.absolutePath,
                note = userNote
            )
        }
    }

    fun retryLastScan() {
        val file = lastPhotoFile
        if (file != null) {
            analyzePhotoFile(file, lastUserNote)
        } else {
            startManualFallback(lastUserNote)
        }
    }

    fun analyzeBarcodeString(barcode: String, fallbackPhoto: File? = null) {
        viewModelScope.launch {
            _workflowState.value = CaptureWorkflowState.Analyzing(fallbackPhoto)
            val item = mealRepository.lookupBarcodeCached(barcode)
            if (item != null) {
                val now = System.currentTimeMillis()
                _draftMeal.value = Meal(
                    loggedAtEpochMillis = now,
                    localDate = DateHelpers.todayLocalDateString(),
                    mealType = MealType.fromHourOfDay(LocalTime.now().hour),
                    name = item.name,
                    photoPath = fallbackPhoto?.absolutePath,
                    source = MealSource.BARCODE,
                    totalCalories = item.calories,
                    totalKilojoules = EnergyConversion.kcalToKj(item.calories),
                    proteinG = item.proteinG,
                    carbsG = item.carbsG,
                    fatG = item.fatG,
                    claMg = (item.fatG * 6.5 * 10.0).roundToInt() / 10.0,
                    fiberG = item.fiberG,
                    confidence = ConfidenceLevel.HIGH,
                    caloriesLow = item.calories,
                    caloriesHigh = item.calories,
                    items = listOf(item)
                )
                _workflowState.value = CaptureWorkflowState.ReadyForReview
            } else if (fallbackPhoto != null) {
                analyzePhotoFile(fallbackPhoto, "Packaged product barcode $barcode")
            } else {
                _workflowState.value = CaptureWorkflowState.Error(
                    title = "Product Not Found in Open Food Facts",
                    message = "Snap a photo of the nutrition label or enter the item manually."
                )
            }
        }
    }

    fun fixWithAi(correctionText: String) {
        val current = _draftMeal.value
        val file = current.photoPath?.let { File(it) }
        val existingDto = MealAnalysis(
            isFood = true,
            mealName = current.name,
            items = current.items.map {
                AnalyzedFoodItemDto(
                    name = it.name,
                    grams = it.grams,
                    calories = it.calories.toDouble(),
                    kilojoules = it.kilojoules.toDouble(),
                    proteinG = it.proteinG,
                    carbsG = it.carbsG,
                    fatG = it.fatG,
                    claMg = it.claMg,
                    fiberG = it.fiberG,
                    confidence = it.confidence
                )
            },
            caloriesLow = current.caloriesLow,
            caloriesHigh = current.caloriesHigh,
            assumptions = current.assumptions
        )
        viewModelScope.launch {
            _workflowState.value = CaptureWorkflowState.Analyzing(file)
            val res = analyzeMealUseCase.analyzePhoto(
                imageFiles = listOfNotNull(file),
                userNote = current.note,
                existingAnalysisToFix = existingDto,
                correctionInstruction = correctionText
            )
            handleAnalysisResult(res, current.photoPath, current.note)
        }
    }

    private fun handleAnalysisResult(
        result: FoodAnalysisResult,
        photoPath: String?,
        note: String?
    ) {
        when (result) {
            is FoodAnalysisResult.Success -> {
                val analysis = result.analysis
                val items = analysis.items.map { dto ->
                    val kcal = dto.calories.roundToInt()
                    FoodItem(
                        name = dto.name,
                        grams = dto.grams,
                        calories = kcal,
                        kilojoules = EnergyConversion.kcalToKj(kcal),
                        proteinG = dto.proteinG,
                        carbsG = dto.carbsG,
                        fatG = dto.fatG,
                        claMg = if (dto.claMg > 0.0) dto.claMg else (dto.fatG * 6.5 * 10.0).roundToInt() / 10.0,
                        fiberG = dto.fiberG,
                        confidence = dto.confidence
                    )
                }
                val worstConfidence = items.minByOrNull {
                    when (it.confidence) {
                        ConfidenceLevel.LOW -> 0
                        ConfidenceLevel.MEDIUM -> 1
                        ConfidenceLevel.HIGH -> 2
                    }
                }?.confidence ?: ConfidenceLevel.MEDIUM

                val totalCal = items.sumOf { it.calories }
                _draftMeal.value = Meal(
                    id = _draftMeal.value.id,
                    loggedAtEpochMillis = System.currentTimeMillis(),
                    localDate = DateHelpers.todayLocalDateString(),
                    mealType = MealType.fromHourOfDay(LocalTime.now().hour),
                    name = analysis.mealName,
                    photoPath = photoPath,
                    source = MealSource.PHOTO,
                    totalCalories = totalCal,
                    totalKilojoules = EnergyConversion.kcalToKj(totalCal),
                    proteinG = items.sumOf { it.proteinG },
                    carbsG = items.sumOf { it.carbsG },
                    fatG = items.sumOf { it.fatG },
                    claMg = items.sumOf { it.claMg },
                    fiberG = items.sumOf { it.fiberG },
                    confidence = worstConfidence,
                    caloriesLow = analysis.caloriesLow,
                    caloriesHigh = analysis.caloriesHigh,
                    note = note,
                    assumptions = analysis.assumptions,
                    rawJson = result.rawJson,
                    items = items
                )
                _workflowState.value = CaptureWorkflowState.ReadyForReview
            }
            is FoodAnalysisResult.NotFood -> {
                _workflowState.value = CaptureWorkflowState.Error(
                    title = "No Food Found",
                    message = "Try another photo or enter manually."
                )
            }
            is FoodAnalysisResult.InvalidKey -> {
                _workflowState.value = CaptureWorkflowState.Error(
                    title = "Invalid Key",
                    message = "Check your API key in Settings.",
                    isKeyError = true
                )
            }
            is FoodAnalysisResult.RateLimited -> {
                _workflowState.value = CaptureWorkflowState.Error(
                    title = "Rate Limited",
                    message = result.detail?.let { "Quota limit hit: $it" }
                        ?: "Rate limit hit. Wait a moment or switch model in Settings.",
                    isKeyError = true
                )
            }
            is FoodAnalysisResult.Offline -> {
                _workflowState.value = CaptureWorkflowState.Error(
                    title = "Offline",
                    message = "Saved. Will analyze once back online.",
                    isOfflineQueued = true
                )
            }
            is FoodAnalysisResult.Timeout -> {
                _workflowState.value = CaptureWorkflowState.Error(
                    title = "Request Timed Out",
                    message = result.message
                )
            }
            is FoodAnalysisResult.MalformedResponse -> {
                _workflowState.value = CaptureWorkflowState.Error(
                    title = "Could Not Read AI Reply",
                    message = result.message
                )
            }
            is FoodAnalysisResult.UnknownError -> {
                _workflowState.value = CaptureWorkflowState.Error(
                    title = "Scan Error",
                    message = result.message
                )
            }
        }
    }

    fun startManualFallback(note: String?) {
        val defaultItem = FoodItem(
            name = note?.takeIf { it.isNotBlank() } ?: "Custom Meal Item",
            grams = 150.0,
            calories = 250,
            kilojoules = EnergyConversion.kcalToKj(250),
            proteinG = 18.0,
            carbsG = 22.0,
            fatG = 10.0,
            claMg = 65.0,
            confidence = ConfidenceLevel.HIGH
        )
        _draftMeal.value = createEmptyDraftMeal().copy(
            name = defaultItem.name,
            photoPath = lastPhotoFile?.absolutePath,
            source = MealSource.MANUAL,
            totalCalories = defaultItem.calories,
            totalKilojoules = defaultItem.kilojoules,
            proteinG = defaultItem.proteinG,
            carbsG = defaultItem.carbsG,
            fatG = defaultItem.fatG,
            claMg = defaultItem.claMg,
            caloriesLow = defaultItem.calories,
            caloriesHigh = defaultItem.calories,
            items = listOf(defaultItem)
        )
        _workflowState.value = CaptureWorkflowState.ReadyForReview
    }

    fun loadExistingMealForEdit(mealId: Long) {
        viewModelScope.launch {
            mealRepository.getMealById(mealId)?.let { existing ->
                _draftMeal.value = existing
                _workflowState.value = CaptureWorkflowState.ReadyForReview
            }
        }
    }

    fun updateMealName(newName: String) {
        _draftMeal.value = _draftMeal.value.copy(name = newName)
    }

    fun updateMealType(newType: MealType) {
        _draftMeal.value = _draftMeal.value.copy(mealType = newType)
    }

    fun updateItemGrams(index: Int, newGrams: Double) {
        val current = _draftMeal.value
        val updatedItems = current.items.toMutableList()
        if (index in updatedItems.indices) {
            updatedItems[index] = updatedItems[index].rescaleToGrams(newGrams)
            recomputeDraftTotals(updatedItems)
        }
    }

    fun updateItemName(index: Int, newName: String) {
        val current = _draftMeal.value
        val updatedItems = current.items.toMutableList()
        if (index in updatedItems.indices) {
            updatedItems[index] = updatedItems[index].copy(name = newName)
            _draftMeal.value = current.copy(items = updatedItems)
        }
    }

    fun applyServingMultiplier(multiplier: Double) {
        val updatedItems = _draftMeal.value.items.map { item ->
            item.rescaleToGrams(item.grams * multiplier)
        }
        recomputeDraftTotals(updatedItems)
    }

    fun removeItem(index: Int) {
        val updatedItems = _draftMeal.value.items.toMutableList().apply {
            if (index in indices) removeAt(index)
        }
        recomputeDraftTotals(updatedItems)
    }

    fun addBlankItem() {
        val updatedItems = _draftMeal.value.items + FoodItem(
            name = "Added Ingredient",
            grams = 50.0,
            calories = 100,
            kilojoules = EnergyConversion.kcalToKj(100),
            proteinG = 5.0,
            carbsG = 10.0,
            fatG = 4.0,
            claMg = 26.0,
            confidence = ConfidenceLevel.HIGH
        )
        recomputeDraftTotals(updatedItems)
    }

    private fun recomputeDraftTotals(items: List<FoodItem>) {
        val current = _draftMeal.value
        val totalKcal = items.sumOf { it.calories }
        val totalKj = EnergyConversion.kcalToKj(totalKcal)
        val p = items.sumOf { it.proteinG }
        val c = items.sumOf { it.carbsG }
        val f = items.sumOf { it.fatG }
        val cla = items.sumOf { it.claMg }
        val fib = items.sumOf { it.fiberG }
        val spread = (totalKcal * current.confidence.rangeFraction).roundToInt()

        _draftMeal.value = current.copy(
            items = items,
            totalCalories = totalKcal,
            totalKilojoules = totalKj,
            proteinG = (p * 10.0).roundToInt() / 10.0,
            carbsG = (c * 10.0).roundToInt() / 10.0,
            fatG = (f * 10.0).roundToInt() / 10.0,
            claMg = (cla * 10.0).roundToInt() / 10.0,
            fiberG = (fib * 10.0).roundToInt() / 10.0,
            caloriesLow = (totalKcal - spread).coerceAtLeast(0),
            caloriesHigh = totalKcal + spread
        )
    }

    fun saveReviewedMeal(onSaved: () -> Unit) {
        viewModelScope.launch {
            mealRepository.saveMeal(_draftMeal.value)
            _workflowState.value = CaptureWorkflowState.Idle
            onSaved()
        }
    }

    private fun createEmptyDraftMeal() = Meal(
        loggedAtEpochMillis = System.currentTimeMillis(),
        localDate = DateHelpers.todayLocalDateString(),
        mealType = MealType.fromHourOfDay(LocalTime.now().hour),
        name = "Scanned Meal",
        totalCalories = 0,
        totalKilojoules = 0,
        proteinG = 0.0,
        carbsG = 0.0,
        fatG = 0.0,
        claMg = 0.0,
        caloriesLow = 0,
        caloriesHigh = 0
    )
}

/**
 * Post-Capture Animated Breakdown & Review Screen:
 * Shows Fat (g), Protein (g), Carbs (g), CLA (mg), Calories (kcal), and Kilojoules (kJ)
 * with smooth counting animations (`animateIntAsState`) and spring-animated progress bars.
 */
@Composable
fun ReviewMealScreen(
    viewModel: ReviewViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    val colors = LocalZCalColors.current
    val strings = LocalZCalStrings.current
    val haptic = LocalHapticFeedback.current
    val meal by viewModel.draftMeal.collectAsState()
    var fixText by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    val animatedKcal by animateIntAsState(
        targetValue = meal.totalCalories,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "animMealKcal"
    )
    val animatedKj by animateIntAsState(
        targetValue = meal.totalKilojoules,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "animMealKj"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .displayCutoutPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            val photoFile = meal.photoPath?.let { File(it) }
            if (photoFile != null && photoFile.exists()) {
                AsyncImage(
                    model = photoFile,
                    contentDescription = meal.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(ZCalShapes.Hero)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConfidenceChip(confidence = meal.confidence)
                Text(
                    text = "Range: ${meal.caloriesLow}–${meal.caloriesHigh} kcal",
                    style = ZCalTypeScale.Label,
                    color = colors.textSecondary
                )
            }
        }

        // Animated Energy Hero Card (Calories kcal + Kilojoules kJ) & 4 Nutrient Breakdown Cards
        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + slideInVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                ) { it / 4 }
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ZCalShapes.Card,
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        OutlinedTextField(
                            value = meal.name,
                            onValueChange = { viewModel.updateMealName(it) },
                            label = { Text(strings.mealTitleLabel) },
                            shape = ZCalShapes.Input,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MealType.entries.forEach { mt ->
                                FilterChip(
                                    selected = meal.mealType == mt,
                                    onClick = { viewModel.updateMealType(mt) },
                                    label = { Text(mt.label) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Dual Energy Display: Calories (kcal) & Kilojoules (kJ)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = "%,d kcal".format(animatedKcal),
                                    style = ZCalTypeScale.Display,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = strings.calories.uppercase(),
                                    style = ZCalTypeScale.Label,
                                    color = colors.textSecondary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "%,d kJ".format(animatedKj),
                                    style = ZCalTypeScale.Headline,
                                    color = colors.accent
                                )
                                Text(
                                    text = strings.energyKj.uppercase(),
                                    style = ZCalTypeScale.Label,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Animated Nutrient Bars for Protein, Fat, Carbs, and CLA (mg)
                        AnimatedNutrientBar(
                            label = strings.protein,
                            valueText = "${meal.proteinG} g",
                            fraction = (meal.proteinG / 60.0).toFloat().coerceIn(0f, 1f),
                            barColor = colors.protein
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        AnimatedNutrientBar(
                            label = strings.fat,
                            valueText = "${meal.fatG} g",
                            fraction = (meal.fatG / 40.0).toFloat().coerceIn(0f, 1f),
                            barColor = colors.fat
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        AnimatedNutrientBar(
                            label = strings.carbs,
                            valueText = "${meal.carbsG} g",
                            fraction = (meal.carbsG / 100.0).toFloat().coerceIn(0f, 1f),
                            barColor = colors.carbs
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        AnimatedNutrientBar(
                            label = "${strings.cla} & ${strings.fiber}",
                            valueText = "${meal.claMg.roundToInt()} mg · ${meal.fiberG} g",
                            fraction = (meal.claMg / 500.0).toFloat().coerceIn(0.1f, 1f),
                            barColor = colors.accent
                        )
                    }
                }
            }
        }

        // Serving Multiplier Pills (0.5x, 1x, 1.5x, 2x)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.scalePortionLabel, style = ZCalTypeScale.Label, color = colors.textSecondary)
                listOf(0.5 to "0.5x", 1.0 to "1x", 1.5 to "1.5x", 2.0 to "2x").forEach { (mult, label) ->
                    OutlinedButton(
                        onClick = { viewModel.applyServingMultiplier(mult) },
                        shape = ZCalShapes.Pill,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(label, style = ZCalTypeScale.Label)
                    }
                }
            }
        }

        // Editable Food Items with Grams Stepper
        itemsIndexed(meal.items) { idx, item ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = ZCalShapes.Card,
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = item.name,
                            onValueChange = { viewModel.updateItemName(idx, it) },
                            label = { Text(strings.itemNumberLabel.format(idx + 1)) },
                            shape = ZCalShapes.Input,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.removeItem(idx) }) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove ${item.name}",
                                tint = colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    viewModel.updateItemGrams(idx, (item.grams - 15.0).coerceAtLeast(5.0))
                                }
                            ) {
                                Icon(Icons.Outlined.Remove, contentDescription = "Decrease grams")
                            }
                            Text(
                                text = "${item.grams.roundToInt()} g",
                                style = ZCalTypeScale.Title,
                                color = colors.textPrimary
                            )
                            IconButton(
                                onClick = {
                                    viewModel.updateItemGrams(idx, item.grams + 15.0)
                                }
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = "Increase grams")
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${item.calories} kcal",
                                style = ZCalTypeScale.Title,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "P ${item.proteinG.roundToInt()}g · C ${item.carbsG.roundToInt()}g · F ${item.fatG.roundToInt()}g",
                                style = ZCalTypeScale.Label,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { viewModel.addBlankItem() },
                shape = ZCalShapes.Pill,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.addMissingItemBtn)
            }
        }

        // Fix with AI Box
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = ZCalShapes.Card,
                color = colors.surfaceVariant,
                border = BorderStroke(1.dp, colors.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(strings.fixWithAiTitle, style = ZCalTypeScale.Title, color = colors.textPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = fixText,
                            onValueChange = { fixText = it },
                            placeholder = { Text(strings.fixWithAiHint) },
                            shape = ZCalShapes.Input,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (fixText.isNotBlank()) {
                                    viewModel.fixWithAi(fixText)
                                    fixText = ""
                                }
                            },
                            shape = ZCalShapes.Pill
                        ) {
                            Icon(Icons.Outlined.AutoFixHigh, contentDescription = "Fix with AI")
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ZCalSecondaryButton(
                    text = strings.retake,
                    icon = Icons.Outlined.Replay,
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                )
                ZCalPrimaryButton(
                    text = strings.saveMealAndSyncBtn,
                    icon = Icons.Outlined.Check,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.saveReviewedMeal(onSaved)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AnimatedNutrientBar(
    label: String,
    valueText: String,
    fraction: Float,
    barColor: Color
) {
    val colors = LocalZCalColors.current
    val animProgress by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "nutrientSpringBar"
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = ZCalTypeScale.BodySmall, color = colors.textSecondary)
            Text(valueText, style = ZCalTypeScale.BodySmall.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(ZCalShapes.Pill)
                .background(colors.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animProgress)
                    .fillMaxHeight()
                    .clip(ZCalShapes.Pill)
                    .background(barColor)
            )
        }
    }
}
