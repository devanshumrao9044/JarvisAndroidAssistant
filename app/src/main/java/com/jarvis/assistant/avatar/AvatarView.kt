kotlin
package com.jarvis.assistant.avatar

import android.view.SurfaceView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jarvis.assistant.ui.theme.JarvisAmber
import com.jarvis.assistant.ui.theme.JarvisCardSurface
import com.jarvis.assistant.ui.theme.JarvisCyan
import com.jarvis.assistant.ui.theme.JarvisDeepNavy
import com.jarvis.assistant.ui.theme.JarvisElectricBlue
import com.jarvis.assistant.ui.theme.JarvisEmerald
import com.jarvis.assistant.ui.theme.JarvisTextPrimary
import com.jarvis.assistant.ui.theme.JarvisTextSecondary
import kotlin.math.cos
import kotlin.math.sin

/**
 * Reusable 3D Avatar Compose View (Sections 2, 5, 9).
 * - Displays the loaded glTF/GLB 3D character via Google Filament's SurfaceView when a GLB file is present.
 * - While awaiting the external .glb asset, renders a real-time 3D perspective-projected Holographic Character Rig
 *   driven by the exact same `AvatarEvaluatedPose` (idle breathing, natural blinking, head pitch/yaw/roll,
 *   13 facial expressions, 9 body gestures, and voice-synchronized jaw/mouth articulation).
 */
@Composable
fun AvatarView(
    avatarRenderer: AvatarRenderer,
    assistantName: String,
    modifier: Modifier = Modifier,
    viewportHeight: Dp = 320.dp
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val loadState by avatarRenderer.modelLoadState.collectAsState()
    val pose by avatarRenderer.evaluatedPose.collectAsState()
    val config by avatarRenderer.configuration.collectAsState()

    var userOrbitYawDeg by remember { mutableFloatStateOf(0f) }
    var userOrbitPitchDeg by remember { mutableFloatStateOf(0f) }

    DisposableEffect(lifecycleOwner) {
        avatarRenderer.startRenderLoop()
        onDispose {
            avatarRenderer.stopRenderLoop()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(viewportHeight),
        colors = CardDefaults.cardColors(containerColor = JarvisDeepNavy),
        border = BorderStroke(1.5.dp, JarvisCyan.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            userOrbitYawDeg = 0f
                            userOrbitPitchDeg = 0f
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        userOrbitYawDeg = (userOrbitYawDeg + dragAmount.x * 0.25f).coerceIn(-35f, 35f)
                        userOrbitPitchDeg = (userOrbitPitchDeg + dragAmount.y * 0.20f).coerceIn(-20f, 20f)
                    }
                }
        ) {
            // 1. Futuristic Blue & Cyan Holographic Environment Backdrop
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawFuturisticEnvironmentGrid(glowIntensity = pose.energyGlowIntensity)
            }

            // 2. 3D Character Viewport (Hardware GLB ModelViewer OR Real-Time 3D Perspective Holographic Rig)
            when (loadState) {
                is AvatarModelLoadState.LoadedGlbModel -> {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).also { sv ->
                                avatarRenderer.attachSurfaceView(sv, lifecycleOwner.lifecycle)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is AvatarModelLoadState.Initializing,
                is AvatarModelLoadState.LoadingFromAssets -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = JarvisCyan)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Loading 3D Avatar Engine...",
                                color = JarvisCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                is AvatarModelLoadState.AwaitingModelAsset,
                is AvatarModelLoadState.Error -> {
                    val meshSubdivisions = when (config.performanceTier) {
                        AvatarPerformanceTier.HIGH_QUALITY -> 24
                        AvatarPerformanceTier.BALANCED -> 18
                        AvatarPerformanceTier.BATTERY_SAVER -> 12
                    }
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        draw3DHolographicCharacterRig(
                            pose = pose,
                            userYawDeg = userOrbitYawDeg,
                            userPitchDeg = userOrbitPitchDeg,
                            subdivisions = meshSubdivisions
                        )
                    }
                }
            }

            // 3. Top-Left Assistant Identity & Active Expression Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(JarvisCardSurface.copy(alpha = 0.82f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(JarvisEmerald)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = assistantName.ifBlank { "AI Assistant" },
                            color = JarvisTextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        text = "Expression: ${pose.expression.displayTitle}",
                        color = JarvisCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Active Gesture & FPS Tier Badge
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(JarvisCardSurface.copy(alpha = 0.82f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = pose.activeGesture.displayLabel,
                        color = JarvisTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${config.performanceTier.targetFps} FPS Cap",
                        color = JarvisTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            // 4. Bottom Honest Model Asset Status Bar
            val bottomBannerText = when (val state = loadState) {
                is AvatarModelLoadState.LoadedGlbModel ->
                    "3D GLB Loaded: ${state.assetPath} (${state.animationClips.size} clips, ${state.detectedMorphTargetCount} morphs)"
                is AvatarModelLoadState.AwaitingModelAsset ->
                    "3D Holo-Rig Active • Drop character GLB in assets/${state.expectedAssetPath}"
                is AvatarModelLoadState.Error ->
                    "3D Holo-Rig Fallback • ${state.reason}"
                else -> "Initializing 3D pipeline..."
            }

            val bottomColor = if (loadState is AvatarModelLoadState.LoadedGlbModel) JarvisEmerald else JarvisAmber

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(JarvisCardSurface.copy(alpha = 0.88f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = bottomBannerText,
                    color = bottomColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

private fun DrawScope.drawFuturisticEnvironmentGrid(glowIntensity: Float) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.52f
    val maxR = size.minDimension * 0.58f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                JarvisCyan.copy(alpha = 0.16f + glowIntensity * 0.14f),
                JarvisElectricBlue.copy(alpha = 0.08f),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = maxR
        ),
        radius = maxR,
        center = Offset(cx, cy)
    )

    val baseCenterY = size.height * 0.86f
    for (ring in 1..3) {
        val rx = size.width * (0.16f + ring * 0.08f)
        val ry = rx * 0.22f
        drawOval(
            color = JarvisCyan.copy(alpha = 0.35f / ring),
            topLeft = Offset(cx - rx, baseCenterY - ry),
            size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

private fun DrawScope.draw3DHolographicCharacterRig(
    pose: AvatarEvaluatedPose,
    userYawDeg: Float,
    userPitchDeg: Float,
    subdivisions: Int
) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.47f + (pose.torsoBreathingOffset * size.height)
    val scale = size.minDimension * 0.33f

    val pitchRad = Math.toRadians((pose.headPitchDeg + userPitchDeg).toDouble()).toFloat()
    val yawRad = Math.toRadians((pose.headYawDeg + userYawDeg).toDouble()).toFloat()
    val rollRad = Math.toRadians(pose.headRollDeg.toDouble()).toFloat()

    val cosP = cos(pitchRad)
    val sinP = sin(pitchRad)
    val cosY = cos(yawRad)
    val sinY = sin(yawRad)
    val cosR = cos(rollRad)
    val sinR = sin(rollRad)

    fun project3D(x: Float, y: Float, z: Float, applyHeadRotation: Boolean = true): Offset {
        var px = x
        var py = y
        var pz = z

        if (applyHeadRotation) {
            val rx = px * cosR - py * sinR
            val ry = px * sinR + py * cosR
            px = rx
            py = ry

            val py2 = py * cosP - pz * sinP
            val pz2 = py * sinP + pz * cosP
            py = py2
            pz = pz2

            val px3 = px * cosY + pz * sinY
            val pz3 = -px * sinY + pz * cosY
            px = px3
            pz = pz3
        }

        val cameraDist = 3.4f
        val perspective = cameraDist / (cameraDist - pz).coerceAtLeast(1.2f)
        return Offset(
            x = cx + px * scale * perspective,
            y = cy - py * scale * perspective
        )
    }

    val primaryColor = when (pose.expression) {
        AvatarExpression.HAPPY, AvatarExpression.EXCITED, AvatarExpression.LAUGHING -> JarvisEmerald
        AvatarExpression.THINKING -> JarvisElectricBlue
        AvatarExpression.SURPRISED, AvatarExpression.CONFUSED -> JarvisAmber
        else -> JarvisCyan
    }

    val neckTopL = project3D(-0.24f, -0.72f, -0.05f, applyHeadRotation = false)
    val neckTopR = project3D(0.24f, -0.72f, -0.05f, applyHeadRotation = false)
    val shoulderL = project3D(-0.95f, -1.12f, -0.10f, applyHeadRotation = false)
    val shoulderR = project3D(0.95f, -1.12f, -0.10f, applyHeadRotation = false)
    val chestCenter = project3D(0f, -1.05f, 0.18f, applyHeadRotation = false)

    val torsoPath = Path().apply {
        moveTo(shoulderL.x, shoulderL.y)
        lineTo(neckTopL.x, neckTopL.y)
        lineTo(neckTopR.x, neckTopR.y)
        lineTo(shoulderR.x, shoulderR.y)
        lineTo(chestCenter.x, chestCenter.y)
        close()
    }
    drawPath(
        path = torsoPath,
        color = primaryColor.copy(alpha = 0.40f),
        style = Stroke(width = 2.dp.toPx())
    )

    if (pose.rightArmWaveAngleDeg > 5f) {
        val waveRad = Math.toRadians(pose.rightArmWaveAngleDeg.toDouble()).toFloat()
        val elbow = project3D(1.05f, -0.82f, 0.15f, applyHeadRotation = false)
        val handX = 1.15f + sin(waveRad) * 0.28f
        val handY = -0.25f + cos(waveRad) * 0.15f
        val hand = project3D(handX, handY, 0.30f, applyHeadRotation = false)
        drawLine(
            color = JarvisEmerald,
            start = shoulderR,
            end = elbow,
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = JarvisEmerald,
            start = elbow,
            end = hand,
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(color = JarvisEmerald, radius = 6.dp.toPx(), center = hand)
    }

    val latRings = listOf(-0.55f, -0.25f, 0.05f, 0.35f, 0.62f, 0.82f)
    for (latY in latRings) {
        val radiusAtY = kotlin.math.sqrt((1f - latY * latY * 0.85f).coerceAtLeast(0.1f)) * 0.72f
        val ringPath = Path()
        val steps = subdivisions.coerceAtLeast(12)
        for (i in 0..steps) {
            val theta = (i.toFloat() / steps) * (2f * Math.PI.toFloat())
            val vx = cos(theta) * radiusAtY
            val vz = sin(theta) * radiusAtY * 0.92f
            val jawDrop = if (latY < -0.45f && vz > 0.1f) pose.mouthOpenWeight * 0.16f else 0f
            val pt = project3D(vx, latY - jawDrop, vz)
            if (i == 0) ringPath.moveTo(pt.x, pt.y) else ringPath.lineTo(pt.x, pt.y)
        }
        drawPath(
            path = ringPath,
            color = primaryColor.copy(alpha = 0.26f),
            style = Stroke(width = 1.2.dp.toPx())
        )
    }

    val meridianPath = Path()
    for (i in 0..20) {
        val t = (i / 20f) * Math.PI.toFloat() - (Math.PI.toFloat() / 2f)
        val my = sin(t) * 0.86f
        val mz = cos(t) * 0.72f
        val jawDrop = if (my < -0.45f) pose.mouthOpenWeight * 0.16f else 0f
        val pt = project3D(0f, my - jawDrop, mz)
        if (i == 0) meridianPath.moveTo(pt.x, pt.y) else meridianPath.lineTo(pt.x, pt.y)
    }
    drawPath(
        path = meridianPath,
        color = primaryColor.copy(alpha = 0.35f),
        style = Stroke(width = 1.4.dp.toPx())
    )

    fun drawEyebrow(isLeft: Boolean, raiseWeight: Float) {
        val sign = if (isLeft) -1f else 1f
        val innerX = sign * 0.12f
        val midX = sign * 0.28f
        val outerX = sign * 0.42f
        val baseY = 0.30f + raiseWeight * 0.09f
        val innerTilt = if (pose.frownWeight > 0.2f) 0.05f else raiseWeight * 0.04f

        val p1 = project3D(innerX, baseY + innerTilt, 0.64f)
        val p2 = project3D(midX, baseY + 0.04f, 0.62f)
        val p3 = project3D(outerX, baseY - 0.02f, 0.54f)

        val browPath = Path().apply {
            moveTo(p1.x, p1.y)
            quadraticBezierTo(p2.x, p2.y, p3.x, p3.y)
        }
        drawPath(
            path = browPath,
            color = primaryColor,
            style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
    drawEyebrow(isLeft = true, raiseWeight = pose.browRaiseLeft)
    drawEyebrow(isLeft = false, raiseWeight = pose.browRaiseRight)

    fun draw3DEye(isLeft: Boolean, blinkWeight: Float) {
        val sign = if (isLeft) -1f else 1f
        val eyeCenterX = sign * 0.26f
        val eyeCenterY = 0.14f
        val eyeZ = 0.65f

        val openness = ((1.0f - blinkWeight) + pose.eyeWideWeight * 0.35f).coerceIn(0.05f, 1.35f)
        val halfW = 0.13f
        val halfH = 0.065f * openness

        val leftCorner = project3D(eyeCenterX - halfW, eyeCenterY, eyeZ - 0.02f)
        val rightCorner = project3D(eyeCenterX + halfW, eyeCenterY, eyeZ - 0.02f)
        val topLid = project3D(eyeCenterX, eyeCenterY + halfH, eyeZ + 0.02f)
        val bottomLid = project3D(eyeCenterX, eyeCenterY - halfH, eyeZ + 0.02f)

        val eyePath = Path().apply {
            moveTo(leftCorner.x, leftCorner.y)
            quadraticBezierTo(topLid.x, topLid.y, rightCorner.x, rightCorner.y)
            quadraticBezierTo(bottomLid.x, bottomLid.y, leftCorner.x, leftCorner.y)
            close()
        }
        drawPath(
            path = eyePath,
            color = primaryColor,
            style = Stroke(width = 2.4.dp.toPx())
        )

        if (openness > 0.22f) {
            val irisCenter = project3D(eyeCenterX, eyeCenterY, eyeZ + 0.03f)
            drawCircle(
                color = primaryColor.copy(alpha = 0.85f),
                radius = (5.5f * openness.coerceAtMost(1.1f)).dp.toPx(),
                center = irisCenter
            )
            drawCircle(
                color = Color.White,
                radius = 2.2.dp.toPx(),
                center = Offset(irisCenter.x - 1.5.dp.toPx(), irisCenter.y - 1.5.dp.toPx())
            )
        }
    }
    draw3DEye(isLeft = true, blinkWeight = pose.eyeBlinkLeft)
    draw3DEye(isLeft = false, blinkWeight = pose.eyeBlinkRight)

    val noseTop = project3D(0f, 0.16f, 0.68f)
    val noseTip = project3D(0f, -0.06f, 0.76f)
    val noseBase = project3D(0f, -0.11f, 0.70f)
    val nosePath = Path().apply {
        moveTo(noseTop.x, noseTop.y)
        lineTo(noseTip.x, noseTip.y)
        lineTo(noseBase.x, noseBase.y)
    }
    drawPath(
        path = nosePath,
        color = primaryColor.copy(alpha = 0.65f),
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    )

    val mouthCenterY = -0.28f
    val cornerCurveY = (pose.smileWeight * 0.09f) - (pose.frownWeight * 0.08f)
    val mouthHalfW = 0.22f + (pose.smileWeight * 0.05f)
    val jawOpenOffset = pose.mouthOpenWeight * 0.16f

    val leftMouthCorner = project3D(-mouthHalfW, mouthCenterY + cornerCurveY, 0.60f)
    val rightMouthCorner = project3D(mouthHalfW, mouthCenterY + cornerCurveY, 0.60f)
    val upperLipMid = project3D(0f, mouthCenterY + 0.03f + jawOpenOffset * 0.18f, 0.68f)
    val lowerLipMid = project3D(0f, mouthCenterY - 0.03f - jawOpenOffset * 0.85f, 0.66f)

    val mouthPath = Path().apply {
        moveTo(leftMouthCorner.x, leftMouthCorner.y)
        quadraticBezierTo(upperLipMid.x, upperLipMid.y, rightMouthCorner.x, rightMouthCorner.y)
        quadraticBezierTo(lowerLipMid.x, lowerLipMid.y, leftMouthCorner.x, leftMouthCorner.y)
        close()
    }

    if (pose.mouthOpenWeight > 0.06f) {
        drawPath(
            path = mouthPath,
            color = primaryColor.copy(alpha = 0.28f)
        )
    }
    drawPath(
        path = mouthPath,
        color = primaryColor,
        style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
    )
}
