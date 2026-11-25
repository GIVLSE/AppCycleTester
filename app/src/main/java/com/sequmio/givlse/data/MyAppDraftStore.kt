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

/** Remote refresh replaces only a clean local copy; local edits require explicit publish. */
internal fun reconcileOwnedDraft(local: MyAppDraft?, baseline: MyAppDraft?, remote: MyAppDraft): MyAppDraft =
    if (local == null || local == baseline) remote else local

/** Account-scoped, per-package drafts and last known cloud snapshots. */
class MyAppDraftStore(context: Context, private val uid: String? = null) {
    private val prefs = context.applicationContext
        .getSharedPreferences("sequmio_my_app_draft_v1", Context.MODE_PRIVATE)

    private val prefix = if (uid == null) "guest:" else "uid:$uid:"
    private val selection = prefix + "selected"
    private fun draftKey(pkg: String) = prefix + "draft:" + pkg
    private fun baselineKey(pkg: String) = prefix + "baseline:" + pkg
    private fun statusKey(pkg: String) = prefix + "status:" + pkg

    private fun read(key: String): MyAppDraft? {
        val raw = prefs.getString(key, null) ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
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

    private fun encode(draft: MyAppDraft): String = JSONObject()
            .put("appName", draft.appName)
            .put("packageName", draft.packageName)
            .put("description", draft.description)
            .put("googleGroupUrl", draft.googleGroupUrl)
            .put("optInUrl", draft.optInUrl)
            .put("playStoreUrl", draft.playStoreUrl)
            .put("scenarioJson", draft.scenarioJson)
            .toString()

    fun load(): MyAppDraft = loadPackage(prefs.getString(selection, "").orEmpty()) ?: MyAppDraft()

    fun loadPackage(packageName: String): MyAppDraft? = read(draftKey(packageName))

    fun choices(): List<MyAppDraft> = prefs.all.keys
        .filter { it.startsWith(prefix + "draft:") }
        .mapNotNull(::read)
        .filter { it.packageName.isNotBlank() }
        .distinctBy { it.packageName }
        .sortedWith(compareBy<MyAppDraft> { it.appName.lowercase() }.thenBy { it.packageName })

    fun select(packageName: String): MyAppDraft {
        prefs.edit().putString(selection, packageName).apply()
        return load()
    }

    fun save(draft: MyAppDraft) {
        prefs.edit().putString(selection, draft.packageName)
            .putString(draftKey(draft.packageName), encode(draft)).apply()
    }

    /** Recorder must write to the captured account/package, not a later selection. */
    fun saveForPackage(draft: MyAppDraft) {
        prefs.edit().putString(draftKey(draft.packageName), encode(draft)).apply()
    }

    fun publicationStatus(packageName: String): String? =
        prefs.getString(statusKey(packageName), null)

    /** Use the published snapshot, not potentially unpublished local edits. */
    fun publishedDrafts(): List<MyAppDraft> = choices().mapNotNull { draft ->
        if (publicationStatus(draft.packageName) == "published") read(baselineKey(draft.packageName))
        else null
    }

    fun markPublished(draft: MyAppDraft) {
        prefs.edit().putString(baselineKey(draft.packageName), encode(draft))
            .putString(statusKey(draft.packageName), "published").apply()
    }

    fun markUnpublished(packageName: String) {
        prefs.edit().putString(statusKey(packageName), "draft").apply()
    }

    /** Upgrade only claims legacy data automatically after cloud ownership is verified. */
    fun restoreOwned(owned: List<PublishedApp>) {
        val accountUid = requireNotNull(uid)
        val verified = owned.filter { it.ownerUid == accountUid }
        val legacy = read("draft")
        val editor = prefs.edit()
        val ownedPackages = verified.map { it.packageName }.toSet()
        // Deleted cloud listings are not reported as published. Keep local work.
        for (item in choices()) {
            if (item.packageName !in ownedPackages) {
                editor.remove(statusKey(item.packageName)).remove(baselineKey(item.packageName))
            }
        }
        for (app in verified) {
            val remote = app.toDraft()
            val pkg = remote.packageName
            val previous = loadPackage(pkg)
            val legacyMatch = legacy?.takeIf { it.packageName == pkg }
            val local = previous ?: legacyMatch
            val next = reconcileOwnedDraft(local, read(baselineKey(pkg)), remote)
            editor.putString(draftKey(pkg), encode(next))
                .putString(baselineKey(pkg), encode(remote))
                .putString(statusKey(pkg), app.status)
            if (legacyMatch != null && (previous == null || previous == legacyMatch)) editor.remove("draft")
        }
        if (!prefs.contains(selection)) {
            val first = verified.sortedWith(compareBy<PublishedApp> { it.name.lowercase() }.thenBy { it.packageName })
                .firstOrNull()?.packageName
            if (first != null) editor.putString(selection, first)
        }
        editor.apply()
    }

    /** An unowned legacy/guest draft is never attached to a Google account silently. */
    fun hasDeviceDraft(): Boolean = uid != null && (read("draft") != null ||
        read("guest:draft:" + prefs.getString("guest:selected", "").orEmpty()) != null)

    fun importDeviceDraft(): MyAppDraft? {
        requireNotNull(uid)
        val guestKey = "guest:draft:" + prefs.getString("guest:selected", "").orEmpty()
        val legacy = read("draft")
        val draft = legacy ?: read(guestKey) ?: return null
        // Never replace a draft already associated with this account.
        if (loadPackage(draft.packageName) != null) return null
        prefs.edit().putString(selection, draft.packageName)
            .putString(draftKey(draft.packageName), encode(draft))
            .remove(if (legacy != null) "draft" else guestKey).apply()
        return draft
    }
}
