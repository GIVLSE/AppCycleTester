package com.sequmio.givlse.data

import android.content.Context
import android.os.Build
import com.sequmio.givlse.model.TestPlan
import com.sequmio.givlse.data.StepExecutionResult
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class RunReport(
    val id: String,
    val status: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Long,
    val completedCycles: Int,
    val reason: String,
    val rawJson: String
) {
    // Only generate the selected report's CSV when requested.
    val csv: String by lazy { ReportDetails.toCsv(JSONObject(rawJson)) }
}

/** Bounded local structured reports. Sessions interrupted by a process kill never become PASS. */
class RunReportStore(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sequmio_run_reports_v1", Context.MODE_PRIVATE)
    companion object {
        // All report mutations and disk commits are ordered across service instances.
        private val io = Executors.newSingleThreadExecutor()
        private val revisionState = MutableStateFlow(0L)
        val revision: StateFlow<Long> = revisionState
    }

    // Worker-thread confined. Main-thread calls only enqueue small immutable inputs.
    private var activeJson: JSONObject? = null
    private var lastSave = 0L
    private var storageError = false
    private val caseCounts = mutableMapOf<Int, Int>()

    private fun dispatch(block: () -> Unit) {
        io.execute {
            try { block() } catch (e: Exception) {
                storageError = true
                Log.e("SequmioReports", "Report operation failed", e)
            }
        }
    }

    private fun pending() = prefs.getString("active", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    private fun save(json: JSONObject, force: Boolean = false) {
        activeJson = json
        val now = SystemClock.elapsedRealtime()
        if (force || now - lastSave >= 2000L) {
            if (!prefs.edit().putString("active", json.toString()).commit()) {
                storageError = true
                Log.e("SequmioReports", "Active report checkpoint failed")
            }
            lastSave = now
        }
    }
    private fun item(json: JSONObject, index: Int) = json.optJSONArray("targets")?.optJSONObject(index)
    private fun change(block: (JSONObject) -> Unit) {
        dispatch {
            val json = activeJson ?: return@dispatch
            block(json)
            save(json)
        }
    }

    fun recoverInterrupted() {
        dispatch { recoverInterruptedOnWorker() }
    }

    private fun recoverInterruptedOnWorker() {
        val json = pending() ?: return
        val state = json.optString("status")
        if (state == "RUNNING" || state.isBlank()) {
            finalizeActive(json, "BLOCKED", "Process or Accessibility service interrupted")
            json.put("status", "INTERRUPTED")
                .put("endedAt", System.currentTimeMillis())
                .put("reason", "Process or Accessibility service interrupted")
        }
        if (!archive(json)) storageError = true
    }

    fun begin(plan: TestPlan) {
        caseCounts.clear()
        dispatch {
        storageError = false
        recoverInterruptedOnWorker()
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
            .put("reason", "").put("targets", targets).put("events", JSONArray()), force = true)
        }
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
        val count = caseCounts[index] ?: 0
        if (count >= 150) return false
        caseCounts[index] = count + 1
        val startedAt = System.currentTimeMillis()
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
                        .put("startedAt", startedAt).put("endedAt", 0L)
                        .put("expectedSteps", expectedSteps).put("steps", JSONArray()))
                    target.put("scenarios", arr)
                    target.put("activeScenarioIndex", arr.length() - 1)
                }
            }
        }
        return true
    }

    fun targetOutcome(index: Int, status: String, reason: String) {
        change { json -> item(json, index)?.let {
            it.put("outcome", status).put("reason", reason.take(220))
        } }
    }

    fun scenarioEnded(index: Int, status: String, reason: String, actualMs: Long? = null) {
        val endedAt = System.currentTimeMillis()
        change { json ->
            item(json, index)?.let { target ->
                val current = target.optInt("activeScenarioIndex", -1)
                target.optJSONArray("scenarios")?.optJSONObject(current)?.let {
                    if (it.optString("status") == "RUNNING") {
                        ReportDetails.finalizeScenario(it, status, reason, endedAt)
                        actualMs?.let { ms -> it.put("actualSeconds", ms.coerceAtLeast(0L) / 1000L) }
                    }
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

    fun event(message: String, localEvent: com.sequmio.givlse.model.RunEvent? = null) {
        val at = localEvent?.timestampMillis ?: System.currentTimeMillis()
        if (localEvent != null) dispatch { SettingsStore(context).appendHistory(localEvent) }
        change { json ->
            val events = json.optJSONArray("events") ?: JSONArray()
            if (events.length() < 250) {
                events.put(JSONObject().put("at", at).put("message", message))
            }
            json.put("events", events)
        }
    }

    fun progress(seconds: Long, cycles: Int) {
        change { it.put("elapsedSeconds", seconds).put("completedCycles", cycles) }
    }

    fun finish(status: String, reason: String, seconds: Long, cycles: Int,
        onSaved: (Boolean) -> Unit = {}) {
        val endedAt = System.currentTimeMillis()
        // Never hold the main thread waiting for the historical report archive.
        io.execute {
            var saved = false
            try {
                val json = activeJson ?: pending()
                if (json != null) {
                    finalizeActive(json, status, reason)
                    json.put("status", status).put("reason", reason)
                        .put("endedAt", endedAt)
                        .put("elapsedSeconds", seconds).put("completedCycles", cycles)
                    // Keep a terminal checkpoint if archiving fails or process dies.
                    save(json, force = true)
                    saved = archive(json)
                    if (saved && status == "PASS")
                        RunProgressStore(context).recordPassedApps(json.optJSONArray("targets") ?: JSONArray())
                }
            } catch (e: Exception) {
                Log.e("SequmioReports", "Final report failed", e)
                saved = false
            } finally {
                activeJson = null
                onSaved(saved && !storageError)
            }
        }
    }

    private fun archive(json: JSONObject): Boolean {
        val arr = JSONArray().put(json)
        val old = runCatching { JSONArray(prefs.getString("history", "[]")) }.getOrDefault(JSONArray())
        val id = json.optString("id")
        for (i in 0 until minOf(old.length(), 39)) {
            old.optJSONObject(i)?.takeIf { it.optString("id") != id }?.let { arr.put(it) }
        }
        val saved = prefs.edit().remove("active").putString("history", arr.toString()).commit()
        if (!saved) {
            Log.e("SequmioReports", "History archive commit failed")
            return false
        }
        revisionState.value += 1
        if (CloudUploadPrefs(context).enabled) {
            runCatching { CloudRunUploader.enqueue(context, id) }
                .onFailure { Log.e("SequmioReports", "Upload scheduling failed", it) }
        }
        return true
    }

    fun recent(): List<RunReport> {
        val arr = runCatching { JSONArray(prefs.getString("history", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { json ->
                // Normalize old terminal reports, without fabricating missing step data.
                if (json.optString("status") != "RUNNING") {
                    val targets = json.optJSONArray("targets") ?: JSONArray()
                    for (j in 0 until targets.length()) {
                        val cases = targets.optJSONObject(j)?.optJSONArray("scenarios") ?: JSONArray()
                        for (z in 0 until cases.length()) {
                            cases.optJSONObject(z)?.let { c ->
                                if (c.optString("status") == "RUNNING")
                                    c.put("status", "INCOMPLETE").put("reason", json.optString("reason"))
                            }
                        }
                    }
                }
                RunReport(
                    json.optString("id"), json.optString("status"),
                    json.optLong("startedAt"), json.optLong("endedAt"),
                    json.optLong("elapsedSeconds"), json.optInt("completedCycles"),
                    json.optString("reason"), json.toString()
                )
            }
        }
    }


}
