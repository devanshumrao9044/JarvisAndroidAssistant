package com.jarvis.assistant.avatar

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Hosts the Filament surface and reports model readiness without pretending a missing GLB is loaded. */
@Composable
fun AvatarView(
    avatarRenderer: AvatarRenderer,
    assistantName: String,
    viewportHeight: Dp
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val modelState by avatarRenderer.modelLoadState.collectAsState()
    val pose by avatarRenderer.evaluatedPose.collectAsState()

    DisposableEffect(avatarRenderer) {
        onDispose { avatarRenderer.stopRenderLoop() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(viewportHeight)
            .background(Color(0xFF07111F)),
        verticalArrangement = Arrangement.Bottom
    ) {
        AndroidView(
            factory = { context ->
                SurfaceView(context).also { view ->
                    avatarRenderer.attachSurfaceView(view, lifecycleOwner.lifecycle)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f)
                .background(Color(0xFF07111F))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1.0f)) {
                Text(
                    text = assistantName.ifBlank { "JARVIS" },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pose.expression.displayTitle + " · " + pose.activeGesture.displayLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = when (modelState) {
                    is AvatarModelLoadState.Initializing -> "Preparing"
                    is AvatarModelLoadState.LoadingFromAssets -> "Loading model"
                    is AvatarModelLoadState.LoadedGlbModel -> "3D model ready"
                    is AvatarModelLoadState.AwaitingModelAsset -> "GLB model needed"
                    is AvatarModelLoadState.Error -> "Model unavailable"
                },
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
