package com.sequmio.givlse.runner

import com.sequmio.givlse.model.*
import com.sequmio.givlse.data.ReportDetails
import com.sequmio.givlse.data.StepOutcome
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AdaptiveExecutionTest {
    @Test fun timeoutUsesRecordingAsEstimate() {
        assertEquals(60, ExecutionPolicy.timeout(15, 0))
        assertEquals(150, ExecutionPolicy.timeout(60, 0))
        assertEquals(300, ExecutionPolicy.timeout(15, 300))
        assertEquals(14430, ExecutionPolicy.timeout(7200, 0))
    }
    @Test fun defaultModesAndExplicitLoop() {
        assertEquals(ExecutionMode.FIXED_DURATION, TestTarget("p","a","l").executionMode)
        assertEquals(ExecutionMode.RUN_TEST_CASES, TestTarget("p","a","l",scenarioJson="{}").executionMode)
        assertFalse(ExecutionPolicy.repeat(ExecutionMode.RUN_TEST_CASES, 1000, 300))
        assertTrue(ExecutionPolicy.repeat(ExecutionMode.LOOP_TEST_CASES, 299999, 300))
        assertFalse(ExecutionPolicy.repeat(ExecutionMode.LOOP_TEST_CASES, 300000, 300))
    }
    @Test fun terminalReportFillsUnexecutedSteps() {
        val s = JSONObject().put("status","RUNNING").put("startedAt",1000L)
            .put("name","Smoke").put("expectedSteps",JSONArray()
                .put(JSONObject().put("stepIndex",1).put("action","CLICK"))
                .put(JSONObject().put("stepIndex",2).put("action","ASSERT_TEXT")))
            .put("steps",JSONArray().put(JSONObject().put("stepIndex",1).put("status","PASS")))
        ReportDetails.finalizeScenario(s,"TIMEOUT","Timeout",16000L)
        assertEquals("TIMEOUT",s.getString("status"))
        assertEquals(15L,s.getLong("actualSeconds"))
        assertEquals("NOT_EXECUTED",s.getJSONArray("steps").getJSONObject(1).getString("status"))
        assertFalse(s.has("expectedSteps"))
        val json = JSONObject().put("id","test").put("status","TIMEOUT").put("targets",
            JSONArray().put(JSONObject().put("label","App").put("scenarios",JSONArray().put(s))))
        val csv = ReportDetails.toCsv(json)
        assertTrue(csv.contains("NOT_EXECUTED"))
        assertTrue(csv.contains("TIMEOUT"))
        assertEquals(4,csv.lines().size)
    }
    @Test fun severityIsNotOverwrittenByLaterSuccess() {
        assertEquals("TIMEOUT", ExecutionPolicy.combine("TIMEOUT","PASS"))
        assertEquals("FAIL", ExecutionPolicy.combine("BLOCKED","FAIL"))
        assertEquals("Text matches configured expectation",StepOutcome.expected("ASSERT_TEXT"))
    }
}
