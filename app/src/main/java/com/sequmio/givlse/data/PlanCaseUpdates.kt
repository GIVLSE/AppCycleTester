package com.sequmio.givlse.data

import com.sequmio.givlse.model.ExecutionMode
import com.sequmio.givlse.model.TestTarget
import org.json.JSONObject

/** Update an explicitly included local case without replacing unrelated or community cases. */
object PlanCaseUpdates {
    fun replaceLocal(target: TestTarget, packageName: String, oldRaw: String, newRaw: String): TestTarget {
        if (target.origin != "local" || target.packageName != packageName ||
            target.scenarioJson.isNullOrBlank() || oldRaw.isBlank()) return target
        return runCatching {
            val cases = ScenarioCodec.split(target.scenarioJson).toMutableList()
            val matches = cases.indices.filter {
                JSONObject(cases[it]).toString() == JSONObject(oldRaw).toString()
            }
            // Identical duplicated legacy cases cannot be safely disambiguated.
            if (matches.size != 1) return target
            val position = matches.single()
            val oldSpec = ScenarioCodec.parse(cases[position], packageName)
            val newSpec = ScenarioCodec.parse(newRaw, packageName)
            cases[position] = newRaw
            val raw = ScenarioCodec.compose(cases)
            val keys = target.selectedCaseKeys?.map {
                if (it == ScenarioCodec.caseKey(position, oldSpec))
                    ScenarioCodec.caseKey(position, newSpec) else it
            }
            target.copy(scenarioJson = raw, selectedCaseKeys = keys,
                durationSeconds = if (target.executionMode == ExecutionMode.RUN_TEST_CASES)
                    ScenarioCodec.selectCases(raw, packageName, keys)
                        .sumOf { it.totalDurationSec }.coerceIn(1, 7200)
                else target.durationSeconds)
        }.getOrDefault(target)
    }
}
