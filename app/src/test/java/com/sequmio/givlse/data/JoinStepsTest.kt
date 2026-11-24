package com.sequmio.givlse.data

import org.junit.Assert.*
import org.junit.Test

class JoinStepsTest {
    @Test fun allThreeRequirementsAreNecessary() {
        for (group in listOf(false, true))
            for (optIn in listOf(false, true))
                for (installed in listOf(false, true))
                    assertEquals(group && optIn && installed, JoinSteps(group, optIn).ready(installed))
    }
    @Test fun savedStepsRoundTripIncludingEmptyPending() {
        for (value in 0..3) assertEquals(value, JoinSteps.decode(value).encode())
    }
    @Test fun pendingSortsBeforeOtherListings() {
        assertTrue(JoinSteps.priority(true) < JoinSteps.priority(false))
    }
}
