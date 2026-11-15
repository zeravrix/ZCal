package app.zcal.data.ai

import app.zcal.domain.model.AiProviderType

/**
 * Picks the model that is best for ONE job: reading a meal photo and returning structured nutrition JSON.
 *
 * "Best for the job" is deliberately not "most powerful" and not "cheapest":
 *  - Pro-tier models are slower (often 10 s+), are frequently unavailable or heavily limited on free keys, and
 *    food-photo estimates are limited by portion ambiguity, not by raw reasoning power.
 *  - Lite-tier models are cheap and fast but weaker at telling similar foods and small items apart.
 *  - The Flash / "mini" tier is the balance: strong vision, reliable JSON, fast, and generous limits.
 *
 * So the order is: Flash tier first, then Pro, then Lite. Inside a tier the newest generation wins, and a stable
 * release beats a preview unless the user allowed previews and the preview is a newer generation.
 *
 * Pure functions on purpose: no Android, no network, fully unit-testable.
 */
object ModelSelector {

    /** The value stored in Settings when the user wants the app to choose. */
    const val AUTO = "auto"

    /** Words that mark a model as not a general vision-to-JSON model. */
    private val REJECT = listOf(
        "image", "imagen", "tts", "audio", "live", "native", "embedding", "embed", "aqa", "gemma", "learnlm",
        "thinking", "robotics", "computer", "customtools", "veo", "transcribe", "realtime", "moderation",
        "whisper", "search", "codex", "instruct", "davinci", "babbage", "-exp"
    )

    private enum class Tier(val rank: Int) { FLASH(3), PRO(2), LITE(1) }

    private data class Parsed(
        val id: String,          // exactly as the API returned it (may carry a "vendor/" prefix)
        val version: Double,
        val tier: Tier,
        val preview: Boolean
    )

    private val GEMINI = Regex("""(?:^|/)gemini-(\d+(?:\.\d+)?)-(flash-lite|flash|pro)(.*)$""")

    private fun parseGemini(rawId: String): Parsed? {
        val id = rawId.removePrefix("models/")
        val lower = id.lowercase()
        if (REJECT.any { lower.contains(it) }) return null
        val m = GEMINI.find(lower) ?: return null
        val tier = when (m.groupValues[2]) {
            "flash" -> Tier.FLASH
            "pro" -> Tier.PRO
            else -> Tier.LITE
        }
        return Parsed(
            id = id,
            version = m.groupValues[1].toDoubleOrNull() ?: return null,
            tier = tier,
            preview = m.groupValues[3].contains("preview")
        )
    }

    private val GPT_MINI = Regex("""(?:^|/)gpt-(\d+(?:\.\d+)?)o?-mini(.*)$""")

    private fun parseGptMini(rawId: String): Parsed? {
        val lower = rawId.lowercase()
        if (REJECT.any { lower.contains(it) }) return null
        val m = GPT_MINI.find(lower) ?: return null
        return Parsed(
            id = rawId,
            version = m.groupValues[1].toDoubleOrNull() ?: return null,
            tier = Tier.FLASH, // OpenAI's "mini" tier is the same balance point as Flash
            preview = m.groupValues[2].contains("preview")
        )
    }

    /** Local vision models for Ollama / LM Studio, best first. */
    private val LOCAL_VISION_ORDER = listOf("qwen3-vl", "qwen2.5vl", "qwen2.5-vl", "gemma3", "llama3.2-vision", "minicpm-v", "llava")

    /** Best-first list. Empty when nothing suitable is offered, and the caller then asks the user to choose. */
    fun rank(provider: AiProviderType, availableIds: List<String>, allowPreview: Boolean): List<String> {
        val ids = availableIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val geminiRanked = rankParsed(ids.mapNotNull(::parseGemini), allowPreview)
        if (provider == AiProviderType.GEMINI) return geminiRanked

        // OpenAI-compatible endpoints (OpenAI, OpenRouter, Ollama): same job, wider vocabulary.
        val gptRanked = rankParsed(ids.mapNotNull(::parseGptMini), allowPreview)
        val local = LOCAL_VISION_ORDER.flatMap { family ->
            ids.filter { it.lowercase().contains(family) && REJECT.none { r -> it.lowercase().contains(r) } }
                .sortedBy { it.length }
        }.distinct()
        return (geminiRanked + gptRanked + local).distinct()
    }

    fun pickBest(provider: AiProviderType, availableIds: List<String>, allowPreview: Boolean): String? =
        rank(provider, availableIds, allowPreview).firstOrNull()

    private fun rankParsed(parsed: List<Parsed>, allowPreview: Boolean): List<String> {
        if (parsed.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        for (tier in Tier.entries.sortedByDescending { it.rank }) {
            val inTier = parsed.filter { it.tier == tier }
            if (inTier.isEmpty()) continue
            val stable = inTier.filter { !it.preview }
                .sortedWith(compareByDescending<Parsed> { it.version }.thenBy { it.id.length })
            val preview = inTier.filter { it.preview }
                .sortedWith(compareByDescending<Parsed> { it.version }.thenBy { it.id.length })
            val newestStable = stable.firstOrNull()
            val newestPreview = preview.firstOrNull()
            val previewWins = allowPreview && newestPreview != null &&
                (newestStable == null || newestPreview.version > newestStable.version)
            val ordered = if (previewWins) preview + stable else stable + (if (allowPreview) preview else emptyList())
            ordered.forEach { out += it.id }
        }
        if (out.isEmpty() && !allowPreview) {
            // Only previews exist: a preview model beats having no model at all.
            return rankParsed(parsed, allowPreview = true)
        }
        return out.distinct()
    }
}
