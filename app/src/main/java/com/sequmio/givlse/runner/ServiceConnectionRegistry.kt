package com.sequmio.givlse.runner

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live system binding, not a persisted permission or a test-session status.
 * Only framework lifecycle callbacks may attach an already connected service.
 * A late callback from an older instance cannot detach its replacement.
 * Mutations are confined to Android's main thread by callers.
 */
internal class ServiceConnectionRegistry<T : Any> {
    private val mutable = MutableStateFlow<T?>(null)
    val connection: StateFlow<T?> = mutable.asStateFlow()
    val current: T? get() = mutable.value

    // Keep the framework-bound instance even when a transient readiness probe
    // fails. Only an actual lifecycle callback may discard this reference.
    var known: T? = null
        private set

    fun attach(service: T, ready: Boolean = true): Boolean {
        val changed = known !== service || current !== (if (ready) service else null)
        known = service
        mutable.value = if (ready) service else null
        return changed
    }

    fun updateReadiness(service: T, ready: Boolean): Boolean {
        if (known !== service) return false
        val next = if (ready) service else null
        if (current === next) return false
        mutable.value = next
        return true
    }

    fun detach(service: T): Boolean {
        if (known !== service) return false
        known = null
        mutable.value = null
        return true
    }
}
