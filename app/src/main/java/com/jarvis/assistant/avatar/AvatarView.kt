package com.jarvis.assistant.avatar
import android.view.SurfaceView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
@Composable
fun AvatarView(
    avatarRenderer: AvatarRenderer,
    assistantName: String,
    modifier: Modifier = Modifier,
    viewportHeight: Dp = 320.dp
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val modelState by avatarRenderer.modelLoadState.collectAsState()
    val pose by avatarRenderer.evaluatedPose.collectAsState()
    DisposableEffect(lifecycleOwner) {
        avatarRenderer.startRenderLoop()
        onDispose { avatarRenderer.stopRenderLoop() }
    }
    Card(
        modifier = modifier
  .fillMaxWidth()
  .height(viewportHeight),
        colors = CardDefaults.cardColors(containerColor = JarvisDeepNavy),
        border = BorderStroke(1.5.dp, JarvisCyan.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
  modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF07111F)),
  verticalArrangement = Arrangement.Bottom
        ) {
  Box(
      modifier = Modifier
          .fillMaxWidth()
          .weight(1.0f)
  ) {
      if (modelState is AvatarModelLoadState.LoadedGlbModel) {
          AndroidView(
              factory = { context ->
                  SurfaceView(context).also { view ->
                      avatarRenderer.attachSurfaceView(view, lifecycleOwner.lifecycle)
                  }
              },
              modifier = Modifier.fillMaxSize()
          )
      } else {
          Canvas(modifier = Modifier.fillMaxSize()) {
              val cx = size.width * 0.5f
              val cy = size.height * 0.48f + (pose.torsoBreathingOffset * size.height)
              val r = size.minDimension * 0.28f
              val primaryColor = when (pose.expression) {
                  AvatarExpression.HAPPY, AvatarExpression.EXCITED, AvatarExpression.LAUGHING -> JarvisEmerald
                  AvatarExpression.THINKING -> JarvisElectricBlue
                  AvatarExpression.SURPRISED, AvatarExpression.CONFUSED -> JarvisAmber
                  else -> JarvisCyan
              }
              drawCircle(
                  brush = Brush.radialGradient(
                      colors = listOf(primaryColor.copy(alpha = 0.25f), Color.Transparent),
                      center = Offset(cx, cy),
                      radius = r * 1.6f
                  ),
                  radius = r * 1.6f,
                  center = Offset(cx, cy)
              )
              drawCircle(
                  color = primaryColor,
                  radius = r,
                  center = Offset(cx, cy),
                  style = Stroke(width = 2.5.dp.toPx())
              )
              val eyeOffsetX = r * 0.36f
              val eyeY = cy - r * 0.15f
              val eyeOpen = (1.0f - pose.eyeBlinkLeft).coerceIn(0.1f, 1.0f)
              drawCircle(
                  color = primaryColor,
                  radius = (5.0f * eyeOpen).dp.toPx(),
                  center = Offset(cx - eyeOffsetX, eyeY)
              )
              drawCircle(
                  color = primaryColor,
                  radius = (5.0f * eyeOpen).dp.toPx(),
                  center = Offset(cx + eyeOffsetX, eyeY)
              )
              val mouthY = cy + r * 0.34f
              val mouthHalfW = r * (0.32f + pose.smileWeight * 0.12f)
              val mouthOpenH = r * (0.04f + pose.mouthOpenWeight * 0.28f)
              drawLine(
                  color = primaryColor,
                  start = Offset(cx - mouthHalfW, mouthY),
                  end = Offset(cx + mouthHalfW, mouthY),
                  strokeWidth = (3.0f + mouthOpenH * 0.15f).dp.toPx(),
                  cap = StrokeCap.Round
              )
          }
      }
  }
  Row(
      modifier = Modifier
          .fillMaxWidth()
          .background(JarvisCardSurface.copy(alpha = 0.9f))
          .padding(horizontal = 14.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
  ) {
      Column(modifier = Modifier.weight(1.0f)) {
          Text(
              text = assistantName.ifBlank { "JARVIS" },
              color = JarvisTextPrimary,
              fontWeight = FontWeight.SemiBold,
              fontSize = 14.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
          )
          Text(
              text = pose.expression.displayTitle + " · " + pose.activeGesture.displayLabel,
              color = JarvisTextSecondary,
              fontSize = 11.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
          )
      }
      Text(
          text = when (modelState) {
              is AvatarModelLoadState.Initializing -> "Preparing"
              is AvatarModelLoadState.LoadingFromAssets -> "Loading model"
              is AvatarModelLoadState.LoadedGlbModel -> "3D GLB Ready"
              is AvatarModelLoadState.AwaitingModelAsset -> "3D Holo-Rig Active"
              is AvatarModelLoadState.Error -> "3D Holo-Rig Fallback"
          },
          color = MaterialTheme.colorScheme.primary,
          fontSize = 11.sp,
          modifier = Modifier.padding(start = 8.dp)
      )
  }
        }
    }
}
