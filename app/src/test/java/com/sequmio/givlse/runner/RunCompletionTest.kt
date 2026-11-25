package com.sequmio.givlse.runner

import com.sequmio.givlse.model.TestTarget
import org.junit.Assert.*
import org.junit.Test

class RunCompletionTest {
    private fun accumulator() = RunCompletionAccumulator(listOf(
        TestTarget("a", "Main", "A"), TestTarget("b", "Main", "B"),
        TestTarget("c", "Main", "C")))

    @Test fun mixedResultsKeepUnexecutedAndFailureAcrossCycles() {
        val a = accumulator()
        a.record(0, "PASS", "")
        a.record(1, "FAIL", "Assertion failed")
        a.record(1, "PASS", "")
        val result = a.finish("FAIL", "Completed with app failures", 21, 1)
        assertEquals(listOf("PASS", "FAIL", "NOT_EXECUTED"), result.apps.map { it.status })
        assertEquals("Assertion failed", result.apps[1].reason)
    }

    @Test fun dismissalConsumesOnlyMatchingSession() {
        val pending = PendingRunCompletion()
        val first = accumulator().finish("PASS", "Finished", 10, 1)
        val second = accumulator().finish("STOPPED", "Stopped", 4, 0)
        pending.show(first)
        assertTrue(pending.dismiss(first.id))
        assertNull(pending.state.value)
        pending.show(second)
        assertFalse(pending.dismiss(first.id))
        assertEquals(second, pending.state.value)
        pending.clear()
        assertNull(pending.state.value)
    }

    @Test fun stoppedAppDoesNotMarkUnvisitedAppsFailed() {
        val a = accumulator()
        a.record(0, "PASS", "")
        a.record(1, "STOPPED", "Stopped by user")
        val result = a.finish("STOPPED", "Stopped by user", 8, 0)
        assertEquals(listOf("PASS", "STOPPED", "NOT_EXECUTED"), result.apps.map { it.status })
    }
}
