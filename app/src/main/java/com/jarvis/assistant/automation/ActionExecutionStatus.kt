package com.jarvis.assistant.automation

/**
 * Honest execution status codes required by Section 4 of the specification.
 */
enum class ActionExecutionStatus(val displayLabel: String) {
    ACTION_COMPLETED("Action completed."),
    ACTION_FAILED("Action failed."),
    TARGET_NOT_FOUND("Target not found."),
    ACCESSIBILITY_SERVICE_DISABLED("Accessibility service disabled."),
    PERMISSION_REQUIRED("Permission required."),
    ACTION_UNSUPPORTED("Action unsupported."),
    USER_CONFIRMATION_REQUIRED("User confirmation required.")
}

data class ActionExecutionResult(
    val status: ActionExecutionStatus,
    val detailMessage: String,
    val actionSummary: String = ""
) {
    val isSuccess: Boolean
        get() = status == ActionExecutionStatus.ACTION_COMPLETED
}
