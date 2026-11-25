package com.sequmio.givlse

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.sequmio.givlse.runner.CycleAccessibilityService

/** Diagnostic-only launcher. No test execution, cloud requests, or permanent IPC binding. */
class MainActivity : Activity() {
    companion object {
        const val ACTION_SHOW_RUN_RESULT = "com.sequmio.givlse.SHOW_RUN_RESULT"
    }
    private lateinit var status: TextView
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 32)
            setBackgroundColor(Color.rgb(13,20,36))
        }
        fun label(value: String, size: Float): TextView = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.rgb(230,237,255))
            setPadding(0,12,0,12)
            column.addView(this, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        fun button(value: String, action: () -> Unit) {
            column.addView(Button(this).apply { text = value; setOnClickListener { action() } })
        }
        label("Sequmio 1.2.9", 26f)
        label("Process Isolation Debug", 20f)
        label("Diagnostic only. No test runs or cloud access. Enable Sequmio Runner manually. Close this app by swiping it out of Recents, then reopen and compare the PIDs.", 16f)
        status = label("Checking…", 18f)
        button("Open Accessibility settings") {
            val component = ComponentName(this, CycleAccessibilityService::class.java)
            val intent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, component.flattenToString())
            try { startActivity(intent) } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        button("Open Accessibility menu") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button("Refresh connection / PIDs") { refresh() }
        label("The probe can start an empty :runner process if Android killed it. CONNECTED means a real framework-bound AccessibilityService, not just a running process. A changed PID means the original process did not survive.", 14f)
        setContentView(ScrollView(this).apply { addView(column) })
        Log.i("SequmioIsolation", "UI created pid=" + Process.myPid())
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onDestroy() { generation++; super.onDestroy() }

    private fun refresh() {
        val token = ++generation
        val permission = isSequmioAccessibilityEnabled(this)
        status.text = "Accessibility: " + (if (permission) "ENABLED" else "DISABLED") +
            "\nService connection: CHECKING\nUI PID: " + Process.myPid()
        val ctx = applicationContext
        Thread {
            val result = runCatching {
                ctx.contentResolver.call(Uri.parse("content://" + ctx.packageName +
                    ".isolation-probe"), "status", null, null)
            }
            runOnUiThread {
                if (isDestroyed || token != generation) return@runOnUiThread
                val data = result.getOrNull()
                val error = data?.getString("error") ?: result.exceptionOrNull()?.javaClass?.simpleName
                val ready = data?.getBoolean("connected") == true
                val enabled = isSequmioAccessibilityEnabled(this)
                status.text = "Accessibility: " + (if (enabled) "ENABLED" else "DISABLED") +
                    "\nService connection: " + (if (error != null || data == null) "UNKNOWN" else if (ready && enabled) "CONNECTED" else "DISCONNECTED") +
                    "\nUI PID: " + Process.myPid() +
                    "\nRunner PID: " + (data?.getInt("runnerPid")?.takeIf { it > 0 }?.toString() ?: "unknown") +
                    (error?.let { "\nProbe error: " + it } ?: "")
                Log.i("SequmioIsolation", status.text.toString().replace('\n', ' '))
            }
        }.start()
    }
}
