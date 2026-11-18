package app.zcal.data.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.zcal.domain.model.AiProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.secureKeyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "secure_keys"
)

/**
 * Section 7 Compliant Hardware-Backed Key Vault:
 * - Never uses deprecated EncryptedSharedPreferences.
 * - Generates a 256-bit AES-GCM key inside AndroidKeyStore (`zcal_master_aes_key`).
 * - Stores only the 12-byte GCM IV + Ciphertext in `secure_keys.preferences_pb`, which is
 *   excluded from cloud/device backup via `backup_rules.xml` and `data_extraction_rules.xml`.
 * - Provides masked display (`••••ABCD`) and never logs raw keys.
 */
@Singleton
class KeyVault @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "zcal_master_aes_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128

        private val KEY_ENCRYPTED_GEMINI = stringPreferencesKey("enc_gemini_api_key")
        private val KEY_ENCRYPTED_COMPAT = stringPreferencesKey("enc_compat_api_key")
        private val KEY_PROVIDER_TYPE = stringPreferencesKey("ai_provider_type")
        private val KEY_MODEL_ID = stringPreferencesKey("ai_model_id")
        private val KEY_CUSTOM_BASE_URL = stringPreferencesKey("ai_custom_base_url")
        private val KEY_ALLOW_PREVIEW = booleanPreferencesKey("ai_allow_preview_models")
        private val KEY_RESOLVED_GEMINI = stringPreferencesKey("ai_resolved_model_gemini")
        private val KEY_RESOLVED_GEMINI_AT = longPreferencesKey("ai_resolved_model_gemini_at")
        private val KEY_RESOLVED_COMPAT = stringPreferencesKey("ai_resolved_model_compat")
        private val KEY_RESOLVED_COMPAT_AT = longPreferencesKey("ai_resolved_model_compat_at")
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingKey != null) {
            return existingKey.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv // 12 bytes standard GCM IV
        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        // Pack as: [1-byte IV length][IV bytes][Ciphertext + AuthTag bytes]
        val combined = ByteArray(1 + iv.size + cipherBytes.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encodedPayload: String): String? {
        return try {
            val combined = Base64.decode(encodedPayload, Base64.NO_WRAP)
            if (combined.size < 14) return null
            val ivLength = combined[0].toInt() and 0xFF
            if (ivLength <= 0 || combined.size <= 1 + ivLength) return null

            val iv = combined.copyOfRange(1, 1 + ivLength)
            val cipherBytes = combined.copyOfRange(1 + ivLength, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            // If key was invalidated or corrupted, fail safely without crashing
            null
        }
    }

    suspend fun saveApiKey(provider: AiProviderType, rawKey: String) {
        val trimmed = rawKey.trim()
        val prefKey = when (provider) {
            AiProviderType.GEMINI -> KEY_ENCRYPTED_GEMINI
            AiProviderType.OPENAI_COMPATIBLE -> KEY_ENCRYPTED_COMPAT
        }
        context.secureKeyDataStore.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(prefKey)
            } else {
                prefs[prefKey] = encrypt(trimmed)
            }
        }
    }

    suspend fun getApiKey(provider: AiProviderType): String? {
        val prefs = context.secureKeyDataStore.data.first()
        val prefKey = when (provider) {
            AiProviderType.GEMINI -> KEY_ENCRYPTED_GEMINI
            AiProviderType.OPENAI_COMPATIBLE -> KEY_ENCRYPTED_COMPAT
        }
        val encrypted = prefs[prefKey] ?: return null
        return decrypt(encrypted)
    }

    suspend fun removeApiKey(provider: AiProviderType) {
        saveApiKey(provider, "")
    }

    val maskedActiveKeyFlow: Flow<String?> = context.secureKeyDataStore.data.map { prefs ->
        val providerName = prefs[KEY_PROVIDER_TYPE] ?: AiProviderType.GEMINI.name
        val provider = runCatching { AiProviderType.valueOf(providerName) }.getOrDefault(AiProviderType.GEMINI)
        val prefKey = if (provider == AiProviderType.GEMINI) KEY_ENCRYPTED_GEMINI else KEY_ENCRYPTED_COMPAT
        val plain = prefs[prefKey]?.let { decrypt(it) }
        if (plain.isNullOrBlank()) {
            null
        } else if (plain.length <= 4) {
            "••••"
        } else {
            "••••••••••••" + plain.takeLast(4)
        }
    }

    val activeProviderFlow: Flow<AiProviderType> = context.secureKeyDataStore.data.map { prefs ->
        val name = prefs[KEY_PROVIDER_TYPE] ?: AiProviderType.GEMINI.name
        runCatching { AiProviderType.valueOf(name) }.getOrDefault(AiProviderType.GEMINI)
    }

    val activeModelIdFlow: Flow<String> = context.secureKeyDataStore.data.map { prefs ->
        val provider = runCatching {
            AiProviderType.valueOf(prefs[KEY_PROVIDER_TYPE] ?: AiProviderType.GEMINI.name)
        }.getOrDefault(AiProviderType.GEMINI)
        prefs[KEY_MODEL_ID]?.takeIf { it.isNotBlank() } ?: provider.defaultModel
    }

    val customBaseUrlFlow: Flow<String> = context.secureKeyDataStore.data.map { prefs ->
        val provider = runCatching {
            AiProviderType.valueOf(prefs[KEY_PROVIDER_TYPE] ?: AiProviderType.GEMINI.name)
        }.getOrDefault(AiProviderType.GEMINI)
        prefs[KEY_CUSTOM_BASE_URL]?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl
    }

    val allowPreviewModelsFlow: Flow<Boolean> = context.secureKeyDataStore.data.map { it[KEY_ALLOW_PREVIEW] ?: false }

    suspend fun setAllowPreviewModels(allow: Boolean) {
        context.secureKeyDataStore.edit {
            it[KEY_ALLOW_PREVIEW] = allow
            // A changed policy can change the winner, so force a fresh pick on the next scan.
            it.remove(KEY_RESOLVED_GEMINI); it.remove(KEY_RESOLVED_COMPAT)
        }
    }

    /** The model "auto" chose last time, with the time it was chosen (epoch ms), or null. */
    suspend fun getResolvedModel(provider: AiProviderType): Pair<String, Long>? {
        val prefs = context.secureKeyDataStore.data.first()
        val (mk, tk) = if (provider == AiProviderType.GEMINI) KEY_RESOLVED_GEMINI to KEY_RESOLVED_GEMINI_AT
        else KEY_RESOLVED_COMPAT to KEY_RESOLVED_COMPAT_AT
        val id = prefs[mk]?.takeIf { it.isNotBlank() } ?: return null
        return id to (prefs[tk] ?: 0L)
    }

    suspend fun saveResolvedModel(provider: AiProviderType, modelId: String) {
        context.secureKeyDataStore.edit {
            if (provider == AiProviderType.GEMINI) {
                it[KEY_RESOLVED_GEMINI] = modelId; it[KEY_RESOLVED_GEMINI_AT] = System.currentTimeMillis()
            } else {
                it[KEY_RESOLVED_COMPAT] = modelId; it[KEY_RESOLVED_COMPAT_AT] = System.currentTimeMillis()
            }
        }
    }

    suspend fun clearResolvedModel(provider: AiProviderType) {
        context.secureKeyDataStore.edit {
            if (provider == AiProviderType.GEMINI) { it.remove(KEY_RESOLVED_GEMINI); it.remove(KEY_RESOLVED_GEMINI_AT) }
            else { it.remove(KEY_RESOLVED_COMPAT); it.remove(KEY_RESOLVED_COMPAT_AT) }
        }
    }

    suspend fun updateProviderConfig(
        provider: AiProviderType,
        modelId: String,
        customBaseUrl: String
    ) {
        context.secureKeyDataStore.edit { prefs ->
            prefs[KEY_PROVIDER_TYPE] = provider.name
            prefs.remove(KEY_RESOLVED_GEMINI); prefs.remove(KEY_RESOLVED_COMPAT)
            prefs[KEY_MODEL_ID] = modelId.trim().ifEmpty { provider.defaultModel }
            prefs[KEY_CUSTOM_BASE_URL] = customBaseUrl.trim().trimEnd('/').ifEmpty { provider.defaultBaseUrl }
        }
    }
}
