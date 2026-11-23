package com.sequmio.givlse.data

import android.content.Context
import com.sequmio.givlse.model.TestPlan
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
class RunReportStore(context: Context) {
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
            targets.put(JSONObject().put("label", t.label)
                .put("packageName", t.packageName)
                .put("durationSeconds", t.durationSeconds)
                .put("launches", 0).put("confirmed", 0)
                .put("retries", 0).put("failures", 0).put("foregroundSeconds", 0))
        }
        save(JSONObject()
            .put("schema", 1).put("id", UUID.randomUUID().toString())
            .put("status", "RUNNING").put("startedAt", System.currentTimeMillis())
            .put("endedAt", 0L).put("elapsedSeconds", 0L)
            .put("completedCycles", 0).put("repeatMode", plan.repeatMode.name)
            .put("reason", "").put("targets", targets).put("events", JSONArray()))
    }

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
        json.put("status", status).put("reason", reason)
            .put("endedAt", System.currentTimeMillis())
            .put("elapsedSeconds", seconds).put("completedCycles", cycles)
        archive(json)
    }

    private fun archive(json: JSONObject) {
        val arr = JSONArray().put(json)
        val old = runCatching { JSONArray(prefs.getString("history", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until minOf(old.length(), 39)) old.optJSONObject(i)?.let { arr.put(it) }
        prefs.edit().remove("active").putString("history", arr.toString()).apply()
    }

    fun recent(): List<RunReport> {
        val arr = runCatching { JSONArray(prefs.getString("history", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { json ->
                RunReport(
                    json.optString("id"), json.optString("status"),
                    json.optLong("startedAt"), json.optLong("endedAt"),
                    json.optLong("elapsedSeconds"), json.optInt("completedCycles"),
                    json.optString("reason"), json.toString(2), toCsv(json)
                )
            }
        }
    }

    private fun toCsv(json: JSONObject): String {
        fun cell(value: Any?): String = "\"" + (value?.toString() ?: "").replace("\"", "\"\"") + "\""
        val columns = listOf("session_id", "status", "started_at", "ended_at",
            "elapsed_seconds", "completed_cycles", "app_name", "package_name",
            "target_seconds", "launches", "confirmed", "retries", "failures", "foreground_seconds")
        val lines = mutableListOf(columns.joinToString(","))
        val entries = json.optJSONArray("targets") ?: JSONArray()
        for (i in 0 until entries.length()) {
            val t = entries.optJSONObject(i) ?: continue
            lines.add(listOf(
                json.optString("id"), json.optString("status"),
                json.optLong("startedAt"), json.optLong("endedAt"),
                json.optLong("elapsedSeconds"), json.optInt("completedCycles"),
                t.optString("label"), t.optString("packageName"),
                t.optInt("durationSeconds"), t.optInt("launches"),
                t.optInt("confirmed"), t.optInt("retries"),
                t.optInt("failures"), t.optLong("foregroundSeconds")
            ).joinToString(",") { cell(it) })
        }
        return lines.joinToString("\n")
    }
}
