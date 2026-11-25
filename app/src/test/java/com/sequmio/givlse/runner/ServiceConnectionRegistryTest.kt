package com.sequmio.givlse.runner

import org.junit.Assert.*
import org.junit.Test

class ServiceConnectionRegistryTest {
    @Test fun startsDisconnectedAndTracksOnlyLiveBinding() {
        val registry = ServiceConnectionRegistry<Any>()
        assertNull(registry.connection.value)
        val service = Any()
        assertTrue(registry.attach(service))
        assertSame(service, registry.connection.value)
        assertFalse(registry.attach(service))
        assertTrue(registry.detach(service))
        assertNull(registry.current)
    }

    @Test fun lateUnbindFromOldServiceCannotClearReplacement() {
        val registry = ServiceConnectionRegistry<Any>()
        val old = Any()
        val replacement = Any()
        registry.attach(old)
        registry.attach(replacement)
        assertFalse(registry.detach(old))
        assertSame(replacement, registry.current)
        assertTrue(registry.detach(replacement))
        assertNull(registry.connection.value)
    }

    @Test fun systemReconnectAfterUnbindRestoresLiveState() {
        val registry = ServiceConnectionRegistry<Any>()
        val service = Any()
        registry.attach(service)
        registry.detach(service)
        assertNull(registry.current)
        registry.attach(service)
        assertSame(service, registry.connection.value)
    }
}
