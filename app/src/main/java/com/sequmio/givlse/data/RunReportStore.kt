package com.sequmio.givlse.data

import android.content.Context
import android.os.Build
import com.sequmio.givlse.model.TestPlan
import com.sequmio.givlse.data.StepExecutionResult
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class RunReport(
    val id: String,
    val status: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Long,
    val completedCycles: Int,
    val reason: String,
    val rawJson: String,
    val csv: String
)

/** Bounded local structured reports. Sessions interrupted by a process kill never become PASS. */
class RunReportStore(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sequmio_run_reports_v1", Context.MODE_PRIVATE)
    private fun pending() = prefs.getString("active", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    private fun save(json: JSONObject) { prefs.edit().putString("active", json.toString()).apply() }
    private fun item(json: JSONObject, index: Int) = json.optJSONArray("targets")?.optJSONObject(index)
    private fun change(block: (JSONObject) -> Unit) {
        val json = pending() ?: return
        block(json)
        save(json)
    }

    fun recoverInterrupted(): Boolean {
        val json = pending() ?: return false
        finalizeActive(json, "BLOCKED", "Process or Accessibility service interrupted")
        json.put("status", "INTERRUPTED")
            .put("endedAt", System.currentTimeMillis())
            .put("reason", "Process or Accessibility service interrupted")
        archive(json)
        return true
    }

    fun begin(plan: TestPlan) {
        recoverInterrupted()
        val targets = JSONArray()
        plan.targets.forEach { t ->
            val version = installedVersion(t.packageName)
            targets.put(JSONObject().put("label", t.label)
                .put("appVersion", version.first)
                .put("appVersionCode", version.second)
                .put("packageName", t.packageName)
                .put("durationSeconds", t.durationSeconds)
                .put("origin", t.origin)
                .put("executionMode", t.executionMode.name)
                .put("scenarioName", t.scenarioJson?.let {
                    runCatching { ScenarioCodec.parseAll(it, t.packageName)
                        .joinToString(" + ") { spec -> spec.name } }.getOrDefault("")
                } ?: "")
                .put("suite", t.scenarioJson?.let {
                    runCatching { ScenarioCodec.parseAll(it, t.packageName)
                        .joinToString(",") { spec -> spec.suite } }.getOrDefault("")
                } ?: "")
                .put("scenarioVersions", t.scenarioJson?.let {
                    runCatching { ScenarioCodec.parseAll(it, t.packageName)
                        .joinToString(",") { spec -> spec.version.toString() } }.getOrDefault("")
                } ?: "")
                .put("steps", JSONArray())
                .put("scenarios", JSONArray())
                .put("activeScenarioIndex", -1)
                .put("launches", 0).put("confirmed", 0)
                .put("retries", 0).put("failures", 0).put("foregroundSeconds", 0))
        }
        save(JSONObject()
            .put("schema", 2).put("id", UUID.randomUUID().toString())
            .put("status", "RUNNING").put("startedAt", System.currentTimeMillis())
            .put("endedAt", 0L).put("elapsedSeconds", 0L)
            .put("completedCycles", 0).put("repeatMode", plan.repeatMode.name)
            .put("reason", "").put("targets", targets).put("events", JSONArray()))
    }

    @Suppress("DEPRECATION")
    private fun installedVersion(packageName: String): Pair<String, Long> =
        runCatching {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(
                packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)
            ) else pm.getPackageInfo(packageName, 0)
            (info.versionName ?: "unknown").take(80) to
                if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
                else info.versionCode.toLong()
        }.getOrDefault("unknown" to 0L)

    private fun inc(index: Int, name: String) {
        change { json -> item(json, index)?.let { it.put(name, it.optInt(name) + 1) } }
    }
    fun launch(index: Int) = inc(index, "launches")
    fun confirmed(index: Int) = inc(index, "confirmed")
    fun retry(index: Int) = inc(index, "retries")
    fun failure(index: Int) = inc(index, "failures")

    fun foregroundSeconds(index: Int, seconds: Long) {
        if (seconds <= 0) return
        change { json ->
            item(json, index)?.let { row ->
                row.put("foregroundSeconds", row.optLong("foregroundSeconds") + seconds)
            }
        }
    }

    fun scenarioStarted(index: Int, spec: ScenarioSpec, cycle: Int, iteration: Int,
        timeout: Int): Boolean {
        var accepted = false
        change { json ->
            item(json, index)?.let { target ->
                val arr = target.optJSONArray("scenarios") ?: JSONArray()
                if (arr.length() < 150) {
                    val expectedSteps = JSONArray()
                    spec.steps.forEachIndexed { i, step ->
                        expectedSteps.put(JSONObject().put("stepIndex", i + 1)
                            .put("action", step.action)
                            .put("expectedResult", StepOutcome.expected(step.action)))
                    }
                    arr.put(JSONObject()
                        .put("name", spec.name).put("suite", spec.suite).put("version", spec.version)
                        .put("cycle", cycle).put("iteration", iteration)
                        .put("estimatedSeconds", spec.totalDurationSec).put("timeoutSeconds", timeout)
                        .put("status", "RUNNING").put("reason", "")
                        .put("startedAt", System.currentTimeMillis()).put("endedAt", 0L)
                        .put("expectedSteps", expectedSteps).put("steps", JSONArray()))
                    target.put("scenarios", arr)
                    target.put("activeScenarioIndex", arr.length() - 1)
                    accepted = true
                }
            }
        }
        return accepted
    }

    fun targetOutcome(index: Int, status: String, reason: String) {
        change { json -> item(json, index)?.let {
            it.put("outcome", status).put("reason", reason.take(220))
        } }
    }

    fun scenarioEnded(index: Int, status: String, reason: String) {
        change { json ->
            item(json, index)?.let { target ->
                val current = target.optInt("activeScenarioIndex", -1)
                target.optJSONArray("scenarios")?.optJSONObject(current)?.let {
                    if (it.optString("status") == "RUNNING")
                        ReportDetails.finalizeScenario(it, status, reason, System.currentTimeMillis())
                }
                target.put("activeScenarioIndex", -1)
            }
        }
    }

    private fun finalizeActive(json: JSONObject, status: String, reason: String) {
        val targets = json.optJSONArray("targets") ?: JSONArray()
        for (i in 0 until targets.length()) {
            val target = targets.optJSONObject(i) ?: continue
            val cases = target.optJSONArray("scenarios") ?: JSONArray()
            for (j in 0 until cases.length()) {
                val scenario = cases.optJSONObject(j) ?: continue
                if (scenario.optString("status") == "RUNNING")
                    ReportDetails.finalizeScenario(scenario,
                        if (status == "PASS") "BLOCKED" else status, reason, System.currentTimeMillis())
            }
            target.put("activeScenarioIndex", -1)
        }
    }

    /** Save safe metadata only. Each scenario owns its rows; legacy aggregate is bounded. */
    fun step(index: Int, result: StepExecutionResult) {
        change { json ->
            item(json, index)?.let { target ->
                val row = JSONObject()
                    .put("scenarioName", result.scenarioName).put("suite", result.suite)
                    .put("scenarioVersion", result.scenarioVersion).put("stepIndex", result.stepIndex)
                    .put("action", result.action).put("status", result.status)
                    .put("durationMs", result.durationMs).put("message", result.message)
                    .put("at", result.at).put("expectedResult", result.expectedResult)
                    .put("actualResult", result.actualResult).put("errorCode", result.errorCode)
                val steps = target.optJSONArray("steps") ?: JSONArray()
                if (steps.length() < 200) steps.put(row)
                target.put("steps", steps)
                val current = target.optInt("activeScenarioIndex", -1)
                target.optJSONArray("scenarios")?.optJSONObject(current)?.let { scenario ->
                    val ownSteps = scenario.optJSONArray("steps") ?: JSONArray()
                    if (ownSteps.length() < 150) ownSteps.put(row)
                    scenario.put("steps", ownSteps)
                }
            }
        }
    }

    fun event(message: String) {
        change { json ->
            val events = json.optJSONArray("events") ?: JSONArray()
            if (events.length() < 250) {
                events.put(JSONObject().put("at", System.currentTimeMillis()).put("message", message))
            }
            json.put("events", events)
        }
    }

    fun progress(seconds: Long, cycles: Int) {
        change { it.put("elapsedSeconds", seconds).put("completedCycles", cycles) }
    }

    fun finish(status: String, reason: String, seconds: Long, cycles: Int) {
        val json = pending() ?: return
        finalizeActive(json, status, reason)
        json.put("status", status).put("reason", reason)
            .put("endedAt", System.currentTimeMillis())
            .put("elapsedSeconds", seconds).put("completedCycles", cycles)
        if (status == "PASS") {
            RunProgressStore(context).recordPassedApps(
                json.optJSONArray("targets") ?: JSONArray()
            )
        }
        archive(json)
    }

    private fun archive(json: JSONObject) {
        val arr = JSONArray().put(json)
        val old = runCatching { JSONArray(prefs.getString("history", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until minOf(old.length(), 39)) old.optJSONObject(i)?.let { arr.put(it) }
        prefs.edit().remove("active").putString("history", arr.toString()).commit()
        if (CloudUploadPrefs(context).enabled) {
            runCatching { CloudRunUploader.enqueue(context, json.optString("id")) }
        }
    }

    fun recent(): List<RunReport> {
        val arr = runCatching { JSONArray(prefs.getString("history", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { json ->
                RunReport(
                    json.optString("id"), json.optString("status"),
                    json.optLong("startedAt"), json.optLong("endedAt"),
                    json.optLong("elapsedSeconds"), json.optInt("completedCycles"),
                    json.optString("reason"), json.toString(2), ReportDetails.toCsv(json)
                )
            }
        }
    }


}
