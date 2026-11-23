package io.github.givlse.appcycletester

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.givlse.appcycletester.data.AppCatalog
import io.github.givlse.appcycletester.data.SettingsStore
import io.github.givlse.appcycletester.model.InstalledApp
import io.github.givlse.appcycletester.model.RepeatMode
import io.github.givlse.appcycletester.model.RunEvent
import io.github.givlse.appcycletester.model.RunPhase
import io.github.givlse.appcycletester.model.TestPlan
import io.github.givlse.appcycletester.model.TestTarget
import io.github.givlse.appcycletester.runner.CycleAccessibilityService
import io.github.givlse.appcycletester.runner.RunnerRuntime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    CycleScreen(onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    })
                }
            }
        }
    }
}

@Composable
private fun CycleScreen(onOpenAccessibility: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val available = remember { AppCatalog.list(context) }
    var plan by remember { mutableStateOf(store.loadPlan()) }
    var showChooser by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val runtime by RunnerRuntime.state.collectAsState()
    val busy = runtime.phase == RunPhase.RUNNING || runtime.phase == RunPhase.PAUSED
    val history = remember(runtime.events.lastOrNull()?.timestampMillis, runtime.phase) {
        store.readHistory().takeLast(20).reversed()
    }

    fun update(next: TestPlan) {
        plan = next
        store.savePlan(next)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("App Cycle Tester", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Text("V1 · цикл приложений", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Accessibility: " + if (runtime.accessibilityReady) "подключена" else "не включена",
                        fontWeight = FontWeight.SemiBold)
                    Text("Для переключения приложений включите App Cycle Runner в специальных возможностях Android.")
                    if (!runtime.accessibilityReady) {
                        OutlinedButton(onClick = onOpenAccessibility) { Text("Открыть настройки") }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Приложения (${plan.targets.size})", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = { showChooser = true }, enabled = !busy) {
                    Text("Добавить")
                }
            }
        }
        if (plan.targets.isEmpty()) {
            item { Text("Добавьте приложения для тестирования.") }
        }
        itemsIndexed(plan.targets, key = { _, target -> target.packageName }) { position, target ->
            TargetCard(
                target = target, position = position, size = plan.targets.size,
                enabled = !busy,
                onDuration = { minutes ->
                    update(plan.copy(targets = plan.targets.map {
                        if (it.packageName == target.packageName)
                            it.copy(durationSeconds = minutes * 60) else it
                    }))
                },
                onMove = { shift ->
                    val nextPosition = position + shift
                    if (nextPosition in plan.targets.indices) {
                        val updated = plan.targets.toMutableList()
                        updated.add(nextPosition, updated.removeAt(position))
                        update(plan.copy(targets = updated))
                    }
                },
                onRemove = {
                    update(plan.copy(targets = plan.targets.filterNot {
                        it.packageName == target.packageName
                    }))
                }
            )
        }
        item {
            HorizontalDivider()
            Text("Повторы", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    RepeatMode.ONCE to "Один раз",
                    RepeatMode.COUNT to "N циклов",
                    RepeatMode.ENDLESS to "Бесконечно"
                ).forEach { (mode, title) ->
                    FilterChip(
                        selected = plan.repeatMode == mode,
                        onClick = { update(plan.copy(repeatMode = mode)) },
                        label = { Text(title) }, enabled = !busy
                    )
                }
            }
            if (plan.repeatMode == RepeatMode.COUNT) {
                NumberControl("Число циклов", plan.repeatCount, 1, 999, 1, !busy) {
                    update(plan.copy(repeatCount = it))
                }
            }
        }
        item {
            HorizontalDivider()
            Text("Общее время", style = MaterialTheme.typography.titleLarge)
            Text("0 минут — без лимита")
            NumberControl("Лимит, мин", plan.maxDurationMinutes, 0, 10080, 5, !busy) {
                update(plan.copy(maxDurationMinutes = it))
            }
            val oneCycle = plan.targets.sumOf { it.durationSeconds } / 60
            Text("Один цикл: $oneCycle мин. " + when (plan.repeatMode) {
                RepeatMode.ONCE -> "Итого: $oneCycle мин."
                RepeatMode.COUNT -> "Всего: ${oneCycle * plan.repeatCount} мин."
                RepeatMode.ENDLESS -> "До остановки или общего лимита."
            })
        }
        item {
            HorizontalDivider()
            Text("Управление", style = MaterialTheme.typography.titleLarge)
            Text("Состояние: ${phaseText(runtime.phase)}")
            if (runtime.phase == RunPhase.FINISHED) {
                Text("Результат: " + if (runtime.success == true) "PASS" else "FAIL / STOPPED",
                    fontWeight = FontWeight.Bold)
            }
            if (runtime.activeApp != null) {
                Text("Активное: ${runtime.activeApp} · осталось ${formatSeconds(runtime.remainingSeconds)}")
            }
            Text("Прошло: ${formatSeconds(runtime.elapsedSeconds)} · циклов: ${runtime.completedCycles}")
            if (runtime.message.isNotBlank()) Text(runtime.message, style = MaterialTheme.typography.bodySmall)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!busy) {
                    Button(enabled = runtime.accessibilityReady && plan.targets.isNotEmpty(), onClick = {
                        error = CycleAccessibilityService.instance?.start(plan)
                            ?: "Служба Accessibility не подключена"
                    }) { Text("Старт") }
                } else {
                    Button(onClick = {
                        if (runtime.phase == RunPhase.RUNNING)
                            CycleAccessibilityService.instance?.pause()
                        else CycleAccessibilityService.instance?.resume()
                    }) { Text(if (runtime.phase == RunPhase.RUNNING) "Пауза" else "Продолжить") }
                    OutlinedButton(onClick = { CycleAccessibilityService.instance?.stop() }) {
                        Text("Стоп")
                    }
                }
            }
        }
        item {
            HorizontalDivider()
            Text("Журнал", style = MaterialTheme.typography.titleLarge)
        }
        if (history.isEmpty()) item { Text("Событий пока нет") }
        items(history) { event ->
            Text("${formatEventTime(event)}  ${event.message}",
                style = MaterialTheme.typography.bodySmall)
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    if (showChooser) {
        AppChooser(
            catalog = available,
            existing = plan.targets.map { it.packageName }.toSet(),
            onDismiss = { showChooser = false },
            onAdd = { selected ->
                update(plan.copy(targets = plan.targets + selected.map {
                    TestTarget(it.packageName, it.activityClass, it.label)
                }))
                showChooser = false
            }
        )
    }
}

@Composable
private fun TargetCard(
    target: TestTarget, position: Int, size: Int, enabled: Boolean,
    onDuration: (Int) -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${position + 1}. ${target.label}", fontWeight = FontWeight.SemiBold)
            Text(target.packageName, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            NumberControl("Минуты", target.durationSeconds / 60, 1, 120, 1, enabled, onDuration)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onMove(-1) }, enabled = enabled && position > 0) {
                    Text("↑")
                }
                OutlinedButton(onClick = { onMove(1) }, enabled = enabled && position < size - 1) {
                    Text("↓")
                }
                TextButton(onClick = onRemove, enabled = enabled) { Text("Убрать") }
            }
        }
    }
}

@Composable
private fun NumberControl(
    title: String, value: Int, min: Int, max: Int, step: Int,
    enabled: Boolean, onChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((value - step).coerceAtLeast(min)) },
            enabled = enabled && value > min) { Text("−") }
        Text("$value", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onChange((value + step).coerceAtMost(max)) },
            enabled = enabled && value < max) { Text("+") }
    }
}

@Composable
private fun AppChooser(
    catalog: List<InstalledApp>, existing: Set<String>,
    onDismiss: () -> Unit, onAdd: (List<InstalledApp>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(emptySet<String>()) }
    val filtered = catalog.filter {
        it.packageName !in existing &&
            (it.label.contains(query, ignoreCase = true) ||
             it.packageName.contains(query, ignoreCase = true))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выбор приложений") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it },
                    label = { Text("Название или package") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                chosen = if (app.packageName in chosen)
                                    chosen - app.packageName else chosen + app.packageName
                            }) {
                            Checkbox(
                                checked = app.packageName in chosen,
                                onCheckedChange = {
                                    chosen = if (it) chosen + app.packageName
                                        else chosen - app.packageName
                                }
                            )
                            Column {
                                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) Text("Приложения не найдены")
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty(), onClick = {
                onAdd(catalog.filter { it.packageName in chosen })
            }) { Text("Добавить (${chosen.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

private fun phaseText(phase: RunPhase) = when (phase) {
    RunPhase.IDLE -> "Не запускался"
    RunPhase.RUNNING -> "Выполняется"
    RunPhase.PAUSED -> "На паузе"
    RunPhase.FINISHED -> "Завершён"
}
private fun formatSeconds(value: Long): String =
    "%02d:%02d:%02d".format(value / 3600, (value % 3600) / 60, value % 60)

private fun formatEventTime(event: RunEvent) =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.timestampMillis))
