package com.jarvis.assistant.avatar
enum class AvatarPerformanceTier(
    val displayName: String,
    val targetFps: Int,
    val enableDynamicResolution: Boolean,
    val enableBloomAndAo: Boolean
) {
    BATTERY_SAVER("Battery Saver", 24, true, false),
    BALANCED("Balanced", 30, true, true),
    HIGH_QUALITY("High Quality", 60, false, true);
    companion object {
        fun fromName(name: String?): AvatarPerformanceTier {
            val normalized = name?.trim().orEmpty()
            return entries.firstOrNull {
                it.name.equals(normalized, ignoreCase = true) ||
                    it.displayName.equals(normalized, ignoreCase = true)
            } ?: BALANCED
        }
    }
}
data class AvatarConfiguration(
    val primaryGlbAssetPath: String = DEFAULT_GLB_ASSET_PATH,
    val animationSpeedMultiplier: Float = 1.0f,
    val performanceTier: AvatarPerformanceTier = AvatarPerformanceTier.BALANCED,
    val autoBlinkEnabled: Boolean = true,
    val idleHeadMotionEnabled: Boolean = true,
    val voiceLipSyncGain: Float = 1.0f
) {
    companion object {
        const val DEFAULT_GLB_ASSET_PATH = "models/jarvis_avatar.glb"
    }
}
