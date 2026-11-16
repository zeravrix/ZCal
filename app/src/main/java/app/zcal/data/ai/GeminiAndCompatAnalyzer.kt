package app.zcal.data.ai

import android.util.Base64
import app.zcal.data.secure.KeyVault
import app.zcal.domain.model.AiProviderType
import app.zcal.domain.model.MealAnalysis
import app.zcal.domain.model.UnitSystem
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.network.sockets.SocketTimeoutException as KtorSocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [FoodAnalyzer] supporting:
 * 1. Google Gemini (`POST /v1beta/models/{model}:generateContent`) with `x-goog-api-key` header,
 *    strict `responseSchema`, low temperature (0.1), and zero thinking overhead for fast response.
 * 2. OpenAI-Compatible endpoints (`POST /v1/chat/completions`) for OpenAI, OpenRouter, Groq, and local Ollama.
 * 3. Automatic 1s / 2s / 4s exponential backoff on HTTP 429 or 503 overload (Section 6 table).
 * 4. One automatic retry on malformed JSON before falling back to manual entry.
 */
@Singleton
class MultiProviderFoodAnalyzer @Inject constructor(
    private val httpClient: HttpClient,
    private val keyVault: KeyVault
) : FoodAnalyzer {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val backoffDelaysMs = listOf(1000L, 2000L, 4000L)

    override suspend fun analyzeMeal(
        imageFiles: List<File>,
        userNote: String?,
        unitSystem: UnitSystem,
        existingAnalysis: MealAnalysis?,
        correctionInstruction: String?
    ): FoodAnalysisResult {
        val provider = keyVault.activeProviderFlow.first()
        val apiKey = keyVault.getApiKey(provider)
        if (apiKey.isNullOrBlank()) {
            return FoodAnalysisResult.InvalidKey("No API key configured. Paste your key in Settings.")
        }
        val configuredModel = keyVault.activeModelIdFlow.first()
        val baseUrl = keyVault.customBaseUrlFlow.first()
        val isAuto = configuredModel.equals(ModelSelector.AUTO, ignoreCase = true)
        var modelId = resolveModel(provider, apiKey, baseUrl, configuredModel)

        val userPrompt = PromptBuilder.buildUserPrompt(
            userNote = userNote,
            unitSystem = unitSystem,
            existingAnalysis = existingAnalysis,
            correctionInstruction = correctionInstruction
        )

        // Allow 1 automatic retry if the model emits malformed JSON (Section 6 error table)
        var jsonAttempt = 0
        var reResolved = false
        while (jsonAttempt < 2) {
            val rawCallResult = executeWithBackoff(
                provider = provider,
                apiKey = apiKey,
                modelId = modelId,
                baseUrl = baseUrl,
                imageFiles = imageFiles,
                userPrompt = userPrompt
            )

            when (rawCallResult) {
                is RawHttpOutcome.SuccessText -> {
                    when (val validated = ResponseValidator.parseAndValidate(rawCallResult.modelText)) {
                        is ResponseValidator.ValidationResult.Valid -> {
                            return FoodAnalysisResult.Success(
                                analysis = validated.analysis,
                                rawJson = validated.cleanedJson
                            )
                        }
                        is ResponseValidator.ValidationResult.NotFood -> {
                            return FoodAnalysisResult.NotFood
                        }
                        is ResponseValidator.ValidationResult.Malformed -> {
                            if (jsonAttempt == 1) {
                                return FoodAnalysisResult.MalformedResponse(validated.reason)
                            }
                            jsonAttempt++
                        }
                    }
                }
                is RawHttpOutcome.Failure -> {
                    if (rawCallResult.modelGone && !reResolved) {
                        // The model was retired or unavailable: clear cached resolved model and switch to fallbackModel
                        reResolved = true
                        keyVault.clearResolvedModel(provider)
                        modelId = if (isAuto) {
                            resolveModel(provider, apiKey, baseUrl, configuredModel)
                        } else {
                            provider.fallbackModel
                        }
                        continue
                    }
                    return rawCallResult.error
                }
            }
        }

        return FoodAnalysisResult.MalformedResponse("Could not parse meal analysis after retry.")
    }

    override suspend fun verifyApiKey(
        candidateKey: String,
        modelId: String,
        baseUrl: String
    ): KeyVerificationResult {
        val trimmedKey = candidateKey.trim()
        if (trimmedKey.isEmpty()) {
            return KeyVerificationResult.Invalid("API key cannot be empty.")
        }
        val provider = keyVault.activeProviderFlow.first()
        val defaultModelToTest = if (!modelId.trim().equals(ModelSelector.AUTO, ignoreCase = true) && modelId.isNotBlank()) {
            modelId.trim().removePrefix("models/")
        } else {
            provider.fallbackModel
        }

        // Fast-path: probe the primary model directly in one quick HTTP call (< 600ms)
        val fastResult = probeModel(provider, trimmedKey, baseUrl, defaultModelToTest)
        if (fastResult is KeyVerificationResult.Valid) {
            keyVault.saveResolvedModel(provider, defaultModelToTest)
            return fastResult
        }
        val err = (fastResult as KeyVerificationResult.Invalid).errorMessage
        val isExplicitKeyError = err.contains("API_KEY_INVALID", ignoreCase = true) ||
            err.contains("API key not valid", ignoreCase = true) ||
            err.contains("invalid api key", ignoreCase = true)
        if (isExplicitKeyError) {
            return fastResult
        }

        // Fallback for custom or enterprise keys where default model might not be enabled
        val ids = fetchModelIds(provider, trimmedKey, baseUrl)
        val allowPreview = keyVault.allowPreviewModelsFlow.first()
        val ranked = ids?.let { ModelSelector.rank(provider, it, allowPreview) }.orEmpty()
        val candidates = (listOf(defaultModelToTest, provider.fallbackModel) + ranked).distinct()
        var last: KeyVerificationResult = fastResult
        for (candidate in candidates) {
            if (candidate == defaultModelToTest) continue
            when (val res = probeModel(provider, trimmedKey, baseUrl, candidate)) {
                is KeyVerificationResult.Valid -> {
                    keyVault.saveResolvedModel(provider, candidate)
                    return res
                }
                is KeyVerificationResult.Invalid -> {
                    if (res.errorMessage.contains("API_KEY_INVALID", ignoreCase = true)) return res
                    last = res
                }
            }
        }
        return last
    }

    override suspend fun listAvailableModels(candidateKey: String, baseUrl: String): ModelChoices? {
        val provider = keyVault.activeProviderFlow.first()
        val key = candidateKey.trim().ifEmpty { keyVault.getApiKey(provider).orEmpty() }
        if (key.isEmpty()) return null
        val ids = fetchModelIds(provider, key, baseUrl) ?: return null
        val recommended = ModelSelector.rank(provider, ids, allowPreview = true)
        return ModelChoices(recommended, ids.filter { it !in recommended }.distinct().sorted())
    }

    /** Re-reads the cached pick weekly so a newly released model is adopted without an app update. */
    private suspend fun resolveModel(
        provider: AiProviderType,
        apiKey: String,
        baseUrl: String,
        configured: String
    ): String {
        if (!configured.equals(ModelSelector.AUTO, ignoreCase = true)) return configured
        val cached = keyVault.getResolvedModel(provider)
        val weekMs = 7L * 24 * 60 * 60 * 1000
        if (cached != null && System.currentTimeMillis() - cached.second < weekMs) return cached.first
        val ids = fetchModelIds(provider, apiKey, baseUrl)
        val best = ids?.let { ModelSelector.pickBest(provider, it, keyVault.allowPreviewModelsFlow.first()) }
        if (best != null) {
            keyVault.saveResolvedModel(provider, best)
            return best
        }
        return cached?.first ?: provider.fallbackModel // listing failed: keep the last good pick, never block a scan
    }

    /** Model IDs this key may call, or null when the list cannot be fetched. */
    private suspend fun fetchModelIds(provider: AiProviderType, apiKey: String, baseUrl: String): List<String>? {
        return try {
            if (provider == AiProviderType.GEMINI) {
                val base = baseUrl.trimEnd('/').ifEmpty { AiProviderType.GEMINI.defaultBaseUrl }
                val ids = ArrayList<String>()
                var pageToken: String? = null
                repeat(4) {
                    val url = "$base/v1beta/models?pageSize=100" + (pageToken?.let { "&pageToken=$it" } ?: "")
                    val response = httpClient.get(url) { header("x-goog-api-key", apiKey) }
                    if (response.status.value !in 200..299) return if (ids.isEmpty()) null else ids
                    val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
                    root["models"]?.jsonArray?.forEach { m ->
                        val o = m.jsonObject
                        val methods = o["supportedGenerationMethods"]?.jsonArray?.map { it.jsonPrimitive.content }
                        if (methods == null || "generateContent" in methods) {
                            o["name"]?.jsonPrimitive?.content?.let { ids += it.removePrefix("models/") }
                        }
                    }
                    pageToken = root["nextPageToken"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    if (pageToken == null) return ids.ifEmpty { null }
                }
                ids.ifEmpty { null }
            } else {
                val base = baseUrl.trimEnd('/').ifEmpty { AiProviderType.OPENAI_COMPATIBLE.defaultBaseUrl }
                val response = httpClient.get("$base/models") { header("Authorization", "Bearer $apiKey") }
                if (response.status.value !in 200..299) return null
                json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]?.jsonArray
                    ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
                    ?.ifEmpty { null }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun probeModel(
        provider: AiProviderType,
        trimmedKey: String,
        baseUrl: String,
        modelId: String
    ): KeyVerificationResult {
        val startMs = System.currentTimeMillis()

        return try {
            val response = if (provider == AiProviderType.GEMINI) {
                val cleanBase = baseUrl.trimEnd('/').ifEmpty { AiProviderType.GEMINI.defaultBaseUrl }
                val cleanModel = modelId.removePrefix("models/").trim().ifEmpty { AiProviderType.GEMINI.fallbackModel }
                val url = "$cleanBase/v1beta/models/$cleanModel:generateContent"
                val payload = buildJsonObject {
                    putJsonArray("contents") {
                        addJsonObject {
                            putJsonArray("parts") {
                                addJsonObject { put("text", "Reply with OK") }
                            }
                        }
                    }
                    putJsonObject("generationConfig") {
                        put("maxOutputTokens", 128)
                        put("temperature", 0.1)
                    }
                }
                httpClient.post(url) {
                    header("x-goog-api-key", trimmedKey)
                    contentType(ContentType.Application.Json)
                    setBody(payload.toString())
                    timeout {
                        requestTimeoutMillis = 10_000L
                        connectTimeoutMillis = 5_000L
                        socketTimeoutMillis = 10_000L
                    }
                }
            } else {
                val cleanBase = baseUrl.trimEnd('/').ifEmpty { AiProviderType.OPENAI_COMPATIBLE.defaultBaseUrl }
                val cleanModel = modelId.trim().ifEmpty { AiProviderType.OPENAI_COMPATIBLE.fallbackModel }
                val url = "$cleanBase/chat/completions"
                val payload = buildJsonObject {
                    put("model", cleanModel)
                    putJsonArray("messages") {
                        addJsonObject {
                            put("role", "user")
                            put("content", "Reply with OK")
                        }
                    }
                    put("max_tokens", 64)
                }
                httpClient.post(url) {
                    header("Authorization", "Bearer $trimmedKey")
                    contentType(ContentType.Application.Json)
                    setBody(payload.toString())
                    timeout {
                        requestTimeoutMillis = 10_000L
                        connectTimeoutMillis = 5_000L
                        socketTimeoutMillis = 10_000L
                    }
                }
            }

            val elapsed = System.currentTimeMillis() - startMs
            if (response.status.value in 200..299) {
                val cleanModel = if (provider == AiProviderType.GEMINI) modelId.removePrefix("models/").trim() else modelId.trim()
                KeyVerificationResult.Valid(latencyMs = elapsed, modelUsed = cleanModel.ifEmpty { provider.fallbackModel })
            } else {
                val body = response.bodyAsText().take(240)
                KeyVerificationResult.Invalid("HTTP ${response.status.value}: $body")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KeyVerificationResult.Invalid(e.localizedMessage ?: "Network error while verifying key.")
        }
    }

    private sealed class RawHttpOutcome {
        data class SuccessText(val modelText: String) : RawHttpOutcome()
        data class Failure(val error: FoodAnalysisResult, val modelGone: Boolean = false) : RawHttpOutcome()
    }

    private suspend fun executeWithBackoff(
        provider: AiProviderType,
        apiKey: String,
        modelId: String,
        baseUrl: String,
        imageFiles: List<File>,
        userPrompt: String
    ): RawHttpOutcome {
        var attempt = 0
        while (true) {
            try {
                val response = if (provider == AiProviderType.GEMINI) {
                    callGeminiApi(apiKey, modelId, baseUrl, imageFiles, userPrompt)
                } else {
                    callOpenAiCompatibleApi(apiKey, modelId, baseUrl, imageFiles, userPrompt)
                }

                val status = response.status.value
                val bodyText = response.bodyAsText()

                when {
                    status in 200..299 -> {
                        val extracted = if (provider == AiProviderType.GEMINI) {
                            extractGeminiCandidateText(bodyText)
                        } else {
                            extractOpenAiChoiceText(bodyText)
                        }
                        return if (extracted != null) {
                            RawHttpOutcome.SuccessText(extracted)
                        } else {
                            RawHttpOutcome.Failure(
                                FoodAnalysisResult.MalformedResponse("Model returned an empty candidate.")
                            )
                        }
                    }
                    status == 401 || (status == 400 && looksLikeKeyProblem(bodyText)) || (status == 403 && looksLikeKeyProblem(bodyText)) -> {
                        val who = if (provider == AiProviderType.GEMINI) "Google" else "The provider"
                        return RawHttpOutcome.Failure(
                            FoodAnalysisResult.InvalidKey("$who did not accept this key (HTTP $status). Check it and try again.")
                        )
                    }
                    status == 400 || status == 403 || status == 404 || status == 413 || status == 422 -> {
                        val isModelUnavailable = status == 404 || status == 403 ||
                            bodyText.contains("not found", ignoreCase = true) ||
                            bodyText.contains("permission to access model", ignoreCase = true) ||
                            bodyText.contains("not available", ignoreCase = true) ||
                            bodyText.contains("deprecated", ignoreCase = true)
                        return RawHttpOutcome.Failure(
                            FoodAnalysisResult.UnknownError(
                                "Request rejected (HTTP $status). Check the model name and base URL in Settings. ${extractErrorMessage(bodyText)}".trim()
                            ),
                            modelGone = isModelUnavailable
                        )
                    }
                    status == 429 || status == 503 -> {
                        if (attempt < backoffDelaysMs.size) {
                            delay(backoffDelaysMs[attempt])
                            attempt++
                            continue
                        } else {
                            return RawHttpOutcome.Failure(FoodAnalysisResult.RateLimited())
                        }
                    }
                    else -> {
                        return RawHttpOutcome.Failure(
                            FoodAnalysisResult.UnknownError("Provider error (HTTP $status). Try again or enter manually.")
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e // never swallow coroutine cancellation (user left the screen)
            } catch (e: KtorSocketTimeoutException) {
                return RawHttpOutcome.Failure(
                    FoodAnalysisResult.Timeout("Request timed out. Tap Retry or edit manually.")
                )
            } catch (e: HttpRequestTimeoutException) {
                return RawHttpOutcome.Failure(
                    FoodAnalysisResult.Timeout("Request timed out. Tap Retry or edit manually.")
                )
            } catch (e: SocketTimeoutException) {
                return RawHttpOutcome.Failure(
                    FoodAnalysisResult.Timeout("Request timed out. Tap Retry or edit manually.")
                )
            } catch (e: UnknownHostException) {
                return RawHttpOutcome.Failure(
                    FoodAnalysisResult.Offline(imageFiles.firstOrNull()?.absolutePath)
                )
            } catch (e: IOException) {
                return RawHttpOutcome.Failure(
                    FoodAnalysisResult.Offline(imageFiles.firstOrNull()?.absolutePath)
                )
            } catch (e: Exception) {
                return RawHttpOutcome.Failure(
                    FoodAnalysisResult.UnknownError(e.localizedMessage ?: "Unexpected analysis failure.")
                )
            }
        }
    }

    private class EncodedImage(val mime: String, val base64: String)

    private suspend fun encodeImages(files: List<File>): List<EncodedImage> = withContext(Dispatchers.IO) {
        files.filter { it.exists() }.map { file ->
            val mime = if (file.extension.equals("webp", ignoreCase = true)) "image/webp" else "image/jpeg"
            EncodedImage(mime, Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
        }
    }

    private fun looksLikeKeyProblem(body: String): Boolean {
        val lower = body.lowercase()
        return "api key" in lower || "api_key" in lower || "invalid_api_key" in lower || "unauthorized" in lower
    }

    private fun extractErrorMessage(body: String): String = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content?.take(200)
    }.getOrNull().orEmpty()

    private suspend fun callGeminiApi(
        apiKey: String,
        modelId: String,
        baseUrl: String,
        imageFiles: List<File>,
        userPrompt: String
    ): io.ktor.client.statement.HttpResponse {
        val images = encodeImages(imageFiles)
        val cleanModel = modelId.removePrefix("models/").trim().ifEmpty { AiProviderType.GEMINI.fallbackModel }
        return httpClient.post("${baseUrl.trimEnd('/')}/v1beta/models/$cleanModel:generateContent") {
        // Section 7 rule: Send the key in the x-goog-api-key header, NEVER in the URL
        header("x-goog-api-key", apiKey)
        contentType(ContentType.Application.Json)

        val requestJson = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    addJsonObject { put("text", PromptBuilder.SYSTEM_INSTRUCTION) }
                }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        // Section 6 Step 2: Put the text prompt FIRST before the image(s)
                        addJsonObject { put("text", userPrompt) }
                        images.forEach { img ->
                            addJsonObject {
                                putJsonObject("inlineData") {
                                    put("mimeType", img.mime)
                                    put("data", img.base64)
                                }
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.1)
                put("responseMimeType", "application/json")
                put("responseSchema", buildGeminiResponseSchema())
            }
        }
        setBody(requestJson.toString())
    }
    }

    private suspend fun callOpenAiCompatibleApi(
        apiKey: String,
        modelId: String,
        baseUrl: String,
        imageFiles: List<File>,
        userPrompt: String
    ): io.ktor.client.statement.HttpResponse {
        val images = encodeImages(imageFiles)
        return httpClient.post("${baseUrl.trimEnd('/')}/chat/completions") {
        header("Authorization", "Bearer $apiKey")
        contentType(ContentType.Application.Json)

        val requestJson = buildJsonObject {
            put("model", modelId)
            put("temperature", 0.1)
            putJsonObject("response_format") { put("type", "json_object") }
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", PromptBuilder.SYSTEM_INSTRUCTION + "\n\n" + PromptBuilder.JSON_SHAPE_HINT)
                }
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", userPrompt)
                        }
                        images.forEach { img ->
                            addJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    put("url", "data:${img.mime};base64,${img.base64}")
                                }
                            }
                        }
                    }
                }
            }
        }
        setBody(requestJson.toString())
    }
    }

    private fun buildGeminiResponseSchema(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("is_food") { put("type", "BOOLEAN") }
            putJsonObject("meal_name") { put("type", "STRING") }
            putJsonObject("calories_low") { put("type", "INTEGER") }
            putJsonObject("calories_high") { put("type", "INTEGER") }
            putJsonObject("assumptions") {
                put("type", "ARRAY")
                putJsonObject("items") { put("type", "STRING") }
            }
            putJsonObject("items") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("name") { put("type", "STRING") }
                        putJsonObject("grams") { put("type", "NUMBER") }
                        putJsonObject("calories") { put("type", "NUMBER") }
                        putJsonObject("kilojoules") { put("type", "NUMBER") }
                        putJsonObject("cla_mg") { put("type", "NUMBER") }
                        putJsonObject("protein_g") { put("type", "NUMBER") }
                        putJsonObject("carbs_g") { put("type", "NUMBER") }
                        putJsonObject("fat_g") { put("type", "NUMBER") }
                        putJsonObject("fiber_g") { put("type", "NUMBER") }
                        putJsonObject("confidence") {
                            put("type", "STRING")
                            putJsonArray("enum") {
                                add(kotlinx.serialization.json.JsonPrimitive("high"))
                                add(kotlinx.serialization.json.JsonPrimitive("medium"))
                                add(kotlinx.serialization.json.JsonPrimitive("low"))
                            }
                        }
                    }
                    putJsonArray("required") {
                        add(kotlinx.serialization.json.JsonPrimitive("name"))
                        add(kotlinx.serialization.json.JsonPrimitive("grams"))
                        add(kotlinx.serialization.json.JsonPrimitive("calories"))
                        add(kotlinx.serialization.json.JsonPrimitive("protein_g"))
                        add(kotlinx.serialization.json.JsonPrimitive("carbs_g"))
                        add(kotlinx.serialization.json.JsonPrimitive("fat_g"))
                        add(kotlinx.serialization.json.JsonPrimitive("confidence"))
                    }
                }
            }
        }
        putJsonArray("required") {
            add(kotlinx.serialization.json.JsonPrimitive("is_food"))
            add(kotlinx.serialization.json.JsonPrimitive("meal_name"))
            add(kotlinx.serialization.json.JsonPrimitive("items"))
            add(kotlinx.serialization.json.JsonPrimitive("calories_low"))
            add(kotlinx.serialization.json.JsonPrimitive("calories_high"))
            add(kotlinx.serialization.json.JsonPrimitive("assumptions"))
        }
    }

    private fun extractGeminiCandidateText(rawBody: String): String? = runCatching {
        val root = json.parseToJsonElement(rawBody).jsonObject
        val parts = root["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")
            ?.jsonObject?.get("parts")?.jsonArray
        parts?.mapNotNull { part ->
            val obj = part.jsonObject
            val isThought = obj["thought"]?.jsonPrimitive?.content == "true"
            if (isThought) null else obj["text"]?.jsonPrimitive?.content
        }?.joinToString("")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun extractOpenAiChoiceText(rawBody: String): String? = runCatching {
        val root = json.parseToJsonElement(rawBody).jsonObject
        root["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")
            ?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
