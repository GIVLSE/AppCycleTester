package com.sequmio.givlse

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.sequmio.givlse.data.RunReport
import com.sequmio.givlse.data.RunReportStore
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.sequmio.givlse.data.MyAppDraftStore
import com.sequmio.givlse.data.ScenarioCodec
import com.sequmio.givlse.data.PublishedApp
import com.sequmio.givlse.account.SequmioAccount
import com.sequmio.givlse.model.*
import com.sequmio.givlse.runner.CycleAccessibilityService
import com.sequmio.givlse.runner.RunnerRuntime
import com.sequmio.givlse.runner.RecorderRuntime
import java.text.SimpleDateFormat
import java.util.Date
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.sequmio.givlse.data.MyAppDraft
import java.util.Locale

private enum class Page { RUNNER, APP_LIST, MY_APP, HISTORY, PLAN, SETUP,
    SETTINGS, ABOUT, SUITE_MANAGER, SCENARIO_EDITOR, QA_DASHBOARD }

@Composable
internal fun SequmioApp(
    permissionRefreshToken: Int,
    systemAccessibilityEnabled: Boolean,
    resultOpenVersion: Int,
    requestNotificationPermission: () -> Unit,
    openAccessibility: () -> Unit,
    openAccessibilityList: () -> Unit,
    openDisplaySettings: () -> Unit,
    openAppDetails: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val apps = remember(permissionRefreshToken) { AppCatalog.list(context) }
    val runner by RunnerRuntime.state.collectAsState()
    var plan by remember { mutableStateOf(store.loadPlan()) }
    var consent by remember { mutableStateOf(store.accessConsentGiven()) }
    var page by remember { mutableStateOf(Page.RUNNER) }
    var settingsReturnPage by remember { mutableStateOf(Page.RUNNER) }
    var setupReturnPage by remember { mutableStateOf(Page.RUNNER) }
    val account = remember { SequmioAccount(context) }
    val draftStore = remember { MyAppDraftStore(context) }
    var ownAppDraft by remember { mutableStateOf(draftStore.load()) }
    var picker by remember { mutableStateOf(false) }
    var durationTarget by remember { mutableStateOf<TestTarget?>(null) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(runner.phase, resultOpenVersion) {
        if (runner.phase == RunPhase.FINISHED) {
            page = Page.RUNNER
            message = ""
        } else if (resultOpenVersion > 0 && runner.phase == RunPhase.IDLE) {
            page = Page.RUNNER
        }
    }
    var recorderNotice by remember { mutableStateOf("") }
    var selectedScenarioIndex by remember { mutableIntStateOf(0) }
    val recorder by RecorderRuntime.state.collectAsState()
    LaunchedEffect(recorder) {
        if (!recorder.recording && recorder.message.isNotBlank()) {
            if (recorder.message.startsWith("Test case created")) ownAppDraft = draftStore.load()
            recorderNotice = recorder.message
            RecorderRuntime.set(com.sequmio.givlse.runner.RecorderState())
        }
    }
    val connected = runner.accessibilityReady && CycleAccessibilityService.instance != null &&
        systemAccessibilityEnabled
    val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    val openPrivacy = {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://givlse.github.io/AppCycleTester/privacy.html"))
        )
    }
    val active = runner.phase == RunPhase.RUNNING || runner.phase == RunPhase.PAUSED
    val reportRevision by RunReportStore.revision.collectAsState()
    val history by produceState(emptyList<RunEvent>(), page, reportRevision) {
        if (page == Page.HISTORY) value = withContext(Dispatchers.IO) {
            store.readHistory().takeLast(120).reversed()
        }
    }
    val runReports by produceState(emptyList<RunReport>(), page, reportRevision, runner.phase) {
        if (page == Page.HISTORY || (page == Page.RUNNER && !active))
            value = withContext(Dispatchers.IO) { RunReportStore(context.applicationContext).recent() }
    }
    val visibleRunner = if (runner.phase == RunPhase.IDLE && runReports.isNotEmpty()) {
        val last = runReports.first()
        runner.copy(phase = RunPhase.FINISHED, terminalStatus = last.status,
            success = last.status == "PASS", message = last.reason,
            elapsedSeconds = last.durationSeconds, completedCycles = last.completedCycles)
    } else runner
    fun setPlan(p: TestPlan) { plan = p; store.savePlan(p) }
    fun syncEditedCase(oldRaw: String, newRaw: String, packageName: String) {
        if (active || oldRaw.isBlank()) return
        val next = plan.targets.map { target ->
            if (target.origin != "local" || target.packageName != packageName ||
                target.scenarioJson.isNullOrBlank()) target
            else runCatching {
                val cases = ScenarioCodec.split(target.scenarioJson).toMutableList()
                val position = cases.indexOfFirst { JSONObject(it).toString() == JSONObject(oldRaw).toString() }
                if (position < 0) target else {
                    val oldSpec = ScenarioCodec.parse(cases[position], packageName)
                    val newSpec = ScenarioCodec.parse(newRaw, packageName)
                    cases[position] = newRaw
                    val raw = ScenarioCodec.compose(cases)
                    val keys = target.selectedCaseKeys?.map {
                        if (it == ScenarioCodec.caseKey(position, oldSpec))
                            ScenarioCodec.caseKey(position, newSpec) else it
                    }
                    target.copy(scenarioJson = raw, selectedCaseKeys = keys,
                        durationSeconds = if (target.executionMode == ExecutionMode.RUN_TEST_CASES)
                            ScenarioCodec.selectCases(raw, packageName, keys)
                                .sumOf { it.totalDurationSec }.coerceIn(1, 7200)
                        else target.durationSeconds)
                }
            }.getOrDefault(target)
        }
        if (next != plan.targets) setPlan(plan.copy(targets = next))
    }
    fun mergeCommunity(
        changes: List<Pair<PublishedApp, InstalledApp>>,
        explicitJoin: Boolean
    ) {
        val imported = changes.mapNotNull { (published, installed) ->
            val spec = runCatching {
                ScenarioCodec.parseAll(published.scenarioJson, published.packageName)
            }.getOrNull() ?: return@mapNotNull null
            installed.packageName to TestTarget(
                installed.packageName, installed.activityClass, published.name,
                spec.sumOf { it.totalDurationSec }.coerceAtMost(7200),
                published.scenarioJson, "community"
            )
        }.toMap()
        if (imported.isEmpty()) return
        val next = plan.targets.map { existing ->
            val remote = imported[existing.packageName]
            if (remote != null && (explicitJoin || existing.origin == "community"))
                remote.copy(executionMode = if (explicitJoin && existing.scenarioJson.isNullOrBlank())
                    remote.executionMode else existing.executionMode,
                    durationSeconds = if (existing.executionMode == ExecutionMode.RUN_TEST_CASES)
                        remote.durationSeconds else existing.durationSeconds,
                    scenarioTimeoutSeconds = existing.scenarioTimeoutSeconds,
                    selectedCaseKeys = existing.selectedCaseKeys) else existing
        }.toMutableList()
        imported.values.forEach { remote ->
            if (next.none { it.packageName == remote.packageName }) next.add(remote)
        }
        if (next != plan.targets) setPlan(plan.copy(targets = next))
    }
    fun openSettings() {
        settingsReturnPage = when (page) {
            Page.SETUP, Page.PLAN, Page.SETTINGS -> Page.RUNNER
            else -> page
        }
        page = Page.SETTINGS
    }
    fun openPermissions(returnTo: Page) {
        setupReturnPage = returnTo
        page = Page.SETUP
    }
    fun start() {
        message = CycleAccessibilityService.instance?.let { it.start(plan).orEmpty() }
            ?: "Sequmio Runner is not connected"
        if (message.isEmpty()) page = Page.RUNNER
    }
    fun requestStart() {
        if (plan.targets.isEmpty()) page = Page.PLAN
        else if (!consent || !connected) openPermissions(Page.RUNNER)
        else start()
    }
    val mainTabs = listOf(Page.RUNNER, Page.APP_LIST, Page.MY_APP, Page.HISTORY)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        if (page in mainTabs) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SEQUMIO", color = SeqColors.Muted, fontSize = 11.sp,
                    letterSpacing = 2.4.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                SeqIconAction(
                    Icons.Outlined.Settings, "Settings", onClick = { openSettings() },
                    tint = SeqColors.Primary
                )
            }
        }
        Box(Modifier.weight(1f)) {
            when (page) {
                Page.RUNNER -> Box(Modifier.fillMaxSize()) {
                    SeqRunnerAtmosphere(Modifier.matchParentSize())
                    SequmioHome(
                    plan, visibleRunner, connected, systemAccessibilityEnabled, message,
                    onStart = { requestStart() },
                    onEdit = { page = Page.PLAN },
                    onSetup = { openPermissions(Page.RUNNER) },
                    onPause = { CycleAccessibilityService.instance?.pause() },
                    onResume = { CycleAccessibilityService.instance?.resume() },
                    onStop = { CycleAccessibilityService.instance?.stop() }
                    )
                }
                Page.PLAN -> SequmioPlan(
                    plan = plan, enabled = !active,
                    onBack = { page = Page.RUNNER },
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
                    onSave = { page = Page.RUNNER }
                )
                Page.APP_LIST -> SequmioCommunityCatalog(
                    account = account,
                    installed = apps,
                    refreshToken = permissionRefreshToken,
                    onOpenSettings = { openSettings() },
                    onJoined = { published, installed ->
                        mergeCommunity(listOf(published to installed), true)
                    },
                    onSync = { list -> mergeCommunity(list, false) },
                    onLocalTest = { installed ->
                        if (plan.targets.none { it.packageName == installed.packageName }) {
                            setPlan(plan.copy(targets = plan.targets + TestTarget(
                                installed.packageName, installed.activityClass,
                                installed.label
                            )))
                        }
                        page = Page.RUNNER
                    }
                )
                Page.MY_APP -> SequmioDeveloperApp(
                    initial = ownAppDraft,
                    account = account,
                    onSave = { updated ->
                        draftStore.save(updated)
                        ownAppDraft = updated
                    },
                    onOpenSettings = { openSettings() },
                    onEditCase = { draft, index ->
                        draftStore.save(draft)
                        ownAppDraft = draft
                        selectedScenarioIndex = index
                        page = Page.SCENARIO_EDITOR
                    },
                    onDashboard = { draft ->
                        draftStore.save(draft)
                        ownAppDraft = draft
                        page = Page.QA_DASHBOARD
                    },
                    recorderState = recorder,
                    recorderNotice = recorderNotice,
                    onDismissRecorderNotice = { recorderNotice = "" },
                    onTest = { draft ->
                        recorderNotice = ""
                        val installed = apps.firstOrNull {
                            it.packageName == draft.packageName
                        }
                        val parsed = runCatching {
                            ScenarioCodec.parseAll(draft.scenarioJson, draft.packageName)
                        }
                        when {
                            active -> recorderNotice = "Stop the current run before editing."
                            parsed.isFailure -> recorderNotice =
                                "Scenario invalid: " + (parsed.exceptionOrNull()?.message ?: "")
                            installed == null -> recorderNotice =
                                "Your application is not installed on this device."
                            else -> {
                                val specs = parsed.getOrThrow()
                                val target = TestTarget(
                                    installed.packageName, installed.activityClass, draft.appName,
                                    specs.sumOf { it.totalDurationSec }.coerceAtMost(7200),
                                    draft.scenarioJson, "local"
                                )
                                val next = plan.targets.filterNot {
                                    it.packageName == installed.packageName
                                } + target
                                setPlan(plan.copy(targets = next))
                                page = Page.RUNNER
                                message = "Your scenario is in Runner. Press Start to execute it."
                            }
                        }
                    },
                    onRecord = { draft ->
                        recorderNotice = ""
                        draftStore.save(draft)
                        ownAppDraft = draft
                        val selected = apps.firstOrNull { it.packageName == draft.packageName }
                        when {
                            !consent -> {
                                recorderNotice = "Review the updated recording disclosure first."
                                openPermissions(Page.MY_APP)
                            }
                            !connected -> {
                                recorderNotice = if (systemAccessibilityEnabled)
                                    "Runner service not connected yet." else "Enable Sequmio Accessibility first."
                                openPermissions(Page.MY_APP)
                            }
                            selected == null -> recorderNotice =
                                "Install your app on this device before recording."
                            else -> {
                                recorderNotice = CycleAccessibilityService.instance
                                    ?.startRecording(selected).orEmpty()
                                if (CycleAccessibilityService.instance == null)
                                    recorderNotice = "Enable Sequmio Accessibility first."
                            }
                        }
                    }
                )
                Page.SUITE_MANAGER -> SequmioSuiteManager(
                    initial = ownAppDraft,
                    onSave = { updated ->
                        draftStore.save(updated)
                        ownAppDraft = updated
                    },
                    onEdit = { chosen ->
                        selectedScenarioIndex = chosen
                        page = Page.SCENARIO_EDITOR
                    },
                    onBack = { page = Page.MY_APP }
                )
                Page.SCENARIO_EDITOR -> SequmioVisualScenarioEditor(
                    initial = ownAppDraft.copy(
                        scenarioJson = runCatching {
                            ScenarioCodec.split(ownAppDraft.scenarioJson)
                                .getOrNull(selectedScenarioIndex)
                        }.getOrNull().orEmpty()
                    ),
                    onSave = { updated ->
                        val cases = runCatching {
                            ScenarioCodec.split(ownAppDraft.scenarioJson).toMutableList()
                        }.getOrElse { mutableListOf() }
                        if (selectedScenarioIndex in cases.indices) {
                            syncEditedCase(cases[selectedScenarioIndex], updated.scenarioJson, ownAppDraft.packageName)
                            cases[selectedScenarioIndex] = updated.scenarioJson
                        }
                        else cases.add(updated.scenarioJson)
                        val next = ownAppDraft.copy(
                            scenarioJson = ScenarioCodec.compose(cases)
                        )
                        draftStore.save(next)
                        ownAppDraft = next
                        page = Page.MY_APP
                    },
                    onBack = { page = Page.MY_APP }
                )
                Page.QA_DASHBOARD -> SequmioQaDashboard(
                    appId = ownAppDraft.packageName,
                    account = account,
                    onBack = { page = Page.MY_APP }
                )
                Page.HISTORY -> SequmioHistory(history, runReports)
                Page.SETTINGS -> SequmioSettings(
                    account = account,
                    onBack = { page = settingsReturnPage },
                    onPermissions = { openPermissions(Page.SETTINGS) },
                    onDisplaySettings = openDisplaySettings,
                    onAbout = { page = Page.ABOUT }
                )
                Page.ABOUT -> {
                    androidx.activity.compose.BackHandler { page = Page.SETTINGS }
                    SequmioAbout(onBack = { page = Page.SETTINGS })
                }
                Page.SETUP -> SequmioAccessSetup(
                    ready = connected,
                    systemEnabled = systemAccessibilityEnabled,
                    notificationsGranted = notificationsGranted,
                    consent = consent,
                    onConsent = { store.saveAccessConsent(); consent = true },
                    onBack = { page = setupReturnPage },
                    onAppInfo = openAppDetails,
                    onAccessibility = openAccessibility,
                    onAccessibilityList = openAccessibilityList,
                    onNotifications = requestNotificationPermission,
                    onDisplaySettings = openDisplaySettings,
                    onPrivacy = openPrivacy,
                    onStart = { start() }
                )
            }
        }
        if (page in mainTabs) {
            SeqNeonNavigation(
                selected = mainTabs.indexOf(page),
                onSelect = { index -> page = mainTabs[index] }
            )
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
        SequmioDurationDialog(target, onDismiss = { durationTarget = null }) { updated ->
            setPlan(plan.copy(targets = plan.targets.map {
                if (it.packageName == target.packageName)
                    updated else it
            }))
            durationTarget = null
        }
    }
}

@Composable
private fun SequmioHome(
    plan: TestPlan, runner: RunSnapshot, connected: Boolean, systemEnabled: Boolean, message: String,
    onStart: () -> Unit, onEdit: () -> Unit, onSetup: () -> Unit,
    onPause: () -> Unit, onResume: () -> Unit, onStop: () -> Unit
) {
    val active = runner.phase == RunPhase.RUNNING || runner.phase == RunPhase.PAUSED
    var appsCollapsed by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(runner.phase) {
        if (runner.phase == RunPhase.FINISHED) listState.scrollToItem(0)
    }
    val cycleSeconds = plan.targets.sumOf { target ->
        if (target.executionMode == ExecutionMode.RUN_TEST_CASES)
            runCatching { ScenarioCodec.selectCases(target.scenarioJson.orEmpty(), target.packageName, target.selectedCaseKeys)
                .sumOf { it.totalDurationSec } }.getOrDefault(target.durationSeconds)
        else target.durationSeconds
    }.toLong()
    val planned = when (plan.repeatMode) {
        RepeatMode.ONCE -> cycleSeconds
        RepeatMode.COUNT -> cycleSeconds * plan.repeatCount
        RepeatMode.ENDLESS -> null
    }
    val seconds = if (plan.maxDurationMinutes > 0) {
        planned?.coerceAtMost(plan.maxDurationMinutes * 60L) ?: plan.maxDurationMinutes * 60L
    } else planned
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 26.dp, top = 3.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (runner.phase == RunPhase.FINISHED) item {
            val status = runner.terminalStatus ?: if (runner.success == true) "PASS" else "FAIL"
            val tint = when (status) {
                "PASS" -> SeqColors.Success
                "STOPPED" -> SeqColors.Primary
                else -> SeqColors.Error
            }
            SeqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (status == "PASS") Icons.Outlined.CheckCircle
                            else if (status == "STOPPED") Icons.Outlined.StopCircle
                            else Icons.Outlined.ErrorOutline, null,
                            tint = tint, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Run finished", Modifier.weight(1f),
                            fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        SeqChip(status, tint)
                    }
                    Text(runner.message, color = SeqColors.Text, fontSize = 13.sp)
                    Text("Elapsed " + duration(runner.elapsedSeconds) + "  •  " +
                        runner.completedCycles + " completed cycles",
                        color = SeqColors.Muted, fontSize = 12.sp)
                }
            }
        }
        item {
            SeqRunnerHero()
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SeqMetric(Icons.Outlined.Layers, plan.targets.size.toString(),
                    "Apps", Modifier.weight(1f))
                SeqMetric(Icons.Outlined.Schedule,
                    seconds?.let { targetTimeLabel(it.coerceAtMost(720000L).toInt()) }
                        ?: "∞",
                    if (plan.targets.any { it.executionMode != ExecutionMode.FIXED_DURATION })
                        "Est. Duration" else "Duration", Modifier.weight(1f))
                SeqMetric(Icons.Outlined.Repeat,
                    when (plan.repeatMode) {
                        RepeatMode.ONCE -> "1×"
                        RepeatMode.COUNT -> plan.repeatCount.toString() + "×"
                        RepeatMode.ENDLESS -> "∞"
                    }, "Cycles", Modifier.weight(1f))
            }
        }
        item {
            SeqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SeqSectionTitle(
                        title = "Test Plan", icon = Icons.Outlined.Assignment,
                        trailing = {
                            SeqTextButton(onClick = onEdit, enabled = !active) {
                                Icon(Icons.Outlined.Edit, contentDescription = null,
                                    modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Edit", fontSize = 13.sp)
                            }
                        }
                    )
                    if (active) {
                        Text((runner.activeApp ?: "Current app") + "  •  " +
                            duration(runner.remainingSeconds) + " remaining",
                            color = SeqColors.Teal, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            SeqPrimaryButton(
                                title = if (runner.phase == RunPhase.RUNNING) "Pause" else "Resume",
                                onClick = if (runner.phase == RunPhase.RUNNING) onPause else onResume,
                                modifier = Modifier.weight(1f),
                                enabled = plan.targets.getOrNull(runner.currentIndex)?.executionMode ==
                                    ExecutionMode.FIXED_DURATION,
                                icon = if (runner.phase == RunPhase.RUNNING)
                                    Icons.Outlined.Pause else Icons.Outlined.PlayArrow
                            )
                            OutlinedButton(
                                onClick = onStop, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(15.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = SeqColors.Error
                                )
                            ) {
                                Icon(Icons.Outlined.Stop, contentDescription = null,
                                    modifier = Modifier.size(19.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Stop")
                            }
                        }
                    } else {
                        SeqPrimaryButton(
                            title = if (plan.targets.isEmpty()) "Create Test Plan" else "Start Plan",
                            onClick = onStart,
                            modifier = Modifier.fillMaxWidth(),
                            icon = if (plan.targets.isEmpty())
                                Icons.Outlined.Add else Icons.Outlined.PlayArrow
                        )
                    }
                    if (plan.targets.isEmpty()) {
                        Text("Add applications to define your test plan.",
                            color = SeqColors.Muted, fontSize = 12.sp)
                    } else {
                        if (!appsCollapsed) {
                            Column(
                                Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                plan.targets.forEachIndexed { index, target ->
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clickable(enabled = !active) { onEdit() }
                                            .padding(vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SeqAppLetter(target.packageName, target.label)
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(target.label,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                executionLabel(target) +
                                                    if (target.origin == "community")
                                                        " · Community" else " · Local plan",
                                                fontSize = 11.sp, color = SeqColors.Muted
                                            )
                                        }
                                        if (active && runner.currentIndex == index) {
                                            SeqChip("Running", SeqColors.Teal)
                                        } else {
                                            SeqChip("Included", SeqColors.Success,
                                                icon = Icons.Outlined.Check)
                                        }
                                        if (!active) {
                                            Icon(
                                                Icons.Outlined.ChevronRight,
                                                contentDescription = "Edit " + target.label,
                                                tint = SeqColors.Muted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    if (index != plan.targets.lastIndex) {
                                        HorizontalDivider(
                                            color = SeqColors.Border.copy(alpha = 0.45f),
                                            thickness = 0.6.dp
                                        )
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center) {
                            SeqTextButton(onClick = { appsCollapsed = !appsCollapsed }) {
                                Text(if (appsCollapsed) "Show apps (" + plan.targets.size + ")"
                                    else "Hide apps", fontSize = 12.sp, color = SeqColors.Muted)
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    if (appsCollapsed) Icons.Outlined.KeyboardArrowDown
                                        else Icons.Outlined.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = SeqColors.Muted, modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            SeqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SeqSectionTitle(
                        "Runner Status", Icons.Outlined.Timeline,
                        trailing = {
                            SeqChip(
                                if (connected) "Connected" else if (systemEnabled) "Disconnected" else "Setup required",
                                if (connected) SeqColors.Success else if (systemEnabled) SeqColors.Primary else SeqColors.Error
                            )
                        }
                    )
                    if (!connected) {
                        Text(if (systemEnabled)
                            "Android access is enabled. Waiting for the Runner service to connect."
                            else "Accessibility access is needed to run test plans.",
                            color = SeqColors.Muted, fontSize = 12.sp)
                        SeqTextButton(onClick = onSetup) { Text("Manage permissions") }
                    } else if (runner.phase == RunPhase.FINISHED) {
                        Text(
                            "Last run: " + (runner.terminalStatus ?: if (runner.success == true) "PASS" else "FAIL"),
                            color = when (runner.terminalStatus) {
                                "STOPPED" -> SeqColors.Primary
                                "PASS" -> SeqColors.Success
                                else -> if (runner.success == true) SeqColors.Success else SeqColors.Error
                            },
                            fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Elapsed " + duration(runner.elapsedSeconds) + "  •  " +
                                runner.completedCycles + " completed cycles",
                            color = SeqColors.Muted, fontSize = 12.sp
                        )
                    } else {
                        Text(
                            if (active) "Running • " + duration(runner.elapsedSeconds)
                            else "Ready for testing",
                            color = SeqColors.Muted, fontSize = 14.sp
                        )
                    }
                    if (message.isNotEmpty()) {
                        Text(message, color = SeqColors.Error, fontSize = 12.sp)
                    }
                    if (connected) {
                        SeqTextButton(onClick = onSetup) {
                            Icon(Icons.Outlined.Tune, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Permissions & access", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeqMetric(
    icon: ImageVector, amount: String, label: String, modifier: Modifier
) {
    SeqCard(modifier, neon = true) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = null, tint = SeqColors.Teal,
                modifier = Modifier.size(23.dp))
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
                SeqTextButton(onClick = onAdd, enabled = enabled) {
                    Icon(Icons.Outlined.AddCircleOutline, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Add App")
                }
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
                        Text(executionLabel(target) +
                            (if (target.origin == "community") " • Community" else " • edit"),
                            color = SeqColors.Muted, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                            SeqIconAction(Icons.Outlined.KeyboardArrowUp, "Move app up",
                                onClick = { onUp(index, -1) },
                                enabled = enabled && index > 0)
                            SeqIconAction(Icons.Outlined.KeyboardArrowDown, "Move app down",
                                onClick = { onUp(index, 1) },
                                enabled = enabled && index < plan.targets.lastIndex)
                        }
                        SeqTextButton(onClick = { onRemove(target.packageName) },
                            enabled = enabled) {
                            Icon(Icons.Outlined.DeleteOutline,
                                contentDescription = null, modifier = Modifier.size(17.dp))
                            Text("Remove", fontSize = 11.sp)
                        }
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
                            selectedLabelColor = Color.White,
                            labelColor = SeqColors.Text,
                            disabledLabelColor = SeqColors.Muted
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
                Icon(Icons.Outlined.Schedule, contentDescription = null,
                    tint = SeqColors.Teal, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Overall Time Limit", fontSize = 17.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = plan.maxDurationMinutes > 0,
                    onCheckedChange = { onLimit(if (it) 15 else 0) },
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = SeqColors.Teal,
                        checkedThumbColor = SeqColors.Background,
                        uncheckedTrackColor = SeqColors.CardRaised,
                        uncheckedThumbColor = SeqColors.Muted,
                        uncheckedBorderColor = SeqColors.Border,
                        checkedBorderColor = SeqColors.Teal
                    ))
            }
            if (plan.maxDurationMinutes > 0)
                SeqCounter("Minutes", plan.maxDurationMinutes, 5, 10080, 5, enabled, onLimit)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null,
                    tint = SeqColors.Muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (plan.maxDurationMinutes == 0) "No overall limit."
                    else "The test stops at the configured time limit.",
                    color = SeqColors.Muted, fontSize = 12.sp)
            }
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
            SeqIconAction(Icons.Outlined.Remove, "Decrease " + label,
                onClick = { onChange((value - step).coerceAtLeast(low)) },
                enabled = enabled && value > low)
            Text(value.toString(), fontWeight = FontWeight.Bold)
            SeqIconAction(Icons.Outlined.Add, "Increase " + label,
                onClick = { onChange((value + step).coerceAtMost(high)) },
                enabled = enabled && value < high)
        }
    }
}

@Composable
internal fun SeqAppLetter(packageName: String, label: String,
    refreshToken: Int = 0, publishedIcon: String = "") {
    val context = LocalContext.current
    val icon = remember(packageName, refreshToken, publishedIcon) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(width = 96, height = 96).asImageBitmap()
        }.getOrNull() ?: com.sequmio.givlse.data.AppIconCodec.decode(publishedIcon)?.asImageBitmap()
    }
    Box(Modifier.size(43.dp).background(SeqColors.CardRaised,
        RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = label,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)))
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
            SeqIconAction(Icons.Outlined.ArrowBack, "Back", onClick = onBack,
                tint = SeqColors.Text)
        }
        Text(title, fontWeight = FontWeight.Bold, fontSize = 23.sp)
    }
}

@Composable
private fun SequmioHistory(history: List<RunEvent>, reports: List<RunReport>) {
    val context = LocalContext.current
    var expandedReportId by remember { mutableStateOf<String?>(null) }
    var showAllEvents by rememberSaveable { mutableStateOf(false) }
    val exportScope = rememberCoroutineScope()
    fun share(report: RunReport, csv: Boolean) {
        exportScope.launch {
        val data = withContext(Dispatchers.IO) { if (csv) report.csv else report.rawJson }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (csv) "text/csv" else "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "Sequmio QA session " + report.id)
            putExtra(Intent.EXTRA_TEXT, data)
        }
        context.startActivity(Intent.createChooser(intent, "Share local QA report"))
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SeqNeonHeader(SeqHeroKind.HISTORY, "Test History",
                "Local QA sessions · " + reports.size + " latest")
        }
        if (reports.isEmpty() && history.isEmpty()) {
            item {
                SeqCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Outlined.AssignmentTurnedIn, null,
                            tint = SeqColors.Teal, modifier = Modifier.size(29.dp))
                        Text("No test runs yet", fontWeight = FontWeight.SemiBold)
                        Text("Run your first Test Plan to see session results here.",
                            color = SeqColors.Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        items(reports, key = { it.id }) { report ->
            val tint = when (report.status) {
                "PASS" -> SeqColors.Success
                "STOPPED" -> SeqColors.Primary
                else -> SeqColors.Error
            }
            val statusIcon = when (report.status) {
                "PASS" -> Icons.Outlined.CheckCircle
                "STOPPED" -> Icons.Outlined.StopCircle
                else -> Icons.Outlined.ErrorOutline
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = SeqColors.Card,
                border = BorderStroke(1.dp, tint.copy(alpha = 0.45f))
            ) {
                Column(
                    Modifier.background(Brush.horizontalGradient(listOf(
                        tint.copy(alpha = 0.14f), Color.Transparent)))
                        .padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(statusIcon, contentDescription = report.status,
                            tint = tint, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(report.status, color = tint,
                            fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.weight(1f))
                        Text(
                            SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                                .format(Date(report.startedAt)),
                            color = SeqColors.Muted, fontSize = 11.sp
                        )
                    }
                    Text(
                        "Elapsed " + duration(report.durationSeconds) +
                            "  •  " + report.completedCycles + " cycles",
                        color = SeqColors.Text, fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(report.reason, color = SeqColors.Muted, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { share(report, false) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = SeqColors.Primary
                            ),
                            border = BorderStroke(
                                1.dp, SeqColors.Primary.copy(alpha = 0.27f))
                        ) {
                            Icon(Icons.Outlined.DataObject, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("JSON", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { share(report, true) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = SeqColors.Primary
                            ),
                            border = BorderStroke(
                                1.dp, SeqColors.Primary.copy(alpha = 0.27f))
                        ) {
                            Icon(Icons.Outlined.Description, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("CSV", fontSize = 12.sp)
                        }
                    }
                    SeqTextButton(onClick = {
                        expandedReportId = if (expandedReportId == report.id)
                            null else report.id
                    }) {
                        Icon(
                            if (expandedReportId == report.id)
                                Icons.Outlined.KeyboardArrowUp
                            else Icons.Outlined.KeyboardArrowDown,
                            null, Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (expandedReportId == report.id) "Hide test case details"
                            else "Show test cases & step results",
                            fontSize = 12.sp
                        )
                    }
                    if (expandedReportId == report.id) {
                        val parsed = remember(report.id) {
                            runCatching { JSONObject(report.rawJson) }.getOrNull()
                        }
                        val targets = parsed?.optJSONArray("targets") ?: JSONArray()
                        for (i in 0 until targets.length()) {
                            val target = targets.optJSONObject(i) ?: continue
                            HorizontalDivider(
                                color = SeqColors.Border.copy(alpha = 0.55f))
                            Text(target.optString("label"),
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            val scenarios = target.optJSONArray("scenarios") ?: JSONArray()
                            if (scenarios.length() == 0) {
                                Text("No structured E2E steps for this target",
                                    color = SeqColors.Muted, fontSize = 12.sp)
                            }
                            for (j in 0 until scenarios.length()) {
                                val test = scenarios.optJSONObject(j) ?: continue
                                Text(
                                    test.optString("suite") + " · " +
                                        test.optString("name") + " v" +
                                        test.optInt("version") + " · " +
                                        if (test.optString("status") == "RUNNING") "INCOMPLETE"
                                        else test.optString("status"),
                                    fontSize = 12.sp, color = if (test.optString("status") == "PASS")
                                        SeqColors.Success else SeqColors.Muted
                                )
                                val rows = test.optJSONArray("steps") ?: JSONArray()
                                if (test.has("cycle")) {
                                    Text("Cycle " + test.optInt("cycle") + " · Iteration " +
                                        test.optInt("iteration") + " · Estimated " +
                                        test.optInt("estimatedSeconds") + "s · Actual " +
                                        test.optLong("actualSeconds") + "s · Timeout " +
                                        test.optInt("timeoutSeconds") + "s",
                                        fontSize = 11.sp, color = SeqColors.Muted)
                                    val statuses = (0 until rows.length()).map {
                                        rows.optJSONObject(it)?.optString("status") }
                                    Text("Steps: " + statuses.count { it == "PASS" } + " PASS · " +
                                        statuses.count { it == "FAIL" } + " FAIL · " +
                                        statuses.count { it == "NOT_EXECUTED" || it == "SKIPPED" } +
                                        " skipped / not executed · " +
                                        statuses.count { it in listOf("BLOCKED", "TIMEOUT", "STOPPED") } +
                                        " interrupted",
                                        color = SeqColors.Muted, fontSize = 11.sp)
                                }
                                if (test.optString("reason").isNotBlank())
                                    Text(test.optString("reason"), color = SeqColors.Error, fontSize = 12.sp)
                                for (k in 0 until rows.length()) {
                                    val step = rows.optJSONObject(k) ?: continue
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            if (step.optString("status") == "PASS")
                                                Icons.Outlined.CheckCircle
                                            else Icons.Outlined.ErrorOutline,
                                            null, Modifier.size(15.dp),
                                            tint = if (step.optString("status") == "PASS")
                                                SeqColors.Success else SeqColors.Error
                                        )
                                        Text(
                                            "#" + step.optInt("stepIndex") + " " +
                                                step.optString("action") + " · " +
                                                step.optString("status") + " (" +
                                                step.optLong("durationMs") + " ms)",
                                            fontSize = 11.sp, color = SeqColors.Muted
                                        )
                                    }
                                    if (step.optString("expectedResult").isNotBlank())
                                        Text("ER: " + step.optString("expectedResult"),
                                            color = SeqColors.Muted, fontSize = 11.sp)
                                    if (step.optString("actualResult").isNotBlank())
                                        Text("AR: " + step.optString("actualResult"),
                                            color = SeqColors.Muted, fontSize = 11.sp)
                                    if (step.optString("status") != "PASS")
                                        Text(step.optString("message"), color = SeqColors.Muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(7.dp))
            SeqSectionTitle("Event Log", Icons.Outlined.Timeline)
            Text(history.size.toString() + " local events",
                color = SeqColors.Muted, fontSize = 12.sp)
        }
        items(if (showAllEvents) history else history.take(25)) { event ->
            val isError = event.message.contains("fail", ignoreCase = true)
            val isStopped = event.message.contains("stop", ignoreCase = true)
            val tint = if (isError) SeqColors.Error else if (isStopped)
                SeqColors.Primary else SeqColors.Teal
            Row(Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(21.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(10.dp).background(tint, CircleShape))
                    Box(Modifier.width(1.dp).height(47.dp).background(
                        SeqColors.Border.copy(alpha = 0.65f)))
                }
                Spacer(Modifier.width(7.dp))
                SeqCard(Modifier.weight(1f)) {
                    Row(Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isError) Icons.Outlined.ErrorOutline
                            else if (isStopped) Icons.Outlined.StopCircle
                            else Icons.Outlined.CheckCircle,
                            null, Modifier.size(18.dp), tint = tint
                        )
                        Spacer(Modifier.width(9.dp))
                        Column {
                            Text(event.message,
                                color = SeqColors.Text, fontSize = 13.sp,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(
                                SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault())
                                    .format(Date(event.timestampMillis)),
                                color = SeqColors.Muted, fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
        if (history.size > 25) {
            item {
                SeqTextButton(
                    onClick = { showAllEvents = !showAllEvents },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showAllEvents) "Show fewer events" else
                        "Show all " + history.size + " events")
                    Icon(
                        if (showAllEvents) Icons.Outlined.KeyboardArrowUp
                            else Icons.Outlined.KeyboardArrowDown,
                        null, Modifier.size(20.dp)
                    )
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

private fun duration(seconds: Long): String =
    "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
