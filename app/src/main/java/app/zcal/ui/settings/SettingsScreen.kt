package app.zcal.ui.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.zcal.data.ai.FoodAnalyzer
import app.zcal.data.ai.KeyVerificationResult
import app.zcal.data.ai.ModelChoices
import app.zcal.data.ai.ModelSelector
import app.zcal.data.repo.MealRepository
import app.zcal.data.repo.ProfileRepository
import app.zcal.data.secure.KeyVault
import app.zcal.domain.model.AiProviderType
import app.zcal.domain.model.AppLanguage
import app.zcal.domain.model.ThemePreference
import app.zcal.domain.model.UnitSystem
import app.zcal.domain.model.UserProfile
import app.zcal.domain.usecase.ComputeGoalsUseCase
import app.zcal.ui.localization.LocalZCalStrings
import app.zcal.ui.theme.LocalZCalColors
import app.zcal.ui.theme.ZCalShapes
import app.zcal.ui.theme.ZCalTypeScale
import app.zcal.util.ExportHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

enum class SettingsSection {
    AI_MODELS,
    BODY_GOALS,
    APPEARANCE,
    LANGUAGE,
    DATA_PRIVACY
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val mealRepository: MealRepository,
    private val keyVault: KeyVault,
    private val foodAnalyzer: FoodAnalyzer,
    private val computeGoalsUseCase: ComputeGoalsUseCase
) : ViewModel() {

    fun updateBodyMetrics(
        weightKg: Double,
        heightCm: Double,
        targetWeightKg: Double
    ) {
        viewModelScope.launch {
            val current = userProfile.value
            val updated = current.copy(
                weightKg = weightKg,
                heightCm = heightCm,
                targetWeightKg = targetWeightKg
            )
            val goals = computeGoalsUseCase.fromProfile(updated)
            val finalProfile = updated.copy(
                calorieGoal = goals.dailyCalories,
                kilojouleGoal = goals.dailyKilojoules,
                proteinGoalG = goals.proteinG,
                carbsGoalG = goals.carbsG,
                fatGoalG = goals.fatG,
                claGoalMg = goals.claMg,
                fiberGoalG = goals.fiberG
            )
            profileRepository.saveUserProfile(finalProfile)
            _verifyMessage.value = "Body metrics & metabolic targets updated!"
        }
    }

    fun setUnitSystem(units: UnitSystem) {
        viewModelScope.launch {
            val current = userProfile.value
            profileRepository.saveUserProfile(current.copy(units = units))
        }
    }

    val userProfile: StateFlow<UserProfile> = profileRepository.userProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    val activeProvider: StateFlow<AiProviderType> = keyVault.activeProviderFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiProviderType.GEMINI)

    val activeModelId: StateFlow<String> = keyVault.activeModelIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiProviderType.GEMINI.defaultModel)

    val allowPreviewModels: StateFlow<Boolean> = keyVault.allowPreviewModelsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setAllowPreviewModels(allow: Boolean) {
        viewModelScope.launch { keyVault.setAllowPreviewModels(allow) }
    }

    val customBaseUrl: StateFlow<String> = keyVault.customBaseUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiProviderType.GEMINI.defaultBaseUrl)

    val maskedKey: StateFlow<String?> = keyVault.maskedActiveKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val themePreference: StateFlow<ThemePreference> = profileRepository.themePreferenceFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreference.SYSTEM)

    val dynamicColor: StateFlow<Boolean> = profileRepository.dynamicColorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val calmPalette: StateFlow<Boolean> = profileRepository.calmPaletteFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val forgiveOneDay: StateFlow<Boolean> = profileRepository.forgiveOneDayFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hideNumbers: StateFlow<Boolean> = profileRepository.hideNumbersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appLanguage: StateFlow<AppLanguage> = profileRepository.languageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLanguage.ENGLISH)

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { profileRepository.saveLanguage(lang) }
    }

    private val _verifyMessage = MutableStateFlow<String?>(null)
    val verifyMessage: StateFlow<String?> = _verifyMessage.asStateFlow()

    fun updateCalibrationPct(pct: Int) {
        viewModelScope.launch {
            profileRepository.saveUserProfile(userProfile.value.copy(personalCalibrationPct = pct))
        }
    }

    fun updateCalorieAndProteinGoal(calories: Int, proteinG: Int) {
        viewModelScope.launch {
            profileRepository.saveUserProfile(
                userProfile.value.copy(
                    calorieGoal = calories.coerceIn(1000, 6500),
                    proteinGoalG = proteinG.coerceIn(30, 400)
                )
            )
        }
    }

    private val _modelChoices = MutableStateFlow<ModelChoices?>(null)
    val modelChoices: StateFlow<ModelChoices?> = _modelChoices.asStateFlow()
    private val _loadingModels = MutableStateFlow(false)
    val loadingModels: StateFlow<Boolean> = _loadingModels.asStateFlow()

    fun loadModelChoices(provider: AiProviderType, typedKey: String, baseUrl: String) {
        viewModelScope.launch {
            _loadingModels.value = true
            keyVault.updateProviderConfig(provider, keyVault.activeModelIdFlow.first(), baseUrl)
            val choices = foodAnalyzer.listAvailableModels(typedKey, baseUrl)
            _loadingModels.value = false
            if (choices == null) {
                _verifyMessage.value = "Could not load the model list. Check the key and base URL, or type a model ID."
            } else {
                _modelChoices.value = choices
            }
        }
    }

    fun dismissModelChoices() { _modelChoices.value = null }

    fun saveAndVerifyProviderKey(
        provider: AiProviderType,
        newKey: String,
        modelId: String,
        baseUrl: String,
        onDone: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val prevProvider = keyVault.activeProviderFlow.first()
            val prevModel = keyVault.activeModelIdFlow.first()
            val prevBase = keyVault.customBaseUrlFlow.first()
            keyVault.updateProviderConfig(provider, modelId, baseUrl)

            val keyToTest = newKey.ifBlank { keyVault.getApiKey(provider).orEmpty() }
            if (keyToTest.isBlank()) {
                _verifyMessage.value = "Provider and model saved. Paste an API key to start scanning."
                onDone(true)
                return@launch
            }
            _verifyMessage.value = "Verifying..."
            val modelToTest = modelId.trim().ifEmpty { provider.defaultModel }
            when (val res = foodAnalyzer.verifyApiKey(keyToTest, modelToTest, baseUrl)) {
                is KeyVerificationResult.Valid -> {
                    if (newKey.isNotBlank()) keyVault.saveApiKey(provider, newKey)
                    val shown = if (modelToTest.equals(ModelSelector.AUTO, true)) "auto picked ${res.modelUsed}" else res.modelUsed
                    _verifyMessage.value = "Saved. Using $shown (${res.latencyMs} ms)"
                    onDone(true)
                }
                is KeyVerificationResult.Invalid -> {
                    val existingKey = keyVault.getApiKey(provider)
                    if (newKey.isBlank() && !existingKey.isNullOrBlank()) {
                        _verifyMessage.value = "Model saved ($modelToTest). Note: ${res.errorMessage}"
                        onDone(true)
                    } else {
                        keyVault.updateProviderConfig(prevProvider, prevModel, prevBase)
                        _verifyMessage.value = "Not saved, previous settings kept. ${res.errorMessage}"
                        onDone(false)
                    }
                }
            }
        }
    }

    fun removeCurrentKey() {
        viewModelScope.launch {
            keyVault.removeApiKey(activeProvider.value)
            _verifyMessage.value = "API key removed from Android Keystore."
        }
    }

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { profileRepository.setThemePreference(theme) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { profileRepository.setDynamicColorEnabled(enabled) }
    }

    fun setCalmPalette(enabled: Boolean) {
        viewModelScope.launch { profileRepository.setCalmPaletteEnabled(enabled) }
    }

    fun setForgiveOneDay(enabled: Boolean) {
        viewModelScope.launch { profileRepository.setForgiveOneDay(enabled) }
    }

    fun setHideNumbers(enabled: Boolean) {
        viewModelScope.launch { profileRepository.setHideNumbers(enabled) }
    }

    fun exportJsonBackup(context: Context) {
        viewModelScope.launch {
            val meals = mealRepository.getAllMealsForExport()
            val file = ExportHelper.exportMealsToJsonFile(context, meals)
            ExportHelper.shareExportFile(context, file, "application/json")
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            mealRepository.resetAllUserData()
        }
    }
}

/**
 * Redesigned Settings Architecture:
 * - Clean Settings Hub with categorized section cards.
 * - Dedicated sub-pages for AI Vision, Body Goals, Appearance, Language, and Data Privacy.
 * - BackHandler & animated back navigation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val colors = LocalZCalColors.current
    val strings = LocalZCalStrings.current
    val context = LocalContext.current

    val appLang by viewModel.appLanguage.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    val provider by viewModel.activeProvider.collectAsState()
    val modelId by viewModel.activeModelId.collectAsState()
    val baseUrl by viewModel.customBaseUrl.collectAsState()
    val allowPreview by viewModel.allowPreviewModels.collectAsState()
    val modelChoices by viewModel.modelChoices.collectAsState()
    val loadingModels by viewModel.loadingModels.collectAsState()
    val maskedKey by viewModel.maskedKey.collectAsState()
    val themePref by viewModel.themePreference.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val calmPalette by viewModel.calmPalette.collectAsState()
    val forgiveOneDay by viewModel.forgiveOneDay.collectAsState()
    val hideNumbers by viewModel.hideNumbers.collectAsState()
    val verifyMsg by viewModel.verifyMessage.collectAsState()

    var currentSection by remember { mutableStateOf<SettingsSection?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    var keyInput by remember { mutableStateOf("") }
    var modelEdit by remember(modelId) { mutableStateOf(modelId) }
    var baseUrlEdit by remember(baseUrl) { mutableStateOf(baseUrl) }

    BackHandler(enabled = currentSection != null) {
        currentSection = null
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(strings.resetLogBtn, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to reset all logged meals and cached records? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetAllData()
                        showResetDialog = false
                    }
                ) {
                    Text(strings.resetLogBtn, color = colors.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .displayCutoutPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (currentSection == null) {
            // MAIN SETTINGS HUB
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(strings.settings, style = ZCalTypeScale.Headline, color = colors.textPrimary)
                    Text(
                        "Preferences, intelligence & metabolic targets",
                        style = ZCalTypeScale.BodySmall,
                        color = colors.textSecondary
                    )
                }
            }

            // Quick Status Glance Bar
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceElevated,
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (maskedKey != null) Color(0xFF10B981) else Color(0xFFF59E0B))
                            )
                            Text(
                                text = if (maskedKey != null) modelId else "No Key",
                                style = ZCalTypeScale.Label.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.textPrimary
                            )
                        }

                        Text(
                            text = "${profile.calorieGoal} kcal • ${if (profile.units == UnitSystem.METRIC) "Metric" else "Imperial"}",
                            style = ZCalTypeScale.Label,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            // Section 1: AI Vision & Models
            item {
                SettingsHubCard(
                    title = strings.settingsAiTitle,
                    subtitle = "${provider.displayName} • ${if (maskedKey != null) maskedKey else "Key required"}",
                    icon = Icons.Filled.Key,
                    iconTint = Color(0xFF8A7CFF),
                    iconBg = Color(0xFF8A7CFF).copy(alpha = 0.14f),
                    onClick = { currentSection = SettingsSection.AI_MODELS }
                )
            }

            // Section 2: Body Metrics & Metabolic Targets
            item {
                val isImperial = profile.units == UnitSystem.IMPERIAL
                val weightStr = if (isImperial) "%.1f lbs".format(Locale.US, profile.weightKg * 2.20462) else "%.1f kg".format(Locale.US, profile.weightKg)
                SettingsHubCard(
                    title = strings.settingsBodyTitle,
                    subtitle = "$weightStr • ${profile.calorieGoal} kcal target",
                    icon = Icons.Filled.FitnessCenter,
                    iconTint = Color(0xFF10B981),
                    iconBg = Color(0xFF10B981).copy(alpha = 0.14f),
                    onClick = { currentSection = SettingsSection.BODY_GOALS }
                )
            }

            // Section 3: Appearance & Themes
            item {
                val themeLabel = when (themePref) {
                    ThemePreference.SYSTEM -> strings.system
                    ThemePreference.LIGHT -> strings.light
                    ThemePreference.DARK -> strings.dark
                    ThemePreference.AMOLED -> strings.amoled
                }
                SettingsHubCard(
                    title = strings.settingsAppearanceTitle,
                    subtitle = "$themeLabel theme • Mindful display",
                    icon = Icons.Outlined.AutoFixHigh,
                    iconTint = Color(0xFFF59E0B),
                    iconBg = Color(0xFFF59E0B).copy(alpha = 0.14f),
                    onClick = { currentSection = SettingsSection.APPEARANCE }
                )
            }

            // Section 4: Language
            item {
                SettingsHubCard(
                    title = strings.settingsLanguageTitle,
                    subtitle = "${appLang.nativeTitle} (${appLang.title})",
                    icon = Icons.Filled.Language,
                    iconTint = Color(0xFF38BDF8),
                    iconBg = Color(0xFF38BDF8).copy(alpha = 0.14f),
                    onClick = { currentSection = SettingsSection.LANGUAGE }
                )
            }

            // Section 5: Data & Privacy
            item {
                SettingsHubCard(
                    title = strings.settingsDataTitle,
                    subtitle = "JSON backup, local cache & attribution",
                    icon = Icons.Filled.Shield,
                    iconTint = Color(0xFF94A3B8),
                    iconBg = Color(0xFF94A3B8).copy(alpha = 0.14f),
                    onClick = { currentSection = SettingsSection.DATA_PRIVACY }
                )
            }
        } else {
            // SUB-PAGE HEADER
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { currentSection = null },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = strings.backToSettings,
                            tint = colors.accent
                        )
                    }
                    Column {
                        Text(
                            text = when (currentSection) {
                                SettingsSection.AI_MODELS -> strings.settingsAiTitle
                                SettingsSection.BODY_GOALS -> strings.settingsBodyTitle
                                SettingsSection.APPEARANCE -> strings.settingsAppearanceTitle
                                SettingsSection.LANGUAGE -> strings.settingsLanguageTitle
                                SettingsSection.DATA_PRIVACY -> strings.settingsDataTitle
                                else -> strings.settings
                            },
                            style = ZCalTypeScale.Headline.copy(fontSize = 20.sp),
                            color = colors.textPrimary
                        )
                        Text(
                            text = strings.settings,
                            style = ZCalTypeScale.Label,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            // SUB-PAGE CONTENTS
            when (currentSection) {
                SettingsSection.AI_MODELS -> {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ZCalShapes.Card,
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.outline)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(strings.byokTitle, style = ZCalTypeScale.Title, color = colors.textPrimary)

                                // Active Provider Pills
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AiProviderType.entries.forEach { p ->
                                        val isSel = provider == p
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(42.dp)
                                                .clip(ZCalShapes.Pill)
                                                .clickable {
                                                    modelEdit = p.defaultModel
                                                    baseUrlEdit = p.defaultBaseUrl
                                                    viewModel.saveAndVerifyProviderKey(p, "", p.defaultModel, p.defaultBaseUrl)
                                                },
                                            shape = ZCalShapes.Pill,
                                            color = if (isSel) colors.accent else colors.surfaceVariant,
                                            border = BorderStroke(1.dp, if (isSel) colors.accent else colors.outline)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Text(
                                                    p.displayName,
                                                    style = ZCalTypeScale.Label.copy(
                                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                                    ),
                                                    color = if (isSel) colors.onAccent else colors.textPrimary
                                                )
                                            }
                                        }
                                    }
                                }

                                // Key Status Badge
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = colors.surfaceElevated,
                                    border = BorderStroke(1.dp, colors.outline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Key,
                                            contentDescription = null,
                                            tint = if (maskedKey != null) Color(0xFF10B981) else Color(0xFFF59E0B),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "${strings.storedKey}: ${maskedKey ?: strings.noKeyConfigured}",
                                                style = ZCalTypeScale.BodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = colors.textPrimary
                                            )
                                            Text(
                                                text = "Encrypted in hardware Keystore (AES-256-GCM)",
                                                style = ZCalTypeScale.Label,
                                                color = colors.textSecondary
                                            )
                                        }
                                    }
                                }

                                // Quick Model Presets
                                Text(strings.quickModelPresets, style = ZCalTypeScale.BodySmall, color = colors.textSecondary)
                                val quickModels = if (provider == AiProviderType.GEMINI) {
                                    listOf("gemini-3.8-flash", "gemini-4-hydrogen", "gemini-2.5-flash", "auto")
                                } else {
                                    listOf("gpt-4o-mini", "gpt-4.1-mini", "auto")
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    quickModels.forEach { qm ->
                                        val isChosen = modelEdit.equals(qm, ignoreCase = true)
                                        Surface(
                                            shape = ZCalShapes.Pill,
                                            color = if (isChosen) colors.accent.copy(alpha = 0.16f) else colors.surface,
                                            border = BorderStroke(1.dp, if (isChosen) colors.accent else colors.outline),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { modelEdit = qm }
                                        ) {
                                            Text(
                                                text = if (qm == "auto") "auto" else qm.substringAfter("gemini-").substringAfter("gpt-"),
                                                style = ZCalTypeScale.Label.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (isChosen) colors.accent else colors.textSecondary,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // Thinking Level Speed Tag
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.accent.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.25f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = strings.thinkingLevelLabel,
                                        style = ZCalTypeScale.Label,
                                        color = colors.accent,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }

                                OutlinedTextField(
                                    value = modelEdit,
                                    onValueChange = { modelEdit = it },
                                    label = { Text(strings.modelLabel) },
                                    supportingText = { Text(strings.modelSupportingText) },
                                    shape = ZCalShapes.Input,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedButton(
                                    onClick = { viewModel.loadModelChoices(provider, keyInput, baseUrlEdit) },
                                    enabled = !loadingModels,
                                    shape = ZCalShapes.Pill,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (loadingModels) strings.loadingModels else strings.chooseModelsBtn)
                                }

                                OutlinedTextField(
                                    value = baseUrlEdit,
                                    onValueChange = { baseUrlEdit = it },
                                    label = { Text(strings.baseUrlLabel) },
                                    supportingText = {
                                        Text(
                                            if (provider == AiProviderType.GEMINI) strings.baseUrlGeminiHint
                                            else strings.baseUrlOpenAiHint
                                        )
                                    },
                                    shape = ZCalShapes.Input,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = keyInput,
                                    onValueChange = { keyInput = it },
                                    label = { Text(strings.apiKeyLabel) },
                                    visualTransformation = PasswordVisualTransformation(),
                                    shape = ZCalShapes.Input,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.saveAndVerifyProviderKey(provider, keyInput, modelEdit, baseUrlEdit) { ok ->
                                                if (ok) keyInput = ""
                                            }
                                        },
                                        shape = ZCalShapes.Pill,
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Text(strings.verifyAndSaveBtn, color = colors.onAccent, fontWeight = FontWeight.Bold)
                                    }
                                    if (maskedKey != null) {
                                        OutlinedButton(
                                            onClick = { viewModel.removeCurrentKey() },
                                            shape = ZCalShapes.Pill,
                                            modifier = Modifier.weight(1f).height(48.dp)
                                        ) {
                                            Text(strings.removeKeyBtn, color = colors.error)
                                        }
                                    }
                                }

                                verifyMsg?.let { msg ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = colors.surfaceElevated,
                                        border = BorderStroke(1.dp, colors.outline),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            msg,
                                            style = ZCalTypeScale.Label,
                                            color = colors.accent,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }

                                SettingsToggleRow(
                                    title = strings.allowPreviewModels,
                                    subtitle = strings.allowPreviewDesc,
                                    checked = allowPreview,
                                    onCheckedChange = { viewModel.setAllowPreviewModels(it) }
                                )
                            }
                        }
                    }
                }

                SettingsSection.BODY_GOALS -> {
                    item {
                        val isImperial = profile.units == UnitSystem.IMPERIAL
                        val displayWeight = if (isImperial) profile.weightKg * 2.20462 else profile.weightKg
                        val displayHeight = if (isImperial) profile.heightCm / 2.54 else profile.heightCm
                        val displayTargetWeight = if (isImperial) profile.targetWeightKg * 2.20462 else profile.targetWeightKg

                        var editWeight by remember(profile.weightKg, isImperial) {
                            mutableStateOf(String.format(Locale.US, "%.1f", displayWeight))
                        }
                        var editHeight by remember(profile.heightCm, isImperial) {
                            mutableStateOf(displayHeight.roundToInt().toString())
                        }
                        var editTargetWeight by remember(profile.targetWeightKg, isImperial) {
                            mutableStateOf(String.format(Locale.US, "%.1f", displayTargetWeight))
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ZCalShapes.Card,
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.outline)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(strings.bodyMetricsTitle, style = ZCalTypeScale.Title, color = colors.textPrimary)

                                // Unit System Switcher
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    UnitSystem.entries.forEach { u ->
                                        val selected = profile.units == u
                                        Surface(
                                            shape = ZCalShapes.Pill,
                                            color = if (selected) colors.accent else colors.surfaceElevated,
                                            border = BorderStroke(1.dp, if (selected) colors.accent else colors.outline),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                                .clickable { viewModel.setUnitSystem(u) }
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Text(
                                                    text = if (u == UnitSystem.METRIC) strings.metric else strings.imperial,
                                                    style = ZCalTypeScale.Label.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                                                    color = if (selected) colors.onAccent else colors.textPrimary
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = editWeight,
                                        onValueChange = { editWeight = it },
                                        label = { Text(if (isImperial) "${strings.weightLabel} (lbs)" else "${strings.weightLabel} (kg)") },
                                        modifier = Modifier.weight(1f),
                                        shape = ZCalShapes.Input
                                    )
                                    OutlinedTextField(
                                        value = editHeight,
                                        onValueChange = { editHeight = it },
                                        label = { Text(if (isImperial) "${strings.heightLabel} (in)" else "${strings.heightLabel} (cm)") },
                                        modifier = Modifier.weight(1f),
                                        shape = ZCalShapes.Input
                                    )
                                    OutlinedTextField(
                                        value = editTargetWeight,
                                        onValueChange = { editTargetWeight = it },
                                        label = { Text(if (isImperial) "${strings.targetWeightLabel} (lbs)" else "${strings.targetWeightLabel} (kg)") },
                                        modifier = Modifier.weight(1f),
                                        shape = ZCalShapes.Input
                                    )
                                }

                                // Targets summary card
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = colors.surfaceElevated,
                                    border = BorderStroke(1.dp, colors.outline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            "Target: %,d kcal • %d g Protein • %d g Carbs • %d g Fat".format(
                                                profile.calorieGoal,
                                                profile.proteinGoalG,
                                                profile.carbsGoalG,
                                                profile.fatGoalG
                                            ),
                                            style = ZCalTypeScale.BodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            "CLA: %,d mg • Fiber: %d g".format(profile.claGoalMg, profile.fiberGoalG),
                                            style = ZCalTypeScale.Label,
                                            color = colors.textSecondary
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        val parsedW = editWeight.toDoubleOrNull() ?: displayWeight
                                        val parsedH = editHeight.toDoubleOrNull() ?: displayHeight
                                        val parsedTw = editTargetWeight.toDoubleOrNull() ?: displayTargetWeight

                                        val finalWeightKg = if (isImperial) parsedW / 2.20462 else parsedW
                                        val finalHeightCm = if (isImperial) parsedH * 2.54 else parsedH
                                        val finalTargetWeightKg = if (isImperial) parsedTw / 2.20462 else parsedTw

                                        viewModel.updateBodyMetrics(finalWeightKg, finalHeightCm, finalTargetWeightKg)
                                    },
                                    shape = ZCalShapes.Pill,
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Text(
                                        strings.updateTargetsBtn,
                                        color = colors.onAccent,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Personal Calibration
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ZCalShapes.Card,
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.outline)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "${strings.personalCalibrationTitle}: ${if (profile.personalCalibrationPct >= 0) "+" else ""}${profile.personalCalibrationPct}%",
                                    style = ZCalTypeScale.Title,
                                    color = colors.textPrimary
                                )
                                Text(
                                    strings.personalCalibrationDesc,
                                    style = ZCalTypeScale.Label,
                                    color = colors.textSecondary
                                )
                                Slider(
                                    value = profile.personalCalibrationPct.toFloat(),
                                    onValueChange = { viewModel.updateCalibrationPct(it.roundToInt()) },
                                    valueRange = -25f..25f
                                )
                            }
                        }
                    }
                }

                SettingsSection.APPEARANCE -> {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ZCalShapes.Card,
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.outline)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(strings.themeAndDisplayTitle, style = ZCalTypeScale.Title, color = colors.textPrimary)

                                ThemePreference.entries.chunked(2).forEach { rowPair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowPair.forEach { t ->
                                            val themeLabel = when (t) {
                                                ThemePreference.SYSTEM -> strings.system
                                                ThemePreference.LIGHT -> strings.light
                                                ThemePreference.DARK -> strings.dark
                                                ThemePreference.AMOLED -> strings.amoled
                                            }
                                            val isSel = themePref == t
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(42.dp)
                                                    .clip(ZCalShapes.Pill)
                                                    .clickable { viewModel.setTheme(t) },
                                                shape = ZCalShapes.Pill,
                                                color = if (isSel) colors.accent else colors.surfaceVariant,
                                                border = BorderStroke(1.dp, if (isSel) colors.accent else colors.outline)
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Text(
                                                        themeLabel,
                                                        style = ZCalTypeScale.Label.copy(
                                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                                        ),
                                                        color = if (isSel) colors.onAccent else colors.textPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                SettingsToggleRow(
                                    title = strings.calmCalendarPalette,
                                    subtitle = strings.calmCalendarDesc,
                                    checked = calmPalette,
                                    onCheckedChange = { viewModel.setCalmPalette(it) }
                                )

                                SettingsToggleRow(
                                    title = strings.forgiveOneDayTitle,
                                    subtitle = strings.forgiveOneDayDesc,
                                    checked = forgiveOneDay,
                                    onCheckedChange = { viewModel.setForgiveOneDay(it) }
                                )

                                SettingsToggleRow(
                                    title = strings.hideNumbersTitle,
                                    subtitle = strings.hideNumbersDesc,
                                    checked = hideNumbers,
                                    onCheckedChange = { viewModel.setHideNumbers(it) }
                                )

                                SettingsToggleRow(
                                    title = strings.dynamicColorTitle,
                                    subtitle = strings.dynamicColorDesc,
                                    checked = dynamicColor,
                                    onCheckedChange = { viewModel.setDynamicColor(it) }
                                )
                            }
                        }
                    }
                }

                SettingsSection.LANGUAGE -> {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ZCalShapes.Card,
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.outline)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(strings.language, style = ZCalTypeScale.Title, color = colors.textPrimary)

                                AppLanguage.entries.forEach { lang ->
                                    val isSel = appLang == lang
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { viewModel.setLanguage(lang) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSel) colors.accent.copy(alpha = 0.12f) else colors.surfaceElevated,
                                        border = BorderStroke(1.dp, if (isSel) colors.accent else colors.outline)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = lang.nativeTitle,
                                                    style = ZCalTypeScale.Body.copy(fontWeight = FontWeight.Bold),
                                                    color = if (isSel) colors.accent else colors.textPrimary
                                                )
                                                Text(
                                                    text = lang.title,
                                                    style = ZCalTypeScale.Label,
                                                    color = colors.textSecondary
                                                )
                                            }

                                            if (isSel) {
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
                            }
                        }
                    }
                }

                SettingsSection.DATA_PRIVACY -> {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ZCalShapes.Card,
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.outline)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(strings.dataAndAttributionTitle, style = ZCalTypeScale.Title, color = colors.textPrimary)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.exportJsonBackup(context) },
                                        shape = ZCalShapes.Pill,
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Text(strings.exportBackupBtn)
                                    }
                                    OutlinedButton(
                                        onClick = { showResetDialog = true },
                                        shape = ZCalShapes.Pill,
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Text(strings.resetLogBtn, color = colors.error)
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = colors.surfaceElevated,
                                    border = BorderStroke(1.dp, colors.outline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            "Privacy Architecture",
                                            style = ZCalTypeScale.BodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            "ZCal is an offline-first app. Your personal logs, body metrics, and encryption keys never leave your device. All API calls go directly to your configured provider.",
                                            style = ZCalTypeScale.Label,
                                            color = colors.textSecondary
                                        )
                                    }
                                }

                                Text(
                                    text = strings.attributionNote,
                                    style = ZCalTypeScale.Label,
                                    color = colors.textSecondary
                                )

                                Text(
                                    text = "ZCal v1.4.0 • Native Jetpack Compose",
                                    style = ZCalTypeScale.Label,
                                    color = colors.accent
                                )
                            }
                        }
                    }
                }
                null -> {}
            }
        }
    }

    // Model selection dialog
    modelChoices?.let { choices ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissModelChoices() },
            title = { Text(strings.chooseModelDialogTitle) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = { modelEdit = ModelSelector.AUTO; viewModel.dismissModelChoices() }) {
                        Text(strings.autoRecommended)
                    }
                    if (choices.recommended.isNotEmpty()) {
                        Text(strings.suitedForFood, style = ZCalTypeScale.Label, color = colors.textSecondary)
                        choices.recommended.forEach { id ->
                            TextButton(onClick = { modelEdit = id; viewModel.dismissModelChoices() }) { Text(id) }
                        }
                    }
                    if (choices.others.isNotEmpty()) {
                        Text(strings.otherModels, style = ZCalTypeScale.Label, color = colors.textSecondary)
                        choices.others.forEach { id ->
                            TextButton(onClick = { modelEdit = id; viewModel.dismissModelChoices() }) { Text(id) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissModelChoices() }) { Text(strings.closeBtn) }
            }
        )
    }
}

@Composable
private fun SettingsHubCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    onClick: () -> Unit
) {
    val colors = LocalZCalColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = ZCalTypeScale.Title.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = ZCalTypeScale.Label,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.textSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalZCalColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = ZCalTypeScale.Body, color = colors.textPrimary)
            Text(subtitle, style = ZCalTypeScale.Label, color = colors.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
