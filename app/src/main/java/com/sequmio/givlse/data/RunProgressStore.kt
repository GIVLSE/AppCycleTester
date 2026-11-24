package com.sequmio.givlse.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Local daily QA completion tracking. It does NOT verify Google Play tester
 * enrollment or substitute for actual manual user feedback.
 */
class RunProgressStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("sequmio_local_qa_progress", Context.MODE_PRIVATE)

    fun days(packageName: String): Int = try {
        val days = JSONArray(prefs.getString(packageName, "[]"))
        (0 until days.length()).mapNotNull { days.optString(it).takeIf(String::isNotBlank) }
            .distinct().size
    } catch (_: Exception) { 0 }

    fun recordPassedApps(targets: JSONArray) {
        val now = LocalDate.now().toString()
        for (i in 0 until targets.length()) {
            val item = targets.optJSONObject(i) ?: continue
            val id = item.optString("packageName")
            val cases = item.optJSONArray("scenarios") ?: JSONArray()
            val casesPassed = cases.length() > 0 &&
                (0 until cases.length()).all { position ->
                    cases.optJSONObject(position)?.optString("status") == "PASS"
                }
            val timedAppPassed = cases.length() == 0 &&
                item.optLong("foregroundSeconds") >= item.optLong("durationSeconds")
            if (id.isBlank() || item.optInt("confirmed") == 0 ||
                item.optInt("failures") != 0 || !(casesPassed || timedAppPassed)) continue
            val old = runCatching { JSONArray(prefs.getString(id, "[]")) }
                .getOrDefault(JSONArray())
            val set = mutableSetOf<String>()
            for (j in 0 until old.length()) set.add(old.optString(j))
            set.add(now)
            val data = JSONArray()
            set.filter { it.isNotBlank() }.sorted().takeLast(60).forEach { data.put(it) }
            prefs.edit().putString(id, data.toString()).apply()
        }
    }
}
