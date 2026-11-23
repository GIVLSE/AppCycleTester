package com.sequmio.givlse

import androidx.compose.foundation.Image
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sequmio.givlse.data.AppCatalog
import com.sequmio.givlse.data.SettingsStore
import com.sequmio.givlse.model.*
import com.sequmio.givlse.runner.CycleAccessibilityService
import com.sequmio.givlse.runner.RunnerRuntime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Page { HOME, PLAN, SCENARIOS, HISTORY, SETUP }

@Composable
internal fun SequmioApp(openAccessibility: () -> Unit, openAppDetails: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val apps = remember { AppCatalog.list(context) }
    val runner by RunnerRuntime.state.collectAsState()
    var plan by remember { mutableStateOf(store.loadPlan()) }
    var consent by remember { mutableStateOf(store.accessConsentGiven()) }
    var page by remember { mutableStateOf(Page.HOME) }
    var picker by remember { mutableStateOf(false) }
    var durationTarget by remember { mutableStateOf<TestTarget?>(null) }
    var message by remember { mutableStateOf("") }
    val connected = runner.accessibilityReady && CycleAccessibilityService.instance != null
    val openPrivacy = {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://givlse.github.io/AppCycleTester/privacy.html"))
        )
    }
    val active = runner.phase == RunPhase.RUNNING || runner.phase == RunPhase.PAUSED
    val history = remember(runner.events.lastOrNull()?.timestampMillis, runner.phase, page) {
        store.readHistory().takeLast(120).reversed()
    }
    fun setPlan(p: TestPlan) { plan = p; store.savePlan(p) }
    fun start() {
        message = CycleAccessibilityService.instance?.let { it.start(plan).orEmpty() }
            ?: "Sequmio Runner is not connected"
        if (message.isEmpty()) page = Page.HOME
    }
    fun requestStart() {
        if (plan.targets.isEmpty()) page = Page.PLAN
        else if (!consent || !connected) page = Page.SETUP
        else start()
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.weight(1f)) {
            when (page) {
                Page.HOME -> SequmioHome(
                    plan, runner, connected, message,
                    onStart = { requestStart() },
                    onEdit = { page = Page.PLAN },
                    onSetup = { page = Page.SETUP },
                    onPrivacy = openPrivacy,
                    onPause = { CycleAccessibilityService.instance?.pause() },
                    onResume = { CycleAccessibilityService.instance?.resume() },
                    onStop = { CycleAccessibilityService.instance?.stop() }
                )
                Page.PLAN -> SequmioPlan(
                    plan = plan, enabled = !active,
                    onBack = { page = Page.HOME },
                    onAdd = { picker = true },
                    onDuration = { durationTarget = it },
                    onUp = { index, delta ->
                        val destination = index + delta
                        if (destination in plan.targets.indices) {
                            val changed = plan.targets.toMutableList()
                            changed.add(destination, changed.removeAt(index))
                            setPlan(plan.copy(targets = changed))
                        }
                    },
                    onRemove = { pkg ->
                        setPlan(plan.copy(targets = plan.targets.filterNot { it.packageName == pkg }))
                    },
                    onRepeat = { setPlan(plan.copy(repeatMode = it)) },
                    onCount = { setPlan(plan.copy(repeatCount = it)) },
                    onLimit = { setPlan(plan.copy(maxDurationMinutes = it)) },
                    onSave = { page = Page.HOME }
                )
                Page.SCENARIOS -> SequmioScenarios()
                Page.HISTORY -> SequmioHistory(history)
                Page.SETUP -> SequmioAccessSetup(
                    ready = connected, consent = consent,
                    onConsent = { store.saveAccessConsent(); consent = true },
                    onBack = { page = Page.HOME },
                    onAppInfo = openAppDetails,
                    onAccessibility = openAccessibility,
                    onPrivacy = openPrivacy,
                    onStart = { start() }
                )
            }
        }
        if (page == Page.HOME || page == Page.SCENARIOS || page == Page.HISTORY) {
            Row(Modifier.fillMaxWidth().background(SeqColors.Background)
                .padding(horizontal = 14.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Triple(Page.HOME, "▶", "Tests"), Triple(Page.SCENARIOS, "◇", "Scenarios"),
                    Triple(Page.HISTORY, "◴", "History")).forEach { (tab, icon, label) ->
                    Column(Modifier.weight(1f).background(
                        if (tab == page) SeqColors.CardRaised else Color.Transparent,
                        RoundedCornerShape(14.dp)).clickable { page = tab }
                        .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(icon, fontSize = 22.sp,
                            color = if (page == tab) SeqColors.Primary else SeqColors.Muted)
                        Text(label, fontSize = 12.sp, color = SeqColors.Text)
                    }
                }
            }
        }
    }
    if (picker) SequmioAppPicker(
        apps, plan.targets.map { it.packageName }.toSet(),
        onDismiss = { picker = false },
        onAdd = { selected ->
            setPlan(plan.copy(targets = plan.targets + selected.map {
                TestTarget(it.packageName, it.activityClass, it.label)
            }))
            picker = false
        }
    )
    durationTarget?.let { target ->
        SequmioDurationDialog(target, onDismiss = { durationTarget = null }) { minutes ->
            setPlan(plan.copy(targets = plan.targets.map {
                if (it.packageName == target.packageName)
                    it.copy(durationSeconds = minutes * 60) else it
            }))
            durationTarget = null
        }
    }
}

@Composable
private fun SequmioHome(
    plan: TestPlan, runner: RunSnapshot, connected: Boolean, message: String,
    onStart: () -> Unit, onEdit: () -> Unit, onSetup: () -> Unit,
    onPrivacy: () -> Unit,
    onPause: () -> Unit, onResume: () -> Unit, onStop: () -> Unit
) {
    val active = runner.phase == RunPhase.RUNNING || runner.phase == RunPhase.PAUSED
    val cycleSeconds = plan.targets.sumOf { it.durationSeconds }.toLong()
    val planned = when (plan.repeatMode) {
        RepeatMode.ONCE -> cycleSeconds
        RepeatMode.COUNT -> cycleSeconds * plan.repeatCount
        RepeatMode.ENDLESS -> null
    }
    val seconds = if (plan.maxDurationMinutes > 0) {
        planned?.coerceAtMost(plan.maxDurationMinutes * 60L) ?: plan.maxDurationMinutes * 60L
    } else planned
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("SEQUMIO", color = SeqColors.Muted, fontSize = 12.sp, letterSpacing = 2.sp)
                Text("QA AUTOMATION", color = SeqColors.Teal, fontSize = 12.sp)
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            SequmioMark(88.dp)
            Spacer(Modifier.height(6.dp))
            Text("Sequmio", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("QA Automation", color = SeqColors.Muted)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeqMetric("▦", plan.targets.size.toString(), "Apps", Modifier.weight(1f))
                SeqMetric("◷", seconds?.let { ((it + 59)/60).toString() + " min" } ?: "∞",
                    "Duration", Modifier.weight(1f))
                SeqMetric("↻", when (plan.repeatMode) {
                    RepeatMode.ONCE -> "1×"
                    RepeatMode.COUNT -> plan.repeatCount.toString() + "×"
                    RepeatMode.ENDLESS -> "∞"
                }, "Cycles", Modifier.weight(1f))
            }
        }
        item {
            SeqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("▤", fontSize = 24.sp, color = SeqColors.Sky)
                        Spacer(Modifier.width(9.dp))
                        Text("Test Plan", fontWeight = FontWeight.Bold, fontSize = 21.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onEdit, enabled = !active) { Text("Edit") }
                    }
                    Text(if (plan.targets.isEmpty()) "Add apps to create a test sequence."
                        else "Run the selected apps automatically in order.",
                        color = SeqColors.Muted)
                    if (plan.targets.isNotEmpty()) {
                        Text(plan.targets.joinToString("  →  ") { it.label },
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = SeqColors.Sky, fontSize = 12.sp)
                    }
                    if (active) {
                        Text((runner.activeApp ?: "Current app") + " • " +
                            duration(runner.remainingSeconds) + " remaining",
                            color = SeqColors.Teal)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SeqPrimaryButton(
                                if (runner.phase == RunPhase.RUNNING) "Pause" else "Resume",
                                if (runner.phase == RunPhase.RUNNING) onPause else onResume,
                                Modifier.weight(1f))
                            OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                                Text("Stop")
                            }
                        }
                    } else SeqPrimaryButton(
                        if (plan.targets.isEmpty()) "+ Create Test Plan" else "▶  Start Test",
                        onStart, Modifier.fillMaxWidth()
                    )
                }
            }
        }
        item {
            SeqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Runner Status", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(if (connected) "● Connected" else "● Setup required",
                            color = if (connected) SeqColors.Success else SeqColors.Error,
                            fontSize = 12.sp)
                    }
                    if (!connected) {
                        Text("Accessibility access is needed to switch between apps.",
                            color = SeqColors.Muted, fontSize = 12.sp)
                        TextButton(onClick = onSetup) { Text("Set up runner →") }
                    } else if (runner.phase == RunPhase.FINISHED) {
                        Text(if (runner.success == true) "Last run: PASS" else "Last run: FAIL / STOP",
                            color = if (runner.success == true) SeqColors.Success else SeqColors.Error)
                        Text("Elapsed " + duration(runner.elapsedSeconds) + " • " +
                            runner.completedCycles + " completed cycles",
                            color = SeqColors.Muted, fontSize = 12.sp)
                    } else Text(if (active) "Running • " + duration(runner.elapsedSeconds)
                        else "Ready for testing", color = SeqColors.Muted)
                    if (message.isNotEmpty()) Text(message, color = SeqColors.Error)
                }
            }
        }
        item {
            TextButton(onClick = onPrivacy) {
                Text("Privacy Policy", color = SeqColors.Muted)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SeqMetric(icon: String, amount: String, label: String, modifier: Modifier) {
    SeqCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 15.dp, horizontal = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(icon, color = SeqColors.Teal, fontSize = 23.sp)
            Text(amount, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
            Text(label, color = SeqColors.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SequmioPlan(
    plan: TestPlan, enabled: Boolean,
    onBack: () -> Unit, onAdd: () -> Unit, onDuration: (TestTarget) -> Unit,
    onUp: (Int, Int) -> Unit, onRemove: (String) -> Unit,
    onRepeat: (RepeatMode) -> Unit, onCount: (Int) -> Unit,
    onLimit: (Int) -> Unit, onSave: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SeqTopBar("Test Plan", onBack) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Selected Apps (" + plan.targets.size + ")",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onAdd, enabled = enabled) { Text("+ Add App") }
            }
        }
        if (plan.targets.isEmpty()) item {
            SeqCard(Modifier.fillMaxWidth()) {
                Text("No apps selected. Tap Add App to get started.",
                    Modifier.padding(18.dp), color = SeqColors.Muted)
            }
        }
        items(plan.targets.size) { index ->
            val target = plan.targets[index]
            SeqCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    SeqAppLetter(target.packageName, target.label)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f).clickable(enabled = enabled) {
                        onDuration(target)
                    }) {
                        Text(target.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold)
                        Text((target.durationSeconds / 60).toString() + " min • edit",
                            color = SeqColors.Muted, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                            TextButton(onClick = { onUp(index, -1) },
                                enabled = enabled && index > 0) { Text("↑") }
                            TextButton(onClick = { onUp(index, 1) },
                                enabled = enabled && index < plan.targets.lastIndex) { Text("↓") }
                        }
                        TextButton(onClick = { onRemove(target.packageName) },
                            enabled = enabled) { Text("Remove", fontSize = 11.sp) }
                    }
                }
            }
        }
        item {
            Text("Repeat Mode", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(RepeatMode.ONCE to "Once", RepeatMode.COUNT to "N cycles",
                    RepeatMode.ENDLESS to "Endless").forEach { (mode, label) ->
                    FilterChip(
                        selected = plan.repeatMode == mode,
                        onClick = { onRepeat(mode) }, enabled = enabled,
                        modifier = Modifier.weight(1f),
                        label = { Text(label, fontSize = 12.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SeqColors.PrimaryDark,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            if (plan.repeatMode == RepeatMode.COUNT) {
                SeqCounter("Cycles", plan.repeatCount, 1, 999, 1, enabled, onCount)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("◷  Overall Time Limit", fontSize = 17.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = plan.maxDurationMinutes > 0,
                    onCheckedChange = { onLimit(if (it) 15 else 0) },
                    enabled = enabled,
                    colors = SwitchDefaults.colors(checkedTrackColor = SeqColors.Teal))
            }
            if (plan.maxDurationMinutes > 0)
                SeqCounter("Minutes", plan.maxDurationMinutes, 5, 10080, 5, enabled, onLimit)
            Text(if (plan.maxDurationMinutes == 0) "ⓘ No overall limit."
                else "ⓘ The test stops when the time limit is reached.",
                color = SeqColors.Muted, fontSize = 12.sp)
        }
        item { SeqPrimaryButton("Save Plan", onSave, Modifier.fillMaxWidth(), enabled) }
        item { Spacer(Modifier.height(14.dp)) }
    }
}

@Composable
private fun SeqCounter(label: String, value: Int, low: Int, high: Int,
    step: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    SeqCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = SeqColors.Muted)
            TextButton(onClick = { onChange((value - step).coerceAtLeast(low)) },
                enabled = enabled && value > low) { Text("−", fontSize = 23.sp) }
            Text(value.toString(), fontWeight = FontWeight.Bold)
            TextButton(onClick = { onChange((value + step).coerceAtMost(high)) },
                enabled = enabled && value < high) { Text("+", fontSize = 23.sp) }
        }
    }
}

@Composable
private fun SeqAppLetter(packageName: String, label: String) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(width = 96, height = 96).asImageBitmap()
        }.getOrNull()
    }
    Box(Modifier.size(43.dp).background(SeqColors.CardRaised,
        RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = label, modifier = Modifier.fillMaxSize())
        } else {
            Text(label.take(1).uppercase(), color = SeqColors.Teal, fontSize = 18.sp,
                fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun SeqTopBar(title: String, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            TextButton(onClick = onBack) {
                Text("‹", color = SeqColors.Text, fontSize = 30.sp)
            }
        }
        Text(title, fontWeight = FontWeight.Bold, fontSize = 23.sp)
    }
}

@Composable
private fun SequmioScenarios() {
    Column(Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SeqTopBar("Scenarios")
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("◇", fontSize = 35.sp, color = SeqColors.Sky)
                Text("E2E Recorder", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Coming in V2", color = SeqColors.Teal)
                Text("Record, edit and replay app-specific steps during a test run. " +
                    "This feature is not implemented in the current release.",
                    color = SeqColors.Muted)
            }
        }
    }
}

@Composable
private fun SequmioHistory(history: List<RunEvent>) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SeqTopBar("Test History") }
        item { Text("Local run events • " + history.size + " latest",
            color = SeqColors.Muted, fontSize = 13.sp) }
        if (history.isEmpty()) item {
            SeqCard(Modifier.fillMaxWidth()) {
                Text("No test runs yet.", Modifier.padding(22.dp), color = SeqColors.Muted)
            }
        }
        items(history) { event ->
            SeqCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("•", color = SeqColors.Teal, fontSize = 23.sp)
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text(event.message)
                        Text(SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault())
                            .format(Date(event.timestampMillis)), color = SeqColors.Muted,
                            fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun duration(seconds: Long): String =
    "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
