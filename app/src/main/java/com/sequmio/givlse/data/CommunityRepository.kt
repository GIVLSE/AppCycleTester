package com.sequmio.givlse.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.sequmio.givlse.account.SequmioAccount
import kotlinx.coroutines.tasks.await

data class PublishedApp(
    val packageName: String,
    val name: String,
    val description: String,
    val ownerUid: String,
    val ownerName: String,
    val googleGroupUrl: String,
    val optInUrl: String,
    val playStoreUrl: String,
    val scenarioJson: String,
    val status: String,
    val iconPng: String = ""
)

internal fun PublishedApp.toDraft() = MyAppDraft(
    appName = name, packageName = packageName, description = description,
    googleGroupUrl = googleGroupUrl, optInUrl = optInUrl,
    playStoreUrl = playStoreUrl, scenarioJson = scenarioJson
)

data class JoinedTest(
    val packageName: String,
    val joinedAt: Long,
    val selfConfirmed: Boolean
)

/**
 * Firebase-hosted community catalog; there are no fabricated local listings.
 * Joining is user self-attestation (not a Google Groups/Play verification).
 * Only app owners can write or unpublish their own listing (firestore.rules).
 */
class CommunityRepository(private val account: SequmioAccount) {
    private fun db(): FirebaseFirestore = account.cloudStore()
        ?: error("Google sign-in and Firebase configuration are required.")

    private fun requiredUid(): String = requireNotNull(account.profile?.uid) {
        "Sign in with Google first."
    }

    suspend fun published(): List<PublishedApp> {
        val snapshot = db().collection("apps")
            .whereEqualTo("status", "published")
            .limit(50)
            .get().await()
        return snapshot.documents.mapNotNull { doc ->
            if (!doc.exists()) null else fromDocument(doc.data.orEmpty())
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun publishedApp(packageName: String): PublishedApp? {
        val doc = db().collection("apps").document(packageName).get().await()
        val result = fromDocument(doc.data.orEmpty())
        return result?.takeIf { it.status == "published" }
    }

    /** Include unpublished listings; server query must be scoped to the signed-in owner. */
    suspend fun myApps(uid: String): List<PublishedApp> {
        require(uid == requiredUid()) { "Account changed" }
        val snapshot = db().collection("apps").whereEqualTo("ownerUid", uid)
            .get(com.google.firebase.firestore.Source.SERVER).await()
        require(uid == requiredUid()) { "Account changed" }
        return snapshot.documents.mapNotNull { fromDocument(it.data.orEmpty()) }
            .filter { it.ownerUid == uid }
            .sortedWith(compareBy<PublishedApp> { it.name.lowercase() }.thenBy { it.packageName })
    }

    suspend fun myPublishedApp(packageName: String): PublishedApp? {
        val doc = db().collection("apps").document(packageName).get().await()
        val app = fromDocument(doc.data.orEmpty())
        return app?.takeIf { it.ownerUid == requiredUid() }
    }

    suspend fun savePublished(draft: MyAppDraft, scenarioJson: String, iconPng: String? = null) {
        val uid = requiredUid()
        val errors = draft.validateForPublish()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        val testCases = ScenarioCodec.parseAll(scenarioJson, draft.packageName)
        require(scenarioJson.length <= 48_000) { "Scenario is too large." }
        val doc = db().collection("apps").document(draft.packageName)
        db().runTransaction { transaction ->
            val existing = transaction.get(doc)
            if (existing.exists() && existing.getString("ownerUid") != uid) {
                error("This package name belongs to another developer")
            }
            val changed = !existing.exists() ||
                existing.getString("scenarioJson") != scenarioJson
            val oldVersion = existing.getLong("scenarioVersion")?.toInt() ?: 0
            val version = if (changed) oldVersion + 1 else maxOf(oldVersion, 1)
            val fields = mutableMapOf<String, Any>(
                "packageName" to draft.packageName,
                "name" to draft.appName,
                "description" to draft.description,
                "ownerUid" to uid,
                "ownerName" to (account.profile?.name.orEmpty().ifBlank { "Developer" }),
                "googleGroupUrl" to draft.googleGroupUrl,
                "optInUrl" to draft.optInUrl,
                "playStoreUrl" to draft.playStoreUrl,
                "scenarioJson" to scenarioJson,
                "scenarioVersion" to version,
                "status" to "published",
                "updatedAt" to Timestamp.now()
            )
            // Keep a previous icon if the developer's app is temporarily unavailable locally.
            val publishedIcon = iconPng ?: existing.getString("iconPng")
            if (!publishedIcon.isNullOrBlank() && publishedIcon.length <= AppIconCodec.MAX_ENCODED)
                fields["iconPng"] = publishedIcon
            transaction.set(doc, fields)
            if (changed) {
                val history = doc.collection("scenarioVersions")
                    .document("v" + version.toString().padStart(6, '0'))
                transaction.set(history, mapOf(
                    "ownerUid" to uid,
                    "appId" to draft.packageName,
                    "version" to version,
                    "scenarioJson" to scenarioJson,
                    "suiteNames" to testCases.map { it.suite }.distinct(),
                    "updatedAt" to Timestamp.now()
                ))
            }
        }.await()
    }

    suspend fun unpublish(packageName: String) {
        val uid = requiredUid()
        val doc = db().collection("apps").document(packageName)
        val data = doc.get().await()
        require(data.getString("ownerUid") == uid) { "Only the owner may unpublish." }
        doc.update("status", "draft", "updatedAt", Timestamp.now()).await()
    }

    suspend fun joined(): List<JoinedTest> {
        val uid = requiredUid()
        return db().collection("memberships")
            .whereEqualTo("userUid", uid).limit(100).get().await()
            .documents.mapNotNull { document ->
                val packageName = document.getString("packageName") ?: return@mapNotNull null
                val time = document.getTimestamp("joinedAt")?.toDate()?.time ?: 0L
                JoinedTest(packageName, time, document.getBoolean("selfConfirmed") == true)
            }
    }

    suspend fun confirmJoin(app: PublishedApp, group: Boolean, optIn: Boolean, install: Boolean) {
        val uid = requiredUid()
        require(group && optIn && install) {
            "Complete the Group, Become a Tester and Install steps first."
        }
        require(app.ownerUid != uid) { "You cannot join your own app." }
        val current = db().collection("apps").document(app.packageName).get().await()
        require(current.getString("status") == "published") {
            "The developer has unpublished this app."
        }
        db().collection("memberships").document(uid + "_" + app.packageName)
            .set(mapOf(
                "userUid" to uid,
                "packageName" to app.packageName,
                "selfConfirmed" to true,
                "joinedAt" to Timestamp.now()
            )).await()
    }

    private fun fromDocument(map: Map<String, Any?>): PublishedApp? {
        fun field(key: String) = map[key] as? String ?: ""
        val id = field("packageName")
        if (id.isBlank()) return null
        return PublishedApp(
            id, field("name"), field("description"), field("ownerUid"), field("ownerName"),
            field("googleGroupUrl"), field("optInUrl"), field("playStoreUrl"),
            field("scenarioJson"), field("status"), field("iconPng")
        )
    }
}
