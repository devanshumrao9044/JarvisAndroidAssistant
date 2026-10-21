package com.jarvis.assistant.avatar
import com.google.android.filament.gltfio.Animator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
class AvatarAnimationController {
    private val _activeGesture = MutableStateFlow(AvatarGesture.NATURAL_IDLE)
    val activeGesture: StateFlow<AvatarGesture> = _activeGesture.asStateFlow()
    private var elapsedTimeSec = 0f
    private var gestureStartTimeSec = 0f
    private var gestureDurationSec = 2.8f
    private var nextBlinkTimeSec = 2.4f
    private var blinkDurationSec = 0.16f
    @Volatile
    var liveVoiceMouthOpenTarget: Float = 0f
    private var smoothedVoiceMouth = 0f
    fun triggerGesture(gesture: AvatarGesture, durationSeconds: Float = 2.8f) {
        _activeGesture.value = gesture
        gestureStartTimeSec = elapsedTimeSec
        gestureDurationSec = durationSeconds
    }
    fun updateAnimationFrame(
        deltaSeconds: Float,
        config: AvatarConfiguration,
        expressionController: AvatarExpressionController,
        filamentAnimator: Animator?
    ): AvatarEvaluatedPose {
        val clampedDelta = deltaSeconds.coerceIn(0.001f, 0.1f)
        val speed = config.animationSpeedMultiplier.coerceIn(0.4f, 2.0f)
        elapsedTimeSec += clampedDelta * speed
        val currentGesture = _activeGesture.value
        if (currentGesture in listOf(
                AvatarGesture.GREETING_WAVE,
                AvatarGesture.SMALL_NOD,
                AvatarGesture.HAPPY_REACTION,
                AvatarGesture.LAUGHING_REACTION
            ) && (elapsedTimeSec - gestureStartTimeSec) > gestureDurationSec
        ) {
            _activeGesture.value = AvatarGesture.NATURAL_IDLE
        }
        val exprPreset = expressionController.stepInterpolation(clampedDelta, speed)
        val expression = expressionController.currentExpression.value
        var blinkWeight = exprPreset.eyeRelaxedLid
        if (config.autoBlinkEnabled) {
            if (elapsedTimeSec >= nextBlinkTimeSec) {
                val blinkProgress = (elapsedTimeSec - nextBlinkTimeSec) / blinkDurationSec
                if (blinkProgress <= 1.0f) {
                    val dynamicBlink = sin(blinkProgress * Math.PI.toFloat())
                    blinkWeight = maxOf(blinkWeight, dynamicBlink)
                } else {
                    nextBlinkTimeSec = elapsedTimeSec + Random.nextDouble(2.6, 5.2).toFloat()
                }
            }
        }
        val mouthAlpha = (clampedDelta * 18f).coerceIn(0.1f, 1.0f)
        smoothedVoiceMouth += (liveVoiceMouthOpenTarget - smoothedVoiceMouth) * mouthAlpha
        val laughingOscillation = if (expression == AvatarExpression.LAUGHING) {
            (sin(elapsedTimeSec * 14f) * 0.25f + 0.25f)
        } else 0f
        val finalMouthOpen = maxOf(
            exprPreset.baseMouthOpen + laughingOscillation,
            smoothedVoiceMouth * config.voiceLipSyncGain
        ).coerceIn(0f, 1f)
        val idleMotionScale = if (expression == AvatarExpression.SLEEPY) 0.45f else 1.0f
        val breathingOffset = sin(elapsedTimeSec * 1.8f * idleMotionScale) * 0.018f
        var headPitch = exprPreset.headPitchBiasDeg
        var headYaw = 0f
        var headRoll = exprPreset.headRollBiasDeg
        var rightArmWaveDeg = 0f
        if (config.idleHeadMotionEnabled) {
            headPitch += sin(elapsedTimeSec * 0.9f) * 2.2f * idleMotionScale
            headYaw += cos(elapsedTimeSec * 0.65f) * 4.5f * idleMotionScale
            headRoll += sin(elapsedTimeSec * 0.55f) * 1.8f * idleMotionScale
        }
        val gestureAge = elapsedTimeSec - gestureStartTimeSec
        when (_activeGesture.value) {
            AvatarGesture.GREETING_WAVE -> {
                rightArmWaveDeg = 45f + sin(gestureAge * 9.0f) * 25f
                headRoll += 4f
            }
            AvatarGesture.SMALL_NOD -> {
                headPitch += sin(gestureAge * 7.5f) * 8.5f
            }
            AvatarGesture.HEAD_TILT -> {
                headRoll += 10.5f
            }
            AvatarGesture.HAPPY_REACTION -> {
                headPitch += sin(gestureAge * 6.0f) * 4.5f
            }
            AvatarGesture.LAUGHING_REACTION -> {
                headPitch += sin(gestureAge * 12.0f) * 5.5f
            }
            AvatarGesture.THINKING_POSE -> {
                headYaw -= 8.0f
                headRoll += 7.0f
            }
            AvatarGesture.LISTENING_POSE -> {
                headPitch += 3.0f
                headRoll += 3.5f
            }
            AvatarGesture.SPEAKING_GESTURE -> {
                headPitch += sin(elapsedTimeSec * 4.2f) * 3.0f * (0.3f + finalMouthOpen)
                headYaw += cos(elapsedTimeSec * 2.8f) * 3.5f
            }
            AvatarGesture.NATURAL_IDLE -> {}
        }
        if (filamentAnimator != null) {
            applySkeletalClipIfAvailable(filamentAnimator, _activeGesture.value, elapsedTimeSec)
        }
        return AvatarEvaluatedPose(
            expression = expression,
            activeGesture = _activeGesture.value,
            mouthOpenWeight = finalMouthOpen,
            smileWeight = exprPreset.smile,
            frownWeight = exprPreset.frown,
            eyeBlinkLeft = blinkWeight.coerceIn(0f, 1f),
            eyeBlinkRight = blinkWeight.coerceIn(0f, 1f),
            eyeWideWeight = exprPreset.eyeWide,
            browRaiseLeft = exprPreset.browLeft,
            browRaiseRight = exprPreset.browRight,
            headPitchDeg = headPitch.coerceIn(-22f, 22f),
            headYawDeg = headYaw.coerceIn(-28f, 28f),
            headRollDeg = headRoll.coerceIn(-20f, 20f),
            torsoBreathingOffset = breathingOffset,
            rightArmWaveAngleDeg = rightArmWaveDeg,
            energyGlowIntensity = exprPreset.energyGlow
        )
    }
    private fun applySkeletalClipIfAvailable(
        animator: Animator,
        gesture: AvatarGesture,
        timeSec: Float
    ) {
        try {
            val count = animator.animationCount
            if (count <= 0) return
            var matchedIndex = -1
            for (i in 0 until count) {
                val clipName = animator.getAnimationName(i).lowercase()
                if (gesture.clipKeywords.any { kw -> clipName.contains(kw) }) {
                    matchedIndex = i
                    break
                }
            }
            val targetClip = if (matchedIndex >= 0) matchedIndex else 0
            val duration = animator.getAnimationDuration(targetClip).coerceAtLeast(0.01f)
            val loopedTime = timeSec % duration
            animator.applyAnimation(targetClip, loopedTime)
            animator.updateBoneMatrices()
        } catch (_: Exception) {
        }
    }
}
