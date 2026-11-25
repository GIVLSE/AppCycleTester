package com.sequmio.givlse.data

import com.sequmio.givlse.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OwnerPlanSyncTest {
    private val pkg = "com.example.app"
    private val installed = InstalledApp(pkg, "Main", "App")
    private fun scenario(name: String, seconds: Int) = JSONObject().put("schema_version", 1)
        .put("meta", JSONObject().put("name", name).put("version", 1))
        .put("target", JSONObject().put("package_name", pkg))
        .put("execution", JSONObject().put("total_duration_sec", seconds))
        .put("steps", JSONArray().put(JSONObject().put("action", "ASSERT_EXISTS")
            .put("selector", JSONObject().put("text", "Ready")))).toString()
    private val draft = MyAppDraft("App", pkg, scenarioJson =
        ScenarioCodec.compose(listOf(scenario("First", 15), scenario("Second", 20), scenario("Third", 25))))
    private fun sync(plan: TestPlan = TestPlan(), uid: String? = "owner",
        published: List<MyAppDraft> = listOf(draft), excluded: Set<String> = emptySet(),
        devices: List<InstalledApp> = listOf(installed)) =
        OwnerPlanSync.merge(plan, uid, published, devices, excluded)

    @Test fun freshInstallRestoresAllCases() {
        val target = sync().targets.single()
        assertEquals("owner", target.origin)
        assertEquals("owner", target.ownerUid)
        assertEquals(ExecutionMode.RUN_TEST_CASES, target.executionMode)
        assertEquals(3, ScenarioCodec.parseAll(target.scenarioJson!!, pkg).size)
        assertEquals(60, target.durationSeconds)
    }
    @Test fun migratesDefaultLocalWithoutDuplicate() {
        val initial = TestPlan(listOf(TestTarget(pkg, "Main", "App")), RepeatMode.COUNT, 4, 7)
        val result = sync(initial)
        assertEquals(1, result.targets.size)
        assertEquals(ExecutionMode.RUN_TEST_CASES, result.targets.single().executionMode)
        assertEquals(initial.repeatMode, result.repeatMode)
        assertEquals(4, result.repeatCount)
        assertEquals(7, result.maxDurationMinutes)
    }
    @Test fun refreshPreservesModeTimeoutAndSelection() {
        val first = sync().targets.single()
        val key = ScenarioCodec.caseKey(1, ScenarioCodec.parseAll(draft.scenarioJson, pkg)[1])
        val custom = first.copy(executionMode = ExecutionMode.LOOP_TEST_CASES,
            durationSeconds = 120, scenarioTimeoutSeconds = 90, selectedCaseKeys = listOf(key))
        val revised = draft.copy(scenarioJson = ScenarioCodec.compose(listOf(
            scenario("First", 16), scenario("Second", 22), scenario("Third", 27))))
        val target = sync(TestPlan(listOf(custom)), published = listOf(revised)).targets.single()
        assertEquals(custom.executionMode, target.executionMode)
        assertEquals(120, target.durationSeconds)
        assertEquals(90, target.scenarioTimeoutSeconds)
        assertEquals(listOf(key), target.selectedCaseKeys)
        assertEquals(revised.scenarioJson, target.scenarioJson)
    }
    @Test fun localRecordedCaseIsNotOverwrittenAutomatically() {
        val local = TestTarget(pkg, "Main", "App", 15, scenario("Local", 15))
        assertEquals(local, sync(TestPlan(listOf(local))).targets.single())
    }
    @Test fun respectsRemovalAndRequiresInstalledPublishedApp() {
        assertTrue(sync(excluded = setOf(pkg)).targets.isEmpty())
        assertTrue(sync(devices = emptyList()).targets.isEmpty())
        assertTrue(sync(sync(), uid = null, published = emptyList()).targets.isEmpty())
        assertTrue(sync(sync(), published = emptyList()).targets.isEmpty())
        assertEquals(1, OwnerPlanSync.addExplicit(TestPlan(), "owner", draft, installed).targets.size)
    }
    @Test fun manualFixedModeIsKeptAfterRefresh() {
        val old = TestTarget(pkg, "Main", "App", 120, executionMode = ExecutionMode.FIXED_DURATION)
        val result = sync(TestPlan(listOf(old))).targets.single()
        assertEquals(ExecutionMode.FIXED_DURATION, result.executionMode)
        assertEquals(120, result.durationSeconds)
        assertEquals("owner", result.origin)
    }
}
