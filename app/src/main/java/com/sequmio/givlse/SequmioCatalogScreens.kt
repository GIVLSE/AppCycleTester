package com.sequmio.givlse

import android.content.Context
import android.content.Intent
import android.net.Uri
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
    onJoined: (PublishedApp, InstalledApp) -> Unit,
    onSync: (List<Pair<PublishedApp, InstalledApp>>) -> Unit,
    onLocalTest: (InstalledApp) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(account) { CommunityRepository(account) }
    val progress = remember(context) { RunProgressStore(context) }
    var published by remember { mutableStateOf<List<PublishedApp>>(emptyList()) }
    var joined by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selected by remember { mutableStateOf<PublishedApp?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var showInstalled by remember { mutableStateOf(false) }
    var groupConfirmed by remember(selected?.packageName) { mutableStateOf(false) }
    var optInConfirmed by remember(selected?.packageName) { mutableStateOf(false) }

    LaunchedEffect(account.profile?.uid, refresh, refreshToken) {
        if (!account.configured) {
            notice = "Community catalog is unavailable until Firebase is configured."
            published = emptyList()
        } else {
            busy = true
            try {
                published = repo.published()
                val memberList = if (account.profile != null)
                    repo.joined().filter { it.selfConfirmed } else emptyList()
                joined = memberList.map { it.packageName }.toSet()
                val known = published.associateBy { it.packageName }
                val synced = memberList.mapNotNull { membership ->
                    val remote = known[membership.packageName] ?: runCatching {
                        repo.publishedApp(membership.packageName)
                    }.getOrNull()
                    val local = installed.firstOrNull {
                        it.packageName == membership.packageName
                    }
                    if (remote != null && local != null) remote to local else null
                }
                if (synced.isNotEmpty()) onSync(synced)
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (selected == null) {
            SeqSectionTitle("App List", Icons.Outlined.Layers,
                trailing = {
                    SeqIconAction(Icons.Outlined.Refresh, "Refresh catalog",
                        onClick = { refresh++ }, enabled = !busy)
                })
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
                }
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
                    val local = installed.firstOrNull { it.packageName == app.packageName }
                    val member = app.packageName in joined
                    SeqCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SeqAppLetter(app.packageName, app.name)
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.name, fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.packageName, color = SeqColors.Muted,
                                        fontSize = 11.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                }
                                if (member) SeqChip("Joined", SeqColors.Success,
                                    icon = Icons.Outlined.CheckCircle)
                                else if (account.profile?.uid == app.ownerUid)
                                    SeqChip("My app", SeqColors.Teal)
                            }
                            if (app.description.isNotBlank())
                                Text(app.description, color = SeqColors.Muted,
                                    fontSize = 12.sp, maxLines = 3,
                                    overflow = TextOverflow.Ellipsis)
                            Text("Developer · " + app.ownerName,
                                color = SeqColors.Muted, fontSize = 11.sp)
                            if (member) Text(
                                progress.days(app.packageName).toString() +
                                    " local QA days (not Google-verified)",
                                color = SeqColors.Muted, fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                if (member && local != null) {
                                    SeqTextButton(onClick = { onJoined(app, local) },
                                        modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Outlined.PlayArrow, null,
                                            Modifier.size(18.dp))
                                        Text("Test", fontSize = 12.sp)
                                    }
                                }
                                if (account.profile?.uid != app.ownerUid) {
                                    SeqTextButton(onClick = { selected = app },
                                        enabled = account.profile != null,
                                        modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Outlined.PersonAddAlt, null,
                                            Modifier.size(18.dp))
                                        Text(if (member) "Links" else "Join tester",
                                            fontSize = 12.sp)
                                    }
                                }
                                SeqIconAction(
                                    Icons.Outlined.OpenInNew, "Open on Google Play",
                                    onClick = { openSafeLink(context, app.playStoreUrl) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val app = requireNotNull(selected)
            val local = installed.firstOrNull { it.packageName == app.packageName }
            val member = app.packageName in joined
            SeqTextButton(onClick = { selected = null }) {
                Icon(Icons.Outlined.ArrowBack, null, Modifier.size(20.dp))
                Text("App List")
            }
            SeqSectionTitle(app.name, Icons.Outlined.Apps)
            Text(app.packageName, color = SeqColors.Muted, fontSize = 12.sp)
            Text("Complete each step in Google services. Sequmio cannot " +
                "independently verify Google Groups or Play tester enrollment.",
                color = SeqColors.Muted, fontSize = 12.sp)
            SeqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeqSectionTitle("1. Join tester group", Icons.Outlined.Groups)
                    SeqTextButton(onClick = { openSafeLink(context, app.googleGroupUrl) }) {
                        Icon(Icons.Outlined.OpenInNew, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Open Google Group")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(groupConfirmed || member,
                            onCheckedChange = { groupConfirmed = it }, enabled = !member)
                        Text("I joined the Google Group", fontSize = 13.sp)
                    }
                }
            }
            SeqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeqSectionTitle("2. Become a tester", Icons.Outlined.VerifiedUser)
                    SeqTextButton(onClick = { openSafeLink(context, app.optInUrl) }) {
                        Icon(Icons.Outlined.OpenInNew, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Open Google Play opt-in")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(optInConfirmed || member,
                            onCheckedChange = { optInConfirmed = it }, enabled = !member)
                        Text("I accepted the invitation", fontSize = 13.sp)
                    }
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
                SeqPrimaryButton("Confirm Join & add to Runner", onClick = {
                    scope.launch {
                        busy = true
                        try {
                            repo.confirmJoin(app, groupConfirmed, optInConfirmed, local != null)
                            joined = joined + app.packageName
                            if (local != null) onJoined(app, local)
                            notice = "Joined (self-confirmed). Added to Runner."
                        } catch (error: Exception) {
                            notice = "Join failed: " + (error.message ?: error.javaClass.simpleName)
                        } finally { busy = false }
                    }
                }, enabled = !busy && account.profile != null &&
                    groupConfirmed && optInConfirmed && local != null,
                    modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.Check)
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
    onSave: (MyAppDraft) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenScenarios: (MyAppDraft) -> Unit,
    onDashboard: (MyAppDraft) -> Unit,
    recorderState: RecorderState,
    recorderNotice: String,
    onTest: (MyAppDraft) -> Unit,
    onRecord: (MyAppDraft) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(account) { CommunityRepository(account) }
    var form by remember(initial) { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var published by remember { mutableStateOf(false) }
    var detailsExpanded by rememberSaveable { mutableStateOf(true) }
    var linksExpanded by rememberSaveable { mutableStateOf(true) }
    val profile = account.profile
    LaunchedEffect(profile?.uid, initial.packageName) {
        if (profile != null && initial.packageName.isNotBlank()) {
            try { published = repo.myPublishedApp(initial.packageName)?.status == "published" }
            catch (_: Exception) { published = false }
        } else published = false
    }
    fun save() {
        onSave(form)
        notice = "Draft saved locally."
    }
    val cases = runCatching { ScenarioCodec.split(form.scenarioJson) }
        .getOrDefault(emptyList())
    val valid = runCatching {
        ScenarioCodec.parseAll(form.scenarioJson, form.packageName)
    }.isSuccess

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        SeqSectionTitle("My App", Icons.Outlined.Code, tint = SeqColors.Primary)
        Text("Developer workspace · Manage your app, test cases and QA coverage.",
            color = SeqColors.Muted, fontSize = 12.sp)
        if (profile == null) {
            SeqTextButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.PersonOutline, null, Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("Sign in to publish")
                Icon(Icons.Outlined.ArrowForward, null, Modifier.size(16.dp))
            }
        } else {
            SeqChip("Owner · " + profile.name.ifBlank { profile.email },
                color = SeqColors.Teal, icon = Icons.Outlined.PersonOutline)
        }
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SeqSectionTitle("QA Overview", Icons.Outlined.Insights)
                Text("Run results, PASS / FAIL by test case, tested Android devices " +
                    "and app version comparison.",
                    color = SeqColors.Muted, fontSize = 12.sp)
                SeqPrimaryButton(
                    "Open QA Dashboard", onClick = { onDashboard(form) },
                    enabled = form.packageName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.BarChart
                )
            }
        }
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SeqSectionTitle("App Details", Icons.Outlined.Widgets,
                    trailing = {
                        SeqIconAction(
                            if (detailsExpanded) Icons.Outlined.ExpandLess
                                else Icons.Outlined.ExpandMore,
                            if (detailsExpanded) "Collapse app details"
                                else "Expand app details",
                            onClick = { detailsExpanded = !detailsExpanded }
                        )
                    })
                if (detailsExpanded) {
                    CatalogTextField("App name", form.appName) {
                        form = form.copy(appName = it)
                    }
                    CatalogTextField("Package name", form.packageName) {
                        form = form.copy(packageName = it)
                    }
                    CatalogTextField("Description", form.description, 3) {
                        form = form.copy(description = it)
                    }
                } else {
                    Text(form.appName.ifBlank { "Application not named yet" },
                        fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(form.packageName.ifBlank { "Set the Android package name" },
                        color = SeqColors.Muted, fontSize = 12.sp)
                }
            }
        }
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)) {
                SeqSectionTitle("Closed-test Links", Icons.Outlined.Link,
                    trailing = {
                        SeqIconAction(
                            if (linksExpanded) Icons.Outlined.ExpandLess
                                else Icons.Outlined.ExpandMore,
                            if (linksExpanded) "Collapse test links" else "Expand test links",
                            onClick = { linksExpanded = !linksExpanded }
                        )
                    })
                if (linksExpanded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Groups, null, tint = SeqColors.Teal,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("1. Google Group", Modifier.weight(1f),
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        SeqIconAction(Icons.Outlined.OpenInNew, "Open Google Group",
                            onClick = { openSafeLink(
                                context, form.googleGroupUrl) },
                            enabled = form.googleGroupUrl.startsWith("https://"))
                    }
                    CatalogTextField("Group URL", form.googleGroupUrl) {
                        form = form.copy(googleGroupUrl = it)
                    }
                    HorizontalDivider(color = SeqColors.Border.copy(alpha = 0.5f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.VerifiedUser, null, tint = SeqColors.Teal,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("2. Become a Tester", Modifier.weight(1f),
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        SeqIconAction(Icons.Outlined.OpenInNew, "Open tester opt-in",
                            onClick = { openSafeLink(
                                context, form.optInUrl) },
                            enabled = form.optInUrl.startsWith("https://"))
                    }
                    CatalogTextField("Opt-in URL", form.optInUrl) {
                        form = form.copy(optInUrl = it)
                    }
                    HorizontalDivider(color = SeqColors.Border.copy(alpha = 0.5f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Shop, null, tint = SeqColors.Teal,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("3. Google Play", Modifier.weight(1f),
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        SeqIconAction(Icons.Outlined.OpenInNew, "Open Google Play",
                            onClick = { openSafeLink(
                                context, form.playStoreUrl) },
                            enabled = form.playStoreUrl.startsWith("https://"))
                    }
                    CatalogTextField("Play Store URL", form.playStoreUrl) {
                        form = form.copy(playStoreUrl = it)
                    }
                } else {
                    Text("Google Group · Become a Tester · Play Store",
                        color = SeqColors.Muted, fontSize = 12.sp)
                }
            }
        }
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)) {
                SeqSectionTitle("Test Suites", Icons.Outlined.Layers,
                    tint = SeqColors.Teal,
                    trailing = {
                        SeqChip(if (valid) "Valid" else "Needs QA",
                            if (valid) SeqColors.Success else SeqColors.Error,
                            icon = if (valid) Icons.Outlined.CheckCircle else
                                Icons.Outlined.ErrorOutline)
                    })
                Text(cases.size.toString() + " test cases · Smoke / Regression / Feature",
                    color = SeqColors.Muted, fontSize = 12.sp)
                SeqTextButton(onClick = { onOpenScenarios(form) }) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Manage test cases")
                }
                SeqTextButton(onClick = { onTest(form) }) {
                    Icon(Icons.Outlined.PlayArrow, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add scenarios to Runner")
                }
                SeqPrimaryButton("Record UI actions", onClick = { onRecord(form) },
                    modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.FiberManualRecord,
                    enabled = !busy && !recorderState.recording &&
                        form.packageName.isNotBlank())
                Text("Record taps and scrolls using the floating Pause/Stop panel. " +
                    "Typed characters are not collected.",
                    color = SeqColors.Muted, fontSize = 11.sp)
                if (recorderState.message.isNotBlank()) {
                    Text(recorderState.message, color = SeqColors.Teal, fontSize = 12.sp)
                }
                if (recorderNotice.isNotBlank()) {
                    Text(recorderNotice, color = SeqColors.Muted, fontSize = 12.sp)
                }
            }
        }
        SeqPrimaryButton("Save local draft", onClick = { save() },
            modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.Save)
        val errors = form.validateForPublish()
        SeqPrimaryButton(if (published) "Update published app" else "Publish to App List",
            onClick = {
                scope.launch {
                    busy = true
                    notice = ""
                    try {
                        save()
                        repo.savePublished(form, form.scenarioJson)
                        published = true
                        notice = "Published successfully. Your app is now in App List."
                    } catch (error: Exception) {
                        notice = "Publish failed: " + (error.message ?: error.javaClass.simpleName)
                    } finally { busy = false }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Outlined.CloudUpload,
            enabled = profile != null && !busy && errors.isEmpty()
        )
        if (published) {
            SeqTextButton(onClick = {
                scope.launch {
                    busy = true
                    try {
                        repo.unpublish(form.packageName)
                        published = false
                        notice = "Listing unpublished."
                    } catch (error: Exception) {
                        notice = "Unpublish failed: " + (error.message ?: "unknown")
                    } finally { busy = false }
                }
            }) {
                Icon(Icons.Outlined.VisibilityOff, null, Modifier.size(18.dp))
                Text("Unpublish")
            }
        }
        if (errors.isNotEmpty()) {
            Text("Publishing requirements", fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp)
            errors.take(6).forEach {
                Text("• " + it, color = SeqColors.Muted, fontSize = 11.sp)
            }
        }
        if (notice.isNotBlank()) {
            Text(notice, color = if (notice.startsWith("Publish failed"))
                SeqColors.Error else SeqColors.Success, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
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
