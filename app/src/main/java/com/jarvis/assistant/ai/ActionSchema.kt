package com.jarvis.assistant.ai

import com.jarvis.assistant.avatar.AvatarExpression
import com.jarvis.assistant.avatar.AvatarGesture
import org.json.JSONArray
import org.json.JSONObject

/**
 * Strict allowlist of phone actions supported in Part 1 & Part 2.
 */
enum class PhoneActionType {
    OPEN_APP,
    CLICK_ELEMENT,
    TAP_FIELD_AND_TYPE,
    TYPE_TEXT,
    SCROLL,
    GO_BACK,
    GO_HOME,
    SHOW_RECENTS,
    TAP_COORDINATES,
    STOP_SPEAKING,
    STOP_CONTROLLING_PHONE;

    companion object {
        fun fromStringOrNull(value: String?): PhoneActionType? {
            if (value.isNullOrBlank()) return null
            val normalized = value.trim().uppercase()
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

enum class ScrollDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT;

    companion object {
        fun fromStringOrDefault(value: String?): ScrollDirection {
            if (value.isNullOrBlank()) return DOWN
            return entries.firstOrNull { it.name == value.trim().uppercase() } ?: DOWN
        }
    }
}

data class ValidatedPhoneAction(
    val actionType: PhoneActionType,
    val targetAppName: String? = null,
    val targetText: String? = null,
    val textToType: String? = null,
    val scrollDirection: ScrollDirection = ScrollDirection.DOWN,
    val durationMs: Long = 450L,
    val tapX: Int? = null,
    val tapY: Int? = null,
    val requiresConfirmation: Boolean = false,
    val confirmationReason: String? = null,
    val functionCallId: String? = null
) {
    fun toSummaryLabel(): String = when (actionType) {
        PhoneActionType.OPEN_APP -> "Opening ${targetAppName ?: "app"}..."
        PhoneActionType.CLICK_ELEMENT -> "Searching accessible screen elements for '${targetText ?: ""}'..."
        PhoneActionType.TAP_FIELD_AND_TYPE -> "Selecting '${targetText ?: "field"}' and typing text..."
        PhoneActionType.TYPE_TEXT -> "Typing text into focused field..."
        PhoneActionType.SCROLL -> "Scrolling ${scrollDirection.name.lowercase()}..."
        PhoneActionType.GO_BACK -> "Navigating back..."
        PhoneActionType.GO_HOME -> "Going to home screen..."
        PhoneActionType.SHOW_RECENTS -> "Opening recent apps..."
        PhoneActionType.TAP_COORDINATES -> "Tapping screen coordinates ($tapX, $tapY)..."
        PhoneActionType.STOP_SPEAKING -> "Stopping speech playback..."
        PhoneActionType.STOP_CONTROLLING_PHONE -> "Stopping phone control automation..."
    }
}

object ActionSchema {

    const val TOOL_FUNCTION_NAME = "executePhoneAction"
    const val AVATAR_EXPRESSION_TOOL_NAME = "setAvatarExpression"

    /**
     * Builds the official Gemini Live API (`BidiGenerateContentSetup`) tool declaration JSON
     * containing both `executePhoneAction` (OS automation) and `setAvatarExpression` (3D Avatar emotion).
     */
    fun buildGeminiToolDeclaration(): JSONArray {
        val phoneActionProperties = JSONObject().apply {
            put("action", JSONObject().apply {
                put("type", "STRING")
                put(
                    "description",
                    "The allowed phone action to execute. Must be one of: OPEN_APP, CLICK_ELEMENT, TAP_FIELD_AND_TYPE, TYPE_TEXT, SCROLL, GO_BACK, GO_HOME, SHOW_RECENTS, TAP_COORDINATES, STOP_SPEAKING, STOP_CONTROLLING_PHONE."
                )
                put("enum", JSONArray().apply {
                    PhoneActionType.entries.forEach { put(it.name) }
                })
            })
            put("target", JSONObject().apply {
                put("type", "STRING")
                put("description", "App name to open when action is OPEN_APP (e.g., 'YouTube', 'Chrome', 'Settings', 'WhatsApp').")
            })
            put("targetText", JSONObject().apply {
                put("type", "STRING")
                put("description", "Visible text, label, or content description of the UI element to click or focus (e.g., 'Search', 'Continue', 'Play', 'Next').")
            })
            put("textToType", JSONObject().apply {
                put("type", "STRING")
                put("description", "Text string to type into the selected or focused input field when action is TAP_FIELD_AND_TYPE or TYPE_TEXT.")
            })
            put("direction", JSONObject().apply {
                put("type", "STRING")
                put("description", "Scroll direction when action is SCROLL: UP, DOWN, LEFT, or RIGHT.")
                put("enum", JSONArray(listOf("UP", "DOWN", "LEFT", "RIGHT")))
            })
            put("durationMs", JSONObject().apply {
                put("type", "INTEGER")
                put("description", "Scroll gesture duration in milliseconds (between 150 and 2000).")
            })
            put("requiresConfirmation", JSONObject().apply {
                put("type", "BOOLEAN")
                put("description", "Set true if the action sends a message, places a call, posts publicly, makes a purchase, deletes data, or changes a sensitive setting.")
            })
        }

        val phoneActionFn = JSONObject().apply {
            put("name", TOOL_FUNCTION_NAME)
            put(
                "description",
                "Executes a validated, user-authorized native Android phone action such as opening an installed app, clicking a visible UI element by label, typing text into a field, scrolling, or navigating back/home/recents."
            )
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", phoneActionProperties)
                put("required", JSONArray(listOf("action")))
            })
        }

        val avatarProperties = JSONObject().apply {
            put("expression", JSONObject().apply {
                put("type", "STRING")
                put(
                    "description",
                    "The facial expression for the 3D AI companion avatar. Must be one of: HAPPY, LAUGHING, FRIENDLY, THINKING, LISTENING, SPEAKING, SURPRISED, SAD, CONFUSED, EXCITED, SERIOUS, SLEEPY, IDLE."
                )
                put("enum", JSONArray().apply {
                    AvatarExpression.entries.forEach { put(it.name) }
                })
            })
            put("gesture", JSONObject().apply {
                put("type", "STRING")
                put(
                    "description",
                    "Optional body/head gesture animation to pair with the expression."
                )
                put("enum", JSONArray().apply {
                    AvatarGesture.entries.forEach { put(it.name) }
                })
            })
        }

        val avatarExpressionFn = JSONObject().apply {
            put("name", AVATAR_EXPRESSION_TOOL_NAME)
            put(
                "description",
                "Updates the 3D AI assistant avatar's facial expression and body gesture to match the emotional tone of the conversation."
            )
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", avatarProperties)
                put("required", JSONArray(listOf("expression")))
            })
        }

        return JSONArray().apply {
            put(JSONObject().apply {
                put("functionDeclarations", JSONArray().apply {
                    put(phoneActionFn)
                    put(avatarExpressionFn)
                })
            })
        }
    }
}
