package com.sequmio.givlse.data

import com.sequmio.givlse.model.ExecutionMode
import com.sequmio.givlse.model.ExecutionPolicy
import com.sequmio.givlse.model.TestTarget
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class PlanCaseUpdatesTest {
    private val pkg = "com.example.app"
    private fun scenario(name: String, seconds: Int, version: Int): String =
        JSONObject().put("schema_version", 1)
            .put("meta", JSONObject().put("name", name).put("version", version))
            .put("target", JSONObject().put("package_name", pkg))
            .put("execution", JSONObject().put("total_duration_sec", seconds))
            .put("steps", JSONArray().put(JSONObject().put("action", "ASSERT_EXISTS")
                .put("selector", JSONObject().put("text", "Ready")))).toString()

    @Test fun editedCaseKeepsSelectionAndManualTimeout() {
        val old = scenario("First", 15, 1)
        val other = scenario("Other", 20, 1)
        val revised = scenario("Renamed", 25, 2)
        val raw = ScenarioCodec.compose(listOf(old, other))
        val key = ScenarioCodec.caseKey(0, ScenarioCodec.parse(old, pkg))
        val target = TestTarget(pkg, "Main", "App", 15, raw, "local",
            ExecutionMode.RUN_TEST_CASES, 120, listOf(key))
        val result = PlanCaseUpdates.replaceLocal(target, pkg, old, revised)
        val selected = ScenarioCodec.selectCases(result.scenarioJson!!, pkg, result.selectedCaseKeys)
        assertEquals("Renamed", selected.single().name)
        assertEquals(2, selected.single().version)
        assertEquals(25, result.durationSeconds)
        assertEquals(120, result.scenarioTimeoutSeconds)
        assertEquals(2, ScenarioCodec.parseAll(result.scenarioJson!!, pkg).size)
    }

    @Test fun communityAndFixedDurationAreNotReconfigured() {
        val old = scenario("First", 15, 1)
        val revised = scenario("First", 25, 2)
        val community = TestTarget(pkg, "Main", "App", 15, old, "community")
        assertEquals(community, PlanCaseUpdates.replaceLocal(community, pkg, old, revised))
        val fixed = community.copy(origin = "local", executionMode = ExecutionMode.FIXED_DURATION)
        val result = PlanCaseUpdates.replaceLocal(fixed, pkg, old, revised)
        assertEquals(15, result.durationSeconds)
        assertEquals(ExecutionMode.FIXED_DURATION, result.executionMode)
        assertEquals(2, ScenarioCodec.parse(result.scenarioJson!!, pkg).version)
    }

    @Test fun duplicateOrInvalidReplacementDoesNotSilentlyChangePlan() {
        val old = scenario("First", 15, 1)
        val duplicate = TestTarget(pkg, "Main", "App", 30, ScenarioCodec.compose(listOf(old, old)))
        assertEquals(duplicate, PlanCaseUpdates.replaceLocal(duplicate, pkg, old, scenario("New", 20, 2)))
        val target = TestTarget(pkg, "Main", "App", 15, old)
        assertEquals(target, PlanCaseUpdates.replaceLocal(target, pkg, old, "{}"))
        assertEquals(60, ExecutionPolicy.timeout(15, 0))
    }
}
