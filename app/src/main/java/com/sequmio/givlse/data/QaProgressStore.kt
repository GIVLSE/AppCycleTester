package com.sequmio.givlse.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.sequmio.givlse.model.AppPoolOutcome
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONObject

/** Read the real Firebase session without instantiating another auth listener. */
internal object QaLocalAccount {
    fun uid(context: Context): String? = runCatching {
        val appContext = context.applicationContext
        val app = synchronized(QaLocalAccount) {
            FirebaseApp.getApps(appContext).firstOrNull { it.name == "SequmioCloud" } ?:
                if (com.sequmio.givlse.BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
                    com.sequmio.givlse.BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
                    com.sequmio.givlse.BuildConfig.FIREBASE_API_KEY.isNotBlank()) {
                    FirebaseApp.initializeApp(appContext, com.google.firebase.FirebaseOptions.Builder()
                        .setProjectId(com.sequmio.givlse.BuildConfig.FIREBASE_PROJECT_ID)
                        .setApplicationId(com.sequmio.givlse.BuildConfig.FIREBASE_APP_ID)
                        .setApiKey(com.sequmio.givlse.BuildConfig.FIREBASE_API_KEY).build(), "SequmioCloud")
                } else null
        }
        app?.let { FirebaseAuth.getInstance(it).currentUser?.uid }
    }.getOrNull()
}

internal object QaProgressPolicy {
    fun dates(today: LocalDate): List<String> = (13 downTo 0).map { today.minusDays(it.toLong()).toString() }
    fun qualifies(target: JSONObject): Boolean {
        if (target.optString("origin") != "community" || target.optInt("confirmed") <= 0) return false
        val cases = target.optJSONArray("scenarios")
        if (cases != null && cases.length() > 0)
            return (0 until cases.length()).any { i ->
                cases.optJSONObject(i)?.optString("status") in listOf("PASS", "FAIL")
            }
        return ReportDetails.appOutcome(target) == AppPoolOutcome.PASSED
    }
    fun due(nowMinute: Int, configuredMinute: Int, today: String, sent: String, pending: Int): Boolean =
        pending > 0 && nowMinute >= configuredMinute && sent != today
}

/** Device-local activity, account scoped; never represents Google Play enrollment. */
internal class QaProgressStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("qa_progress_v2", Context.MODE_PRIVATE)
    fun days(uid: String, pkg: String): Set<String> =
        prefs.getStringSet("days:$uid:$pkg", emptySet()).orEmpty().toSet()
    fun record(report: JSONObject) {
        val uid = report.optString("testerUid").takeIf { it.isNotBlank() } ?: return
        val targets = report.optJSONArray("targets") ?: return
        val end = report.optLong("endedAt").takeIf { it > 0 } ?: return
        val day = Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()).toLocalDate()
        val valid = (0 until 60).map { day.minusDays(it.toLong()).toString() }.toSet()
        val editor = prefs.edit()
        for (i in 0 until targets.length()) {
            val target = targets.optJSONObject(i) ?: continue
            if (target.optString("accountUid") != uid || !QaProgressPolicy.qualifies(target)) continue
            val pkg = target.optString("packageName").takeIf { it.isNotBlank() } ?: continue
            editor.putStringSet("days:$uid:$pkg", (days(uid,pkg) + day.toString()).intersect(valid))
        }
        editor.apply()
    }
    fun setJoined(uid: String, packages: List<String>, installed: Set<String>) {
        prefs.edit().putStringSet("joined:$uid", packages.filter { it in installed }.toSet()).apply()
    }
    fun pending(uid: String, day: String): Set<String> =
        prefs.getStringSet("joined:$uid", emptySet()).orEmpty().filter { day !in days(uid,it) }.toSet()
    fun enabled(uid: String) = prefs.getBoolean("reminder:$uid", false)
    fun minute(uid: String) = prefs.getInt("minute:$uid", 18*60).coerceIn(0,1439)
    fun lastSent(uid: String) = prefs.getString("sent:$uid","").orEmpty()
    fun setEnabled(uid: String, value: Boolean) { prefs.edit().putBoolean("reminder:$uid",value).apply() }
    fun setMinute(uid: String, value: Int) { prefs.edit().putInt("minute:$uid",value.coerceIn(0,1439)).apply() }
    fun markSent(uid: String, day: String) { prefs.edit().putString("sent:$uid",day).apply() }
    fun clear(uid: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.endsWith(":$uid") || it.startsWith("days:$uid:") }.forEach { editor.remove(it) }
        editor.apply()
    }
}
