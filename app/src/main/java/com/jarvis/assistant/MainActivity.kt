package com.jarvis.assistant

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jarvis.assistant.ui.AccessibilitySetupScreen
import com.jarvis.assistant.ui.AssistantNameScreen
import com.jarvis.assistant.ui.ConversationScreen
import com.jarvis.assistant.ui.HomeScreen
import com.jarvis.assistant.ui.MainViewModel
import com.jarvis.assistant.ui.SettingsScreen
import com.jarvis.assistant.ui.theme.JarvisAssistantTheme
import com.jarvis.assistant.ui.theme.JarvisDeepNavy

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JarvisAssistantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = JarvisDeepNavy
                ) {
                    JarvisAssistantApp(
                        viewModel = mainViewModel,
                        onOpenSystemAccessibilitySettings = {
                            startActivity(mainViewModel.permissionManager.createOpenAccessibilitySettingsIntent())
                        }
                    )
                }
            }
        }
    }
}

private object Routes {
    const val ONBOARDING_NAME = "onboarding_name"
    const val HOME = "home"
    const val CONVERSATION = "conversation"
    const val ACCESSIBILITY_SETUP = "accessibility_setup"
    const val SETTINGS = "settings"
}

@Composable
private fun JarvisAssistantApp(
    viewModel: MainViewModel,
    onOpenSystemAccessibilitySettings: () -> Unit
) {
    val navController = rememberNavController()
    val identityState by viewModel.identityState.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val voiceAmplitude by viewModel.voiceAmplitude.collectAsState()
    val isAssistantSpeaking by viewModel.isAssistantSpeaking.collectAsState()
    val avatarModelLoadState by viewModel.avatarModelLoadState.collectAsState()
    val permissionSnapshot by viewModel.permissionSnapshot.collectAsState()
    val hasApiCredential by viewModel.hasApiCredential.collectAsState()
    val latestPhoneStatusBanner by viewModel.latestPhoneStatusBanner.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val automationLogs by viewModel.automationLogs.collectAsState()
    val pendingConfirmation by viewModel.pendingConfirmationAction.collectAsState()
    val connectionTestResult by viewModel.connectionTestStatus.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        viewModel.refreshPermissions()
        val audioGranted = grantResults[Manifest.permission.RECORD_AUDIO] == true ||
            viewModel.permissionManager.hasRecordAudioPermission()
        if (audioGranted) {
            viewModel.startLiveVoiceSession()
        }
    }

    val triggerStartVoiceWithPermissionCheck = {
        if (viewModel.permissionManager.hasRecordAudioPermission()) {
            viewModel.startLiveVoiceSession()
        } else {
            val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !viewModel.permissionManager.hasNotificationPermission()
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            micPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    val startDestination = if (identityState.isOnboardingCompleted) {
        Routes.HOME
    } else {
        Routes.ONBOARDING_NAME
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.ONBOARDING_NAME) {
            AssistantNameScreen(
                currentSavedName = identityState.assistantName,
                isFirstLaunch = !identityState.isOnboardingCompleted,
                onSaveName = { rawName, onSuccess, onError ->
                    viewModel.saveAssistantName(
                        newName = rawName,
                        onSuccess = {
                            onSuccess()
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.ONBOARDING_NAME) { inclusive = true }
                            }
                        },
                        onError = onError
                    )
                },
                onCancelRename = if (identityState.isOnboardingCompleted) {
                    { navController.popBackStack() }
                } else null
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                greetingText = viewModel.buildGreetingMessage(identityState.assistantName),
                identityState = identityState,
                avatarRenderer = viewModel.avatarRenderer,
                sessionState = sessionState,
                diagnostics = diagnostics,
                voiceAmplitude = voiceAmplitude,
                isAssistantSpeaking = isAssistantSpeaking,
                permissionSnapshot = permissionSnapshot,
                hasApiCredential = hasApiCredential,
                latestPhoneStatusBanner = latestPhoneStatusBanner,
                recentMessages = messages,
                pendingConfirmationAction = pendingConfirmation,
                onStartConversationClick = {
                    triggerStartVoiceWithPermissionCheck()
                },
                onEndConversationClick = {
                    viewModel.endLiveVoiceSession()
                },
                onStopSpeakingClick = {
                    viewModel.stopSpeaking()
                },
                onSelectAvatarExpression = { expr, gesture ->
                    viewModel.triggerAvatarExpressionManual(expr, gesture)
                },
                onOpenConversationScreen = {
                    navController.navigate(Routes.CONVERSATION)
                },
                onOpenAccessibilitySetup = {
                    navController.navigate(Routes.ACCESSIBILITY_SETUP)
                },
                onOpenSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onEditNameClick = {
                    navController.navigate(Routes.ONBOARDING_NAME)
                },
                onConfirmSensitiveAction = {
                    viewModel.confirmPendingSensitiveAction()
                },
                onCancelSensitiveAction = {
                    viewModel.cancelPendingSensitiveAction()
                }
            )
        }

        composable(Routes.CONVERSATION) {
            ConversationScreen(
                assistantName = identityState.assistantName.ifBlank { "Assistant" },
                avatarRenderer = viewModel.avatarRenderer,
                sessionState = sessionState,
                diagnostics = diagnostics,
                voiceAmplitude = voiceAmplitude,
                isAssistantSpeaking = isAssistantSpeaking,
                latestPhoneStatusBanner = latestPhoneStatusBanner,
                messages = messages,
                onBackClick = { navController.popBackStack() },
                onStartCallClick = { triggerStartVoiceWithPermissionCheck() },
                onInterruptClick = { viewModel.stopSpeaking() },
                onEndCallClick = { viewModel.endLiveVoiceSession() },
                onSendTextCommand = { viewModel.sendTypedMessageOrCommand(it) }
            )
        }

        composable(Routes.ACCESSIBILITY_SETUP) {
            AccessibilitySetupScreen(
                assistantName = identityState.assistantName.ifBlank { "Assistant" },
                permissionSnapshot = permissionSnapshot,
                automationEnabledByUser = identityState.automationEnabledByUser,
                latestStatusBanner = latestPhoneStatusBanner,
                automationLogs = automationLogs,
                onBackClick = { navController.popBackStack() },
                onOpenAndroidAccessibilitySettings = onOpenSystemAccessibilitySettings,
                onToggleAutomationEnabled = { viewModel.setAutomationEnabled(it) },
                onRunTestAction = { viewModel.runAccessibilityTestAction(it) },
                onStopControllingPhone = {
                    viewModel.setAutomationEnabled(false)
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                identityState = identityState,
                avatarModelLoadState = avatarModelLoadState,
                maskedCredentialPreview = viewModel.settingsRepository.getMaskedCredentialPreview(),
                permissionSnapshot = permissionSnapshot,
                connectionTestResult = connectionTestResult,
                isTestingConnection = isTestingConnection,
                storedMessageCount = messages.size,
                onBackClick = { navController.popBackStack() },
                onSaveAssistantName = { newName, onSuccess, onError ->
                    viewModel.saveAssistantName(newName, onSuccess, onError)
                },
                onSaveModelId = { modelId, onError ->
                    viewModel.updateActiveModelId(modelId, onError)
                },
                onSaveApiCredential = { cred, onSuccess, onError ->
                    viewModel.saveApiCredential(cred, onSuccess, onError)
                },
                onClearApiCredential = { viewModel.clearApiCredential() },
                onSelectAuthMode = { viewModel.setAuthMode(it) },
                onTestConnection = { viewModel.runConnectionTest() },
                onSelectLanguage = { viewModel.setVoiceLanguage(it) },
                onSelectVoiceName = { viewModel.setVoiceName(it) },
                onToggleContinuousConversation = { viewModel.setContinuousConversation(it) },
                onToggleSaveHistory = { viewModel.setSaveTranscriptHistory(it) },
                onSelectAvatarPerformanceTier = { viewModel.setAvatarPerformanceTier(it) },
                onSelectAvatarAnimationSpeed = { viewModel.setAvatarAnimationSpeed(it) },
                onReloadAvatarModelAsset = { viewModel.reloadAvatarModelAsset() },
                onRequestMicPermission = {
                    micPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                },
                onOpenAccessibilitySetup = {
                    navController.navigate(Routes.ACCESSIBILITY_SETUP)
                },
                onClearConversationHistory = { viewModel.clearConversationHistory() },
                onStopAllActivity = { viewModel.stopAllAssistantActivity() }
            )
        }
    }
}
