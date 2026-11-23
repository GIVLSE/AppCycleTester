package io.github.givlse.appcycletester.data

import android.content.Context
import io.github.givlse.appcycletester.model.RepeatMode
import io.github.givlse.appcycletester.model.RunEvent
import io.github.givlse.appcycletester.model.TestPlan
import io.github.givlse.appcycletester.model.TestTarget
import org.json.JSONArray
import org.json.JSONObject

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_cycle_tester", Context.MODE_PRIVATE)

    fun loadPlan(): TestPlan = try {
        val json = JSONObject(prefs.getString("plan", "{}") ?: "{}")
        val targetsArray = json.optJSONArray("targets") ?: JSONArray()
        val targets = buildList {
            for (i in 0 until targetsArray.length()) {
                val target = targetsArray.optJSONObject(i) ?: continue
                val pkg = target.optString("packageName")
                val activity = target.optString("activityClass")
                if (pkg.isBlank() || activity.isBlank()) continue
                add(TestTarget(pkg, activity, target.optString("label", pkg),
                    target.optInt("durationSeconds", 300).coerceIn(60, 7200)))
            }
        }
        TestPlan(
            targets = targets,
            repeatMode = runCatching { RepeatMode.valueOf(json.optString("repeatMode", "ONCE")) }
                .getOrDefault(RepeatMode.ONCE),
            repeatCount = json.optInt("repeatCount", 3).coerceIn(1, 999),
            maxDurationMinutes = json.optInt("maxDurationMinutes", 0).coerceIn(0, 10080)
        )
    } catch (_: Exception) { TestPlan() }

    fun savePlan(plan: TestPlan) {
        val json = JSONObject().put("repeatMode", plan.repeatMode.name)
            .put("repeatCount", plan.repeatCount)
            .put("maxDurationMinutes", plan.maxDurationMinutes)
        val targets = JSONArray()
        plan.targets.forEach {
            targets.put(JSONObject().put("packageName", it.packageName)
                .put("activityClass", it.activityClass)
                .put("label", it.label).put("durationSeconds", it.durationSeconds))
        }
        json.put("targets", targets)
        prefs.edit().putString("plan", json.toString()).apply()
    }

    fun readHistory(): List<RunEvent> = try {
        val arr = JSONArray(prefs.getString("history", "[]"))
        buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                add(RunEvent(item.optLong("at"), item.optString("message")))
            }
        }
    } catch (_: Exception) { emptyList() }

    fun appendHistory(event: RunEvent) {
        val arr = JSONArray()
        (readHistory() + event).takeLast(150).forEach {
            arr.put(JSONObject().put("at", it.timestampMillis).put("message", it.message))
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }
}
