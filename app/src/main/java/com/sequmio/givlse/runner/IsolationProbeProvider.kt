package com.sequmio.givlse.runner

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import com.sequmio.givlse.isSequmioAccessibilityEnabled
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Read-only temporary IPC probe. Never binds or starts AccessibilityService. */
class IsolationProbeProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        Log.i("SequmioIsolation", "Probe provider created runnerPid=" + Process.myPid())
        return true
    }
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        require(method == "status")
        val result = Bundle()
        val latch = CountDownLatch(1)
        val work = Runnable {
            try {
                val enabled = context?.let { isSequmioAccessibilityEnabled(it) } ?: false
                val connected = enabled && CycleAccessibilityService.refreshConnection()
                result.putBoolean("enabled", enabled)
                result.putBoolean("connected", connected)
                result.putInt("runnerPid", Process.myPid())
                Log.i("SequmioIsolation", "Probe runnerPid=" + Process.myPid() +
                    " enabled=" + enabled + " connected=" + connected)
            } finally { latch.countDown() }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) work.run()
        else Handler(Looper.getMainLooper()).post(work)
        if (!latch.await(3, TimeUnit.SECONDS))
            return Bundle().apply { putString("error", "Runner probe timed out") }
        return result
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?): Int = 0
}
