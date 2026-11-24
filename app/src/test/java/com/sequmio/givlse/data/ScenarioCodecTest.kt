package com.sequmio.givlse.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ScenarioCodecTest {
    private val pkg = "com.example.qa"

    private fun scenario(vararg steps: JSONObject, packageName: String = pkg): String =
        JSONObject()
            .put("schema_version", 1)
            .put("meta", JSONObject().put("name", "QA"))
            .put("target", JSONObject().put("package_name", packageName))
            .put("execution", JSONObject()
                .put("total_duration_sec", 30).put("loop_steps", false))
            .put("steps", JSONArray().apply { steps.forEach { put(it) } })
            .toString()

    private fun click(): JSONObject = JSONObject()
        .put("action", "CLICK")
        .put("selector", JSONObject().put("view_id", "$pkg:id/start"))

    @Test fun acceptsDeterministicClick() {
        val spec = ScenarioCodec.parse(scenario(click()), pkg)
        assertEquals(pkg, spec.packageName)
        assertEquals("CLICK", spec.steps.single().action)
    }

    @Test fun forbidsScenarioTargetingOtherPackage() {
        assertThrows(IllegalArgumentException::class.java) {
            ScenarioCodec.parse(scenario(click(), packageName = "com.other.app"), pkg)
        }
    }

    @Test fun rejectsWaitOnlyScript() {
        val wait = JSONObject().put("action", "WAIT")
            .put("params", JSONObject().put("duration_ms", 500))
        assertThrows(IllegalArgumentException::class.java) {
            ScenarioCodec.parse(scenario(wait), pkg)
        }
    }

    @Test fun rejectsMissingUiSelectors() {
        assertThrows(IllegalArgumentException::class.java) {
            ScenarioCodec.parse(scenario(JSONObject().put("action", "ASSERT_EXISTS")), pkg)
        }
    }

    @Test fun rejectsRecordedTextPlaceholder() {
        val text = JSONObject().put("action", "INPUT_TEXT")
            .put("selector", JSONObject().put("view_id", "$pkg:id/text"))
            .put("params", JSONObject().put("text", "REPLACE_WITH_TEST_TEXT"))
        assertThrows(IllegalArgumentException::class.java) {
            ScenarioCodec.parse(scenario(text), pkg)
        }
    }

    @Test fun acceptsSwipeWithNormalizedCoordinates() {
        val swipe = JSONObject().put("action", "SWIPE")
            .put("params", JSONObject()
                .put("start_x_percent", 0.5).put("start_y_percent", 0.8)
                .put("end_x_percent", 0.5).put("end_y_percent", 0.2))
        assertEquals("SWIPE", ScenarioCodec.parse(scenario(swipe), pkg).steps.single().action)
    }

    @Test fun rejectsUnknownCommands() {
        assertThrows(IllegalArgumentException::class.java) {
            ScenarioCodec.parse(scenario(JSONObject().put("action", "SHELL")), pkg)
        }
    }
}
