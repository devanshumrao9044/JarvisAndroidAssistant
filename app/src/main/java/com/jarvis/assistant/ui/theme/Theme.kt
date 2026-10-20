package com.jarvis.assistant.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.voice.VoiceSessionState
import kotlin.math.sin

val JarvisDeepNavy = Color(0xFF050A14)
val JarvisCardSurface = Color(0xFF0D1627)
val JarvisElevatedSurface = Color(0xFF132038)
val JarvisCyan = Color(0xFF00E5FF)
val JarvisElectricBlue = Color(0xFF2979FF)
val JarvisEmerald = Color(0xFF00E676)
val JarvisAmber = Color(0xFFFFB300)
val JarvisCrimson = Color(0xFFFF5252)
val JarvisTextPrimary = Color(0xFFE6F4FF)
val JarvisTextSecondary = Color(0xFF8AB4F8)

private val JarvisDarkColorScheme = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = Color(0xFF001F26),
    secondary = JarvisElectricBlue,
    onSecondary = Color.White,
    tertiary = JarvisEmerald,
    background = JarvisDeepNavy,
    onBackground = JarvisTextPrimary,
    surface = JarvisCardSurface,
    onSurface = JarvisTextPrimary,
    surfaceVariant = JarvisElevatedSurface,
    onSurfaceVariant = JarvisTextSecondary,
    error = JarvisCrimson,
    onError = Color.White
)

@Composable
fun JarvisAssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisDarkColorScheme,
        content = content
    )
}

/**
 * Futuristic HUD circular animated voice orb that responds to the real VoiceSessionState
 * and real-time microphone/speaker audio amplitude.
 */
@Composable
fun JarvisVoiceOrb(
    sessionState: VoiceSessionState,
    amplitude: Float,
    modifier: Modifier = Modifier,
    orbSize: Dp = 200.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_orb_transition")
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_rotation"
    )
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_rotation"
    )
    val breathPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_pulse"
    )

    val primaryAccent = when (sessionState) {
        VoiceSessionState.IDLE -> JarvisTextSecondary
        VoiceSessionState.CONNECTING, VoiceSessionState.RECONNECTING -> JarvisAmber
        VoiceSessionState.LISTENING -> JarvisCyan
        VoiceSessionState.PROCESSING, VoiceSessionState.EXECUTING_ACTION -> JarvisElectricBlue
        VoiceSessionState.SPEAKING -> JarvisEmerald
        VoiceSessionState.INTERRUPTED -> JarvisAmber
        VoiceSessionState.ERROR -> JarvisCrimson
    }

    val dynamicScale = (1f + amplitude.coerceIn(0f, 1f) * 0.28f) *
        if (sessionState.isActiveSession) breathPulse else 1f

    Box(
        modifier = modifier.size(orbSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(orbSize)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f

            // Ambient radial glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryAccent.copy(alpha = 0.32f),
                        primaryAccent.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius * dynamicScale.coerceAtMost(1.25f)
                ),
                radius = maxRadius,
                center = center
            )

            // Outer segmented HUD ring
            rotate(degrees = outerRotation, pivot = center) {
                for (angle in 0 until 360 step 45) {
                    drawArc(
                        color = primaryAccent.copy(alpha = 0.65f),
                        startAngle = angle.toFloat(),
                        sweepAngle = 26f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Counter-rotating inner tactical ring
            val midRadius = maxRadius * 0.76f
            rotate(degrees = innerRotation, pivot = center) {
                for (angle in 0 until 360 step 60) {
                    drawArc(
                        color = JarvisCyan.copy(alpha = 0.85f),
                        startAngle = angle.toFloat(),
                        sweepAngle = 34f,
                        useCenter = false,
                        topLeft = Offset(center.x - midRadius, center.y - midRadius),
                        size = androidx.compose.ui.geometry.Size(midRadius * 2, midRadius * 2),
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Core pulsing energy sphere
            val coreRadius = (maxRadius * 0.42f * dynamicScale).coerceAtMost(maxRadius * 0.68f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.90f),
                        primaryAccent,
                        primaryAccent.copy(alpha = 0.25f)
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )
        }
    }
}

/**
 * Animated multi-bar sound-wave equalizer reacting to live voice activity.
 */
@Composable
fun JarvisSoundWaveBar(
    isActive: Boolean,
    amplitude: Float,
    accentColor: Color = JarvisCyan,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "wave_phase")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase_float"
    )

    Row(
        modifier = modifier.height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barCount = 15
        for (i in 0 until barCount) {
            val waveFactor = if (isActive) {
                val baseWave = (sin(phase + i * 0.55f) + 1f) / 2f
                val energy = maxOf(0.18f, amplitude)
                (0.20f + baseWave * energy * 0.80f).coerceIn(0.15f, 1f)
            } else {
                0.15f
            }
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((34f * waveFactor).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isActive) accentColor else JarvisTextSecondary.copy(alpha = 0.35f)
                    )
            )
        }
    }
}
