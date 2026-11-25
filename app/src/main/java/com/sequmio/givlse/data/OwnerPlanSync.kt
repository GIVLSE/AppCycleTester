package com.sequmio.givlse.data

import com.sequmio.givlse.model.ExecutionMode
import com.sequmio.givlse.model.InstalledApp
import com.sequmio.givlse.model.TestPlan
import com.sequmio.givlse.model.TestTarget

/** Owner catalog plans are distinct from explicit local tests and joined community plans. */
internal object OwnerPlanSync {
    private fun remote(uid: String, draft: MyAppDraft, installed: InstalledApp): TestTarget? =
        runCatching {
            require(draft.packageName == installed.packageName)
            val cases = ScenarioCodec.parseAll(draft.scenarioJson, draft.packageName)
            TestTarget(installed.packageName, installed.activityClass, draft.appName,
                cases.sumOf { it.totalDurationSec }.coerceIn(1, 7200), draft.scenarioJson,
                "owner", ownerUid = uid)
        }.getOrNull()

    private fun refresh(old: TestTarget, fresh: TestTarget): TestTarget {
        // A blank default local plan is the exact migration case from 1.2.10.
        val defaultLocal = old.origin == "local" && old.scenarioJson.isNullOrBlank() &&
            old.executionMode == ExecutionMode.FIXED_DURATION && old.durationSeconds == 300
        val mode = if (defaultLocal) fresh.executionMode else old.executionMode
        val keys = if (old.scenarioJson.isNullOrBlank()) null else old.selectedCaseKeys
        // Retain selection: if a published case disappeared, Runner will ask to review
        // the plan instead of silently executing a different set of cases.
        val duration = if (mode == ExecutionMode.RUN_TEST_CASES) runCatching {
            ScenarioCodec.selectCases(fresh.scenarioJson.orEmpty(), fresh.packageName, keys)
                .sumOf { it.totalDurationSec }.coerceIn(1, 7200)
        }.getOrDefault(fresh.durationSeconds) else old.durationSeconds
        return fresh.copy(executionMode = mode, durationSeconds = duration,
            scenarioTimeoutSeconds = old.scenarioTimeoutSeconds, selectedCaseKeys = keys)
    }

    fun merge(plan: TestPlan, uid: String?, published: List<MyAppDraft>,
        installed: List<InstalledApp>, excluded: Set<String>): TestPlan {
        val available = installed.associateBy { it.packageName }
        val known = published.associateBy { it.packageName }
        val imports = if (uid == null) emptyMap() else known.values
            .filterNot { it.packageName in excluded }
            .mapNotNull { draft -> available[draft.packageName]?.let { remote(uid, draft, it) } }
            .associateBy { it.packageName }
        val next = plan.targets.mapNotNull { old ->
            if (old.origin == "owner" && (old.ownerUid != uid || old.packageName !in imports))
                return@mapNotNull null
            val fresh = imports[old.packageName] ?: return@mapNotNull old
            if (old.origin == "owner" || (old.origin == "local" && old.scenarioJson.isNullOrBlank()))
                refresh(old, fresh) else old
        }.toMutableList()
        imports.values.forEach { fresh ->
            if (next.none { it.packageName == fresh.packageName }) next.add(fresh)
        }
        return plan.copy(targets = next)
    }

    fun addExplicit(plan: TestPlan, uid: String, draft: MyAppDraft, installed: InstalledApp): TestPlan {
        val fresh = remote(uid, draft, installed) ?: return plan
        val old = plan.targets.firstOrNull { it.packageName == fresh.packageName }
        // Explicit selection of My App switches a local scenario to published cases.
        val updated = if (old == null) fresh else if (old.origin == "owner" && old.ownerUid == uid ||
            old.origin == "local" && old.scenarioJson.isNullOrBlank()) refresh(old, fresh) else fresh
        return plan.copy(targets = if (old == null) plan.targets + updated else
            plan.targets.map { if (it.packageName == updated.packageName) updated else it })
    }
}
