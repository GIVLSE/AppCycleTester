package com.sequmio.givlse.runner

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.sequmio.givlse.data.RunReportStore
import com.sequmio.givlse.data.ScenarioCodec
import com.sequmio.givlse.data.SettingsStore
import com.sequmio.givlse.model.RunEvent
import com.sequmio.givlse.model.RunPhase
import com.sequmio.givlse.model.RunSnapshot
import com.sequmio.givlse.model.TestPlan
import com.sequmio.givlse.model.TestTarget

/**
 * V0.4 foreground app runner. No UI-content reading, gesture injection, or
 * automatic acceptance of other apps' permission dialogs.
 */
class CycleAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: CycleAccessibilityService? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private val store by lazy { SettingsStore(this) }
    private val reports by lazy { RunReportStore(this) }
    private var plan: TestPlan? = null
    private var phase = RunPhase.IDLE
    private var index = 0
    private var retries = 0
    private var completedCycles = 0
    private var elapsedMs = 0L
    private var appElapsedMs = 0L
    private var recordedAppSeconds = 0L
    private var lastTickMs = 0L
    private var requestedLaunchAt = 0L
    private var launchConfirmed = false
    private var otherWindowAt = 0L
    private var otherWindowPackage = ""
    private var hadFailure = false
    private var success: Boolean? = null
    private var events: List<RunEvent> = emptyList()
    private var lastMessage = ""
    private var receiverRegistered = false
    private var scenarioPlayer: ScenarioPlayer? = null
    private var scenarioFailed = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF && phase == RunPhase.RUNNING) {
                finish("FAIL: screen switched off or locked during an active test", false)
            }
        }
    }
    private val ticker = object : Runnable {
        override fun run() {
            tick()
            if (phase == RunPhase.RUNNING) handler.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (reports.recoverInterrupted()) {
            store.appendHistory(RunEvent(System.currentTimeMillis(),
                "FAIL: previous test interrupted by Android or a process restart"))
        }
        instance = this
        publish()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (phase != RunPhase.RUNNING || event == null ||
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val expected = plan?.targets?.getOrNull(index)?.packageName ?: return
        val foreground = event.packageName?.toString() ?: return
        if (foreground == expected) {
            otherWindowAt = 0L
            otherWindowPackage = ""
            if (!launchConfirmed) {
                launchConfirmed = true
                reports.confirmed(index)
                addEvent("Foreground confirmed: " + expected)
                startScenarioIfNeeded()
            }
        } else if (foreground != otherWindowPackage) {
            otherWindowAt = SystemClock.elapsedRealtime()
            otherWindowPackage = foreground
            if (RunRecoveryPolicy.isPermissionDialog(foreground)) {
                addEvent("System permission window detected: waiting for user action")
            }
        }
    }

    override fun onInterrupt() {
        if (phase == RunPhase.RUNNING || phase == RunPhase.PAUSED)
            finish("FAIL: Accessibility service interrupted", false)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        scenarioPlayer?.stop()
        if (phase == RunPhase.RUNNING || phase == RunPhase.PAUSED)
            finish("FAIL: Accessibility service stopped", false)
        unregisterScreenReceiver()
        instance = null
        publish()
        super.onDestroy()
    }

    fun start(newPlan: TestPlan): String? {
        check(Looper.myLooper() == Looper.getMainLooper())
        newPlan.validate()?.let { return it }
        if (phase == RunPhase.RUNNING || phase == RunPhase.PAUSED)
            return "Stop the current test first"
        if (!RunnerForegroundService.start(this))
            return "Android refused the foreground test service. Open Sequmio and retry."

        plan = newPlan.copy(targets = newPlan.targets.toList())
        phase = RunPhase.RUNNING
        index = 0
        retries = 0
        completedCycles = 0
        elapsedMs = 0L
        appElapsedMs = 0L
        recordedAppSeconds = 0L
        lastTickMs = SystemClock.elapsedRealtime()
        hadFailure = false
        success = null
        events = emptyList()
        lastMessage = ""
        reports.begin(newPlan)
        registerScreenReceiver()
        addEvent("Start: " + newPlan.targets.size + " apps, mode " + newPlan.repeatMode.name)
        if (!launchWithFallback()) return "Could not open the selected applications"
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 1000L)
        publish()
        return null
    }

    fun pause() {
        if (phase != RunPhase.RUNNING) return
        accumulateTime()
        checkpoint()
        scenarioPlayer?.stop()
        scenarioPlayer = null
        phase = RunPhase.PAUSED
        handler.removeCallbacks(ticker)
        addEvent("Paused by user")
        publish()
    }

    fun resume() {
        if (phase != RunPhase.PAUSED) return
        val power = getSystemService(POWER_SERVICE) as PowerManager
        if (!power.isInteractive) {
            finish("FAIL: device screen is off; unlock before resuming", false)
            return
        }
        phase = RunPhase.RUNNING
        lastTickMs = SystemClock.elapsedRealtime()
        addEvent("Resumed")
        val target = plan?.targets?.getOrNull(index)
        if (target == null) {
            finish("FAIL: no current app to resume", false)
            return
        }
        if (!launch(target)) retryCurrent("Could not relaunch after pause")
        if (phase == RunPhase.RUNNING) {
            handler.removeCallbacks(ticker)
            handler.postDelayed(ticker, 1000L)
        }
        publish()
    }

    fun stop() {
        if (phase == RunPhase.RUNNING || phase == RunPhase.PAUSED)
            finish("Stopped by user", false, manuallyStopped = true)
    }

    fun abort(reason: String) {
        if (phase == RunPhase.RUNNING || phase == RunPhase.PAUSED) finish("FAIL: " + reason, false)
    }

    private fun tick() {
        if (phase != RunPhase.RUNNING) return
        val power = getSystemService(POWER_SERVICE) as PowerManager
        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!power.isInteractive || keyguard.isKeyguardLocked) {
            finish("FAIL: screen off or locked, foreground test cannot continue", false)
            return
        }
        accumulateTime()
        val p = plan ?: return
        if (p.maxDurationMinutes > 0 && elapsedMs >= p.maxDurationMinutes * 60_000L) {
            if (p.targets[index].scenarioJson != null && !scenarioFailed &&
                (scenarioPlayer?.completedPasses ?: 0) == 0) {
                hadFailure = true
                reports.failure(index)
                addEvent("FAIL: scenario did not complete before overall time cap")
            }
            finish("Overall time limit reached", !hadFailure)
            return
        }
        val target = p.targets[index]
        val now = SystemClock.elapsedRealtime()
        if (!launchConfirmed && otherWindowAt > 0L &&
            RunRecoveryPolicy.isPermissionDialog(otherWindowPackage)) {
            if (now - otherWindowAt >= RunRecoveryPolicy.PERMISSION_DIALOG_TIMEOUT_MS)
                retryCurrent("Permission dialog prevented initial window confirmation")
        } else if (!launchConfirmed &&
            now - requestedLaunchAt >= RunRecoveryPolicy.WINDOW_CONFIRM_TIMEOUT_MS) {
            retryCurrent("Window not confirmed within 8 seconds")
        } else if (launchConfirmed && otherWindowAt > 0L &&
            now - otherWindowAt >= RunRecoveryPolicy.interruptionTimeout(otherWindowPackage)) {
            retryCurrent(
                if (RunRecoveryPolicy.isPermissionDialog(otherWindowPackage))
                    "Permission dialog was not handled by user"
                else "Unexpected foreground app " + otherWindowPackage
            )
        } else if (appElapsedMs >= target.durationSeconds * 1000L) {
            addEvent("Completed foreground time for " + target.label)
            advanceToNext()
        }
        if (phase == RunPhase.RUNNING) {
            if (elapsedMs / 1000L % 5L == 0L) checkpoint()
            publish()
        }
    }

    private fun accumulateTime() {
        val now = SystemClock.elapsedRealtime()
        if (phase == RunPhase.RUNNING) {
            val delta = (now - lastTickMs).coerceAtLeast(0L)
            elapsedMs += delta
            if (launchConfirmed && otherWindowAt == 0L) appElapsedMs += delta
        }
        lastTickMs = now
    }

    private fun checkpoint() {
        val seconds = appElapsedMs / 1000L
        if (seconds > recordedAppSeconds) {
            reports.foregroundSeconds(index, seconds - recordedAppSeconds)
            recordedAppSeconds = seconds
        }
        reports.progress(elapsedMs / 1000L, completedCycles)
    }

    private fun advanceToNext() {
        checkpoint()
        val p = plan ?: return
        if (p.targets[index].scenarioJson != null && !scenarioFailed &&
            (scenarioPlayer?.completedPasses ?: 0) == 0) {
            hadFailure = true
            reports.failure(index)
            addEvent("FAIL: scenario did not complete for " + p.targets[index].label)
        }
        scenarioPlayer?.stop()
        scenarioPlayer = null
        val next = RunPolicy.next(index, p.targets.size, completedCycles,
            p.repeatMode, p.repeatCount)
        completedCycles = next.completedCycles
        if (next.index == null) {
            finish(if (hadFailure) "Completed with app failures" else "All cycles completed",
                !hadFailure)
            return
        }
        index = next.index
        retries = 0
        appElapsedMs = 0L
        recordedAppSeconds = 0L
        launchWithFallback()
    }

    /** Finite fallback across the plan if a package has been uninstalled. */
    private fun launchWithFallback(): Boolean {
        val p = plan ?: return false
        var inspected = 0
        while (inspected < p.targets.size && phase == RunPhase.RUNNING) {
            val target = p.targets[index]
            retries = 0
            if (launch(target)) return true
            while (RunRecoveryPolicy.canRetry(retries)) {
                retries++
                reports.retry(index)
                addEvent("Retry " + retries + " for " + target.label)
                if (launch(target)) return true
            }
            hadFailure = true
            reports.failure(index)
            addEvent("FAIL: could not launch " + target.label)
            inspected++
            val next = RunPolicy.next(index, p.targets.size, completedCycles,
                p.repeatMode, p.repeatCount)
            completedCycles = next.completedCycles
            if (next.index == null) {
                finish("No more launchable test targets", false)
                return false
            }
            index = next.index
            appElapsedMs = 0L
            recordedAppSeconds = 0L
        }
        if (phase == RunPhase.RUNNING) finish("No test target could launch", false)
        return false
    }

    private fun retryCurrent(reason: String) {
        val target = plan?.targets?.getOrNull(index) ?: return
        if (RunRecoveryPolicy.canRetry(retries)) {
            retries++
            reports.retry(index)
            addEvent("Recovery " + retries + "/" + RunRecoveryPolicy.MAX_RETRIES +
                " for " + target.label + ": " + reason)
            if (!launch(target)) retryCurrent("Relaunch failed")
        } else {
            hadFailure = true
            reports.failure(index)
            addEvent("FAIL: " + target.label + " after recovery attempts: " + reason)
            advanceToNext()
        }
    }

    private fun launch(target: TestTarget): Boolean = try {
        scenarioPlayer?.stop()
        scenarioPlayer = null
        scenarioFailed = false
        val intent = Intent.makeMainActivity(ComponentName(target.packageName, target.activityClass))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        startActivity(intent)
        requestedLaunchAt = SystemClock.elapsedRealtime()
        launchConfirmed = false
        otherWindowAt = 0L
        otherWindowPackage = ""
        reports.launch(index)
        addEvent("Launch " + target.label + " (" + (index + 1) + "/" + (plan?.targets?.size ?: 0) + ")")
        true
    } catch (error: Exception) {
        addEvent("Launch failed " + target.label + ": " + error.javaClass.simpleName)
        false
    }

    private fun finish(reason: String, passed: Boolean, manuallyStopped: Boolean = false) {
        handler.removeCallbacks(ticker)
        scenarioPlayer?.stop()
        scenarioPlayer = null
        if (phase == RunPhase.RUNNING) accumulateTime()
        checkpoint()
        phase = RunPhase.FINISHED
        success = passed
        lastMessage = reason
        addEvent(reason)
        reports.finish(
            if (manuallyStopped) "STOPPED" else if (passed) "PASS" else "FAIL",
            reason, elapsedMs / 1000L, completedCycles
        )
        unregisterScreenReceiver()
        RunnerForegroundService.stop(this)
        publish()
    }

    private fun registerScreenReceiver() {
        if (!receiverRegistered) {
            registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            receiverRegistered = true
        }
    }

    private fun unregisterScreenReceiver() {
        if (receiverRegistered) {
            unregisterReceiver(screenReceiver)
            receiverRegistered = false
        }
    }

    private fun startScenarioIfNeeded() {
        val target = plan?.targets?.getOrNull(index) ?: return
        val json = target.scenarioJson ?: return
        val spec = runCatching { ScenarioCodec.parse(json, target.packageName) }
            .getOrElse { error ->
                failScenario("Invalid scenario: " + (error.message ?: "parse error"))
                return
            }
        val targetPackage = target.packageName
        val player = ScenarioPlayer(
            service = this,
            handler = handler,
            scenario = spec,
            mayRun = {
                phase == RunPhase.RUNNING && launchConfirmed &&
                    otherWindowAt == 0L &&
                    plan?.targets?.getOrNull(index)?.packageName == targetPackage
            },
            onStep = { event -> addEvent(event) },
            onFailure = { error -> failScenario(error) }
        )
        scenarioPlayer = player
        addEvent("Executing scenario " + spec.name + " (" + spec.steps.size + " steps)")
        player.start()
    }

    private fun failScenario(error: String) {
        if (phase != RunPhase.RUNNING) return
        scenarioFailed = true
        hadFailure = true
        reports.failure(index)
        addEvent("FAIL: " + error)
        advanceToNext()
    }

    private fun addEvent(message: String) {
        val event = RunEvent(System.currentTimeMillis(), message)
        events = (events + event).takeLast(60)
        lastMessage = message
        store.appendHistory(event)
        reports.event(message)
        publish()
    }

    private fun publish() {
        val target = plan?.targets?.getOrNull(index)
        RunnerRuntime.publish(RunSnapshot(
            phase = phase,
            accessibilityReady = instance != null,
            activeApp = if (phase == RunPhase.RUNNING || phase == RunPhase.PAUSED)
                target?.label else null,
            currentIndex = index,
            completedCycles = completedCycles,
            elapsedSeconds = elapsedMs / 1000L,
            remainingSeconds = if (target != null && (phase == RunPhase.RUNNING ||
                    phase == RunPhase.PAUSED))
                ((target.durationSeconds * 1000L - appElapsedMs).coerceAtLeast(0L) + 999L) / 1000L
                else 0L,
            message = lastMessage,
            success = success,
            events = events
        ))
    }
}
