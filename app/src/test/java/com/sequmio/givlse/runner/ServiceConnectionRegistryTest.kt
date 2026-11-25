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

    @Test fun transientProbeFailureKeepsInstanceForRetry() {
        val registry = ServiceConnectionRegistry<Any>()
        val service = Any()
        registry.attach(service)
        assertTrue(registry.updateReadiness(service, false))
        assertNull(registry.current)
        assertSame(service, registry.known)
        assertTrue(registry.updateReadiness(service, true))
        assertSame(service, registry.current)
    }

    @Test fun actualUnbindClearsEvenUnavailableCandidate() {
        val registry = ServiceConnectionRegistry<Any>()
        val service = Any()
        registry.attach(service, ready = false)
        assertSame(service, registry.known)
        assertTrue(registry.detach(service))
        assertNull(registry.known)
        assertFalse(registry.updateReadiness(service, true))
    }

    @Test fun oldProbeCannotRestoreReplacedService() {
        val registry = ServiceConnectionRegistry<Any>()
        val old = Any()
        val new = Any()
        registry.attach(old, ready = false)
        registry.attach(new)
        assertFalse(registry.updateReadiness(old, true))
        assertSame(new, registry.current)
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
