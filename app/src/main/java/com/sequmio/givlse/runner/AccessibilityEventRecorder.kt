package com.sequmio.givlse.runner

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.sequmio.givlse.model.InstalledApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class RecorderState(
    val recording: Boolean = false,
    val paused: Boolean = false,
    val stepCount: Int = 0,
    val message: String = ""
)

object RecorderRuntime {
    private val stateFlow = MutableStateFlow(RecorderState())
    val state: StateFlow<RecorderState> = stateFlow
    fun set(state: RecorderState) { stateFlow.value = state }
}

/**
 * Explicit opt-in UI event recorder using Accessibility overlay.
 * Captures selector/geometry information, not passwords or raw touch streams.
 * Text entry is intentionally saved as a placeholder to prevent accidental
 * collection of typed sensitive values. User must edit before publishing.
 */
class AccessibilityEventRecorder(
    private val service: AccessibilityService,
    private val app: InstalledApp,
    private val onStopped: (String?) -> String
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val steps = JSONArray()
    private var overlay: LinearLayout? = null
    private var paused = false
    private var stopped = false
    private var lastEventAt = 0L
    private var startedAt = 0L
    private var pausedAt = 0L
    private var totalPausedMs = 0L
    private var lastTextResource = ""
    private var labelView: TextView? = null
    private var pauseButton: Button? = null

    fun start(): String? = try {
        showOverlay()
        startedAt = SystemClock.uptimeMillis()
        RecorderRuntime.set(RecorderState(recording = true, message = "Recording " + app.label))
        val intent = Intent.makeMainActivity(ComponentName(app.packageName, app.activityClass))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        service.startActivity(intent)
        null
    } catch (error: Exception) {
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
        RecorderRuntime.set(RecorderState(message = "Recorder start failed"))
        error.javaClass.simpleName
    }

    private fun showOverlay() {
        val root = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(14, 8, 14, 8)
            setBackgroundColor(Color.rgb(22, 35, 55))
            gravity = Gravity.CENTER_VERTICAL
        }
        val text = TextView(service).apply {
            this.text = "REC ● 0"
            textSize = 13f
            setTextColor(Color.WHITE)
        }
        labelView = text
        val pause = Button(service).apply {
            this.text = "Pause"
            setOnClickListener { togglePause() }
        }
        pauseButton = pause
        val stop = Button(service).apply {
            this.text = "Stop"
            setOnClickListener { stop() }
        }
        root.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(pause)
        root.addView(stop)
        val args = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 55
        }
        wm.addView(root, args)
        overlay = root
    }

    private fun togglePause() {
        val now = SystemClock.uptimeMillis()
        if (paused) {
            totalPausedMs += now - pausedAt
            if (lastEventAt != 0L) lastEventAt += now - pausedAt
        } else pausedAt = now
        paused = !paused
        pauseButton?.text = if (paused) "Resume" else "Pause"
        RecorderRuntime.set(RecorderState(recording = true, paused = paused,
            stepCount = steps.length(), message = if (paused) "Recorder paused" else "Recording"))
    }

    fun onEvent(event: AccessibilityEvent) {
        if (stopped || paused || event.packageName?.toString() != app.packageName) return
        if (steps.length() >= 50) { stop(); return }
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            type != AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            type != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        val now = SystemClock.uptimeMillis()
        val debounce = if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) 700 else 200
        if (now - lastEventAt < debounce) return
        val source = event.source ?: return
        if (source.isPassword) return
        val step = JSONObject()
        val selector = JSONObject()
        val viewId = source.viewIdResourceName.orEmpty()
        val description = source.contentDescription?.toString().orEmpty()
        if (viewId.isNotBlank()) selector.put("view_id", viewId)
        else if (description.isNotBlank() && description.length <= 100)
            selector.put("content_desc", description)
        val bounds = Rect()
        source.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return
        val metrics = service.resources.displayMetrics
        val x = if (metrics.widthPixels > 0)
            (bounds.centerX().toDouble() / metrics.widthPixels).coerceIn(0.0, 1.0)
            else 0.5
        val y = if (metrics.heightPixels > 0)
            (bounds.centerY().toDouble() / metrics.heightPixels).coerceIn(0.0, 1.0)
            else 0.5

        when (type) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                step.put("action", "CLICK")
                    .put("selector", selector)
                    .put("fallback_coords", JSONObject()
                        .put("x_percent", x).put("y_percent", y))
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val down = Build.VERSION.SDK_INT < 28 || event.scrollDeltaY >= 0
                step.put("action", "SWIPE")
                    .put("params", JSONObject()
                        .put("start_x_percent", x)
                        .put("start_y_percent", if (down) 0.75 else 0.25)
                        .put("end_x_percent", x)
                        .put("end_y_percent", if (down) 0.25 else 0.75)
                        .put("duration_ms", 450))
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                // Never capture actual typed characters or password values.
                if (viewId.isBlank() && description.isBlank()) return
                if (viewId == lastTextResource) return
                lastTextResource = viewId
                step.put("action", "INPUT_TEXT")
                    .put("selector", selector)
                    .put("params", JSONObject()
                        .put("text", "REPLACE_WITH_TEST_TEXT")
                        .put("clear_before", true))
            }
        }
        step.put("order", steps.length() + 1)
            .put("delay_before_ms", if (lastEventAt == 0L) 500L
                else (now - lastEventAt).coerceIn(0L, 30_000L))
        steps.put(step)
        lastEventAt = now
        labelView?.text = "REC ● " + steps.length()
        RecorderRuntime.set(RecorderState(recording = true, paused = paused,
            stepCount = steps.length(), message = "Recording " + app.label))
    }

    fun stop() {
        if (stopped) return
        stopped = true
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
        val endedAt = if (paused) pausedAt else SystemClock.uptimeMillis()
        val duration = ((endedAt - startedAt - totalPausedMs + 999L) / 1000L)
            .coerceIn(5L, 7200L).toInt()
        val json = if (steps.length() == 0) null else JSONObject()
            .put("schema_version", 1)
            .put("meta", JSONObject().put("name", "Recorded " + app.label))
            .put("target", JSONObject().put("package_name", app.packageName))
            .put("execution", JSONObject().put("total_duration_sec", duration)
                .put("loop_steps", false))
            .put("steps", steps).toString(2)
        val result = onStopped(json)
        RecorderRuntime.set(RecorderState(
            message = result,
            stepCount = steps.length()
        ))
    }
}
