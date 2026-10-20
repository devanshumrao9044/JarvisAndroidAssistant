package com.jarvis.assistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.ai.PhoneActionType
import com.jarvis.assistant.ai.ScrollDirection
import com.jarvis.assistant.ai.ValidatedPhoneAction
import com.jarvis.assistant.automation.ActionPermissionManager
import com.jarvis.assistant.automation.AppLauncher
import com.jarvis.assistant.automation.AutomationLogEntry
import com.jarvis.assistant.automation.AutomationLogger
import com.jarvis.assistant.automation.PhoneActionRouter
import com.jarvis.assistant.avatar.AvatarConfiguration
import com.jarvis.assistant.avatar.AvatarExpression
import com.jarvis.assistant.avatar.AvatarGesture
import com.jarvis.assistant.avatar.AvatarModelLoadState
import com.jarvis.assistant.avatar.AvatarPerformanceTier
import com.jarvis.assistant.avatar.AvatarRenderer
import com.jarvis.assistant.data.ConnectionTestResult
import com.jarvis.assistant.data.SettingsRepository
import com.jarvis.assistant.memory.AssistantIdentityState
import com.jarvis.assistant.memory.AssistantPreferences
import com.jarvis.assistant.memory.AuthMode
import com.jarvis.assistant.memory.LocalConversationRepository
import com.jarvis.assistant.memory.TranscriptMessage
import com.jarvis.assistant.memory.VoiceLanguage
import com.jarvis.assistant.permissions.DevicePermissionSnapshot
import com.jarvis.assistant.permissions.PermissionManager
import com.jarvis.assistant.voice.GeminiLiveManager
import com.jarvis.assistant.voice.LiveSessionDiagnostics
import com.jarvis.assistant.voice.VoiceSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    val assistantPreferences = AssistantPreferences(appContext)
    val settingsRepository = SettingsRepository(appContext, assistantPreferences)
    val conversationRepository = LocalConversationRepository(appContext)
    val permissionManager = PermissionManager(appContext)
    val automationLogger = AutomationLogger()
    val actionPermissionManager = ActionPermissionManager(permissionManager)
    val appLauncher = AppLauncher(appContext)

    // Part 2: 3D Avatar Rendering, Expression, Animation & Voice Sync Engine
    val avatarRenderer = AvatarRenderer(
        context = appContext,
        scope = viewModelScope
    )

    lateinit var geminiLiveManager: GeminiLiveManager
        private set

    val phoneActionRouter = PhoneActionRouter(
        appLauncher = appLauncher,
        actionPermissionManager = actionPermissionManager,
        assistantPreferences = assistantPreferences,
        automationLogger = automationLogger,
        onStopSpeakingRequested = {
            if (::geminiLiveManager.isInitialized) {
                geminiLiveManager.stopSpeakingImmediately()
                avatarRenderer.voiceSynchronizer.onStopSpeakingTriggered(
                    sessionStillActive = geminiLiveManager.sessionState.value.isActiveSession
                )
            }
        }
    )

    init {
        geminiLiveManager = GeminiLiveManager(
            context = appContext,
            scope = viewModelScope,
            settingsRepository = settingsRepository,
            assistantPreferences = assistantPreferences,
            conversationRepository = conversationRepository,
            permissionManager = permissionManager,
            phoneActionRouter = phoneActionRouter,
            onAvatarExpressionRequested = { expr, gesture ->
                avatarRenderer.voiceSynchronizer.triggerContextualExpression(expr, gesture)
            },
            onConversationTurnTextObserved = { text, isUserTurn ->
                val inferred = avatarRenderer.expressionController.inferContextualExpression(text, isUserTurn)
                if (inferred != null) {
                    avatarRenderer.voiceSynchronizer.triggerContextualExpression(
                        expression = inferred.first,
                        gesture = inferred.second
                    )
                }
            }
        )

        viewModelScope.launch {
            conversationRepository.loadSavedHistory()
        }

        // Synchronize live voice states & audio amplitudes with the 3D Avatar
        viewModelScope.launch {
            combine(
                geminiLiveManager.sessionState,
                geminiLiveManager.audioInputManager.inputAmplitude,
                geminiLiveManager.audioOutputManager.outputAmplitude,
                geminiLiveManager.audioOutputManager.isPlaying
            ) { state, inAmp, outAmp, speaking ->
                 {
                    avatarRenderer.voiceSynchronizer.onVoiceFrameUpdate(
                        sessionState = state,
                        inputAmplitude = inAmp,
                        outputAmplitude = outAmp,
                        isAssistantSpeaking = speaking
                    )
                }
            }.collectLatest { updateBlock ->
                updateBlock()
            }
        }

        // Synchronize persisted 3D Avatar settings into AvatarRenderer
        viewModelScope.launch {
            assistantPreferences.identityFlow.collectLatest { identity ->
                avatarRenderer.updateConfiguration(
                    AvatarConfiguration(
                        primaryGlbAssetPath = identity.avatarGlbAssetPath,
                        animationSpeedMultiplier = identity.avatarAnimationSpeed,
                        performanceTier = identity.avatarPerformanceTier
                    )
                )
            }
        }
    }

    val identityState: StateFlow<AssistantIdentityState> = assistantPreferences.identityFlow
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            AssistantIdentityState()
        )

    val sessionState: StateFlow<VoiceSessionState> = geminiLiveManager.sessionState
    val diagnostics: StateFlow<LiveSessionDiagnostics> = geminiLiveManager.diagnostics
    val voiceAmplitude: StateFlow<Float> = geminiLiveManager.combinedVoiceAmplitude
    val isAssistantSpeaking: StateFlow<Boolean> = geminiLiveManager.audioOutputManager.isPlaying
    val avatarModelLoadState: StateFlow<AvatarModelLoadState> = avatarRenderer.modelLoadState

    val messages: StateFlow<List<TranscriptMessage>> = conversationRepository.messages
    val automationLogs: StateFlow<List<AutomationLogEntry>> = automationLogger.logs
    val latestPhoneStatusBanner: StateFlow<String> = automationLogger.latestStatusBanner
    val pendingConfirmationAction: StateFlow<ValidatedPhoneAction?> = actionPermissionManager.pendingConfirmation
    val hasApiCredential: StateFlow<Boolean> = settingsRepository.hasCredentialFlow

    private val _permissionSnapshot = MutableStateFlow(permissionManager.getPermissionSnapshot())
    val permissionSnapshot: StateFlow<DevicePermissionSnapshot> = _permissionSnapshot.asStateFlow()

    private val _connectionTestStatus = MutableStateFlow<ConnectionTestResult?>(null)
    val connectionTestStatus: StateFlow<ConnectionTestResult?> = _connectionTestStatus.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    fun refreshPermissions() {
        _permissionSnapshot.value = permissionManager.getPermissionSnapshot()
    }

    fun buildGreetingMessage(assistantName: String): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
        val cleanName = assistantName.ifBlank { "your AI Assistant" }
        return "$timeGreeting. I'm $cleanName."
    }

    fun saveAssistantName(
        newName: String,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = assistantPreferences.setAssistantName(newName)
            result.onSuccess { cleanName ->
                geminiLiveManager.onAssistantIdentityChanged(cleanName)
                avatarRenderer.voiceSynchronizer.triggerContextualExpression(
                    AvatarExpression.FRIENDLY,
                    AvatarGesture.GREETING_WAVE
                )
                onSuccess(cleanName)
            }.onFailure { err ->
                onError(err.message ?: "Invalid name.")
            }
        }
    }

    fun triggerAvatarExpressionManual(expression: AvatarExpression, gesture: AvatarGesture? = null) {
        avatarRenderer.voiceSynchronizer.triggerContextualExpression(
            expression = expression,
            gesture = gesture ?: expression.defaultGesture,
            holdDurationMs = 5000L
        )
    }

    fun setAvatarPerformanceTier(tier: AvatarPerformanceTier) {
        viewModelScope.launch {
            assistantPreferences.setAvatarPerformanceTier(tier)
        }
    }

    fun setAvatarAnimationSpeed(speed: Float) {
        viewModelScope.launch {
            assistantPreferences.setAvatarAnimationSpeed(speed)
        }
    }

    fun reloadAvatarModelAsset() {
        avatarRenderer.probeAndLoadConfiguredModel()
    }

    fun updateActiveModelId(
        modelId: String,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = assistantPreferences.setActiveModelId(modelId)
            res.onFailure { onError(it.message ?: "Invalid model ID.") }
        }
    }

    fun saveApiCredential(
        rawCredential: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val res = settingsRepository.saveApiCredential(rawCredential)
        res.onSuccess { onSuccess() }.onFailure { onError(it.message ?: "Invalid API key.") }
    }

    fun clearApiCredential() {
        settingsRepository.clearApiCredential()
        _connectionTestStatus.value = null
    }

    fun runConnectionTest() {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestStatus.value = null
            val currentIdentity = identityState.value
            val result = settingsRepository.testConnection(
                modelId = currentIdentity.activeModelId,
                authMode = currentIdentity.authMode
            )
            _connectionTestStatus.value = result
            _isTestingConnection.value = false
        }
    }

    fun setVoiceLanguage(language: VoiceLanguage) {
        viewModelScope.launch {
            assistantPreferences.setVoiceLanguage(language)
        }
    }

    fun setVoiceName(voiceName: String) {
        viewModelScope.launch {
            assistantPreferences.setVoiceName(voiceName)
        }
    }

    fun setContinuousConversation(enabled: Boolean) {
        viewModelScope.launch {
            assistantPreferences.setContinuousConversation(enabled)
        }
    }

    fun setSaveTranscriptHistory(enabled: Boolean) {
        viewModelScope.launch {
            assistantPreferences.setSaveTranscriptHistory(enabled)
        }
    }

    fun setAutomationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            assistantPreferences.setAutomationEnabledByUser(enabled)
        }
    }

    fun setAuthMode(mode: AuthMode) {
        viewModelScope.launch {
            assistantPreferences.setAuthMode(mode)
        }
    }

    fun startLiveVoiceSession() {
        refreshPermissions()
        avatarRenderer.voiceSynchronizer.triggerContextualExpression(
            AvatarExpression.FRIENDLY,
            AvatarGesture.GREETING_WAVE,
            holdDurationMs = 2500L
        )
        geminiLiveManager.startSession()
    }

    fun stopSpeaking() {
        geminiLiveManager.stopSpeakingImmediately()
        avatarRenderer.voiceSynchronizer.onStopSpeakingTriggered(
            sessionStillActive = geminiLiveManager.sessionState.value.isActiveSession
        )
    }

    fun endLiveVoiceSession() {
        geminiLiveManager.endSession(reason = null)
        avatarRenderer.voiceSynchronizer.onStopSpeakingTriggered(sessionStillActive = false)
    }

    fun sendTypedMessageOrCommand(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // Update 3D Avatar expression if the typed message matches a contextual emotion cue
        val inferred = avatarRenderer.expressionController.inferContextualExpression(trimmed, isUserTurn = true)
        if (inferred != null) {
            avatarRenderer.voiceSynchronizer.triggerContextualExpression(inferred.first, inferred.second)
        }

        if (sessionState.value.isActiveSession) {
            geminiLiveManager.sendTextTurn(trimmed)
        } else {
            executeDirectLocalCommandForTest(trimmed)
        }
    }

    fun confirmPendingSensitiveAction() {
        val pending = actionPermissionManager.consumePendingConfirmation() ?: return
        viewModelScope.launch {
            phoneActionRouter.executeAction(pending, isConfirmedByUser = true)
        }
    }

    fun cancelPendingSensitiveAction() {
        actionPermissionManager.cancelPendingConfirmation()
        automationLogger.updateLiveBanner("Cancelled sensitive action.")
    }

    fun runAccessibilityTestAction(action: ValidatedPhoneAction) {
        viewModelScope.launch {
            phoneActionRouter.executeAction(action, isConfirmedByUser = true)
        }
    }

    fun clearConversationHistory() {
        viewModelScope.launch {
            conversationRepository.clearAllHistory()
            automationLogger.clearLogs()
        }
    }

    fun stopAllAssistantActivity() {
        viewModelScope.launch {
            geminiLiveManager.endSession(reason = null)
            avatarRenderer.voiceSynchronizer.onStopSpeakingTriggered(sessionStillActive = false)
            actionPermissionManager.cancelPendingConfirmation()
            assistantPreferences.setAutomationEnabledByUser(false)
            automationLogger.updateLiveBanner("All assistant voice & phone control activity stopped.")
        }
    }

    override fun onCleared() {
        avatarRenderer.releaseGpuResources()
        super.onCleared()
    }

    private fun executeDirectLocalCommandForTest(input: String) {
        viewModelScope.launch {
            val lower = input.lowercase()
            if (lower == "stop speaking") {
                stopSpeaking()
                return@launch
            }
            val action = when {
                lower.startsWith("open ") -> ValidatedPhoneAction(
                    actionType = PhoneActionType.OPEN_APP,
                    targetAppName = input.substringAfter(" ").trim()
                )
                lower.contains("scroll down") -> ValidatedPhoneAction(
                    actionType = PhoneActionType.SCROLL,
                    scrollDirection = ScrollDirection.DOWN
                )
                lower.contains("scroll up") -> ValidatedPhoneAction(
                    actionType = PhoneActionType.SCROLL,
                    scrollDirection = ScrollDirection.UP
                )
                lower == "go back" || lower == "back" -> ValidatedPhoneAction(
                    actionType = PhoneActionType.GO_BACK
                )
                lower.contains("home screen") || lower == "go home" -> ValidatedPhoneAction(
                    actionType = PhoneActionType.GO_HOME
                )
                lower.contains("recent apps") || lower == "recents" -> ValidatedPhoneAction(
                    actionType = PhoneActionType.SHOW_RECENTS
                )
                lower.startsWith("tap ") || lower.startsWith("click ") -> ValidatedPhoneAction(
                    actionType = PhoneActionType.CLICK_ELEMENT,
                    targetText = input.substringAfter(" ").trim()
                )
                else -> null
            }

            if (action != null) {
                phoneActionRouter.executeAction(action, isConfirmedByUser = false)
            } else {
                automationLogger.updateLiveBanner("Avatar expression updated. Start Gemini Live to chat by voice.")
            }
        }
    }
}
