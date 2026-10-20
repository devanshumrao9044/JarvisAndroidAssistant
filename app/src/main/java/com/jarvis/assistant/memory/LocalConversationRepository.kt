package com.jarvis.assistant.memory

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class TranscriptRole {
    USER,
    ASSISTANT,
    SYSTEM_ACTION
}

data class TranscriptMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: TranscriptRole,
    val speakerLabel: String,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val isPartial: Boolean = false
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }
}

class LocalConversationRepository(private val context: Context) {

    private val historyFile = File(context.filesDir, "local_conversation_history.json")
    private val _messages = MutableStateFlow<List<TranscriptMessage>>(emptyList())
    val messages: StateFlow<List<TranscriptMessage>> = _messages.asStateFlow()

    suspend fun loadSavedHistory() = withContext(Dispatchers.IO) {
        if (!historyFile.exists()) {
            _messages.value = emptyList()
            return@withContext
        }
        try {
            val raw = historyFile.readText(Charsets.UTF_8)
            val array = JSONArray(raw)
            val parsed = mutableListOf<TranscriptMessage>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                parsed.add(
                    TranscriptMessage(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        role = runCatching { TranscriptRole.valueOf(obj.getString("role")) }
                            .getOrDefault(TranscriptRole.ASSISTANT),
                        speakerLabel = obj.optString("speakerLabel", "Assistant"),
                        text = obj.optString("text", ""),
                        timestampMs = obj.optLong("timestampMs", System.currentTimeMillis()),
                        isPartial = false
                    )
                )
            }
            _messages.value = parsed.takeLast(200)
        } catch (_: Exception) {
            _messages.value = emptyList()
        }
    }

    suspend fun appendOrUpdateMessage(
        role: TranscriptRole,
        speakerLabel: String,
        text: String,
        isPartial: Boolean,
        persistToDisk: Boolean
    ) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return

        val current = _messages.value.toMutableList()
        val lastIndex = current.lastIndex
        if (lastIndex >= 0 && current[lastIndex].role == role && current[lastIndex].isPartial) {
            current[lastIndex] = current[lastIndex].copy(
                speakerLabel = speakerLabel,
                text = cleanText,
                isPartial = isPartial
            )
        } else {
            current.add(
                TranscriptMessage(
                    role = role,
                    speakerLabel = speakerLabel,
                    text = cleanText,
                    isPartial = isPartial
                )
            )
        }
        val bounded = current.takeLast(200)
        _messages.value = bounded

        if (persistToDisk && !isPartial) {
            persistMessages(bounded.filter { !it.isPartial })
        }
    }

    suspend fun finalizePartialMessages(persistToDisk: Boolean) {
        val updated = _messages.value.map { msg ->
            if (msg.isPartial) msg.copy(isPartial = false) else msg
        }
        _messages.value = updated
        if (persistToDisk) {
            persistMessages(updated)
        }
    }

    suspend fun relabelAssistantMessages(newAssistantName: String, persistToDisk: Boolean) {
        val updated = _messages.value.map { msg ->
            if (msg.role == TranscriptRole.ASSISTANT) {
                msg.copy(speakerLabel = newAssistantName)
            } else {
                msg
            }
        }
        _messages.value = updated
        if (persistToDisk) {
            persistMessages(updated.filter { !it.isPartial })
        }
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        _messages.value = emptyList()
        if (historyFile.exists()) {
            historyFile.delete()
        }
    }

    private suspend fun persistMessages(list: List<TranscriptMessage>) = withContext(Dispatchers.IO) {
        try {
            val array = JSONArray()
            for (item in list.takeLast(200)) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("role", item.role.name)
                    put("speakerLabel", item.speakerLabel)
                    put("text", item.text)
                    put("timestampMs", item.timestampMs)
                }
                array.put(obj)
            }
            historyFile.writeText(array.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
            // Ignore disk write failure cleanly
        }
    }
}
