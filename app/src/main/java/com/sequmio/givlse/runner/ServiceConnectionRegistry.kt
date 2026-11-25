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

    fun attach(service: T): Boolean {
        if (current === service) return false
        mutable.value = service
        return true
    }

    fun detach(service: T): Boolean {
        if (current !== service) return false
        mutable.value = null
        return true
    }
}
