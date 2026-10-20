package com.jarvis.assistant.avatar

import com.google.android.filament.Engine
import com.google.android.filament.gltfio.FilamentAsset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Interpolated face controls consumed by the renderer on each frame. */
data class AvatarExpressionPreset(
    val baseMouthOpen: Float = 0.0f,
    val smile: Float = 0.0f,
    val frown: Float = 0.0f,
    val eyeRelaxedLid: Float = 0.0f,
    val eyeWide: Float = 0.0f,
    val browLeft: Float = 0.0f,
    val browRight: Float = 0.0f,
    val headPitchBiasDeg: Float = 0.0f,
    val headRollBiasDeg: Float = 0.0f,
    val energyGlow: Float = 0.5f
)

/** Validates expressions, interpolates transitions, and applies canonical GLB morph weights. */
class AvatarExpressionController {
    private val _currentExpression = MutableStateFlow(AvatarExpression.IDLE)
    val currentExpression: StateFlow<AvatarExpression> = _currentExpression.asStateFlow()

    private var displayed = presetFor(AvatarExpression.IDLE)
    private var transitionStart = displayed
    private var transitionTarget = displayed
    private var transitionProgress = 1.0f

    fun setExpression(expression: AvatarExpression) {
        if (_currentExpression.value == expression) return
        transitionStart = displayed
        transitionTarget = presetFor(expression)
        transitionProgress = 0.0f
        _currentExpression.value = expression
    }

    fun inferContextualExpression(text: String, isUserTurn: Boolean): Pair<AvatarExpression, AvatarGesture>? {
        val value = text.lowercase()
        val selected = when {
            listOf("sorry", "sad", "upset", "hurt", "दुख", "माफ़", "माफ").any { value.contains(it) } -> AvatarExpression.SAD
            listOf("congrat", "awesome", "amazing", "great job", "बहुत अच्छा", "शानदार").any { value.contains(it) } -> AvatarExpression.EXCITED
            listOf("hello", "hi ", "namaste", "नमस्ते", "thank", "धन्यवाद").any { value.contains(it) } -> AvatarExpression.FRIENDLY
            listOf("why", "how", "what", "explain", "क्यों", "कैसे", "क्या", "समझाओ").any { value.contains(it) } -> AvatarExpression.THINKING
            isUserTurn -> AvatarExpression.LISTENING
            else -> null
        } ?: return null
        return selected to selected.defaultGesture
    }

    fun stepInterpolation(deltaSeconds: Float, speedMultiplier: Float): AvatarExpressionPreset {
        val step = deltaSeconds.coerceAtLeast(0.0f) * speedMultiplier.coerceIn(0.25f, 3.0f) * 3.5f
        transitionProgress = (transitionProgress + step).coerceIn(0.0f, 1.0f)
        val t = transitionProgress
        displayed = AvatarExpressionPreset(
            baseMouthOpen = mix(transitionStart.baseMouthOpen, transitionTarget.baseMouthOpen, t),
            smile = mix(transitionStart.smile, transitionTarget.smile, t),
            frown = mix(transitionStart.frown, transitionTarget.frown, t),
            eyeRelaxedLid = mix(transitionStart.eyeRelaxedLid, transitionTarget.eyeRelaxedLid, t),
            eyeWide = mix(transitionStart.eyeWide, transitionTarget.eyeWide, t),
            browLeft = mix(transitionStart.browLeft, transitionTarget.browLeft, t),
            browRight = mix(transitionStart.browRight, transitionTarget.browRight, t),
            headPitchBiasDeg = mix(transitionStart.headPitchBiasDeg, transitionTarget.headPitchBiasDeg, t),
            headRollBiasDeg = mix(transitionStart.headRollBiasDeg, transitionTarget.headRollBiasDeg, t),
            energyGlow = mix(transitionStart.energyGlow, transitionTarget.energyGlow, t)
        )
        return displayed
    }

    /**
     * Compatible GLBs use this canonical order: blink L, blink R, mouth, smile, frown,
     * eye-wide, brow L, brow R. Unmapped extra targets stay neutral.
     */
    fun applyMorphTargetsToFilamentAsset(engine: Engine, asset: FilamentAsset, pose: AvatarEvaluatedPose) {
        val manager = engine.renderableManager
        for (entity in asset.entities) {
            if (!manager.hasComponent(entity)) continue
            val instance = manager.getInstance(entity)
            val count = manager.getMorphTargetCount(instance)
            if (count <= 0) continue
            val canonical = floatArrayOf(
                pose.eyeBlinkLeft,
                pose.eyeBlinkRight,
                pose.mouthOpenWeight,
                pose.smileWeight,
                pose.frownWeight,
                pose.eyeWideWeight,
                pose.browRaiseLeft,
                pose.browRaiseRight
            )
            val weights = FloatArray(count) { index -> canonical.getOrElse(index) { 0.0f } }
            runCatching { manager.setMorphWeights(instance, weights, 0) }
        }
    }

    private fun presetFor(expression: AvatarExpression): AvatarExpressionPreset = when (expression) {
        AvatarExpression.HAPPY -> AvatarExpressionPreset(smile = 0.72f, energyGlow = 0.78f)
        AvatarExpression.LAUGHING -> AvatarExpressionPreset(baseMouthOpen = 0.5f, smile = 0.95f, eyeRelaxedLid = 0.12f, energyGlow = 0.95f)
        AvatarExpression.FRIENDLY -> AvatarExpressionPreset(smile = 0.42f, energyGlow = 0.68f)
        AvatarExpression.THINKING -> AvatarExpressionPreset(browLeft = 0.18f, browRight = 0.1f, headRollBiasDeg = 4.0f, energyGlow = 0.55f)
        AvatarExpression.LISTENING -> AvatarExpressionPreset(eyeWide = 0.12f, energyGlow = 0.58f)
        AvatarExpression.SPEAKING -> AvatarExpressionPreset(baseMouthOpen = 0.08f, smile = 0.12f, energyGlow = 0.65f)
        AvatarExpression.SURPRISED -> AvatarExpressionPreset(eyeWide = 0.78f, browLeft = 0.65f, browRight = 0.65f, baseMouthOpen = 0.22f, energyGlow = 0.85f)
        AvatarExpression.SAD -> AvatarExpressionPreset(frown = 0.65f, browLeft = 0.32f, browRight = 0.32f, headPitchBiasDeg = 4.0f, energyGlow = 0.3f)
        AvatarExpression.CONFUSED -> AvatarExpressionPreset(browLeft = 0.25f, browRight = 0.05f, headRollBiasDeg = 6.0f, energyGlow = 0.52f)
        AvatarExpression.EXCITED -> AvatarExpressionPreset(smile = 0.85f, eyeWide = 0.28f, energyGlow = 1.0f)
        AvatarExpression.SERIOUS -> AvatarExpressionPreset(energyGlow = 0.45f)
        AvatarExpression.SLEEPY -> AvatarExpressionPreset(eyeRelaxedLid = 0.45f, energyGlow = 0.35f)
        AvatarExpression.IDLE -> AvatarExpressionPreset()
    }

    private fun mix(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount
}
