package com.sequmio.givlse.data

import org.junit.Assert.*
import org.junit.Test

class QaAnalyticsTest {
    private fun row(id: String, status: String, run: String = "run", tester: String = "qa",
        step: List<QaStep> = emptyList()) = QaRun(id,"pkg",status,"1","Phone",33,"SMOKE","Case","1",1000L,tester,step,run)
    @Test fun sessionsAreNotCaseExecutions() {
        val rows = listOf(row("a","PASS"),row("b","FAIL"),row("c","PASS",tester="other"))
        assertEquals(2, QaAnalytics.sessions(rows).size)
        assertEquals(1, QaAnalytics.sessions(rows).count { it.status == "FAIL" })
    }
    @Test fun passRateExcludesIncomplete() {
        assertEquals(50.0, QaAnalytics.rate(listOf(row("a","PASS"),row("b","FAIL"),row("c","INCOMPLETE")))!!,0.01)
        assertNull(QaAnalytics.rate(listOf(row("a","STOPPED"))))
    }
    @Test fun coverageUsesVersion() {
        val rows = listOf(row("a","PASS"))
        assertEquals(1 to 2, QaAnalytics.coverage(rows, listOf(QaCaseKey("SMOKE","Case","1"),QaCaseKey("SMOKE","Case","2"))))
    }
    @Test fun groupedFailureCountsCasesNotRepeatedStepRows() {
        val step = QaStep(4,"ASSERT_EXISTS","FAIL",5L,"missing","Case")
        assertEquals(2, QaAnalytics.failures(listOf(row("a","FAIL",step=listOf(step,step)),row("b","FAIL",step=listOf(step)))).single().second.size)
    }
    @Test fun csvEscapesFormulaAndJsonKeepsDetails() {
        val rows = listOf(row("a","FAIL",step=listOf(QaStep(1,"CLICK","FAIL",2L,"=formula","Case"))))
        assertTrue(QaAnalytics.export(rows,true).contains("'=formula"))
        assertTrue(QaAnalytics.export(rows,false).contains("=formula"))
    }
    @Test fun mixedNeedsComparableConfiguration() {
        assertTrue(QaAnalytics.mixed(listOf(row("a","PASS"),row("b","FAIL"))))
        assertFalse(QaAnalytics.mixed(listOf(row("a","PASS"),row("b","FAIL").copy(androidApi=36))))
    }
}
