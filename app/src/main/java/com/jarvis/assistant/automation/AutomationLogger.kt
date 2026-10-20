package com.jarvis.assistant.automation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AutomationLogEntry(
    val timestampMs: Long = System.currentTimeMillis(),
    val actionName: String,
    val status: ActionExecutionStatus,
    val detail: String
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }
}

class AutomationLogger {

    private val _logs = MutableStateFlow<List<AutomationLogEntry>>(emptyList())
    val logs: StateFlow<List<AutomationLogEntry>> = _logs.asStateFlow()

    private val _latestStatusBanner = MutableStateFlow("Ready")
    val latestStatusBanner: StateFlow<String> = _latestStatusBanner.asStateFlow()

    fun updateLiveBanner(statusText: String) {
        _latestStatusBanner.value = statusText
    }

    fun record(actionName: String, result: ActionExecutionResult) {
        _latestStatusBanner.value = "${result.status.displayLabel} ${result.detailMessage}".trim()
        val entry = AutomationLogEntry(
            actionName = actionName,
            status = result.status,
            detail = result.detailMessage
        )
        _logs.value = (_logs.value + entry).takeLast(100)
    }

    fun clearLogs() {
        _logs.value = emptyList()
        _latestStatusBanner.value = "Ready"
    }
}
