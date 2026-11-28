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
import com.sequmio.givlse.data.HistoryRunSummary
import com.sequmio.givlse.data.RunReportStore
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.sequmio.givlse.runner.RunCompletion
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
import com.sequmio.givlse.data.ResultReturnStatus
import com.sequmio.givlse.data.MyAppDraftStore
import com.sequmio.givlse.data.ScenarioCodec
import com.sequmio.givlse.data.PublishedApp
import com.sequmio.givlse.data.toDraft
import com.sequmio.givlse.account.SequmioAccount
import com.sequmio.givlse.model.*
import com.sequmio.givlse.runner.CycleAccessibilityService
import com.sequmio.givlse.runner.RunnerRuntime
import com.sequmio.givlse.runner.RecorderRuntime
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sequmio.givlse.data.PlanCaseUpdates
import com.sequmio.givlse.data.OwnerPlanSync
import com.sequmio.givlse.data.JoinedPlanSync
import com.sequmio.givlse.data.CommunityRepository
import java.util.Locale

private const val RUNNER_DISCONNECTED_MESSAGE =
    "Android access is enabled, but Runner is not connected. Retry connection below."

private enum class Page { RUNNER, APP_LIST, MY_APP, HISTORY, PLAN, SETUP,
    SETTINGS, ABOUT, SUITE_MANAGER, SCENARIO_EDITOR, QA_DASHBOARD, QA_PROGRESS }

@Composable
internal fun SequmioApp(
    permissionRefreshToken: Int,
    systemAccessibilityEnabled: Boolean,
    resultOpenVersion: Int,
    requestNotificationPermission: () -> Unit,
    refreshRunnerConnection: () -> Unit,
    openAccessibility: () -> Unit,
    openAccessibilityList: () -> Unit,
    openDisplaySettings: () -> Unit,
    openAppDetails: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    var returnStatus by remember { mutableStateOf(store.resultReturnStatus()) }
    var returnHintDismissed by remember { mutableStateOf(store.resultReturnHintDismissed()) }
    DisposableEffect(store) {
        val unsubscribe = store.observeResultReturnSettings {
            returnStatus = store.resultReturnStatus()
            returnHintDismissed = store.resultReturnHintDismissed()
        }
        onDispose { unsubscribe() }
    }
    val apps = remember(permissionRefreshToken) { AppCatalog.list(context) }
    val runner by RunnerRuntime.state.collectAsState()
    var plan by remember { mutableStateOf(store.loadPlan()) }
    var consent by remember { mutableStateOf(store.accessConsentGiven()) }
    var page by remember { mutableStateOf(Page.RUNNER) }
    var settingsReturnPage by remember { mutableStateOf(Page.RUNNER) }
    var setupReturnPage by remember { mutableStateOf(Page.RUNNER) }
    val account = remember { SequmioAccount(context) }
    val ownerUid = account.profile?.uid
    var joinedPublished by remember(ownerUid) { mutableStateOf<List<PublishedApp>?>(null) }
    var excludedJoined by remember(ownerUid) {
        mutableStateOf(ownerUid?.let(store::excludedJoinedApps).orEmpty())
    }
    var joinedRefresh by remember(ownerUid) { mutableIntStateOf(0) }
    var queuedJoined by remember(ownerUid) {
        mutableStateOf<List<Pair<PublishedApp, InstalledApp>>>(emptyList())
    }
    LaunchedEffect(ownerUid, permissionRefreshToken, joinedRefresh) {
        val uid = ownerUid ?: return@LaunchedEffect
        try {
            val snapshot = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                CommunityRepository(account).joinedPublishedApps(uid)
            } ?: error("Joined apps sync timed out")
            if (account.profile?.uid == uid) joinedPublished = snapshot
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (error: Exception) {
            // No authoritative response: retain the last verified snapshot and local plan.
            android.util.Log.w("SequmioSync", "Joined apps sync unavailable", error)
        }
    }
    val draftStore = remember(ownerUid) { MyAppDraftStore(context, ownerUid) }
    var ownAppDraft by remember(ownerUid) { mutableStateOf(draftStore.load()) }
    var ownedChoices by remember(ownerUid) { mutableStateOf(draftStore.choices()) }
    var publishedOwned by remember(ownerUid) {
        mutableStateOf(if (ownerUid == null) emptyList() else draftStore.publishedDrafts())
    }
    var excludedOwned by remember(ownerUid) {
        mutableStateOf(ownerUid?.let(store::excludedOwnedApps).orEmpty())
    }
    var initialOwnerSyncDone by remember(ownerUid) { mutableStateOf(false) }
    var draftRevision by remember(ownerUid) { mutableIntStateOf(0) }
    var restoreRevision by remember(ownerUid) { mutableIntStateOf(0) }
    val onMyApp = page == Page.MY_APP
    var restoring by remember(ownerUid, onMyApp, restoreRevision) {
        mutableStateOf(ownerUid != null && onMyApp)
    }
    var restoreError by remember(ownerUid, onMyApp, restoreRevision) { mutableStateOf(false) }
    fun reloadDraft() {
        ownAppDraft = draftStore.load()
        ownedChoices = draftStore.choices()
        draftRevision++
    }
    fun saveDraft(updated: com.sequmio.givlse.data.MyAppDraft) {
        draftStore.save(updated)
        ownAppDraft = updated
        ownedChoices = draftStore.choices()
    }
    LaunchedEffect(ownerUid, onMyApp, restoreRevision) {
        if (ownerUid != null && (onMyApp || !initialOwnerSyncDone)) {
            try {
                val owned = kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                    com.sequmio.givlse.data.CommunityRepository(account).myApps(ownerUid)
                } ?: error("Cloud sync timed out")
                if (account.profile?.uid == ownerUid) {
                    draftStore.restoreOwned(owned)
                    publishedOwned = draftStore.publishedDrafts()
                    initialOwnerSyncDone = true
                    reloadDraft()
                }
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                restoreError = true
            } finally { restoring = false }
        }
    }
    LaunchedEffect(ownerUid) {
        if (page in listOf(Page.SCENARIO_EDITOR, Page.SUITE_MANAGER, Page.QA_DASHBOARD))
            page = Page.MY_APP
    }
    var picker by remember { mutableStateOf(false) }
    var durationTarget by remember { mutableStateOf<TestTarget?>(null) }
    var message by remember { mutableStateOf("") }
    val completion by RunnerRuntime.completion.collectAsState()
    LaunchedEffect(completion?.id) {
        if (completion != null) {
            page = Page.RUNNER
            message = ""
        }
    }
    // A notification may reopen the Activity, but never resurrect an old popup.
    LaunchedEffect(resultOpenVersion) {
        if (resultOpenVersion > 0) page = Page.RUNNER
    }
    var recorderNotice by remember(ownerUid) { mutableStateOf("") }
    var selectedScenarioIndex by remember(ownerUid) { mutableIntStateOf(0) }
    val recorder by RecorderRuntime.state.collectAsState()
    LaunchedEffect(recorder) {
        if (!recorder.recording && recorder.message.isNotBlank()) {
            if (recorder.message.startsWith("Test case created")) reloadDraft()
            recorderNotice = recorder.message
            RecorderRuntime.set(com.sequmio.givlse.runner.RecorderState())
        }
    }
    // Bind status is independent of a test session's snapshot and survives
    // Activity recreation while Android keeps the actual service connected.
    val liveService by CycleAccessibilityService.connection.collectAsState()
    val connected = systemAccessibilityEnabled && liveService != null
    LaunchedEffect(connected) {
        if (connected && message == RUNNER_DISCONNECTED_MESSAGE) message = ""
    }
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
    LaunchedEffect(ownerUid, joinedPublished, apps) {
        val uid = ownerUid
        if (uid != null && account.profile?.uid == uid && joinedPublished != null) {
            com.sequmio.givlse.data.QaProgressStore(context).setJoined(uid,
                joinedPublished.orEmpty().map { it.packageName }, apps.map { it.packageName }.toSet())
        }
        com.sequmio.givlse.data.QaReminderScheduler.reconcile(context)
    }
    val runReports by produceState(emptyList<HistoryRunSummary>(), page, reportRevision) {
        if (page == Page.HISTORY) value = withContext(Dispatchers.IO) {
            RunReportStore(context.applicationContext).recent().map(HistoryRunSummary::fromReport)
        }
    }
    fun setPlan(p: TestPlan) { plan = p; store.savePlan(p) }
    // Auto-import after sign-in even if My App or Community has never been opened.
    // Wait until the current run/dialog ends before modifying its plan.
    LaunchedEffect(ownerUid, publishedOwned, excludedOwned, apps, active, durationTarget, picker) {
        if (!active && durationTarget == null && !picker) {
            val next = OwnerPlanSync.merge(plan, ownerUid, publishedOwned, apps, excludedOwned)
            if (next != plan) setPlan(next)
        }
    }
    // A null snapshot means offline / not yet verified, never "no memberships".
    LaunchedEffect(ownerUid, joinedPublished, excludedJoined, apps, active, durationTarget, picker, queuedJoined) {
        if (!active && durationTarget == null && !picker) {
            val uid = ownerUid
            if (account.profile?.uid == uid) {
                var next = JoinedPlanSync.merge(plan, uid, joinedPublished, apps, excludedJoined)
                if (uid != null && queuedJoined.isNotEmpty()) {
                    queuedJoined.forEach { (remote, installed) ->
                        if (installed.packageName !in excludedJoined &&
                            apps.any { it.packageName == installed.packageName }) {
                            next = JoinedPlanSync.addExplicit(next, uid, remote, installed)
                        }
                    }
                    queuedJoined = emptyList()
                }
                if (next != plan) setPlan(next)
            }
        }
    }
    fun setJoinedExcluded(pkg: String, excluded: Boolean) {
        val uid = ownerUid ?: return
        store.setJoinedExcluded(uid, pkg, excluded)
        excludedJoined = store.excludedJoinedApps(uid)
    }
    fun setOwnedExcluded(pkg: String, excluded: Boolean) {
        val uid = ownerUid ?: return
        store.setOwnedExcluded(uid, pkg, excluded)
        excludedOwned = store.excludedOwnedApps(uid)
    }
    fun addInstalledToPlan(installed: InstalledApp) {
        if (active) return
        val uid = ownerUid
        val draft = publishedOwned.firstOrNull { it.packageName == installed.packageName }
        if (uid != null && draft != null) {
            setOwnedExcluded(installed.packageName, false)
            val next = OwnerPlanSync.addExplicit(plan, uid, draft, installed)
            if (next != plan) setPlan(next)
        } else if (uid != null && joinedPublished?.any { it.packageName == installed.packageName } == true) {
            val remote = joinedPublished!!.first { it.packageName == installed.packageName }
            setJoinedExcluded(installed.packageName, false)
            val next = JoinedPlanSync.addExplicit(plan, uid, remote, installed)
            if (next != plan) setPlan(next)
        } else if (plan.targets.none { it.packageName == installed.packageName }) {
            setPlan(plan.copy(targets = plan.targets + TestTarget(
                installed.packageName, installed.activityClass, installed.label)))
        }
    }
    fun syncEditedCase(oldRaw: String, newRaw: String, packageName: String) {
        if (active || oldRaw.isBlank()) return
        val next = plan.targets.map { PlanCaseUpdates.replaceLocal(it, packageName, oldRaw, newRaw) }
        if (next != plan.targets) setPlan(plan.copy(targets = next))
    }
    fun saveEditedCase(updated: com.sequmio.givlse.data.MyAppDraft) {
        val cases = ScenarioCodec.split(ownAppDraft.scenarioJson).toMutableList()
        val previous = cases.getOrNull(selectedScenarioIndex)
        if (previous != null) cases[selectedScenarioIndex] = updated.scenarioJson
        else cases.add(updated.scenarioJson)
        val combined = ScenarioCodec.compose(cases)
        require(combined.length <= 48000) { "Test cases exceed the 48 KB limit" }
        val count = cases.sumOf { org.json.JSONObject(it).optJSONArray("steps")?.length() ?: 0 }
        require(count <= 150) { "Too many combined test steps" }
        if (previous != null) syncEditedCase(previous, updated.scenarioJson, ownAppDraft.packageName)
        saveDraft(ownAppDraft.copy(scenarioJson = combined))
    }
    fun joinCommunity(published: PublishedApp, installed: InstalledApp) {
        val uid = ownerUid ?: return
        if (account.profile?.uid != uid || published.ownerUid == uid) return
        setJoinedExcluded(installed.packageName, false)
        // A just-confirmed join may not be in the previous server snapshot.
        joinedPublished = joinedPublished?.filterNot { it.packageName == published.packageName }?.plus(published)
        if (active || durationTarget != null || picker) {
            queuedJoined = queuedJoined.filterNot { it.first.packageName == published.packageName } +
                (published to installed)
        } else {
            val next = JoinedPlanSync.addExplicit(plan, uid, published, installed)
            if (next != plan) setPlan(next)
        }
        joinedRefresh++
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
        if (plan.targets.isEmpty()) { page = Page.PLAN; return }
        if (!consent || !isSequmioAccessibilityEnabled(context)) {
            openPermissions(Page.RUNNER)
            return
        }
        // Recheck the live service at the moment of Start, not a cached UI flag.
        refreshRunnerConnection()
        if (!CycleAccessibilityService.refreshConnection()) {
            message = RUNNER_DISCONNECTED_MESSAGE
            return
        }
        message = CycleAccessibilityService.instance?.let { it.start(plan).orEmpty() }
            ?: RUNNER_DISCONNECTED_MESSAGE
        if (message.isEmpty()) page = Page.RUNNER
    }
    fun requestStart() = start()
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
        if (page == Page.RUNNER && returnStatus == ResultReturnStatus.NOT_OBSERVED &&
            !returnHintDismissed && !active) {
            SeqCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Automatic return was not observed", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Tests can still run without automatic return.", color = SeqColors.Muted, fontSize = 12.sp)
                        SeqTextButton(onClick = { openPermissions(Page.RUNNER) }) {
                            Text("Review background launch settings")
                        }
                    }
                    IconButton(onClick = { store.setResultReturnHintDismissed(true) }) {
                        Icon(Icons.Outlined.Close, "Hide automatic return recommendation", tint = SeqColors.Muted)
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when (page) {
                Page.RUNNER -> Box(Modifier.fillMaxSize()) {
                    SeqRunnerAtmosphere(Modifier.matchParentSize())
                    SequmioHome(
                    plan, runner, connected, systemAccessibilityEnabled, message,
                    onStart = { requestStart() },
                    onEdit = { page = Page.PLAN },
                    onSetup = { openPermissions(Page.RUNNER) },
                    onRetryConnection = refreshRunnerConnection,
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
                        if (publishedOwned.any { it.packageName == pkg } ||
                            plan.targets.any { it.packageName == pkg && it.origin == "owner" })
                            setOwnedExcluded(pkg, true)
                        if (joinedPublished?.any { it.packageName == pkg } == true ||
                            plan.targets.any { it.packageName == pkg && it.origin == "community" })
                            setJoinedExcluded(pkg, true)
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
                    onProgress = { page = Page.QA_PROGRESS },
                    onJoined = { published, installed ->
                        joinCommunity(published, installed)
                    },
                    onSync = { joinedRefresh++ },
                    onOwnedTest = { published, installed ->
                        if (!active && ownerUid != null && published.ownerUid == ownerUid) {
                            val draft = published.toDraft()
                            publishedOwned = publishedOwned.filterNot { it.packageName == draft.packageName } + draft
                            setOwnedExcluded(installed.packageName, false)
                            setPlan(OwnerPlanSync.addExplicit(plan, ownerUid, draft, installed))
                            page = Page.RUNNER
                        }
                    },
                    onLocalTest = { installed ->
                        addInstalledToPlan(installed)
                        page = Page.RUNNER
                    }
                )
                Page.MY_APP -> if (restoring) {
                    Column(Modifier.fillMaxSize().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SeqNeonHeader(SeqHeroKind.DEVELOPER, "My App", "Developer listing")
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Restoring your apps from your Google account…", color = SeqColors.Muted)
                    }
                } else key(ownerUid, ownAppDraft.packageName, draftRevision) {
                    SequmioDeveloperApp(
                    initial = ownAppDraft,
                    account = account,
                    ownedChoices = ownedChoices,
                    publicationStatus = draftStore.publicationStatus(ownAppDraft.packageName),
                    restoreError = restoreError,
                    onRetryRestore = { restoreRevision++ },
                    onSelectApp = { pkg -> draftStore.select(pkg); reloadDraft() },
                    hasDeviceDraft = draftStore.hasDeviceDraft(),
                    onImportDeviceDraft = {
                        val imported = draftStore.importDeviceDraft()
                        if (imported != null) reloadDraft()
                        imported != null
                    },
                    onPublished = { draft ->
                        draftStore.markPublished(draft)
                        publishedOwned = draftStore.publishedDrafts()
                    },
                    onUnpublished = { pkg ->
                        draftStore.markUnpublished(pkg)
                        publishedOwned = draftStore.publishedDrafts()
                    },
                    onSave = { updated -> saveDraft(updated) },
                    onOpenSettings = { openSettings() },
                    onEditCase = { draft, index ->
                        saveDraft(draft)
                        selectedScenarioIndex = index
                        page = Page.SCENARIO_EDITOR
                    },
                    onDashboard = { draft ->
                        saveDraft(draft)
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
                        saveDraft(draft)
                        val selected = apps.firstOrNull { it.packageName == draft.packageName }
                        when {
                            !consent -> {
                                recorderNotice = "Review the updated recording disclosure first."
                                openPermissions(Page.MY_APP)
                            }
                            !isSequmioAccessibilityEnabled(context) -> {
                                recorderNotice = "Enable Sequmio Accessibility first."
                                openPermissions(Page.MY_APP)
                            }
                            !CycleAccessibilityService.refreshConnection() -> {
                                recorderNotice = RUNNER_DISCONNECTED_MESSAGE
                            }
                            selected == null -> recorderNotice =
                                "Install your app on this device before recording."
                            else -> {
                                recorderNotice = CycleAccessibilityService.instance
                                    ?.startRecording(selected, ownerUid).orEmpty()
                                if (CycleAccessibilityService.instance == null)
                                    recorderNotice = "Enable Sequmio Accessibility first."
                            }
                        }
                    }
                )
                }
                Page.SUITE_MANAGER -> SequmioSuiteManager(
                    initial = ownAppDraft,
                    onSave = { updated ->
                        saveDraft(updated)
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
                        saveEditedCase(updated)
                        page = Page.MY_APP
                    },
                    onBack = { page = Page.MY_APP },
                    onPick = { callback ->
                        val target = apps.firstOrNull { it.packageName == ownAppDraft.packageName }
                        when {
                            active -> "Stop the current run first."
                            !consent -> "Enable Accessibility and accept the recording disclosure in Settings."
                            target == null -> "Install your app on this device first."
                            !CycleAccessibilityService.refreshConnection() -> RUNNER_DISCONNECTED_MESSAGE
                            else -> {
                                val service = CycleAccessibilityService.instance
                                if (service == null) RUNNER_DISCONNECTED_MESSAGE
                                else service.startElementPicker(target, callback)
                            }
                        }
                    },
                    onCancelPick = { CycleAccessibilityService.instance?.cancelElementPicker() },
                    onRun = { draft ->
                        val installed = apps.firstOrNull { it.packageName == draft.packageName }
                        when {
                            active -> "Stop the current run first."
                            !consent -> "Enable Accessibility and accept the recording disclosure in Settings."
                            installed == null -> "Install your app on this device first."
                            !CycleAccessibilityService.refreshConnection() -> RUNNER_DISCONNECTED_MESSAGE
                            else -> {
                                val specs = ScenarioCodec.parseAll(draft.scenarioJson, draft.packageName)
                                val target = TestTarget(installed.packageName, installed.activityClass,
                                    draft.appName, specs.sumOf { it.totalDurationSec }.coerceIn(1, 7200),
                                    draft.scenarioJson, "local")
                                saveEditedCase(draft)
                                val service = CycleAccessibilityService.instance
                                val error = if (service != null) service.start(TestPlan(targets = listOf(target)))
                                    else RUNNER_DISCONNECTED_MESSAGE
                                page = Page.RUNNER
                                message = error.orEmpty()
                                error
                            }
                        }
                    }
                )
                Page.QA_DASHBOARD -> SequmioQaDashboard(
                    appId = ownAppDraft.packageName,
                    account = account,
                    onBack = { page = Page.MY_APP }
                )
                Page.QA_PROGRESS -> SequmioQaProgress(account, joinedPublished, apps,
                    reportRevision, notificationsGranted, requestNotificationPermission,
                    onBack = { page = Page.APP_LIST })
                Page.HISTORY -> SequmioHistory(runReports)
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
                    returnStatus = returnStatus,
                    returnHintDismissed = returnHintDismissed,
                    onReturnHintDismissed = { store.setResultReturnHintDismissed(it) },
                    consent = consent,
                    onConsent = { store.saveAccessConsent(); consent = true },
                    onBack = { page = setupReturnPage },
                    onAppInfo = openAppDetails,
                    onAccessibility = openAccessibility,
                    onAccessibilityList = openAccessibilityList,
                    onRetryConnection = refreshRunnerConnection,
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
    completion?.let { result ->
        if (page == Page.RUNNER) RunCompletionDialog(result) {
            if (RunnerRuntime.dismissCompletion(result.id)) {
                runCatching {
                    context.getSystemService(android.app.NotificationManager::class.java).cancel(411)
                }
            }
        }
    }
    if (picker) SequmioAppPicker(
        apps, plan.targets.map { it.packageName }.toSet(),
        onDismiss = { picker = false },
        onAdd = { selected ->
            selected.forEach { addInstalledToPlan(it) }
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
    onRetryConnection: () -> Unit,
    onPause: () -> Unit, onResume: () -> Unit, onStop: () -> Unit
) {
    val active = runner.phase == RunPhase.RUNNING || runner.phase == RunPhase.PAUSED
    var appsCollapsed by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
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
                                                    when (target.origin) {
                                                        "community" -> " · Community"
                                                        "owner" -> " · My App"
                                                        else -> " · Local plan"
                                                    },
                                                fontSize = 11.sp, color = SeqColors.Muted
                                            )
                                            val timing = remember(target) { scenarioTimingLabel(target) }
                                            if (timing != null) Text(timing, fontSize = 11.sp, color = SeqColors.Muted)
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
                        if (systemEnabled)
                            SeqTextButton(onClick = onRetryConnection) { Text("Retry connection") }
                        SeqTextButton(onClick = onSetup) { Text("Manage permissions") }
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
private fun RunCompletionDialog(result: RunCompletion, onDismiss: () -> Unit) {
    fun tint(status: String): Color = when (status) {
        "FINISHED", "PASSED" -> SeqColors.Success
        "FAILED", "BLOCKED", "ERROR" -> SeqColors.Error
        "NOT_EXECUTED" -> SeqColors.Muted
        else -> SeqColors.Primary
    }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Dismiss run results",
                onClick = onDismiss),
            shape = RoundedCornerShape(22.dp),
            color = SeqColors.CardRaised,
            border = BorderStroke(1.dp, SeqColors.Border)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (result.status == "FINISHED") Icons.Outlined.CheckCircle
                        else Icons.Outlined.ErrorOutline, null,
                        tint = tint(result.status), modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Run " + result.status.lowercase(Locale.ROOT), Modifier.weight(1f), fontSize = 20.sp,
                        fontWeight = FontWeight.Bold)
                    SeqChip(result.status, tint(result.status))
                }
                Text(duration(result.elapsedSeconds) + " · " + result.completedCycles + " completed cycles",
                    color = SeqColors.Muted, fontSize = 12.sp)
                Text(result.reason, color = SeqColors.Text, fontSize = 13.sp)
                HorizontalDivider(color = SeqColors.Border)
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    result.apps.forEach { app ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SeqAppLetter(app.packageName, app.label)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label, fontWeight = FontWeight.Medium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (app.reason.isNotBlank())
                                    Text(app.reason, color = SeqColors.Muted, fontSize = 11.sp)
                            }
                            SeqChip(app.status, tint(app.status))
                        }
                    }
                }
                HorizontalDivider(color = SeqColors.Border)
                Text("Tap to dismiss · Details in History", color = SeqColors.Muted,
                    fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
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
                            (when (target.origin) {
                                "community" -> " • Community"
                                "owner" -> " • My App"
                                else -> " • edit"
                            }),
                            color = SeqColors.Muted, fontSize = 12.sp)
                        val timing = remember(target) { scenarioTimingLabel(target) }
                        if (timing != null) Text(timing, color = SeqColors.Muted, fontSize = 11.sp)
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

/** QA-facing run summaries. Full report data stays in the existing archive/uploader. */
@Composable
private fun SequmioHistory(reports: List<HistoryRunSummary>) {
    val listState = rememberLazyListState()
    var expandedReportId by rememberSaveable { mutableStateOf<String?>(null) }
    var eventReportId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedLog = reports.firstOrNull { it.id == eventReportId }
    if (selectedLog != null) {
        androidx.activity.compose.BackHandler { eventReportId = null }
        HistoryEventLog(selectedLog, onBack = { eventReportId = null })
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SeqNeonHeader(SeqHeroKind.HISTORY, "Test History",
                "Recent test runs · " + reports.size)
        }
        if (reports.isEmpty()) {
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
                "FINISHED" -> SeqColors.Success
                "CANCELLED" -> SeqColors.Primary
                "BLOCKED" -> Color(0xFFFBBF24)
                else -> SeqColors.Muted
            }
            val statusIcon = when (report.status) {
                "FINISHED" -> Icons.Outlined.CheckCircle
                "CANCELLED" -> Icons.Outlined.StopCircle
                else -> Icons.Outlined.ErrorOutline
            }
            val expanded = expandedReportId == report.id
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = SeqColors.Card,
                border = BorderStroke(1.dp, tint.copy(alpha = 0.45f))
            ) {
                Column(
                    Modifier.background(Brush.horizontalGradient(listOf(
                        tint.copy(alpha = 0.10f), Color.Transparent)))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(statusIcon, contentDescription = report.status,
                            tint = tint, modifier = Modifier.size(23.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(report.status, color = tint,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.weight(1f))
                        Text(historyDate(report.startedAt), color = SeqColors.Muted,
                            fontSize = 11.sp)
                    }
                    Text("Duration " + duration(report.durationSeconds) + " · " +
                        report.completedCycles + if (report.completedCycles == 1) " cycle" else " cycles",
                        color = SeqColors.Text, fontSize = 13.sp)
                    if (report.status != "FINISHED" && report.reason.isNotBlank()) {
                        Text(report.reason, color = SeqColors.Muted, fontSize = 12.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    HorizontalDivider(color = SeqColors.Border.copy(alpha = 0.55f))
                    if (report.apps.isEmpty()) {
                        Text("No app results recorded", color = SeqColors.Muted, fontSize = 12.sp)
                    } else {
                        Text(report.summaryLabel, fontSize = 12.sp, color = SeqColors.Muted,
                            fontWeight = FontWeight.Medium)
                        val visibleApps = if (expanded) report.apps else report.apps.take(3)
                        visibleApps.forEach { app ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Icon(Icons.Outlined.Smartphone, null, Modifier.size(17.dp),
                                    tint = SeqColors.Muted)
                                Text(app.label, Modifier.weight(1f), fontSize = 13.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                SeqChip(when (app.outcome) {
                                    AppPoolOutcome.NOT_EXECUTED -> "NOT RUN"
                                    else -> app.outcome.name
                                }, when (app.outcome) {
                                    AppPoolOutcome.PASSED -> SeqColors.Success
                                    AppPoolOutcome.FAILED -> SeqColors.Error
                                    AppPoolOutcome.ERROR -> Color(0xFFFBBF24)
                                    AppPoolOutcome.NOT_EXECUTED -> SeqColors.Muted
                                })
                            }
                        }
                        if (report.apps.size > 3) {
                            SeqTextButton(onClick = {
                                expandedReportId = if (expanded) null else report.id
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (expanded) "Show fewer apps"
                                    else "Show all " + report.apps.size + " apps", fontSize = 12.sp)
                                Spacer(Modifier.width(4.dp))
                                Icon(if (expanded) Icons.Outlined.KeyboardArrowUp
                                    else Icons.Outlined.KeyboardArrowDown, null, Modifier.size(18.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = SeqColors.Border.copy(alpha = 0.55f))
                    SeqTextButton(onClick = { eventReportId = report.id },
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Timeline, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Event Log", fontSize = 12.sp)
                        Spacer(Modifier.width(5.dp))
                        Text("(" + report.events.size + ")", fontSize = 11.sp,
                            color = SeqColors.Muted)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun HistoryEventLog(report: HistoryRunSummary, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SeqTopBar("Event Log", onBack)
        Text(historyDate(report.startedAt) + " · " + report.status + " · " +
            report.events.size + " events", fontSize = 12.sp, color = SeqColors.Muted)
        if (report.events.isEmpty()) {
            SeqCard(Modifier.fillMaxWidth()) {
                Text("No events saved for this run.", modifier = Modifier.padding(16.dp),
                    color = SeqColors.Muted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 12.dp)) {
                items(report.events) { event ->
                    SeqCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(11.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            val problem = event.message.contains("fail", ignoreCase = true) ||
                                event.message.contains("error", ignoreCase = true)
                            Icon(if (problem) Icons.Outlined.ErrorOutline else Icons.Outlined.Timeline,
                                null, Modifier.size(17.dp),
                                tint = if (problem) SeqColors.Error else SeqColors.Teal)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(historyDate(event.timestampMillis, includeSeconds = true),
                                    color = SeqColors.Muted, fontSize = 11.sp)
                                Text(event.message, fontSize = 13.sp, color = SeqColors.Text)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun historyDate(time: Long, includeSeconds: Boolean = false): String =
    if (time <= 0) "Unknown date" else SimpleDateFormat(
        if (includeSeconds) "dd MMM yyyy HH:mm:ss" else "dd MMM yyyy · HH:mm",
        Locale.getDefault()).format(Date(time))

private fun duration(seconds: Long): String =
    "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
