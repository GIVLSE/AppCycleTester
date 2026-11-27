package com.sequmio.givlse.data

import com.sequmio.givlse.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JoinedPlanSyncTest {
    private val pkg = "com.example.app"
    private val installed = InstalledApp(pkg, "Main", "App")
    private fun scenario(name: String, seconds: Int) = JSONObject().put("schema_version", 1)
        .put("meta", JSONObject().put("name", name).put("version", 1))
        .put("target", JSONObject().put("package_name", pkg))
        .put("execution", JSONObject().put("total_duration_sec", seconds))
        .put("steps", JSONArray().put(JSONObject().put("action", "ASSERT_EXISTS")
            .put("selector", JSONObject().put("text", "Ready")))).toString()
    private val app = PublishedApp(pkg, "App", "", "publisher", "Publisher", "", "", "",
        ScenarioCodec.compose(listOf(scenario("First", 15), scenario("Second", 20))), "published")
    private fun sync(plan: TestPlan = TestPlan(), uid: String? = "tester",
        published: List<PublishedApp>? = listOf(app), excluded: Set<String> = emptySet(),
        devices: List<InstalledApp> = listOf(installed)) =
        JoinedPlanSync.merge(plan, uid, published, devices, excluded)

    @Test fun freshInstallRestoresJoinedCases() {
        val target = sync().targets.single()
        assertEquals("community", target.origin)
        assertEquals("tester", target.ownerUid)
        assertEquals(35, target.durationSeconds)
        assertEquals(2, ScenarioCodec.parseAll(target.scenarioJson!!, pkg).size)
    }
    @Test fun refreshPreservesOptionsAndAvoidsDuplicates() {
        val old = sync().targets.single().copy(executionMode = ExecutionMode.LOOP_TEST_CASES,
            durationSeconds = 90, scenarioTimeoutSeconds = 80, selectedCaseKeys = listOf("selection"))
        val initial = TestPlan(listOf(old), RepeatMode.COUNT, 4, 7)
        val revised = app.copy(scenarioJson = scenario("Revised", 22))
        val result = sync(initial, published = listOf(revised))
        val target = result.targets.single()
        assertEquals(revised.scenarioJson, target.scenarioJson)
        assertEquals(old.executionMode, target.executionMode)
        assertEquals(90, target.durationSeconds)
        assertEquals(80, target.scenarioTimeoutSeconds)
        assertEquals(old.selectedCaseKeys, target.selectedCaseKeys)
        assertEquals(initial.repeatMode, result.repeatMode)
        assertEquals(initial.repeatCount, result.repeatCount)
        assertEquals(initial.maxDurationMinutes, result.maxDurationMinutes)
    }
    @Test fun notInstalledAndUnpublishedDoNotImport() {
        assertTrue(sync(devices = emptyList()).targets.isEmpty())
        assertTrue(sync(sync(), devices = emptyList()).targets.isEmpty())
        assertTrue(sync(sync(), published = emptyList()).targets.isEmpty())
        assertTrue(sync(published = listOf(app.copy(status = "draft"))).targets.isEmpty())
    }
    @Test fun manualExclusionAndExplicitReadd() {
        assertTrue(sync(sync(), excluded = setOf(pkg)).targets.isEmpty())
        assertEquals(1, JoinedPlanSync.addExplicit(TestPlan(), "tester", app, installed).targets.size)
    }
    @Test fun offlineRetainsSameAccountButNotOtherAccount() {
        val plan = sync()
        assertEquals(plan, sync(plan, published = null))
        assertTrue(sync(plan, uid = "another", published = null).targets.isEmpty())
        assertTrue(sync(plan, uid = null, published = null).targets.isEmpty())
    }
    @Test fun switchingAccountDoesNotInheritOldSettings() {
        val old = sync().targets.single().copy(durationSeconds = 120, executionMode = ExecutionMode.FIXED_DURATION)
        val fresh = sync(TestPlan(listOf(old)), uid = "another").targets.single()
        assertEquals("another", fresh.ownerUid)
        assertEquals(35, fresh.durationSeconds)
        assertEquals(ExecutionMode.RUN_TEST_CASES, fresh.executionMode)
    }
    @Test fun legacyRowsOnlyAdoptVerifiedMembership() {
        val old = sync().targets.single().copy(ownerUid = null)
        val initial = TestPlan(listOf(old))
        assertTrue(sync(initial, published = null).targets.isEmpty())
        assertTrue(sync(initial, published = emptyList()).targets.isEmpty())
        assertEquals("tester", sync(initial).targets.single().ownerUid)
    }
    @Test fun ownerAndLocalTargetsAreNotOverwritten() {
        val local = TestTarget(pkg, "Main", "Local", 30, scenario("Local", 30))
        val owner = local.copy(origin = "owner", ownerUid = "tester")
        assertEquals(local, sync(TestPlan(listOf(local))).targets.single())
        assertEquals(owner, sync(TestPlan(listOf(owner))).targets.single())
        assertEquals(owner, JoinedPlanSync.addExplicit(TestPlan(listOf(owner)), "tester", app, installed).targets.single())
    }
    @Test fun accountCannotJoinOwnApp() {
        assertTrue(sync(uid = "publisher").targets.isEmpty())
    }
}
