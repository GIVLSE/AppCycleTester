package com.sequmio.givlse.runner

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.sequmio.givlse.model.InstalledApp
import org.json.JSONObject

/** A single explicitly selected, visible accessibility element; no screenshots or typed values. */
data class PickedElement(
    val viewId: String = "", val text: String = "", val description: String = "",
    val label: String, val x: Double, val y: Double,
    val expectedText: String = "", val enabled: Boolean = false, val editable: Boolean = false
) {
    fun selector() = JSONObject().apply {
        if (viewId.isNotBlank()) put("view_id", viewId)
        if (text.isNotBlank()) put("text", text)
        if (description.isNotBlank()) put("content_desc", description)
    }
    fun hasSelector() = viewId.isNotBlank() || text.isNotBlank() || description.isNotBlank()
    fun canCheckText() = expectedText.isNotBlank() && expectedText.length <= 200
}

/**
 * The overlay consumes the pick gesture rather than sending it to the target.
 * Only the selected element leaves this local, ephemeral tree snapshot.
 * Never read password or editable-field text, and never capture other packages.
 */
internal class VisualElementPicker(
    private val service: AccessibilityService,
    private val app: InstalledApp,
    private val verification: Boolean,
    private val onResult: (PickedElement?, String?) -> Unit
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var window: View? = null
    private var finished = false
    private data class Candidate(val bounds: Rect, val id: String, val text: String,
        val desc: String, val enabled: Boolean, val editable: Boolean)
    private fun dp(v: Int) = (v * service.resources.displayMetrics.density).toInt()
    private fun button(label: String, action: () -> Unit) = Button(service).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }
    private fun label(value: String) = TextView(service).apply {
        text = value; textSize = 14f; setTextColor(Color.WHITE)
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }
    private fun panel() = LinearLayout(service).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.rgb(22, 35, 55))
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }
    private fun attach(view: View, full: Boolean) {
        removeWindow()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            if (full) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }
        wm.addView(view, params)
        window = view
    }
    private fun removeWindow() {
        window?.let { runCatching { wm.removeView(it) } }
        window = null
    }
    fun cancel(notify: Boolean = true) {
        if (finished) return
        finished = true
        removeWindow()
        if (notify) onResult(null, null)
    }
    private fun complete(element: PickedElement, action: String?) {
        if (finished) return
        finished = true
        removeWindow()
        onResult(element, action)
    }
    /** For an editor selection, allow normal navigation until the user presses Pick. */
    fun startNavigation(): String? = try {
        val root = panel()
        val row = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
        val status = label("Navigate to the element")
        root.addView(status)
        row.addView(button("Pick element") {
            if (!showSelection()) status.text = "Open " + app.label + " and try again."
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button("Cancel") { cancel() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row)
        attach(root, false)
        service.startActivity(Intent.makeMainActivity(ComponentName(app.packageName, app.activityClass))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        null
    } catch (e: Exception) {
        cancel(false)
        "Could not open element picker: " + e.javaClass.simpleName
    }
    private fun candidates(): List<Candidate> {
        val root = service.rootInActiveWindow ?: return emptyList()
        if (root.packageName?.toString() != app.packageName) return emptyList()
        val result = mutableListOf<Candidate>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Boolean>>()
        queue.addLast(root to false)
        var seen = 0
        while (queue.isNotEmpty() && seen++ < 1000) {
            val (node, inheritedSensitive) = queue.removeFirst()
            val sensitive = inheritedSensitive || node.isPassword
            if (node.packageName?.toString() == app.packageName && !sensitive) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val id = node.viewIdResourceName.orEmpty().take(200)
                val editable = node.isEditable
                val text = if (editable) "" else node.text?.toString().orEmpty().takeIf { it.length <= 200 }.orEmpty()
                val desc = if (editable) "" else node.contentDescription?.toString().orEmpty().takeIf { it.length <= 200 }.orEmpty()
                if (node.isVisibleToUser && !bounds.isEmpty &&
                    (id.isNotBlank() || text.isNotBlank() || desc.isNotBlank() || node.isClickable))
                    result.add(Candidate(bounds, id, text, desc, node.isEnabled, editable))
                for (i in 0 until node.childCount) node.getChild(i)?.let {
                    queue.addLast(it to sensitive)
                }
            }
        }
        return result
    }
    private fun select(c: Candidate, all: List<Candidate>): PickedElement {
        // Match the existing player: use only a unique identifier, then unique text or description.
        // A duplicate ID must not silently select the first item of a RecyclerView.
        val id = c.id.takeIf { it.isNotBlank() && all.count { n -> n.id == it } == 1 }.orEmpty()
        val text = if (id.isBlank()) c.text.takeIf { it.isNotBlank() &&
            all.count { n -> n.text.equals(it, true) } == 1 }.orEmpty() else ""
        val desc = if (id.isBlank() && text.isBlank()) c.desc.takeIf { it.isNotBlank() &&
            all.count { n -> n.desc.equals(it, true) } == 1 }.orEmpty() else ""
        val metrics = service.resources.displayMetrics
        val title = c.text.ifBlank { c.desc.ifBlank { c.id.substringAfterLast('/').replace('_', ' ') } }
            .ifBlank { "Screen position" }.take(100)
        return PickedElement(id, text, desc, title,
            (c.bounds.centerX().toDouble() / metrics.widthPixels.coerceAtLeast(1)).coerceIn(0.0, 1.0),
            (c.bounds.centerY().toDouble() / metrics.heightPixels.coerceAtLeast(1)).coerceIn(0.0, 1.0),
            c.text, c.enabled, c.editable)
    }
    fun showSelection(): Boolean {
        if (finished) return false
        val all = runCatching { candidates() }.getOrDefault(emptyList())
        if (all.isEmpty()) return false
        return try {
            val frame = FrameLayout(service)
            var picked: PickedElement? = null
            var highlight: Rect? = null
            lateinit var options: LinearLayout
            val canvas = object : View(service) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                override fun onDraw(c: Canvas) {
                    c.drawColor(Color.argb(20, 0, 0, 0))
                    val rect = highlight ?: return
                    val loc = IntArray(2); getLocationOnScreen(loc)
                    val local = Rect(rect).apply { offset(-loc[0], -loc[1]) }
                    paint.color = Color.rgb(56, 220, 205)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = dp(3).toFloat()
                    c.drawRect(local, paint)
                }
                override fun onTouchEvent(e: MotionEvent): Boolean {
                    if (e.action == MotionEvent.ACTION_UP) {
                        val candidate = all.filter { it.bounds.contains(e.rawX.toInt(), e.rawY.toInt()) }
                            .minByOrNull { it.bounds.width().toLong() * it.bounds.height() }
                        picked = candidate?.let { select(it, all) }
                        highlight = candidate?.bounds
                        invalidate()
                        options.removeAllViews()
                        val element = picked
                        options.addView(label(if (element == null) "No accessible element here. Try another."
                            else "Selected: " + element.label))
                        if (element != null) {
                            if (verification) {
                                if (!element.hasSelector()) {
                                    options.addView(label("No unique accessible selector. Choose another element."))
                                } else {
                                    options.addView(button("Element is visible") { complete(element, "ASSERT_EXISTS") })
                                    if (element.canCheckText()) options.addView(button("Text equals: " + element.expectedText.take(45)) {
                                        complete(element, "ASSERT_TEXT")
                                    })
                                    options.addView(button("Element is enabled") { complete(element, "ASSERT_ENABLED") })
                                    options.addView(button("Screen marker is visible") { complete(element, "ASSERT_SCREEN") })
                                }
                            } else options.addView(button("Use selected element") { complete(element, null) })
                        }
                        options.addView(button("Cancel") { cancel() })
                    }
                    return true
                }
            }
            frame.addView(canvas, FrameLayout.LayoutParams(-1, -1))
            val top = panel()
            val row = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(label("Tap an element to select"), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(button("Cancel") { cancel() })
            top.addView(row)
            frame.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
            options = panel()
            options.addView(label("Selection does not tap the app."))
            frame.addView(options, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            attach(frame, true)
            true
        } catch (_: Exception) { cancel(); false }
    }
}
