package com.sequmio.givlse.runner

import org.junit.Assert.*
import org.junit.Test

class RunRecoveryPolicyTest {
    @Test fun retriesHaveFiniteLimit() {
        assertTrue(RunRecoveryPolicy.canRetry(0))
        assertTrue(RunRecoveryPolicy.canRetry(1))
        assertFalse(RunRecoveryPolicy.canRetry(2))
    }
    @Test fun permissionWindowsNeedManualAction() {
        assertEquals(45_000L, RunRecoveryPolicy.interruptionTimeout("com.android.permissioncontroller"))
        assertEquals(45_000L, RunRecoveryPolicy.interruptionTimeout("com.google.android.permissioncontroller"))
        assertEquals(5_000L, RunRecoveryPolicy.interruptionTimeout("com.example.other"))
    }
}
