package com.sequmio.givlse.model

data class InstalledApp(val packageName: String, val activityClass: String, val label: String)
data class TestTarget(
    val packageName: String, val activityClass: String, val label: String,
    val durationSeconds: Int = 300,
    val scenarioJson: String? = null
)
enum class RepeatMode { ONCE, COUNT, ENDLESS }
data class TestPlan(
    val targets: List<TestTarget> = emptyList(),
    val repeatMode: RepeatMode = RepeatMode.ONCE,
    val repeatCount: Int = 3,
    val maxDurationMinutes: Int = 0
) {
    fun validate(): String? = when {
        targets.isEmpty() -> "Добавьте хотя бы одно приложение"
        targets.any { it.durationSeconds !in 1..7200 } -> "Время приложения: от 1 секунды до 120 минут"
        repeatCount !in 1..999 -> "Количество циклов: от 1 до 999"
        maxDurationMinutes !in 0..10080 -> "Общий лимит: от 0 до 10080 минут"
        else -> null
    }
}
enum class RunPhase { IDLE, RUNNING, PAUSED, FINISHED }
data class RunEvent(val timestampMillis: Long, val message: String)
data class RunSnapshot(
    val phase: RunPhase = RunPhase.IDLE,
    val accessibilityReady: Boolean = false,
    val activeApp: String? = null,
    val currentIndex: Int = 0,
    val completedCycles: Int = 0,
    val elapsedSeconds: Long = 0,
    val remainingSeconds: Long = 0,
    val message: String = "",
    val success: Boolean? = null,
    val events: List<RunEvent> = emptyList()
)
