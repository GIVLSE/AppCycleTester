package com.sequmio.givlse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sequmio.givlse.model.InstalledApp
import com.sequmio.givlse.model.TestTarget
import com.sequmio.givlse.model.ExecutionMode
import com.sequmio.givlse.model.ExecutionPolicy
import com.sequmio.givlse.data.ScenarioCodec
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch

@Composable
internal fun SequmioAppPicker(
    apps: List<InstalledApp>,
    existing: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (List<InstalledApp>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(emptySet<String>()) }
    val visible = apps.filter {
        it.packageName !in existing &&
            (it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Apps") },
        containerColor = SeqColors.CardRaised,
        titleContentColor = SeqColors.Text,
        textContentColor = SeqColors.Text,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text("Search installed apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SeqColors.Text,
                        unfocusedTextColor = SeqColors.Text,
                        focusedLabelColor = SeqColors.Primary,
                        unfocusedLabelColor = SeqColors.Muted,
                        focusedBorderColor = SeqColors.Primary,
                        unfocusedBorderColor = SeqColors.Border,
                        cursorColor = SeqColors.Teal,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(visible, key = { it.packageName }) { app ->
                        Row(Modifier.fillMaxWidth().clickable {
                            chosen = if (app.packageName in chosen)
                                chosen - app.packageName else chosen + app.packageName
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = app.packageName in chosen, onCheckedChange = {
                                chosen = if (it) chosen + app.packageName
                                    else chosen - app.packageName
                            }, colors = CheckboxDefaults.colors(
                                checkedColor = SeqColors.PrimaryAction,
                                uncheckedColor = SeqColors.Muted,
                                checkmarkColor = Color.White
                            ))
                            Column {
                                Text(app.label, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, fontSize = 11.sp,
                                    color = SeqColors.Muted, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (visible.isEmpty()) Text("No apps found.")
            }
        },
        confirmButton = {
            SeqTextButton(enabled = chosen.isNotEmpty(), onClick = {
                onAdd(apps.filter { it.packageName in chosen })
            }) { Text("Add (" + chosen.size + ")") }
        },
        dismissButton = {
            SeqTextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

internal fun targetTimeLabel(seconds: Int): String =
    if (seconds >= 60)
        (seconds / 60).toString() + "m " + (seconds % 60).toString() + "s"
    else seconds.toString() + "s"

internal fun executionLabel(target: TestTarget): String = when (target.executionMode) {
    ExecutionMode.FIXED_DURATION -> targetTimeLabel(target.durationSeconds) + " · Fixed"
    ExecutionMode.RUN_TEST_CASES -> "Test cases · " + targetTimeLabel(
        runCatching { ScenarioCodec.selectCases(target.scenarioJson.orEmpty(), target.packageName, target.selectedCaseKeys)
            .sumOf { it.totalDurationSec } }.getOrDefault(target.durationSeconds)) + " est."
    ExecutionMode.LOOP_TEST_CASES -> targetTimeLabel(target.durationSeconds) + " · Loop"
}

@Composable
internal fun SequmioDurationDialog(
    target: TestTarget, onDismiss: () -> Unit, onSave: (TestTarget) -> Unit
) {
    val cases = remember(target.scenarioJson) {
        runCatching { ScenarioCodec.parseAll(target.scenarioJson.orEmpty(), target.packageName) }
            .getOrDefault(emptyList())
    }
    val allKeys = cases.mapIndexed { i, spec -> ScenarioCodec.caseKey(i, spec) }
    var selectedKeys by remember(target) { mutableStateOf(
        target.selectedCaseKeys?.toSet()?.intersect(allKeys.toSet()) ?: allKeys.toSet()) }
    var mode by remember(target) { mutableStateOf(target.executionMode) }
    var seconds by remember(target) { mutableStateOf(target.durationSeconds.coerceIn(1, 7200)) }
    var auto by remember(target) { mutableStateOf(target.scenarioTimeoutSeconds == 0) }
    var manual by remember(target) { mutableStateOf(
        if (target.scenarioTimeoutSeconds > 0) target.scenarioTimeoutSeconds.toString() else "300") }
    val timeout = manual.toIntOrNull()
    fun adjust(delta: Int) { seconds = (seconds + delta).coerceIn(1, 7200) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.label) },
        containerColor = SeqColors.CardRaised,
        titleContentColor = SeqColors.Text,
        textContentColor = SeqColors.Text,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Execution mode")
                listOf(ExecutionMode.FIXED_DURATION to "Fixed Duration",
                    ExecutionMode.RUN_TEST_CASES to "Run Test Cases",
                    ExecutionMode.LOOP_TEST_CASES to "Loop Test Cases").forEach { (option, label) ->
                    val available = option == ExecutionMode.FIXED_DURATION || cases.isNotEmpty()
                    Row(Modifier.fillMaxWidth().clickable(enabled = available) { mode = option },
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == option, onClick = { mode = option }, enabled = available)
                        Text(label, color = if (available) SeqColors.Text else SeqColors.Muted)
                    }
                }
                if (mode != ExecutionMode.FIXED_DURATION) {
                    cases.forEachIndexed { i, spec ->
                        val key = allKeys[i]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = key in selectedKeys, onCheckedChange = {
                                selectedKeys = if (it) selectedKeys + key else selectedKeys - key
                            })
                            Text(spec.name + " v" + spec.version + " · " +
                                targetTimeLabel(spec.totalDurationSec) + " estimated · " +
                                targetTimeLabel(ExecutionPolicy.timeout(spec.totalDurationSec,
                                    if (auto) 0 else timeout ?: 300)) + " timeout", fontSize = 12.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Automatic timeout", Modifier.weight(1f))
                        Switch(checked = auto, onCheckedChange = { auto = it })
                    }
                    if (!auto) OutlinedTextField(value = manual,
                        onValueChange = { manual = it.filter(Char::isDigit).take(5) },
                        label = { Text("Timeout per case (seconds)") },
                        isError = timeout == null || timeout !in 1..14430, singleLine = true)
                    Text(if (mode == ExecutionMode.RUN_TEST_CASES)
                        "Proceed immediately when the selected cases finish."
                        else "No new case starts after the duration. The last case may finish.",
                        color = SeqColors.Muted, fontSize = 12.sp)
                }
                if (mode != ExecutionMode.RUN_TEST_CASES) {
                Text("Foreground time for this app")
                Text(targetTimeLabel(seconds), fontSize = 24.sp, color = SeqColors.Teal)
                Text("Minutes", color = SeqColors.Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SeqTextButton(onClick = { adjust(-60) }, enabled = seconds > 60) {
                        Text("− 1m")
                    }
                    Text((seconds / 60).toString(), modifier = Modifier.weight(1f))
                    SeqTextButton(onClick = { adjust(60) }, enabled = seconds <= 7140) {
                        Text("+ 1m")
                    }
                }
                Text("Seconds", color = SeqColors.Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SeqTextButton(onClick = { adjust(-1) }, enabled = seconds > 1) {
                        Text("− 1s")
                    }
                    Text((seconds % 60).toString(), modifier = Modifier.weight(1f))
                    SeqTextButton(onClick = { adjust(1) }, enabled = seconds < 7200) {
                        Text("+ 1s")
                    }
                }
                Text("Range: 1 second – 120 minutes", color = SeqColors.Muted, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { SeqTextButton(
            enabled = mode == ExecutionMode.FIXED_DURATION ||
                (selectedKeys.isNotEmpty() && (auto || timeout != null && timeout in 1..14430)),
            onClick = { onSave(target.copy(durationSeconds = seconds, executionMode = mode,
                scenarioTimeoutSeconds = if (auto) 0 else timeout?.coerceIn(1, 14430) ?: 0,
                selectedCaseKeys = if (selectedKeys == allKeys.toSet()) null else allKeys.filter { it in selectedKeys })) }
        ) { Text("Save") } },
        dismissButton = { SeqTextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
