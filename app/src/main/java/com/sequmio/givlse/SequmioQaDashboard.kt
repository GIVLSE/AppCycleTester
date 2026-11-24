package com.sequmio.givlse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sequmio.givlse.account.SequmioAccount
import com.sequmio.givlse.data.QaDashboardData
import com.sequmio.givlse.data.QaDashboardRepository
import com.sequmio.givlse.data.QaRun
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SequmioQaDashboard(
    appId: String,
    account: SequmioAccount,
    onBack: () -> Unit
) {
    val repository = remember(account) { QaDashboardRepository(account) }
    var data by remember { mutableStateOf<QaDashboardData?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(appId, account.profile?.uid, refresh) {
        loading = true
        error = ""
        data = null
        try {
            data = repository.load(appId)
        } catch (exception: Exception) {
            error = "Dashboard unavailable: " +
                (exception.message?.take(150) ?: exception.javaClass.simpleName)
        } finally { loading = false }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        SeqTopBar("QA Dashboard", onBack)
        Text(appId, fontSize = 12.sp, color = SeqColors.Muted)
        Text("Actual submitted test results only. Date grouping uses UTC. " +
            "Only signed-in QA participants who opted in share run metadata.",
            fontSize = 12.sp, color = SeqColors.Muted)
        SeqTextButton(onClick = { refresh++ }, enabled = !loading) { Text("Refresh data") }
        if (loading) CircularProgressIndicator(color = SeqColors.Teal)
        if (error.isNotBlank()) Text(error, color = SeqColors.Error)
        val summary = data
        if (summary != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MetricTile("Runs", summary.total.toString(), Modifier.weight(1f))
                MetricTile("PASS", summary.pass.toString(), Modifier.weight(1f))
                MetricTile("FAIL", summary.fail.toString(), Modifier.weight(1f))
                MetricTile("Devices", summary.devices.toString(), Modifier.weight(1f))
            }
            Text("Recent runs · up to 250 loaded", fontSize = 17.sp,
                fontWeight = FontWeight.Bold)
            if (summary.runs.isEmpty()) {
                SeqCard(Modifier.fillMaxWidth()) {
                    Text("No cloud QA reports yet. Testers must enable automatic " +
                        "result sharing, sign in and complete actual runs.",
                        Modifier.padding(16.dp), color = SeqColors.Muted)
                }
            }
            summary.runs.take(25).forEach { run ->
                SeqCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(run.status, color = statusColor(run.status),
                                fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(formatRunDate(run.endedAt), color = SeqColors.Muted,
                                fontSize = 11.sp)
                        }
                        Text(run.scenario.ifBlank { "Scenario" },
                            fontWeight = FontWeight.SemiBold)
                        Text(run.suite + " · v" + run.versions + " · build " +
                            run.build, fontSize = 12.sp, color = SeqColors.Muted)
                        Text(run.model + " · Android API " + run.androidApi,
                            fontSize = 12.sp, color = SeqColors.Muted)
                        SeqTextButton(onClick = {
                            expandedId = if (expandedId == run.id) null else run.id
                        }) { Text(if (expandedId == run.id) "Hide step results" else
                            "Step results (" + run.steps.size + ")") }
                        if (expandedId == run.id) {
                            run.steps.take(75).forEach { step ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement =
                                    Arrangement.spacedBy(8.dp)) {
                                    Text("#" + step.step, color = SeqColors.Muted,
                                        fontSize = 12.sp)
                                    Column(Modifier.weight(1f)) {
                                        Text(step.scenario + ": " + step.action,
                                            fontSize = 12.sp)
                                        Text(step.detail + " · " + step.ms + "ms",
                                            color = SeqColors.Muted, fontSize = 11.sp)
                                    }
                                    Text(step.status, color = statusColor(step.status),
                                        fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            Text("Build comparison", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            val builds = summary.byBuild.entries.sortedByDescending { entry ->
                entry.value.maxOfOrNull { it.endedAt } ?: 0
            }
            if (builds.isEmpty()) Text("No builds to compare",
                color = SeqColors.Muted, fontSize = 12.sp)
            builds.take(6).forEach { entry ->
                val pass = entry.value.count { it.status == "PASS" }
                val fail = entry.value.count { it.status == "FAIL" }
                SeqCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Build " + entry.key, modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Medium)
                        Text(pass.toString() + " PASS", color = SeqColors.Success,
                            fontSize = 12.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(fail.toString() + " FAIL", color = SeqColors.Error,
                            fontSize = 12.sp)
                    }
                }
            }

            Text("Smoke / Regression / Feature", fontSize = 17.sp,
                fontWeight = FontWeight.Bold)
            summary.bySuite.forEach { entry ->
                val pass = entry.value.count { it.status == "PASS" }
                Text(entry.key + " · " + pass + "/" + entry.value.size + " PASS",
                    color = SeqColors.Muted, fontSize = 13.sp)
            }
            Text("Previous daily summaries (UTC)", fontSize = 17.sp,
                fontWeight = FontWeight.Bold)
            if (summary.digests.isEmpty()) {
                Text("No scheduled summaries yet. The Cloud Function must be " +
                    "deployed and run after a completed UTC day.",
                    color = SeqColors.Muted, fontSize = 12.sp)
            }
            summary.digests.take(14).forEach { digest ->
                SeqCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(digest.day, fontWeight = FontWeight.SemiBold)
                        Text("Runs " + digest.total + " · PASS " + digest.pass +
                            " · FAIL " + digest.fail + " · other " +
                            (digest.stopped + digest.incomplete),
                            color = SeqColors.Muted, fontSize = 12.sp)
                        Text(digest.devices.toString() + " devices · " +
                            digest.testers + " testers" +
                            if (digest.emailed) " · email delivered" else " · in-app report",
                            color = SeqColors.Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier) {
    SeqCard(modifier) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, fontSize = 11.sp, color = SeqColors.Muted)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun statusColor(value: String) = when(value) {
    "PASS" -> SeqColors.Success
    "FAIL" -> SeqColors.Error
    else -> SeqColors.Muted
}
private fun formatRunDate(time: Long): String =
    SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(time))
