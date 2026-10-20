package com.jarvis.assistant.avatar

import com.jarvis.assistant.voice.VoiceSessionState

/** Bridges Gemini Live state and audio levels into avatar expressions and motion. */
class AvatarVoiceSynchronizer(
    private val expressionController: AvatarExpressionController,
    private val animationController: AvatarAnimationController
) {
    private var contextualHoldUntilMs: Long = 0L

    fun triggerContextualExpression(
        expression: AvatarExpression,
        gesture: AvatarGesture? = null,
        holdDurationMs: Long = 4500L
    ) {
        contextualHoldUntilMs = System.currentTimeMillis() + holdDurationMs
        expressionController.setExpression(expression)
        val durationSec = (holdDurationMs / 1000f).coerceIn(1.2f, 8.0f)
        animationController.triggerGesture(gesture ?: expression.defaultGesture, durationSec)
    }

    fun onVoiceFrameUpdate(
        sessionState: VoiceSessionState,
        inputAmplitude: Float,
        outputAmplitude: Float,
        isAssistantSpeaking: Boolean
    ) {
        val now = System.currentTimeMillis()
        val hasContextualHold = now < contextualHoldUntilMs

        animationController.liveVoiceMouthOpenTarget =
            if (isAssistantSpeaking) outputAmplitude.coerceIn(0.0f, 1.0f) else 0.0f

        when {
            isAssistantSpeaking -> {
                if (!hasContextualHold || expressionController.currentExpression.value == AvatarExpression.IDLE) {
                    expressionController.setExpression(AvatarExpression.SPEAKING)
                }
                if (animationController.activeGesture.value != AvatarGesture.SPEAKING_GESTURE) {
                    animationController.triggerGesture(AvatarGesture.SPEAKING_GESTURE)
                }
            }
            sessionState == VoiceSessionState.LISTENING && inputAmplitude > 0.025f -> {
                if (!hasContextualHold) {
                    expressionController.setExpression(AvatarExpression.LISTENING)
                }
                if (animationController.activeGesture.value != AvatarGesture.LISTENING_POSE) {
                    animationController.triggerGesture(AvatarGesture.LISTENING_POSE)
                }
            }
            sessionState == VoiceSessionState.PROCESSING || sessionState == VoiceSessionState.EXECUTING_ACTION -> {
                expressionController.setExpression(AvatarExpression.THINKING)
                if (animationController.activeGesture.value != AvatarGesture.THINKING_POSE) {
                    animationController.triggerGesture(AvatarGesture.THINKING_POSE)
                }
            }
            sessionState == VoiceSessionState.LISTENING -> {
                if (!hasContextualHold) {
                    expressionController.setExpression(AvatarExpression.LISTENING)
                }
            }
            !sessionState.isActiveSession -> {
                if (!hasContextualHold) {
                    expressionController.setExpression(AvatarExpression.IDLE)
                    if (animationController.activeGesture.value != AvatarGesture.NATURAL_IDLE) {
                        animationController.triggerGesture(AvatarGesture.NATURAL_IDLE)
                    }
                }
            }
        }
    }

    fun onStopSpeakingTriggered(sessionStillActive: Boolean) {
        animationController.liveVoiceMouthOpenTarget = 0.0f
        contextualHoldUntilMs = 0L
        val expression = if (sessionStillActive) AvatarExpression.LISTENING else AvatarExpression.IDLE
        expressionController.setExpression(expression)
        animationController.triggerGesture(expression.defaultGesture)
    }
}