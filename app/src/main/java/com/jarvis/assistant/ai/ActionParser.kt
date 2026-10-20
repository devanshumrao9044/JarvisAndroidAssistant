package com.jarvis.assistant.ai

import org.json.JSONObject

/**
 * Validates and sanitizes AI tool-call arguments into a [ValidatedPhoneAction].
 * Rejects unknown actions, malformed parameters, security bypass targets, and enforces confirmation rules.
 */
object ActionParser {

    // Security-barrier labels that must NEVER be auto-clicked or automated
    private val FORBIDDEN_SECURITY_KEYWORDS = listOf(
        "password", "passcode", "pin", "cvv", "otp", "captcha",
        "biometric", "fingerprint", "face unlock", "factory reset",
        "format device", "grant admin", "device administrator"
    )

    // Action labels that require explicit user confirmation before executing
    private val SENSITIVE_CONFIRMATION_KEYWORDS = listOf(
        "send", "pay", "purchase", "buy", "checkout", "place order",
        "delete", "remove", "erase", "uninstall", "call", "dial",
        "post", "publish", "share", "transfer", "confirm payment"
    )

    fun parseToolCall(
        functionName: String,
        argsJson: JSONObject?,
        functionCallId: String? = null
    ): Result<ValidatedPhoneAction> {
        if (functionName != ActionSchema.TOOL_FUNCTION_NAME) {
            return Result.failure(IllegalArgumentException("Unsupported tool function: $functionName"))
        }
        val args = argsJson ?: JSONObject()
        return parseActionObject(args, functionCallId)
    }

    fun parseActionObject(
        json: JSONObject,
        functionCallId: String? = null
    ): Result<ValidatedPhoneAction> {
        val rawAction = json.optString("action", "").trim()
        val actionType = PhoneActionType.fromStringOrNull(rawAction)
            ?: return Result.failure(IllegalArgumentException("Unknown or unsupported action type: '$rawAction'"))

        val targetApp = json.optString("target", "").trim().takeIf { it.isNotEmpty() }
        val targetText = json.optString("targetText", "").trim().takeIf { it.isNotEmpty() }
            ?: targetApp // Fallback if model placed element label in 'target'
        val textToType = json.optString("textToType", "").trim().takeIf { it.isNotEmpty() }
        val direction = ScrollDirection.fromStringOrDefault(json.optString("direction", "DOWN"))
        val rawDuration = json.optLong("durationMs", 450L)
        val durationMs = rawDuration.coerceIn(150L, 2500L)
        val tapX = if (json.has("x")) json.optInt("x", -1).takeIf { it >= 0 } else null
        val tapY = if (json.has("y")) json.optInt("y", -1).takeIf { it >= 0 } else null
        val aiRequestedConfirmation = json.optBoolean("requiresConfirmation", false)

        // Security check: block any attempt to interact with password/PIN/CAPTCHA/security barriers
        val combinedTargetText = listOfNotNull(targetApp, targetText, textToType)
            .joinToString(" ")
            .lowercase()

        if (FORBIDDEN_SECURITY_KEYWORDS.any { keyword -> combinedTargetText.contains(keyword) }) {
            return Result.failure(
                SecurityException("Action blocked by security policy: cannot automate passwords, PINs, CAPTCHAs, or security barriers.")
            )
        }

        // Validate required arguments per action type
        when (actionType) {
            PhoneActionType.OPEN_APP -> {
                val appName = targetApp ?: targetText
                if (appName.isNullOrBlank() || appName.length > 80) {
                    return Result.failure(IllegalArgumentException("OPEN_APP requires a valid 'target' app name."))
                }
            }
            PhoneActionType.CLICK_ELEMENT -> {
                if (targetText.isNullOrBlank() || targetText.length > 120) {
                    return Result.failure(IllegalArgumentException("CLICK_ELEMENT requires a non-empty 'targetText' label."))
                }
            }
            PhoneActionType.TAP_FIELD_AND_TYPE -> {
                if (targetText.isNullOrBlank()) {
                    return Result.failure(IllegalArgumentException("TAP_FIELD_AND_TYPE requires 'targetText' to identify the field."))
                }
                if (textToType.isNullOrBlank()) {
                    return Result.failure(IllegalArgumentException("TAP_FIELD_AND_TYPE requires non-empty 'textToType'."))
                }
            }
            PhoneActionType.TYPE_TEXT -> {
                if (textToType.isNullOrBlank()) {
                    return Result.failure(IllegalArgumentException("TYPE_TEXT requires non-empty 'textToType'."))
                }
            }
            PhoneActionType.TAP_COORDINATES -> {
                if (tapX == null || tapY == null) {
                    return Result.failure(IllegalArgumentException("TAP_COORDINATES requires valid positive x and y coordinates."))
                }
            }
            PhoneActionType.SCROLL,
            PhoneActionType.GO_BACK,
            PhoneActionType.GO_HOME,
            PhoneActionType.SHOW_RECENTS,
            PhoneActionType.STOP_SPEAKING,
            PhoneActionType.STOP_CONTROLLING_PHONE -> {
                // Valid without extra target strings
            }
        }

        // Determine if explicit user confirmation is required
        val matchedSensitiveKeyword = SENSITIVE_CONFIRMATION_KEYWORDS.firstOrNull { kw ->
            val targetLower = (targetText ?: "").lowercase()
            targetLower == kw || targetLower.startsWith("$kw ") || targetLower.endsWith(" $kw")
        }

        val needsConfirmation = aiRequestedConfirmation || (
            actionType in listOf(
                PhoneActionType.CLICK_ELEMENT,
                PhoneActionType.TAP_FIELD_AND_TYPE,
                PhoneActionType.TYPE_TEXT,
                PhoneActionType.TAP_COORDINATES
            ) && matchedSensitiveKeyword != null
        )

        val confirmationReason = when {
            matchedSensitiveKeyword != null ->
                "Clicking '${targetText ?: "element"}' may perform a sensitive action ($matchedSensitiveKeyword). Please confirm."
            aiRequestedConfirmation ->
                "This action requires your confirmation before execution."
            else -> null
        }

        return Result.success(
            ValidatedPhoneAction(
                actionType = actionType,
                targetAppName = targetApp ?: targetText,
                targetText = targetText,
                textToType = textToType,
                scrollDirection = direction,
                durationMs = durationMs,
                tapX = tapX,
                tapY = tapY,
                requiresConfirmation = needsConfirmation,
                confirmationReason = confirmationReason,
                functionCallId = functionCallId
            )
        )
    }
}
