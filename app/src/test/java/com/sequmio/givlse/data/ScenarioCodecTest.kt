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

    private fun assertion(): JSONObject = JSONObject()
        .put("action", "ASSERT_EXISTS")
        .put("selector", JSONObject().put("view_id", "$pkg:id/ready"))

    @Test fun acceptsDeterministicClick() {
        val spec = ScenarioCodec.parse(scenario(click(), assertion()), pkg)
        assertEquals(pkg, spec.packageName)
        assertEquals("CLICK", spec.steps.first().action)
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
        assertEquals("SWIPE", ScenarioCodec.parse(scenario(swipe, assertion()), pkg).steps.first().action)
    }

    @Test fun rejectsUnknownCommands() {
        assertThrows(IllegalArgumentException::class.java) {
            ScenarioCodec.parse(scenario(JSONObject().put("action", "SHELL")), pkg)
        }
    }

    @Test fun deniesActionOnlyScript() {
        assertThrows(IllegalArgumentException::class.java) {
            ScenarioCodec.parse(scenario(click()), pkg)
        }
    }

    @Test fun assertionTextRequiresExpectedValue() {
        val text = JSONObject().put("action", "ASSERT_TEXT")
            .put("selector", JSONObject().put("view_id", "$pkg:id/status"))
        assertThrows(IllegalArgumentException::class.java) {
            ScenarioCodec.parse(scenario(text), pkg)
        }
        text.put("params", JSONObject().put("expected", "Completed"))
        assertEquals("ASSERT_TEXT",
            ScenarioCodec.parse(scenario(text), pkg).steps.single().action)
    }

    @Test fun notExistsAndEnabledAreValidAssertions() {
        for (action in listOf("ASSERT_NOT_EXISTS", "ASSERT_ENABLED", "ASSERT_SCREEN")) {
            val row = JSONObject().put("action", action)
                .put("selector", JSONObject().put("view_id", "$pkg:id/ready"))
            assertEquals(action, ScenarioCodec.parse(scenario(row), pkg).steps.single().action)
        }
    }

    @Test fun invalidRegressionIsRejected() {
        val root = JSONObject(scenario(assertion()))
        root.getJSONObject("meta").put("suite", "REGRESSION")
        assertThrows(IllegalArgumentException::class.java) {
            ScenarioCodec.parse(root.toString(), pkg)
        }
        root.getJSONObject("meta").put("regression_approved", true)
        assertEquals("REGRESSION", ScenarioCodec.parse(root.toString(), pkg).suite)
    }

    @Test fun multiCaseBundleRetainsSuiteAndVersion() {
        val a = JSONObject(scenario(click(), assertion()))
        a.getJSONObject("meta").put("suite", "SMOKE").put("version", 2)
        val b = JSONObject(scenario(assertion()))
        b.getJSONObject("meta").put("suite", "FEATURE").put("version", 3)
        val bundle = ScenarioCodec.compose(listOf(a.toString(), b.toString()))
        val parsed = ScenarioCodec.parseAll(bundle, pkg)
        assertEquals(2, parsed.size)
        assertEquals("SMOKE", parsed[0].suite)
        assertEquals(2, parsed[0].version)
        assertEquals("FEATURE", parsed[1].suite)
        assertEquals(3, parsed[1].version)
    }
}
