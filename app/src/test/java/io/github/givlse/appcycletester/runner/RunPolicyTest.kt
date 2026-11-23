package io.github.givlse.appcycletester.runner

import io.github.givlse.appcycletester.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunPolicyTest {
    @Test fun onceMovesBetweenAppsThenStops() {
        assertEquals(RunPolicy.Next(1, 0), RunPolicy.next(0, 2, 0, RepeatMode.ONCE, 1))
        val end = RunPolicy.next(1, 2, 0, RepeatMode.ONCE, 1)
        assertNull(end.index)
        assertEquals(1, end.completedCycles)
    }
    @Test fun countedModeStopsOnRequestedCycle() {
        assertEquals(RunPolicy.Next(0, 1), RunPolicy.next(1, 2, 0, RepeatMode.COUNT, 3))
        assertEquals(RunPolicy.Next(0, 2), RunPolicy.next(1, 2, 1, RepeatMode.COUNT, 3))
        assertEquals(RunPolicy.Next(null, 3), RunPolicy.next(1, 2, 2, RepeatMode.COUNT, 3))
    }
    @Test fun endlessNeverTerminates() {
        assertEquals(RunPolicy.Next(0, 120), RunPolicy.next(0, 1, 119, RepeatMode.ENDLESS, 1))
    }
    @Test(expected = IllegalArgumentException::class)
    fun emptyPlanIsRejected() {
        RunPolicy.next(0, 0, 0, RepeatMode.ONCE, 1)
    }
}
