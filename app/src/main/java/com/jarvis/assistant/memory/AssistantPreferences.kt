package com.jarvis.assistant.memory

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jarvis.assistant.avatar.AvatarConfiguration
import com.jarvis.assistant.avatar.AvatarPerformanceTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.assistantDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "assistant_identity_and_preferences"
)

enum class VoiceLanguage(val code: String, val displayName: String, val promptGuidance: String) {
    ENGLISH(
        code = "en-IN",
        displayName = "English",
        promptGuidance = "Speak in natural, clear English with crisp pronunciation and a composed, helpful tone."
    ),
    HINDI(
        code = "hi-IN",
        displayName = "Hindi (हिन्दी)",
        promptGuidance = "Speak in natural, polite conversational Hindi (हिन्दी)."
    ),
    HINGLISH(
        code = "hinglish",
        displayName = "Hinglish (Hindi + English)",
        promptGuidance = "Speak in natural conversational Hinglish (a seamless mix of Hindi and English commonly spoken in India)."
    ),
    SPANISH(
        code = "es-ES",
        displayName = "Spanish (Español)",
        promptGuidance = "Speak in natural, clear Spanish."
    ),
    FRENCH(
        code = "fr-FR",
        displayName = "French (Français)",
        promptGuidance = "Speak in natural, clear French."
    );

    companion object {
        fun fromCode(code: String): VoiceLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

enum class AuthMode(val id: String, val label: String) {
    DEV_API_KEY("DEV_API_KEY", "Development API Key (Direct BidiGenerateContent)"),
    EPHEMERAL_TOKEN("EPHEMERAL_TOKEN", "Production Ephemeral Token (BidiGenerateContentConstrained)");

    companion object {
        fun fromId(id: String): AuthMode = entries.firstOrNull { it.id == id } ?: DEV_API_KEY
    }
}

data class AssistantIdentityState(
    val assistantName: String = "",
    val isOnboardingCompleted: Boolean = false,
    val activeModelId: String = DEFAULT_GEMINI_LIVE_MODEL,
    val voiceName: String = "Aoede",
    val language: VoiceLanguage = VoiceLanguage.ENGLISH,
    val continuousConversation: Boolean = true,
    val saveTranscriptHistory: Boolean = true,
    val automationEnabledByUser: Boolean = true,
    val authMode: AuthMode = AuthMode.DEV_API_KEY,
    val customWakeWordEnabled: Boolean = false,
    val avatarPerformanceTier: AvatarPerformanceTier = AvatarPerformanceTier.BALANCED,
    val avatarAnimationSpeed: Float = 1.0f,
    val avatarGlbAssetPath: String = AvatarConfiguration.DEFAULT_GLB_ASSET_PATH
) {
    companion object {
        const val DEFAULT_GEMINI_LIVE_MODEL = "gemini-3.1-flash-live-preview"
        val PRESET_LIVE_MODELS = listOf(
            "gemini-3.1-flash-live-preview",
            "gemini-2.5-flash-native-audio-preview-12-2025",
            "gemini-3.8-live"
        )
        val EXAMPLE_NAMES = listOf("JARVIS", "YESMIN", "FRIDAY", "ALEX", "MYRA")
    }
}

class AssistantPreferences(private val context: Context) {

    private object Keys {
        val ASSISTANT_NAME = stringPreferencesKey("assistant_name")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val ACTIVE_MODEL_ID = stringPreferencesKey("active_model_id")
        val VOICE_NAME = stringPreferencesKey("voice_name")
        val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
        val CONTINUOUS_CONVERSATION = booleanPreferencesKey("continuous_conversation")
        val SAVE_TRANSCRIPT_HISTORY = booleanPreferencesKey("save_transcript_history")
        val AUTOMATION_ENABLED = booleanPreferencesKey("automation_enabled_by_user")
        val AUTH_MODE = stringPreferencesKey("auth_mode")
        val CUSTOM_WAKE_WORD_ENABLED = booleanPreferencesKey("custom_wake_word_enabled")
        val AVATAR_PERFORMANCE_TIER = stringPreferencesKey("avatar_performance_tier")
        val AVATAR_ANIMATION_SPEED = floatPreferencesKey("avatar_animation_speed")
        val AVATAR_GLB_ASSET_PATH = stringPreferencesKey("avatar_glb_asset_path")
    }

    val identityFlow: Flow<AssistantIdentityState> = context.assistantDataStore.data.map { prefs ->
        val rawName = prefs[Keys.ASSISTANT_NAME]?.trim().orEmpty()
        AssistantIdentityState(
            assistantName = rawName,
            isOnboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] == true && rawName.isNotEmpty(),
            activeModelId = prefs[Keys.ACTIVE_MODEL_ID]?.takeIf { it.isNotBlank() }
                ?: AssistantIdentityState.DEFAULT_GEMINI_LIVE_MODEL,
            voiceName = prefs[Keys.VOICE_NAME] ?: "Aoede",
            language = VoiceLanguage.fromCode(prefs[Keys.VOICE_LANGUAGE] ?: VoiceLanguage.ENGLISH.code),
            continuousConversation = prefs[Keys.CONTINUOUS_CONVERSATION] ?: true,
            saveTranscriptHistory = prefs[Keys.SAVE_TRANSCRIPT_HISTORY] ?: true,
            automationEnabledByUser = prefs[Keys.AUTOMATION_ENABLED] ?: true,
            authMode = AuthMode.fromId(prefs[Keys.AUTH_MODE] ?: AuthMode.DEV_API_KEY.id),
            customWakeWordEnabled = prefs[Keys.CUSTOM_WAKE_WORD_ENABLED] ?: false,
            avatarPerformanceTier = AvatarPerformanceTier.fromName(prefs[Keys.AVATAR_PERFORMANCE_TIER]),
            avatarAnimationSpeed = (prefs[Keys.AVATAR_ANIMATION_SPEED] ?: 1.0f).coerceIn(0.5f, 2.0f),
            avatarGlbAssetPath = prefs[Keys.AVATAR_GLB_ASSET_PATH]?.takeIf { it.isNotBlank() }
                ?: AvatarConfiguration.DEFAULT_GLB_ASSET_PATH
        )
    }

    suspend fun setAssistantName(rawName: String): Result<String> {
        val validation = validateAssistantName(rawName)
        if (validation.isFailure) return validation
        val cleanName = validation.getOrThrow()
        context.assistantDataStore.edit { prefs ->
            prefs[Keys.ASSISTANT_NAME] = cleanName
            prefs[Keys.ONBOARDING_COMPLETED] = true
        }
        return Result.success(cleanName)
    }

    suspend fun setActiveModelId(modelId: String): Result<String> {
        val clean = modelId.trim().removePrefix("models/")
        if (clean.isEmpty() || !clean.matches(Regex("^[a-zA-Z0-9._-]+$"))) {
            return Result.failure(IllegalArgumentException("Model ID must contain only letters, numbers, hyphens, dots, or underscores."))
        }
        context.assistantDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_MODEL_ID] = clean
        }
        return Result.success(clean)
    }

    suspend fun setVoiceLanguage(language: VoiceLanguage) {
        context.assistantDataStore.edit { prefs ->
            prefs[Keys.VOICE_LANGUAGE] = language.code
        }
    }

    suspend fun setVoiceName(voiceName: String) {
        context.assistantDataStore.edit { prefs ->
            prefs[Keys.VOICE_NAME] = voiceName
        }
    }

    suspend fun setContinuousConversation(enabled: Boolean) {
        context.assistantDataStore.edit { prefs ->
            prefs[Keys.CONTINUOUS_CONVERSATION] = enabled
        }
    }

    suspend fun setSaveTranscriptHistory(enabled: Boolean) {
        context.assistantDataStore.edit { prefs ->
            prefs[Keys.SAVE_TRANSCRIPT_HISTORY] = enabled
        }
    }

    suspend fun setAutomationEnabledByUser(enabled: Boolean) {
        context.assistantDataStore.edit { prefs ->
            prefs[Keys.AUTOMATION_ENABLED] = enabled
        }
    }

    suspend fun setAuthMode(mode: AuthMode) {
        context.assistantDataStore.edit { prefs ->
            prefs[Keys.AUTH_MODE] = mode.id
        }
    }

    suspend fun setAvatarPerformanceTier(tier: AvatarPerformanceTier) {
        context.assistantDataStore.edit { prefs ->
            prefs[Keys.AVATAR_PERFORMANCE_TIER] = tier.name
        }
    }

    suspend fun setAvatarAnimationSpeed(speed: Float) {
        context.assistantDataStore.edit { prefs ->
            prefs[Keys.AVATAR_ANIMATION_SPEED] = speed.coerceIn(0.5f, 2.0f)
        }
    }

    suspend fun setAvatarGlbAssetPath(path: String) {
        val clean = path.trim().removePrefix("/")
        if (clean.isNotEmpty()) {
            context.assistantDataStore.edit { prefs ->
                prefs[Keys.AVATAR_GLB_ASSET_PATH] = clean
            }
        }
    }

    companion object {
        private val VALID_NAME_REGEX = Regex("^[\\p{L}\\p{N}][\\p{L}\\p{N} _-]{0,23}$")

        fun validateAssistantName(input: String): Result<String> {
            val trimmed = input.trim().replace(Regex("\\s+"), " ")
            if (trimmed.isEmpty()) {
                return Result.failure(IllegalArgumentException("Assistant name cannot be empty."))
            }
            if (trimmed.length < 2) {
                return Result.failure(IllegalArgumentException("Assistant name must be at least 2 characters."))
            }
            if (trimmed.length > 24) {
                return Result.failure(IllegalArgumentException("Assistant name must be 24 characters or fewer."))
            }
            if (!VALID_NAME_REGEX.matches(trimmed)) {
                return Result.failure(
                    IllegalArgumentException("Name contains invalid characters. Use letters, numbers, spaces, hyphens, or underscores.")
                )
            }
            return Result.success(trimmed)
        }
    }
}
