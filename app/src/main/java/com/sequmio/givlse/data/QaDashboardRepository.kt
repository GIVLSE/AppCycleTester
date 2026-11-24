package com.sequmio.givlse.data

import com.google.firebase.firestore.DocumentSnapshot
import com.sequmio.givlse.account.SequmioAccount
import kotlinx.coroutines.tasks.await

data class QaStep(
    val step: Int, val action: String, val status: String,
    val ms: Long, val detail: String, val scenario: String
)
data class QaRun(
    val id: String, val appId: String, val status: String,
    val build: String, val model: String, val androidApi: Int,
    val suite: String, val scenario: String, val versions: String,
    val endedAt: Long, val testerUid: String, val steps: List<QaStep>
)
data class QaDigest(
    val day: String, val total: Int, val pass: Int, val fail: Int,
    val stopped: Int, val incomplete: Int, val devices: Int,
    val testers: Int, val emailed: Boolean
)
data class QaDashboardData(val runs: List<QaRun>, val digests: List<QaDigest>) {
    val total: Int get() = runs.size
    val pass: Int get() = runs.count { it.status == "PASS" }
    val fail: Int get() = runs.count { it.status == "FAIL" }
    val devices: Int get() = runs.map { it.model + "|" + it.androidApi }.distinct().size
    val byBuild: Map<String, List<QaRun>> get() = runs.groupBy { it.build }
    val bySuite: Map<String, List<QaRun>> get() = runs.groupBy { it.suite }
}

/**
 * Owner-only queries. Uses an equality filter already covered by Firestore
 * security rules; never downloads another developer's private test results.
 */
class QaDashboardRepository(private val account: SequmioAccount) {
    suspend fun load(appId: String): QaDashboardData {
        val uid = requireNotNull(account.profile?.uid) { "Sign in first" }
        val db = requireNotNull(account.cloudStore()) { "Cloud backend unavailable" }
        val runDocs = db.collection("testRuns").whereEqualTo("ownerUid", uid)
            .limit(250).get().await().documents
        val reports = db.collection("dailyReports").whereEqualTo("ownerUid", uid)
            .limit(80).get().await().documents
        val runs = runDocs.filter { it.getString("appId") == appId }.map { doc ->
            val steps = (doc.get("stepResults") as? List<*>).orEmpty()
                .mapNotNull { row ->
                    val item = row as? Map<*, *> ?: return@mapNotNull null
                    QaStep(
                        (item["stepIndex"] as? Number)?.toInt() ?: 0,
                        item["action"] as? String ?: "",
                        item["status"] as? String ?: "",
                        (item["durationMs"] as? Number)?.toLong() ?: 0,
                        item["message"] as? String ?: "",
                        item["scenarioName"] as? String ?: ""
                    )
                }
            QaRun(
                doc.id,
                doc.getString("appId").orEmpty(),
                doc.getString("status").orEmpty(),
                doc.getString("appVersion") ?: "unknown",
                (doc.getString("deviceManufacturer").orEmpty() + " " +
                    doc.getString("deviceModel").orEmpty()).trim(),
                doc.getLong("androidApi")?.toInt() ?: 0,
                doc.getString("suite").orEmpty(),
                doc.getString("scenarioName").orEmpty(),
                doc.getString("scenarioVersions").orEmpty(),
                doc.getTimestamp("endedAt")?.toDate()?.time ?: 0,
                doc.getString("testerUid").orEmpty(),
                steps
            )
        }.sortedByDescending { it.endedAt }
        val digests = reports.filter { it.getString("appId") == appId }.map { d ->
            QaDigest(
                d.getString("dayUtc") ?: "",
                d.getLong("total")?.toInt() ?: 0,
                d.getLong("pass")?.toInt() ?: 0,
                d.getLong("fail")?.toInt() ?: 0,
                d.getLong("stopped")?.toInt() ?: 0,
                d.getLong("incomplete")?.toInt() ?: 0,
                d.getLong("uniqueDevices")?.toInt() ?: 0,
                d.getLong("uniqueTesters")?.toInt() ?: 0,
                d.getBoolean("emailSent") == true
            )
        }.sortedByDescending { it.day }
        return QaDashboardData(runs, digests)
    }
}
