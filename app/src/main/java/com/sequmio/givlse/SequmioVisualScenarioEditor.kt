package com.sequmio.givlse

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sequmio.givlse.data.MyAppDraft
import com.sequmio.givlse.data.ScenarioCodec
import org.json.JSONArray
import org.json.JSONObject

private data class EditableStep(
    val action: String,
    val delay: String = "300",
    val viewId: String = "",
    val text: String = "",
    val description: String = "",
    val x: String = "0.5",
    val y: String = "0.5",
    val endX: String = "0.5",
    val endY: String = "0.25",
    val timeMs: String = "1000",
    val inputText: String = "",
    val expected: String = "",
    val optional: Boolean = false
)

private fun parseSteps(json: String): List<EditableStep> {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
    val arr = root.optJSONArray("steps") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val step = arr.optJSONObject(i) ?: return@mapNotNull null
        val params = step.optJSONObject("params") ?: JSONObject()
        val selector = step.optJSONObject("selector") ?: JSONObject()
        val coords = step.optJSONObject("fallback_coords") ?: JSONObject()
        val action = step.optString("action", "WAIT")
        EditableStep(
            action = action,
            delay = step.optInt("delay_before_ms", 300).toString(),
            viewId = selector.optString("view_id"),
            text = selector.optString("text"),
            description = selector.optString("content_desc"),
            x = if (action == "SWIPE")
                params.optDouble("start_x_percent", 0.5).toString()
                else coords.optDouble("x_percent", 0.5).toString(),
            y = if (action == "SWIPE")
                params.optDouble("start_y_percent", 0.75).toString()
                else coords.optDouble("y_percent", 0.5).toString(),
            endX = params.optDouble("end_x_percent", 0.5).toString(),
            endY = params.optDouble("end_y_percent", 0.25).toString(),
            timeMs = params.optLong("duration_ms", if (action == "SWIPE") 450 else 1000).toString(),
            inputText = params.optString("text"),
            expected = params.optString("expected"),
            optional = step.optBoolean("optional", false)
        )
    }
}

private fun createJson(
    packageName: String, name: String, durationSeconds: Int,
    loop: Boolean, steps: List<EditableStep>,
    suite: String, version: Int, regressionApproved: Boolean
): String {
    val arr = JSONArray()
    steps.forEachIndexed { index, s ->
        val obj = JSONObject()
            .put("order", index + 1)
            .put("action", s.action)
            .put("delay_before_ms", s.delay.toLongOrNull() ?: 0L)
            .put("optional", s.optional)
        val selector = JSONObject()
        if (s.viewId.isNotBlank()) selector.put("view_id", s.viewId.trim())
        if (s.text.isNotBlank()) selector.put("text", s.text.trim())
        if (s.description.isNotBlank()) selector.put("content_desc", s.description.trim())
        if (selector.length() > 0) obj.put("selector", selector)
        val params = JSONObject()
        when (s.action) {
            "WAIT" -> params.put("duration_ms", s.timeMs.toLongOrNull() ?: 1000L)
            "SWIPE" -> {
                params.put("start_x_percent", s.x.toDoubleOrNull() ?: -1.0)
                    .put("start_y_percent", s.y.toDoubleOrNull() ?: -1.0)
                    .put("end_x_percent", s.endX.toDoubleOrNull() ?: -1.0)
                    .put("end_y_percent", s.endY.toDoubleOrNull() ?: -1.0)
                    .put("duration_ms", s.timeMs.toLongOrNull() ?: 450L)
            }
            "ASSERT_TEXT" -> params.put("expected", s.expected)
            "INPUT_TEXT" -> {
                params.put("text", s.inputText)
                    .put("clear_before", true)
            }
            "CLICK" -> {
                val coords = JSONObject()
                    .put("x_percent", s.x.toDoubleOrNull() ?: -1.0)
                    .put("y_percent", s.y.toDoubleOrNull() ?: -1.0)
                obj.put("fallback_coords", coords)
            }
        }
        if (params.length() > 0) obj.put("params", params)
        arr.put(obj)
    }
    return JSONObject()
        .put("schema_version", 1)
        .put("meta", JSONObject().put("name", name)
            .put("suite", suite).put("version", version)
            .put("regression_approved", regressionApproved))
        .put("target", JSONObject().put("package_name", packageName))
        .put("execution", JSONObject().put("total_duration_sec", durationSeconds)
            .put("loop_steps", loop))
        .put("steps", arr)
        .toString(2)
}

@Composable
private fun TextConfig(
    name: String, value: String,
    modifier: Modifier = Modifier, singleLine: Boolean = true,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(name) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 10,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = SeqColors.Text,
            unfocusedTextColor = SeqColors.Text,
            focusedLabelColor = SeqColors.Primary,
            unfocusedLabelColor = SeqColors.Muted,
            focusedBorderColor = SeqColors.Primary,
            unfocusedBorderColor = SeqColors.Border,
            cursorColor = SeqColors.Teal
        )
    )
}

/**
 * No-code editor built around the supported deterministic E2E actions.
 * Recorded steps can be refined, reordered, or deleted without editing JSON.
 */
@Composable
internal fun SequmioVisualScenarioEditor(
    initial: MyAppDraft,
    onSave: (MyAppDraft) -> Unit,
    onBack: () -> Unit
) {
    val parsed = remember(initial.scenarioJson) {
        runCatching { JSONObject(initial.scenarioJson) }.getOrNull()
    }
    var name by remember(initial.packageName) {
        mutableStateOf(parsed?.optJSONObject("meta")?.optString("name", "UI smoke test")
            ?: "UI smoke test")
    }
    var duration by remember(initial.packageName) {
        mutableStateOf(parsed?.optJSONObject("execution")
            ?.optInt("total_duration_sec", 300)?.toString() ?: "300")
    }
    val startingSuite = parsed?.optJSONObject("meta")?.optString("suite", "SMOKE") ?: "SMOKE"
    val startingVersion = parsed?.optJSONObject("meta")?.optInt("version", 1) ?: 1
    var suite by remember(initial.packageName) { mutableStateOf(startingSuite) }
    var loop by remember(initial.packageName) {
        mutableStateOf(parsed?.optJSONObject("execution")?.optBoolean("loop_steps", false) ?: false)
    }
    var steps by remember(initial.scenarioJson) { mutableStateOf(parseSteps(initial.scenarioJson)) }
    var expanded by remember { mutableIntStateOf(-1) }
    var validation by remember { mutableStateOf("") }
    var showJson by remember { mutableStateOf(false) }
    var rawJson by remember(initial.scenarioJson) { mutableStateOf(initial.scenarioJson) }

    fun changeStep(index: Int, block: (EditableStep) -> EditableStep) {
        steps = steps.toMutableList().apply { this[index] = block(this[index]) }
    }
    fun snapshot(): String = if (showJson) rawJson else createJson(
        initial.packageName, name, duration.toIntOrNull() ?: 0, loop, steps,
        suite, startingVersion, startingSuite == "REGRESSION"
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SeqTopBar("Scenario Editor", onBack)
        Text("For " + initial.packageName, color = SeqColors.Muted, fontSize = 12.sp)
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Build a test like a QA engineer", fontWeight = FontWeight.Bold)
                Text("1 · Choose a suite and a user journey",
                    color = SeqColors.Muted, fontSize = 12.sp)
                Text("2 · Add actions: what the user does",
                    color = SeqColors.Muted, fontSize = 12.sp)
                Text("3 · Add an ASSERT: what the app MUST show",
                    color = SeqColors.Muted, fontSize = 12.sp)
                Text("4 · Run on your own device before publishing",
                    color = SeqColors.Muted, fontSize = 12.sp)
                Text("5 · Promote successful tests to Regression",
                    color = SeqColors.Muted, fontSize = 12.sp)
            }
        }
        Text("Test suite", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("SMOKE", "FEATURE").forEach { option ->
                FilterChip(selected = suite == option, onClick = { suite = option },
                    label = { Text(option, fontSize = 12.sp) })
            }
            if (startingSuite == "REGRESSION") {
                FilterChip(selected = suite == "REGRESSION", onClick = {
                    suite = "REGRESSION"
                }, label = { Text("REGRESSION", fontSize = 11.sp) })
            }
        }
        if (startingSuite == "REGRESSION") Text(
            "Editing an approved Regression test creates a new Feature version; " +
                "re-run it and approve again from Test Suites.",
            color = SeqColors.Muted, fontSize = 12.sp)
        TextConfig("Scenario name", name) { name = it }
        TextConfig("Estimated Duration (seconds)", duration) { duration = it }
        val estimated = duration.toIntOrNull()
        Text("Automatic Timeout: " + (if (estimated != null && estimated in 5..7200)
            targetTimeLabel(com.sequmio.givlse.model.ExecutionPolicy.timeout(estimated, 0)) else "Not set"),
            color = SeqColors.Teal, fontSize = 14.sp)
        Text("Recording time is an estimate, not a hard limit. Override the timeout and choose looping in Test Plan.",
            color = SeqColors.Muted, fontSize = 12.sp)
        Row {
            SeqTextButton(onClick = {
                if (showJson) {
                    val result = runCatching { JSONObject(rawJson) }
                    if (result.isSuccess) {
                        steps = parseSteps(rawJson)
                        val obj = result.getOrThrow()
                        name = obj.optJSONObject("meta")?.optString("name", name) ?: name
                        suite = obj.optJSONObject("meta")?.optString("suite", "SMOKE") ?: "SMOKE"
                        duration = obj.optJSONObject("execution")
                            ?.optInt("total_duration_sec", 300)?.toString() ?: "300"
                        loop = obj.optJSONObject("execution")
                            ?.optBoolean("loop_steps", false) ?: false
                        showJson = false
                        validation = ""
                    } else {
                        validation = "Invalid JSON: " + result.exceptionOrNull()?.message
                    }
                } else {
                    rawJson = snapshot()
                    showJson = true
                }
            }) { Text(if (showJson) "Visual editor" else "Advanced JSON") }
        }
        if (showJson) {
            TextConfig("Advanced scenario JSON", rawJson, singleLine = false) { rawJson = it }
        } else {
            Text("Steps (" + steps.size + "/50)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("CLICK", "WAIT", "SWIPE", "INPUT_TEXT",
                    "ASSERT_EXISTS", "ASSERT_TEXT", "ASSERT_NOT_EXISTS",
                    "ASSERT_ENABLED", "ASSERT_SCREEN", "SYSTEM_BACK")
                    .forEach { action ->
                        SeqTextButton(
                            onClick = {
                                if (steps.size < 50) {
                                    steps = steps + EditableStep(action)
                                    expanded = steps.lastIndex
                                }
                            }, enabled = steps.size < 50
                        ) { Text("+ " + action, fontSize = 12.sp) }
                    }
            }
            steps.forEachIndexed { i, step ->
                SeqCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text((i+1).toString() + ". " + step.action,
                                modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            SeqTextButton(onClick = { expanded = if (expanded == i) -1 else i }) {
                                Text(if (expanded == i) "Hide" else "Edit")
                            }
                        }
                        Row {
                            SeqTextButton(onClick = {
                                if (i > 0) {
                                    val mutable = steps.toMutableList()
                                    mutable.add(i-1, mutable.removeAt(i))
                                    steps = mutable
                                    expanded = i-1
                                }
                            }, enabled = i > 0) { Text("↑") }
                            SeqTextButton(onClick = {
                                if (i < steps.lastIndex) {
                                    val mutable = steps.toMutableList()
                                    mutable.add(i+1, mutable.removeAt(i))
                                    steps = mutable
                                    expanded = i+1
                                }
                            }, enabled = i < steps.lastIndex) { Text("↓") }
                            SeqTextButton(onClick = {
                                steps = steps.toMutableList().apply { removeAt(i) }
                                expanded = -1
                            }) { Text("Remove") }
                        }
                        if (expanded == i) {
                            TextConfig("Delay before (ms)", step.delay) { next ->
                                changeStep(i) { it.copy(delay = next) }
                            }
                            if (step.action in listOf("CLICK","ASSERT_EXISTS","INPUT_TEXT",
                                    "ASSERT_TEXT","ASSERT_NOT_EXISTS",
                                    "ASSERT_ENABLED","ASSERT_SCREEN")) {
                                TextConfig("View ID", step.viewId) { next ->
                                    changeStep(i) { it.copy(viewId = next) }
                                }
                                TextConfig("Visible text", step.text) { next ->
                                    changeStep(i) { it.copy(text = next) }
                                }
                                TextConfig("Content description", step.description) { next ->
                                    changeStep(i) { it.copy(description = next) }
                                }
                            }
                            if (step.action == "ASSERT_TEXT") {
                                Text("Expected result: the target element's text equals...",
                                    color = SeqColors.Muted, fontSize = 12.sp)
                                TextConfig("Expected exact text", step.expected) { next ->
                                    changeStep(i) { it.copy(expected = next) }
                                }
                            }
                            if (step.action == "CLICK" || step.action == "SWIPE") {
                                TextConfig("Start/Fallback X [0..1]", step.x) { next ->
                                    changeStep(i) { it.copy(x = next) }
                                }
                                TextConfig("Start/Fallback Y [0..1]", step.y) { next ->
                                    changeStep(i) { it.copy(y = next) }
                                }
                            }
                            if (step.action == "SWIPE") {
                                TextConfig("End X [0..1]", step.endX) { next ->
                                    changeStep(i) { it.copy(endX = next) }
                                }
                                TextConfig("End Y [0..1]", step.endY) { next ->
                                    changeStep(i) { it.copy(endY = next) }
                                }
                            }
                            if (step.action == "WAIT" || step.action == "SWIPE") {
                                TextConfig("Duration (ms)", step.timeMs) { next ->
                                    changeStep(i) { it.copy(timeMs = next) }
                                }
                            }
                            if (step.action == "INPUT_TEXT") {
                                Text("Use only non-sensitive test text.",
                                    color = SeqColors.Muted, fontSize = 12.sp)
                                TextConfig("Test text", step.inputText) { next ->
                                    changeStep(i) { it.copy(inputText = next) }
                                }
                            }
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Checkbox(checked = step.optional,
                                    onCheckedChange = { selected ->
                                        changeStep(i) { it.copy(optional = selected) }
                                    })
                                Text("Optional step", color = SeqColors.Muted)
                            }
                        }
                    }
                }
            }
        }
        if (!showJson && steps.none { it.action.startsWith("ASSERT_") }) {
            Text("Missing expected result: add an ASSERT step. " +
                "A sequence of taps without verification is not a test.",
                color = SeqColors.Error, fontSize = 13.sp)
            SeqTextButton(onClick = {
                steps = steps + EditableStep("ASSERT_EXISTS")
                expanded = steps.lastIndex
            }) { Text("+ Add an expected-result assertion") }
        }
        SeqTextButton(onClick = {
            validation = runCatching {
                ScenarioCodec.parse(snapshot(), initial.packageName)
                "Valid scenario"
            }.getOrElse { it.message ?: "Invalid scenario" }
        }) { Text("Validate") }
        if (validation.isNotBlank())
            Text(validation,
                color = if (validation == "Valid scenario") SeqColors.Success else SeqColors.Error)
        SeqPrimaryButton("Save test case", onClick = {
            validation = runCatching {
                val updated = JSONObject(snapshot())
                val oldMeta = parsed?.optJSONObject("meta") ?: JSONObject()
                val changed = updated.toString() != parsed?.toString()
                val meta = updated.optJSONObject("meta") ?: JSONObject()
                val originalVersion = oldMeta.optInt("version", 1)
                meta.put("version", if (changed) originalVersion + 1 else originalVersion)
                if (startingSuite == "REGRESSION" && changed &&
                    suite == "REGRESSION") {
                    meta.put("suite", "FEATURE")
                    meta.put("regression_approved", false)
                } else if (suite != "REGRESSION") {
                    meta.put("regression_approved", false)
                }
                updated.put("meta", meta)
                val finalJson = updated.toString()
                ScenarioCodec.parse(finalJson, initial.packageName)
                onSave(initial.copy(scenarioJson = finalJson))
                onBack()
                "Saved"
            }.getOrElse { it.message ?: "Fix scenario errors" }
        }, modifier = Modifier.fillMaxWidth())
        Text("Save requires a valid actionable scenario. It is stored locally " +
            "until you explicitly publish your My App listing.",
            color = SeqColors.Muted, fontSize = 12.sp)
    }
}
