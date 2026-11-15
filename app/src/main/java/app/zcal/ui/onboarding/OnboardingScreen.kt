package app.zcal.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
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
            _keyTestStatus.value = "Testing connection..."
            keyVault.updateProviderConfig(provider, modelId, baseUrl)
            when (val res = foodAnalyzer.verifyApiKey(apiKey, modelId, baseUrl)) {
                is KeyVerificationResult.Valid -> {
                    keyVault.saveApiKey(provider, apiKey)
                    _keyTestStatus.value = "Key works! Verified ${res.modelUsed} in ${res.latencyMs} ms."
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
 * First-Time Setup & Goal Generator Page:
 * Collects Weight, Height (Tall), Age/Sex, Goal, and API Key before starting the app for the first time,
 * and generates the exact Day (1D), Week (7D), and Month (30D) targets for Calories (kcal),
 * Kilojoules (kJ), Protein (g), Fat (g), Carbs (g), CLA (mg), and Fiber (g).
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
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { (step + 1).toFloat() / totalSteps.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = colors.accent,
                trackColor = colors.surfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))

            when (step) {
                0 -> {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo_transparent),
                        contentDescription = "ZCal Logo",
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Welcome to ZCal", style = ZCalTypeScale.Headline, color = colors.textPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("First-Time Setup: Weight & Height", style = ZCalTypeScale.Title, color = colors.primary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Enter your weight, height (tall), and age so ZCal can calculate your exact Daily, Weekly, and Monthly macro & energy targets.",
                        style = ZCalTypeScale.BodySmall,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BiologicalSex.entries.forEach { sex ->
                            FilterChip(
                                selected = draft.sex == sex,
                                onClick = { viewModel.updateDraft { it.copy(sex = sex) } },
                                label = { Text(sex.name) }
                            )
                        }
                        UnitSystem.entries.forEach { u ->
                            FilterChip(
                                selected = draft.units == u,
                                onClick = { viewModel.updateDraft { it.copy(units = u) } },
                                label = { Text(u.name) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "Height (Tall): ${draft.heightCm.roundToInt()} cm",
                        style = ZCalTypeScale.Title,
                        color = colors.textPrimary
                    )
                    Slider(
                        value = draft.heightCm.toFloat(),
                        onValueChange = { h -> viewModel.updateDraft { it.copy(heightCm = h.toDouble()) } },
                        valueRange = 135f..215f
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Current Weight: ${String.format("%.1f", draft.weightKg)} kg",
                        style = ZCalTypeScale.Title,
                        color = colors.textPrimary
                    )
                    Slider(
                        value = draft.weightKg.toFloat(),
                        onValueChange = { w -> viewModel.updateDraft { it.copy(weightKg = w.toDouble()) } },
                        valueRange = 40f..180f
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Target Weight: ${String.format("%.1f", draft.targetWeightKg)} kg",
                        style = ZCalTypeScale.Title,
                        color = colors.textPrimary
                    )
                    Slider(
                        value = draft.targetWeightKg.toFloat(),
                        onValueChange = { tw -> viewModel.updateDraft { it.copy(targetWeightKg = tw.toDouble()) } },
                        valueRange = 40f..180f
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft.birthYear.toString(),
                        onValueChange = { yr ->
                            yr.toIntOrNull()?.let { y -> viewModel.updateDraft { it.copy(birthYear = y) } }
                        },
                        label = { Text("Birth Year (e.g. 1998)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = ZCalShapes.Input,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                1 -> {
                    Text("Select Your Goal & Activity", style = ZCalTypeScale.Headline, color = colors.textPrimary)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Primary Goal", style = ZCalTypeScale.Title, color = colors.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        GoalType.entries.forEach { gt ->
                            FilterChip(
                                selected = draft.goalType == gt,
                                onClick = { viewModel.updateDraft { it.copy(goalType = gt) } },
                                label = { Text(gt.label) }
                            )
                        }
                    }

                    if (draft.goalType != GoalType.MAINTAIN) {
                        val absPace = kotlin.math.abs(draft.weeklyChangeKg).toFloat().coerceIn(0.1f, 1.2f)
                        Text(
                            "Weekly Pace: ${String.format("%.2f", absPace)} kg / week",
                            style = ZCalTypeScale.Title,
                            color = colors.textPrimary
                        )
                        Slider(
                            value = absPace,
                            onValueChange = { p -> viewModel.updateDraft { it.copy(weeklyChangeKg = p.toDouble()) } },
                            valueRange = 0.1f..1.2f
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Daily Activity Level", style = ZCalTypeScale.Title, color = colors.textPrimary)
                    ActivityLevel.entries.forEach { level ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { viewModel.updateDraft { it.copy(activityLevel = level) } },
                            shape = ZCalShapes.Input,
                            color = if (draft.activityLevel == level) colors.surfaceVariant else colors.surface,
                            border = BorderStroke(
                                width = if (draft.activityLevel == level) 2.dp else 1.dp,
                                color = if (draft.activityLevel == level) colors.accent else colors.outline
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(level.label, style = ZCalTypeScale.Title, color = colors.textPrimary)
                                Text(level.description, style = ZCalTypeScale.BodySmall, color = colors.textSecondary)
                            }
                        }
                    }
                }

                2 -> {
                    Text("Your Day / Week / Month Targets", style = ZCalTypeScale.Headline, color = colors.textPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "BMR: ${goals.bmrKcal} kcal (${goals.bmrKj} kJ) • TDEE: ${goals.tdeeKcal} kcal (${goals.tdeeKj} kJ)",
                        style = ZCalTypeScale.BodySmall,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Segmented Period Selector: Day (1D) | Week (7D) | Month (30D)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Daily (1D)", "Weekly (7D)", "Monthly (30D)").forEachIndexed { idx, label ->
                            FilterChip(
                                selected = selectedPeriodTab == idx,
                                onClick = { selectedPeriodTab = idx },
                                label = { Text(label) }
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
                        modifier = Modifier.fillMaxWidth(),
                        shape = ZCalShapes.Card,
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(activePeriod.periodLabel, style = ZCalTypeScale.Label, color = colors.accent)
                            Text(
                                text = "%,d kcal  /  %,d kJ".format(activePeriod.caloriesKcal, activePeriod.energyKj),
                                style = ZCalTypeScale.Headline,
                                color = colors.textPrimary
                            )

                            TargetNutrientRow("Protein (P)", "%,d g".format(activePeriod.proteinG))
                            TargetNutrientRow("Fat (F)", "%,d g".format(activePeriod.fatG))
                            TargetNutrientRow("Carbohydrates (C)", "%,d g".format(activePeriod.carbsG))
                            TargetNutrientRow("CLA (Healthy Fatty Acid)", "%,d mg".format(activePeriod.claMg))
                            TargetNutrientRow("Dietary Fiber", "%,d g".format(activePeriod.fiberG))
                        }
                    }

                    goals.belowClinicalFloorWarning?.let { warning ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(warning, style = ZCalTypeScale.BodySmall, color = colors.carbs)
                    }
                    goals.rapidPaceWarning?.let { warning ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(warning, style = ZCalTypeScale.BodySmall, color = colors.carbs)
                    }
                }

                3 -> {
                    Text("Connect Your AI API Key", style = ZCalTypeScale.Headline, color = colors.textPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Paste your Gemini or OpenAI-compatible API key so the pre-built Camera Main Page can analyze your meals immediately.",
                        style = ZCalTypeScale.BodySmall,
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AiProviderType.entries.forEach { provider ->
                            FilterChip(
                                selected = selectedProvider == provider,
                                onClick = {
                                    selectedProvider = provider
                                    modelInput = provider.defaultModel
                                    baseUrlInput = provider.defaultBaseUrl
                                },
                                label = { Text(provider.displayName) }
                            )
                        }
                    }

                    if (selectedProvider == AiProviderType.GEMINI) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                                context.startActivity(intent)
                            },
                            shape = ZCalShapes.Pill
                        ) {
                            Text("Get Free Gemini Key at Google AI Studio")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Paste API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        shape = ZCalShapes.Input,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        label = { Text("Model (auto = best for food photos)") },
                        shape = ZCalShapes.Input,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (selectedProvider == AiProviderType.OPENAI_COMPATIBLE) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = baseUrlInput,
                            onValueChange = { baseUrlInput = it },
                            label = { Text("Base URL") },
                            shape = ZCalShapes.Input,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.verifyAndSaveKey(
                                    provider = selectedProvider,
                                    apiKey = apiKeyInput,
                                    modelId = modelInput,
                                    baseUrl = baseUrlInput
                                )
                            },
                            enabled = !isVerifying && apiKeyInput.isNotBlank(),
                            shape = ZCalShapes.Pill,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                        ) {
                            Text("Verify & Save Key", color = colors.onPrimary)
                        }
                        if (isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.height(24.dp))
                        }
                    }

                    keyStatus?.let { status ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = status,
                            style = ZCalTypeScale.BodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (status.startsWith("Key works")) colors.accent else colors.error
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (step > 0) {
                OutlinedButton(
                    onClick = { step-- },
                    shape = ZCalShapes.Pill,
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("Back")
                }
            } else {
                Spacer(modifier = Modifier.height(52.dp))
            }

            Button(
                onClick = {
                    if (step < totalSteps - 1) {
                        step++
                    } else {
                        viewModel.finishOnboarding(onFinished)
                    }
                },
                shape = ZCalShapes.Pill,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                modifier = Modifier.height(52.dp)
            ) {
                Text(
                    text = if (step < totalSteps - 1) "Continue" else "Launch Camera Main Page",
                    color = colors.onPrimary,
                    style = ZCalTypeScale.Title
                )
            }
        }
    }
}

@Composable
private fun TargetNutrientRow(label: String, value: String) {
    val colors = LocalZCalColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = ZCalTypeScale.Body, color = colors.textSecondary)
        Text(value, style = ZCalTypeScale.Body.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
    }
}
