package com.sequmio.givlse

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.text.style.TextOverflow
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
private fun SettingsMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .heightIn(min = 72.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(12.dp),
            color = SeqColors.Primary.copy(alpha = 0.10f)) {
            Icon(icon, contentDescription = null, tint = SeqColors.Primary,
                modifier = Modifier.padding(10.dp).size(25.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (subtitle.isNotBlank()) Text(subtitle, color = SeqColors.Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        if (trailing == null) Icon(Icons.Outlined.ChevronRight, null,
            tint = SeqColors.Muted, modifier = Modifier.size(24.dp))
        else trailing()
    }
}

@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(Modifier.padding(start = 70.dp, end = 14.dp),
        color = SeqColors.Border.copy(alpha = 0.65f))
}

@Composable
internal fun SequmioSettings(
    account: SequmioAccount,
    onBack: () -> Unit,
    onPermissions: () -> Unit,
    onDisplaySettings: () -> Unit,
    onAbout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile = account.profile
    val uploadPrefs = remember { CloudUploadPrefs(context) }
    var shareResults by remember { mutableStateOf(uploadPrefs.enabled) }
    var confirmSharing by remember { mutableStateOf(false) }
    var confirmDeletion by remember { mutableStateOf(false) }
    var showReports by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }
    var showEvidence by remember { mutableStateOf(false) }
    val canShare = profile != null && account.configured
    LaunchedEffect(profile?.uid) {
        shareResults = uploadPrefs.enabled
        if (profile != null) account.refreshDailyDigest()
    }
    fun changeSharing(desired: Boolean) {
        if (!canShare) return
        if (desired) confirmSharing = true else {
            shareResults = false
            uploadPrefs.enabled = false
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SeqTopBar("Settings", onBack)
        Text("Profile", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = SeqColors.Primary.copy(alpha = 0.16f)) {
                        Icon(Icons.Outlined.Person, null, tint = SeqColors.Primary,
                            modifier = Modifier.padding(12.dp).size(28.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile?.name?.ifBlank { "Google account" } ?: "Sign in with Google",
                            fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (profile != null) Text(profile.email, color = SeqColors.Muted, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (profile != null) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SeqColors.Primary.copy(alpha = 0.12f),
                            contentColor = if (account.busy) SeqColors.Muted else SeqColors.Primary,
                            modifier = Modifier.clickable(enabled = !account.busy) {
                                scope.launch { account.signOut() }
                            }
                        ) {
                            Row(
                                Modifier.heightIn(min = 44.dp).padding(horizontal = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Outlined.Logout, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                                Text("Sign Out", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                    maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
                if (profile != null) {
                    SeqGlassButton("Delete my Sequmio account", SeqColors.Error,
                        onClick = { confirmDeletion = true }, enabled = !account.busy,
                        modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.DeleteOutline)
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .clickable(enabled = !account.busy) {
                                if (account.configured) {
                                    val activity = context as? Activity
                                    if (activity != null) scope.launch { account.signIn(activity) }
                                } else {
                                    android.widget.Toast.makeText(context, "Coming soon",
                                        android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                        shape = RoundedCornerShape(16.dp), color = Color.White,
                        contentColor = Color(0xFF1F1F1F)
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.google_sign_in_mark), null,
                                modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(if (account.busy) "Signing in..." else "Continue with Google",
                                fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                if (account.configured) account.status?.let {
                    Text(it, color = SeqColors.Muted, fontSize = 12.sp)
                }
            }
        }
        SeqCard(Modifier.fillMaxWidth()) {
            SettingsMenuRow(Icons.Outlined.Layers, "QA Reports", "Automatically share test results",
                onClick = { showReports = true }, trailing = {
                    Switch(checked = shareResults && canShare, enabled = canShare,
                        onCheckedChange = { changeSharing(it) })
                })
            SettingsRowDivider()
            SettingsMenuRow(Icons.Outlined.Image, "Screenshot Evidence",
                "Optional failure screenshots · review before sharing",
                onClick = { showEvidence = true })
            SettingsRowDivider()
            SettingsMenuRow(Icons.Outlined.VerifiedUser, "Permissions & Accessibility",
                "Required for recording and automation", onClick = onPermissions)
            SettingsRowDivider()
            SettingsMenuRow(Icons.Outlined.Timer, "Android screen timeout",
                "Configure screen timeout for test runs", onClick = onDisplaySettings)
            SettingsRowDivider()
            SettingsMenuRow(Icons.Outlined.HeadsetMic, "Support", "Help, reporting issues, and feedback",
                onClick = { showSupport = true })
            SettingsRowDivider()
            SettingsMenuRow(Icons.Outlined.Info, "About", "App version, build info, and legal",
                onClick = onAbout)
        }
    }
    if (showEvidence) EvidenceSettingsDialog(account, onDismiss = { showEvidence = false })
    if (showSupport) {
        AlertDialog(onDismissRequest = { showSupport = false },
            title = { Text("Support") },
            text = { Text("Coming soon") },
            confirmButton = { TextButton(onClick = { showSupport = false }) { Text("Close") } })
    }
    if (showReports) {
        AlertDialog(onDismissRequest = { showReports = false },
            title = { Text("QA Reports") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Share test metadata with the tested app's developer. Automatic metadata reports do not contain screenshots, screen contents, input text or passwords. Separate screenshot sharing requires explicit review.",
                        color = SeqColors.Muted, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Automatic sharing", Modifier.weight(1f))
                        Switch(checked = shareResults && canShare, enabled = canShare,
                            onCheckedChange = { desired ->
                                showReports = false
                                changeSharing(desired)
                            })
                    }
                    if (!canShare) Text("Sign in to enable sharing.", color = SeqColors.Muted, fontSize = 12.sp)
                    Text(uploadPrefs.lastStatus, color = SeqColors.Muted, fontSize = 12.sp)
                    if (profile != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Daily email QA digest", Modifier.weight(1f))
                            Switch(checked = account.dailyDigest,
                                onCheckedChange = { requested -> scope.launch { account.setDailyDigest(requested) } })
                        }
                        Text("Optional daily summary when the email service is configured.",
                            color = SeqColors.Muted, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showReports = false }) { Text("Close") } })
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
                "Automatic metadata reports contain no screenshots or typed text. You can turn this off at " +
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

@Composable
internal fun SequmioAbout(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SeqTopBar("About", onBack)
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SequmioMark(size = 76.dp)
                Text("Sequmio v" + BuildConfig.VERSION_NAME, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold)
                Text("Build " + BuildConfig.VERSION_CODE, color = SeqColors.Muted, fontSize = 13.sp)
                Text(BuildConfig.APPLICATION_ID, color = SeqColors.Muted, fontSize = 12.sp)
            }
        }
        SeqCard(Modifier.fillMaxWidth()) {
            SettingsMenuRow(Icons.Outlined.Description, "Privacy Policy", "",
                onClick = { openExternal(context, "https://givlse.github.io/AppCycleTester/privacy.html") })
            SettingsRowDivider()
            SettingsMenuRow(Icons.Outlined.Gavel, "Terms of Service", "",
                onClick = { openExternal(context, "https://givlse.github.io/AppCycleTester/terms.html") })
            SettingsRowDivider()
            SettingsMenuRow(Icons.Outlined.DeleteOutline, "Account and data deletion", "",
                onClick = { openExternal(context, "https://givlse.github.io/AppCycleTester/delete-account.html") })
            SettingsRowDivider()
            SettingsMenuRow(Icons.Outlined.Code, "Source code", "",
                onClick = { openExternal(context, "https://github.com/GIVLSE/AppCycleTester") })
        }
    }
}
