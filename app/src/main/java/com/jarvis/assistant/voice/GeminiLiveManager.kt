package com.jarvis.assistant.voice

import android.content.Context
import android.util.Base64
import com.jarvis.assistant.ai.ActionParser
import com.jarvis.assistant.ai.ActionSchema
import com.jarvis.assistant.ai.AssistantInstructions
import com.jarvis.assistant.automation.ActionExecutionResult
import com.jarvis.assistant.automation.PhoneActionRouter
import com.jarvis.assistant.avatar.AvatarExpression
import com.jarvis.assistant.avatar.AvatarGesture
import com.jarvis.assistant.data.SettingsRepository
import com.jarvis.assistant.memory.AssistantPreferences
import com.jarvis.assistant.memory.AuthMode
import com.jarvis.assistant.memory.LocalConversationRepository
import com.jarvis.assistant.memory.TranscriptRole
import com.jarvis.assistant.permissions.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job

/**
 * Real-time bidirectional voice & tool-calling engine using Google's official Gemini Live API
 * (`BidiGenerateContent` over WebSockets).
 * Supports both OS Phone Automation (`executePhoneAction`) and 3D Avatar Expression control (`setAvatarExpression`),
 * keeping them strictly isolated.
 */
class GeminiLiveManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val assistantPreferences: AssistantPreferences,
    private val conversationRepository: LocalConversationRepository,
    private val permissionManager: PermissionManager,
    private val phoneActionRouter: PhoneActionRouter,
    private val onAvatarExpressionRequested: (AvatarExpression, AvatarGesture?) -> Unit = { _, _ -> },
    private val onConversationTurnTextObserved: (String, Boolean) -> Unit = { _, _ -> }
) {
    private val appContext = context.applicationContext

    private val _sessionState = MutableStateFlow(VoiceSessionState.IDLE)
    val sessionState: StateFlow<VoiceSessionState> = _sessionState.asStateFlow()

    private val _diagnostics = MutableStateFlow(LiveSessionDiagnostics())
    val diagnostics: StateFlow<LiveSessionDiagnostics> = _diagnostics.asStateFlow()

    private var webSocket: WebSocket? = null
    private val userExplicitlyStopped = AtomicBoolean(true)
    private var modelTurnCompleted = AtomicBoolean(false)
    private var durationTimerJob: Job? = null
    private var reconnectJob: Job? = null
    private var activeSessionAssistantName: String = ""

    private val wsClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    val audioOutputManager = AudioOutputManager(
        scope = scope,
        onPlaybackStarted = {
            if (_sessionState.value != VoiceSessionState.IDLE &&
                _sessionState.value != VoiceSessionState.ERROR
            ) {
                _sessionState.value = VoiceSessionState.SPEAKING
            }
        },
        onPlaybackQueueDrained = {
            scope.launch {
                val prefs = assistantPreferences.identityFlow.first()
                conversationRepository.finalizePartialMessages(prefs.saveTranscriptHistory)
                if (!userExplicitlyStopped.get() && _sessionState.value == VoiceSessionState.SPEAKING) {
                    if (prefs.continuousConversation) {
                        _sessionState.value = VoiceSessionState.LISTENING
                    } else if (modelTurnCompleted.get()) {
                        endSession(reason = null)
                    }
                }
            }
        },
        onPlaybackError = { errMsg ->
            updateErrorState(errMsg, recoverable = false)
        }
    )

    val audioInputManager = AudioInputManager(
        scope = scope,
        isAssistantSpeakingProvider = { audioOutputManager.isPlaying.value },
        onUserBargeInDetected = {
            handleUserBargeIn()
        },
        onPcmChunkCaptured = { pcmBytes ->
            sendRealtimeAudioChunk(pcmBytes)
        },
        onCaptureError = { errMsg ->
            updateErrorState(errMsg, recoverable = false)
        }
    )

    val combinedVoiceAmplitude: StateFlow<Float> = combine(
        audioInputManager.inputAmplitude,
        audioOutputManager.outputAmplitude
    ) { inAmp, outAmp ->
        maxOf(inAmp, outAmp)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), 0f)

    init {
        VoiceForegroundService.onStopRequestedCallback = {
            endSession(reason = null)
        }
    }

    fun startSession() {
        scope.launch {
            if (_sessionState.value == VoiceSessionState.CONNECTING ||
                _sessionState.value == VoiceSessionState.LISTENING ||
                _sessionState.value == VoiceSessionState.SPEAKING
            ) {
                return@launch
            }

            if (!permissionManager.hasRecordAudioPermission()) {
                updateErrorState("Microphone permission is required to start a live voice conversation.", recoverable = false)
                return@launch
            }

            if (!permissionManager.isNetworkConnected()) {
                updateErrorState("No internet connection available. Please check Wi-Fi or mobile data.", recoverable = false)
                return@launch
            }

            val credential = settingsRepository.getApiCredentialOrNull()
            if (credential.isNullOrBlank()) {
                updateErrorState("Missing Gemini API key. Open Settings to configure your API key.", recoverable = false)
                return@launch
            }

            userExplicitlyStopped.set(false)
            reconnectJob?.cancel()

            val identity = assistantPreferences.identityFlow.first()
            activeSessionAssistantName = identity.assistantName.ifBlank { "Assistant" }

            _diagnostics.value = LiveSessionDiagnostics(
                activeModelId = identity.activeModelId,
                isConnected = false,
                callDurationSeconds = 0L,
                reconnectAttempt = 0,
                lastErrorMessage = null
            )

            connectWebSocketInternal(credential, isReconnect = false)
        }
    }

    fun onAssistantIdentityChanged(newAssistantName: String) {
        scope.launch {
            val prefs = assistantPreferences.identityFlow.first()
            conversationRepository.relabelAssistantMessages(
                newAssistantName = newAssistantName,
                persistToDisk = prefs.saveTranscriptHistory
            )
            if (!userExplicitlyStopped.get() && _sessionState.value.isActiveSession) {
                endSession(reason = null)
                delay(250)
                startSession()
            }
        }
    }

    fun sendTextTurn(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val ws = webSocket ?: return

        scope.launch {
            val prefs = assistantPreferences.identityFlow.first()
            conversationRepository.appendOrUpdateMessage(
                role = TranscriptRole.USER,
                speakerLabel = "You",
                text = clean,
                isPartial = false,
                persistToDisk = prefs.saveTranscriptHistory
            )
            onConversationTurnTextObserved(clean, true)

            audioOutputManager.stopPlaybackAndClearQueue()
            _sessionState.value = VoiceSessionState.PROCESSING

            val clientContentMsg = JSONObject().apply {
                put("clientContent", JSONObject().apply {
                    put("turns", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", clean)
                                })
                            })
                        })
                    })
                    put("turnComplete", true)
                })
            }
            ws.send(clientContentMsg.toString())
        }
    }

    fun stopSpeakingImmediately() {
        audioOutputManager.stopPlaybackAndClearQueue()
        if (!userExplicitlyStopped.get() && _sessionState.value.isActiveSession) {
            _sessionState.value = VoiceSessionState.LISTENING
        }
    }

    fun endSession(reason: String? = null) {
        userExplicitlyStopped.set(true)
        reconnectJob?.cancel()
        reconnectJob = null
        durationTimerJob?.cancel()
        durationTimerJob = null

        audioInputManager.stopCapture()
        audioOutputManager.stopPlaybackAndClearQueue()
        audioOutputManager.release()

        try {
            webSocket?.close(1000, "User ended session")
        } catch (_: Exception) {}
        webSocket = null

        VoiceForegroundService.stop(appContext)

        if (reason != null) {
            _sessionState.value = VoiceSessionState.ERROR
            _diagnostics.value = _diagnostics.value.copy(
                isConnected = false,
                lastErrorMessage = reason
            )
        } else {
            _sessionState.value = VoiceSessionState.IDLE
            _diagnostics.value = _diagnostics.value.copy(
                isConnected = false
            )
        }
    }

    private suspend fun connectWebSocketInternal(credential: String, isReconnect: Boolean) {
        val identity = assistantPreferences.identityFlow.first()
        val permSnapshot = permissionManager.getPermissionSnapshot()
        activeSessionAssistantName = identity.assistantName.ifBlank { "Assistant" }

        _sessionState.value = if (isReconnect) {
            VoiceSessionState.RECONNECTING
        } else {
            VoiceSessionState.CONNECTING
        }

        val endpointUrl = when (identity.authMode) {
            AuthMode.DEV_API_KEY ->
                "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$credential"
            AuthMode.EPHEMERAL_TOKEN ->
                "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContentConstrained?access_token=$credential"
        }

        val request = Request.Builder().url(endpointUrl).build()
        audioOutputManager.initializeTrack()

        webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                scope.launch {
                    sendInitialSetupMessage(
                        ws = webSocket,
                        assistantName = activeSessionAssistantName,
                        modelId = identity.activeModelId,
                        voiceName = identity.voiceName,
                        language = identity.language,
                        phoneControlEnabled = permSnapshot.isPhoneControlReady && identity.automationEnabledByUser
                    )
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerJsonPayload(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleServerJsonPayload(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!userExplicitlyStopped.get()) {
                    val nonRecoverable = code in listOf(1002, 1003, 1007, 1008, 4000, 4001, 4003, 4004)
                    val msg = if (reason.isNotBlank()) {
                        "Session closed ($code): $reason"
                    } else {
                        "Gemini Live session disconnected (code $code)."
                    }
                    updateErrorState(msg, recoverable = !nonRecoverable)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (userExplicitlyStopped.get()) return
                val httpCode = response?.code
                val nonRecoverable = httpCode in listOf(400, 401, 403, 404)
                val friendlyError = when (httpCode) {
                    400 -> "Invalid request or unsupported model configuration (HTTP 400)."
                    401, 403 -> "Invalid or unauthorized Gemini API key (HTTP $httpCode)."
                    404 -> "Selected model '${identity.activeModelId}' was not found on Gemini Live API (HTTP 404)."
                    429 -> "Gemini API quota exhausted or rate-limited (HTTP 429)."
                    else -> "Live connection error: ${t.localizedMessage ?: "Network lost"}"
                }
                updateErrorState(friendlyError, recoverable = !nonRecoverable)
            }
        })
    }

    private fun sendInitialSetupMessage(
        ws: WebSocket,
        assistantName: String,
        modelId: String,
        voiceName: String,
        language: com.jarvis.assistant.memory.VoiceLanguage,
        phoneControlEnabled: Boolean
    ) {
        val cleanModel = modelId.trim().removePrefix("models/")
        val systemPrompt = AssistantInstructions.buildSystemInstruction(
            assistantName = assistantName,
            language = language,
            phoneControlEnabled = phoneControlEnabled
        )

        val setupJson = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", "models/$cleanModel")
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply { put("AUDIO") })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", voiceName)
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemPrompt)
                        })
                    })
                })
                put("tools", ActionSchema.buildGeminiToolDeclaration())
                put("inputAudioTranscription", JSONObject())
                put("outputAudioTranscription", JSONObject())
            })
        }

        ws.send(setupJson.toString())
    }

    private fun sendRealtimeAudioChunk(pcmBytes: ByteArray) {
        if (userExplicitlyStopped.get()) return
        val ws = webSocket ?: return
        val state = _sessionState.value
        if (state == VoiceSessionState.CONNECTING || state == VoiceSessionState.RECONNECTING || state == VoiceSessionState.IDLE) {
            return
        }

        val b64 = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
        val audioPayload = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("audio", JSONObject().apply {
                    put("data", b64)
                    put("mimeType", "audio/pcm;rate=16000")
                })
            })
        }
        if (ws.send(audioPayload.toString())) {
            _diagnostics.value = _diagnostics.value.copy(
                audioChunksSent = _diagnostics.value.audioChunksSent + 1
            )
        }
    }

    private fun handleServerJsonPayload(rawJson: String) {
        if (userExplicitlyStopped.get()) return
        scope.launch {
            try {
                val root = JSONObject(rawJson)
                val prefs = assistantPreferences.identityFlow.first()
                val currentName = prefs.assistantName.ifBlank { activeSessionAssistantName.ifBlank { "Assistant" } }

                if (root.has("setupComplete")) {
                    _diagnostics.value = _diagnostics.value.copy(
                        isConnected = true,
                        reconnectAttempt = 0,
                        lastErrorMessage = null
                    )
                    _sessionState.value = VoiceSessionState.LISTENING
                    VoiceForegroundService.startOrUpdate(
                        context = appContext,
                        assistantName = currentName,
                        statusText = "Connected to Gemini Live (${prefs.activeModelId})"
                    )
                    startDurationTimer()
                    audioInputManager.startCapture()
                    return@launch
                }

                if (root.has("serverContent")) {
                    val serverContent = root.getJSONObject("serverContent")

                    if (serverContent.optBoolean("interrupted", false)) {
                        audioOutputManager.stopPlaybackAndClearQueue()
                        _sessionState.value = VoiceSessionState.INTERRUPTED
                        delay(120)
                        if (!userExplicitlyStopped.get()) {
                            _sessionState.value = VoiceSessionState.LISTENING
                        }
                    }

                    if (serverContent.has("inputTranscription")) {
                        val inTrans = serverContent.optJSONObject("inputTranscription")
                        val userText = inTrans?.optString("text", "").orEmpty()
                        if (userText.isNotBlank()) {
                            conversationRepository.appendOrUpdateMessage(
                                role = TranscriptRole.USER,
                                speakerLabel = "You",
                                text = userText,
                                isPartial = true,
                                persistToDisk = prefs.saveTranscriptHistory
                            )
                            onConversationTurnTextObserved(userText, true)
                        }
                    }

                    if (serverContent.has("outputTranscription")) {
                        val outTrans = serverContent.optJSONObject("outputTranscription")
                        val assistantText = outTrans?.optString("text", "").orEmpty()
                        if (assistantText.isNotBlank()) {
                            conversationRepository.appendOrUpdateMessage(
                                role = TranscriptRole.ASSISTANT,
                                speakerLabel = currentName,
                                text = assistantText,
                                isPartial = true,
                                persistToDisk = prefs.saveTranscriptHistory
                            )
                            onConversationTurnTextObserved(assistantText, false)
                        }
                    }

                    val modelTurn = serverContent.optJSONObject("modelTurn")
                    val parts = modelTurn?.optJSONArray("parts")
                    if (parts != null) {
                        modelTurnCompleted.set(false)
                        for (i in 0 until parts.length()) {
                            val part = parts.optJSONObject(i) ?: continue
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                val mimeType = inlineData.optString("mimeType", "")
                                val b64Data = inlineData.optString("data", "")
                                if (b64Data.isNotEmpty() && mimeType.startsWith("audio/pcm")) {
                                    val pcmBytes = Base64.decode(b64Data, Base64.DEFAULT)
                                    audioOutputManager.enqueuePcmChunk(pcmBytes)
                                    _diagnostics.value = _diagnostics.value.copy(
                                        audioChunksReceived = _diagnostics.value.audioChunksReceived + 1
                                    )
                                }
                            }
                            val textPart = part.optString("text", "")
                            if (textPart.isNotBlank() && !serverContent.has("outputTranscription")) {
                                conversationRepository.appendOrUpdateMessage(
                                    role = TranscriptRole.ASSISTANT,
                                    speakerLabel = currentName,
                                    text = textPart,
                                    isPartial = true,
                                    persistToDisk = prefs.saveTranscriptHistory
                                )
                                onConversationTurnTextObserved(textPart, false)
                            }
                        }
                    }

                    if (serverContent.optBoolean("turnComplete", false)) {
                        modelTurnCompleted.set(true)
                        conversationRepository.finalizePartialMessages(prefs.saveTranscriptHistory)
                        if (!audioOutputManager.isPlaying.value && !userExplicitlyStopped.get()) {
                            _sessionState.value = VoiceSessionState.LISTENING
                        }
                    }
                }

                if (root.has("toolCall")) {
                    val toolCallObj = root.getJSONObject("toolCall")
                    val functionCalls = toolCallObj.optJSONArray("functionCalls")
                    if (functionCalls != null && functionCalls.length() > 0) {
                        handleIncomingToolCalls(functionCalls, prefs.saveTranscriptHistory)
                    }
                }
            } catch (_: Exception) {
                // Prevent malformed frame from crashing the session
            }
        }
    }

    private suspend fun handleIncomingToolCalls(
        functionCalls: JSONArray,
        persistHistory: Boolean
    ) {
        val functionResponses = JSONArray()

        for (i in 0 until functionCalls.length()) {
            val fc = functionCalls.optJSONObject(i) ?: continue
            val callId = fc.optString("id", "")
            val fnName = fc.optString("name", "")
            val argsObj = fc.optJSONObject("args")

            // Branch 1: 3D Avatar Facial Expression & Gesture Tool (strictly separated from OS automation)
            if (fnName == ActionSchema.AVATAR_EXPRESSION_TOOL_NAME) {
                val rawExpr = argsObj?.optString("expression", "IDLE")
                val rawGesture = argsObj?.optString("gesture", "")
                val validatedExpr = AvatarExpression.fromValidatedName(rawExpr)
                val validatedGesture = AvatarGesture.fromValidatedName(rawGesture)

                onAvatarExpressionRequested(validatedExpr, validatedGesture)

                val responseItem = JSONObject().apply {
                    put("id", callId)
                    put("name", fnName)
                    put("response", JSONObject().apply {
                        put("result", JSONObject().apply {
                            put("status", "APPLIED")
                            put("activeExpression", validatedExpr.name)
                            put("activeGesture", (validatedGesture ?: validatedExpr.defaultGesture).name)
                        })
                    })
                }
                functionResponses.put(responseItem)
                continue
            }

            // Branch 2: OS Phone Automation Tool (Part 1)
            _sessionState.value = VoiceSessionState.EXECUTING_ACTION
            val parsedActionResult = ActionParser.parseToolCall(fnName, argsObj, callId)
            val executionResult: ActionExecutionResult = if (parsedActionResult.isSuccess) {
                val validatedAction = parsedActionResult.getOrThrow()
                phoneActionRouter.executeAction(validatedAction, isConfirmedByUser = false)
            } else {
                val errReason = parsedActionResult.exceptionOrNull()?.message ?: "Invalid action parameters."
                ActionExecutionResult(
                    status = com.jarvis.assistant.automation.ActionExecutionStatus.ACTION_UNSUPPORTED,
                    detailMessage = errReason
                )
            }

            _diagnostics.value = _diagnostics.value.copy(
                actionsExecutedCount = _diagnostics.value.actionsExecutedCount + 1
            )

            conversationRepository.appendOrUpdateMessage(
                role = TranscriptRole.SYSTEM_ACTION,
                speakerLabel = "Phone Control",
                text = "${executionResult.status.displayLabel} ${executionResult.detailMessage}",
                isPartial = false,
                persistToDisk = persistHistory
            )

            val responseItem = JSONObject().apply {
                put("id", callId)
                put("name", fnName)
                put("response", JSONObject().apply {
                    put("result", JSONObject().apply {
                        put("status", executionResult.status.name)
                        put("statusLabel", executionResult.status.displayLabel)
                        put("detailMessage", executionResult.detailMessage)
                    })
                })
            }
            functionResponses.put(responseItem)
        }

        val toolResponsePayload = JSONObject().apply {
            put("toolResponse", JSONObject().apply {
                put("functionResponses", functionResponses)
            })
        }
        webSocket?.send(toolResponsePayload.toString())

        if (!userExplicitlyStopped.get() && !audioOutputManager.isPlaying.value) {
            _sessionState.value = VoiceSessionState.PROCESSING
        }
    }

    private fun handleUserBargeIn() {
        if (audioOutputManager.isPlaying.value) {
            audioOutputManager.stopPlaybackAndClearQueue()
            _sessionState.value = VoiceSessionState.INTERRUPTED
            scope.launch {
                delay(120)
                if (!userExplicitlyStopped.get()) {
                    _sessionState.value = VoiceSessionState.LISTENING
                }
            }
        }
    }

    private fun startDurationTimer() {
        durationTimerJob?.cancel()
        durationTimerJob = scope.launch {
            while (isActive && !userExplicitlyStopped.get()) {
                delay(1000L)
                if (_diagnostics.value.isConnected) {
                    _diagnostics.value = _diagnostics.value.copy(
                        callDurationSeconds = _diagnostics.value.callDurationSeconds + 1
                    )
                }
            }
        }
    }

    private fun updateErrorState(errorMessage: String, recoverable: Boolean) {
        audioInputManager.stopCapture()
        audioOutputManager.stopPlaybackAndClearQueue()

        try {
            webSocket?.close(1000, "Closing on error")
        } catch (_: Exception) {}
        webSocket = null

        val currentAttempt = _diagnostics.value.reconnectAttempt
        val maxAttempts = _diagnostics.value.maxReconnectAttempts

        if (recoverable && !userExplicitlyStopped.get() && currentAttempt < maxAttempts) {
            val nextAttempt = currentAttempt + 1
            _sessionState.value = VoiceSessionState.RECONNECTING
            _diagnostics.value = _diagnostics.value.copy(
                isConnected = false,
                reconnectAttempt = nextAttempt,
                lastErrorMessage = "$errorMessage — Reconnecting ($nextAttempt/$maxAttempts)..."
            )

            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                val backoffMs = 1500L * (1 shl (nextAttempt - 1))
                delay(backoffMs)
                if (!userExplicitlyStopped.get()) {
                    val cred = settingsRepository.getApiCredentialOrNull()
                    if (!cred.isNullOrBlank() && permissionManager.isNetworkConnected()) {
                        connectWebSocketInternal(cred, isReconnect = true)
                    } else {
                        endSession(reason = errorMessage)
                    }
                }
            }
        } else {
            endSession(reason = errorMessage)
        }
    }
}
