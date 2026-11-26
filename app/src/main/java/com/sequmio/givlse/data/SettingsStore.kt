package com.sequmio.givlse.data

import android.content.Context
import com.sequmio.givlse.model.RepeatMode
import com.sequmio.givlse.model.ExecutionMode
import com.sequmio.givlse.model.RunEvent
import com.sequmio.givlse.model.TestPlan
import com.sequmio.givlse.model.TestTarget
import org.json.JSONArray
import org.json.JSONObject

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_cycle_tester", Context.MODE_PRIVATE)

    fun accessConsentGiven(): Boolean = prefs.getBoolean("access_disclosure_consent_v3", false)

    fun saveAccessConsent() {
        prefs.edit().putBoolean("access_disclosure_consent_v3", true).apply()
    }

    // Do not migrate the unreliable boolean from 1.3.1/1.3.2 as a confirmed restriction.
    fun resultReturnStatus(): ResultReturnStatus = runCatching {
        ResultReturnStatus.valueOf(prefs.getString("result_return_status_v2", null) ?: "UNKNOWN")
    }.getOrDefault(ResultReturnStatus.UNKNOWN)

    fun setResultReturnNeedsReview(value: Boolean) {
        val status = if (value) ResultReturnStatus.NOT_OBSERVED else ResultReturnStatus.CONFIRMED
        prefs.edit().putString("result_return_status_v2", status.name).apply()
    }

    fun resultReturnHintDismissed(): Boolean = prefs.getBoolean("result_return_hint_dismissed", false)

    fun setResultReturnHintDismissed(value: Boolean) {
        prefs.edit().putBoolean("result_return_hint_dismissed", value).apply()
    }

    fun observeResultReturnSettings(onChange: () -> Unit): () -> Unit {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "result_return_status_v2" || key == "result_return_hint_dismissed") onChange()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onChange()
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun excludedOwnedApps(uid: String): Set<String> =
        prefs.getStringSet("owned_plan_excluded:$uid", emptySet()).orEmpty().toSet()

    fun setOwnedExcluded(uid: String, pkg: String, excluded: Boolean) {
        val previous = excludedOwnedApps(uid)
        val next = if (excluded) previous + pkg else previous - pkg
        prefs.edit().putStringSet("owned_plan_excluded:$uid", next).apply()
    }

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
                    target.optInt("durationSeconds", 300).coerceIn(1, 7200),
                    target.optString("scenarioJson", "").takeIf { it.isNotBlank() },
                    target.optString("origin", "local"),
                    runCatching { ExecutionMode.valueOf(target.optString("executionMode")) }
                        .getOrDefault(if (target.optString("scenarioJson").isBlank())
                            ExecutionMode.FIXED_DURATION else ExecutionMode.RUN_TEST_CASES),
                    target.optInt("scenarioTimeoutSeconds", 0).coerceIn(0, 14430),
                    target.optJSONArray("selectedCaseKeys")?.let { arr ->
                        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                    }, target.optString("ownerUid", "").takeIf { it.isNotBlank() }))
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
                .put("label", it.label).put("durationSeconds", it.durationSeconds)
                .put("scenarioJson", it.scenarioJson ?: "")
                .put("origin", it.origin)
                .put("ownerUid", it.ownerUid ?: "")
                .put("executionMode", it.executionMode.name)
                .put("scenarioTimeoutSeconds", it.scenarioTimeoutSeconds)
                .put("selectedCaseKeys", it.selectedCaseKeys?.let { keys -> JSONArray(keys) } ?: JSONObject.NULL))
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


enum class ResultReturnStatus { UNKNOWN, CONFIRMED, NOT_OBSERVED }
