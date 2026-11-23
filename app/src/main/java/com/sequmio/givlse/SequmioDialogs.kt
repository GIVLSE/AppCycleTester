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

@Composable
internal fun SequmioDurationDialog(
    target: TestTarget, onDismiss: () -> Unit, onSave: (Int) -> Unit
) {
    var seconds by remember(target.packageName) {
        mutableStateOf(target.durationSeconds.coerceIn(1, 7200))
    }
    fun adjust(delta: Int) { seconds = (seconds + delta).coerceIn(1, 7200) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.label) },
        containerColor = SeqColors.CardRaised,
        titleContentColor = SeqColors.Text,
        textContentColor = SeqColors.Text,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        },
        confirmButton = { SeqTextButton(onClick = { onSave(seconds) }) { Text("Save") } },
        dismissButton = { SeqTextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
