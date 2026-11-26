package com.sequmio.givlse.runner

import org.junit.Assert.*
import org.junit.Test

class ResultReturnObservationTest {
    @Test fun lateTokenClearsWarningAfterEarlierUntypedFocus() {
        val o = ResultReturnObservation()
        o.begin(42L)
        assertNull(o.visible(0L, 0L))
        assertEquals(false, o.visible(42L, 0L))
        assertEquals(false, o.deadline(true, true))
    }
    @Test fun successfulReturnWithoutTokenClearsStaleWarning() {
        val o = ResultReturnObservation()
        o.begin(42L)
        assertEquals(false, o.deadline(true, true))
    }
    @Test fun visibleThenLeavesBeforeDeadlineIsNotFailure() {
        val o = ResultReturnObservation()
        o.begin(42L)
        assertNull(o.visible(0L, 0L))
        assertEquals(false, o.deadline(true, false))
    }
    @Test fun failedReturnAndManualNotificationKeepWarning() {
        val o = ResultReturnObservation()
        o.begin(42L)
        assertEquals(true, o.deadline(true, false))
        assertEquals(true, o.visible(0L, 42L))
        assertNull(o.visible(0L, 0L))
        assertEquals(false, o.visible(42L, 0L))
    }
    @Test fun manualBeforeDeadlineDoesNotClearWarning() {
        val o = ResultReturnObservation()
        o.begin(42L)
        assertEquals(true, o.visible(0L, 42L))
        assertEquals(true, o.deadline(true, true))
    }
    @Test fun lockOrUnknownForegroundDoesNotCreateWarning() {
        val o = ResultReturnObservation()
        o.begin(42L)
        assertNull(o.deadline(false, false))
        o.begin(43L)
        assertNull(o.deadline(true, null))
    }
    @Test fun priorRunTokenCannotConfirmNewRun() {
        val o = ResultReturnObservation()
        o.begin(42L)
        o.begin(43L)
        assertNull(o.visible(42L, 0L))
        assertEquals(false, o.visible(43L, 0L))
    }
    @Test fun dialogWindowEvidenceClearsWarningWithoutActivityFocus() {
        val o = ResultReturnObservation()
        o.begin(42L)
        assertEquals(false, o.foregroundObserved())
        assertEquals(false, o.deadline(true, false))
        assertNull(o.visible(0L, 42L))
    }
    @Test fun manualNotificationHasPriorityOverWindowEvidence() {
        val o = ResultReturnObservation()
        o.begin(42L)
        assertEquals(true, o.visible(0L, 42L))
        assertNull(o.foregroundObserved())
        assertEquals(true, o.deadline(true, true))
    }
    @Test fun lateWindowAfterFailureIsNotAutomaticEvidence() {
        val o = ResultReturnObservation()
        o.begin(42L)
        assertEquals(true, o.deadline(true, false))
        assertNull(o.foregroundObserved())
    }
    @Test fun manualNotificationAfterAutomaticSuccessDoesNotRecreateWarning() {
        val o = ResultReturnObservation()
        o.begin(42L)
        assertEquals(false, o.visible(42L, 0L))
        assertNull(o.visible(0L, 42L))
        assertEquals(false, o.deadline(true, false))
    }
}
