package com.jarvis.assistant.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jarvis.assistant.memory.AssistantPreferences
import com.jarvis.assistant.memory.AuthMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
    val latencyMs: Long = 0L
)

class SettingsRepository(
    context: Context,
    val assistantPreferences: AssistantPreferences
) {
    private val appContext = context.applicationContext

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "secure_gemini_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _hasCredentialFlow = MutableStateFlow(hasConfiguredCredential())
    val hasCredentialFlow: StateFlow<Boolean> = _hasCredentialFlow.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun saveApiCredential(credential: String): Result<Unit> {
        val trimmed = credential.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Credential cannot be empty."))
        }
        if (trimmed.length < 16 || trimmed.contains(" ")) {
            return Result.failure(IllegalArgumentException("Credential format appears invalid (too short or contains spaces)."))
        }
        encryptedPrefs.edit().putString(KEY_API_CREDENTIAL, trimmed).apply()
        _hasCredentialFlow.value = true
        return Result.success(Unit)
    }

    fun clearApiCredential() {
        encryptedPrefs.edit().remove(KEY_API_CREDENTIAL).apply()
        _hasCredentialFlow.value = false
    }

    fun getApiCredentialOrNull(): String? {
        return encryptedPrefs.getString(KEY_API_CREDENTIAL, null)?.takeIf { it.isNotBlank() }
    }

    fun getMaskedCredentialPreview(): String {
        val cred = getApiCredentialOrNull() ?: return "Not configured"
        if (cred.length <= 8) return "Configured (****)"
        return "${cred.take(4)}••••••••${cred.takeLast(4)}"
    }

    fun hasConfiguredCredential(): Boolean = getApiCredentialOrNull() != null

    /**
     * Tests the configured Gemini API key and checks whether the selected model exists and is accessible.
     * Never logs the API key.
     */
    suspend fun testConnection(modelId: String, authMode: AuthMode): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            val credential = getApiCredentialOrNull()
                ?: return@withContext ConnectionTestResult(
                    success = false,
                    message = "Missing API credential. Please enter your Gemini API key or ephemeral token in Settings."
                )

            val cleanModel = modelId.trim().removePrefix("models/")
            val startMs = System.currentTimeMillis()
            try {
                val url = when (authMode) {
                    AuthMode.DEV_API_KEY ->
                        "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel?key=$credential"
                    AuthMode.EPHEMERAL_TOKEN ->
                        "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel?access_token=$credential"
                }
                val request = Request.Builder().url(url).get().build()
                httpClient.newCall(request).execute().use { response ->
                    val elapsed = System.currentTimeMillis() - startMs
                    val bodyStr = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val json = runCatching { JSONObject(bodyStr) }.getOrNull()
                        val displayName = json?.optString("displayName", cleanModel) ?: cleanModel
                        return@withContext ConnectionTestResult(
                            success = true,
                            message = "Verified model '$displayName' ($cleanModel) in ${elapsed}ms.",
                            latencyMs = elapsed
                        )
                    } else {
                        val errorMsg = runCatching {
                            JSONObject(bodyStr).optJSONObject("error")?.optString("message")
                        }.getOrNull()
                        val actionable = when (response.code) {
                            400, 403 -> "Invalid API key or permission denied (${response.code}): ${errorMsg ?: "Check your Gemini API key."}"
                            404 -> "Model '$cleanModel' not found (HTTP 404). Try 'gemini-3.1-flash-live-preview' or 'gemini-2.5-flash-native-audio-preview-12-2025'."
                            429 -> "API quota exhausted or rate-limited (HTTP 429). Check your Google AI Studio billing/quota."
                            else -> "HTTP ${response.code}: ${errorMsg ?: response.message}"
                        }
                        return@withContext ConnectionTestResult(
                            success = false,
                            message = actionable,
                            latencyMs = elapsed
                        )
                    }
                }
            } catch (e: Exception) {
                return@withContext ConnectionTestResult(
                    success = false,
                    message = "Network error during connection test: ${e.localizedMessage ?: "Check internet connection."}"
                )
            }
        }

    companion object {
        private const val KEY_API_CREDENTIAL = "gemini_api_credential"
    }
}
