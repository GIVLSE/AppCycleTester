package com.sequmio.givlse

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sequmio.givlse.account.AccountProfile
import com.sequmio.givlse.account.SequmioAccount
import com.sequmio.givlse.data.MyAppDraft
import com.sequmio.givlse.data.CloudUploadPrefs
import kotlinx.coroutines.launch

private fun openExternal(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
internal fun SequmioAppList(onOpenSettings: () -> Unit, profile: AccountProfile?) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SeqTopBar("App List")
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Discover apps to test", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Find other developers' closed-test applications, join their tester group, " +
                        "accept the Google Play invitation, and install the app.",
                    color = SeqColors.Muted, fontSize = 14.sp
                )
                Text("Catalog coming in V0.6", color = SeqColors.Teal,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    "No public apps are available yet. Sequmio does not list sample apps " +
                        "or claim that joining a Google Group can be verified automatically.",
                    color = SeqColors.Muted, fontSize = 13.sp
                )
            }
        }
        if (profile == null) {
            SeqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Google account required", fontWeight = FontWeight.SemiBold)
                    Text("Local tests still work without an account. Sign in to use " +
                        "the future community catalog and Join workflow.",
                        color = SeqColors.Muted)
                    SeqPrimaryButton("Open Profile & Settings", onOpenSettings,
                        Modifier.fillMaxWidth())
                }
            }
        } else {
            Text("Signed in as " + (profile.name.ifBlank { profile.email }),
                color = SeqColors.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SeqInput(
    title: String, value: String, onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(title) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 3,
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
internal fun SequmioMyApp(
    initial: MyAppDraft,
    profile: AccountProfile?,
    onSave: (MyAppDraft) -> Unit,
    onOpenSettings: () -> Unit
) {
    var appName by remember(initial) { mutableStateOf(initial.appName) }
    var packageName by remember(initial) { mutableStateOf(initial.packageName) }
    var description by remember(initial) { mutableStateOf(initial.description) }
    var groupUrl by remember(initial) { mutableStateOf(initial.googleGroupUrl) }
    var optInUrl by remember(initial) { mutableStateOf(initial.optInUrl) }
    var playUrl by remember(initial) { mutableStateOf(initial.playStoreUrl) }
    var saved by remember { mutableStateOf(false) }
    val draft = MyAppDraft(appName.trim(), packageName.trim(), description.trim(),
        groupUrl.trim(), optInUrl.trim(), playUrl.trim())
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SeqTopBar("My App")
        Text("Your developer listing", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Save a local draft now. Publishing requires a Google account, " +
            "server catalog, and at least one runnable E2E scenario.",
            color = SeqColors.Muted, fontSize = 13.sp)
        if (profile == null) {
            SeqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Not signed in", fontWeight = FontWeight.SemiBold)
                    Text("You can edit a draft offline. Sign in before publishing.",
                        color = SeqColors.Muted)
                    SeqTextButton(onClick = onOpenSettings) { Text("Open Profile") }
                }
            }
        }
        SeqInput("App name", appName, { appName = it; saved = false })
        SeqInput("Android package name", packageName, { packageName = it; saved = false })
        SeqInput("Short description", description, { description = it; saved = false },
            singleLine = false)
        Text("Closed testing links", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        SeqInput("1. Google Group URL", groupUrl, { groupUrl = it; saved = false })
        SeqInput("2. Become a Tester URL", optInUrl, { optInUrl = it; saved = false })
        SeqInput("3. Google Play URL", playUrl, { playUrl = it; saved = false })
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Test Scenarios", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("0 runnable scenarios", color = SeqColors.Error)
                Text(
                    "An E2E scenario will be mandatory for publication. " +
                        "Scenario Player arrives in V0.7 and Recorder in V0.8. " +
                        "No test actions are recorded in this release.",
                    color = SeqColors.Muted, fontSize = 13.sp
                )
            }
        }
        SeqPrimaryButton("Save draft on this device",
            onClick = { onSave(draft); saved = true }, modifier = Modifier.fillMaxWidth())
        if (saved) Text("Draft saved locally.", color = SeqColors.Success, fontSize = 13.sp)
        Text("Publish to App List — unavailable in V0.5",
            color = SeqColors.Muted, fontSize = 13.sp)
        val validation = draft.validateForPublish()
        if (validation.isNotEmpty()) {
            Text("Before publishing:", fontWeight = FontWeight.SemiBold)
            validation.forEach { Text("• " + it, color = SeqColors.Muted, fontSize = 12.sp) }
        }
    }
}

@Composable
internal fun SequmioSettings(
    account: SequmioAccount,
    onBack: () -> Unit,
    onPermissions: () -> Unit,
    onDisplaySettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile = account.profile
    val uploadPrefs = remember { CloudUploadPrefs(context) }
    var shareResults by remember { mutableStateOf(uploadPrefs.enabled) }
    var confirmSharing by remember { mutableStateOf(false) }
    var confirmDeletion by remember { mutableStateOf(false) }
    LaunchedEffect(profile?.uid) {
        if (profile != null) account.refreshDailyDigest()
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SeqTopBar("Settings", onBack)
        Text("Profile", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (profile != null) {
                    Text(profile.name.ifBlank { "Google account" }, fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text(profile.email, color = SeqColors.Muted)
                    Text("Google account connected", color = SeqColors.Success, fontSize = 13.sp)
                    SeqTextButton(onClick = { scope.launch { account.signOut() } }) {
                        Text("Sign out")
                    }
                    SeqTextButton(onClick = { confirmDeletion = true }) {
                        Text("Delete my Sequmio account", color = SeqColors.Error)
                    }
                } else {
                    Text("Sign in with Google", fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text("Connect your identity to a future My App listing and " +
                        "community testing participation. The app stores your public " +
                        "display name, email, and profile image URL in Firebase " +
                        "when you sign in.", color = SeqColors.Muted, fontSize = 13.sp)
                    if (account.configured) {
                        SeqPrimaryButton("Continue with Google", onClick = {
                            val activity = context as? Activity
                            if (activity != null) scope.launch { account.signIn(activity) }
                        }, modifier = Modifier.fillMaxWidth(), enabled = !account.busy)
                    } else {
                        Text(
                            "Google sign-in is not configured for this build. " +
                                "Firebase project and OAuth client IDs must be connected " +
                                "by the app developer. No demo account is used.",
                            color = SeqColors.Error, fontSize = 13.sp
                        )
                    }
                }
                if (account.busy) Text("Signing in...", color = SeqColors.Muted)
                account.status?.let { Text(it, color = SeqColors.Muted, fontSize = 13.sp) }
            }
        }
        Text("QA report sharing", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatically share QA results", fontWeight = FontWeight.SemiBold)
                        Text("Default OFF. After a run, send your tester ID, " +
                            "device model, Android version, tested app version, " +
                            "scenario/suite and PASS/FAIL step metadata to that " +
                            "app's developer. No screen contents, input text or " +
                            "passwords are uploaded.",
                            color = SeqColors.Muted, fontSize = 12.sp)
                    }
                    Switch(checked = shareResults, enabled = profile != null &&
                        account.configured, onCheckedChange = { desired ->
                        if (desired) confirmSharing = true else {
                            shareResults = false
                            uploadPrefs.enabled = false
                        }
                    })
                }
                if (profile == null) Text("Sign in with Google before enabling " +
                    "automatic sharing.", color = SeqColors.Muted, fontSize = 12.sp)
                Text(uploadPrefs.lastStatus, color = SeqColors.Muted, fontSize = 12.sp)
                Text("Your local Runner and reports work with sharing disabled.",
                    color = SeqColors.Muted, fontSize = 12.sp)
            }
        }
        if (profile != null) {
            SeqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Daily email QA digest",
                                fontWeight = FontWeight.SemiBold)
                            Text("Optional. Email your previous UTC day's QA " +
                                "summary when the cloud scheduler and email " +
                                "provider are configured. Reports are also shown " +
                                "in My App → QA Dashboard.",
                                color = SeqColors.Muted, fontSize = 12.sp)
                        }
                        Switch(checked = account.dailyDigest,
                            onCheckedChange = { requested ->
                                scope.launch { account.setDailyDigest(requested) }
                            })
                    }
                }
            }
        }
        Text("App settings", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SeqTextButton(onClick = onPermissions) { Text("Permissions & Accessibility →") }
                SeqTextButton(onClick = onDisplaySettings) { Text("Android screen timeout →") }
                Text("Runner configuration stays in the Runner tab.",
                    color = SeqColors.Muted, fontSize = 13.sp)
            }
        }
        Text("About", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Sequmio v" + BuildConfig.VERSION_NAME,
                    fontWeight = FontWeight.SemiBold)
                Text("Package: " + BuildConfig.APPLICATION_ID,
                    color = SeqColors.Muted, fontSize = 12.sp)
                SeqTextButton(onClick = {
                    openExternal(context, "https://givlse.github.io/AppCycleTester/privacy.html")
                }) { Text("Privacy Policy") }
                SeqTextButton(onClick = {
                    openExternal(context, "https://givlse.github.io/AppCycleTester/terms.html")
                }) { Text("Terms of Service") }
                SeqTextButton(onClick = {
                    openExternal(context, "https://givlse.github.io/AppCycleTester/delete-account.html")
                }) { Text("Account and data deletion") }
                SeqTextButton(onClick = {
                    openExternal(context, "https://github.com/GIVLSE/AppCycleTester/issues")
                }) { Text("Support / Report a problem") }
                SeqTextButton(onClick = {
                    openExternal(context, "https://github.com/GIVLSE/AppCycleTester")
                }) { Text("Source code") }
            }
        }
    }
    if (confirmSharing) {
        AlertDialog(
            onDismissRequest = { confirmSharing = false },
            containerColor = SeqColors.CardRaised,
            titleContentColor = SeqColors.Text,
            textContentColor = SeqColors.Text,
            title = { Text("Allow automatic QA report upload?") },
            text = { Text("Following each completed or interrupted test, " +
                "Sequmio may send only test metadata to the app owner: " +
                "your account tester ID, device model, Android/API version, " +
                "tested build version, scenario steps, timing and outcomes. " +
                "No screen or typed text is sent. You can turn this off at " +
                "any time in Settings.") },
            confirmButton = {
                SeqTextButton(onClick = {
                    uploadPrefs.enabled = true
                    shareResults = true
                    confirmSharing = false
                }) { Text("Enable sharing") }
            },
            dismissButton = {
                SeqTextButton(onClick = { confirmSharing = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (confirmDeletion) {
        AlertDialog(
            onDismissRequest = { if (!account.busy) confirmDeletion = false },
            containerColor = SeqColors.CardRaised,
            titleContentColor = SeqColors.Text,
            textContentColor = SeqColors.Text,
            title = { Text("Delete Sequmio account?") },
            text = { Text("This removes your Firebase account, published apps, " +
                "profile and joined test memberships. It does not delete the " +
                "Google account itself. Local test plans remain on this device.") },
            confirmButton = {
                SeqTextButton(onClick = {
                    confirmDeletion = false
                    scope.launch { account.deleteAccount() }
                }, enabled = !account.busy) {
                    Text("Delete account", color = SeqColors.Error)
                }
            },
            dismissButton = {
                SeqTextButton(onClick = { confirmDeletion = false }) { Text("Cancel") }
            }
        )
    }

}
