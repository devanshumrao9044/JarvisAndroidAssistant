package com.jarvis.assistant.automation

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

data class MatchedNodeCandidate(
    val node: AccessibilityNodeInfo,
    val matchedLabel: String,
    val matchScore: Int,
    val boundsInScreen: Rect
)

/**
 * Inspects the active Accessibility UI tree to locate visible elements by text, contentDescription,
 * hintText, or resource ID, and performs click or text-entry actions safely.
 */
object ScreenNodeInspector {

    fun findBestMatchingNode(
        rootNode: AccessibilityNodeInfo?,
        targetQuery: String,
        requireEditable: Boolean = false
    ): MatchedNodeCandidate? {
        if (rootNode == null) return null
        val query = targetQuery.trim().lowercase()
        if (query.isEmpty()) return null

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)
        val candidates = mutableListOf<MatchedNodeCandidate>()
        var visitedCount = 0

        while (queue.isNotEmpty() && visitedCount < 600) {
            val current = queue.removeFirst()
            visitedCount++

            if (current.isVisibleToUser) {
                val scoreAndLabel = scoreNode(current, query, requireEditable)
                if (scoreAndLabel != null) {
                    val rect = Rect()
                    current.getBoundsInScreen(rect)
                    if (!rect.isEmpty && rect.width() > 0 && rect.height() > 0) {
                        candidates.add(
                            MatchedNodeCandidate(
                                node = current,
                                matchedLabel = scoreAndLabel.second,
                                matchScore = scoreAndLabel.first,
                                boundsInScreen = rect
                            )
                        )
                    }
                }
            }

            val childCount = current.childCount
            for (i in 0 until childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }

        return candidates.maxByOrNull { it.matchScore }
    }

    private fun scoreNode(
        node: AccessibilityNodeInfo,
        query: String,
        requireEditable: Boolean
    ): Pair<Int, String>? {
        if (requireEditable && !node.isEditable && node.className?.contains("EditText", ignoreCase = true) != true) {
            // Still allow matching a search bar container whose label matches, but penalize non-editable slightly
        }

        val text = node.text?.toString()?.trim().orEmpty()
        val contentDesc = node.contentDescription?.toString()?.trim().orEmpty()
        val hint = node.hintText?.toString()?.trim().orEmpty()
        val viewId = node.viewIdResourceName?.substringAfterLast('/')?.replace('_', ' ').orEmpty()

        val fields = listOf(
            text to 100,
            contentDesc to 95,
            hint to 90,
            viewId to 70
        ).filter { it.first.isNotEmpty() }

        var bestScore = 0
        var bestLabel = ""

        for ((label, baseWeight) in fields) {
            val lower = label.lowercase()
            val score = when {
                lower == query -> baseWeight + 50
                lower.startsWith(query) -> baseWeight + 30
                lower.contains(query) -> baseWeight + 15
                query.contains(lower) && lower.length >= 3 -> baseWeight
                else -> 0
            }
            if (score > bestScore) {
                bestScore = score
                bestLabel = label
            }
        }

        if (bestScore == 0) return null

        if (node.isClickable) bestScore += 10
        if (requireEditable && (node.isEditable || node.className?.contains("EditText", ignoreCase = true) == true)) {
            bestScore += 35
        }
        return bestScore to bestLabel
    }

    /**
     * Walks up the parent hierarchy to find the nearest clickable node and performs ACTION_CLICK.
     */
    fun performClickOnNodeOrAncestor(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 6) {
            if (current.isClickable && current.isEnabled) {
                if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
            current = current.parent
            depth++
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * Focuses an editable field (or the currently focused input field) and types text using ACTION_SET_TEXT.
     */
    fun typeTextIntoNode(node: AccessibilityNodeInfo, text: String): Boolean {
        val targetEditable = findEditableTarget(node) ?: node
        targetEditable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        targetEditable.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        return targetEditable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun findCurrentlyFocusedEditableNode(rootNode: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (rootNode == null) return null
        val inputFocused = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (inputFocused != null && (inputFocused.isEditable || inputFocused.className?.contains("EditText", true) == true)) {
            return inputFocused
        }
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var visited = 0
        while (queue.isNotEmpty() && visited < 400) {
            val current = queue.removeFirst()
            visited++
            if (current.isVisibleToUser && (current.isEditable || current.className?.contains("EditText", true) == true)) {
                return current
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findEditableTarget(start: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (start.isEditable || start.className?.contains("EditText", true) == true) {
            return start
        }
        for (i in 0 until start.childCount) {
            val child = start.getChild(i) ?: continue
            if (child.isEditable || child.className?.contains("EditText", true) == true) {
                return child
            }
        }
        return null
    }
}
