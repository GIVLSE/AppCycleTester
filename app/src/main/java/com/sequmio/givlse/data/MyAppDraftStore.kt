package com.sequmio.givlse.data

import android.content.Context
import org.json.JSONObject

data class MyAppDraft(
    val appName: String = "",
    val packageName: String = "",
    val description: String = "",
    val googleGroupUrl: String = "",
    val optInUrl: String = "",
    val playStoreUrl: String = "",
    val scenarioJson: String = ""
) {
    fun validateForPublish(): List<String> = buildList {
        if (appName.isBlank()) add("App name is required")
        if (!Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
                .matches(packageName)) add("Enter a valid Android package name")
        if (!googleGroupUrl.isHttps()) add("Google Group link must use HTTPS")
        if (!optInUrl.isHttps()) add("Become a Tester link must use HTTPS")
        if (!playStoreUrl.isHttps()) add("Google Play link must use HTTPS")
        if (googleGroupUrl.isNotBlank() && android.net.Uri.parse(googleGroupUrl).host != "groups.google.com")
            add("Google Group must use groups.google.com")
        if (optInUrl.isNotBlank() && android.net.Uri.parse(optInUrl).host != "play.google.com")
            add("Become a Tester must use play.google.com")
        if (playStoreUrl.isNotBlank() && android.net.Uri.parse(playStoreUrl).host != "play.google.com")
            add("Google Play URL must use play.google.com")
        val scenarioError = runCatching {
            ScenarioCodec.parseAll(scenarioJson, packageName)
        }.exceptionOrNull()
        if (scenarioError != null) add("Scenario: " + (scenarioError.message ?: "Invalid JSON"))
    }
}

private fun String.isHttps() =
    startsWith("https://", ignoreCase = true) &&
    android.net.Uri.parse(this).host?.isNotBlank() == true

/** Offline developer draft; JSON may contain up to 12 versioned test cases. */
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
            playStoreUrl = json.optString("playStoreUrl"),
            scenarioJson = json.optString("scenarioJson")
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
            .put("scenarioJson", draft.scenarioJson)
        prefs.edit().putString("draft", json.toString()).apply()
    }
}
