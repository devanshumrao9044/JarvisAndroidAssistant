package com.jarvis.assistant.voice

/**
 * Complete state machine required by Section 3 of the specification:
 * Listen -> Understand -> Perform authorized action if needed -> Speak -> Listen again.
 */
enum class VoiceSessionState(val statusTitle: String, val isActiveSession: Boolean) {
    IDLE("Idle — Ready", false),
    CONNECTING("Connecting to Gemini Live...", true),
    LISTENING("Listening...", true),
    PROCESSING("Thinking...", true),
    EXECUTING_ACTION("Executing Phone Action...", true),
    SPEAKING("Speaking...", true),
    INTERRUPTED("Interrupted — Listening...", true),
    RECONNECTING("Reconnecting...", true),
    ERROR("Connection Error", false)
}

data class LiveSessionDiagnostics(
    val activeModelId: String = "gemini-3.1-flash-live-preview",
    val isConnected: Boolean = false,
    val callDurationSeconds: Long = 0L,
    val audioChunksSent: Long = 0L,
    val audioChunksReceived: Long = 0L,
    val actionsExecutedCount: Int = 0,
    val reconnectAttempt: Int = 0,
    val maxReconnectAttempts: Int = 3,
    val lastErrorMessage: String? = null
)

/**
 * Clean extension point for future on-device wake-word engines (e.g., Porcupine / OpenWakeWord).
 * As required by Section 2 ("Important identity limitations"), a saved display name is not
 * falsely claimed to be an arbitrary wake word without a trained acoustic model.
 */
interface WakeWordIntegrationPoint {
    val isHardwareWakeWordSupported: Boolean
    fun startWakeWordDetection(targetName: String, onWakeWordDetected: () -> Unit)
    fun stopWakeWordDetection()
}

class ManualVoiceActivationEngine : WakeWordIntegrationPoint {
    override val isHardwareWakeWordSupported: Boolean = false
    override fun startWakeWordDetection(targetName: String, onWakeWordDetected: () -> Unit) {
        // Explicit manual activation is used in Part 1; future acoustic wake-word engines plug in here.
    }
    override fun stopWakeWordDetection() {}
}
