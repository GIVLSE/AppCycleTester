package com.sequmio.givlse.runner

import com.sequmio.givlse.model.TestTarget
import com.sequmio.givlse.model.AppPoolOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class AppRunCompletion(val packageName: String, val label: String,
    val status: String = "NOT_EXECUTED", val reason: String = "")

data class RunCompletion(val id: String, val status: String, val reason: String,
    val elapsedSeconds: Long, val completedCycles: Int, val apps: List<AppRunCompletion>)

/** Main-thread summary of actual target outcomes, independent of asynchronous report IO. */
internal class RunCompletionAccumulator(targets: List<TestTarget>) {
    private val id = UUID.randomUUID().toString()
    private val apps = targets.map { AppRunCompletion(it.packageName, it.label) }.toMutableList()

    fun record(index: Int, status: String, reason: String) {
        val old = apps.getOrNull(index) ?: return
        // A later successful cycle cannot hide an earlier failure.
        val incoming = AppPoolOutcome.fromExecution(status)
        if (incoming.ordinal >= AppPoolOutcome.fromExecution(old.status).ordinal)
            apps[index] = old.copy(status = incoming.name, reason = reason)
    }

    fun finish(status: String, reason: String, elapsedSeconds: Long, cycles: Int) =
        RunCompletion(id, status, reason, elapsedSeconds, cycles, apps.toList())
}

/** Dismiss by exact completion ID; Activity recreation does not replay consumed results. */
internal class PendingRunCompletion {
    private val mutable = MutableStateFlow<RunCompletion?>(null)
    val state: StateFlow<RunCompletion?> = mutable.asStateFlow()
    fun show(value: RunCompletion) { mutable.value = value }
    fun dismiss(id: String): Boolean {
        if (mutable.value?.id != id) return false
        mutable.value = null
        return true
    }
    fun clear() { mutable.value = null }
}
