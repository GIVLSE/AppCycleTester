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

@Composable
internal fun SequmioDurationDialog(
    target: TestTarget, onDismiss: () -> Unit, onSave: (Int) -> Unit
) {
    var minutes by remember(target.packageName) {
        mutableStateOf(target.durationSeconds / 60)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.label) },
        containerColor = SeqColors.CardRaised,
        titleContentColor = SeqColors.Text,
        textContentColor = SeqColors.Text,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Foreground time for this app")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SeqTextButton(onClick = { minutes = (minutes - 1).coerceAtLeast(1) },
                        enabled = minutes > 1) { Text("−", fontSize = 26.sp) }
                    Text(minutes.toString() + " min", modifier = Modifier.weight(1f))
                    SeqTextButton(onClick = { minutes = (minutes + 1).coerceAtMost(120) },
                        enabled = minutes < 120) { Text("+", fontSize = 26.sp) }
                }
                Text("Choose 1–120 minutes", color = SeqColors.Muted, fontSize = 12.sp)
            }
        },
        confirmButton = { SeqTextButton(onClick = { onSave(minutes) }) { Text("Save") } },
        dismissButton = { SeqTextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
