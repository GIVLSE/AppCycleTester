package com.sequmio.givlse.data

import java.time.Instant
import java.time.ZoneOffset
import org.json.JSONArray
import org.json.JSONObject

data class QaCaseKey(val suite: String, val name: String, val version: String)
internal data class QaFailureKey(val test: QaCaseKey, val step: Int, val action: String)
internal data class QaSession(val key: Pair<String,String>, val rows: List<QaRun>) {
    val endedAt get() = rows.maxOf { it.endedAt }
    val status get() = when {
        rows.any { it.status == "FAIL" } -> "FAIL"
        rows.all { it.status == "PASS" } -> "PASS"
        else -> "INCOMPLETE"
    }
}
internal object QaAnalytics {
    fun caseKey(run: QaRun) = QaCaseKey(run.suite, run.scenario, run.versions)
    fun build(run: QaRun) = if (run.buildCode > 0) "${run.build} (${run.buildCode})" else run.build
    fun device(run: QaRun) = run.model.ifBlank { "Unknown model" } + " · API " + run.androidApi
    fun sessions(rows: List<QaRun>) = rows.groupBy {
        it.testerUid to it.runId.ifBlank { "legacy:" + it.id }
    }.map { (key, values) -> QaSession(key, values.sortedBy { it.endedAt }) }
        .sortedByDescending { it.endedAt }
    fun pass(rows: List<QaRun>) = rows.count { it.status == "PASS" }
    fun fail(rows: List<QaRun>) = rows.count { it.status == "FAIL" }
    fun rate(rows: List<QaRun>): Double? {
        val count = pass(rows) + fail(rows)
        return if (count == 0) null else 100.0 * pass(rows) / count
    }
    fun failures(rows: List<QaRun>): List<Pair<QaFailureKey,List<QaRun>>> =
        rows.flatMap { run -> run.steps.filter { it.status == "FAIL" }
            .map { QaFailureKey(caseKey(run), it.step, it.action) to run }.distinct() }
            .groupBy({ it.first }, { it.second }).entries
            .sortedByDescending { it.value.size }.map { it.key to it.value }
    fun days(rows: List<QaRun>) = rows.groupBy {
        Instant.ofEpochMilli(it.endedAt).atZone(ZoneOffset.UTC).toLocalDate().toString()
    }.toSortedMap()
    fun coverage(rows: List<QaRun>, published: List<QaCaseKey>?): Pair<Int,Int>? =
        published?.distinct()?.let { cases ->
            cases.count { key -> rows.any { caseKey(it) == key } } to cases.size
        }
    // Same case/version, build and device configuration, not evidence of a root cause.
    fun mixed(rows: List<QaRun>): Boolean = rows.groupBy { Triple(caseKey(it), build(it), device(it)) }
        .values.any { pass(it) > 0 && fail(it) > 0 }
    fun export(rows: List<QaRun>, csv: Boolean): String {
        if (!csv) return JSONArray(rows.map { r ->
            JSONObject().put("reportId", r.id).put("runId", r.runId).put("testerUid", r.testerUid)
                .put("appId", r.appId).put("status", r.status).put("sessionStatus", r.sessionStatus)
                .put("build", r.build).put("buildCode", r.buildCode).put("model", r.model)
                .put("androidApi", r.androidApi).put("suite", r.suite).put("scenario", r.scenario)
                .put("scenarioVersion", r.versions).put("startedAt", r.startedAt)
                .put("endedAt", r.endedAt).put("elapsedSeconds", r.elapsedSeconds)
                .put("steps", JSONArray(r.steps.map { step ->
                    JSONObject().put("step", step.step).put("action", step.action)
                        .put("status", step.status).put("durationMs", step.ms)
                        .put("message", step.detail)
                }))
        }).toString(2)
        fun cell(value: Any?): String {
            val raw = value?.toString().orEmpty()
            val safe = if (raw.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'" + raw else raw
            return "\"" + safe.replace("\"", "\"\"") + "\""
        }
        return buildString {
            appendLine("report_id,run_id,tester_uid,app_id,case_status,session_status,build,build_code,model,android_api,suite,scenario,scenario_version,started_at,ended_at,elapsed_seconds,step,action,step_status,step_ms,message")
            rows.forEach { r ->
                val prefix = listOf(r.id,r.runId,r.testerUid,r.appId,r.status,r.sessionStatus,r.build,
                    r.buildCode,r.model,r.androidApi,r.suite,r.scenario,r.versions,r.startedAt,r.endedAt,r.elapsedSeconds)
                val steps: List<QaStep?> = r.steps.ifEmpty { listOf<QaStep>() }.map { it }
                    .ifEmpty { listOf(null) }
                steps.forEach { step ->
                    appendLine((prefix + listOf(step?.step,step?.action,step?.status,step?.ms,step?.detail))
                        .joinToString(",") { cell(it) })
                }
            }
        }
    }
}
