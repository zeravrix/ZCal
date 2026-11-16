package app.zcal.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import app.zcal.domain.model.AppLanguage
import app.zcal.ui.localization.LocalZCalStrings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import app.zcal.domain.model.UnitSystem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
import app.zcal.domain.model.ThemePreference
import app.zcal.domain.model.UserProfile
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
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val mealRepository: MealRepository,
    private val keyVault: KeyVault,
    private val foodAnalyzer: FoodAnalyzer,
    private val computeGoalsUseCase: app.zcal.domain.usecase.ComputeGoalsUseCase
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

    /** Fetches the models this key can use so the user can pick one (blank key = use the stored one). */
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

    /**
     * Saves provider, base URL and model, but only keeps them if they WORK. A typed key is verified and stored; with a
     * blank key the stored key is used, so the model can be changed without pasting the key again. A failed check
     * restores the previous settings so a typo can never break scanning.
     */
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
            val modelToTest = modelId.trim().ifEmpty { ModelSelector.AUTO }
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

    fun exportJsonBackup(context: android.content.Context) {
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
 * Section 4 Page 6 & Section 7/10/12 Settings Screen:
 * - Masked API Key display (`••••••••ABCD`) + Remove Key + Verify Key
 * - Editable AI Provider & Model ID (`gemini-2.5-flash`, `gemini-3.5-flash-lite`, OpenAI, Ollama)
 * - Personal Calibration Slider (`-25%` to `+25%`, Section 6 Accuracy Lever #6)
 * - Theme Selector (`System`, `Light`, `Dark`, `AMOLED Pure Black`) & Wallpaper Colors toggle
 * - Calm Calendar Palette & Hide Numbers/Streaks toggles (Section 10 & 11)
 * - JSON / CSV Backup & Open Food Facts ODbL Attribution
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

    var keyInput by remember { mutableStateOf("") }
    var modelEdit by remember(modelId) { mutableStateOf(modelId) }
    var baseUrlEdit by remember(baseUrl) { mutableStateOf(baseUrl) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .displayCutoutPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(strings.settings, style = ZCalTypeScale.Headline, color = colors.textPrimary)
        }

        // Language Selector Card (8 Languages)
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = ZCalShapes.Card,
                color = colors.surfaceElevated,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, colors.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(strings.language, style = ZCalTypeScale.Title, color = colors.textPrimary)
                    }

                    AppLanguage.entries.chunked(2).forEach { rowPair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPair.forEach { lang ->
                                val isSel = appLang == lang
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(ZCalShapes.Pill)
                                        .clickable { viewModel.setLanguage(lang) },
                                    shape = ZCalShapes.Pill,
                                    color = if (isSel) colors.accent else colors.surfaceVariant,
                                    border = BorderStroke(1.dp, if (isSel) colors.accent else colors.outline)
                                ) {
                                    androidx.compose.foundation.layout.Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = "${lang.nativeTitle} (${lang.title})",
                                            style = ZCalTypeScale.Label.copy(
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 12.sp
                                            ),
                                            color = if (isSel) colors.onAccent else colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1. AI Provider & Android Keystore Key Vault
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = ZCalShapes.Card,
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.byokTitle, style = ZCalTypeScale.Title, color = colors.textPrimary)
                    Text(
                        text = "${strings.storedKey}: ${maskedKey ?: strings.noKeyConfigured}",
                        style = ZCalTypeScale.BodySmall,
                        color = colors.textSecondary
                    )

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
                                androidx.compose.foundation.layout.Box(
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

                    OutlinedTextField(
                        value = modelEdit,
                        onValueChange = { modelEdit = it },
                        label = { Text(strings.modelLabel) },
                        supportingText = {
                            Text(strings.modelSupportingText)
                        },
                        shape = ZCalShapes.Input,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val quickModels = if (provider == AiProviderType.GEMINI) {
                        listOf("auto", "gemini-4-hydrogen", "gemini-3.8-flash", "gemini-2.5-flash", "gemini-2.0-flash")
                    } else {
                        listOf("auto", "gpt-4o-mini", "gpt-4.1-mini")
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
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp)
                                )
                            }
                        }
                    }

                    SettingsToggleRow(
                        title = strings.allowPreviewModels,
                        subtitle = strings.allowPreviewDesc,
                        checked = allowPreview,
                        onCheckedChange = { viewModel.setAllowPreviewModels(it) }
                    )

                    OutlinedButton(
                        onClick = { viewModel.loadModelChoices(provider, keyInput, baseUrlEdit) },
                        enabled = !loadingModels,
                        shape = ZCalShapes.Pill
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
                                    if (ok) keyInput = "" // keep what was typed if the check failed, so nothing is retyped
                                }
                            },
                            shape = ZCalShapes.Pill,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Text(strings.verifyAndSaveBtn, color = colors.onAccent)
                        }
                        if (maskedKey != null) {
                            OutlinedButton(
                                onClick = { viewModel.removeCurrentKey() },
                                shape = ZCalShapes.Pill,
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) {
                                Text(strings.removeKeyBtn)
                            }
                        }
                    }

                    verifyMsg?.let { msg ->
                        Text(msg, style = ZCalTypeScale.Label, color = colors.accent)
                    }

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
            }
        }

        // 2. Personal Calibration Lever (Section 6 Accuracy Lever #6)
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = ZCalShapes.Card,
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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

        // Body Measurements & Long-Term Metabolic Target Recalculation
        item {
            val isImperial = profile.units == UnitSystem.IMPERIAL
            val displayWeight = if (isImperial) profile.weightKg * 2.20462 else profile.weightKg
            val displayHeight = if (isImperial) profile.heightCm / 2.54 else profile.heightCm
            val displayTargetWeight = if (isImperial) profile.targetWeightKg * 2.20462 else profile.targetWeightKg

            var editWeight by remember(profile.weightKg, isImperial) {
                mutableStateOf(String.format(java.util.Locale.US, "%.1f", displayWeight))
            }
            var editHeight by remember(profile.heightCm, isImperial) {
                mutableStateOf(displayHeight.roundToInt().toString())
            }
            var editTargetWeight by remember(profile.targetWeightKg, isImperial) {
                mutableStateOf(String.format(java.util.Locale.US, "%.1f", displayTargetWeight))
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = ZCalShapes.Card,
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.bodyMetricsTitle, style = ZCalTypeScale.Title, color = colors.textPrimary)
                    }

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
                                    .height(36.dp)
                                    .clickable { viewModel.setUnitSystem(u) }
                            ) {
                                androidx.compose.foundation.layout.Box(
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

                    Text(
                        text = if (isImperial) {
                            "Current: %.1f lbs • %d in • Calorie Target: %,d kcal".format(
                                profile.weightKg * 2.20462,
                                (profile.heightCm / 2.54).roundToInt(),
                                profile.calorieGoal
                            )
                        } else {
                            "Current: %.1f kg • %d cm • Calorie Target: %,d kcal".format(
                                profile.weightKg,
                                profile.heightCm.roundToInt(),
                                profile.calorieGoal
                            )
                        },
                        style = ZCalTypeScale.BodySmall,
                        color = colors.textSecondary
                    )

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

        // 3. Appearance (Light, Dark, AMOLED Pure Black, Material You, Calm Palette)
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = ZCalShapes.Card,
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.themeAndDisplayTitle, style = ZCalTypeScale.Title, color = colors.textPrimary)

                    ThemePreference.entries.chunked(2).forEach { rowPair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPair.forEach { t ->
                                val themeLabel = when(t) {
                                    ThemePreference.SYSTEM -> strings.system
                                    ThemePreference.LIGHT -> strings.light
                                    ThemePreference.DARK -> strings.dark
                                    ThemePreference.AMOLED -> strings.amoled
                                }
                                val isSel = themePref == t
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clip(ZCalShapes.Pill)
                                        .clickable { viewModel.setTheme(t) },
                                    shape = ZCalShapes.Pill,
                                    color = if (isSel) colors.accent else colors.surfaceVariant,
                                    border = BorderStroke(1.dp, if (isSel) colors.accent else colors.outline)
                                ) {
                                    androidx.compose.foundation.layout.Box(
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

        // 4. Data Export, Reset & Open Food Facts Attribution
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = ZCalShapes.Card,
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.dataAndAttributionTitle, style = ZCalTypeScale.Title, color = colors.textPrimary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.exportJsonBackup(context) },
                            shape = ZCalShapes.Pill,
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Text(strings.exportBackupBtn)
                        }
                        OutlinedButton(
                            onClick = { viewModel.resetAllData() },
                            shape = ZCalShapes.Pill,
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Text(strings.resetLogBtn, color = colors.error)
                        }
                    }
                    Text(
                        text = strings.attributionNote,
                        style = ZCalTypeScale.Label,
                        color = colors.textSecondary
                    )
                }
            }
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
