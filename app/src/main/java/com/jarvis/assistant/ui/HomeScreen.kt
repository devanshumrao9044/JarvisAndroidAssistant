package com.jarvis.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ai.ValidatedPhoneAction
import com.jarvis.assistant.avatar.AvatarExpression
import com.jarvis.assistant.avatar.AvatarGesture
import com.jarvis.assistant.avatar.AvatarRenderer
import com.jarvis.assistant.avatar.AvatarView
import com.jarvis.assistant.memory.AssistantIdentityState
import com.jarvis.assistant.memory.TranscriptMessage
import com.jarvis.assistant.permissions.DevicePermissionSnapshot
import com.jarvis.assistant.ui.theme.JarvisAmber
import com.jarvis.assistant.ui.theme.JarvisCardSurface
import com.jarvis.assistant.ui.theme.JarvisCrimson
import com.jarvis.assistant.ui.theme.JarvisCyan
import com.jarvis.assistant.ui.theme.JarvisDeepNavy
import com.jarvis.assistant.ui.theme.JarvisElevatedSurface
import com.jarvis.assistant.ui.theme.JarvisEmerald
import com.jarvis.assistant.ui.theme.JarvisSoundWaveBar
import com.jarvis.assistant.ui.theme.JarvisTextPrimary
import com.jarvis.assistant.ui.theme.JarvisTextSecondary
import com.jarvis.assistant.voice.LiveSessionDiagnostics
import com.jarvis.assistant.voice.VoiceSessionState

@Composable
fun HomeScreen(
    greetingText: String,
    identityState: AssistantIdentityState,
    avatarRenderer: AvatarRenderer,
    sessionState: VoiceSessionState,
    diagnostics: LiveSessionDiagnostics,
    voiceAmplitude: Float,
    isAssistantSpeaking: Boolean,
    permissionSnapshot: DevicePermissionSnapshot,
    hasApiCredential: Boolean,
    latestPhoneStatusBanner: String,
    recentMessages: List<TranscriptMessage>,
    pendingConfirmationAction: ValidatedPhoneAction?,
    onStartConversationClick: () -> Unit,
    onEndConversationClick: () -> Unit,
    onStopSpeakingClick: () -> Unit,
    onSelectAvatarExpression: (AvatarExpression, AvatarGesture?) -> Unit,
    onOpenConversationScreen: () -> Unit,
    onOpenAccessibilitySetup: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditNameClick: () -> Unit,
    onConfirmSensitiveAction: () -> Unit,
    onCancelSensitiveAction: () -> Unit
) {
    val isConnected = diagnostics.isConnected && sessionState.isActiveSession
    val isPhoneControlActive = permissionSnapshot.isPhoneControlReady && identityState.automationEnabledByUser
    val currentPose by avatarRenderer.evaluatedPose.collectAsState()

    if (pendingConfirmationAction != null) {
        AlertDialog(
            onDismissRequest = onCancelSensitiveAction,
            containerColor = JarvisCardSurface,
            titleContentColor = JarvisTextPrimary,
            textContentColor = JarvisTextSecondary,
            title = {
                Text("Confirm Phone Action", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    pendingConfirmationAction.confirmationReason
                        ?: "Allow ${identityState.assistantName} to execute ${pendingConfirmationAction.toSummaryLabel()}?"
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmSensitiveAction,
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisDeepNavy)
                ) {
                    Text("Approve & Execute", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelSensitiveAction) {
                    Text("Cancel", color = JarvisCrimson)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepNavy)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) JarvisEmerald else JarvisCyan)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = identityState.assistantName.ifBlank { "AI Assistant" },
                            style = MaterialTheme.typography.titleLarge,
                            color = JarvisTextPrimary,
                            fontWeight = FontWeight.ExtraBold
                        )
                        IconButton(
                            onClick = onEditNameClick,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Change Assistant Name",
                                tint = JarvisTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = "Model: ${identityState.activeModelId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisTextSecondary
                    )
                }
            }

            Row {
                IconButton(onClick = onOpenAccessibilitySetup) {
                    Icon(
                        imageVector = Icons.Default.AccessibilityNew,
                        contentDescription = "Accessibility Setup",
                        tint = if (isPhoneControlActive) JarvisEmerald else JarvisAmber
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = JarvisCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = greetingText,
            style = MaterialTheme.typography.titleMedium,
            color = JarvisTextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusPillCard(
                modifier = Modifier.weight(1f),
                label = if (isConnected) "Gemini Live: Connected" else "Gemini Live: ${sessionState.name}",
                dotColor = when {
                    isConnected -> JarvisEmerald
                    sessionState == VoiceSessionState.CONNECTING || sessionState == VoiceSessionState.RECONNECTING -> JarvisAmber
                    sessionState == VoiceSessionState.ERROR -> JarvisCrimson
                    else -> JarvisTextSecondary
                },
                onClick = onOpenSettings
            )

            StatusPillCard(
                modifier = Modifier.weight(1f),
                label = if (isPhoneControlActive) "Phone Control: Enabled" else "Phone Control: Disabled",
                dotColor = if (isPhoneControlActive) JarvisEmerald else JarvisAmber,
                onClick = onOpenAccessibilitySetup
            )
        }

        if (!hasApiCredential) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSettings() },
                colors = CardDefaults.cardColors(containerColor = JarvisCrimson.copy(alpha = 0.16f)),
                border = BorderStroke(1.dp, JarvisCrimson)
            ) {
                Text(
                    text = "Gemini API Key not configured. Tap here to enter your key in Settings before starting a live voice call.",
                    color = JarvisTextPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (diagnostics.lastErrorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisCrimson.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, JarvisCrimson)
            ) {
                Text(
                    text = diagnostics.lastErrorMessage,
                    color = JarvisTextPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AvatarView(
            avatarRenderer = avatarRenderer,
            assistantName = identityState.assistantName,
            viewportHeight = 320.dp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "3D Avatar Expressions (Auto AI-Driven or Tap to Preview):",
                style = MaterialTheme.typography.labelSmall,
                color = JarvisTextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AvatarExpression.entries.forEach { expr ->
                    val isSelected = currentPose.expression == expr
                    AssistChip(
                        onClick = { onSelectAvatarExpression(expr, expr.defaultGesture) },
                        label = {
                            Text(
                                text = expr.displayTitle,
                                color = if (isSelected) JarvisDeepNavy else JarvisCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isSelected) JarvisCyan else JarvisCardSurface
                        ),
                        border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.55f))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = sessionState.statusTitle,
                color = JarvisCyan,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            JarvisSoundWaveBar(
                isActive = sessionState.isActiveSession,
                amplitude = voiceAmplitude,
                accentColor = if (isAssistantSpeaking) JarvisEmerald else JarvisCyan
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!sessionState.isActiveSession) {
            Button(
                onClick = onStartConversationClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisCyan,
                    contentColor = JarvisDeepNavy
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Start Conversation",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isAssistantSpeaking || sessionState == VoiceSessionState.SPEAKING) {
                    OutlinedButton(
                        onClick = onStopSpeakingClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        border = BorderStroke(1.5.dp, JarvisAmber),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.VolumeOff, contentDescription = null, tint = JarvisAmber)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop Speaking", color = JarvisAmber, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onEndConversationClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisCrimson,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("End Conversation", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = JarvisCardSurface),
            border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.25f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "PHONE CONTROL STATUS",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisCyan,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = latestPhoneStatusBanner,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenConversationScreen() },
            colors = CardDefaults.cardColors(containerColor = JarvisCardSurface),
            border = BorderStroke(1.dp, JarvisTextSecondary.copy(alpha = 0.22f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = null,
                            tint = JarvisCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recent Conversation Preview",
                            style = MaterialTheme.typography.titleSmall,
                            color = JarvisTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Open Full View →",
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisCyan
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (recentMessages.isEmpty()) {
                    Text(
                        text = "No messages yet. Tap 'Start Conversation' and try saying: 'Hello!', 'Tell me something funny', or 'Open YouTube.'",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextSecondary
                    )
                } else {
                    recentMessages.takeLast(3).forEach { msg ->
                        Column(modifier = Modifier.padding(vertical = 3.dp)) {
                            Text(
                                text = "${msg.speakerLabel} (${msg.formattedTime()}):",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPillCard(
    modifier: Modifier = Modifier,
    label: String,
    dotColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = JarvisElevatedSurface),
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = JarvisTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
