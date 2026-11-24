package com.sequmio.givlse.runner

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.sequmio.givlse.data.ScenarioSpec
import com.sequmio.givlse.data.ScenarioStep
import com.sequmio.givlse.data.StepExecutionResult
import com.sequmio.givlse.data.StepOutcome

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
    private val onFailure: (String) -> Unit,
    private val onResult: (StepExecutionResult) -> Unit = {},
    private val singlePass: Boolean = false,
    private val onCompleted: (ScenarioSpec) -> Unit = {}
) {
    var completedPasses: Int = 0
        private set
    var stopped: Boolean = false
        private set
    private var current = 0
    private var stepStartedAt = 0L
    private var pending: Runnable? = null

    /** Terminal interruption; never replay a partially executed gesture. */
    fun interrupt(status: String, reason: String) {
        if (stopped) return
        stop()
        for (i in current until scenario.steps.size) {
            val step = scenario.steps[i]
            val active = i == current && stepStartedAt != 0L
            onResult(StepExecutionResult(scenario.name, scenario.suite, scenario.version,
                i + 1, step.action, if (active) status else "NOT_EXECUTED",
                if (active) (SystemClock.elapsedRealtime() - stepStartedAt).coerceAtLeast(0) else 0L,
                reason.take(220), System.currentTimeMillis(),
                StepOutcome.expected(step.action),
                if (active) "Interrupted before verification" else "Not executed",
                status))
        }
    }

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
        if (current >= scenario.steps.size) {
            completedPasses++
            onStep("Scenario pass " + completedPasses + " completed")
            onCompleted(scenario)
            if (singlePass || !scenario.loopSteps) return
            current = 0
        }
        if (!mayRun()) {
            later(300) { advance() }
            return
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
        if (!mayRun()) { later(250) { execute(step) }; return }
        if (stepStartedAt == 0L) stepStartedAt = SystemClock.elapsedRealtime()
        when (step.action) {
            "WAIT" -> later(step.params.optLong("duration_ms", 500)) { complete(step) }
            "SYSTEM_BACK" -> {
                if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
                    complete(step)
                else fail(step, "Android did not perform Back")
            }
            "ASSERT_EXISTS", "ASSERT_SCREEN" -> {
                if (findNode(step) != null) complete(step)
                else retryMissing(step, "Expected screen element was not visible")
            }
            "ASSERT_NOT_EXISTS" -> {
                if (!isCorrectForeground()) {
                    retryMissing(step, "Target application is not foreground")
                } else if (findNode(step) == null) complete(step)
                else retryMissing(step, "Element is still visible")
            }
            "ASSERT_TEXT" -> {
                val node = findNode(step)
                val expected = step.params.optString("expected")
                val found = node?.text?.toString()
                val caseInsensitive = step.params.optBoolean("ignore_case", false)
                if (found != null && found.equals(expected, ignoreCase = caseInsensitive))
                    complete(step)
                else retryMissing(step, "Text assertion did not match expected value")
            }
            "ASSERT_ENABLED" -> {
                val node = findNode(step)
                if (node?.isEnabled == true) complete(step)
                else retryMissing(step, "Expected element not enabled")
            }
            "CLICK" -> {
                val node = findNode(step)
                if (node == null && step.selector != null &&
                    SystemClock.elapsedRealtime() - stepStartedAt < 2500L) {
                    later(250) { execute(step) }
                    return
                }
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
                } else retryMissing(step, "Click target not found or not clickable")
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
                if (node == null) {
                    retryMissing(step, "Editable UI element missing")
                    return
                }
                if (node.isPassword) {
                    fail(step, "Password fields cannot be targeted")
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

    private fun isCorrectForeground(): Boolean =
        service.rootInActiveWindow?.packageName?.toString() == scenario.packageName

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

    private fun retryMissing(step: ScenarioStep, message: String) {
        if (SystemClock.elapsedRealtime() - stepStartedAt < 5000L) {
            later(250) { execute(step) }
        } else {
            fail(step, message)
        }
    }

    private fun complete(step: ScenarioStep) {
        if (stopped) return
        record(step, "PASS", "Verified")
        onStep("Step " + (current + 1) + ": " + step.action + " PASS")
        current++
        stepStartedAt = 0L
        advance()
    }

    private fun fail(step: ScenarioStep, message: String) {
        if (stopped) return
        if (step.optional) {
            record(step, "SKIPPED", message)
            onStep("Optional step " + (current + 1) + " skipped: " + message)
            current++
            stepStartedAt = 0L
            advance()
        } else {
            record(step, "FAIL", message)
            val failedIndex = current + 1
            current++
            interrupt("FAIL", "Not executed after required step failed")
            onFailure("Step " + failedIndex + " " + step.action + " FAIL: " + message)
        }
    }

    private fun record(step: ScenarioStep, status: String, message: String) {
        onResult(StepExecutionResult(
            scenarioName = scenario.name,
            suite = scenario.suite,
            scenarioVersion = scenario.version,
            stepIndex = current + 1,
            action = step.action,
            status = status,
            durationMs = if (stepStartedAt == 0L) 0L else
                (SystemClock.elapsedRealtime() - stepStartedAt).coerceAtLeast(0),
            message = message.take(220),
            at = System.currentTimeMillis(),
            expectedResult = StepOutcome.expected(step.action),
            actualResult = if (status == "PASS") StepOutcome.passed(step.action) else message.take(220),
            errorCode = if (status == "PASS") "" else if (step.action.startsWith("ASSERT_"))
                "ASSERTION_NOT_MET" else "ACTION_NOT_COMPLETED"
        ))
    }
}
