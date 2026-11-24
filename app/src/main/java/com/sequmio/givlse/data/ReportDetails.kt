package com.sequmio.givlse.data

import org.json.JSONArray
import org.json.JSONObject

/** Semantically useful AR/ER without selector strings, input text or captured screen values. */
object StepOutcome {
    fun expected(action: String): String = when (action) {
        "ASSERT_EXISTS", "ASSERT_SCREEN" -> "Expected element visible"
        "ASSERT_NOT_EXISTS" -> "Expected element absent"
        "ASSERT_TEXT" -> "Text matches configured expectation"
        "ASSERT_ENABLED" -> "Expected element enabled"
        "CLICK" -> "Click accepted"
        "SWIPE" -> "Swipe completed"
        "INPUT_TEXT" -> "Test input accepted"
        "SYSTEM_BACK" -> "Back action accepted"
        "WAIT" -> "Configured wait completed"
        else -> "Action completed"
    }
    fun passed(action: String): String = expected(action)
}

object ReportDetails {
    fun finalizeScenario(s: JSONObject, status: String, reason: String, now: Long) {
        val steps = s.optJSONArray("steps") ?: JSONArray()
        val existing = (0 until steps.length()).map { steps.optJSONObject(it)?.optInt("stepIndex") }.toSet()
        val expected = s.optJSONArray("expectedSteps") ?: JSONArray()
        for (i in 0 until expected.length()) {
            val row = expected.optJSONObject(i) ?: continue
            if (row.optInt("stepIndex") in existing) continue
            steps.put(JSONObject().put("stepIndex", row.optInt("stepIndex"))
                .put("action", row.optString("action")).put("status", "NOT_EXECUTED")
                .put("scenarioName", s.optString("name")).put("suite", s.optString("suite"))
                .put("scenarioVersion", s.optInt("version", 1))
                .put("durationMs", 0).put("at", now).put("expectedResult", row.optString("expectedResult"))
                .put("actualResult", "Not executed").put("errorCode", status)
                .put("message", reason.take(220)))
        }
        val actualStatus = if (status == "PASS" && (0 until steps.length()).any {
                steps.optJSONObject(it)?.optString("status") == "NOT_EXECUTED" }) "BLOCKED" else status
        s.put("steps", steps).put("status", actualStatus).put("reason", reason.take(220))
            .put("endedAt", now).put("actualSeconds",
                ((now - s.optLong("startedAt", now)).coerceAtLeast(0L)) / 1000L)
        s.remove("expectedSteps")
    }

    fun toCsv(json: JSONObject): String {
        fun cell(value: Any?): String {
            val raw = value?.toString().orEmpty()
            // Avoid spreadsheet formula injection from user-provided app/case names.
            val safe = if (raw.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'" + raw else raw
            return "\"" + safe.replace("\"", "\"\"") + "\""
        }
        val header = "session_id,session_status,cycle_number,iteration_number,app_name,package_name,app_version,test_case_name,test_case_version,scenario_status,estimated_duration_seconds,actual_duration_seconds,timeout_seconds,step_index,step_action,expected_result,actual_result,step_status,step_duration_ms,error_code,error_message,started_at,ended_at"
        val lines = mutableListOf(header)
        val targets = json.optJSONArray("targets") ?: JSONArray()
        for (i in 0 until targets.length()) {
            val t = targets.optJSONObject(i) ?: continue
            val cases = t.optJSONArray("scenarios") ?: JSONArray()
            fun row(s: JSONObject?, step: JSONObject?) {
                val status = s?.optString("status")?.let { if (it == "RUNNING") "INCOMPLETE" else it }
                    ?: t.optString("outcome", json.optString("status"))
                val start = s?.optLong("startedAt") ?: json.optLong("startedAt")
                val end = s?.optLong("endedAt")?.takeIf { it > 0 } ?: json.optLong("endedAt")
                val reason = s?.optString("reason").orEmpty().ifBlank { t.optString("reason").ifBlank {
                    if (status != "PASS") json.optString("reason") else "" } }
                val actual = if (s == null) t.optLong("foregroundSeconds") else
                    s.optLong("actualSeconds", ((end-start).coerceAtLeast(0L))/1000L)
                lines.add(listOf(json.optString("id"), json.optString("status"),
                    s?.opt("cycle"), s?.opt("iteration"), t.optString("label"), t.optString("packageName"),
                    t.optString("appVersion"), s?.optString("name"), s?.opt("version"), status,
                    s?.opt("estimatedSeconds") ?: t.opt("durationSeconds"), actual, s?.opt("timeoutSeconds"),
                    step?.opt("stepIndex"), step?.optString("action") ?: "SUMMARY",
                    step?.optString("expectedResult"), step?.optString("actualResult"), step?.optString("status"),
                    step?.opt("durationMs"), step?.optString("errorCode") ?: status.takeIf { it != "PASS" },
                    step?.optString("message") ?: reason, start, end
                ).joinToString(",") { cell(it) })
            }
            if (cases.length() == 0) row(null, null)
            for (j in 0 until cases.length()) {
                val s = cases.optJSONObject(j) ?: continue
                row(s, null)
                val rows = s.optJSONArray("steps") ?: JSONArray()
                for (z in 0 until rows.length()) rows.optJSONObject(z)?.let { row(s, it) }
            }
        }
        return lines.joinToString("\n")
    }
}
