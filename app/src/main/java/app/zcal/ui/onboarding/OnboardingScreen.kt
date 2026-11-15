package app.zcal.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import app.zcal.ui.theme.LocalZCalColors
import app.zcal.ui.theme.ZCalShapes
import app.zcal.ui.theme.ZCalTypeScale
import app.zcal.util.DateHelpers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
        return computeGoalsUseCase(
            sex = draft.sex,
            birthYear = draft.birthYear,
            heightCm = draft.heightCm,
            weightKg = draft.weightKg,
            activityLevel = draft.activityLevel,
            goalType = draft.goalType,
            weeklyChangeKg = draft.weeklyChangeKg
        )
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

    fun verifyAndSaveKey(
        provider: AiProviderType,
        apiKey: String,
        modelId: String,
        baseUrl: String
    ) {
        viewModelScope.launch {
            _isVerifyingKey.value = true
            _keyTestStatus.value = "Testing AI connection..."
            keyVault.updateProviderConfig(provider, modelId, baseUrl)
            when (val res = foodAnalyzer.verifyApiKey(apiKey, modelId, baseUrl)) {
                is KeyVerificationResult.Valid -> {
                    keyVault.saveApiKey(provider, apiKey)
                    _keyTestStatus.value = "Key verified! Connected to ${res.modelUsed} (${res.latencyMs}ms)"
                }
                is KeyVerificationResult.Invalid -> {
                    _keyTestStatus.value = "Verification failed: ${res.errorMessage}"
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

/**
 * Enterprise-grade, alive Onboarding & Calibration Experience (Cal AI / Fud AI quality):
 * - Smooth horizontal page transitions between setup steps
 * - Full-width interactive touch targets and selection cards with tactile spring bounce
 * - Floating numeric readouts and vibrant accent glows
 * - Plan generation display with calorie & macro breakdown
 * - Responsive edge-to-edge layout matching any phone screen
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit
) {
    val colors = LocalZCalColors.current
    val draft by viewModel.profileDraft.collectAsState()
    val goals by viewModel.computedGoals.collectAsState()
    val keyStatus by viewModel.keyTestStatus.collectAsState()
    val isVerifying by viewModel.isVerifyingKey.collectAsState()
    val context = LocalContext.current

    var step by remember { mutableIntStateOf(0) }
    val totalSteps = 4
    var selectedPeriodTab by remember { mutableIntStateOf(0) } // 0 = Day, 1 = Week, 2 = Month

    var selectedProvider by remember { mutableStateOf(AiProviderType.GEMINI) }
    var apiKeyInput by remember { mutableStateOf("") }
    var modelInput by remember { mutableStateOf(AiProviderType.GEMINI.defaultModel) }
    var baseUrlInput by remember { mutableStateOf(AiProviderType.GEMINI.defaultBaseUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header: Step Indicator & Animated Progress Bar
        Column {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STEP ${step + 1} OF $totalSteps",
                    style = ZCalTypeScale.Label.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                    color = colors.accent
                )
                Text(
                    text = when (step) {
                        0 -> "Calibration"
                        1 -> "Your Goal"
                        2 -> "Targets"
                        else -> "AI Vision"
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
                    .height(6.dp)
                    .clip(ZCalShapes.Pill)
                    .background(colors.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(6.dp)
                        .clip(ZCalShapes.Pill)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(colors.accent, colors.secondary)
                            )
                        )
                )
            }
        }

        // Animated Body Content
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
                        .padding(vertical = 16.dp)
                ) {
                    when (currentStep) {
                        0 -> {
                            // Step 0: Welcome & Body Metrics
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.app_logo_transparent),
                                    contentDescription = "ZCal Logo",
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "Welcome to ZCal",
                                        style = ZCalTypeScale.Headline,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "Calibrate your metabolic profile",
                                        style = ZCalTypeScale.BodySmall,
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Biological Sex Selection Cards
                            Text(
                                text = "Biological Sex",
                                style = ZCalTypeScale.Title,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                BiologicalSex.entries.forEach { sex ->
                                    val isSelected = draft.sex == sex
                                    InteractiveSelectCard(
                                        title = sex.name.lowercase().replaceFirstChar { it.uppercase() },
                                        icon = if (sex == BiologicalSex.MALE) Icons.Filled.Male else Icons.Filled.Female,
                                        isSelected = isSelected,
                                        onClick = { viewModel.updateDraft { it.copy(sex = sex) } },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Unit System Selection
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Measurement Units",
                                    style = ZCalTypeScale.Title,
                                    color = colors.textPrimary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    UnitSystem.entries.forEach { unit ->
                                        val isSel = draft.units == unit
                                        PillBadgeToggle(
                                            label = unit.name,
                                            isSelected = isSel,
                                            onClick = { viewModel.updateDraft { it.copy(units = unit) } }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Height Slider with Large Numeric Readout
                            MetricSliderCard(
                                label = "Height",
                                valueText = "${draft.heightCm.roundToInt()} cm",
                                value = draft.heightCm.toFloat(),
                                range = 130f..220f,
                                onValueChange = { h -> viewModel.updateDraft { it.copy(heightCm = h.toDouble()) } }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Current Weight Slider
                            MetricSliderCard(
                                label = "Current Weight",
                                valueText = "${String.format("%.1f", draft.weightKg)} kg",
                                value = draft.weightKg.toFloat(),
                                range = 40f..180f,
                                onValueChange = { w -> viewModel.updateDraft { it.copy(weightKg = w.toDouble()) } }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Target Weight Slider
                            MetricSliderCard(
                                label = "Target Goal Weight",
                                valueText = "${String.format("%.1f", draft.targetWeightKg)} kg",
                                value = draft.targetWeightKg.toFloat(),
                                range = 40f..180f,
                                onValueChange = { tw -> viewModel.updateDraft { it.copy(targetWeightKg = tw.toDouble()) } }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Birth Year Input
                            OutlinedTextField(
                                value = draft.birthYear.toString(),
                                onValueChange = { yr ->
                                    yr.toIntOrNull()?.let { y -> viewModel.updateDraft { it.copy(birthYear = y) } }
                                },
                                label = { Text("Year of Birth") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = ZCalShapes.Input,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accent,
                                    unfocusedBorderColor = colors.outline,
                                    focusedLabelColor = colors.accent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        1 -> {
                            // Step 1: Goal & Activity Level
                            Text(
                                text = "What is your primary goal?",
                                style = ZCalTypeScale.Headline,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "ZCal dynamically calculates your metabolic deficit or surplus.",
                                style = ZCalTypeScale.BodySmall,
                                color = colors.textSecondary
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Goal Type Cards
                            GoalType.entries.forEach { gt ->
                                val isSelected = draft.goalType == gt
                                val (icon, desc) = when (gt) {
                                    GoalType.LOSE -> Icons.AutoMirrored.Filled.TrendingDown to "Burn fat with a sustainable caloric deficit"
                                    GoalType.MAINTAIN -> Icons.Filled.FitnessCenter to "Maintain current weight and recomposition"
                                    GoalType.GAIN -> Icons.AutoMirrored.Filled.TrendingUp to "Build lean muscle mass with controlled surplus"
                                }

                                LargeSelectionCard(
                                    title = gt.label,
                                    subtitle = desc,
                                    icon = icon,
                                    isSelected = isSelected,
                                    onClick = { viewModel.updateDraft { it.copy(goalType = gt) } },
                                    modifier = Modifier.padding(vertical = 5.dp)
                                )
                            }

                            if (draft.goalType != GoalType.MAINTAIN) {
                                Spacer(modifier = Modifier.height(12.dp))
                                val absPace = kotlin.math.abs(draft.weeklyChangeKg).toFloat().coerceIn(0.1f, 1.2f)
                                MetricSliderCard(
                                    label = "Weekly Pace",
                                    valueText = "${String.format("%.2f", absPace)} kg / week",
                                    value = absPace,
                                    range = 0.1f..1.2f,
                                    onValueChange = { p -> viewModel.updateDraft { it.copy(weeklyChangeKg = p.toDouble()) } }
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = "Daily Activity Level",
                                style = ZCalTypeScale.Title,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            ActivityLevel.entries.forEach { level ->
                                val isSelected = draft.activityLevel == level
                                LargeSelectionCard(
                                    title = level.label,
                                    subtitle = level.description,
                                    icon = Icons.Filled.Speed,
                                    isSelected = isSelected,
                                    onClick = { viewModel.updateDraft { it.copy(activityLevel = level) } },
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }

                        2 -> {
                            // Step 2: Calorie & Macro Target Plan
                            Text(
                                text = "Your Personalized Plan",
                                style = ZCalTypeScale.Headline,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Scientific TDEE and macro distribution calculated for your body.",
                                style = ZCalTypeScale.BodySmall,
                                color = colors.textSecondary
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Metabolic BMR / TDEE Banner
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = ZCalShapes.Card,
                                color = colors.surfaceElevated,
                                border = BorderStroke(1.dp, colors.outline)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("BMR (Basal)", style = ZCalTypeScale.Label, color = colors.textSecondary)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("${goals.bmrKcal} kcal", style = ZCalTypeScale.Title.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(36.dp)
                                            .background(colors.outline)
                                    )
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("TDEE (Maintenance)", style = ZCalTypeScale.Label, color = colors.textSecondary)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("${goals.tdeeKcal} kcal", style = ZCalTypeScale.Title.copy(fontWeight = FontWeight.Bold), color = colors.accent)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Period Selector: Day / Week / Month
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Daily (1D)", "Weekly (7D)", "Monthly (30D)").forEachIndexed { idx, label ->
                                    val isSel = selectedPeriodTab == idx
                                    PillBadgeToggle(
                                        label = label,
                                        isSelected = isSel,
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

                            Spacer(modifier = Modifier.height(14.dp))

                            // Hero Target Card with Accent Glow
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(6.dp, ZCalShapes.Card, spotColor = colors.accentGlow),
                                shape = ZCalShapes.Card,
                                color = colors.surfaceElevated,
                                border = BorderStroke(1.2.dp, colors.accent.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
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
                                            .background(colors.outline.copy(alpha = 0.6f))
                                    )

                                    TargetNutrientItem("Protein (P)", "%,d g".format(activePeriod.proteinG), colors.protein)
                                    TargetNutrientItem("Fat (F)", "%,d g".format(activePeriod.fatG), colors.fat)
                                    TargetNutrientItem("Carbohydrates (C)", "%,d g".format(activePeriod.carbsG), colors.carbs)
                                    TargetNutrientItem("Healthy CLA", "%,d mg".format(activePeriod.claMg), colors.accent)
                                    TargetNutrientItem("Dietary Fiber", "%,d g".format(activePeriod.fiberG), colors.secondary)
                                }
                            }

                            goals.belowClinicalFloorWarning?.let { warning ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(warning, style = ZCalTypeScale.BodySmall, color = colors.error)
                            }
                        }

                        3 -> {
                            // Step 3: AI Engine & Key Setup
                            Text(
                                text = "Connect Your AI Key",
                                style = ZCalTypeScale.Headline,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "ZCal uses private Bring-Your-Own-Key. You get 100% free food vision scans directly with Gemini or OpenAI.",
                                style = ZCalTypeScale.BodySmall,
                                color = colors.textSecondary
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Provider Selector Cards
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AiProviderType.entries.forEach { provider ->
                                    val isSelected = selectedProvider == provider
                                    InteractiveSelectCard(
                                        title = if (provider == AiProviderType.GEMINI) "Google Gemini" else "OpenAI / Local",
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
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                                            context.startActivity(intent)
                                        },
                                    shape = ZCalShapes.Card,
                                    color = colors.accent.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Need a free key?",
                                                style = ZCalTypeScale.Title.copy(fontSize = 15.sp),
                                                color = colors.textPrimary
                                            )
                                            Text(
                                                text = "Click to open Google AI Studio (Instant & Free)",
                                                style = ZCalTypeScale.Label,
                                                color = colors.accent
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                label = { Text("Paste AI API Key") },
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
                                label = { Text("Model Name (Default: Auto)") },
                                shape = ZCalShapes.Input,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accent,
                                    unfocusedBorderColor = colors.outline
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (selectedProvider == AiProviderType.OPENAI_COMPATIBLE) {
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = baseUrlInput,
                                    onValueChange = { baseUrlInput = it },
                                    label = { Text("Custom Base URL") },
                                    shape = ZCalShapes.Input,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.accent,
                                        unfocusedBorderColor = colors.outline
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Test Key Button
                            ZCalSecondaryButton(
                                text = if (isVerifying) "Testing..." else "Test & Verify Connection",
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
                                Spacer(modifier = Modifier.height(10.dp))
                                val isSuccess = status.startsWith("Key verified")
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = ZCalShapes.Card,
                                    color = if (isSuccess) colors.secondary.copy(alpha = 0.12f) else colors.error.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, if (isSuccess) colors.secondary else colors.error)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
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
                    }
                }
            }
        }

        // Bottom Action Bar: Full-Width Responsive Touch Targets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 0) {
                ZCalSecondaryButton(
                    text = "Back",
                    onClick = { step-- },
                    modifier = Modifier.weight(0.35f)
                )
            }

            ZCalPrimaryButton(
                text = if (step < totalSteps - 1) "Continue" else "Launch ZCal Camera",
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
    onValueChange: (Float) -> Unit
) {
    val colors = LocalZCalColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ZCalShapes.Card,
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = ZCalTypeScale.Body, color = colors.textSecondary)
                Text(
                    text = valueText,
                    style = ZCalTypeScale.Title.copy(fontWeight = FontWeight.Bold),
                    color = colors.accent
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
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
        targetValue = if (isPressed) 0.95f else 1f,
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
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) colors.accent else colors.textSecondary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = ZCalTypeScale.Body.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
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
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(ZCalShapes.Chip)
                    .background(if (isSelected) colors.accent.copy(alpha = 0.18f) else colors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) colors.accent else colors.textSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = ZCalTypeScale.Title.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary
                )
                Text(
                    text = subtitle,
                    style = ZCalTypeScale.BodySmall,
                    color = colors.textSecondary
                )
            }

            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp)
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = ZCalTypeScale.Label.copy(fontWeight = FontWeight.SemiBold),
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
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(label, style = ZCalTypeScale.Body, color = colors.textSecondary)
        }
        Text(value, style = ZCalTypeScale.Body.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
    }
}
