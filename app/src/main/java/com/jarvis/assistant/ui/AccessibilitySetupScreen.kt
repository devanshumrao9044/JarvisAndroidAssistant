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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ai.PhoneActionType
import com.jarvis.assistant.ai.ScrollDirection
import com.jarvis.assistant.ai.ValidatedPhoneAction
import com.jarvis.assistant.automation.AutomationLogEntry
import com.jarvis.assistant.permissions.DevicePermissionSnapshot
import com.jarvis.assistant.ui.theme.JarvisAmber
import com.jarvis.assistant.ui.theme.JarvisCardSurface
import com.jarvis.assistant.ui.theme.JarvisCrimson
import com.jarvis.assistant.ui.theme.JarvisCyan
import com.jarvis.assistant.ui.theme.JarvisDeepNavy
import com.jarvis.assistant.ui.theme.JarvisEmerald
import com.jarvis.assistant.ui.theme.JarvisTextPrimary
import com.jarvis.assistant.ui.theme.JarvisTextSecondary

@Composable
fun AccessibilitySetupScreen(
    assistantName: String,
    permissionSnapshot: DevicePermissionSnapshot,
    automationEnabledByUser: Boolean,
    latestStatusBanner: String,
    automationLogs: List<AutomationLogEntry>,
    onBackClick: () -> Unit,
    onOpenAndroidAccessibilitySettings: () -> Unit,
    onToggleAutomationEnabled: (Boolean) -> Unit,
    onRunTestAction: (ValidatedPhoneAction) -> Unit,
    onStopControllingPhone: () -> Unit
) {
    val isReady = permissionSnapshot.isPhoneControlReady && automationEnabledByUser

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepNavy)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = JarvisCyan
                )
            }
            Text(
                text = "Accessibility & Phone Control Setup",
                style = MaterialTheme.typography.titleLarge,
                color = JarvisTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = JarvisCardSurface),
            border = BorderStroke(1.5.dp, if (isReady) JarvisEmerald else JarvisAmber),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isReady) JarvisEmerald else JarvisAmber
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isReady) {
                            "Phone Control Status: ENABLED & ACTIVE"
                        } else {
                            "Phone Control Status: DISABLED"
                        },
                        color = if (isReady) JarvisEmerald else JarvisAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val settingStateText = if (permissionSnapshot.isAccessibilitySettingEnabled) "Enabled" else "Disabled"
                val boundStateText = if (permissionSnapshot.isAccessibilityServiceBound) "Bound & Ready" else "Not Bound"
                val masterStateText = if (automationEnabledByUser) "Allowed" else "Paused by User"

                Text(
                    text = "• Android Accessibility Setting: $settingStateText\n• Service Connection: $boundStateText\n• In-App Master Switch: $masterStateText",
                    color = JarvisTextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Allow $assistantName to control phone screen",
                        color = JarvisTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = automationEnabledByUser,
                        onCheckedChange = onToggleAutomationEnabled
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onOpenAndroidAccessibilitySettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisCyan,
                        contentColor = JarvisDeepNavy
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Android Accessibility Settings", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = JarvisCardSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "What This Service Does",
                    color = JarvisCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "When enabled by you in Android Settings, AccessibilityAutomationService allows $assistantName to perform explicit voice-authorized actions:\n" +
                        "1. Click visible buttons and UI elements matching spoken labels (e.g., 'Tap the Search button').\n" +
                        "2. Focus and type text into selected input fields.\n" +
                        "3. Dispatch smooth directional scroll gestures (Up, Down, Left, Right).\n" +
                        "4. Navigate Back, Home, or open Recent Apps.\n\n" +
                        "Safety & Privacy Policy:\n" +
                        "• The service never runs shell commands or arbitrary code.\n" +
                        "• It blocks interaction with passwords, PINs, banking screens, and CAPTCHAs.\n" +
                        "• Sensitive actions (sending messages, calls, purchases, deletions) always ask for your confirmation.\n" +
                        "• If Accessibility is disabled, normal voice chat and app launching ('Open YouTube') continue to work.",
                    color = JarvisTextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = JarvisCardSurface),
            border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Test Phone Control Actions",
                    color = JarvisCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Latest Result: $latestStatusBanner",
                    color = JarvisTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onRunTestAction(
                                ValidatedPhoneAction(
                                    actionType = PhoneActionType.SCROLL,
                                    scrollDirection = ScrollDirection.DOWN,
                                    durationMs = 500L
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, JarvisCyan)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Scroll", color = JarvisCyan, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            onRunTestAction(
                                ValidatedPhoneAction(
                                    actionType = PhoneActionType.OPEN_APP,
                                    targetAppName = "Settings"
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, JarvisCyan)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Open App", color = JarvisCyan, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onStopControllingPhone,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisCrimson,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.StopCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Controlling Phone Immediately", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (automationLogs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisCardSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Recent Automation Diagnostics",
                        color = JarvisCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    automationLogs.takeLast(6).reversed().forEach { entry ->
                        Text(
                            text = "[${entry.formattedTime()}] ${entry.actionName}: ${entry.status.displayLabel} (${entry.detail})",
                            color = JarvisTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
