package io.github.givlse.appcycletester.runner

import io.github.givlse.appcycletester.model.RunSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Observable in-process runner snapshot. */
object RunnerRuntime {
    private val mutable = MutableStateFlow(RunSnapshot())
    val state: StateFlow<RunSnapshot> = mutable
    fun publish(snapshot: RunSnapshot) { mutable.value = snapshot }
}
