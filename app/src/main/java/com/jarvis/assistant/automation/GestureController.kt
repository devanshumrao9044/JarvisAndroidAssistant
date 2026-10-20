package com.jarvis.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import com.jarvis.assistant.ai.ScrollDirection
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Dispatches validated tap and scroll gestures through Android's AccessibilityService
 * and awaits hardware/system gesture completion callbacks before returning.
 */
object GestureController {

    suspend fun dispatchTap(
        service: AccessibilityService,
        x: Int,
        y: Int
    ): ActionExecutionResult {
        val metrics = service.resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        if (x !in 1 until screenW || y !in 1 until screenH) {
            return ActionExecutionResult(
                status = ActionExecutionStatus.ACTION_FAILED,
                detailMessage = "Coordinates ($x, $y) are outside the screen bounds (${screenW}x${screenH})."
            )
        }

        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 65L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val completed = dispatchAndAwait(service, gesture)
        return if (completed) {
            ActionExecutionResult(
                status = ActionExecutionStatus.ACTION_COMPLETED,
                detailMessage = "Tapped at ($x, $y)."
            )
        } else {
            ActionExecutionResult(
                status = ActionExecutionStatus.ACTION_FAILED,
                detailMessage = "System cancelled or rejected the tap gesture at ($x, $y)."
            )
        }
    }

    suspend fun dispatchScroll(
        service: AccessibilityService,
        direction: ScrollDirection,
        durationMs: Long
    ): ActionExecutionResult {
        val metrics = service.resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()

        val centerX = w * 0.5f
        val centerY = h * 0.5f

        // Note: To scroll the screen DOWN (reveal lower content), the finger swipes UP from 72% height to 26% height.
        val (startX, startY, endX, endY) = when (direction) {
            ScrollDirection.DOWN -> listOf(centerX, h * 0.74f, centerX, h * 0.26f)
            ScrollDirection.UP -> listOf(centerX, h * 0.28f, centerX, h * 0.76f)
            ScrollDirection.LEFT -> listOf(w * 0.22f, centerY, w * 0.80f, centerY)
            ScrollDirection.RIGHT -> listOf(w * 0.80f, centerY, w * 0.22f, centerY)
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val clampedDuration = durationMs.coerceIn(180L, 2000L)
        val stroke = GestureDescription.StrokeDescription(path, 0L, clampedDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val completed = dispatchAndAwait(service, gesture)
        return if (completed) {
            ActionExecutionResult(
                status = ActionExecutionStatus.ACTION_COMPLETED,
                detailMessage = "Scrolled ${direction.name.lowercase()}."
            )
        } else {
            ActionExecutionResult(
                status = ActionExecutionStatus.ACTION_FAILED,
                detailMessage = "Scroll ${direction.name.lowercase()} gesture was cancelled by the system."
            )
        }
    }

    private suspend fun dispatchAndAwait(
        service: AccessibilityService,
        gesture: GestureDescription
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val dispatched = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            },
            null
        )
        if (!dispatched && continuation.isActive) {
            continuation.resume(false)
        }
    }
}
