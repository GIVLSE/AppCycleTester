package com.sequmio.givlse.data

import android.content.Context
import org.json.JSONObject

data class MyAppDraft(
    val appName: String = "",
    val packageName: String = "",
    val description: String = "",
    val googleGroupUrl: String = "",
    val optInUrl: String = "",
    val playStoreUrl: String = ""
) {
    fun validateForPublish(): List<String> = buildList {
        if (appName.isBlank()) add("App name is required")
        if (!Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
                .matches(packageName)) add("Enter a valid Android package name")
        if (!googleGroupUrl.isHttps()) add("Google Group link must use HTTPS")
        if (!optInUrl.isHttps()) add("Become a Tester link must use HTTPS")
        if (!playStoreUrl.isHttps()) add("Google Play link must use HTTPS")
        // No scenario engine yet. Publishing cannot be enabled in V0.5.
        add("At least one runnable scenario is required (V0.7)")
    }
}

private fun String.isHttps() =
    startsWith("https://", ignoreCase = true) &&
    android.net.Uri.parse(this).host?.isNotBlank() == true

/** One offline draft in V0.5. No public listing or server-side publishing. */
class MyAppDraftStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("sequmio_my_app_draft_v1", Context.MODE_PRIVATE)

    fun load(): MyAppDraft {
        val json = runCatching {
            JSONObject(prefs.getString("draft", "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        return MyAppDraft(
            appName = json.optString("appName"),
            packageName = json.optString("packageName"),
            description = json.optString("description"),
            googleGroupUrl = json.optString("googleGroupUrl"),
            optInUrl = json.optString("optInUrl"),
            playStoreUrl = json.optString("playStoreUrl")
        )
    }

    fun save(draft: MyAppDraft) {
        val json = JSONObject()
            .put("appName", draft.appName)
            .put("packageName", draft.packageName)
            .put("description", draft.description)
            .put("googleGroupUrl", draft.googleGroupUrl)
            .put("optInUrl", draft.optInUrl)
            .put("playStoreUrl", draft.playStoreUrl)
        prefs.edit().putString("draft", json.toString()).apply()
    }
}
