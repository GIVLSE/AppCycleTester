package com.sequmio.givlse

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sequmio.givlse.data.MyAppDraft
import com.sequmio.givlse.data.RunReportStore
import com.sequmio.givlse.data.ScenarioCodec
import org.json.JSONArray
import org.json.JSONObject

/** Preserves multiple independent cases in one published bundle. */
@Composable
internal fun SequmioSuiteManager(
    initial: MyAppDraft,
    onSave: (MyAppDraft) -> Unit,
    onEdit: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val reportStore = remember { RunReportStore(context) }
    val cases = remember(initial.scenarioJson) {
        runCatching { ScenarioCodec.split(initial.scenarioJson) }.getOrDefault(emptyList())
    }
    var message by remember { mutableStateOf("") }
    fun persist(next: List<String>) {
        val raw = if (next.isEmpty()) "" else ScenarioCodec.compose(next)
        onSave(initial.copy(scenarioJson = raw))
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        SeqTopBar("Test Suites", onBack)
        Text("Organize tests around actual user behavior. Each scenario should include " +
            "at least one verification of the expected result.",
            color = SeqColors.Muted, fontSize = 13.sp)
        Text("Cases " + cases.size + "/12", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        SeqPrimaryButton("+ Create test case", onClick = {
            if (cases.size < 12 && initial.packageName.isNotBlank()) {
                persist(cases + ScenarioCodec.starter(initial.packageName))
                message = "New test case added. Replace the placeholder assertion."
            }
        }, enabled = cases.size < 12 && initial.packageName.isNotBlank(),
            modifier = Modifier.fillMaxWidth())
        if (message.isNotBlank()) Text(message, color = SeqColors.Muted, fontSize = 12.sp)
        if (cases.isEmpty()) {
            SeqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Start with a Smoke test", fontWeight = FontWeight.SemiBold)
                    Text("Example: launch your app, tap the main action and assert that " +
                        "the expected screen or text is visible.",
                        color = SeqColors.Muted, fontSize = 13.sp)
                }
            }
        }
        cases.forEachIndexed { index, json ->
            val meta = runCatching { JSONObject(json).optJSONObject("meta") }.getOrNull()
            val title = meta?.optString("name", "Test " + (index + 1))
                ?: "Test " + (index + 1)
            val suite = meta?.optString("suite", "SMOKE") ?: "SMOKE"
            val version = meta?.optInt("version", 1) ?: 1
            val spec = runCatching { ScenarioCodec.parse(json, initial.packageName) }
            val validated = spec.isSuccess
            val completed = if (validated) {
                reportStore.recent().any { run ->
                    if (run.status != "PASS") false
                    else {
                        val report = JSONObject(run.rawJson)
                        val rows = report.optJSONArray("targets") ?: JSONArray()
                        (0 until rows.length()).any { i ->
                            val row = rows.optJSONObject(i)
                            if (row?.optString("packageName") != initial.packageName) false
                            else {
                                val steps = row.optJSONArray("steps") ?: JSONArray()
                                val passed = (0 until steps.length()).any { j ->
                                    val step = steps.optJSONObject(j)
                                    step?.optString("scenarioName") == title &&
                                        step.optInt("scenarioVersion") == version &&
                                        step.optString("action").startsWith("ASSERT_") &&
                                        step.optString("status") == "PASS"
                                }
                                passed
                            }
                        }
                    }
                }
            } else false
            SeqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(suite + " · Case version " + version +
                        if (validated) " · Valid" else " · Needs verification step",
                        color = if (validated) SeqColors.Success else SeqColors.Error,
                        fontSize = 12.sp)
                    if (completed) Text("Local PASS verified for this scenario version",
                        color = SeqColors.Success, fontSize = 12.sp)
                    SeqTextButton(onClick = { onEdit(index) }) { Text("Edit test case →") }
                    if (suite != "REGRESSION") {
                        SeqTextButton(
                            onClick = {
                                val modified = JSONObject(json)
                                val m = modified.optJSONObject("meta") ?: JSONObject()
                                m.put("suite", "REGRESSION")
                                    .put("regression_approved", true)
                                modified.put("meta", m)
                                persist(cases.toMutableList().apply {
                                    this[index] = modified.toString()
                                })
                            },
                            enabled = completed
                        ) { Text("Approve for Regression") }
                    }
                    SeqTextButton(onClick = {
                        persist(cases.toMutableList().apply { removeAt(index) })
                    }) { Text("Delete test case", color = SeqColors.Error) }
                }
            }
        }
        Text("Smoke: basic critical paths. Feature: new behavior under test. " +
            "Regression: a reviewed scenario with at least one local PASS. " +
            "An edited regression scenario should be revalidated before use.",
            color = SeqColors.Muted, fontSize = 12.sp)
    }
}
