package com.sequmio.givlse.runner

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.sequmio.givlse.isSequmioAccessibilityEnabled
import kotlinx.coroutines.flow.StateFlow
import android.view.accessibility.AccessibilityEvent
import com.sequmio.givlse.data.RunReportStore
import com.sequmio.givlse.data.ScenarioCodec
import com.sequmio.givlse.data.ScenarioSpec
import com.sequmio.givlse.data.MyAppDraftStore
import com.sequmio.givlse.model.InstalledApp
import com.sequmio.givlse.MainActivity
import com.sequmio.givlse.data.SettingsStore
import com.sequmio.givlse.model.RunEvent
import com.sequmio.givlse.model.RunPhase
import com.sequmio.givlse.model.RunSnapshot
import com.sequmio.givlse.model.TestPlan
import com.sequmio.givlse.model.TestTarget
import com.sequmio.givlse.model.ExecutionMode
import com.sequmio.givlse.model.ExecutionPolicy

/**
 * V0.4 foreground app runner. No UI-content reading, gesture injection, or
 * automatic acceptance of other apps' permission dialogs.
 */
class CycleAccessibilityService : AccessibilityService() {
    companion object {
        private val registry = ServiceConnectionRegistry<CycleAccessibilityService>()
        val connection: StateFlow<CycleAccessibilityService?> = registry.connection
        val instance: CycleAccessibilityService? get() = registry.current

        /** Activity resume / explicit Retry: verify a known service with the framework.
         * Never start or bind an AccessibilityService ourselves.
         */
        fun refreshConnection(): Boolean {
            val candidate = registry.current ?: return false
            if (candidate.hasSystemConnection()) return true
            registry.detach(candidate)
            Log.w("SequmioRunner", "Known Runner connection is no longer available")
            candidate.publishConnectionState()
            return false
        }
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
    private var terminalStatus: String? = null
    private var events: List<RunEvent> = emptyList()
    private var lastMessage = ""
    private var receiverRegistered = false
    private var scenarioPlayer: ScenarioPlayer? = null
    private var scenarioFailed = false
    private var scenarioQueue: List<ScenarioSpec> = emptyList()
    private var scenarioPosition = 0
    private var scenarioCompletedCount = 0
    private var scenarioStartedAt = 0L
    private var scenarioDeadline = 0L
    private var scenarioIteration = 1
    private var worstStatus = "PASS"
    private var runGeneration = 0
    private var resultPresented = false
    private var lastCheckpointSecond = -1L
    private var destroying = false
    private var destroyed = false

    private fun hasSystemConnection(): Boolean = !destroyed && runCatching {
        isSequmioAccessibilityEnabled(this) && serviceInfo != null
    }.getOrDefault(false)

    private fun publishConnectionState() {
        val snapshot = RunnerRuntime.state.value
        RunnerRuntime.publish(snapshot.copy(accessibilityReady = instance != null))
    }

    /** Framework callbacks are the only authority for registering a live service. */
    private fun registerConnection(from: String, authoritative: Boolean): Boolean {
        if (destroyed) return false
        // A queued event from an old service must not displace a newer binding.
        if (!authoritative && instance != null && instance !== this) return false
        if (!hasSystemConnection()) return false
        val fresh = registry.attach(this)
        destroying = false
        if (fresh) {
            Log.i("SequmioRunner", "Runner connected via " + from +
                " pid=" + android.os.Process.myPid())
            publishConnectionState()
        }
        return true
    }
    private val scenarioTimeout = Runnable {
        if (phase == RunPhase.RUNNING && scenarioPlayer != null) {
            val now = SystemClock.elapsedRealtime()
            if (now >= scenarioDeadline) failScenario("Scenario timeout exceeded", "TIMEOUT")
        }
    }
    private var recorder: AccessibilityEventRecorder? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF && phase == RunPhase.RUNNING) {
                finish("Screen switched off or locked during an active test", false, status = "BLOCKED")
            }
        }
    }
    private val ticker = object : Runnable {
        override fun run() {
            try { tick() } catch (e: Exception) {
                Log.e("SequmioRunner", "Runner tick failed", e)
                finish("Runner error: " + e.javaClass.simpleName, false, status = "BLOCKED")
            }
            if (phase == RunPhase.RUNNING) handler.postDelayed(this, 500L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val fresh = instance !== this
        // onServiceConnected is called only after Android initializes the connection.
        // A later real event can recover a transient null serviceInfo on some OEMs.
        if (registerConnection("onServiceConnected", authoritative = true)) {
            if (fresh && phase == RunPhase.IDLE) reports.recoverInterrupted()
            publish()
        } else {
            Log.w("SequmioRunner", "onServiceConnected received; waiting for framework connection")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || destroyed) return
        // Repair a lost in-process reference only when Android delivers an actual
        // event AND the live framework connection can be verified.
        if (instance !== this) {
            if (!registerConnection("onAccessibilityEvent", authoritative = false)) return
            if (phase == RunPhase.IDLE) reports.recoverInterrupted()
        }
        if (destroying) return
        try {
            recorder?.onEvent(event)
            handleRunEvent(event)
        } catch (e: Exception) {
            // An exception in an event callback must not crash the accessibility
            // process and leave an enabled-but-unbound system service.
            Log.e("SequmioRunner", "Accessibility event handling failed", e)
            runCatching { recorder?.stop() }
            if (phase == RunPhase.RUNNING || phase == RunPhase.PAUSED)
                runCatching { finish("Accessibility event handling failed", false, status = "BLOCKED") }
        }
    }

    private fun handleRunEvent(event: AccessibilityEvent) {
        if (phase != RunPhase.RUNNING ||
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
        runCatching { recorder?.stop() }
        if (phase == RunPhase.RUNNING || phase == RunPhase.PAUSED)
            finish("Accessibility service interrupted", false, status = "BLOCKED")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        destroying = true
        Log.w("SequmioRunner", "Accessibility service unbound pid=" + android.os.Process.myPid())
        try {
            runCatching { recorder?.stop() }
            if (phase == RunPhase.RUNNING || phase == RunPhase.PAUSED)
                finish("Accessibility service disconnected", false, status = "BLOCKED")
        } finally {
            if (registry.detach(this)) publishConnectionState()
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        destroying = true
        destroyed = true
        Log.w("SequmioRunner", "Accessibility service destroyed pid=" + android.os.Process.myPid())
        try {
            runCatching { recorder?.stop() }
            if (phase == RunPhase.RUNNING || phase == RunPhase.PAUSED)
                finish("Accessibility service stopped", false, status = "BLOCKED")
        } finally {
            handler.removeCallbacksAndMessages(null)
            scenarioPlayer?.stop()
            scenarioPlayer = null
            runCatching { unregisterScreenReceiver() }
            if (registry.detach(this)) publishConnectionState()
            super.onDestroy()
        }
    }

    /**
     * A local recorder starts only on explicit user request from My App.
     * It cannot run concurrently with the timed runner or target other packages.
     */
    fun startRecording(target: InstalledApp): String? {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (instance !== this || !hasSystemConnection()) return "Runner service is disconnected"
        if (!store.accessConsentGiven()) return "Review and accept the updated Accessibility disclosure"
        if (phase == RunPhase.RUNNING || phase == RunPhase.PAUSED)
            return "Stop the running test before recording"
        if (recorder != null) return "Recorder is already active"
        val draftStore = MyAppDraftStore(this)
        val draft = draftStore.load()
        if (target.packageName != draft.packageName)
            return "Save your My App package name before recording"
        val instance = AccessibilityEventRecorder(this, target) { json ->
            recorder = null
            val result = when {
                json == null -> "No UI actions recorded in the app."
                else -> runCatching {
                    val current = draftStore.load()
                    require(current.packageName == target.packageName) { "App package changed during recording." }
                    val existing = ScenarioCodec.split(current.scenarioJson)
                    require(existing.size < 12) { "Maximum 12 test cases reached." }
                    val named = org.json.JSONObject(json)
                    named.getJSONObject("meta").put("name", "Test Case " + (existing.size + 1))
                    val combined = ScenarioCodec.compose(existing + named.toString())
                    require(combined.length <= 48000) { "Test cases exceed the 48 KB limit." }
                    draftStore.save(current.copy(scenarioJson = combined))
                    "Test case created successfully. Add an assertion before publishing."
                }.getOrElse { "Test case not created: " + (it.message ?: "Unable to save") }
            }
            // Show the resulting scenario in My App after the user stops recording.
            val back = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            runCatching { startActivity(back) }
            result
        }
        recorder = instance
        val error = instance.start()
        if (error != null) recorder = null
        return error
    }

    fun start(newPlan: TestPlan): String? {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!store.accessConsentGiven()) return "Review the Accessibility disclosure first"
        if (instance !== this || !hasSystemConnection()) return "Runner service is disconnected"
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
        lastCheckpointSecond = -1L
        hadFailure = false
        worstStatus = "PASS"
        runGeneration++
        resultPresented = false
        success = null
        terminalStatus = null
        runCatching { getSystemService(NotificationManager::class.java).cancel(411) }
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
        // Pausing a UI action mid-flight can duplicate input on resume.
        if (plan?.targets?.getOrNull(index)?.executionMode != ExecutionMode.FIXED_DURATION) return
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
            finish("Device screen is off; unlock before resuming", false, status = "BLOCKED")
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
        if (phase == RunPhase.RUNNING || phase == RunPhase.PAUSED) finish(reason, false, status = "BLOCKED")
    }

    private fun tick() {
        if (phase != RunPhase.RUNNING) return
        val power = getSystemService(POWER_SERVICE) as PowerManager
        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!power.isInteractive || keyguard.isKeyguardLocked) {
            finish("Screen off or locked, foreground test cannot continue", false, status = "BLOCKED")
            return
        }
        accumulateTime()
        val p = plan ?: return
        if (p.maxDurationMinutes > 0 && elapsedMs >= p.maxDurationMinutes * 60_000L) {
            finish("Overall time limit reached", false, status = "TIMEOUT")
            return
        }
        val target = p.targets[index]
        val now = SystemClock.elapsedRealtime()
        // A launch can reuse an existing activity without a new window event.
        val foreground = rootInActiveWindow?.packageName?.toString()
        if (foreground == target.packageName) {
            otherWindowAt = 0L
            otherWindowPackage = ""
            if (!launchConfirmed) {
                launchConfirmed = true
                reports.confirmed(index)
                startScenarioIfNeeded()
                return
            }
        } else if (launchConfirmed && foreground != null && foreground != otherWindowPackage) {
            otherWindowAt = now
            otherWindowPackage = foreground
        }
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
        } else if (target.executionMode == ExecutionMode.FIXED_DURATION &&
            launchConfirmed && appElapsedMs >= target.durationSeconds * 1000L) {
            reports.targetOutcome(index, "PASS", "")
            addEvent("Completed foreground time for " + target.label)
            advanceToNext()
        }
        if (phase == RunPhase.RUNNING) {
            val second = elapsedMs / 1000L
            if (second - lastCheckpointSecond >= 5L) {
                lastCheckpointSecond = second
                checkpoint()
            }
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
        accumulateTime()
        checkpoint()
        val p = plan ?: return
        handler.removeCallbacks(scenarioTimeout)
        scenarioPlayer?.stop()
        scenarioPlayer = null
        scenarioQueue = emptyList()
        scenarioCompletedCount = 0
        scenarioPosition = 0
        scenarioDeadline = 0L
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
            noteFailure("BLOCKED")
            reports.failure(index)
            reports.targetOutcome(index, "BLOCKED", "Could not launch application")
            addEvent("BLOCKED: could not launch " + target.label)
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
        if (scenarioPlayer != null || scenarioQueue.isNotEmpty()) {
            failScenario(reason, "BLOCKED")
            return
        }
        if (RunRecoveryPolicy.canRetry(retries)) {
            retries++
            reports.retry(index)
            addEvent("Recovery " + retries + "/" + RunRecoveryPolicy.MAX_RETRIES +
                " for " + target.label + ": " + reason)
            if (!launch(target)) retryCurrent("Relaunch failed")
        } else {
            noteFailure("BLOCKED")
            reports.failure(index)
            reports.targetOutcome(index, "BLOCKED", reason)
            addEvent("BLOCKED: " + target.label + " after recovery attempts: " + reason)
            advanceToNext()
        }
    }

    private fun launch(target: TestTarget): Boolean = try {
        scenarioPlayer?.stop()
        scenarioPlayer = null
        scenarioFailed = false
        scenarioQueue = emptyList()
        scenarioPosition = 0
        scenarioCompletedCount = 0
        scenarioIteration = 1
        scenarioDeadline = 0L
        handler.removeCallbacks(scenarioTimeout)
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

    private fun noteFailure(status: String) {
        hadFailure = true
        worstStatus = ExecutionPolicy.combine(worstStatus, status)
    }

    private fun finish(reason: String, passed: Boolean, manuallyStopped: Boolean = false,
        status: String? = null) {
        if (phase != RunPhase.RUNNING && phase != RunPhase.PAUSED) return
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(scenarioTimeout)
        val outcome = if (manuallyStopped) "STOPPED" else status
            ?: if (passed) "PASS" else if (worstStatus == "PASS") "BLOCKED" else worstStatus
        if (phase == RunPhase.RUNNING) accumulateTime()
        // Terminal phase set before callbacks to make finalization idempotent.
        phase = RunPhase.FINISHED
        terminalStatus = outcome
        success = outcome == "PASS"
        lastMessage = reason
        val generation = runGeneration
        try {
            scenarioPlayer?.interrupt(outcome, reason)
            if (scenarioPlayer != null) {
                reports.scenarioEnded(index, outcome, reason,
                    SystemClock.elapsedRealtime() - scenarioStartedAt)
                recordUnstartedCases(reason)
            }
            checkpoint()
            // Do not overwrite a successfully completed target with a different target's failure.
            if (status != null || manuallyStopped) reports.targetOutcome(index, outcome, reason)
            addEvent(reason)
        } catch (e: Exception) {
            Log.e("SequmioRunner", "Finalization metadata failed", e)
            terminalStatus = "BLOCKED"
            success = false
            lastMessage = "Runner finalization error: " + e.javaClass.simpleName
        } finally {
            scenarioPlayer?.stop()
            scenarioPlayer = null
            runCatching { unregisterScreenReceiver() }
            val finalStatus = terminalStatus ?: "BLOCKED"
            val finalReason = lastMessage
            reports.finish(finalStatus, finalReason, elapsedMs / 1000L, completedCycles) { saved ->
                if (!saved) handler.post {
                    if (runGeneration == generation && phase == RunPhase.FINISHED) {
                        lastMessage = finalReason + " · Local report could not be saved completely"
                        publish()
                        Log.e("SequmioRunner", "Final report persistence failed")
                    }
                }
            }
            publish()
            try {
                presentRunResult(finalReason, finalStatus)
            } catch (e: Exception) {
                Log.e("SequmioRunner", "Result presentation failed", e)
            } finally {
                runCatching { RunnerForegroundService.stop(this) }
            }
        }
    }

    /** Return promptly; durable report writing continues on the ordered IO worker. */
    private fun presentRunResult(reason: String, status: String) {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_SHOW_RUN_RESULT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // Notification is also a fallback when Android/OEM background launch is restricted.
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.areNotificationsEnabled()) {
                val channel = "sequmio_run_results_high_v2"
                manager.createNotificationChannel(NotificationChannel(
                    channel, "Sequmio test results", NotificationManager.IMPORTANCE_HIGH
                ))
                val open = PendingIntent.getActivity(this, 411, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                manager.notify(411, Notification.Builder(this, channel)
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .setContentTitle("Run finished · " + status)
                    .setContentText(reason)
                    .setStyle(Notification.BigTextStyle().bigText(reason))
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build())
            } else Log.w("SequmioRunner", "Result notifications disabled by Android")
        }.onFailure { Log.e("SequmioRunner", "Result notification failed", it) }
        val power = getSystemService(POWER_SERVICE) as PowerManager
        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!destroying && power.isInteractive && !keyguard.isKeyguardLocked) {
            Log.i("SequmioRunner", "Requesting return to Sequmio")
            runCatching { startActivity(intent) }
                .onFailure { Log.e("SequmioRunner", "Return request failed", it) }
            val generation = runGeneration
            handler.postDelayed({
                if (phase == RunPhase.FINISHED && runGeneration == generation &&
                    !resultPresented && rootInActiveWindow?.packageName?.toString() != packageName &&
                    power.isInteractive && !keyguard.isKeyguardLocked) {
                    Log.w("SequmioRunner", "Return not observed; retry once")
                    runCatching { startActivity(intent) }
                        .onFailure { Log.e("SequmioRunner", "Return retry failed", it) }
                }
            }, 1000L)
        }
    }

    fun acknowledgeResultVisible() {
        resultPresented = true
        Log.i("SequmioRunner", "Result activity visible")
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
        if (target.executionMode == ExecutionMode.FIXED_DURATION) return
        val raw = target.scenarioJson ?: run {
            failScenario("No scenario configured", "BLOCKED")
            return
        }
        scenarioQueue = runCatching {
            ScenarioCodec.selectCases(raw, target.packageName, target.selectedCaseKeys)
        }.getOrElse { error ->
            failScenario("Invalid scenario or changed test case selection; review Test Plan", "BLOCKED")
            return
        }
        scenarioPosition = 0
        scenarioCompletedCount = 0
        startNextScenario()
    }

    private fun startNextScenario() {
        val target = plan?.targets?.getOrNull(index) ?: return
        val spec = scenarioQueue.getOrNull(scenarioPosition) ?: return
        val targetPackage = target.packageName
        val timeout = ExecutionPolicy.timeout(spec.totalDurationSec, target.scenarioTimeoutSeconds)
        if (!reports.scenarioStarted(index, spec, completedCycles + 1, scenarioIteration, timeout)) {
            finish("Local report capacity reached; start a new plan", false, status = "BLOCKED")
            return
        }
        scenarioStartedAt = SystemClock.elapsedRealtime()
        scenarioDeadline = scenarioStartedAt + timeout * 1000L
        handler.removeCallbacks(scenarioTimeout)
        handler.postDelayed(scenarioTimeout, timeout * 1000L)
        val player = ScenarioPlayer(
            service = this, handler = handler, scenario = spec,
            mayRun = {
                phase == RunPhase.RUNNING && launchConfirmed && otherWindowAt == 0L &&
                    SystemClock.elapsedRealtime() < scenarioDeadline &&
                    rootInActiveWindow?.packageName?.toString() == targetPackage &&
                    plan?.targets?.getOrNull(index)?.packageName == targetPackage
            },
            onStep = { event -> addEvent(event) },
            onFailure = { error -> failScenario(error, "FAIL") },
            onError = { error -> failScenario(error, "BLOCKED") },
            onResult = { result -> reports.step(index, result) },
            singlePass = true,
            onCompleted = { completed ->
                if (SystemClock.elapsedRealtime() >= scenarioDeadline) {
                    failScenario("Scenario timeout exceeded", "TIMEOUT")
                } else {
                    handler.removeCallbacks(scenarioTimeout)
                    reports.scenarioEnded(index, "PASS", "",
                        SystemClock.elapsedRealtime() - scenarioStartedAt)
                    scenarioCompletedCount++
                    addEvent("Scenario PASS: " + completed.name + " v" + completed.version)
                    scenarioPlayer?.stop()
                    scenarioPlayer = null
                    accumulateTime()
                    if (scenarioPosition + 1 < scenarioQueue.size &&
                        (target.executionMode != ExecutionMode.LOOP_TEST_CASES ||
                            appElapsedMs < target.durationSeconds * 1000L)) {
                        scenarioPosition++
                        startNextScenario()
                    } else if (ExecutionPolicy.repeat(target.executionMode, appElapsedMs, target.durationSeconds)) {
                        scenarioPosition = 0
                        scenarioIteration++
                        startNextScenario()
                    } else {
                        if (!scenarioFailed) reports.targetOutcome(index, "PASS", "")
                        advanceToNext()
                    }
                }
            }
        )
        scenarioPlayer = player
        addEvent("Executing " + spec.suite + ": " + spec.name +
            " (v" + spec.version + ", timeout " + timeout + "s)")
        player.start()
    }

    private fun failScenario(error: String, status: String) {
        if (phase != RunPhase.RUNNING) return
        handler.removeCallbacks(scenarioTimeout)
        scenarioPlayer?.interrupt(status, error)
        reports.scenarioEnded(index, status, error,
            if (scenarioStartedAt > 0) SystemClock.elapsedRealtime() - scenarioStartedAt else 0L)
        scenarioPlayer = null
        scenarioFailed = true
        noteFailure(status)
        reports.failure(index)
        reports.targetOutcome(index, status, error)
        addEvent(status + ": " + error)
        // Continue independent cases after an ordinary test failure when still in
        // the target app, but do not restart a failed loop iteration.
        val target = plan?.targets?.getOrNull(index)
        if (status == "FAIL" && target?.executionMode == ExecutionMode.RUN_TEST_CASES &&
            scenarioPosition + 1 < scenarioQueue.size &&
            rootInActiveWindow?.packageName?.toString() == target.packageName) {
            scenarioPosition++
            startNextScenario()
        } else {
            recordUnstartedCases(error)
            advanceToNext()
        }
    }

    private fun recordUnstartedCases(reason: String) {
        val target = plan?.targets?.getOrNull(index) ?: return
        for (position in scenarioPosition + 1 until scenarioQueue.size) {
            val spec = scenarioQueue[position]
            if (!reports.scenarioStarted(index, spec, completedCycles + 1, scenarioIteration,
                    ExecutionPolicy.timeout(spec.totalDurationSec, target.scenarioTimeoutSeconds))) break
            reports.scenarioEnded(index, "NOT_EXECUTED", reason, 0L)
        }
    }

    private fun addEvent(message: String) {
        val event = RunEvent(System.currentTimeMillis(), message)
        events = (events + event).takeLast(60)
        lastMessage = message
        reports.event(message, event)
        publish()
    }

    private fun publish() {
        val target = plan?.targets?.getOrNull(index)
        val previous = RunnerRuntime.state.value
        if (phase == RunPhase.IDLE && previous.phase == RunPhase.FINISHED) {
            RunnerRuntime.publish(previous.copy(accessibilityReady = instance != null))
            return
        }
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
                ((if (target.executionMode == ExecutionMode.RUN_TEST_CASES)
                    (scenarioDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                else (target.durationSeconds * 1000L - appElapsedMs).coerceAtLeast(0L)) + 999L) / 1000L
                else 0L,
            message = lastMessage,
            success = success,
            events = events,
            terminalStatus = terminalStatus
        ))
    }
}
