package com.jarvis.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.memory.AssistantIdentityState
import com.jarvis.assistant.ui.theme.JarvisCardSurface
import com.jarvis.assistant.ui.theme.JarvisCrimson
import com.jarvis.assistant.ui.theme.JarvisCyan
import com.jarvis.assistant.ui.theme.JarvisDeepNavy
import com.jarvis.assistant.ui.theme.JarvisTextPrimary
import com.jarvis.assistant.ui.theme.JarvisTextSecondary
import com.jarvis.assistant.ui.theme.JarvisVoiceOrb
import com.jarvis.assistant.voice.VoiceSessionState

/**
 * First-launch and identity rename screen implementing Section 2 ("Custom Assistant Name System").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantNameScreen(
    currentSavedName: String,
    isFirstLaunch: Boolean,
    onSaveName: (String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onCancelRename: (() -> Unit)? = null
) {
    var nameInput by rememberSaveable { mutableStateOf(currentSavedName) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepNavy)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        JarvisVoiceOrb(
            sessionState = VoiceSessionState.LISTENING,
            amplitude = 0.25f,
            orbSize = 150.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Create Your AI Assistant",
            style = MaterialTheme.typography.headlineMedium,
            color = JarvisTextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Give your personal AI assistant a name.",
            style = MaterialTheme.typography.bodyLarge,
            color = JarvisTextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = JarvisCardSurface),
            border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        validationError = null
                    },
                    label = { Text("Assistant Name") },
                    placeholder = { Text("Enter assistant name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisTextSecondary.copy(alpha = 0.4f),
                        focusedLabelColor = JarvisCyan,
                        unfocusedLabelColor = JarvisTextSecondary,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary
                    )
                )

                if (validationError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = validationError.orEmpty(),
                        color = JarvisCrimson,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Example names (tap to fill):",
                    style = MaterialTheme.typography.labelMedium,
                    color = JarvisTextSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistantIdentityState.EXAMPLE_NAMES.forEach { sampleName ->
                        AssistChip(
                            onClick = {
                                nameInput = sampleName
                                validationError = null
                            },
                            label = {
                                Text(
                                    text = sampleName,
                                    color = if (nameInput.equals(sampleName, ignoreCase = true)) {
                                        JarvisDeepNavy
                                    } else {
                                        JarvisCyan
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (nameInput.equals(sampleName, ignoreCase = true)) {
                                    JarvisCyan
                                } else {
                                    JarvisDeepNavy
                                }
                            ),
                            border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.6f))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        onSaveName(
                            nameInput,
                            {},
                            { err -> validationError = err }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisCyan,
                        contentColor = JarvisDeepNavy
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isFirstLaunch) "Create Assistant" else "Change Name",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                if (!isFirstLaunch && onCancelRename != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onCancelRename,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        border = BorderStroke(1.dp, JarvisTextSecondary.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", color = JarvisTextSecondary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Your chosen name is stored persistently on your device and injected into every Gemini Live voice session, greeting, and notification.",
            style = MaterialTheme.typography.bodySmall,
            color = JarvisTextSecondary.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
    }
}
