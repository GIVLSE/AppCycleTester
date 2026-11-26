package com.sequmio.givlse.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SmartScenarioStepsTest {
    @Test fun legacyTimeoutIsFiveSeconds() {
        assertEquals(5000L, ScenarioCodec.assertionTimeoutMs(JSONObject()))
        assertEquals(15000L, ScenarioCodec.assertionTimeoutMs(JSONObject().put("timeout_ms",15000)))
    }
    @Test fun pickerPreservesUnrelatedProperties() {
        val old = JSONObject("""{"action":"ASSERT_TEXT","optional":true,"params":{"ignore_case":true,"timeout_ms":15000},"custom":"legacy"}""")
        val next = SmartScenarioSteps.replaceElement(old, JSONObject().put("view_id","pkg:id/status"),
            "Status", .5, .5, "Connected")
        assertTrue(next.getBoolean("optional"))
        assertEquals("legacy", next.getString("custom"))
        assertTrue(next.getJSONObject("params").getBoolean("ignore_case"))
        assertEquals(15000L, next.getJSONObject("params").getLong("timeout_ms"))
        assertEquals("Connected",next.getJSONObject("params").getString("expected"))
        assertEquals("pkg:id/status",next.getJSONObject("selector").getString("view_id"))
    }
    @Test fun blankNewClickCannotSilentlyTapCenter() {
        assertEquals("Choose an element",SmartScenarioSteps.warning(SmartScenarioSteps.newStep("CLICK")))
    }
    @Test fun recordedTextPlaceholderNeedsEditing() {
        val step = SmartScenarioSteps.newStep("INPUT_TEXT")
            .put("selector",JSONObject().put("view_id","pkg:id/input"))
        step.getJSONObject("params").put("text","REPLACE_WITH_TEST_TEXT")
        assertEquals("Enter a safe test value",SmartScenarioSteps.warning(step))
    }
    @Test fun reorderingPreservesLegacyStepParams() {
        val root = JSONObject()
        SmartScenarioSteps.writeRows(root,listOf(
            """{"action":"WAIT","params":{"duration_ms":1500},"order":8}""",
            """{"action":"ASSERT_TEXT","params":{"expected":"Ready","ignore_case":true},"order":2}"""))
        assertEquals(1,root.getJSONArray("steps").getJSONObject(0).getInt("order"))
        assertTrue(root.getJSONArray("steps").getJSONObject(1).getJSONObject("params").getBoolean("ignore_case"))
    }
    @Test fun swipePresetsRoundTrip() {
        for (dir in listOf("Up","Down","Left","Right")) assertEquals(dir,SmartScenarioSteps.direction(SmartScenarioSteps.swipe(dir)))
    }
}
