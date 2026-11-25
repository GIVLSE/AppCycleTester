package com.sequmio.givlse.runner

import com.sequmio.givlse.model.RunSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Observable in-process runner snapshot. */
object RunnerRuntime {
    private val mutable = MutableStateFlow(RunSnapshot())
    val state: StateFlow<RunSnapshot> = mutable
    fun publish(snapshot: RunSnapshot) { mutable.value = snapshot }

    // One pending completion, separate from persistent History and live runner state.
    private val result = PendingRunCompletion()
    val completion: StateFlow<RunCompletion?> = result.state
    fun showCompletion(value: RunCompletion) = result.show(value)
    fun dismissCompletion(id: String): Boolean = result.dismiss(id)
    fun clearCompletion() = result.clear()
}
