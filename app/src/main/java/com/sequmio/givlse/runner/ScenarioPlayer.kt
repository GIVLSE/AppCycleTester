package com.sequmio.givlse.runner

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.view.accessibility.AccessibilityNodeInfo
import com.sequmio.givlse.data.ScenarioSpec
import com.sequmio.givlse.data.ScenarioStep

/**
 * Deterministic, bounded, on-device scenario player.
 * Only runs within the exact foreground target package selected by the tester.
 * No external code, JavaScript, global scripts, screenshots or text collection.
 */
class ScenarioPlayer(
    private val service: AccessibilityService,
    private val handler: Handler,
    private val scenario: ScenarioSpec,
    private val mayRun: () -> Boolean,
    private val onStep: (String) -> Unit,
    private val onFailure: (String) -> Unit
) {
    var completedPasses: Int = 0
        private set
    var stopped: Boolean = false
        private set
    private var current = 0
    private var pending: Runnable? = null

    fun start() { advance() }

    fun stop() {
        stopped = true
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }

    private fun later(ms: Long, action: () -> Unit) {
        if (stopped) return
        val runnable = Runnable {
            pending = null
            if (!stopped) action()
        }
        pending = runnable
        handler.postDelayed(runnable, ms.coerceIn(0L, 30_000L))
    }

    private fun advance() {
        if (stopped) return
        if (!mayRun()) {
            later(300) { advance() }
            return
        }
        if (current >= scenario.steps.size) {
            completedPasses++
            onStep("Scenario pass " + completedPasses + " completed")
            if (!scenario.loopSteps) return
            current = 0
        }
        val step = scenario.steps[current]
        later(step.delayBeforeMs) {
            if (!mayRun()) {
                advance()
            } else execute(step)
        }
    }

    private fun execute(step: ScenarioStep) {
        if (stopped) return
        when (step.action) {
            "WAIT" -> later(step.params.optLong("duration_ms", 500)) { complete(step) }
            "SYSTEM_BACK" -> {
                if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
                    complete(step)
                else fail(step, "Android did not perform Back")
            }
            "ASSERT_EXISTS" -> {
                if (findNode(step) != null) complete(step)
                else fail(step, "Expected UI element not found")
            }
            "CLICK" -> {
                val node = findNode(step)
                var clickable = node
                var depth = 0
                while (clickable != null && !clickable.isClickable && depth++ < 8)
                    clickable = clickable.parent
                if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                    complete(step)
                } else if (step.fallback != null) {
                    val x = step.fallback.optDouble("x_percent", Double.NaN)
                    val y = step.fallback.optDouble("y_percent", Double.NaN)
                    gesture(x, y, x, y, 90L,
                        onSuccess = { complete(step) },
                        onFail = { fail(step, "Tap gesture rejected by Android") })
                } else fail(step, "Click target not found or not clickable")
            }
            "SWIPE" -> {
                val p = step.params
                gesture(
                    p.optDouble("start_x_percent"), p.optDouble("start_y_percent"),
                    p.optDouble("end_x_percent"), p.optDouble("end_y_percent"),
                    p.optLong("duration_ms", 450L).coerceIn(100L, 3000L),
                    onSuccess = { complete(step) },
                    onFail = { fail(step, "Swipe gesture rejected by Android") }
                )
            }
            "INPUT_TEXT" -> {
                val node = findNode(step)
                if (node == null || node.isPassword) {
                    fail(step, "Editable UI element missing or is a password field")
                    return
                }
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        step.params.optString("text"))
                }
                if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) complete(step)
                else fail(step, "Android refused text input")
            }
            else -> fail(step, "Unsupported UI action")
        }
    }

    private fun findNode(step: ScenarioStep): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        // Prevent actions against a system dialog, another app or the device launcher.
        if (root.packageName?.toString() != scenario.packageName) return null
        val s = step.selector ?: return null
        val resourceId = s.optString("view_id")
        if (resourceId.isNotBlank()) {
            val matches = runCatching { root.findAccessibilityNodeInfosByViewId(resourceId) }
                .getOrNull()
            if (!matches.isNullOrEmpty()) return matches.firstOrNull { it.isVisibleToUser }
        }
        val text = s.optString("text")
        val description = s.optString("content_desc")
        if (text.isBlank() && description.isBlank()) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var seen = 0
        while (queue.isNotEmpty() && seen++ < 1000) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser && (
                    text.isNotBlank() && node.text?.toString()?.equals(text, true) == true ||
                    description.isNotBlank() &&
                    node.contentDescription?.toString()?.equals(description, true) == true
                )) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.addLast(it) }
            }
        }
        return null
    }

    private fun gesture(
        startX: Double, startY: Double, endX: Double, endY: Double, duration: Long,
        onSuccess: () -> Unit, onFail: () -> Unit
    ) {
        if (!mayRun()) { onFail(); return }
        val metrics = service.resources.displayMetrics
        val path = Path().apply {
            moveTo((startX * metrics.widthPixels).toFloat(),
                (startY * metrics.heightPixels).toFloat())
            lineTo((endX * metrics.widthPixels).toFloat(),
                (endY * metrics.heightPixels).toFloat())
        }
        val builder = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
        val accepted = service.dispatchGesture(builder.build(),
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (!stopped) onSuccess()
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (!stopped) onFail()
                }
            }, handler)
        if (!accepted) onFail()
    }

    private fun complete(step: ScenarioStep) {
        if (stopped) return
        onStep("Step " + (current + 1) + ": " + step.action + " PASS")
        current++
        advance()
    }

    private fun fail(step: ScenarioStep, message: String) {
        if (stopped) return
        if (step.optional) {
            onStep("Optional step " + (current + 1) + " skipped: " + message)
            current++
            advance()
        } else {
            stop()
            onFailure("Step " + (current + 1) + " " + step.action + " FAIL: " + message)
        }
    }
}
