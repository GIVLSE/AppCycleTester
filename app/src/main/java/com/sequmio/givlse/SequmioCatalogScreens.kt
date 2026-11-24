package com.sequmio.givlse

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    onSync: (List<Pair<PublishedApp, InstalledApp>>) -> Unit
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
    var groupConfirmed by remember(selected?.packageName) { mutableStateOf(false) }
    var optInConfirmed by remember(selected?.packageName) { mutableStateOf(false) }

    LaunchedEffect(account.profile?.uid, refresh, refreshToken) {
        if (!account.configured) {
            notice = "Community backend is not configured. Add Firebase credentials to load real apps."
            published = emptyList()
        } else {
            busy = true
            try {
                published = repo.published()
                val membershipList = if (account.profile != null)
                    repo.joined().filter { it.selfConfirmed } else emptyList()
                joined = membershipList.map { it.packageName }.toSet()
                val known = published.associateBy { it.packageName }
                val synced = membershipList.mapNotNull { membership ->
                    val remote = known[membership.packageName] ?: runCatching {
                        repo.publishedApp(membership.packageName)
                    }.getOrNull()
                    val installedApp = installed.firstOrNull {
                        it.packageName == membership.packageName
                    }
                    if (remote != null && installedApp != null)
                        remote to installedApp else null
                }
                if (synced.isNotEmpty()) onSync(synced)
                notice = ""
            } catch (error: Exception) {
                published = emptyList()
                notice = "Catalog unavailable: " + error.javaClass.simpleName
            } finally { busy = false }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("App List", fontSize = 23.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            SeqTextButton(onClick = { refresh++ }, enabled = !busy) { Text("Refresh") }
        }
        if (selected == null) {
            Text("Join another developer's closed test. The Google Group and " +
                "Play opt-in steps must be completed by you in Google services.",
                color = SeqColors.Muted, fontSize = 13.sp)
            if (busy) CircularProgressIndicator(color = SeqColors.Teal)
            if (notice.isNotBlank()) Text(notice, color = SeqColors.Muted)
            if (account.profile == null) {
                SeqCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sign in to join testing", fontWeight = FontWeight.SemiBold)
                        Text("Browsing is possible without a login when the catalog is available. " +
                            "Joining requires Google authentication.", color = SeqColors.Muted)
                        SeqTextButton(onClick = onOpenSettings) { Text("Open Profile") }
                    }
                }
            }
            if (published.isEmpty() && !busy && notice.isBlank()) {
                SeqCard(Modifier.fillMaxWidth()) {
                    Text("No published applications yet.", Modifier.padding(18.dp),
                        color = SeqColors.Muted)
                }
            }
            published.forEach { app ->
                SeqCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(app.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(app.packageName, color = SeqColors.Muted, fontSize = 12.sp)
                        if (app.description.isNotBlank())
                            Text(app.description, color = SeqColors.Muted, fontSize = 13.sp)
                        Text("By " + app.ownerName, color = SeqColors.Muted, fontSize = 12.sp)
                        if (app.packageName in joined) {
                            Text("Joined (self-confirmed)", color = SeqColors.Success)
                            Text(progress.days(app.packageName).toString() +
                                " local QA days logged (not Google-verified)",
                                color = SeqColors.Muted, fontSize = 12.sp)
                            SeqTextButton(onClick = { selected = app }) { Text("View test links") }
                        } else if (account.profile?.uid == app.ownerUid) {
                            Text("Your application", color = SeqColors.Teal)
                        } else {
                            SeqPrimaryButton(
                                "+ Join", onClick = { selected = app },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = account.profile != null
                            )
                        }
                    }
                }
            }
        } else {
            val app = requireNotNull(selected)
            val installedApp = installed.firstOrNull { it.packageName == app.packageName }
            val alreadyJoined = app.packageName in joined
            SeqTextButton(onClick = { selected = null }) { Text("← Back to App List") }
            Text(app.name, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(app.packageName, color = SeqColors.Muted, fontSize = 12.sp)
            Text("Complete the following three steps outside Sequmio. " +
                "Sequmio cannot check Google Groups or Play opt-in membership. " +
                "Your marks are self-confirmations, not Google verification.",
                color = SeqColors.Muted, fontSize = 13.sp)
            SeqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("1. Join the Google Group", fontWeight = FontWeight.SemiBold)
                    SeqTextButton(onClick = { openSafeLink(context, app.googleGroupUrl) }) {
                        Text("Open tester group ↗")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = groupConfirmed || alreadyJoined,
                            enabled = !alreadyJoined,
                            onCheckedChange = { groupConfirmed = it })
                        Text("I joined the tester group", color = SeqColors.Text)
                    }
                }
            }
            SeqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("2. Become a tester", fontWeight = FontWeight.SemiBold)
                    SeqTextButton(onClick = { openSafeLink(context, app.optInUrl) }) {
                        Text("Open Google Play opt-in ↗")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = optInConfirmed || alreadyJoined,
                            enabled = !alreadyJoined,
                            onCheckedChange = { optInConfirmed = it })
                        Text("I accepted the invitation", color = SeqColors.Text)
                    }
                }
            }
            SeqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("3. Install the app", fontWeight = FontWeight.SemiBold)
                    SeqTextButton(onClick = { openSafeLink(context, app.playStoreUrl) }) {
                        Text("Open Google Play ↗")
                    }
                    Text(if (installedApp != null) "Installed on this device"
                        else "Not detected. Install it and return to Sequmio.",
                        color = if (installedApp != null) SeqColors.Success else SeqColors.Muted)
                }
            }
            if (notice.isNotBlank()) Text(notice, color = SeqColors.Muted)
            if (alreadyJoined) {
                Text("Joined. If the app is already in Runner, its scenario will run " +
                    "when the Player feature is enabled.", color = SeqColors.Success)
                if (installedApp != null) {
                    SeqTextButton(onClick = {
                        onJoined(app, installedApp)
                        notice = "Added/updated in Runner."
                    }) { Text("Add/update in Runner") }
                }
            } else {
                SeqPrimaryButton(
                    "Confirm Join & add to Runner",
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                repo.confirmJoin(app, groupConfirmed, optInConfirmed, installedApp != null)
                                joined = joined + app.packageName
                                if (installedApp != null) onJoined(app, installedApp)
                                notice = "Joined (self-confirmed). App added to Runner."
                            } catch (error: Exception) {
                                notice = "Join failed: " + (error.message ?: error.javaClass.simpleName)
                            } finally { busy = false }
                        }
                    },
                    enabled = !busy && account.profile != null &&
                        groupConfirmed && optInConfirmed && installedApp != null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
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
    recorderState: RecorderState,
    recorderNotice: String,
    onRecord: (MyAppDraft) -> Unit
) {
    val scope = rememberCoroutineScope()
    val repo = remember(account) { CommunityRepository(account) }
    var form by remember(initial) { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var published by remember { mutableStateOf(false) }
    val profile = account.profile

    LaunchedEffect(profile?.uid, initial.packageName) {
        if (profile != null && initial.packageName.isNotBlank()) {
            try { published = repo.myPublishedApp(initial.packageName)?.status == "published" }
            catch (_: Exception) { published = false }
        } else { published = false }
    }
    fun save() { onSave(form); notice = "Draft saved on this device." }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SeqTopBar("My App")
        Text("Developer listing", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Your details are saved locally until you choose Publish. " +
            "A public listing requires a Google account and a valid UI scenario.",
            color = SeqColors.Muted, fontSize = 13.sp)
        if (profile == null) {
            SeqTextButton(onClick = onOpenSettings) {
                Text("Sign in to publish →")
            }
        } else {
            Text("Owner: " + profile.name.ifBlank { profile.email },
                color = SeqColors.Teal, fontSize = 13.sp)
        }
        CatalogTextField("App name", form.appName) { form = form.copy(appName = it) }
        CatalogTextField("Package name", form.packageName) { form = form.copy(packageName = it) }
        CatalogTextField("Description", form.description, 3) { form = form.copy(description = it) }
        Text("Closed-test links", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        CatalogTextField("1. Google Group", form.googleGroupUrl) {
            form = form.copy(googleGroupUrl = it)
        }
        CatalogTextField("2. Become a Tester", form.optInUrl) {
            form = form.copy(optInUrl = it)
        }
        CatalogTextField("3. Google Play", form.playStoreUrl) {
            form = form.copy(playStoreUrl = it)
        }
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Test Scenario", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                val valid = runCatching {
                    ScenarioCodec.parse(form.scenarioJson, form.packageName)
                }.isSuccess
                Text(if (valid) "Valid scenario defined" else "Scenario required",
                    color = if (valid) SeqColors.Success else SeqColors.Error)
                SeqTextButton(onClick = { onOpenScenarios(form) }) {
                    Text("Edit scenario JSON →")
                }
                SeqPrimaryButton("Record UI actions", onClick = { onRecord(form) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && !recorderState.recording && form.packageName.isNotBlank())
                Text("Record clicks, scrolls and editable-field events in your own installed " +
                    "app. A floating panel provides Pause/Stop. Typed content is not captured; " +
                    "replace any text placeholders before publishing.",
                    color = SeqColors.Muted, fontSize = 12.sp)
                if (recorderState.message.isNotBlank())
                    Text(recorderState.message, color = SeqColors.Muted, fontSize = 12.sp)
                if (recorderNotice.isNotBlank())
                    Text(recorderNotice, color = SeqColors.Muted, fontSize = 12.sp)
            }
        }
        SeqPrimaryButton("Save local draft", onClick = { save() },
            modifier = Modifier.fillMaxWidth())
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
            }) { Text("Unpublish") }
        }
        if (errors.isNotEmpty()) {
            Text("Publishing requirements:", fontWeight = FontWeight.SemiBold)
            errors.take(6).forEach { Text("• " + it, color = SeqColors.Muted, fontSize = 12.sp) }
        }
        if (notice.isNotBlank()) Text(notice,
            color = if (notice.startsWith("Publish failed")) SeqColors.Error else SeqColors.Muted)
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
