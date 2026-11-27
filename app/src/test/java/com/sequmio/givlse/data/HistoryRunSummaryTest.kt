package com.sequmio.givlse.data

import com.sequmio.givlse.model.AppPoolOutcome
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HistoryRunSummaryTest {
    private fun target(name: String, status: String) = JSONObject()
        .put("packageName", "com.example." + name.lowercase())
        .put("label", name).put("outcome", status)
    private fun report(id: String, targets: JSONArray = JSONArray(), events: JSONArray = JSONArray(),
        status: String = "FINISHED", reason: String = "All planned cycles processed") =
        RunReport(id, status, 1000L, 5000L, 4L, 1, reason,
            JSONObject().put("targets", targets).put("events", events).toString())

    @Test fun finishedRunCanContainFailedAndErrorApps() {
        val summary = HistoryRunSummary.fromReport(report("one", JSONArray()
            .put(target("A", "PASSED")).put(target("B", "FAILED"))
            .put(target("C", "ERROR")).put(target("D", "NOT_EXECUTED"))))
        assertEquals("FINISHED", summary.status)
        assertEquals(listOf(AppPoolOutcome.PASSED, AppPoolOutcome.FAILED,
            AppPoolOutcome.ERROR, AppPoolOutcome.NOT_EXECUTED), summary.apps.map { it.outcome })
        assertEquals("4 apps · 1 Passed · 1 Failed · 1 Error · 1 Not run", summary.summaryLabel)
    }

    @Test fun blockedRunDoesNotChangeCompletedAppVerdict() {
        val summary = HistoryRunSummary.fromReport(report("one", JSONArray()
            .put(target("A", "PASSED")).put(target("B", "NOT_EXECUTED")),
            status = "BLOCKED", reason = "Runner interrupted"))
        assertEquals("BLOCKED", summary.status)
        assertEquals(AppPoolOutcome.PASSED, summary.apps.first().outcome)
        assertEquals(AppPoolOutcome.NOT_EXECUTED, summary.apps.last().outcome)
    }

    @Test fun keepsAllFifteenAppResultsInPlanOrder() {
        val targets = JSONArray()
        repeat(15) { targets.put(target("App$it", "PASSED")) }
        val summary = HistoryRunSummary.fromReport(report("large", targets))
        assertEquals(15, summary.apps.size)
        assertEquals((0..14).map { "App$it" }, summary.apps.map { it.label })
        assertEquals("15 apps · 15 Passed", summary.summaryLabel)
    }

    @Test fun eventLogIsScopedToSelectedRun() {
        fun event(time: Long, text: String) = JSONObject().put("at", time).put("message", text)
        val first = HistoryRunSummary.fromReport(report("first", events =
            JSONArray().put(event(1000L, "Start first")).put(event(2000L, "Finish first"))))
        val second = HistoryRunSummary.fromReport(report("second", events =
            JSONArray().put(event(3000L, "Start second"))))
        assertEquals(listOf("Start first", "Finish first"), first.events.map { it.message })
        assertEquals(listOf("Start second"), second.events.map { it.message })
        assertEquals(1000L, first.events.first().timestampMillis)
    }

    @Test fun legacyCaseEvidenceStillDeterminesAppOutcome() {
        val legacy = JSONObject().put("packageName", "com.example.old")
            .put("scenarios", JSONArray().put(JSONObject().put("status", "FAIL")))
        val summary = HistoryRunSummary.fromReport(report("old", JSONArray().put(legacy)))
        assertEquals("com.example.old", summary.apps.single().label)
        assertEquals(AppPoolOutcome.FAILED, summary.apps.single().outcome)
        assertTrue(summary.events.isEmpty())
    }

    @Test fun malformedReportDoesNotInventAppsOrEvents() {
        val summary = HistoryRunSummary.fromReport(report("bad").copy(rawJson = "{"))
        assertEquals("FINISHED", summary.status)
        assertTrue(summary.apps.isEmpty())
        assertTrue(summary.events.isEmpty())
    }
}
