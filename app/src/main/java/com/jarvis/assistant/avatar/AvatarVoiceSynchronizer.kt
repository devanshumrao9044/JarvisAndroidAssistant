package com.jarvis.assistant.avatar

import com.jarvis.assistant.voice.VoiceSessionState

/** Bridges Gemini Live state and audio levels into avatar expressions and motion. */
class AvatarVoiceSynchronizer(
    private val expressionController: AvatarExpressionController,
    private val animationController: AvatarAnimationController
) {
    fun triggerContextualExpression(expression: AvatarExpression, gesture: AvatarGesture? = null) {
        expressionController.setExpression(expression)
        animationController.triggerGesture(gesture ?: expression.defaultGesture)
    }

    fun onVoiceFrameUpdate(
        sessionState: VoiceSessionState,
        inputAmplitude: Float,
        outputAmplitude: Float,
        isAssistantSpeaking: Boolean
    ) {
        animationController.liveVoiceMouthOpenTarget =
            if (isAssistantSpeaking) outputAmplitude.coerceIn(0.0f, 1.0f) else 0.0f

        when {
            isAssistantSpeaking -> {
                expressionController.setExpression(AvatarExpression.SPEAKING)
                if (animationController.activeGesture.value != AvatarGesture.SPEAKING_GESTURE) {
                    animationController.triggerGesture(AvatarGesture.SPEAKING_GESTURE)
                }
            }
            sessionState == VoiceSessionState.LISTENING && inputAmplitude > 0.025f -> {
                expressionController.setExpression(AvatarExpression.LISTENING)
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
                expressionController.setExpression(AvatarExpression.LISTENING)
            }
            !sessionState.isActiveSession -> {
                expressionController.setExpression(AvatarExpression.IDLE)
                if (animationController.activeGesture.value != AvatarGesture.NATURAL_IDLE) {
                    animationController.triggerGesture(AvatarGesture.NATURAL_IDLE)
                }
            }
        }
    }

    fun onStopSpeakingTriggered(sessionStillActive: Boolean) {
        animationController.liveVoiceMouthOpenTarget = 0.0f
        val expression = if (sessionStillActive) AvatarExpression.LISTENING else AvatarExpression.IDLE
        expressionController.setExpression(expression)
        animationController.triggerGesture(expression.defaultGesture)
    }
}
