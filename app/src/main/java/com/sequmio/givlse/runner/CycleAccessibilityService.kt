package com.sequmio.givlse.runner

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.sequmio.givlse.data.SettingsStore
import com.sequmio.givlse.model.RunEvent
import com.sequmio.givlse.model.RunPhase
import com.sequmio.givlse.model.RunSnapshot
import com.sequmio.givlse.model.TestPlan
import com.sequmio.givlse.model.TestTarget

/**
 * V1 foreground-app runner.
 * User must explicitly enable this service in Accessibility settings.
 * No UI node inspection, gesture injection or screen capture.
 */
class CycleAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: CycleAccessibilityService? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private val store by lazy { SettingsStore(this) }
    private var plan: TestPlan? = null
    private var phase: RunPhase = RunPhase.IDLE
    private var index = 0
    private var completedCycles = 0
    private var elapsedMs = 0L
    private var appElapsedMs = 0L
    private var lastTickMs = 0L
    private var requestedLaunchAt = 0L
    private var launchConfirmed = false
    private var otherWindowAt = 0L
    private var otherWindowPackage = ""
    private var hadFailure = false
    private var successfulResult: Boolean? = null
    private var events: List<RunEvent> = emptyList()
    private var lastMessage = ""

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            if (phase == RunPhase.RUNNING) handler.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        publish()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (phase != RunPhase.RUNNING || event == null) return
        val expected = plan?.targets?.getOrNull(index)?.packageName ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val foreground = event.packageName?.toString() ?: return
        if (foreground == expected) {
            otherWindowAt = 0L
            otherWindowPackage = ""
            if (!launchConfirmed) {
                launchConfirmed = true
                addEvent("Окно $expected обнаружено")
            }
        } else if (launchConfirmed && foreground != otherWindowPackage) {
            otherWindowAt = SystemClock.elapsedRealtime()
            otherWindowPackage = foreground
        }
    }

    override fun onInterrupt() { }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        if (phase == RunPhase.RUNNING || phase == RunPhase.PAUSED)
            finish("Accessibility-служба остановлена", false)
        instance = null
        publish()
        super.onDestroy()
    }

    fun start(newPlan: TestPlan): String? {
        check(Looper.myLooper() == Looper.getMainLooper())
        newPlan.validate()?.let { return it }
        if (phase == RunPhase.RUNNING || phase == RunPhase.PAUSED)
            return "Сначала остановите текущий прогон"
        plan = newPlan.copy(targets = newPlan.targets.toList())
        phase = RunPhase.RUNNING
        index = 0
        completedCycles = 0
        elapsedMs = 0L
        appElapsedMs = 0L
        lastTickMs = SystemClock.elapsedRealtime()
        hadFailure = false
        successfulResult = null
        events = emptyList()
        lastMessage = ""
        addEvent("Старт: ${newPlan.targets.size} приложений, режим ${newPlan.repeatMode}")
        if (!launchWithFallback()) return "Не удалось открыть выбранные приложения"
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 1000L)
        publish()
        return null
    }

    fun pause() {
        if (phase != RunPhase.RUNNING) return
        accumulateTime()
        phase = RunPhase.PAUSED
        handler.removeCallbacks(ticker)
        addEvent("Пауза")
        publish()
    }

    fun resume() {
        if (phase != RunPhase.PAUSED) return
        phase = RunPhase.RUNNING
        lastTickMs = SystemClock.elapsedRealtime()
        addEvent("Продолжение")
        val target = plan?.targets?.getOrNull(index)
        if (target == null || !launch(target)) {
            finish("Не удалось вернуть приложение после паузы", false)
            return
        }
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 1000L)
        publish()
    }

    fun stop() {
        if (phase == RunPhase.RUNNING || phase == RunPhase.PAUSED)
            finish("Остановлено пользователем", false)
    }

    private fun tick() {
        if (phase != RunPhase.RUNNING) return
        accumulateTime()
        val currentPlan = plan ?: return
        if (currentPlan.maxDurationMinutes > 0 &&
            elapsedMs >= currentPlan.maxDurationMinutes * 60_000L) {
            finish("Достигнут общий лимит времени", !hadFailure)
            return
        }
        val target = currentPlan.targets[index]
        if (!launchConfirmed &&
            SystemClock.elapsedRealtime() - requestedLaunchAt >= 8000L) {
            finish("FAIL: не подтверждено открытие окна ${target.label} за 8 секунд", false)
            return
        }
        if (launchConfirmed && otherWindowAt > 0L &&
            SystemClock.elapsedRealtime() - otherWindowAt >= 5000L) {
            finish("FAIL: ${target.label} покинул передний план ($otherWindowPackage)", false)
            return
        }
        if (appElapsedMs >= target.durationSeconds * 1000L) {
            addEvent("Время ${target.label} завершено")
            val next = RunPolicy.next(
                index, currentPlan.targets.size, completedCycles,
                currentPlan.repeatMode, currentPlan.repeatCount
            )
            completedCycles = next.completedCycles
            if (next.index == null) {
                finish(if (hadFailure) "Завершено с ошибками запуска" else "Все циклы завершены", !hadFailure)
                return
            }
            index = next.index
            appElapsedMs = 0L
            launchWithFallback()
        }
        publish()
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

    /** Avoid endless spinning if all selected app launches throw exceptions. */
    private fun launchWithFallback(): Boolean {
        val currentPlan = plan ?: return false
        var tried = 0
        while (tried < currentPlan.targets.size) {
            val target = currentPlan.targets[index]
            if (launch(target)) return true
            tried++
            hadFailure = true
            addEvent("Не удалось запустить ${target.label}")
            val next = RunPolicy.next(
                index, currentPlan.targets.size, completedCycles,
                currentPlan.repeatMode, currentPlan.repeatCount
            )
            completedCycles = next.completedCycles
            if (next.index == null) {
                finish("Цикл завершён; некоторые приложения не запустились", false)
                return false
            }
            index = next.index
            appElapsedMs = 0L
        }
        finish("Ни одно приложение не удалось запустить", false)
        return false
    }

    private fun launch(target: TestTarget): Boolean = try {
        // Never force-stop other apps, only bring the requested one forward.
        val intent = Intent.makeMainActivity(ComponentName(target.packageName, target.activityClass))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        startActivity(intent)
        requestedLaunchAt = SystemClock.elapsedRealtime()
        launchConfirmed = false
        otherWindowAt = 0L
        otherWindowPackage = ""
        addEvent("Запуск ${target.label} (${index + 1}/${plan!!.targets.size})")
        true
    } catch (e: Exception) {
        addEvent("Ошибка запуска ${target.label}: ${e.javaClass.simpleName}")
        false
    }

    private fun finish(reason: String, success: Boolean) {
        handler.removeCallbacks(ticker)
        if (phase == RunPhase.RUNNING) accumulateTime()
        phase = RunPhase.FINISHED
        successfulResult = success
        lastMessage = reason
        addEvent(reason)
        publish()
    }

    private fun addEvent(message: String) {
        val event = RunEvent(System.currentTimeMillis(), message)
        events = (events + event).takeLast(60)
        lastMessage = message
        store.appendHistory(event)
        publish()
    }

    private fun publish() {
        val target = plan?.targets?.getOrNull(index)
        RunnerRuntime.publish(RunSnapshot(
            phase = phase,
            accessibilityReady = instance != null,
            activeApp = if (phase == RunPhase.RUNNING || phase == RunPhase.PAUSED) target?.label else null,
            currentIndex = index,
            completedCycles = completedCycles,
            elapsedSeconds = elapsedMs / 1000L,
            remainingSeconds = if (target != null && (phase == RunPhase.RUNNING || phase == RunPhase.PAUSED))
                ((target.durationSeconds * 1000L - appElapsedMs).coerceAtLeast(0L) + 999L) / 1000L else 0L,
            message = lastMessage,
            success = successfulResult,
            events = events
        ))
    }
}
