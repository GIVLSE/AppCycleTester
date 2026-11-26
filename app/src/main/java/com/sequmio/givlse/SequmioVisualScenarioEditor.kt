package com.sequmio.givlse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex
import com.sequmio.givlse.data.StepReorder
import com.sequmio.givlse.data.StepBounds
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sequmio.givlse.data.MyAppDraft
import com.sequmio.givlse.data.ScenarioCodec
import com.sequmio.givlse.data.SmartScenarioSteps as StepUi
import com.sequmio.givlse.runner.PickedElement
import org.json.JSONObject

@Composable
private fun SmartField(label: String, value: String, numeric: Boolean = false,
    multiline: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = !multiline,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text))
}

private fun msToSeconds(ms: Long): String = if (ms % 1000L == 0L) (ms / 1000L).toString()
    else (ms / 1000.0).toString()
private fun secondsToMs(text: String): Long = text.replace(',', '.').toDoubleOrNull()
    ?.takeIf { it.isFinite() && it in 0.0..30.0 }?.let { (it * 1000).toLong() } ?: -1L

@Composable
internal fun SequmioVisualScenarioEditor(
    initial: MyAppDraft,
    onSave: (MyAppDraft) -> Unit,
    onBack: () -> Unit,
    onPick: ((PickedElement?) -> Unit) -> String?,
    onCancelPick: () -> Unit,
    onRun: (MyAppDraft) -> String?
) {
    val parsed = remember(initial.scenarioJson) {
        runCatching { JSONObject(initial.scenarioJson) }.getOrElse { JSONObject() }
    }
    val originalMeta = parsed.optJSONObject("meta") ?: JSONObject()
    val startingSuite = originalMeta.optString("suite", "SMOKE")
    var name by rememberSaveable(initial.scenarioJson) { mutableStateOf(originalMeta.optString("name", "UI smoke test")) }
    var suite by rememberSaveable(initial.scenarioJson) { mutableStateOf(startingSuite) }
    var duration by rememberSaveable(initial.scenarioJson) {
        mutableStateOf(parsed.optJSONObject("execution")?.optInt("total_duration_sec", 300)?.toString() ?: "300")
    }
    var steps by rememberSaveable(initial.scenarioJson) {
        mutableStateOf(ArrayList(runCatching { StepUi.rows(parsed) }.getOrDefault(emptyList())))
    }
    var editing by rememberSaveable { mutableIntStateOf(-1) }
    var addKind by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var showJson by rememberSaveable { mutableStateOf(false) }
    var rawJson by rememberSaveable(initial.scenarioJson) { mutableStateOf(initial.scenarioJson) }
    var baseJson by rememberSaveable(initial.scenarioJson) { mutableStateOf(parsed.toString()) }
    var pickPending by remember { mutableStateOf(false) }
    val currentCancelPick by rememberUpdatedState(onCancelPick)
    var leaveConfirm by remember { mutableStateOf(false) }
    var advancedCase by remember { mutableStateOf(false) }
    var loop by rememberSaveable(initial.scenarioJson) {
        mutableStateOf(parsed.optJSONObject("execution")?.optBoolean("loop_steps", false) ?: false)
    }
    DisposableEffect(Unit) { onDispose { currentCancelPick() } }

    fun change(i: Int, block: (JSONObject) -> Unit) {
        if (i !in steps.indices) return
        steps = ArrayList(steps).apply { this[i] = JSONObject(this[i]).apply(block).toString() }
        validation = ""
    }
    fun move(from: Int, to: Int) {
        if (from !in steps.indices || to !in steps.indices || from == to) return
        steps = ArrayList(steps).apply { add(to, removeAt(from)) }
        editing = -1
    }
    fun snapshot(): JSONObject {
        if (showJson) return JSONObject(rawJson)
        val root = JSONObject(baseJson)
        root.put("schema_version", 1)
        val meta = root.optJSONObject("meta") ?: JSONObject()
        meta.put("name", name.trim()).put("suite", suite)
        root.put("meta", meta)
        val target = root.optJSONObject("target") ?: JSONObject()
        root.put("target", target.put("package_name", initial.packageName))
        val execution = root.optJSONObject("execution") ?: JSONObject()
        root.put("execution", execution.put("total_duration_sec", duration.toIntOrNull() ?: 0)
            .put("loop_steps", loop))
        StepUi.writeRows(root, steps)
        return root
    }
    fun finalDraft(): MyAppDraft {
        val updated = snapshot()
        val meta = updated.optJSONObject("meta") ?: JSONObject()
        val oldVersion = originalMeta.optInt("version", 1)
        val changed = updated.toString() != parsed.toString()
        meta.put("version", if (changed) oldVersion + 1 else oldVersion)
        val chosenSuite = meta.optString("suite", "SMOKE")
        if (startingSuite == "REGRESSION" && changed && chosenSuite == "REGRESSION")
            meta.put("suite", "FEATURE").put("regression_approved", false)
        else if (chosenSuite != "REGRESSION") meta.put("regression_approved", false)
        updated.put("meta", meta)
        val raw = updated.toString()
        ScenarioCodec.parse(raw, initial.packageName)
        return initial.copy(scenarioJson = raw)
    }
    fun requestBack() {
        if (runCatching { snapshot().toString() != parsed.toString() }.getOrDefault(true))
            leaveConfirm = true else onBack()
    }
    BackHandler { if (pickPending) { onCancelPick(); pickPending = false } else requestBack() }
    val rowObjects = steps.map { JSONObject(it) }
    val checks = rowObjects.count { it.optString("action") in StepUi.checks }
    val issues = rowObjects.count { StepUi.warning(it) != null } + if (checks == 0) 1 else 0
    val scroll = rememberScrollState()
    val bounds = remember(steps.size, showJson, pickPending) { mutableStateMapOf<Int, StepBounds>() }
    var dragging by remember { mutableIntStateOf(-1) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var dragStartScroll by remember { mutableIntStateOf(0) }
    var viewportTop by remember { mutableFloatStateOf(0f) }
    var viewportBottom by remember { mutableFloatStateOf(0f) }
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val dragBounds = bounds[dragging]
    val displacement = if (dragging >= 0) dragDelta + scroll.value - dragStartScroll else 0f
    val destination = if (dragBounds != null)
        StepReorder.destination(dragging, dragBounds.center + displacement, bounds)
        else dragging
    val gap = with(LocalDensity.current) { 12.dp.toPx() }
    fun endDrag(commit: Boolean) {
        val from = dragging
        val to = if (from >= 0) bounds[from]?.let {
            StepReorder.destination(from, it.center + dragDelta + scroll.value - dragStartScroll, bounds)
        } ?: from else from
        dragging = -1
        dragDelta = 0f
        if (commit) move(from, to)
    }
    LaunchedEffect(dragging) {
        if (dragging >= 0) {
            while (dragging >= 0) {
                val item = bounds[dragging] ?: break
                val screenY = item.center + dragDelta - dragStartScroll
                val speed = when {
                    screenY < viewportTop + edge && dragging > 0 ->
                        -((viewportTop + edge - screenY) / edge).coerceIn(0f, 1f) * 18f
                    screenY > viewportBottom - edge && dragging < steps.lastIndex ->
                        ((screenY - (viewportBottom - edge)) / edge).coerceIn(0f, 1f) * 18f
                    else -> 0f
                }
                if (speed != 0f) scroll.scrollBy(speed)
                delay(16)
            }
        }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned {
        viewportTop = it.positionInRoot().y
        viewportBottom = viewportTop + it.size.height
    }) {
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SeqTopBar("Scenario Editor", {
            if (pickPending) { onCancelPick(); pickPending = false } else requestBack()
        })
        if (pickPending) {
            Text("Select an element in the target app, or cancel to return.", color = SeqColors.Teal)
            TextButton(onClick = { onCancelPick(); pickPending = false }) { Text("Cancel selection") }
        } else {
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(initial.appName.ifBlank { initial.packageName }, fontWeight = FontWeight.Bold)
                SmartField("Test case name", name) { name = it }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf("SMOKE", "FEATURE") + if (startingSuite == "REGRESSION") listOf("REGRESSION") else emptyList())
                        .forEach { option ->
                            FilterChip(selected = suite == option, onClick = { suite = option },
                                label = { Text(option, fontSize = 11.sp) })
                        }
                }
                Text("${steps.size} steps · $checks checks · " + if (issues == 0) "Ready to validate" else "$issues to review",
                    color = if (issues == 0) SeqColors.Teal else SeqColors.Error, fontSize = 12.sp)
                if (startingSuite == "REGRESSION") Text("Editing an approved test requires a new local PASS before Regression approval.",
                    fontSize = 12.sp, color = SeqColors.Muted)
                TextButton(onClick = { advancedCase = !advancedCase }) {
                    Text(if (advancedCase) "Hide test settings" else "Test settings")
                }
                if (advancedCase) {
                    SmartField("Estimated duration (seconds)", duration, numeric = true) { duration = it }
                    val seconds = duration.toIntOrNull()
                    Text("Automatic timeout: " + if (seconds != null && seconds in 5..7200)
                        targetTimeLabel(com.sequmio.givlse.model.ExecutionPolicy.timeout(seconds, 0)) else "Not set",
                        color = SeqColors.Muted, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = loop, onCheckedChange = { loop = it })
                        Text("Legacy loop setting")
                    }
                    TextButton(onClick = {
                        if (showJson) {
                            runCatching {
                                val root = JSONObject(rawJson)
                                require(root.getJSONObject("target").getString("package_name") == initial.packageName)
                                steps = ArrayList(StepUi.rows(root))
                                baseJson = root.toString()
                                name = root.optJSONObject("meta")?.optString("name", name) ?: name
                                suite = root.optJSONObject("meta")?.optString("suite", suite) ?: suite
                                duration = root.optJSONObject("execution")?.optInt("total_duration_sec", 300)?.toString() ?: "300"
                                loop = root.optJSONObject("execution")?.optBoolean("loop_steps", false) ?: false
                                showJson = false
                            }.onFailure { validation = "Invalid JSON: " + it.message }
                        } else {
                            rawJson = snapshot().toString(2)
                            showJson = true
                        }
                    }) { Text(if (showJson) "Visual editor" else "Advanced JSON") }
                }
            }
        }
        if (showJson) {
            SmartField("Advanced scenario JSON", rawJson, multiline = true) { rawJson = it }
        } else {
            if (checks == 0) Text("Add a verification to tell Sequmio what should happen.", color = SeqColors.Error, fontSize = 13.sp)
            rowObjects.forEachIndexed { i, step ->
                val action = step.optString("action")
                val isCheck = action in StepUi.checks
                val tint = if (isCheck) SeqColors.Teal else SeqColors.Primary
                val params = step.optJSONObject("params") ?: JSONObject()
                val element = StepUi.elementLabel(step)
                val warning = StepUi.warning(step)
                val selected = i == dragging
                val neighborOffset = StepReorder.neighborOffset(i, dragging, destination,
                    (dragBounds?.height ?: 0f) + gap)
                val animatedOffset by animateFloatAsState(neighborOffset, spring(), label = "step shift")
                val displayIndex = StepReorder.displayIndex(i, dragging, destination)
                Box(Modifier.fillMaxWidth().zIndex(if (selected) 2f else 0f)
                    .onGloballyPositioned {
                        val next = StepBounds(it.positionInRoot().y + scroll.value, it.size.height.toFloat())
                        if (bounds[i] != next) bounds[i] = next
                    }) {
                if (selected) {
                    Box(Modifier.matchParentSize().border(1.dp, SeqColors.Border, RoundedCornerShape(16.dp)))
                }
                SeqCard(Modifier.fillMaxWidth().graphicsLayer {
                    translationY = if (selected) displacement else animatedOffset
                    shadowElevation = if (selected) 12.dp.toPx() else 0f
                }.then(if (selected) Modifier.border(2.dp, SeqColors.Teal, RoundedCornerShape(16.dp)) else Modifier)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${displayIndex + 1}", color = if (selected) SeqColors.Teal else SeqColors.Muted, modifier = Modifier.padding(end = 9.dp))
                            Icon(if (isCheck) Icons.Outlined.CheckCircle else Icons.Outlined.PlayArrow,
                                null, tint = tint, modifier = Modifier.size(23.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
                                Text(if (isCheck) "Verification" else "Action", color = tint, fontSize = 10.sp)
                                Text(StepUi.title(action), fontWeight = FontWeight.Bold)
                                val detail = when (action) {
                                    "WAIT" -> msToSeconds(params.optLong("duration_ms", 1000)) + " seconds"
                                    "SWIPE" -> StepUi.direction(params)
                                    "ASSERT_TEXT" -> element + " = " + params.optString("expected")
                                    else -> element.ifBlank {
                                        if (action == "CLICK" && step.optJSONObject("fallback_coords") != null) "Screen position" else ""
                                    }
                                }
                                if (detail.isNotBlank()) Text(detail.take(100), color = SeqColors.Muted, fontSize = 12.sp)
                                if (isCheck) Text("Wait up to " + msToSeconds(ScenarioCodec.assertionTimeoutMs(params)) + " s",
                                    color = SeqColors.Muted, fontSize = 11.sp)
                                if (warning != null) Text(warning, color = SeqColors.Error, fontSize = 12.sp)
                            }
                            IconButton(onClick = { editing = i; showAdvanced = false }, enabled = dragging < 0) {
                                Icon(Icons.Outlined.Edit, "Edit step")
                            }
                            Icon(Icons.Outlined.DragHandle, "Hold and drag to reorder",
                                tint = if (selected) SeqColors.Teal else SeqColors.Muted,
                                modifier = Modifier.size(36.dp).pointerInput(i, steps.size) {
                                    detectDragGesturesAfterLongPress(onDragStart = {
                                        if (bounds.size == steps.size) {
                                            dragStartScroll = scroll.value
                                            dragDelta = 0f
                                            dragging = i
                                        }
                                    }, onDragEnd = { if (dragging == i) endDrag(true) },
                                        onDragCancel = { if (dragging == i) endDrag(false) }) { change, amount ->
                                        if (dragging == i) { change.consume(); dragDelta += amount.y }
                                    }
                                })
                        }
                     }
                }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeqGlassButton("+ Action", SeqColors.Primary, onClick = { addKind = "action" },
                    enabled = steps.size < 50, modifier = Modifier.weight(1f))
                SeqGlassButton("+ Verification", SeqColors.Teal, onClick = { addKind = "check" },
                    enabled = steps.size < 50, modifier = Modifier.weight(1f))
            }
        }
        if (validation.isNotBlank()) Text(validation,
            color = if (validation == "Valid scenario") SeqColors.Success else SeqColors.Error)
        SeqPrimaryButton("Run Test Case", onClick = {
            validation = runCatching { onRun(finalDraft()).orEmpty() }.getOrElse { it.message ?: "Unable to run" }
        }, modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.PlayArrow, enabled = !pickPending)
        SeqGlassButton("Save Test Case", SeqColors.Teal, onClick = {
            validation = runCatching { onSave(finalDraft()); "Saved" }.getOrElse { it.message ?: "Fix scenario errors" }
        }, modifier = Modifier.fillMaxWidth(), enabled = !pickPending)
        TextButton(onClick = {
            validation = runCatching { finalDraft(); "Valid scenario" }.getOrElse { it.message ?: "Invalid scenario" }
        }) { Text("Validate") }
        Text("Changes stay on this device until you update the published listing.",
            color = SeqColors.Muted, fontSize = 12.sp)
        }
    }
    }
    if (addKind.isNotBlank()) AlertDialog(onDismissRequest = { addKind = "" },
        title = { Text(if (addKind == "check") "Add Verification" else "Add Action") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (if (addKind == "check") StepUi.checks else StepUi.actions).forEach { action ->
                    TextButton(onClick = {
                        if (steps.size < 50) {
                            steps = ArrayList(steps).apply { add(StepUi.newStep(action).toString()) }
                            editing = steps.lastIndex; showAdvanced = false
                        }
                        addKind = ""
                    }) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(StepUi.title(action), fontWeight = FontWeight.Bold)
                            Text(StepUi.explanation(action), color = SeqColors.Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { addKind = "" }) { Text("Cancel") } })
    if (editing in steps.indices && !showJson && !pickPending) {
        val index = editing
        val step = JSONObject(steps[index])
        val action = step.optString("action")
        val selector = step.optJSONObject("selector") ?: JSONObject()
        val params = step.optJSONObject("params") ?: JSONObject()
        val isCheck = action in StepUi.checks
        fun updateParam(key: String, value: Any) = change(index) {
            val p = it.optJSONObject("params") ?: JSONObject()
            it.put("params", p.put(key, value))
        }
        fun updateSelector(key: String, value: String) = change(index) {
            val s = it.optJSONObject("selector") ?: JSONObject()
            if (value.isBlank()) s.remove(key) else s.put(key, value)
            it.put("selector", s)
            it.remove("label")
        }
        fun capture() {
            pickPending = true
            val error = onPick { element ->
                pickPending = false
                if (element != null && index in steps.indices) {
                    if (action != "CLICK" && !element.hasSelector()) validation = "Choose an element with a unique accessible identifier."
                    else if (action == "INPUT_TEXT" && !element.editable) validation = "Choose an editable text field."
                    else change(index) { obj ->
                        val next = StepUi.replaceElement(obj, element.selector(), element.label,
                            element.x, element.y, element.expectedText)
                        val keys = obj.keys().asSequence().toList()
                        keys.forEach { obj.remove(it) }
                        next.keys().forEach { k -> obj.put(k, next.get(k)) }
                    }
                }
            }
            if (error != null) { pickPending = false; validation = error }
        }
        AlertDialog(onDismissRequest = { if (!pickPending) editing = -1 },
            title = { Text("Step ${index + 1} · " + StepUi.title(action)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(StepUi.explanation(action), color = SeqColors.Muted, fontSize = 13.sp)
                    if (StepUi.requiresElement(action)) {
                        Text("Selected element", fontWeight = FontWeight.SemiBold)
                        Text(StepUi.elementLabel(step).ifBlank {
                            if (action == "CLICK" && step.optJSONObject("fallback_coords") != null) "Screen position" else "Not selected"
                        })
                        SeqGlassButton(if (pickPending) "Selecting…" else "Choose on screen", SeqColors.Teal,
                            onClick = { capture() }, enabled = !pickPending, modifier = Modifier.fillMaxWidth())
                        if (action in StepUi.checks) {
                            val prior = rowObjects.take(index).filter { StepUi.hasSelector(it) }.distinctBy {
                                it.optJSONObject("selector").toString()
                            }
                            if (prior.isNotEmpty()) {
                                var previousOpen by remember { mutableStateOf(false) }
                                TextButton(onClick = { previousOpen = !previousOpen }) { Text("Use element from earlier step") }
                                if (previousOpen) prior.forEach { old ->
                                    TextButton(onClick = {
                                        change(index) {
                                            it.put("selector", JSONObject(old.getJSONObject("selector").toString()))
                                            it.put("label", StepUi.elementLabel(old))
                                        }
                                        previousOpen = false
                                    }) { Text(StepUi.elementLabel(old).ifBlank { "Recorded element" }) }
                                }
                            }
                        }
                    }
                    if (action == "ASSERT_TEXT") SmartField("Expected text", params.optString("expected")) {
                        updateParam("expected", it)
                    }
                    if (action == "INPUT_TEXT") {
                        Text("Use non-sensitive test data, never real credentials.", color = SeqColors.Muted, fontSize = 12.sp)
                        SmartField("Test value", params.optString("text").takeUnless { it == "REPLACE_WITH_TEST_TEXT" }.orEmpty()) {
                            updateParam("text", it)
                        }
                    }
                    if (isCheck || action == "WAIT") {
                        val field = if (isCheck) "timeout_ms" else "duration_ms"
                        var seconds by remember(index, action) {
                            mutableStateOf(msToSeconds(params.optLong(field, if (isCheck) 5000 else 1000)))
                        }
                        SmartField(if (isCheck) "Wait up to (seconds)" else "Wait (seconds)", seconds, numeric = true) {
                            seconds = it; updateParam(field, secondsToMs(it))
                        }
                    }
                    if (action == "SWIPE") {
                        Text("Direction", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            listOf("Up", "Down", "Left", "Right").forEach { dir ->
                                FilterChip(selected = StepUi.direction(params) == dir, onClick = {
                                    change(index) { obj ->
                                        val p = obj.optJSONObject("params") ?: JSONObject()
                                        val next = StepUi.swipe(dir)
                                        next.keys().forEach { k -> if (k != "duration_ms" || !p.has(k)) p.put(k, next.get(k)) }
                                        obj.put("params", p)
                                    }
                                }, label = { Text(dir, fontSize = 10.sp) })
                            }
                        }
                    }
                    TextButton(onClick = { showAdvanced = !showAdvanced }) {
                        Text(if (showAdvanced) "Hide advanced settings" else "Advanced settings")
                    }
                    if (showAdvanced) {
                        if (StepUi.requiresElement(action)) {
                            SmartField("View ID", selector.optString("view_id")) { updateSelector("view_id", it) }
                            SmartField("Visible text", selector.optString("text")) { updateSelector("text", it) }
                            SmartField("Content description", selector.optString("content_desc")) { updateSelector("content_desc", it) }
                            Text("View ID is an internal identifier. Text and description are accessibility labels.",
                                fontSize = 11.sp, color = SeqColors.Muted)
                        }
                        if (action == "CLICK") {
                            val f = step.optJSONObject("fallback_coords") ?: JSONObject()
                            listOf("x_percent", "y_percent").forEach { field ->
                                SmartField(if (field == "x_percent") "Fallback X (0–1)" else "Fallback Y (0–1)",
                                    if (f.has(field)) f.optString(field) else "", numeric = true) { raw ->
                                    change(index) {
                                        val coords = it.optJSONObject("fallback_coords") ?: JSONObject()
                                        val value = raw.replace(',', '.').toDoubleOrNull()
                                        if (raw.isBlank()) coords.remove(field)
                                        else coords.put(field, value?.takeIf { n -> n.isFinite() } ?: -1.0)
                                        if (coords.length() == 0) it.remove("fallback_coords") else it.put("fallback_coords", coords)
                                    }
                                }
                            }
                        }
                        if (action == "SWIPE") listOf("start_x_percent", "start_y_percent", "end_x_percent", "end_y_percent", "duration_ms").forEach { field ->
                            SmartField(field, params.optString(field), numeric = true) { raw ->
                                updateParam(field, raw.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() } ?: -1.0)
                            }
                        }
                        var delay by remember(index) { mutableStateOf(msToSeconds(step.optLong("delay_before_ms", 0))) }
                        SmartField("Delay before step (seconds)", delay, numeric = true) { raw ->
                            delay = raw; change(index) { it.put("delay_before_ms", secondsToMs(raw)) }
                        }
                        if (action == "ASSERT_TEXT") Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = params.optBoolean("ignore_case"), onCheckedChange = { updateParam("ignore_case", it) })
                            Text("Ignore letter case")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = step.optBoolean("optional"), onCheckedChange = { checked ->
                                change(index) { it.put("optional", checked) }
                            })
                            Text("Optional step")
                        }
                    }
                    StepUi.warning(step)?.let { Text(it, color = SeqColors.Error, fontSize = 12.sp) }
                    if (validation.isNotBlank()) Text(validation, color = SeqColors.Error, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { move(index, index - 1) }, enabled = index > 0 && !pickPending) { Text("Move up") }
                        TextButton(onClick = { move(index, index + 1) }, enabled = index < steps.lastIndex && !pickPending) { Text("Move down") }
                        TextButton(onClick = {
                            steps = ArrayList(steps).apply { removeAt(index) }
                            editing = -1
                        }, enabled = !pickPending) { Text("Delete", color = SeqColors.Error) }
                    }
                }
            }, confirmButton = { TextButton(onClick = { editing = -1 }, enabled = !pickPending) { Text("Done") } })
    }
    if (leaveConfirm) AlertDialog(onDismissRequest = { leaveConfirm = false },
        title = { Text("Discard unsaved changes?") },
        text = { Text("Saved test cases are not affected.") },
        confirmButton = { TextButton(onClick = onBack) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { leaveConfirm = false }) { Text("Keep editing") } })
}
