package com.sequmio.givlse

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sequmio.givlse.account.SequmioAccount
import com.sequmio.givlse.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val QaOther = Color(0xFFFBBF24)
private enum class DashboardTab { Overview, Cases, Devices, Reports }

@Composable
internal fun SequmioQaDashboard(appId: String, account: SequmioAccount, onBack: () -> Unit) {
    val uid = account.profile?.uid
    val repository = remember(account) { QaDashboardRepository(account) }
    var data by remember(appId, uid) { mutableStateOf<QaDashboardData?>(null) }
    var loading by remember(appId, uid) { mutableStateOf(false) }
    var error by remember(appId, uid) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var days by remember(appId, uid) { mutableIntStateOf(7) }
    var tab by remember(appId, uid) { mutableStateOf(DashboardTab.Overview) }
    var build by remember(appId, uid) { mutableStateOf("All builds") }
    var suite by remember(appId, uid) { mutableStateOf("All suites") }
    var caseFilter by remember(appId, uid) { mutableStateOf<QaCaseKey?>(null) }
    var deviceFilter by remember(appId, uid) { mutableStateOf<String?>(null) }
    var failureFilter by remember(appId, uid) { mutableStateOf<QaFailureKey?>(null) }
    var statusFilter by remember(appId, uid) { mutableStateOf("All") }
    var search by remember(appId, uid) { mutableStateOf("") }
    var selectedSession by remember(appId, uid) { mutableStateOf<Pair<String,String>?>(null) }
    var selectedCase by remember(appId, uid) { mutableStateOf<QaCaseKey?>(null) }
    var expandedCase by remember(appId, uid) { mutableStateOf<String?>(null) }
    var includeOwner by remember(appId, uid) { mutableStateOf(false) }
    var reportLimit by remember(appId, uid) { mutableIntStateOf(20) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExport by remember(appId, uid) { mutableStateOf<List<QaRun>>(emptyList()) }
    var pendingUid by remember(appId, uid) { mutableStateOf<String?>(null) }
    var exporting by remember(appId, uid) { mutableStateOf(false) }
    fun writeExport(uri: android.net.Uri?, csv: Boolean) {
        val records = pendingExport
        pendingExport = emptyList()
        if (uri == null || account.profile?.uid != pendingUid || records.isEmpty()) {
            exporting = false
            return
        }
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    require(account.profile?.uid == pendingUid)
                    ctx.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                        it.write(QaAnalytics.export(records, csv))
                    } ?: error("Could not open file")
                }
                Toast.makeText(ctx, "Report saved", Toast.LENGTH_SHORT).show()
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { Toast.makeText(ctx, "Export failed", Toast.LENGTH_SHORT).show() }
            finally { exporting = false }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { writeExport(it,true) }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { writeExport(it,false) }

    LaunchedEffect(appId, uid, days, refresh) {
        loading = true
        error = ""
        data = null
        try { data = repository.load(appId, days) }
        catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (exception: Exception) { error = exception.message?.take(180) ?: exception.javaClass.simpleName }
        finally { loading = false }
    }
    val all = data?.runs.orEmpty()
    val cohort = remember(all, includeOwner, uid) { all.filter { includeOwner || it.testerUid != uid } }
    val builds = remember(cohort) { cohort.map(QaAnalytics::build).distinct() }
    val suites = remember(cohort) { cohort.map { it.suite }.filter { it.isNotBlank() }.distinct().sorted() }
    val rows = remember(cohort, build, suite) {
        cohort.filter { (build == "All builds" || QaAnalytics.build(it) == build) &&
            (suite == "All suites" || it.suite == suite) }
    }
    val reportRows = remember(rows, caseFilter, deviceFilter, failureFilter, search) {
        rows.filter { r ->
            (caseFilter == null || QaAnalytics.caseKey(r) == caseFilter) &&
            (deviceFilter == null || QaAnalytics.device(r) == deviceFilter) &&
            (failureFilter == null || QaAnalytics.caseKey(r) == failureFilter?.test &&
                r.steps.any { it.step == failureFilter?.step && it.action == failureFilter?.action && it.status == "FAIL" }) &&
            (search.isBlank() || listOf(r.scenario, r.model, r.build, r.runId, r.suite)
                .any { it.contains(search, ignoreCase = true) })
        }
    }
    val sessions = remember(reportRows, statusFilter) {
        QaAnalytics.sessions(reportRows).filter { statusFilter == "All" || it.status == statusFilter }
    }
    val selected = sessions.firstOrNull { it.key == selectedSession }
    val detail = selectedCase
    fun resetDetails() { selectedSession = null; selectedCase = null; expandedCase = null; reportLimit = 20 }
    fun openReports(key: QaCaseKey? = null, device: String? = null, failure: QaFailureKey? = null) {
        caseFilter = key; deviceFilter = device; failureFilter = failure
        statusFilter = "All"; search = ""; resetDetails(); tab = DashboardTab.Reports
    }
    androidx.activity.compose.BackHandler(enabled = selected != null || detail != null) { resetDetails() }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        SeqTopBar(if (selected != null) "Session report" else if (detail != null) "Case details" else "QA Dashboard") {
            if (selected != null || detail != null) resetDetails() else onBack()
        }
        if (selected == null && detail == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(appId, Modifier.weight(1f), fontSize = 11.sp, color = SeqColors.Muted)
                SeqTextButton(onClick = { refresh++; resetDetails() }, enabled = !loading) { Text("Refresh") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1 to "Today", 7 to "7 days", 30 to "30 days", 0 to "All time").forEach { (value,label) ->
                    FilterChip(selected = days == value, onClick = { days = value; resetDetails(); build = "All builds"; suite = "All suites"; caseFilter = null; deviceFilter = null; failureFilter = null }, label = { Text(label, fontSize = 10.sp) })
                }
            }
            if (!loading && data != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QaMenu(build, listOf("All builds") + builds, Modifier.weight(1f)) { build = it; resetDetails() }
                    QaMenu(suite, listOf("All suites") + suites, Modifier.weight(1f)) { suite = it; resetDetails() }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeOwner, onCheckedChange = { includeOwner = it; build = "All builds"; suite = "All suites"; resetDetails() }, modifier = Modifier.size(36.dp))
                    Text("Include developer's own tests", fontSize = 12.sp, color = SeqColors.Muted)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DashboardTab.entries.forEach { t ->
                        Text(t.name, Modifier.weight(1f).clip(RoundedCornerShape(7.dp))
                            .background(if (tab == t) SeqColors.PrimaryDark else SeqColors.Card)
                            .clickable { tab = t; resetDetails() }.padding(vertical = 10.dp),
                            fontSize = 11.sp, color = SeqColors.Text,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text("Dashboard unavailable: " + error, color = SeqColors.Error, fontSize = 12.sp)
        if (data == null) return@Column
        if (data?.limited == true) Text("Partial data: first 2,000 case reports in this UTC period. Narrow the date range.", color = QaOther, fontSize = 11.sp)
        if (selected != null) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(selected.rows.first().model + " · " + QaAnalytics.build(selected.rows.first()), fontWeight = FontWeight.Bold)
                    Text("Run " + selected.key.second + " · " + formatRunDate(selected.endedAt), fontSize = 12.sp, color = SeqColors.Muted)
                    QaCounts(selected.rows)
                    Text("Matching submitted cases only", color = SeqColors.Muted, fontSize = 11.sp)
                }
                items(selected.rows, key = { it.id }) { run ->
                    QaPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(run.scenario, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text(run.status, color = qaColor(run.status), fontSize = 12.sp)
                        }
                        Text(run.suite + " · v" + run.versions + " · " + qaDuration(run.elapsedSeconds), color = SeqColors.Muted, fontSize = 11.sp)
                        SeqTextButton(onClick = { expandedCase = if (expandedCase == run.id) null else run.id }) {
                            Text(if (expandedCase == run.id) "Hide steps" else "Step results (${run.steps.size})")
                        }
                        if (run.status == "FAIL") QaSharedEvidence(account, run.id)
                        if (expandedCase == run.id) run.steps.forEach { step ->
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("#${step.step} ${step.action}", fontSize = 12.sp)
                                    Text(step.detail + " · " + step.ms + " ms", color = SeqColors.Muted, fontSize = 11.sp)
                                }
                                Text(step.status, color = qaColor(step.status), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        } else if (detail != null) {
            val matching = rows.filter { QaAnalytics.caseKey(it) == detail }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { QaPanel { Text(detail.name, fontWeight = FontWeight.Bold); Text(detail.suite + " · v" + detail.version); QaCounts(matching); Text("Avg. duration " + qaAverage(matching), fontSize = 12.sp); if (QaAnalytics.mixed(matching)) Text("Mixed PASS/FAIL on matching configuration", color = QaOther, fontSize = 12.sp) } }
                item { QaHeading("Problem steps") }
                items(QaAnalytics.failures(matching)) { (key, values) ->
                    QaPanel { Text("#${key.step} ${key.action} · ${values.size} failures", color = SeqColors.Error)
                        values.flatMap { it.steps }.filter { it.step == key.step && it.action == key.action && it.status == "FAIL" }.map { it.detail }.filter { it.isNotBlank() }.distinct().take(3).forEach { Text(it, fontSize = 12.sp, color = SeqColors.Muted) }
                        SeqTextButton(onClick = { openReports(key.test, failure = key) }) { Text("View reports") }
                    }
                }
                item { QaHeading("Device configurations") }
                items(matching.groupBy(QaAnalytics::device).entries.toList()) { (name, values) -> QaPanel { Text(name); QaCounts(values) } }
                item { SeqTextButton(onClick = { openReports(detail) }) { Text("All case reports") } }
            }
        } else when(tab) {
            DashboardTab.Overview -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    val coverage = QaAnalytics.coverage(rows, data?.publishedCases?.filter { suite == "All suites" || it.suite == suite })
                    val active = rows.map { it.testerUid }.filter { it.isNotBlank() && it != uid }.distinct().size
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            QaMetric("Pass rate", qaRate(rows), "${QaAnalytics.pass(rows)}/${QaAnalytics.pass(rows)+QaAnalytics.fail(rows)} verdicts", Modifier.weight(1f))
                            QaMetric("Test sessions", QaAnalytics.sessions(rows).size.toString(), "${rows.size} case executions", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            QaMetric("Reporting QA", "$active / " + (data?.joined?.toString() ?: "—"), "QA in filters / joined total", Modifier.weight(1f))
                            QaMetric("Case coverage", coverage?.let { "${it.first} / ${it.second}" } ?: "—", "Current published versions", Modifier.weight(1f))
                        }
                        QaPanel { QaHeading("Execution results"); QaCounts(rows)
                            Text("${rows.map(QaAnalytics::device).distinct().size} configurations · Avg. " + qaAverage(rows), color = SeqColors.Muted, fontSize = 12.sp)
                            Text("PASS rate excludes incomplete cases. Results require QA consent. UTC dates.", fontSize = 11.sp, color = SeqColors.Muted)
                        }
                    }
                }
                item { QaHeading("Daily activity (UTC)") }
                items(QaAnalytics.days(rows).entries.toList().takeLast(30)) { (day, values) ->
                    QaPanel { Row { Text(day, Modifier.weight(1f), fontSize = 12.sp); Text("${values.size} cases", fontSize = 12.sp) }; QaBar(values); QaCounts(values, false) }
                }
                item { QaHeading("Top failed steps") }
                val failures = QaAnalytics.failures(rows).take(5)
                if (failures.isEmpty()) item { Text("No failed steps reported", color = SeqColors.Muted, fontSize = 12.sp) }
                items(failures) { (key, values) ->
                    QaPanel { Text(key.test.name, fontWeight = FontWeight.Medium)
                        Text("#${key.step} ${key.action} · ${values.size} failures", color = SeqColors.Error, fontSize = 12.sp)
                        Text("${values.map(QaAnalytics::device).distinct().size} configurations", fontSize = 11.sp, color = SeqColors.Muted)
                        SeqTextButton(onClick = { openReports(key.test, failure = key) }) { Text("View evidence") }
                    }
                }
                item { QaHeading("Build comparison"); Text("All builds in selected period and suite", fontSize = 11.sp, color = SeqColors.Muted) }
                items(cohort.filter { suite == "All suites" || it.suite == suite }.groupBy(QaAnalytics::build).entries.sortedByDescending { it.value.maxOf { r -> r.endedAt } }.take(10)) { (name,values) ->
                    QaPanel { Row { Text(name, Modifier.weight(1f)); Text(qaRate(values), color = SeqColors.Teal) }; QaCounts(values) }
                }
            }
            DashboardTab.Cases -> {
                val groups = rows.groupBy(QaAnalytics::caseKey)
                val keys = (groups.keys + data?.publishedCases.orEmpty().filter { suite == "All suites" || it.suite == suite })
                    .distinct().sortedWith(compareByDescending<QaCaseKey> { QaAnalytics.fail(groups[it].orEmpty()) }.thenBy { it.name })
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("Grouped by suite, case and version · ${keys.size} cases", fontSize = 12.sp, color = SeqColors.Muted) }
                    items(keys) { key ->
                        val values = groups[key].orEmpty()
                        QaPanel {
                            Row { Text(key.name.ifBlank { "Unnamed case" }, Modifier.weight(1f), fontWeight = FontWeight.Medium); Text(qaRate(values), color = SeqColors.Teal) }
                            Text(key.suite + " · v" + key.version + " · Avg. " + qaAverage(values), color = SeqColors.Muted, fontSize = 12.sp)
                            if (values.isEmpty()) Text("Not executed in filters", color = QaOther, fontSize = 12.sp) else QaCounts(values)
                            if (QaAnalytics.mixed(values)) Text("Mixed verdicts · possible flaky test", fontSize = 11.sp, color = QaOther)
                            SeqTextButton(onClick = { selectedCase = key }) { Text("Details") }
                        }
                    }
                }
            }
            DashboardTab.Devices -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Device model + Android API; not physical device count.", color = SeqColors.Muted, fontSize = 12.sp) }
                items(rows.groupBy(QaAnalytics::device).entries.sortedByDescending { QaAnalytics.fail(it.value) }) { (name, values) ->
                    QaPanel {
                        Row { Text(name, Modifier.weight(1f), fontWeight = FontWeight.Medium); Text(qaRate(values), color = SeqColors.Teal) }
                        QaCounts(values)
                        Text("${QaAnalytics.sessions(values).size} sessions · ${values.map { it.testerUid }.filter { it.isNotBlank() }.distinct().size} reporting accounts", fontSize = 11.sp, color = SeqColors.Muted)
                        SeqTextButton(onClick = { openReports(device = name) }) { Text("Reports") }
                    }
                }
                item { QaHeading("Android versions") }
                items(rows.groupBy { it.androidApi }.entries.sortedBy { it.key }) { (api,values) -> QaPanel { Text("Android API $api"); QaCounts(values) } }
            }
            DashboardTab.Reports -> {
                if (caseFilter != null || deviceFilter != null || failureFilter != null) {
                    Text(listOfNotNull(caseFilter?.name, deviceFilter, failureFilter?.let { "Failed step #${it.step}" }).joinToString(" · "), fontSize = 12.sp, color = SeqColors.Primary)
                    SeqTextButton(onClick = { caseFilter = null; deviceFilter = null; failureFilter = null; resetDetails() }) { Text("Clear detail filters") }
                }
                OutlinedTextField(value = search, onValueChange = { search = it; reportLimit = 20 }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search case, build, device, run ID", fontSize = 12.sp) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QaMenu(statusFilter, listOf("All","PASS","FAIL","INCOMPLETE"), Modifier.weight(1f)) { statusFilter = it; reportLimit = 20 }
                    Text("${sessions.size} sessions", fontSize = 12.sp, color = SeqColors.Muted)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeqTextButton(onClick = { pendingExport = sessions.flatMap { it.rows }; pendingUid = uid; exporting = true; csvLauncher.launch("sequmio-qa.csv") }, enabled = sessions.isNotEmpty() && !exporting, modifier = Modifier.weight(1f)) { Text("Export CSV") }
                    SeqTextButton(onClick = { pendingExport = sessions.flatMap { it.rows }; pendingUid = uid; exporting = true; jsonLauncher.launch("sequmio-qa.json") }, enabled = sessions.isNotEmpty() && !exporting, modifier = Modifier.weight(1f)) { Text("Export JSON") }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sessions.isEmpty()) item { Text("No matching submitted reports", color = SeqColors.Muted) }
                    items(sessions.take(reportLimit)) { session ->
                        QaPanel {
                            Row { Text("Run " + session.key.second.takeLast(10), Modifier.weight(1f), fontWeight = FontWeight.Medium); Text(session.status, color = qaColor(session.status), fontSize = 12.sp) }
                            Text(formatRunDate(session.endedAt) + " · " + QaAnalytics.device(session.rows.first()), fontSize = 12.sp, color = SeqColors.Muted)
                            QaCounts(session.rows, false)
                            SeqTextButton(onClick = { selectedSession = session.key }) { Text("Open report") }
                        }
                    }
                    if (sessions.size > reportLimit) item { SeqTextButton(onClick = { reportLimit += 20 }) { Text("Show more (${sessions.size - reportLimit})") } }
                }
            }
        }
    }
}

@Composable private fun QaPanel(content: @Composable ColumnScope.() -> Unit) {
    SeqCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}
@Composable private fun QaHeading(title: String) = Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
@Composable private fun QaMetric(label: String, value: String, sub: String, modifier: Modifier) {
    SeqCard(modifier) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = SeqColors.Muted, fontSize = 11.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(sub, color = SeqColors.Muted, fontSize = 10.sp)
    } }
}
@Composable private fun QaMenu(label: String, values: List<String>, modifier: Modifier, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 6.dp)) {
            Text(label + " ▾", fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            values.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { open = false; onSelect(value) }) }
        }
    }
}
@Composable private fun QaBar(rows: List<QaRun>) {
    val pass = QaAnalytics.pass(rows); val fail = QaAnalytics.fail(rows); val other = rows.size-pass-fail
    Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)).background(SeqColors.Border)) {
        if (pass > 0) Box(Modifier.weight(pass.toFloat()).fillMaxHeight().background(SeqColors.Success))
        if (fail > 0) Box(Modifier.weight(fail.toFloat()).fillMaxHeight().background(SeqColors.Error))
        if (other > 0) Box(Modifier.weight(other.toFloat()).fillMaxHeight().background(QaOther))
    }
}
@Composable private fun QaCounts(rows: List<QaRun>, bar: Boolean = true) {
    if (bar) QaBar(rows)
    val pass = QaAnalytics.pass(rows); val fail = QaAnalytics.fail(rows)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$pass PASS", color = SeqColors.Success, fontSize = 11.sp)
        Text("$fail FAIL", color = SeqColors.Error, fontSize = 11.sp)
        Text("${rows.size-pass-fail} other", color = QaOther, fontSize = 11.sp)
    }
}
private fun qaRate(rows: List<QaRun>) = QaAnalytics.rate(rows)?.let { String.format(Locale.ROOT,"%.1f%%",it) } ?: "—"
private fun qaDuration(seconds: Long) = if (seconds < 0) "—" else "${seconds}s"
private fun qaAverage(rows: List<QaRun>) = rows.map { it.elapsedSeconds }.filter { it >= 0 }.takeIf { it.isNotEmpty() }?.average()?.let { String.format(Locale.ROOT,"%.1fs",it) } ?: "—"
private fun qaColor(status: String) = when(status) { "PASS" -> SeqColors.Success; "FAIL" -> SeqColors.Error; else -> QaOther }
private fun formatRunDate(time: Long): String = if (time <= 0) "Unknown date" else SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(Date(time)) + " UTC"
