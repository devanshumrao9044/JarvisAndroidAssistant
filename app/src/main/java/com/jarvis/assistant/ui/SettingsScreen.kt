package com.jarvis.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.avatar.AvatarModelLoadState
import com.jarvis.assistant.avatar.AvatarPerformanceTier
import com.jarvis.assistant.data.ConnectionTestResult
import com.jarvis.assistant.memory.AssistantIdentityState
import com.jarvis.assistant.memory.AuthMode
import com.jarvis.assistant.memory.VoiceLanguage
import com.jarvis.assistant.permissions.DevicePermissionSnapshot
import com.jarvis.assistant.ui.theme.JarvisAmber
import com.jarvis.assistant.ui.theme.JarvisCardSurface
import com.jarvis.assistant.ui.theme.JarvisCrimson
import com.jarvis.assistant.ui.theme.JarvisCyan
import com.jarvis.assistant.ui.theme.JarvisDeepNavy
import com.jarvis.assistant.ui.theme.JarvisEmerald
import com.jarvis.assistant.ui.theme.JarvisTextPrimary
import com.jarvis.assistant.ui.theme.JarvisTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    identityState: AssistantIdentityState,
    avatarModelLoadState: AvatarModelLoadState,
    maskedCredentialPreview: String,
    permissionSnapshot: DevicePermissionSnapshot,
    connectionTestResult: ConnectionTestResult?,
    isTestingConnection: Boolean,
    storedMessageCount: Int,
    onBackClick: () -> Unit,
    onSaveAssistantName: (String, onSuccess: (String) -> Unit, onError: (String) -> Unit) -> Unit,
    onSaveModelId: (String, onError: (String) -> Unit) -> Unit,
    onSaveApiCredential: (String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onClearApiCredential: () -> Unit,
    onSelectAuthMode: (AuthMode) -> Unit,
    onTestConnection: () -> Unit,
    onSelectLanguage: (VoiceLanguage) -> Unit,
    onSelectVoiceName: (String) -> Unit,
    onToggleContinuousConversation: (Boolean) -> Unit,
    onToggleSaveHistory: (Boolean) -> Unit,
    onSelectAvatarPerformanceTier: (AvatarPerformanceTier) -> Unit,
    onSelectAvatarAnimationSpeed: (Float) -> Unit,
    onReloadAvatarModelAsset: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onOpenAccessibilitySetup: () -> Unit,
    onClearConversationHistory: () -> Unit,
    onStopAllActivity: () -> Unit
) {
    var nameField by rememberSaveable(identityState.assistantName) { mutableStateOf(identityState.assistantName) }
    var nameFeedback by rememberSaveable { mutableStateOf<String?>(null) }

    var modelField by rememberSaveable(identityState.activeModelId) { mutableStateOf(identityState.activeModelId) }
    var modelFeedback by rememberSaveable { mutableStateOf<String?>(null) }

    var apiKeyField by rememberSaveable { mutableStateOf("") }
    var apiKeyFeedback by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepNavy)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = JarvisCyan)
            }
            Text(
                text = "Assistant & 3D Avatar Settings",
                style = MaterialTheme.typography.titleLarge,
                color = JarvisTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        // 1. Assistant Identity Card
        SettingsSectionCard(title = "1. Assistant Identity & Wake-Word Info") {
            Text(
                text = "Current Active Identity: ${identityState.assistantName} (AI Provider: Google Gemini Live)",
                color = JarvisTextSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = nameField,
                onValueChange = {
                    nameField = it
                    nameFeedback = null
                },
                label = { Text("Assistant Display & Spoken Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisTextSecondary.copy(alpha = 0.4f),
                    focusedTextColor = JarvisTextPrimary,
                    unfocusedTextColor = JarvisTextPrimary
                )
            )
            if (nameFeedback != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = nameFeedback.orEmpty(), color = JarvisCyan, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    onSaveAssistantName(
                        nameField,
                        { saved -> nameFeedback = "Updated assistant identity to '$saved' across all screens and Gemini Live." },
                        { err -> nameFeedback = err }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisDeepNavy)
            ) {
                Text("Change Name", fontWeight = FontWeight.Bold)
            }
        }

        // 2. 3D Avatar Engine, Performance & GLB Asset Inspector (Part 2)
        SettingsSectionCard(title = "2. 3D Avatar Engine & GLB Asset Status (Part 2)") {
            val statusDescription = when (avatarModelLoadState) {
                is AvatarModelLoadState.LoadedGlbModel ->
                    "GLB Model Loaded: ${avatarModelLoadState.assetPath}
" +
                        "• Entities: ${avatarModelLoadState.entityCount}
" +
                        "• Skeletal Clips: ${avatarModelLoadState.animationClips.joinToString().ifEmpty { "None" }}
" +
                        "• Facial Morph Targets: ${avatarModelLoadState.detectedMorphTargetCount} (${if (avatarModelLoadState.hasFacialMorphTargets) "Supported" else "None in mesh"})"
                is AvatarModelLoadState.AwaitingModelAsset ->
                    "Status: AWAITING_MODEL_ASSET (Interactive 3D Holo-Rig Active)
" +
                        "• Expected GLB File Path: app/src/main/assets/${avatarModelLoadState.expectedAssetPath}
" +
                        "• Drop any glTF 2.0 binary (.glb) character into that path to replace the 3D Holo-Rig without changing code."
                is AvatarModelLoadState.Error ->
                    "Status: Fallback Active (${avatarModelLoadState.reason})"
                else -> "Status: Initializing 3D Filament Engine..."
            }
            Text(
                text = statusDescription,
                color = if (avatarModelLoadState is AvatarModelLoadState.LoadedGlbModel) JarvisEmerald else JarvisAmber,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text("Mobile Performance & Frame-Rate Tier:", color = JarvisTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AvatarPerformanceTier.entries.forEach { tier ->
                    AssistChip(
                        onClick = { onSelectAvatarPerformanceTier(tier) },
                        label = {
                            Text(
                                text = tier.displayName,
                                color = if (identityState.avatarPerformanceTier == tier) JarvisDeepNavy else JarvisCyan,
                                fontSize = 11.sp
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (identityState.avatarPerformanceTier == tier) JarvisCyan else JarvisDeepNavy
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Animation Speed Multiplier (${identityState.avatarAnimationSpeed}x):", color = JarvisTextPrimary, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                    AssistChip(
                        onClick = { onSelectAvatarAnimationSpeed(speed) },
                        label = {
                            Text(
                                text = "${speed}x",
                                color = if (identityState.avatarAnimationSpeed == speed) JarvisDeepNavy else JarvisCyan
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (identityState.avatarAnimationSpeed == speed) JarvisCyan else JarvisDeepNavy
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onReloadAvatarModelAsset,
                border = BorderStroke(1.dp, JarvisCyan)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = JarvisCyan)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Rescan / Reload GLB Model from Assets", color = JarvisCyan)
            }
        }

        // 3. Gemini Live API & Model Configuration
        SettingsSectionCard(title = "3. Gemini Live API & Active Model") {
            Text(
                text = "Active Model ID: ${identityState.activeModelId}",
                color = JarvisTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistantIdentityState.PRESET_LIVE_MODELS.forEach { preset ->
                    AssistChip(
                        onClick = {
                            modelField = preset
                            onSaveModelId(preset) { modelFeedback = it }
                            modelFeedback = "Active model set to $preset"
                        },
                        label = {
                            Text(
                                text = preset,
                                color = if (identityState.activeModelId == preset) JarvisDeepNavy else JarvisCyan,
                                fontSize = 12.sp
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (identityState.activeModelId == preset) JarvisCyan else JarvisDeepNavy
                        ),
                        border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.6f))
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = modelField,
                onValueChange = {
                    modelField = it
                    modelFeedback = null
                },
                label = { Text("Custom Gemini Live Model ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisTextSecondary.copy(alpha = 0.4f),
                    focusedTextColor = JarvisTextPrimary,
                    unfocusedTextColor = JarvisTextPrimary
                )
            )
            if (modelFeedback != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = modelFeedback.orEmpty(), color = JarvisCyan, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onSaveModelId(modelField) { err -> modelFeedback = err }
                    if (modelFeedback == null) modelFeedback = "Saved model ID: ${modelField.trim()}"
                },
                border = BorderStroke(1.dp, JarvisCyan)
            ) {
                Text("Save Model ID", color = JarvisCyan)
            }
        }

        // 4. API Key / Ephemeral Credential Security & Connection Test
        SettingsSectionCard(title = "4. Gemini API Credentials & Connection Test") {
            Text(
                text = "Stored Credential: $maskedCredentialPreview",
                color = JarvisTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthMode.entries.forEach { mode ->
                    AssistChip(
                        onClick = { onSelectAuthMode(mode) },
                        label = {
                            Text(
                                text = if (mode == AuthMode.DEV_API_KEY) "Dev API Key" else "Ephemeral Token",
                                color = if (identityState.authMode == mode) JarvisDeepNavy else JarvisCyan
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (identityState.authMode == mode) JarvisCyan else JarvisDeepNavy
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKeyField,
                onValueChange = {
                    apiKeyField = it
                    apiKeyFeedback = null
                },
                label = { Text("Enter Gemini API Key or Short-Lived Token") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisTextSecondary.copy(alpha = 0.4f),
                    focusedTextColor = JarvisTextPrimary,
                    unfocusedTextColor = JarvisTextPrimary
                )
            )
            if (apiKeyFeedback != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = apiKeyFeedback.orEmpty(), color = JarvisCyan, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        onSaveApiCredential(
                            apiKeyField,
                            {
                                apiKeyField = ""
                                apiKeyFeedback = "Saved securely in EncryptedSharedPreferences."
                            },
                            { err -> apiKeyFeedback = err }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisDeepNavy)
                ) {
                    Text("Save Key", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        onClearApiCredential()
                        apiKeyFeedback = "Cleared stored credential."
                    },
                    border = BorderStroke(1.dp, JarvisCrimson)
                ) {
                    Text("Remove Key", color = JarvisCrimson)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onTestConnection,
                enabled = !isTestingConnection,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisEmerald, contentColor = JarvisDeepNavy)
            ) {
                if (isTestingConnection) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Gemini API & Model Connection", fontWeight = FontWeight.Bold)
            }

            if (connectionTestResult != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = connectionTestResult.message,
                    color = if (connectionTestResult.success) JarvisEmerald else JarvisCrimson,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 5. Voice Language, Persona & Continuous Conversation
        SettingsSectionCard(title = "5. Voice Language & Conversation Flow") {
            Text("Selected Language: ${identityState.language.displayName}", color = JarvisTextPrimary, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VoiceLanguage.entries.forEach { lang ->
                    AssistChip(
                        onClick = { onSelectLanguage(lang) },
                        label = {
                            Text(
                                text = lang.displayName,
                                color = if (identityState.language == lang) JarvisDeepNavy else JarvisCyan,
                                fontSize = 12.sp
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (identityState.language == lang) JarvisCyan else JarvisDeepNavy
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Prebuilt Gemini Live Voice Persona:", color = JarvisTextSecondary, fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Aoede", "Puck", "Charon", "Kore", "Fenrir").forEach { voice ->
                    AssistChip(
                        onClick = { onSelectVoiceName(voice) },
                        label = {
                            Text(
                                text = voice,
                                color = if (identityState.voiceName == voice) JarvisDeepNavy else JarvisCyan
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (identityState.voiceName == voice) JarvisCyan else JarvisDeepNavy
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Continuous Conversation Mode", color = JarvisTextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Automatically resume listening after each spoken response until you tap End Call.",
                        color = JarvisTextSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = identityState.continuousConversation,
                    onCheckedChange = onToggleContinuousConversation
                )
            }
        }

        // 6. Permissions & Phone Control Access
        SettingsSectionCard(title = "6. Permissions & Phone Control") {
            Text(
                text = "• Microphone Permission: ${if (permissionSnapshot.hasRecordAudioPermission) "Granted" else "Not Granted"}
" +
                    "• Accessibility Service: ${if (permissionSnapshot.isPhoneControlReady) "Enabled & Bound" else "Disabled"}",
                color = JarvisTextPrimary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!permissionSnapshot.hasRecordAudioPermission) {
                    Button(
                        onClick = onRequestMicPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisAmber, contentColor = JarvisDeepNavy)
                    ) {
                        Text("Grant Microphone", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = onOpenAccessibilitySetup,
                    border = BorderStroke(1.dp, JarvisCyan)
                ) {
                    Text("Accessibility Setup", color = JarvisCyan)
                }
            }
        }

        // 7. Local Memory, Privacy & Emergency Stop
        SettingsSectionCard(title = "7. Local Memory, Privacy & Emergency Stop") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Save Conversation Transcript Locally", color = JarvisTextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Stored messages on device: $storedMessageCount.",
                        color = JarvisTextSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = identityState.saveTranscriptHistory,
                    onCheckedChange = onToggleSaveHistory
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onClearConversationHistory,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, JarvisAmber)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = JarvisAmber)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear Local Conversation History & Logs", color = JarvisAmber, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onStopAllActivity,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCrimson, contentColor = Color.White)
            ) {
                Icon(Icons.Default.StopCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop All Assistant Activity Immediately", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = JarvisCardSurface),
        border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = JarvisCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
