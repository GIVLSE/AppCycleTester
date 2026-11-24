package com.sequmio.givlse.data

import android.content.Context

/** Local in-progress enrollment, separate from server-confirmed membership. */
data class JoinSteps(val groupConfirmed: Boolean = false, val optInConfirmed: Boolean = false) {
    fun ready(installed: Boolean) = groupConfirmed && optInConfirmed && installed
    fun encode(): Int = (if (groupConfirmed) 1 else 0) or (if (optInConfirmed) 2 else 0)
    companion object {
        fun decode(value: Int) = JoinSteps(value and 1 != 0, value and 2 != 0)
        fun priority(pending: Boolean) = if (pending) 0 else 1
    }
}

/** Per-account state persists across process death and navigation; no cloud writes. */
class PendingJoinStore(context: Context, uid: String) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "sequmio_pending_join_" + uid, Context.MODE_PRIVATE
    )
    fun load(): Map<String, JoinSteps> = prefs.all.mapNotNull { (pkg, value) ->
        (value as? Int)?.let { pkg to JoinSteps.decode(it) }
    }.toMap()
    fun save(packageName: String, steps: JoinSteps?) {
        val editor = prefs.edit()
        if (steps == null) editor.remove(packageName)
        else editor.putInt(packageName, steps.encode())
        editor.apply()
    }
    fun clear() { prefs.edit().clear().apply() }
}
