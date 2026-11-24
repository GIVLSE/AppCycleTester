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
    val status: String
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

    suspend fun myPublishedApp(packageName: String): PublishedApp? {
        val doc = db().collection("apps").document(packageName).get().await()
        val app = fromDocument(doc.data.orEmpty())
        return app?.takeIf { it.ownerUid == requiredUid() }
    }

    suspend fun savePublished(draft: MyAppDraft, scenarioJson: String) {
        val uid = requiredUid()
        val errors = draft.validateForPublish()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        require(scenarioJson.length <= 48_000) { "Scenario is too large." }
        val doc = db().collection("apps").document(draft.packageName)
        val existing = doc.get().await()
        if (existing.exists() && existing.getString("ownerUid") != uid) {
            error("This package name is already registered by another developer.")
        }
        val fields = mapOf(
            "packageName" to draft.packageName,
            "name" to draft.appName,
            "description" to draft.description,
            "ownerUid" to uid,
            "ownerName" to (account.profile?.name.orEmpty().ifBlank { "Developer" }),
            "googleGroupUrl" to draft.googleGroupUrl,
            "optInUrl" to draft.optInUrl,
            "playStoreUrl" to draft.playStoreUrl,
            "scenarioJson" to scenarioJson,
            "status" to "published",
            "updatedAt" to Timestamp.now()
        )
        doc.set(fields).await()
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
            field("scenarioJson"), field("status")
        )
    }
}
