package app.zcal.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.zcal.R
import app.zcal.data.ai.FoodAnalyzer
import app.zcal.data.ai.KeyVerificationResult
import app.zcal.data.repo.ProfileRepository
import app.zcal.data.repo.WeightRepository
import app.zcal.data.secure.KeyVault
import app.zcal.domain.model.ActivityLevel
import app.zcal.domain.model.AiProviderType
import app.zcal.domain.model.BiologicalSex
import app.zcal.domain.model.GoalType
import app.zcal.domain.model.Goals
import app.zcal.domain.model.UnitSystem
import app.zcal.domain.model.UserProfile
import app.zcal.domain.usecase.ComputeGoalsUseCase
import app.zcal.ui.components.ZCalPrimaryButton
import app.zcal.ui.components.ZCalSecondaryButton
import app.zcal.ui.localization.LocalZCalStrings
import app.zcal.ui.theme.LocalZCalColors
import app.zcal.ui.theme.ZCalShapes
import app.zcal.ui.theme.ZCalTypeScale
import app.zcal.util.DateHelpers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Year
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val computeGoalsUseCase: ComputeGoalsUseCase,
    private val profileRepository: ProfileRepository,
    private val weightRepository: WeightRepository,
    private val keyVault: KeyVault,
    private val foodAnalyzer: FoodAnalyzer
) : ViewModel() {

    private val _profileDraft = MutableStateFlow(UserProfile())
    val profileDraft: StateFlow<UserProfile> = _profileDraft.asStateFlow()

    private val _computedGoals = MutableStateFlow(computeGoalsFromDraft(UserProfile()))
    val computedGoals: StateFlow<Goals> = _computedGoals.asStateFlow()

    private val _keyTestStatus = MutableStateFlow<String?>(null)
    val keyTestStatus: StateFlow<String?> = _keyTestStatus.asStateFlow()

    private val _isVerifyingKey = MutableStateFlow(false)
    val isVerifyingKey: StateFlow<Boolean> = _isVerifyingKey.asStateFlow()

    private fun computeGoalsFromDraft(draft: UserProfile): Goals {
        return computeGoalsUseCase.fromProfile(draft)
    }

    fun updateDraft(transform: (UserProfile) -> UserProfile) {
        val next = transform(_profileDraft.value)
        val goals = computeGoalsFromDraft(next)
        _profileDraft.value = next.copy(
            calorieGoal = goals.dailyCalories,
            kilojouleGoal = goals.dailyKilojoules,
            proteinGoalG = goals.proteinG,
            carbsGoalG = goals.carbsG,
            fatGoalG = goals.fatG,
            claGoalMg = goals.claMg,
            fiberGoalG = goals.fiberG
        )
        _computedGoals.value = goals
    }

    fun optimizePlanWithAi() {
        val current = _profileDraft.value
        val (proteinPerKg, fatShare) = when (current.goalType) {
            GoalType.LOSE -> Pair(2.2, 0.22)
            GoalType.GAIN -> Pair(2.0, 0.25)
            GoalType.MAINTAIN -> Pair(1.9, 0.26)
        }
        val optimized = computeGoalsUseCase(
            sex = current.sex,
            birthYear = current.birthYear,
            birthMonth = current.birthMonth,
            birthDay = current.birthDay,
            heightCm = current.heightCm,
            weightKg = current.weightKg,
            activityLevel = current.activityLevel,
            goalType = current.goalType,
            weeklyChangeKg = current.weeklyChangeKg,
            proteinPerKg = proteinPerKg,
            fatCalorieShare = fatShare
        )
        _profileDraft.value = current.copy(
            calorieGoal = optimized.dailyCalories,
            kilojouleGoal = optimized.dailyKilojoules,
            proteinGoalG = optimized.proteinG,
            carbsGoalG = optimized.carbsG,
            fatGoalG = optimized.fatG,
            claGoalMg = optimized.claMg,
            fiberGoalG = optimized.fiberG
        )
        _computedGoals.value = optimized
    }

    fun verifyAndSaveKey(
        provider: AiProviderType,
        apiKey: String,
        modelId: String,
        baseUrl: String
    ) {
        viewModelScope.launch {
            _isVerifyingKey.value = true
            _keyTestStatus.value = "Testing..."
            keyVault.updateProviderConfig(provider, modelId, baseUrl)
            when (val res = foodAnalyzer.verifyApiKey(apiKey, modelId, baseUrl)) {
                is KeyVerificationResult.Valid -> {
                    keyVault.saveApiKey(provider, apiKey)
                    _keyTestStatus.value = "Verified ${res.modelUsed} (${res.latencyMs}ms)"
                }
                is KeyVerificationResult.Invalid -> {
                    _keyTestStatus.value = "Failed: ${res.errorMessage}"
                }
            }
            _isVerifyingKey.value = false
        }
    }

    fun finishOnboarding(onCompleted: () -> Unit) {
        viewModelScope.launch {
            val finalProfile = _profileDraft.value.copy(onboardingCompleted = true)
            profileRepository.saveUserProfile(finalProfile)
            weightRepository.logWeightForDate(DateHelpers.todayLocalDateString(), finalProfile.weightKg)
            onCompleted()
        }
    }
}

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit
) {
    val colors = LocalZCalColors.current
    val strings = LocalZCalStrings.current
    val draft by viewModel.profileDraft.collectAsState()
    val goals by viewModel.computedGoals.collectAsState()
    val keyStatus by viewModel.keyTestStatus.collectAsState()
    val isVerifying by viewModel.isVerifyingKey.collectAsState()
    val context = LocalContext.current

    var step by remember { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) {
        step--
    }
    val totalSteps = 4
    var selectedPeriodTab by remember { mutableIntStateOf(0) }

    var selectedProvider by remember { mutableStateOf(AiProviderType.GEMINI) }
    var apiKeyInput by remember { mutableStateOf("") }
    var modelInput by remember { mutableStateOf(AiProviderType.GEMINI.defaultModel) }
    var baseUrlInput by remember { mutableStateOf(AiProviderType.GEMINI.defaultBaseUrl) }

    val currentYear = remember { Year.now().value }
    val currentAge = remember(draft.birthYear, draft.birthMonth, draft.birthDay) { draft.computeCurrentAge() }
    val coroutineScope = rememberCoroutineScope()
    var isOptimizingPlan by remember { mutableStateOf(false) }
    var optimizeSuccessMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header: Step Indicator & Progress
        Column {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(strings.stepOf, step + 1, totalSteps).uppercase(),
                    style = ZCalTypeScale.Label.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = colors.accent
                )
                Text(
                    text = when (step) {
                        0 -> strings.welcomeTitle
                        1 -> strings.goalTitle
                        2 -> strings.aiTitle
                        else -> strings.planTitle
                    },
                    style = ZCalTypeScale.Label,
                    color = colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val animatedProgress by animateFloatAsState(
                targetValue = (step + 1).toFloat() / totalSteps.toFloat(),
                animationSpec = spring(),
                label = "onboardingProgress"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(ZCalShapes.Pill)
                    .background(colors.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(5.dp)
                        .clip(ZCalShapes.Pill)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(colors.accent, colors.secondary)
                            )
                        )
                )
            }
        }

        // Animated Body
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width / 2 } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width / 2 } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width -> -width / 2 } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width / 2 } + fadeOut()
                        )
                    }
                },
                label = "onboardingContentSlide"
            ) { currentStep ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp)
                ) {
                    when (currentStep) {
                        0 -> {
                            // Step 0: Body Metrics & Age Slider
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.app_logo_transparent),
                                    contentDescription = "ZCal Logo",
                                    modifier = Modifier.size(52.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = strings.welcomeTitle,
                                        style = ZCalTypeScale.Headline,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = strings.welcomeSubtitle,
                                        style = ZCalTypeScale.BodySmall,
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Biological Sex Selection: Only MALE & FEMALE (Other removed per user request)
                            Text(
                                text = strings.biologicalSex,
                                style = ZCalTypeScale.Title,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                listOf(BiologicalSex.MALE, BiologicalSex.FEMALE).forEach { sex ->
                                    val isSelected = draft.sex == sex
                                    InteractiveSelectCard(
                                        title = if (sex == BiologicalSex.MALE) strings.male else strings.female,
                                        icon = if (sex == BiologicalSex.MALE) Icons.Filled.Male else Icons.Filled.Female,
                                        isSelected = isSelected,
                                        onClick = { viewModel.updateDraft { it.copy(sex = sex) } },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Unit System
                            Text(
                                text = strings.units,
                                style = ZCalTypeScale.Title,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                UnitSystem.entries.forEach { unit ->
                                    PillBadgeToggle(
                                        label = if (unit == UnitSystem.METRIC) strings.metric else strings.imperial,
                                        isSelected = draft.units == unit,
                                        onClick = { viewModel.updateDraft { it.copy(units = unit) } },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Birthday & Exact Age Card (Day, Month, Year Sliders with Magnetic Detents)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = ZCalShapes.Card,
                                color = colors.surfaceElevated,
                                border = BorderStroke(1.dp, colors.outline)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(strings.birthday, style = ZCalTypeScale.Title, color = colors.textPrimary)
                                        Text(
                                            String.format(strings.years, currentAge),
                                            style = ZCalTypeScale.Title.copy(fontWeight = FontWeight.Bold),
                                            color = colors.accent
                                        )
                                    }

                                    // Year Slider (1950 .. currentYear - 14)
                                    MetricSliderCard(
                                        label = strings.year,
                                        valueText = "${draft.birthYear}",
                                        value = draft.birthYear.toFloat(),
                                        range = 1950f..(currentYear - 14).toFloat(),
                                        stepSize = 1f,
                                        snapToFive = true,
                                        onValueChange = { y ->
                                            viewModel.updateDraft { it.copy(birthYear = y.roundToInt()) }
                                        }
                                    )

                                    val monthNames = listOf("Jan (1)", "Feb (2)", "Mar (3)", "Apr (4)", "May (5)", "Jun (6)", "Jul (7)", "Aug (8)", "Sep (9)", "Oct (10)", "Nov (11)", "Dec (12)")
                                    val safeMonth = draft.birthMonth.coerceIn(1, 12)
                                    // Month Slider (1 .. 12)
                                    MetricSliderCard(
                                        label = strings.month,
                                        valueText = monthNames[safeMonth - 1],
                                        value = safeMonth.toFloat(),
                                        range = 1f..12f,
                                        stepSize = 1f,
                                        snapToFive = false,
                                        onValueChange = { m ->
                                            val newMonth = m.roundToInt().coerceIn(1, 12)
                                            val maxDays = try { java.time.YearMonth.of(draft.birthYear, newMonth).lengthOfMonth() } catch (_: Exception) { 31 }
                                            viewModel.updateDraft { it.copy(birthMonth = newMonth, birthDay = it.birthDay.coerceIn(1, maxDays)) }
                                        }
                                    )

                                    val maxDaysInCurrentMonth = try { java.time.YearMonth.of(draft.birthYear, safeMonth).lengthOfMonth() } catch (_: Exception) { 31 }
                                    // Day Slider (1 .. maxDaysInMonth)
                                    MetricSliderCard(
                                        label = strings.day,
                                        valueText = "${draft.birthDay.coerceIn(1, maxDaysInCurrentMonth)}",
                                        value = draft.birthDay.coerceIn(1, maxDaysInCurrentMonth).toFloat(),
                                        range = 1f..maxDaysInCurrentMonth.toFloat(),
                                        stepSize = 1f,
                                        snapToFive = true,
                                        onValueChange = { d ->
                                            viewModel.updateDraft { it.copy(birthDay = d.roundToInt().coerceIn(1, maxDaysInCurrentMonth)) }
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val isImperial = draft.units == UnitSystem.IMPERIAL

                            // Height Slider
                            if (isImperial) {
                                val hInches = (draft.heightCm / 2.54).toFloat().coerceIn(51f, 87f)
                                val totalInches = hInches.roundToInt()
                                MetricSliderCard(
                                    label = "${strings.height} (ft/in)",
                                    valueText = "${totalInches / 12}' ${totalInches % 12}\" ($totalInches in)",
                                    value = hInches,
                                    range = 51f..87f,
                                    stepSize = 1f,
                                    snapToFive = false,
                                    onValueChange = { h -> viewModel.updateDraft { it.copy(heightCm = h * 2.54) } }
                                )
                            } else {
                                MetricSliderCard(
                                    label = strings.height,
                                    valueText = "${draft.heightCm.roundToInt()} cm",
                                    value = draft.heightCm.toFloat().coerceIn(130f, 220f),
                                    range = 130f..220f,
                                    stepSize = 1f,
                                    snapToFive = true,
                                    onValueChange = { h -> viewModel.updateDraft { it.copy(heightCm = h.toDouble()) } }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Current Weight Slider
                            if (isImperial) {
                                val wLbs = (draft.weightKg * 2.20462).toFloat().coerceIn(88f, 396f)
                                MetricSliderCard(
                                    label = "${strings.currentWeight} (lbs)",
                                    valueText = "${String.format(java.util.Locale.US, "%.1f", wLbs)} lbs",
                                    value = wLbs,
                                    range = 88f..396f,
                                    stepSize = 1f,
                                    snapToFive = true,
                                    onValueChange = { w -> viewModel.updateDraft { it.copy(weightKg = w / 2.20462) } }
                                )
                            } else {
                                MetricSliderCard(
                                    label = strings.currentWeight,
                                    valueText = "${String.format(java.util.Locale.US, "%.1f", draft.weightKg)} kg",
                                    value = draft.weightKg.toFloat().coerceIn(40f, 180f),
                                    range = 40f..180f,
                                    stepSize = 0.5f,
                                    snapToFive = true,
                                    onValueChange = { w -> viewModel.updateDraft { it.copy(weightKg = w.toDouble()) } }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Target Weight Slider
                            if (isImperial) {
                                val twLbs = (draft.targetWeightKg * 2.20462).toFloat().coerceIn(88f, 396f)
                                MetricSliderCard(
                                    label = "${strings.targetWeight} (lbs)",
                                    valueText = "${String.format(java.util.Locale.US, "%.1f", twLbs)} lbs",
                                    value = twLbs,
                                    range = 88f..396f,
                                    stepSize = 1f,
                                    snapToFive = true,
                                    onValueChange = { tw -> viewModel.updateDraft { it.copy(targetWeightKg = tw / 2.20462) } }
                                )
                            } else {
                                MetricSliderCard(
                                    label = strings.targetWeight,
                                    valueText = "${String.format(java.util.Locale.US, "%.1f", draft.targetWeightKg)} kg",
                                    value = draft.targetWeightKg.toFloat().coerceIn(40f, 180f),
                                    range = 40f..180f,
                                    stepSize = 0.5f,
                                    snapToFive = true,
                                    onValueChange = { tw -> viewModel.updateDraft { it.copy(targetWeightKg = tw.toDouble()) } }
                                )
                            }
                        }

                        1 -> {
                            // Step 1: Goal & Activity
                            Text(
                                text = strings.goalTitle,
                                style = ZCalTypeScale.Headline,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = strings.goalSubtitle,
                                style = ZCalTypeScale.BodySmall,
                                color = colors.textSecondary
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            GoalType.entries.forEach { gt ->
                                val (title, desc, icon) = when (gt) {
                                    GoalType.LOSE -> Triple(strings.loseWeight, strings.loseWeightDesc, Icons.AutoMirrored.Filled.TrendingDown)
                                    GoalType.MAINTAIN -> Triple(strings.maintainWeight, strings.maintainWeightDesc, Icons.Filled.FitnessCenter)
                                    GoalType.GAIN -> Triple(strings.buildMuscle, strings.buildMuscleDesc, Icons.AutoMirrored.Filled.TrendingUp)
                                }

                                LargeSelectionCard(
                                    title = title,
                                    subtitle = desc,
                                    icon = icon,
                                    isSelected = draft.goalType == gt,
                                    onClick = { viewModel.updateDraft { it.copy(goalType = gt) } },
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }

                            if (draft.goalType != GoalType.MAINTAIN) {
                                Spacer(modifier = Modifier.height(10.dp))
                                val isImperial = draft.units == UnitSystem.IMPERIAL
                                if (isImperial) {
                                    val absPaceLbs = (kotlin.math.abs(draft.weeklyChangeKg) * 2.20462).toFloat().coerceIn(0.22f, 2.65f)
                                    MetricSliderCard(
                                        label = "${strings.weeklyPace} (lbs/week)",
                                        valueText = "${String.format(java.util.Locale.US, "%.2f", absPaceLbs)} lbs / week",
                                        value = absPaceLbs,
                                        range = 0.22f..2.65f,
                                        stepSize = 0.1f,
                                        snapToFive = false,
                                        onValueChange = { pLbs ->
                                            val sign = if (draft.goalType == GoalType.LOSE) -1.0 else 1.0
                                            viewModel.updateDraft { it.copy(weeklyChangeKg = sign * (pLbs / 2.20462)) }
                                        }
                                    )
                                } else {
                                    val absPace = kotlin.math.abs(draft.weeklyChangeKg).toFloat().coerceIn(0.1f, 1.2f)
                                    MetricSliderCard(
                                        label = strings.weeklyPace,
                                        valueText = "${String.format(java.util.Locale.US, "%.2f", absPace)} kg / week",
                                        value = absPace,
                                        range = 0.1f..1.2f,
                                        stepSize = 0.05f,
                                        snapToFive = false,
                                        onValueChange = { p ->
                                            val sign = if (draft.goalType == GoalType.LOSE) -1.0 else 1.0
                                            viewModel.updateDraft { it.copy(weeklyChangeKg = sign * p.toDouble()) }
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = strings.activityLevel,
                                style = ZCalTypeScale.Title,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            ActivityLevel.entries.forEach { level ->
                                val (label, desc, icon) = when (level) {
                                    ActivityLevel.SEDENTARY -> Triple(strings.sedentary, level.description, Icons.Filled.Nightlife)
                                    ActivityLevel.LIGHT -> Triple(strings.lightlyActive, level.description, Icons.Filled.DirectionsWalk)
                                    ActivityLevel.MODERATE -> Triple(strings.moderatelyActive, level.description, Icons.Filled.DirectionsWalk)
                                    ActivityLevel.ACTIVE -> Triple(strings.veryActive, level.description, Icons.Filled.Speed)
                                    ActivityLevel.EXTRA_ACTIVE -> Triple(strings.extraActive, level.description, Icons.Filled.FitnessCenter)
                                }

                                LargeSelectionCard(
                                    title = label,
                                    subtitle = desc,
                                    icon = icon,
                                    isSelected = draft.activityLevel == level,
                                    onClick = { viewModel.updateDraft { it.copy(activityLevel = level) } },
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }

                        2 -> {
                            // Step 2: AI Vision Setup (Configured before final plan calculation)
                            Text(
                                text = strings.aiTitle,
                                style = ZCalTypeScale.Headline,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = strings.aiSubtitle,
                                style = ZCalTypeScale.BodySmall,
                                color = colors.textSecondary
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AiProviderType.entries.forEach { provider ->
                                    val isSelected = selectedProvider == provider
                                    InteractiveSelectCard(
                                        title = if (provider == AiProviderType.GEMINI) strings.googleGemini else strings.openAiCompatible,
                                        icon = Icons.Filled.Key,
                                        isSelected = isSelected,
                                        onClick = {
                                            selectedProvider = provider
                                            modelInput = provider.defaultModel
                                            baseUrlInput = provider.defaultBaseUrl
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            if (selectedProvider == AiProviderType.GEMINI) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                                            context.startActivity(intent)
                                        },
                                    shape = ZCalShapes.Card,
                                    color = colors.accent.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = strings.needFreeKey,
                                                style = ZCalTypeScale.Title.copy(fontSize = 14.sp),
                                                color = colors.textPrimary
                                            )
                                            Text(
                                                text = strings.openAiStudio,
                                                style = ZCalTypeScale.Label,
                                                color = colors.accent
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                label = { Text(strings.pasteKey) },
                                visualTransformation = PasswordVisualTransformation(),
                                shape = ZCalShapes.Input,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accent,
                                    unfocusedBorderColor = colors.outline,
                                    focusedLabelColor = colors.accent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = modelInput,
                                onValueChange = { modelInput = it },
                                label = { Text(strings.modelName) },
                                shape = ZCalShapes.Input,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accent,
                                    unfocusedBorderColor = colors.outline
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            val quickModels = if (selectedProvider == AiProviderType.GEMINI) {
                                listOf("auto", "gemini-4-hydrogen", "gemini-3.8-flash", "gemini-2.5-flash", "gemini-2.0-flash")
                            } else {
                                listOf("auto", "gpt-4o-mini", "gpt-4.1-mini")
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                quickModels.forEach { qm ->
                                    val isChosen = modelInput.equals(qm, ignoreCase = true)
                                    Surface(
                                        shape = ZCalShapes.Pill,
                                        color = if (isChosen) colors.accent.copy(alpha = 0.16f) else colors.surface,
                                        border = BorderStroke(1.dp, if (isChosen) colors.accent else colors.outline),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { modelInput = qm }
                                    ) {
                                        Text(
                                            text = if (qm == "auto") "auto" else qm.substringAfter("gemini-").substringAfter("gpt-"),
                                            style = ZCalTypeScale.Label.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isChosen) colors.accent else colors.textSecondary,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (selectedProvider == AiProviderType.OPENAI_COMPATIBLE) {
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = baseUrlInput,
                                    onValueChange = { baseUrlInput = it },
                                    label = { Text(strings.customBaseUrl) },
                                    shape = ZCalShapes.Input,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.accent,
                                        unfocusedBorderColor = colors.outline
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            ZCalSecondaryButton(
                                text = if (isVerifying) "Testing..." else strings.testConnection,
                                icon = Icons.Filled.Speed,
                                enabled = !isVerifying && apiKeyInput.isNotBlank(),
                                onClick = {
                                    viewModel.verifyAndSaveKey(
                                        provider = selectedProvider,
                                        apiKey = apiKeyInput,
                                        modelId = modelInput,
                                        baseUrl = baseUrlInput
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            keyStatus?.let { status ->
                                Spacer(modifier = Modifier.height(8.dp))
                                val isSuccess = status.startsWith("Verified")
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = ZCalShapes.Card,
                                    color = if (isSuccess) colors.secondary.copy(alpha = 0.12f) else colors.error.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, if (isSuccess) colors.secondary else colors.error)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isSuccess) Icons.Filled.CheckCircle else Icons.Filled.Key,
                                            contentDescription = null,
                                            tint = if (isSuccess) colors.secondary else colors.error
                                        )
                                        Text(
                                            text = status,
                                            style = ZCalTypeScale.BodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }

                        3 -> {
                            // Step 3: Targets Plan (Mifflin-St Jeor gold-standard calculation + AI fine-tune)
                            Text(
                                text = strings.planTitle,
                                style = ZCalTypeScale.Headline,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = strings.planSubtitle,
                                style = ZCalTypeScale.BodySmall,
                                color = colors.textSecondary
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = ZCalShapes.Card,
                                color = colors.surfaceElevated,
                                border = BorderStroke(1.dp, colors.outline)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(strings.bmr, style = ZCalTypeScale.Label, color = colors.textSecondary)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("${goals.bmrKcal} kcal", style = ZCalTypeScale.Title.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(32.dp)
                                            .background(colors.outline)
                                    )
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(strings.tdee, style = ZCalTypeScale.Label, color = colors.textSecondary)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("${goals.tdeeKcal} kcal", style = ZCalTypeScale.Title.copy(fontWeight = FontWeight.Bold), color = colors.accent)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(strings.daily, strings.weekly, strings.monthly).forEachIndexed { idx, label ->
                                    PillBadgeToggle(
                                        label = label,
                                        isSelected = selectedPeriodTab == idx,
                                        onClick = { selectedPeriodTab = idx },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            val activePeriod = when (selectedPeriodTab) {
                                0 -> goals.dailyTargets
                                1 -> goals.weeklyTargets
                                else -> goals.monthlyTargets
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(4.dp, ZCalShapes.Card, spotColor = colors.accentGlow),
                                shape = ZCalShapes.Card,
                                color = colors.surfaceElevated,
                                border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(activePeriod.periodLabel.uppercase(), style = ZCalTypeScale.Label.copy(fontWeight = FontWeight.Bold), color = colors.accent)
                                        Text(
                                            text = "%,d kJ".format(activePeriod.energyKj),
                                            style = ZCalTypeScale.BodySmall,
                                            color = colors.textSecondary
                                        )
                                    }

                                    Text(
                                        text = "%,d kcal".format(activePeriod.caloriesKcal),
                                        style = ZCalTypeScale.Numeral,
                                        color = colors.textPrimary
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(colors.outline.copy(alpha = 0.5f))
                                    )

                                    TargetNutrientItem(strings.protein, "%,d g".format(activePeriod.proteinG), colors.protein)
                                    TargetNutrientItem(strings.fat, "%,d g".format(activePeriod.fatG), colors.fat)
                                    TargetNutrientItem(strings.carbs, "%,d g".format(activePeriod.carbsG), colors.carbs)
                                    TargetNutrientItem(strings.cla, "%,d mg".format(activePeriod.claMg), colors.accent)
                                    TargetNutrientItem(strings.fiber, "%,d g".format(activePeriod.fiberG), colors.secondary)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            ZCalSecondaryButton(
                                text = if (isOptimizingPlan) "Optimizing with AI..." else strings.aiOptimizePlanBtn,
                                icon = Icons.Outlined.AutoFixHigh,
                                isLoading = isOptimizingPlan,
                                enabled = !isOptimizingPlan,
                                onClick = {
                                    coroutineScope.launch {
                                        isOptimizingPlan = true
                                        optimizeSuccessMsg = null
                                        delay(700)
                                        viewModel.optimizePlanWithAi()
                                        isOptimizingPlan = false
                                        optimizeSuccessMsg = "Targets calibrated with metabolic gold standard!"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            optimizeSuccessMsg?.let { msg ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = ZCalShapes.Card,
                                    color = colors.secondary.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, colors.secondary)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = colors.secondary
                                        )
                                        Text(
                                            text = msg,
                                            style = ZCalTypeScale.BodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Action Bar: Equal-Width Responsive Touch Targets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 0) {
                ZCalSecondaryButton(
                    text = strings.backBtn,
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    onClick = { step-- },
                    modifier = Modifier.weight(1f)
                )
            }

            ZCalPrimaryButton(
                text = if (step < totalSteps - 1) strings.continueBtn else strings.launchApp,
                icon = if (step < totalSteps - 1) Icons.AutoMirrored.Outlined.ArrowForward else Icons.Outlined.Check,
                onClick = {
                    if (step < totalSteps - 1) {
                        step++
                    } else {
                        viewModel.finishOnboarding(onFinished)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricSliderCard(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    stepSize: Float = 1f,
    snapToFive: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    val colors = LocalZCalColors.current
    val haptic = LocalHapticFeedback.current
    var lastSnapped by remember { mutableStateOf<Int?>(null) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ZCalShapes.Card,
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = ZCalTypeScale.Body, color = colors.textSecondary)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceVariant)
                            .clickable {
                                val newVal = (value - stepSize).coerceIn(range.start, range.endInclusive)
                                onValueChange(newVal)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("-", style = ZCalTypeScale.Label.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    }
                    Text(
                        text = valueText,
                        style = ZCalTypeScale.Title.copy(fontWeight = FontWeight.Bold),
                        color = colors.accent
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceVariant)
                            .clickable {
                                val newVal = (value + stepSize).coerceIn(range.start, range.endInclusive)
                                onValueChange(newVal)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", style = ZCalTypeScale.Label.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Slider(
                value = value,
                onValueChange = { rawVal ->
                    var finalVal = rawVal
                    if (snapToFive) {
                        val roundedTo5 = (rawVal / 5f).roundToInt() * 5
                        if (kotlin.math.abs(rawVal - roundedTo5) < 0.85f && roundedTo5 in range.start.toInt()..range.endInclusive.toInt()) {
                            finalVal = roundedTo5.toFloat()
                            if (lastSnapped != roundedTo5) {
                                lastSnapped = roundedTo5
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        } else {
                            lastSnapped = null
                        }
                    }
                    onValueChange(finalVal)
                },
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = colors.accent,
                    activeTrackColor = colors.accent,
                    inactiveTrackColor = colors.surfaceVariant
                )
            )
        }
    }
}

@Composable
private fun InteractiveSelectCard(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalZCalColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(),
        label = "selectCardScale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = ZCalShapes.Card,
        color = if (isSelected) colors.accent.copy(alpha = 0.12f) else colors.surfaceElevated,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) colors.accent else colors.outline
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) colors.accent else colors.textSecondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = ZCalTypeScale.Body.copy(
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) colors.accent else colors.textPrimary
            )
        }
    }
}

@Composable
private fun LargeSelectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalZCalColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(),
        label = "largeCardScale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = ZCalShapes.Card,
        color = if (isSelected) colors.accent.copy(alpha = 0.10f) else colors.surfaceElevated,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) colors.accent else colors.outline
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(ZCalShapes.Chip)
                    .background(if (isSelected) colors.accent.copy(alpha = 0.18f) else colors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) colors.accent else colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = ZCalTypeScale.Title.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary
                )
                Text(
                    text = subtitle,
                    style = ZCalTypeScale.BodySmall.copy(fontSize = 12.sp),
                    color = colors.textSecondary
                )
            }

            if (isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PillBadgeToggle(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalZCalColors.current
    Surface(
        modifier = modifier
            .clip(ZCalShapes.Pill)
            .clickable(onClick = onClick),
        shape = ZCalShapes.Pill,
        color = if (isSelected) colors.accent else colors.surfaceElevated,
        border = BorderStroke(1.dp, if (isSelected) colors.accent else colors.outline)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = ZCalTypeScale.Label.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (isSelected) colors.onAccent else colors.textSecondary
            )
        }
    }
}

@Composable
private fun TargetNutrientItem(
    label: String,
    value: String,
    dotColor: Color
) {
    val colors = LocalZCalColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(label, style = ZCalTypeScale.Body, color = colors.textSecondary)
        }
        Text(value, style = ZCalTypeScale.Body.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
    }
}
