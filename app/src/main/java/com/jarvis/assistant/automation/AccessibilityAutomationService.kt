package com.jarvis.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.jarvis.assistant.ai.ScrollDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User-authorized Android AccessibilityService for screen inspection and touch automation.
 * Respects system consent and provides immediate stop/disable controls.
 */
class AccessibilityAutomationService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()
        if (!pkg.isNullOrBlank()) {
            _activeForegroundPackage.value = pkg
        }
    }

    override fun onInterrupt() {
        // Triggered when the system interrupts accessibility feedback
    }

    override fun onUnbind(intent: Intent?): Boolean {
        _isServiceConnected.value = false
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        _isServiceConnected.value = false
        instance = null
        super.onDestroy()
    }

    suspend fun clickVisibleElement(targetLabel: String): ActionExecutionResult {
        val root = rootInActiveWindow
            ?: return ActionExecutionResult(
                status = ActionExecutionStatus.TARGET_NOT_FOUND,
                detailMessage = "No active window content is available to inspect."
            )

        val candidate = ScreenNodeInspector.findBestMatchingNode(root, targetLabel, requireEditable = false)
            ?: return ActionExecutionResult(
                status = ActionExecutionStatus.TARGET_NOT_FOUND,
                detailMessage = "Could not find any visible UI element matching '$targetLabel' on the current screen."
            )

        // Prefer node-level Accessibility ACTION_CLICK; fall back to validated center-of-bounds gesture tap
        val nodeClicked = ScreenNodeInspector.performClickOnNodeOrAncestor(candidate.node)
        if (nodeClicked) {
            return ActionExecutionResult(
                status = ActionExecutionStatus.ACTION_COMPLETED,
                detailMessage = "Clicked visible element '${candidate.matchedLabel}'."
            )
        }

        val centerX = candidate.boundsInScreen.centerX()
        val centerY = candidate.boundsInScreen.centerY()
        return GestureController.dispatchTap(this, centerX, centerY).let { tapResult ->
            if (tapResult.isSuccess) {
                ActionExecutionResult(
                    status = ActionExecutionStatus.ACTION_COMPLETED,
                    detailMessage = "Tapped visible element '${candidate.matchedLabel}' at ($centerX, $centerY)."
                )
            } else {
                tapResult
            }
        }
    }

    suspend fun tapFieldAndType(targetFieldLabel: String, textToType: String): ActionExecutionResult {
        val root = rootInActiveWindow
            ?: return ActionExecutionResult(
                status = ActionExecutionStatus.TARGET_NOT_FOUND,
                detailMessage = "No active window content is available."
            )

        val candidate = ScreenNodeInspector.findBestMatchingNode(root, targetFieldLabel, requireEditable = true)
            ?: return ActionExecutionResult(
                status = ActionExecutionStatus.TARGET_NOT_FOUND,
                detailMessage = "Could not find text field matching '$targetFieldLabel'."
            )

        val typed = ScreenNodeInspector.typeTextIntoNode(candidate.node, textToType)
        if (typed) {
            return ActionExecutionResult(
                status = ActionExecutionStatus.ACTION_COMPLETED,
                detailMessage = "Typed '$textToType' into '${candidate.matchedLabel}'."
            )
        }

        // Fallback: tap the field bounds first, then set text on focused node
        val tapResult = GestureController.dispatchTap(
            this,
            candidate.boundsInScreen.centerX(),
            candidate.boundsInScreen.centerY()
        )
        if (!tapResult.isSuccess) return tapResult

        val refreshedRoot = rootInActiveWindow
        val focusedNode = ScreenNodeInspector.findCurrentlyFocusedEditableNode(refreshedRoot)
        if (focusedNode != null && ScreenNodeInspector.typeTextIntoNode(focusedNode, textToType)) {
            return ActionExecutionResult(
                status = ActionExecutionStatus.ACTION_COMPLETED,
                detailMessage = "Tapped '${candidate.matchedLabel}' and typed '$textToType'."
            )
        }

        return ActionExecutionResult(
            status = ActionExecutionStatus.ACTION_FAILED,
            detailMessage = "Focused '${candidate.matchedLabel}', but the field rejected automated text input."
        )
    }

    fun typeIntoCurrentlyFocusedField(textToType: String): ActionExecutionResult {
        val root = rootInActiveWindow
            ?: return ActionExecutionResult(
                status = ActionExecutionStatus.TARGET_NOT_FOUND,
                detailMessage = "No active screen window available."
            )
        val focusedNode = ScreenNodeInspector.findCurrentlyFocusedEditableNode(root)
            ?: return ActionExecutionResult(
                status = ActionExecutionStatus.TARGET_NOT_FOUND,
                detailMessage = "No focused or editable text field found on screen."
            )

        return if (ScreenNodeInspector.typeTextIntoNode(focusedNode, textToType)) {
            ActionExecutionResult(
                status = ActionExecutionStatus.ACTION_COMPLETED,
                detailMessage = "Typed '$textToType' into the selected field."
            )
        } else {
            ActionExecutionResult(
                status = ActionExecutionStatus.ACTION_FAILED,
                detailMessage = "The selected field did not accept automated text entry."
            )
        }
    }

    suspend fun scrollScreen(direction: ScrollDirection, durationMs: Long): ActionExecutionResult {
        return GestureController.dispatchScroll(this, direction, durationMs)
    }

    suspend fun tapCoordinates(x: Int, y: Int): ActionExecutionResult {
        return GestureController.dispatchTap(this, x, y)
    }

    fun performNavigateBack(): ActionExecutionResult {
        val ok = performGlobalAction(GLOBAL_ACTION_BACK)
        return if (ok) {
            ActionExecutionResult(ActionExecutionStatus.ACTION_COMPLETED, "Navigated back.")
        } else {
            ActionExecutionResult(ActionExecutionStatus.ACTION_FAILED, "System rejected Back navigation.")
        }
    }

    fun performNavigateHome(): ActionExecutionResult {
        val ok = performGlobalAction(GLOBAL_ACTION_HOME)
        return if (ok) {
            ActionExecutionResult(ActionExecutionStatus.ACTION_COMPLETED, "Navigated to the Home screen.")
        } else {
            ActionExecutionResult(ActionExecutionStatus.ACTION_FAILED, "System rejected Home navigation.")
        }
    }

    fun performShowRecents(): ActionExecutionResult {
        val ok = performGlobalAction(GLOBAL_ACTION_RECENTS)
        return if (ok) {
            ActionExecutionResult(ActionExecutionStatus.ACTION_COMPLETED, "Opened Recent Apps.")
        } else {
            ActionExecutionResult(ActionExecutionStatus.ACTION_FAILED, "System rejected Recent Apps action.")
        }
    }

    companion object {
        @Volatile
        var instance: AccessibilityAutomationService? = null
            private set

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        private val _activeForegroundPackage = MutableStateFlow("")
        val activeForegroundPackage: StateFlow<String> = _activeForegroundPackage.asStateFlow()
    }
}
