package com.jarvis.assistant.automation

import com.jarvis.assistant.ai.PhoneActionType
import com.jarvis.assistant.ai.ValidatedPhoneAction
import com.jarvis.assistant.permissions.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Enforces user-level automation permissions, AccessibilityService readiness, and confirmation gates.
 */
class ActionPermissionManager(
    private val permissionManager: PermissionManager
) {
    private val _pendingConfirmation = MutableStateFlow<ValidatedPhoneAction?>(null)
    val pendingConfirmation: StateFlow<ValidatedPhoneAction?> = _pendingConfirmation.asStateFlow()

    fun checkActionGate(
        action: ValidatedPhoneAction,
        automationEnabledInPrefs: Boolean,
        isConfirmedByUser: Boolean = false
    ): ActionExecutionResult? {
        // 1. STOP_SPEAKING and STOP_CONTROLLING_PHONE are always permitted safety controls
        if (action.actionType == PhoneActionType.STOP_SPEAKING ||
            action.actionType == PhoneActionType.STOP_CONTROLLING_PHONE
        ) {
            return null
        }

        // 2. Check if user has paused/disabled phone control inside the app
        if (!automationEnabledInPrefs) {
            return ActionExecutionResult(
                status = ActionExecutionStatus.PERMISSION_REQUIRED,
                detailMessage = "Phone control is currently turned off in app settings. Enable 'Allow Phone Control' to perform this action.",
                actionSummary = action.toSummaryLabel()
            )
        }

        // 3. OPEN_APP uses standard PackageManager launch intents and does not require AccessibilityService
        if (action.actionType != PhoneActionType.OPEN_APP) {
            val isEnabledInSettings = permissionManager.isAccessibilityServiceEnabledInSettings()
            val isBound = permissionManager.isAccessibilityServiceCurrentlyBound()
            if (!isEnabledInSettings || !isBound) {
                return ActionExecutionResult(
                    status = ActionExecutionStatus.ACCESSIBILITY_SERVICE_DISABLED,
                    detailMessage = "Accessibility Phone Control service is disabled. Open Accessibility Setup in the app to enable it.",
                    actionSummary = action.toSummaryLabel()
                )
            }
        }

        // 4. Sensitive action confirmation gate
        if (action.requiresConfirmation && !isConfirmedByUser) {
            _pendingConfirmation.value = action
            return ActionExecutionResult(
                status = ActionExecutionStatus.USER_CONFIRMATION_REQUIRED,
                detailMessage = action.confirmationReason
                    ?: "Please confirm on screen before executing ${action.actionType.name}.",
                actionSummary = action.toSummaryLabel()
            )
        }

        return null // Permitted to execute
    }

    fun consumePendingConfirmation(): ValidatedPhoneAction? {
        val pending = _pendingConfirmation.value
        _pendingConfirmation.value = null
        return pending
    }

    fun cancelPendingConfirmation() {
        _pendingConfirmation.value = null
    }
}
