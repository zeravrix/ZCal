package app.zcal.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
    private val foodAnalyzer: FoodAnalyzer
) : ViewModel() {

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
                    keyVault.updateProviderConfig(prevProvider, prevModel, prevBase)
                    _verifyMessage.value = "Not saved, previous settings kept. ${res.errorMessage}"
                    onDone(false)
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
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val colors = LocalZCalColors.current
    val context = LocalContext.current

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
            .background(colors.canvas),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Settings", style = ZCalTypeScale.Headline, color = colors.textPrimary)
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
                    Text("BYOK AI Provider & Key Vault", style = ZCalTypeScale.Title, color = colors.textPrimary)
                    Text(
                        text = "Stored Key: ${maskedKey ?: "None configured (Encrypted with Android Keystore AES-GCM)"}",
                        style = ZCalTypeScale.BodySmall,
                        color = colors.textSecondary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AiProviderType.entries.forEach { p ->
                            FilterChip(
                                selected = provider == p,
                                onClick = {
                                    modelEdit = p.defaultModel
                                    baseUrlEdit = p.defaultBaseUrl
                                    viewModel.saveAndVerifyProviderKey(p, "", p.defaultModel, p.defaultBaseUrl)
                                },
                                label = { Text(p.displayName) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = modelEdit,
                        onValueChange = { modelEdit = it },
                        label = { Text("Model (auto = best for food photos)") },
                        supportingText = {
                            Text(
                                "Leave as auto: ZCal checks which models your key can use and picks the best one for " +
                                    "reading food photos (balanced vision, JSON and speed, not the biggest or the cheapest). " +
                                    "Type a model ID only to override."
                            )
                        },
                        shape = ZCalShapes.Input,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SettingsToggleRow(
                        title = "Allow preview models",
                        subtitle = "Auto may pick a newer preview model. Previews can change or disappear, so this is off by default.",
                        checked = allowPreview,
                        onCheckedChange = { viewModel.setAllowPreviewModels(it) }
                    )

                    OutlinedButton(
                        onClick = { viewModel.loadModelChoices(provider, keyInput, baseUrlEdit) },
                        enabled = !loadingModels,
                        shape = ZCalShapes.Pill
                    ) {
                        Text(if (loadingModels) "Loading models..." else "Choose from my key's models")
                    }

                    OutlinedTextField(
                        value = baseUrlEdit,
                        onValueChange = { baseUrlEdit = it },
                        label = { Text("Base URL") },
                        supportingText = {
                            Text(
                                if (provider == AiProviderType.GEMINI) "Leave the default unless you use your own proxy."
                                else "OpenAI, OpenRouter (https://openrouter.ai/api/v1) or a local Ollama (http://10.0.2.2:11434/v1 in the emulator)."
                            )
                        },
                        shape = ZCalShapes.Input,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Replace or Paste New API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        shape = ZCalShapes.Input,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                viewModel.saveAndVerifyProviderKey(provider, keyInput, modelEdit, baseUrlEdit) { ok ->
                                    if (ok) keyInput = "" // keep what was typed if the check failed, so nothing is retyped
                                }
                            },
                            shape = ZCalShapes.Pill,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                        ) {
                            Text("Verify & Save", color = colors.onAccent)
                        }
                        if (maskedKey != null) {
                            OutlinedButton(
                                onClick = { viewModel.removeCurrentKey() },
                                shape = ZCalShapes.Pill
                            ) {
                                Text("Remove Key")
                            }
                        }
                    }

                    verifyMsg?.let { msg ->
                        Text(msg, style = ZCalTypeScale.Label, color = colors.accent)
                    }

                    modelChoices?.let { choices ->
                        AlertDialog(
                            onDismissRequest = { viewModel.dismissModelChoices() },
                            title = { Text("Choose a model") },
                            text = {
                                Column(
                                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    TextButton(onClick = { modelEdit = ModelSelector.AUTO; viewModel.dismissModelChoices() }) {
                                        Text("Auto (recommended): ZCal picks the best for food photos")
                                    }
                                    if (choices.recommended.isNotEmpty()) {
                                        Text("Suited to food photos, best first", style = ZCalTypeScale.Label, color = colors.textSecondary)
                                        choices.recommended.forEach { id ->
                                            TextButton(onClick = { modelEdit = id; viewModel.dismissModelChoices() }) { Text(id) }
                                        }
                                    }
                                    if (choices.others.isNotEmpty()) {
                                        Text("Other models on this key", style = ZCalTypeScale.Label, color = colors.textSecondary)
                                        choices.others.forEach { id ->
                                            TextButton(onClick = { modelEdit = id; viewModel.dismissModelChoices() }) { Text(id) }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { viewModel.dismissModelChoices() }) { Text("Close") }
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
                        "Personal AI Calibration: ${if (profile.personalCalibrationPct >= 0) "+" else ""}${profile.personalCalibrationPct}%",
                        style = ZCalTypeScale.Title,
                        color = colors.textPrimary
                    )
                    Text(
                        "Scales AI meal estimates up or down to account for systematic portion or oil differences.",
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

        // 3. Appearance (Light, Dark, AMOLED Pure Black, Material You, Calm Palette)
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = ZCalShapes.Card,
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Theme & Mindful Display", style = ZCalTypeScale.Title, color = colors.textPrimary)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreference.entries.forEach { t ->
                            FilterChip(
                                selected = themePref == t,
                                onClick = { viewModel.setTheme(t) },
                                label = { Text(t.name) }
                            )
                        }
                    }

                    SettingsToggleRow(
                        title = "Calm Calendar Palette",
                        subtitle = "Uses gentle green tints only instead of amber/coral on off-goal days",
                        checked = calmPalette,
                        onCheckedChange = { viewModel.setCalmPalette(it) }
                    )

                    SettingsToggleRow(
                        title = "Forgive 1 Missed Day per Week",
                        subtitle = "Keeps your logging streak alive if you miss a single day in a 7-day window",
                        checked = forgiveOneDay,
                        onCheckedChange = { viewModel.setForgiveOneDay(it) }
                    )

                    SettingsToggleRow(
                        title = "Hide Calorie Numbers & Streaks",
                        subtitle = "Focus on mindful photo food journaling without numeric totals",
                        checked = hideNumbers,
                        onCheckedChange = { viewModel.setHideNumbers(it) }
                    )

                    SettingsToggleRow(
                        title = "Wallpaper Dynamic Colors (Android 12+)",
                        subtitle = "Applies system accent while keeping P/C/F macro colors fixed",
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
                    Text("Your Data & Attribution", style = ZCalTypeScale.Title, color = colors.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.exportJsonBackup(context) },
                            shape = ZCalShapes.Pill,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Export JSON Backup")
                        }
                        OutlinedButton(
                            onClick = { viewModel.resetAllData() },
                            shape = ZCalShapes.Pill,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset Local Log", color = colors.error)
                        }
                    }
                    Text(
                        text = "Packaged food barcode data is sourced from Open Food Facts under the Open Database License (ODbL). Estimates are for informational tracking only and are not medical advice.",
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
