package com.sequmio.givlse

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sequmio.givlse.account.SequmioAccount
import com.sequmio.givlse.data.*
import com.sequmio.givlse.model.InstalledApp
import com.sequmio.givlse.runner.RecorderState
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.async

private fun openSafeLink(context: Context, url: String) {
    if (url.startsWith("https://")) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

@Composable
internal fun SequmioCommunityCatalog(
    account: SequmioAccount,
    installed: List<InstalledApp>,
    refreshToken: Int,
    onOpenSettings: () -> Unit,
    onProgress: () -> Unit,
    onJoined: (PublishedApp, InstalledApp) -> Unit,
    onSync: () -> Unit,
    onOwnedTest: (PublishedApp, InstalledApp) -> Unit,
    onLocalTest: (InstalledApp) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(account) { CommunityRepository(account) }
    var published by remember { mutableStateOf<List<PublishedApp>>(emptyList()) }
    val uid = account.profile?.uid
    val joinStore = remember(context, uid) { uid?.let { PendingJoinStore(context, it) } }
    var pending by remember(uid) { mutableStateOf(joinStore?.load().orEmpty()) }
    var joined by remember(uid) { mutableStateOf<Set<String>>(emptySet()) }
    var selected by remember(uid) { mutableStateOf<PublishedApp?>(null) }
    var cancelJoin by remember(uid) { mutableStateOf(false) }
    fun savePending(packageName: String, steps: JoinSteps?) {
        joinStore?.save(packageName, steps)
        pending = if (steps == null) pending - packageName else pending + (packageName to steps)
    }
    fun openJoin(app: PublishedApp) {
        if (uid == null) { onOpenSettings(); return }
        if (app.packageName !in joined && app.packageName !in pending)
            savePending(app.packageName, JoinSteps())
        selected = app
    }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var showInstalled by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val selection = selected?.packageName
    LaunchedEffect(selection) { scroll.scrollTo(0) }
    BackHandler(enabled = selected != null && !cancelJoin) {
        if (!busy) { selected = null; notice = "" }
    }

    LaunchedEffect(account.profile?.uid, refresh, refreshToken) {
        if (!account.configured) {
            notice = "Community catalog is unavailable until Firebase is configured."
            published = emptyList()
        } else {
            busy = true
            try {
                val catalog = repo.published()
                val memberList = if (account.profile != null)
                    repo.joined().filter { it.selfConfirmed } else emptyList()
                joined = memberList.map { it.packageName }.toSet()
                // Reconcile successful joins, including a previously interrupted response.
                joined.forEach { if (it in pending) savePending(it, null) }
                val known = catalog.associateBy { it.packageName }
                val extra = ((pending.keys + joined) - known.keys).mapNotNull { pkg ->
                    runCatching { repo.publishedApp(pkg) }.getOrNull()
                }
                published = catalog + extra
                selected?.let { old ->
                    published.firstOrNull { it.packageName == old.packageName }?.let { selected = it }
                }
                // Root owns authoritative plan synchronization, including empty snapshots.
                onSync()
                notice = ""
            } catch (error: Exception) {
                published = emptyList()
                notice = "Catalog unavailable: " + error.javaClass.simpleName
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (selected == null) {
            SeqNeonHeader(SeqHeroKind.CATALOG, "App List", "Installed / testable apps",
                trailing = {
                    SeqIconAction(Icons.Outlined.Refresh, "Refresh catalog",
                        onClick = { refresh++ }, enabled = !busy)
                })
            if (uid != null) {
                SeqCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("My QA Progress", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Daily testing activity and reminders", color = SeqColors.Muted, fontSize = 11.sp)
                        }
                        SeqTextButton(onClick = onProgress) { Text("Open") }
                    }
                }
            }
            Text("Discover community tests or prepare an installed app for Runner.",
                color = SeqColors.Muted, fontSize = 12.sp)
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search applications", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null,
                        tint = SeqColors.Primary)
                },
                singleLine = true,
                shape = RoundedCornerShape(17.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SeqColors.Text,
                    unfocusedTextColor = SeqColors.Text,
                    focusedBorderColor = SeqColors.Primary,
                    unfocusedBorderColor = SeqColors.Border
                )
            )
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FilterChip(
                    selected = !showInstalled, onClick = { showInstalled = false },
                    label = { Text("Community") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Public, null, Modifier.size(17.dp))
                    }
                )
                FilterChip(
                    selected = showInstalled, onClick = { showInstalled = true },
                    label = { Text("On this device") },
                    leadingIcon = {
                        Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(17.dp))
                    }
                )
            }
            if (showInstalled) {
                val localMatches = installed.filter {
                    search.isBlank() ||
                        it.label.contains(search, ignoreCase = true) ||
                        it.packageName.contains(search, ignoreCase = true)
                }
                Text("Installed applications · " + localMatches.size,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (localMatches.isEmpty()) {
                    SeqCard(Modifier.fillMaxWidth()) {
                        Text("No installed apps match your search.",
                            Modifier.padding(18.dp), color = SeqColors.Muted)
                    }
                }
                localMatches.take(60).forEach { app ->
                    SeqCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            SeqAppLetter(app.packageName, app.label)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, color = SeqColors.Muted,
                                    fontSize = 11.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            }
                            SeqIconAction(
                                Icons.Outlined.PlayArrow, "Add " + app.label + " to Runner",
                                onClick = { onLocalTest(app) }, tint = SeqColors.Primary
                            )
                        }
                    }
                }
                if (localMatches.size > 60) {
                    Text("Showing first 60 results. Refine your search.",
                        fontSize = 12.sp, color = SeqColors.Muted)
                }
            } else {
                if (busy) CircularProgressIndicator(color = SeqColors.Teal)
                if (notice.isNotBlank()) {
                    SeqCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(15.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CloudOff, contentDescription = null,
                                tint = SeqColors.Primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(notice, color = SeqColors.Muted, fontSize = 12.sp)
                        }
                    }
                }
                if (account.profile == null) {
                    SeqCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(15.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Join community testing",
                                fontWeight = FontWeight.SemiBold)
                            Text("Sign in with Google to join published tests. " +
                                "Local app testing is available without an account.",
                                color = SeqColors.Muted, fontSize = 12.sp)
                            SeqTextButton(onClick = onOpenSettings) {
                                Icon(Icons.Outlined.PersonOutline, null,
                                    Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Open Profile")
                            }
                        }
                    }
                }
                val matches = published.filter {
                    search.isBlank() ||
                        it.name.contains(search, ignoreCase = true) ||
                        it.packageName.contains(search, ignoreCase = true) ||
                        it.description.contains(search, ignoreCase = true)
                }.sortedWith(compareBy<PublishedApp> {
                    JoinSteps.priority(it.packageName in pending && it.packageName !in joined &&
                        it.ownerUid != uid)
                }.thenBy { it.name.lowercase() })
                if (!busy && matches.isEmpty() && notice.isBlank()) {
                    SeqCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Inventory2, null,
                                tint = SeqColors.Teal, modifier = Modifier.size(30.dp))
                            Text("No published applications yet",
                                fontWeight = FontWeight.SemiBold)
                            Text("When developers publish tests, their real listings " +
                                "appear here.", color = SeqColors.Muted, fontSize = 12.sp)
                        }
                    }
                }
                matches.forEach { app ->
                    val member = app.packageName in joined
                    SeqCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SeqAppLetter(app.packageName, app.name,
                                    refreshToken = refreshToken, publishedIcon = app.iconPng)
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(app.name, fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        SeqIconAction(Icons.Outlined.OpenInNew, "Open on Google Play",
                                            onClick = { openSafeLink(context, app.playStoreUrl) },
                                            modifier = Modifier.size(34.dp))
                                    }
                                }
                                if (uid == app.ownerUid) {
                                    val local = installed.firstOrNull { it.packageName == app.packageName }
                                    if (local != null) {
                                        Surface(onClick = { onOwnedTest(app, local) },
                                            shape = RoundedCornerShape(50),
                                            color = SeqColors.Teal.copy(alpha = 0.15f),
                                            contentColor = SeqColors.Teal) {
                                            Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Outlined.PlayArrow, "Add My App to Runner",
                                                    Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("My App", fontSize = 12.sp, maxLines = 1)
                                            }
                                        }
                                    } else SeqChip("My App", SeqColors.Teal)
                                } else if (member) {
                                    Surface(onClick = { notice = ""; selected = app },
                                        shape = RoundedCornerShape(50),
                                        color = SeqColors.Success.copy(alpha = 0.15f),
                                        contentColor = SeqColors.Success) {
                                        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.Check, null, Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Joined", fontSize = 12.sp, maxLines = 1, softWrap = false)
                                        }
                                    }
                                } else {
                                    val continuing = app.packageName in pending
                                    Surface(onClick = { notice = ""; openJoin(app) },
                                        enabled = !busy,
                                        shape = RoundedCornerShape(50),
                                        color = if (continuing) Color(0xFF72521C) else SeqColors.PrimaryDark,
                                        contentColor = Color.White) {
                                        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Icon(if (continuing) Icons.Outlined.ArrowForward else Icons.Outlined.Add,
                                                null, Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (continuing) "Continue" else "Join",
                                                fontSize = 12.sp, maxLines = 1, softWrap = false)
                                        }
                                    }
                                }
                            }
                            if (app.description.isNotBlank())
                                Text(app.description, color = SeqColors.Muted,
                                    fontSize = 12.sp, maxLines = 3,
                                    overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        } else {
            val app = requireNotNull(selected)
            val local = installed.firstOrNull { it.packageName == app.packageName }
            val member = app.packageName in joined
            val steps = pending[app.packageName] ?: JoinSteps()
            SeqTextButton(onClick = { selected = null; notice = "" }, enabled = !busy) {
                Icon(Icons.Outlined.ArrowBack, null, Modifier.size(20.dp))
                Text("App List")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeqAppLetter(app.packageName, app.name,
                    refreshToken = refreshToken, publishedIcon = app.iconPng)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(app.packageName, color = SeqColors.Muted, fontSize = 12.sp)
                }
            }
            Text("Complete each step in Google services. Sequmio cannot " +
                "independently verify Google Groups or Play tester enrollment.",
                color = SeqColors.Muted, fontSize = 12.sp)
            SeqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeqSectionTitle("1. Join tester group", Icons.Outlined.Groups)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val confirmed = steps.groupConfirmed || member
                        Row(Modifier.weight(1f)
                            .heightIn(min = 48.dp)
                            .clickable { openSafeLink(context, app.googleGroupUrl) }
                            .padding(end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Open Google Group", fontSize = 12.sp, color = SeqColors.Primary,
                                modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(5.dp))
                            Icon(Icons.Outlined.OpenInNew, "Open link", Modifier.size(16.dp),
                                tint = SeqColors.Primary)
                        }
                        Spacer(Modifier.width(6.dp))
                        SeqGlassButton(
                            title = if (confirmed) "Confirmed" else "Confirm",
                            tint = if (confirmed) SeqColors.Success else SeqColors.Muted,
                            onClick = { savePending(app.packageName, steps.copy(groupConfirmed = !confirmed)) },
                            modifier = Modifier.widthIn(min = 88.dp),
                            enabled = !member && !busy, compact = true,
                            inactiveTint = if (confirmed) SeqColors.Success else SeqColors.Muted)
                    }
                    Text("Confirm after joining the Google Group.",
                        color = SeqColors.Muted, fontSize = 12.sp)
                }
            }
            SeqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeqSectionTitle("2. Become a tester", Icons.Outlined.VerifiedUser)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val confirmed = steps.optInConfirmed || member
                        Row(Modifier.weight(1f)
                            .heightIn(min = 48.dp)
                            .clickable { openSafeLink(context, app.optInUrl) }
                            .padding(end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Open Google Play opt-in", fontSize = 12.sp, color = SeqColors.Primary,
                                modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(5.dp))
                            Icon(Icons.Outlined.OpenInNew, "Open link", Modifier.size(16.dp),
                                tint = SeqColors.Primary)
                        }
                        Spacer(Modifier.width(6.dp))
                        SeqGlassButton(
                            title = if (confirmed) "Confirmed" else "Confirm",
                            tint = if (confirmed) SeqColors.Success else SeqColors.Muted,
                            onClick = { savePending(app.packageName, steps.copy(optInConfirmed = !confirmed)) },
                            modifier = Modifier.widthIn(min = 88.dp),
                            enabled = !member && !busy, compact = true,
                            inactiveTint = if (confirmed) SeqColors.Success else SeqColors.Muted)
                    }
                    Text("Confirm after accepting the invitation.",
                        color = SeqColors.Muted, fontSize = 12.sp)
                }
            }
            SeqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SeqSectionTitle("3. Install and test", Icons.Outlined.PhoneAndroid)
                    SeqTextButton(onClick = { openSafeLink(context, app.playStoreUrl) }) {
                        Icon(Icons.Outlined.OpenInNew, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Open Google Play")
                    }
                    SeqChip(if (local == null) "Not installed" else "Installed",
                        if (local == null) SeqColors.Muted else SeqColors.Success)
                    if (local == null && !member) {
                        Text("Google Play may take several minutes or longer to make the app available. " +
                            "You can return later using Continue in App List. Your progress is saved.",
                            color = SeqColors.Muted, fontSize = 12.sp)
                    }
                }
            }
            if (notice.isNotBlank()) Text(notice, color = SeqColors.Muted)
            if (member) {
                if (local != null) {
                    SeqPrimaryButton("Add / update in Runner",
                        onClick = { onJoined(app, local); notice = "Added to Runner." },
                        modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.PlayArrow)
                }
            } else {
                val ready = !busy && uid != null && steps.ready(local != null)
                SeqGlassButton(
                    title = "Confirm", tint = SeqColors.Success,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                repo.confirmJoin(app, steps.groupConfirmed, steps.optInConfirmed, local != null)
                                joined = joined + app.packageName
                                savePending(app.packageName, null)
                                if (local != null) onJoined(app, local)
                                notice = "Joined (self-confirmed). Added to Runner."
                            } catch (error: Exception) {
                                notice = "Join failed: " + (error.message ?: error.javaClass.simpleName)
                            } finally { busy = false }
                        }
                    },
                    enabled = ready, modifier = Modifier.fillMaxWidth()
                )
                SeqGlassButton("Cancel", SeqColors.Error,
                    onClick = { cancelJoin = true }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth())
                if (cancelJoin) {
                    AlertDialog(
                        onDismissRequest = { cancelJoin = false },
                        title = { Text("Cancel joining?") },
                        text = { Text("Your saved progress in Sequmio will be cleared. " +
                            "You must manually leave the Google Group and opt out of Google Play testing. " +
                            "Sequmio cannot do this for you.") },
                        confirmButton = {
                            TextButton(onClick = {
                                savePending(app.packageName, null)
                                cancelJoin = false
                                selected = null
                                notice = ""
                            }) { Text("Cancel join", color = SeqColors.Error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { cancelJoin = false }) { Text("Keep joining") }
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun CatalogTextField(
    title: String, value: String, lines: Int = 1, onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(title) },
        modifier = Modifier.fillMaxWidth(),
        maxLines = lines, singleLine = lines == 1,
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

@Composable
internal fun SequmioDeveloperApp(
    initial: MyAppDraft,
    account: SequmioAccount,
    ownedChoices: List<MyAppDraft>,
    publicationStatus: String?,
    restoreError: Boolean,
    onRetryRestore: () -> Unit,
    onSelectApp: (String) -> Unit,
    hasDeviceDraft: Boolean,
    onImportDeviceDraft: () -> Boolean,
    onPublished: (MyAppDraft) -> Unit,
    onUnpublished: (String) -> Unit,
    onSave: (MyAppDraft) -> Unit,
    onOpenSettings: () -> Unit,
    onEditCase: (MyAppDraft, Int) -> Unit,
    onDashboard: (MyAppDraft) -> Unit,
    recorderState: RecorderState,
    recorderNotice: String,
    onDismissRecorderNotice: () -> Unit,
    onTest: (MyAppDraft) -> Unit,
    onRecord: (MyAppDraft) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(account) { CommunityRepository(account) }
    var form by remember(initial) { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var published by remember(account.profile?.uid, form.packageName, publicationStatus) {
        mutableStateOf(publicationStatus == "published")
    }
    var editDetails by remember(initial) { mutableStateOf(initial.validateDetails().isNotEmpty()) }
    var edited by remember(initial) { mutableStateOf(initial) }
    var requirementsOpen by remember { mutableStateOf(false) }
    var detailErrors by remember { mutableStateOf(emptyList<String>()) }
    var unpublishConfirm by remember { mutableStateOf(false) }
    var previewRefresh by remember { mutableIntStateOf(0) }
    var deleteCase by remember { mutableIntStateOf(-1) }
    val profile = account.profile
    var appPicker by remember { mutableStateOf(false) }
    var importConfirm by remember { mutableStateOf(false) }
    val dashboardRepo = remember(account) { QaDashboardRepository(account) }
    var preview by remember(profile?.uid, form.packageName) { mutableStateOf<QaTodaySummary?>(null) }
    var joinedCount by remember(profile?.uid, form.packageName) { mutableStateOf<Long?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf(false) }
    LaunchedEffect(profile?.uid, form.packageName, published, previewRefresh) {
        preview = null
        joinedCount = null
        previewError = false
        previewLoading = profile != null && published
        if (previewLoading) {
            suspend fun <T> fetchMetric(fetch: suspend () -> T): T? =
                try { fetch() } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { null }
            supervisorScope {
                val count = async { fetchMetric { dashboardRepo.joinedCount(form.packageName) } }
                val today = async { fetchMetric { dashboardRepo.today(form.packageName) } }
                joinedCount = count.await()
                preview = today.await()
                previewError = joinedCount == null || preview == null
            }
            previewLoading = false
        }
    }
    val splitResult = runCatching { ScenarioCodec.split(form.scenarioJson) }
    val cases = splitResult.getOrDefault(emptyList())
    val errors = form.validateForPublish()
    val detailsReady = form.validateDetails().isEmpty()
    val readyToPublish = errors.isEmpty() && profile != null && !editDetails
    val editable = !busy && !recorderState.recording

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        SeqNeonHeader(SeqHeroKind.DEVELOPER, "My App", "Developer listing")
        val canSwitch = editable && (!editDetails || edited == form)
        if (restoreError) {
            Text("Could not sync your apps. Showing saved data for this account.",
                color = SeqColors.Muted, fontSize = 12.sp)
            SeqGlassButton("Retry sync", SeqColors.Sky, onClick = onRetryRestore,
                enabled = canSwitch, modifier = Modifier.fillMaxWidth())
        }
        if (profile != null && ownedChoices.isNotEmpty()) {
            SeqGlassButton("Select app · " + form.appName.ifBlank { "New app" },
                SeqColors.Teal, onClick = { appPicker = true },
                enabled = canSwitch, modifier = Modifier.fillMaxWidth())
            if (appPicker) AlertDialog(onDismissRequest = { appPicker = false },
                title = { Text("Your apps") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ownedChoices.forEach { app ->
                            TextButton(onClick = { appPicker = false; onSelectApp(app.packageName) }) {
                                Text(app.appName + "\n" + app.packageName)
                            }
                        }
                        TextButton(onClick = { appPicker = false; onSelectApp("") }) {
                            Text("New app")
                        }
                    }
                }, confirmButton = { TextButton(onClick = { appPicker = false }) { Text("Close") } })
        }
        if (profile != null && hasDeviceDraft) {
            SeqGlassButton("Import previous device draft", SeqColors.Sky,
                onClick = { importConfirm = true }, enabled = canSwitch,
                modifier = Modifier.fillMaxWidth())
        }
        if (importConfirm) AlertDialog(onDismissRequest = { importConfirm = false },
            title = { Text("Import local draft?") },
            text = { Text("This device contains an older draft without a linked account. Import it into your current account only if it belongs to you. Existing drafts will not be replaced.") },
            confirmButton = { TextButton(onClick = {
                importConfirm = false
                if (!onImportDeviceDraft()) notice = "Import failed: an account draft already exists or no device draft is available."
            }) { Text("Import") } },
            dismissButton = { TextButton(onClick = { importConfirm = false }) { Text("Cancel") } })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (readyToPublish) "Ready to publish" else "Not ready to publish",
                color = if (readyToPublish) SeqColors.Success else SeqColors.Error,
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Surface(onClick = { requirementsOpen = true }, shape = RoundedCornerShape(50),
                color = SeqColors.Sky.copy(alpha = 0.15f), contentColor = SeqColors.Sky) {
                Icon(Icons.Outlined.Info, "Publishing requirements",
                    Modifier.padding(8.dp).size(20.dp))
            }
        }
        if (profile == null) {
            SeqPrimaryButton("Sign in to publish", onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.PersonOutline)
        } else {
            SeqPrimaryButton(if (published) "Update published app" else "Publish to App List",
                onClick = {
                    scope.launch {
                        busy = true
                        notice = ""
                        try {
                            onSave(form)
                            repo.savePublished(form, form.scenarioJson,
                                AppIconCodec.capture(context, form.packageName))
                            onPublished(form)
                            published = true
                            previewRefresh++
                            notice = "Published successfully. Your app is now in App List."
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (error: Exception) {
                            notice = "Publish failed: " + (error.message ?: error.javaClass.simpleName)
                        } finally { busy = false }
                    }
                }, modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.CloudUpload,
                enabled = editable && readyToPublish)
        }
        if (notice.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(notice, Modifier.weight(1f),
                    color = if (notice.contains("failed", true)) SeqColors.Error else SeqColors.Success,
                    fontSize = 12.sp)
                SeqIconAction(Icons.Outlined.Close, "Dismiss message", onClick = { notice = "" })
            }
        }
        if (profile != null && published) {
            SeqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SeqSectionTitle("QA Dashboard", Icons.Outlined.BarChart, trailing = {
                        SeqIconAction(Icons.Outlined.Refresh, "Refresh QA metrics",
                            onClick = { previewRefresh++ }, enabled = editable && !previewLoading)
                    })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(joinedCount?.toString() ?: "—", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("Joined QA", color = SeqColors.Muted, fontSize = 12.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(preview?.total?.toString() ?: "—", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("Runs today", color = SeqColors.Muted, fontSize = 12.sp)
                        }
                    }
                    preview?.let { summary ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(summary.passed.toString() + " Passed", color = SeqColors.Success, fontSize = 12.sp)
                            Text(summary.failed.toString() + " Failed", color = SeqColors.Error, fontSize = 12.sp)
                            Text(summary.errors.toString() + " Errors", color = SeqColors.Sky, fontSize = 12.sp)
                        }
                    }
                    if (previewLoading) Text("Loading QA metrics…", color = SeqColors.Muted, fontSize = 12.sp)
                    else if (previewError) Text("Some QA metrics are unavailable. Check the cloud connection and access.",
                        color = SeqColors.Muted, fontSize = 12.sp)
                    else Text("Shared tester results · local day", color = SeqColors.Muted, fontSize = 11.sp)
                    SeqPrimaryButton("Open QA Dashboard", onClick = { onDashboard(form) },
                        modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.BarChart,
                        enabled = editable && !editDetails)
                }
            }
        }
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SeqSectionTitle("App Details", Icons.Outlined.Widgets, trailing = {
                    if (!editDetails) {
                        if (detailsReady) Icon(Icons.Outlined.CheckCircle, "App details accepted",
                            tint = SeqColors.Success, modifier = Modifier.size(23.dp))
                        SeqIconAction(Icons.Outlined.Edit, "Edit app details",
                            onClick = { edited = form; editDetails = true }, enabled = editable)
                    }
                })
                if (editDetails) {
                    CatalogTextField("App name", edited.appName) { edited = edited.copy(appName = it) }
                    CatalogTextField("Package name", edited.packageName) { edited = edited.copy(packageName = it) }
                    CatalogTextField("Description", edited.description, 3) { edited = edited.copy(description = it) }
                    HorizontalDivider(color = SeqColors.Border)
                    Text("Closed-test Links", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    CatalogTextField("Google Group", edited.googleGroupUrl) { edited = edited.copy(googleGroupUrl = it) }
                    CatalogTextField("Become a tester", edited.optInUrl) { edited = edited.copy(optInUrl = it) }
                    CatalogTextField("Google Play", edited.playStoreUrl) { edited = edited.copy(playStoreUrl = it) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SeqGlassButton("Cancel", SeqColors.Error, onClick = {
                            edited = form
                            editDetails = !detailsReady
                        }, modifier = Modifier.weight(1f), enabled = editable)
                        SeqGlassButton("Save", SeqColors.Success, onClick = {
                            val candidate = edited.copy(appName = edited.appName.trim(),
                                packageName = edited.packageName.trim(), description = edited.description.trim(),
                                googleGroupUrl = edited.googleGroupUrl.trim(), optInUrl = edited.optInUrl.trim(),
                                playStoreUrl = edited.playStoreUrl.trim(), scenarioJson = form.scenarioJson)
                            val invalid = candidate.validateDetails()
                            if (invalid.isNotEmpty()) detailErrors = invalid
                            else {
                                form = candidate
                                edited = candidate
                                editDetails = false
                                onSave(candidate)
                            }
                        }, modifier = Modifier.weight(1f), enabled = editable)
                    }
                }
            }
        }
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SeqSectionTitle("Test Cases", Icons.Outlined.Layers, trailing = {
                    SeqChip(cases.size.toString() + " test cases", SeqColors.Primary)
                })
                SeqPrimaryButton("Add test case", onClick = {
                    onDismissRecorderNotice()
                    when {
                        !detailsReady || editDetails -> notice = "Save valid app details before recording."
                        splitResult.isFailure -> notice = "Existing test cases could not be read."
                        cases.size >= 12 -> notice = "Maximum 12 test cases reached."
                        else -> onRecord(form)
                    }
                }, modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.Add,
                    enabled = editable && !editDetails)
                if (splitResult.isFailure) Text("Unable to read saved test cases.",
                    color = SeqColors.Error, fontSize = 12.sp)
                else if (cases.isEmpty()) Text("No test cases yet.", color = SeqColors.Muted, fontSize = 12.sp)
                cases.forEachIndexed { index, raw ->
                    val root = remember(raw) { runCatching { org.json.JSONObject(raw) }.getOrNull() }
                    val name = root?.optJSONObject("meta")?.optString("name").orEmpty()
                        .ifBlank { "Test Case " + (index + 1) }
                    val seconds = root?.optJSONObject("execution")?.optInt("total_duration_sec", -1) ?: -1
                    val duration = if (seconds in 5..7200) targetTimeLabel(seconds) else "Not set"
                    val timeout = if (seconds in 5..7200)
                        targetTimeLabel(com.sequmio.givlse.model.ExecutionPolicy.timeout(seconds, 0)) else "Not set"
                    val caseValid = remember(raw, form.packageName) {
                        runCatching { ScenarioCodec.parse(raw, form.packageName) }.isSuccess
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = SeqColors.Background.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SeqColors.Border)) {
                        Row(Modifier.fillMaxWidth().padding(start = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            SeqIconAction(Icons.Outlined.PlayArrow, "Test " + name,
                                onClick = { onTest(form.copy(scenarioJson = raw)) },
                                enabled = editable && !editDetails && caseValid, tint = SeqColors.Teal)
                            Column(Modifier.weight(1f).padding(vertical = 9.dp)) {
                                Text(name, fontWeight = FontWeight.SemiBold, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                Text("Estimated Duration: " + duration, color = SeqColors.Muted, fontSize = 12.sp)
                                Text("Automatic Timeout: " + timeout, color = SeqColors.Muted, fontSize = 12.sp)
                                if (!caseValid) Text("Needs QA", color = SeqColors.Error, fontSize = 11.sp)
                            }
                            SeqIconAction(Icons.Outlined.Edit, "Edit " + name,
                                onClick = { onEditCase(form, index) }, enabled = editable && !editDetails)
                            SeqIconAction(Icons.Outlined.DeleteOutline, "Delete " + name,
                                onClick = { deleteCase = index }, enabled = editable && !editDetails, tint = SeqColors.Error)
                        }
                    }
                }
                if (recorderNotice.isNotBlank()) {
                    val success = recorderNotice.startsWith("Test case created")
                    Surface(shape = RoundedCornerShape(12.dp),
                        color = (if (success) SeqColors.Success else SeqColors.Error).copy(alpha = 0.12f)) {
                        Row(Modifier.fillMaxWidth().padding(start = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (success) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null,
                                tint = if (success) SeqColors.Success else SeqColors.Error)
                            Spacer(Modifier.width(8.dp))
                            Text(recorderNotice, Modifier.weight(1f), fontSize = 12.sp)
                            SeqIconAction(Icons.Outlined.Close, "Dismiss recording result",
                                onClick = onDismissRecorderNotice)
                        }
                    }
                }
            }
        }
        if (published) {
            SeqGlassButton("Unpublish", SeqColors.Error, onClick = { unpublishConfirm = true },
                modifier = Modifier.fillMaxWidth(), enabled = editable && !editDetails,
                icon = Icons.Outlined.VisibilityOff)
        }
        Spacer(Modifier.height(12.dp))
    }
    if (requirementsOpen) {
        AlertDialog(onDismissRequest = { requirementsOpen = false },
            title = { Text("Publishing requirements") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val checks = listOf(
                        "Sign in with Google" to (profile != null),
                        "Save valid App Details and closed-test links" to (detailsReady && !editDetails),
                        "Add valid test cases" to (errors.none { it.startsWith("Test case") || it.startsWith("Scenario:") })
                    )
                    checks.forEach { (label, ready) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                                null, tint = if (ready) SeqColors.Success else SeqColors.Error,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(label, Modifier.weight(1f), fontSize = 13.sp)
                        }
                    }
                    errors.forEach { Text("• " + it, color = SeqColors.Muted, fontSize = 12.sp) }
                }
            }, confirmButton = { TextButton(onClick = { requirementsOpen = false }) { Text("Close") } })
    }
    if (detailErrors.isNotEmpty()) {
        AlertDialog(onDismissRequest = { detailErrors = emptyList() },
            title = { Text("App details incomplete") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                detailErrors.forEach { Text("• " + it, color = SeqColors.Error, fontSize = 13.sp) }
            } }, confirmButton = {
                TextButton(onClick = { detailErrors = emptyList() }) { Text("OK") }
            })
    }
    if (unpublishConfirm) {
        AlertDialog(onDismissRequest = { unpublishConfirm = false },
            title = { Text("Unpublish app?") },
            text = { Text("Your app will be hidden from Community. Existing QA reports and local test cases are retained.") },
            confirmButton = { TextButton(onClick = {
                unpublishConfirm = false
                scope.launch {
                    busy = true
                    try {
                        repo.unpublish(form.packageName)
                        onUnpublished(form.packageName)
                        published = false
                        notice = "Listing unpublished."
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (error: Exception) {
                        notice = "Unpublish failed: " + (error.message ?: "unknown")
                    } finally { busy = false }
                }
            }) { Text("Unpublish", color = SeqColors.Error) } },
            dismissButton = { TextButton(onClick = { unpublishConfirm = false }) { Text("Cancel") } })
    }
    if (deleteCase in cases.indices) {
        val index = deleteCase
        AlertDialog(onDismissRequest = { deleteCase = -1 },
            title = { Text("Delete test case?") },
            text = { Text("This removes the selected case from your local draft.") },
            confirmButton = { TextButton(onClick = {
                val remaining = cases.filterIndexed { i, _ -> i != index }
                form = form.copy(scenarioJson = if (remaining.isEmpty()) "" else ScenarioCodec.compose(remaining))
                onSave(form)
                deleteCase = -1
                notice = "Test case deleted."
            }) { Text("Delete", color = SeqColors.Error) } },
            dismissButton = { TextButton(onClick = { deleteCase = -1 }) { Text("Cancel") } })
    }
}

@Composable
internal fun SequmioScenarioEditor(
    initial: MyAppDraft,
    onSave: (MyAppDraft) -> Unit,
    onBack: () -> Unit
) {
    var json by remember(initial.packageName) { mutableStateOf(initial.scenarioJson) }
    var result by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SeqTopBar("Test Scenario", onBack)
        Text("Create a deterministic JSON UI test for " +
            initial.packageName, color = SeqColors.Muted, fontSize = 13.sp)
        SeqTextButton(onClick = {
            json = ScenarioCodec.starter(initial.packageName)
        }, enabled = initial.packageName.isNotBlank()) {
            Text("Insert starter template")
        }
        Text("Replace the example selector with a real element in your app. " +
            "A WAIT-only script is not valid for publishing.",
            color = SeqColors.Muted, fontSize = 13.sp)
        CatalogTextField("Scenario JSON", json, 15) { json = it }
        SeqTextButton(onClick = {
            result = runCatching {
                ScenarioCodec.parse(json, initial.packageName)
                "Valid scenario"
            }.getOrElse { it.message ?: "Invalid scenario" }
        }) { Text("Validate scenario") }
        if (result.isNotBlank()) Text(result,
            color = if (result == "Valid scenario") SeqColors.Success else SeqColors.Error)
        SeqPrimaryButton(
            "Save scenario",
            onClick = {
                val parsed = runCatching { ScenarioCodec.parse(json, initial.packageName) }
                if (parsed.isSuccess) {
                    onSave(initial.copy(scenarioJson = json))
                    onBack()
                } else {
                    result = parsed.exceptionOrNull()?.message ?: "Invalid scenario"
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
