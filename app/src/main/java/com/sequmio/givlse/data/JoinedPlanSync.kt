package com.sequmio.givlse.data

import com.sequmio.givlse.model.*

/** Joined plans belong to the tester UID, not the publisher UID.
 * ownerUid is the existing persisted account-scope field for managed targets.
 */
internal object JoinedPlanSync {
    private fun remote(uid: String, app: PublishedApp, installed: InstalledApp): TestTarget? =
        runCatching {
            require(app.status == "published" && app.ownerUid != uid && app.packageName == installed.packageName)
            val cases = ScenarioCodec.parseAll(app.scenarioJson, app.packageName)
            TestTarget(installed.packageName, installed.activityClass, app.name,
                cases.sumOf { it.totalDurationSec }.coerceIn(1, 7200), app.scenarioJson,
                "community", ownerUid = uid)
        }.getOrNull()

    private fun refresh(old: TestTarget, fresh: TestTarget): TestTarget {
        val keys = if (old.scenarioJson.isNullOrBlank()) null else old.selectedCaseKeys
        val mode = if (old.scenarioJson.isNullOrBlank()) fresh.executionMode else old.executionMode
        val duration = if (mode == ExecutionMode.RUN_TEST_CASES) runCatching {
            ScenarioCodec.selectCases(fresh.scenarioJson.orEmpty(), fresh.packageName, keys)
                .sumOf { it.totalDurationSec }.coerceIn(1, 7200)
        }.getOrDefault(fresh.durationSeconds) else old.durationSeconds
        return fresh.copy(executionMode = mode, durationSeconds = duration,
            scenarioTimeoutSeconds = old.scenarioTimeoutSeconds, selectedCaseKeys = keys)
    }

    fun merge(plan: TestPlan, uid: String?, published: List<PublishedApp>?,
        installed: List<InstalledApp>, excluded: Set<String>): TestPlan {
        val available = installed.associateBy { it.packageName }
        val imports = if (uid == null || published == null) emptyMap() else published
            .filterNot { it.packageName in excluded }
            .mapNotNull { app -> available[app.packageName]?.let { remote(uid, app, it) } }
            .associateBy { it.packageName }
        val next = plan.targets.mapNotNull { old ->
            if (old.origin != "community") return@mapNotNull old
            if (uid == null || (old.ownerUid != null && old.ownerUid != uid) ||
                old.packageName in excluded) return@mapNotNull null
            // Legacy unscoped rows can only be adopted using verified membership.
            if (published == null) return@mapNotNull old.takeIf { it.ownerUid == uid }
            val fresh = imports[old.packageName] ?: return@mapNotNull null
            refresh(old, fresh)
        }.toMutableList()
        imports.values.forEach { fresh ->
            if (next.none { it.packageName == fresh.packageName }) next.add(fresh)
        }
        return plan.copy(targets = next)
    }

    fun addExplicit(plan: TestPlan, uid: String, app: PublishedApp, installed: InstalledApp): TestPlan {
        val fresh = remote(uid, app, installed) ?: return plan
        val old = plan.targets.firstOrNull { it.packageName == fresh.packageName }
        // Do not replace an owner's managed target with a community target.
        if (old?.origin == "owner") return plan
        val updated = if (old != null && old.origin == "community" &&
            (old.ownerUid == uid || old.ownerUid == null)) refresh(old, fresh) else fresh
        return plan.copy(targets = if (old == null) plan.targets + updated else
            plan.targets.map { if (it.packageName == updated.packageName) updated else it })
    }
}
