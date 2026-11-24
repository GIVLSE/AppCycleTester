package com.sequmio.givlse.data

import org.json.JSONArray
import org.json.JSONObject

data class ScenarioStep(
    val action: String,
    val delayBeforeMs: Long,
    val params: JSONObject,
    val selector: JSONObject?,
    val fallback: JSONObject?,
    val optional: Boolean
)
data class ScenarioSpec(
    val packageName: String,
    val name: String,
    val totalDurationSec: Int,
    val loopSteps: Boolean,
    val steps: List<ScenarioStep>
)

/**
 * Strict, data-only JSON scenario. Never executes code loaded from the network.
 * Only known deterministic Android UI actions are accepted.
 */
object ScenarioCodec {
    private val actions = setOf("WAIT","CLICK","SWIPE","INPUT_TEXT","SYSTEM_BACK","ASSERT_EXISTS")

    fun parse(json: String, expectedPackage: String): ScenarioSpec {
        require(json.length in 50..48_000) { "Scenario JSON must be 50–48,000 characters" }
        val data = JSONObject(json)
        require(data.optInt("schema_version") == 1) { "Unsupported scenario schema" }
        val target = data.getJSONObject("target")
        val packageName = target.getString("package_name")
        require(packageName == expectedPackage) { "Scenario package does not match My App" }
        val execution = data.optJSONObject("execution") ?: JSONObject()
        val duration = execution.optInt("total_duration_sec", 300)
        require(duration in 5..7200) { "Scenario duration must be 5–7200 seconds" }
        val rows = data.optJSONArray("steps") ?: JSONArray()
        require(rows.length() in 1..50) { "Specify 1–50 scenario steps" }
        val steps = mutableListOf<ScenarioStep>()
        for (i in 0 until rows.length()) {
            val step = rows.getJSONObject(i)
            val action = step.getString("action").uppercase()
            require(action in actions) { "Unknown action: $action" }
            val delay = step.optLong("delay_before_ms", 0)
            require(delay in 0..30_000) { "Step delay exceeds 30 seconds" }
            val params = step.optJSONObject("params") ?: JSONObject()
            val selector = step.optJSONObject("selector")
            val fallback = step.optJSONObject("fallback_coords")
            when (action) {
                "WAIT" -> require(params.optLong("duration_ms", 0) in 100..30_000) {
                    "WAIT duration must be 100–30,000 ms"
                }
                "CLICK" -> require(validSelector(selector) || validCoordinates(fallback)) {
                    "CLICK requires selector or normalized coordinates"
                }
                "ASSERT_EXISTS" -> require(validSelector(selector)) {
                    "ASSERT_EXISTS requires a UI selector"
                }
                "INPUT_TEXT" -> {
                    require(validSelector(selector)) { "Text input needs a selector" }
                    val value = params.optString("text")
                    require(value.length in 1..1000) { "Text input must be 1–1,000 chars" }
                }
                "SWIPE" -> require(
                    listOf("start_x_percent","start_y_percent","end_x_percent","end_y_percent")
                        .all { key -> params.has(key) && params.optDouble(key) in 0.0..1.0 }
                ) { "SWIPE coordinates must be normalized to [0,1]" }
            }
            steps.add(ScenarioStep(action, delay, params, selector, fallback,
                step.optBoolean("optional", false)))
        }
        // A WAIT-only script does not count as an actual UI test.
        require(steps.any { it.action != "WAIT" }) {
            "A publishable scenario must test or interact with the UI"
        }
        val name = data.optJSONObject("meta")?.optString("name", "Smoke test") ?: "Smoke test"
        return ScenarioSpec(packageName, name.take(120), duration,
            execution.optBoolean("loop_steps", false), steps)
    }

    private fun validSelector(s: JSONObject?): Boolean =
        s != null && listOf("view_id","text","content_desc")
            .any { s.optString(it).isNotBlank() }

    private fun validCoordinates(c: JSONObject?): Boolean =
        c != null && c.has("x_percent") && c.has("y_percent") &&
            c.optDouble("x_percent") in 0.0..1.0 &&
            c.optDouble("y_percent") in 0.0..1.0

    fun starter(packageName: String): String {
        val data = JSONObject()
            .put("schema_version", 1)
            .put("meta", JSONObject().put("name", "My first UI test"))
            .put("target", JSONObject().put("package_name", packageName))
            .put("execution", JSONObject()
                .put("total_duration_sec", 300)
                .put("loop_steps", true))
            .put("steps", JSONArray()
                .put(JSONObject()
                    .put("order", 1).put("action", "WAIT")
                    .put("params", JSONObject().put("duration_ms", 1000)))
                .put(JSONObject()
                    .put("order", 2).put("action", "ASSERT_EXISTS")
                    .put("selector", JSONObject().put("text", "REPLACE_WITH_VISIBLE_TEXT"))
                ))
        return data.toString(2)
    }
}
