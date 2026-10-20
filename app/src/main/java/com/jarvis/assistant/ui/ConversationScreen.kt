package com.jarvis.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.avatar.AvatarRenderer
import com.jarvis.assistant.avatar.AvatarView
import com.jarvis.assistant.memory.TranscriptMessage
import com.jarvis.assistant.memory.TranscriptRole
import com.jarvis.assistant.ui.theme.JarvisAmber
import com.jarvis.assistant.ui.theme.JarvisCardSurface
import com.jarvis.assistant.ui.theme.JarvisCrimson
import com.jarvis.assistant.ui.theme.JarvisCyan
import com.jarvis.assistant.ui.theme.JarvisDeepNavy
import com.jarvis.assistant.ui.theme.JarvisElectricBlue
import com.jarvis.assistant.ui.theme.JarvisElevatedSurface
import com.jarvis.assistant.ui.theme.JarvisEmerald
import com.jarvis.assistant.ui.theme.JarvisTextPrimary
import com.jarvis.assistant.ui.theme.JarvisTextSecondary
import com.jarvis.assistant.voice.LiveSessionDiagnostics
import com.jarvis.assistant.voice.VoiceSessionState
import java.util.Locale

@Composable
fun ConversationScreen(
    assistantName: String,
    avatarRenderer: AvatarRenderer,
    sessionState: VoiceSessionState,
    diagnostics: LiveSessionDiagnostics,
    voiceAmplitude: Float,
    isAssistantSpeaking: Boolean,
    latestPhoneStatusBanner: String,
    messages: List<TranscriptMessage>,
    onBackClick: () -> Unit,
    onStartCallClick: () -> Unit,
    onInterruptClick: () -> Unit,
    onEndCallClick: () -> Unit,
    onSendTextCommand: (String) -> Unit
) {
    var typedInput by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepNavy)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = JarvisCyan
                    )
                }
                Column {
                    Text(
                        text = "$assistantName — Live 3D Call",
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${sessionState.statusTitle} • ${formatCallDuration(diagnostics.callDurationSeconds)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (sessionState == VoiceSessionState.RECONNECTING) JarvisAmber else JarvisCyan
                    )
                }
            }

            if (diagnostics.reconnectAttempt > 0) {
                Text(
                    text = "Retry ${diagnostics.reconnectAttempt}/${diagnostics.maxReconnectAttempts}",
                    color = JarvisAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Compact 3D Avatar Viewport Synchronized with Voice & Emotions
        AvatarView(
            avatarRenderer = avatarRenderer,
            assistantName = assistantName,
            viewportHeight = 205.dp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Live Conversation Transcript List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = JarvisCardSurface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Live Transcript, Expressions & Action Log",
                                color = JarvisCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Speak naturally or type below. Try:
" +
                                    "• "Hello!" (Friendly + Greeting Wave)
" +
                                    "• "Tell me something funny" (Laughing)
" +
                                    "• "Think carefully about this question" (Thinking)
" +
                                    "• "Open YouTube" / "Scroll down"",
                                color = JarvisTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                TranscriptBubble(message = msg)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = typedInput,
                onValueChange = { typedInput = it },
                placeholder = { Text("Type message or command (e.g. Hello! / Open YouTube)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisTextSecondary.copy(alpha = 0.35f),
                    focusedTextColor = JarvisTextPrimary,
                    unfocusedTextColor = JarvisTextPrimary
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (typedInput.isNotBlank()) {
                        onSendTextCommand(typedInput)
                        typedInput = ""
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = JarvisCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!sessionState.isActiveSession) {
                Button(
                    onClick = onStartCallClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisCyan,
                        contentColor = JarvisDeepNavy
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Live Voice Session", fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onInterruptClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    border = BorderStroke(1.dp, JarvisAmber),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.VolumeOff, contentDescription = null, tint = JarvisAmber)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Interrupt", color = JarvisAmber, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onEndCallClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisCrimson,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("End Call", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TranscriptBubble(message: TranscriptMessage) {
    val (bgColor, borderColor, alignment) = when (message.role) {
        TranscriptRole.USER -> Triple(
            JarvisElectricBlue.copy(alpha = 0.22f),
            JarvisElectricBlue,
            Alignment.End
        )
        TranscriptRole.ASSISTANT -> Triple(
            JarvisCardSurface,
            JarvisCyan.copy(alpha = 0.55f),
            Alignment.Start
        )
        TranscriptRole.SYSTEM_ACTION -> Triple(
            JarvisElevatedSurface,
            JarvisEmerald.copy(alpha = 0.65f),
            Alignment.CenterHorizontally
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bgColor),
            border = BorderStroke(1.dp, borderColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.speakerLabel,
                        color = borderColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = message.formattedTime(),
                        color = JarvisTextSecondary,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.text,
                    color = JarvisTextPrimary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

private fun formatCallDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
