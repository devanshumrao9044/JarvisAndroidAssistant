package com.jarvis.assistant.automation

import com.jarvis.assistant.ai.PhoneActionType
import com.jarvis.assistant.ai.ValidatedPhoneAction
import com.jarvis.assistant.memory.AssistantPreferences
import kotlinx.coroutines.flow.first

/**
 * Narrow, validated execution router for all native Android phone actions.
 * Keeps AI interpretation completely separate from OS execution.
 */
class PhoneActionRouter(
    private val appLauncher: AppLauncher,
    private val actionPermissionManager: ActionPermissionManager,
    private val assistantPreferences: AssistantPreferences,
    private val automationLogger: AutomationLogger,
    private val onStopSpeakingRequested: () -> Unit
) {

    /**
     * Narrow action interface required by Section 5 of the specification:
     * executeAction(actionType, validatedArguments)
     */
    suspend fun executeAction(
        action: ValidatedPhoneAction,
        isConfirmedByUser: Boolean = false
    ): ActionExecutionResult {
        val prefs = assistantPreferences.identityFlow.first()

        // 1. Immediate safety override actions
        if (action.actionType == PhoneActionType.STOP_SPEAKING) {
            onStopSpeakingRequested()
            val res = ActionExecutionResult(
                status = ActionExecutionStatus.ACTION_COMPLETED,
                detailMessage = "Stopped assistant speech playback.",
                actionSummary = action.toSummaryLabel()
            )
            automationLogger.record(action.actionType.name, res)
            return res
        }

        if (action.actionType == PhoneActionType.STOP_CONTROLLING_PHONE) {
            assistantPreferences.setAutomationEnabledByUser(false)
            actionPermissionManager.cancelPendingConfirmation()
            val res = ActionExecutionResult(
                status = ActionExecutionStatus.ACTION_COMPLETED,
                detailMessage = "Phone control automation has been disabled as requested.",
                actionSummary = action.toSummaryLabel()
            )
            automationLogger.record(action.actionType.name, res)
            return res
        }

        // 2. Check permission & confirmation gates
        val gateFailure = actionPermissionManager.checkActionGate(
            action = action,
            automationEnabledInPrefs = prefs.automationEnabledByUser,
            isConfirmedByUser = isConfirmedByUser
        )
        if (gateFailure != null) {
            automationLogger.record(action.actionType.name, gateFailure)
            return gateFailure
        }

        // 3. Update live progress status banner before execution
        automationLogger.updateLiveBanner(action.toSummaryLabel())

        // 4. Execute validated action
        val result: ActionExecutionResult = when (action.actionType) {
            PhoneActionType.OPEN_APP -> {
                appLauncher.openApp(action.targetAppName.orEmpty())
            }
            PhoneActionType.CLICK_ELEMENT -> {
                val service = AccessibilityAutomationService.instance
                if (service == null) {
                    ActionExecutionResult(
                        status = ActionExecutionStatus.ACCESSIBILITY_SERVICE_DISABLED,
                        detailMessage = "Accessibility service is not connected."
                    )
                } else {
                    service.clickVisibleElement(action.targetText.orEmpty())
                }
            }
            PhoneActionType.TAP_FIELD_AND_TYPE -> {
                val service = AccessibilityAutomationService.instance
                if (service == null) {
                    ActionExecutionResult(
                        status = ActionExecutionStatus.ACCESSIBILITY_SERVICE_DISABLED,
                        detailMessage = "Accessibility service is not connected."
                    )
                } else {
                    service.tapFieldAndType(
                        targetFieldLabel = action.targetText.orEmpty(),
                        textToType = action.textToType.orEmpty()
                    )
                }
            }
            PhoneActionType.TYPE_TEXT -> {
                val service = AccessibilityAutomationService.instance
                if (service == null) {
                    ActionExecutionResult(
                        status = ActionExecutionStatus.ACCESSIBILITY_SERVICE_DISABLED,
                        detailMessage = "Accessibility service is not connected."
                    )
                } else {
                    service.typeIntoCurrentlyFocusedField(action.textToType.orEmpty())
                }
            }
            PhoneActionType.SCROLL -> {
                val service = AccessibilityAutomationService.instance
                if (service == null) {
                    ActionExecutionResult(
                        status = ActionExecutionStatus.ACCESSIBILITY_SERVICE_DISABLED,
                        detailMessage = "Accessibility service is not connected."
                    )
                } else {
                    service.scrollScreen(action.scrollDirection, action.durationMs)
                }
            }
            PhoneActionType.GO_BACK -> {
                val service = AccessibilityAutomationService.instance
                service?.performNavigateBack() ?: ActionExecutionResult(
                    status = ActionExecutionStatus.ACCESSIBILITY_SERVICE_DISABLED,
                    detailMessage = "Accessibility service is not connected."
                )
            }
            PhoneActionType.GO_HOME -> {
                val service = AccessibilityAutomationService.instance
                service?.performNavigateHome() ?: ActionExecutionResult(
                    status = ActionExecutionStatus.ACCESSIBILITY_SERVICE_DISABLED,
                    detailMessage = "Accessibility service is not connected."
                )
            }
            PhoneActionType.SHOW_RECENTS -> {
                val service = AccessibilityAutomationService.instance
                service?.performShowRecents() ?: ActionExecutionResult(
                    status = ActionExecutionStatus.ACCESSIBILITY_SERVICE_DISABLED,
                    detailMessage = "Accessibility service is not connected."
                )
            }
            PhoneActionType.TAP_COORDINATES -> {
                val service = AccessibilityAutomationService.instance
                val x = action.tapX ?: -1
                val y = action.tapY ?: -1
                if (service == null) {
                    ActionExecutionResult(
                        status = ActionExecutionStatus.ACCESSIBILITY_SERVICE_DISABLED,
                        detailMessage = "Accessibility service is not connected."
                    )
                } else {
                    service.tapCoordinates(x, y)
                }
            }
            PhoneActionType.STOP_SPEAKING,
            PhoneActionType.STOP_CONTROLLING_PHONE -> {
                ActionExecutionResult(ActionExecutionStatus.ACTION_COMPLETED, "Completed.")
            }
        }

        val enrichedResult = result.copy(actionSummary = action.toSummaryLabel())
        automationLogger.record(action.actionType.name, enrichedResult)
        return enrichedResult
    }
}
