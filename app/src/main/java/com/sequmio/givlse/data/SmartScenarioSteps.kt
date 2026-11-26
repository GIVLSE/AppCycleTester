package com.sequmio.givlse.data

import org.json.JSONArray
import org.json.JSONObject

/** Edit individual fields without dropping legacy params, selectors or metadata. */
internal object SmartScenarioSteps {
    val actions = listOf("CLICK", "INPUT_TEXT", "SWIPE", "WAIT", "SYSTEM_BACK")
    val checks = listOf("ASSERT_EXISTS", "ASSERT_NOT_EXISTS", "ASSERT_TEXT", "ASSERT_ENABLED", "ASSERT_SCREEN")

    fun title(action: String): String = when (action) {
        "CLICK" -> "Tap element"
        "INPUT_TEXT" -> "Enter text"
        "SWIPE" -> "Swipe / scroll"
        "WAIT" -> "Wait"
        "SYSTEM_BACK" -> "Go back"
        "ASSERT_EXISTS" -> "Element is visible"
        "ASSERT_NOT_EXISTS" -> "Element is not visible"
        "ASSERT_TEXT" -> "Text equals"
        "ASSERT_ENABLED" -> "Element is enabled"
        "ASSERT_SCREEN" -> "Screen marker is visible"
        else -> action
    }
    fun explanation(action: String): String = when (action) {
        "CLICK" -> "Tap a button or other UI element."
        "INPUT_TEXT" -> "Enter a safe test value into a text field."
        "SWIPE" -> "Move a finger in a selected direction."
        "WAIT" -> "Pause before the next action."
        "SYSTEM_BACK" -> "Press Android Back."
        "ASSERT_EXISTS" -> "Pass when the chosen element appears."
        "ASSERT_NOT_EXISTS" -> "Pass when a previously identified element disappears."
        "ASSERT_TEXT" -> "Pass when the element's current text equals the expected value."
        "ASSERT_ENABLED" -> "Pass when the selected element is enabled."
        "ASSERT_SCREEN" -> "Pass when a distinctive element of the expected screen is visible."
        else -> ""
    }
    fun elementLabel(step: JSONObject): String {
        val selector = step.optJSONObject("selector") ?: JSONObject()
        return step.optString("label").ifBlank {
            selector.optString("text").ifBlank { selector.optString("content_desc") }
                .ifBlank { selector.optString("view_id").substringAfterLast('/').replace('_', ' ') }
        }
    }
    fun requiresElement(action: String) = action == "CLICK" || action == "INPUT_TEXT" || action in checks
    fun hasSelector(step: JSONObject): Boolean {
        val selector = step.optJSONObject("selector") ?: return false
        return listOf("view_id", "text", "content_desc").any { selector.optString(it).isNotBlank() }
    }
    fun warning(step: JSONObject): String? {
        val action = step.optString("action")
        val params = step.optJSONObject("params") ?: JSONObject()
        if (requiresElement(action) && !hasSelector(step) &&
            (action != "CLICK" || step.optJSONObject("fallback_coords") == null))
            return "Choose an element"
        if (step.optJSONObject("selector")?.optString("text") == "REPLACE_WITH_VISIBLE_TEXT")
            return "Choose a real element"
        if (action == "ASSERT_TEXT" && (params.optString("expected").isBlank() ||
                params.optString("expected") == "EXPECTED_TEXT")) return "Set expected text"
        if (action == "INPUT_TEXT" && (params.optString("text").isBlank() ||
                params.optString("text") == "REPLACE_WITH_TEST_TEXT")) return "Enter a safe test value"
        if (action in checks && params.has("timeout_ms") &&
            params.optLong("timeout_ms", 0) !in 1000L..30000L) return "Set a timeout of 1–30 seconds"
        return null
    }
    fun newStep(action: String): JSONObject = JSONObject().put("action", action)
        .put("delay_before_ms", 300).put("optional", false).apply {
            if (action == "WAIT") put("params", JSONObject().put("duration_ms", 1000))
            if (action == "SWIPE") put("params", swipe("Up"))
            if (action in checks) put("params", JSONObject().put("timeout_ms", 5000))
            if (action == "INPUT_TEXT") put("params", JSONObject().put("clear_before", true))
        }
    fun swipe(direction: String): JSONObject {
        val coords = when (direction) {
            "Down" -> listOf(0.5, 0.25, 0.5, 0.75)
            "Left" -> listOf(0.75, 0.5, 0.25, 0.5)
            "Right" -> listOf(0.25, 0.5, 0.75, 0.5)
            else -> listOf(0.5, 0.75, 0.5, 0.25)
        }
        return JSONObject().put("start_x_percent", coords[0]).put("start_y_percent", coords[1])
            .put("end_x_percent", coords[2]).put("end_y_percent", coords[3]).put("duration_ms", 450)
    }
    fun direction(params: JSONObject): String {
        val dx = params.optDouble("end_x_percent", .5) - params.optDouble("start_x_percent", .5)
        val dy = params.optDouble("end_y_percent", .25) - params.optDouble("start_y_percent", .75)
        return if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
            if (dx < 0) "Left" else "Right"
        } else if (dy < 0) "Up" else "Down"
    }
    fun replaceElement(step: JSONObject, selector: JSONObject, label: String,
        x: Double, y: Double, expected: String = ""): JSONObject =
        JSONObject(step.toString()).apply {
            if (selector.length() > 0) put("selector", selector) else remove("selector")
            put("label", label.take(100))
            if (optString("action") == "CLICK") put("fallback_coords",
                JSONObject().put("x_percent", x).put("y_percent", y))
            if (optString("action") == "ASSERT_TEXT" && expected.isNotBlank()) {
                val p = optJSONObject("params") ?: JSONObject()
                put("params", p.put("expected", expected))
            }
        }
    fun rows(root: JSONObject): List<String> {
        val a = root.optJSONArray("steps") ?: JSONArray()
        return (0 until a.length()).map { a.getJSONObject(it).toString() }
    }
    fun writeRows(root: JSONObject, steps: List<String>) {
        val arr = JSONArray()
        steps.forEachIndexed { i, raw -> arr.put(JSONObject(raw).put("order", i + 1)) }
        root.put("steps", arr)
    }
}
