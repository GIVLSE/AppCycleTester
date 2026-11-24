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
    val steps: List<ScenarioStep>,
    val suite: String = "SMOKE",
    val version: Int = 1,
    val regressionApproved: Boolean = false
)

/**
 * Strict, data-only JSON scenario. Never executes code loaded from the network.
 * Only known deterministic Android UI actions are accepted.
 */
object ScenarioCodec {
    val allowedSuites = setOf("SMOKE", "REGRESSION", "FEATURE")
    val assertionActions = setOf("ASSERT_EXISTS", "ASSERT_NOT_EXISTS",
        "ASSERT_TEXT", "ASSERT_ENABLED", "ASSERT_SCREEN")
    private val actions = setOf("WAIT","CLICK","SWIPE","INPUT_TEXT","SYSTEM_BACK") +
        assertionActions

    /** One JSON scenario or a bundle of independent smoke/regression/feature scenarios. */
    fun split(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val root = JSONObject(raw)
        val scenarios = root.optJSONArray("scenarios")
        if (scenarios == null) return listOf(root.toString())
        return (0 until scenarios.length()).map { scenarios.getJSONObject(it).toString() }
    }

    fun compose(scenarios: List<String>): String {
        require(scenarios.size in 1..12) { "Specify 1–12 test cases" }
        if (scenarios.size == 1) return JSONObject(scenarios[0]).toString()
        val arr = JSONArray()
        scenarios.forEach { arr.put(JSONObject(it)) }
        return JSONObject().put("schema_version", 1)
            .put("scenarios", arr).toString()
    }

    fun parseAll(raw: String, expectedPackage: String): List<ScenarioSpec> {
        require(raw.length <= 48_000) { "Test suite exceeds 48 KB" }
        val scenarios = split(raw)
        require(scenarios.size in 1..12) { "Specify 1–12 test cases" }
        val parsed = scenarios.map { parse(it, expectedPackage) }
        require(parsed.sumOf { it.steps.size } <= 150) { "Too many combined test steps" }
        return parsed
    }

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
                in assertionActions -> {
                    require(validSelector(selector) &&
                        selector?.optString("text") != "REPLACE_WITH_VISIBLE_TEXT") {
                        "$action requires a real UI selector, not a template placeholder"
                    }
                    if (action == "ASSERT_TEXT") {
                        val expected = params.optString("expected")
                        require(expected.isNotBlank() && expected.length <= 200 &&
                            expected != "EXPECTED_TEXT") {
                            "ASSERT_TEXT needs an expected UI value"
                        }
                    }
                }
                "INPUT_TEXT" -> {
                    require(validSelector(selector)) { "Text input needs a selector" }
                    val value = params.optString("text")
                    require(value.length in 1..1000 && value != "REPLACE_WITH_TEST_TEXT") {
                        "Replace recorded text placeholder with safe test data"
                    }
                }
                "SWIPE" -> require(
                    listOf("start_x_percent","start_y_percent","end_x_percent","end_y_percent")
                        .all { key -> params.has(key) && params.optDouble(key) in 0.0..1.0 }
                ) { "SWIPE coordinates must be normalized to [0,1]" }
            }
            steps.add(ScenarioStep(action, delay, params, selector, fallback,
                step.optBoolean("optional", false)))
        }
        require(steps.any { it.action in assertionActions }) {
            "Add an expected-result assertion; actions alone are not a QA test"
        }
        val meta = data.optJSONObject("meta") ?: JSONObject()
        val name = meta.optString("name", "Smoke test").take(120)
        val suite = meta.optString("suite", "SMOKE").uppercase()
        val version = meta.optInt("version", 1)
        require(suite in allowedSuites) { "Unsupported test suite" }
        require(version in 1..1000000) { "Scenario version must be positive" }
        val approved = meta.optBoolean("regression_approved", false)
        require(suite != "REGRESSION" || approved) {
            "Regression suite needs explicit approval after a real local PASS"
        }
        return ScenarioSpec(packageName, name, duration,
            execution.optBoolean("loop_steps", false), steps,
            suite, version, approved)
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
            .put("meta", JSONObject().put("name", "My first UI test")
                .put("suite", "SMOKE").put("version", 1)
                .put("regression_approved", false))
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
