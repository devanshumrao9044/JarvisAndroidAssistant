package com.jarvis.assistant.avatar
enum class AvatarGesture(val displayLabel: String, val clipKeywords: List<String>) {
    GREETING_WAVE("Greeting Wave", listOf("wave", "greet", "hello")),
    SMALL_NOD("Small Nod", listOf("nod", "yes", "agree")),
    HEAD_TILT("Head Tilt", listOf("tilt", "question", "curious")),
    NATURAL_IDLE("Natural Idle Movement", listOf("idle", "stand", "breathe", "default")),
    HAPPY_REACTION("Happy Reaction", listOf("happy", "joy", "excited", "cheer")),
    LAUGHING_REACTION("Laughing Reaction", listOf("laugh", "chuckle", "giggle")),
    THINKING_POSE("Thinking Pose", listOf("think", "ponder", "wonder")),
    LISTENING_POSE("Listening Pose", listOf("listen", "attentive")),
    SPEAKING_GESTURE("Speaking Gestures", listOf("talk", "speak", "explain"));
    companion object {
        fun fromValidatedName(rawName: String?): AvatarGesture? {
            if (rawName.isNullOrBlank()) return null
            val normalized = rawName.trim().uppercase()
            return entries.firstOrNull { it.name == normalized }
        }
    }
}
enum class AvatarExpression(
    val displayTitle: String,
    val hindiLabel: String,
    val description: String,
    val defaultGesture: AvatarGesture
) {
    HAPPY(
        displayTitle = "Happy",
        hindiLabel = "खुश (Happy)",
        description = "Natural smile and bright eyes",
        defaultGesture = AvatarGesture.HAPPY_REACTION
    ),
    LAUGHING(
        displayTitle = "Laughing",
        hindiLabel = "हँसना (Laughing)",
        description = "Smiling, rhythmic mouth movement and subtle head movement",
        defaultGesture = AvatarGesture.LAUGHING_REACTION
    ),
    FRIENDLY(
        displayTitle = "Friendly",
        hindiLabel = "मित्रवत (Friendly)",
        description = "Warm, welcoming expression",
        defaultGesture = AvatarGesture.GREETING_WAVE
    ),
    THINKING(
        displayTitle = "Thinking",
        hindiLabel = "सोचते हुए (Thinking)",
        description = "Thoughtful face and gentle head tilt",
        defaultGesture = AvatarGesture.THINKING_POSE
    ),
    LISTENING(
        displayTitle = "Listening",
        hindiLabel = "सुनते हुए (Listening)",
        description = "Attentive eyes and subtle movement",
        defaultGesture = AvatarGesture.LISTENING_POSE
    ),
    SPEAKING(
        displayTitle = "Speaking",
        hindiLabel = "बोलते हुए (Speaking)",
        description = "Mouth animation synchronized with assistant voice",
        defaultGesture = AvatarGesture.SPEAKING_GESTURE
    ),
    SURPRISED(
        displayTitle = "Surprised",
        hindiLabel = "आश्चर्यचकित (Surprised)",
        description = "Raised eyebrows and widened eyes",
        defaultGesture = AvatarGesture.HEAD_TILT
    ),
    SAD(
        displayTitle = "Sad",
        hindiLabel = "उदास / चिंतित (Sad)",
        description = "Gentle, concerned expression",
        defaultGesture = AvatarGesture.SMALL_NOD
    ),
    CONFUSED(
        displayTitle = "Confused",
        hindiLabel = "उलझन में (Confused)",
        description = "Slight head tilt and questioning expression",
        defaultGesture = AvatarGesture.HEAD_TILT
    ),
    EXCITED(
        displayTitle = "Excited",
        hindiLabel = "उत्साहित (Excited)",
        description = "Energetic smile and lively movement",
        defaultGesture = AvatarGesture.HAPPY_REACTION
    ),
    SERIOUS(
        displayTitle = "Serious",
        hindiLabel = "गंभीर (Serious)",
        description = "Calm and focused expression",
        defaultGesture = AvatarGesture.NATURAL_IDLE
    ),
    SLEEPY(
        displayTitle = "Sleepy",
        hindiLabel = "नींद / आराम (Sleepy)",
        description = "Relaxed eyes and slow idle movement",
        defaultGesture = AvatarGesture.NATURAL_IDLE
    ),
    IDLE(
        displayTitle = "Idle",
        hindiLabel = "सामान्य (Idle)",
        description = "Calm, attentive neutral stance with natural blinking",
        defaultGesture = AvatarGesture.NATURAL_IDLE
    );
    companion object {
        fun fromValidatedName(rawName: String?): AvatarExpression {
            if (rawName.isNullOrBlank()) return IDLE
            val normalized = rawName.trim().uppercase()
            return entries.firstOrNull { it.name == normalized } ?: IDLE
        }
    }
}
sealed class AvatarModelLoadState {
    data object Initializing : AvatarModelLoadState()
    data class LoadingFromAssets(val assetPath: String) : AvatarModelLoadState()
    data class LoadedGlbModel(
        val assetPath: String,
        val animationClips: List<String>,
        val entityCount: Int,
        val hasFacialMorphTargets: Boolean,
        val detectedMorphTargetCount: Int
    ) : AvatarModelLoadState()
    data class AwaitingModelAsset(
        val expectedAssetPath: String,
        val detailMessage: String
    ) : AvatarModelLoadState()
    data class Error(
        val assetPath: String,
        val reason: String
    ) : AvatarModelLoadState()
}
data class AvatarEvaluatedPose(
    val expression: AvatarExpression = AvatarExpression.IDLE,
    val activeGesture: AvatarGesture = AvatarGesture.NATURAL_IDLE,
    val mouthOpenWeight: Float = 0f,
    val smileWeight: Float = 0f,
    val frownWeight: Float = 0f,
    val eyeBlinkLeft: Float = 0f,
    val eyeBlinkRight: Float = 0f,
    val eyeWideWeight: Float = 0f,
    val browRaiseLeft: Float = 0f,
    val browRaiseRight: Float = 0f,
    val headPitchDeg: Float = 0f,
    val headYawDeg: Float = 0f,
    val headRollDeg: Float = 0f,
    val torsoBreathingOffset: Float = 0f,
    val rightArmWaveAngleDeg: Float = 0f,
    val energyGlowIntensity: Float = 0.5f
)
