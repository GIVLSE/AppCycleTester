package com.sequmio.givlse.runner

import com.sequmio.givlse.model.*
import com.sequmio.givlse.data.ReportDetails
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RunStatusSeparationTest {
    @Test fun allProcessedRunFinishesDespiteFailedAndErroredPools() {
        val acc = RunCompletionAccumulator(listOf(TestTarget("a","Main","A"),
            TestTarget("b","Main","B"), TestTarget("c","Main","C")))
        acc.record(0,"FAIL","Assertion mismatch")
        acc.record(1,"TIMEOUT","Scenario timeout")
        acc.record(2,"PASS","")
        val result = acc.finish(RunStatus.FINISHED.name,"All planned cycles processed",30,1)
        assertEquals("FINISHED", result.status)
        assertEquals(listOf("FAILED","ERROR","PASSED"), result.apps.map { it.status })
    }

    @Test fun unvisitedAppDoesNotInheritSessionFailure() {
        val target = JSONObject().put("label","Never visited")
        assertEquals(AppPoolOutcome.NOT_EXECUTED, ReportDetails.appOutcome(target))
        val report = JSONObject().put("status","BLOCKED").put("targets",JSONArray().put(target))
        val csv = ReportDetails.toCsv(report)
        assertTrue(csv.contains("app_status"))
        assertTrue(csv.contains("NOT_EXECUTED"))
    }

    @Test fun legacyHistoryAndNewLifecycleRemainDistinct() {
        assertEquals("FINISHED", RunStatus.fromStored("FAIL","Completed with app failures"))
        assertEquals("BLOCKED", RunStatus.fromStored("FAIL","Unknown termination"))
        assertEquals("CANCELLED", RunStatus.fromStored("STOPPED","Stopped by user"))
        assertEquals("BLOCKED", RunStatus.fromStored("INTERRUPTED","Process killed"))
        assertEquals("FINISHED", RunStatus.fromStored("FINISHED","All planned cycles processed"))
    }

    @Test fun confirmedFailureIsNotMaskedByLaterPassOrError() {
        val target = JSONObject().put("outcome","PASSED").put("scenarios",JSONArray()
            .put(JSONObject().put("status","FAIL")).put(JSONObject().put("status","TIMEOUT")))
        assertEquals(AppPoolOutcome.FAILED,ReportDetails.appOutcome(target))
    }
}
