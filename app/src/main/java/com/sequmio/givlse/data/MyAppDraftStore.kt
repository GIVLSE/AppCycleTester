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
    fun validateDetails(): List<String> = buildList {
        if (appName.isBlank()) add("App name is required")
        if (appName.length >= 100) add("App name must be shorter than 100 characters")
        if (description.isBlank()) add("Description is required")
        if (description.length > 1000) add("Description must be 1000 characters or less")
        if (!Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
                .matches(packageName)) add("Enter a valid Android package name")
        val links = listOf(
            Triple("Google Group", googleGroupUrl, "https://groups.google.com/"),
            Triple("Become a tester", optInUrl, "https://play.google.com/apps/testing/"),
            Triple("Google Play", playStoreUrl, "https://play.google.com/store/apps/details")
        )
        links.forEach { (label, url, prefix) ->
            if (url.isBlank()) add("$label link is required")
            else if (!url.startsWith(prefix) || !url.isHttps())
                add("$label: enter a valid URL starting with $prefix")
        }
    }

    fun validateForPublish(): List<String> = buildList {
        addAll(validateDetails())
        if (scenarioJson.length !in 41..48000)
            add("Test case data must contain 41 to 48000 characters")
        val scenarioError = runCatching {
            ScenarioCodec.parseAll(scenarioJson, packageName)
        }.exceptionOrNull()
        if (scenarioError != null) add("Scenario: " + (scenarioError.message ?: "Invalid JSON"))
    }
}

private fun String.isHttps() =
    startsWith("https://", ignoreCase = true) &&
    runCatching { java.net.URI(this).host?.isNotBlank() == true }.getOrDefault(false)

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
