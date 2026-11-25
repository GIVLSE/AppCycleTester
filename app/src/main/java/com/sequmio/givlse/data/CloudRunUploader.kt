package com.sequmio.givlse.data

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.sequmio.givlse.account.SequmioAccount
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Default OFF. Explicit cloud-upload consent is separate from Accessibility.
 * Reports include device/build/QA step statuses, never screen text or entered values.
 */
class CloudUploadPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "sequmio_cloud_reporting_v1", Context.MODE_PRIVATE
    )
    var enabled: Boolean
        get() = prefs.getBoolean("consent", false)
        set(value) { prefs.edit().putBoolean("consent", value).apply() }
    var lastStatus: String
        get() = prefs.getString("lastStatus", "Nothing uploaded yet") ?: ""
        set(value) { prefs.edit().putString("lastStatus", value.take(160)).apply() }
}

/** Idempotent retries even after temporary network failure or application restart. */
object CloudRunUploader {
    fun enqueue(context: Context, reportId: String) {
        if (reportId.isBlank() || !CloudUploadPrefs(context).enabled) return
        val request = OneTimeWorkRequestBuilder<RunUploadWorker>()
            .setInputData(workDataOf("reportId" to reportId))
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "sequmio_upload_" + reportId, ExistingWorkPolicy.KEEP, request
        )
    }
}

class RunUploadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = CloudUploadPrefs(applicationContext)
        if (!prefs.enabled) return Result.success()
        val id = inputData.getString("reportId") ?: return Result.failure()
        val account = SequmioAccount(applicationContext)
        val uid = account.profile?.uid
        val db = account.cloudStore()
        if (uid == null || db == null) {
            prefs.lastStatus = "Cloud upload requires Google sign-in and Firebase setup"
            return Result.failure()
        }
        val report = RunReportStore(applicationContext).recent().firstOrNull { it.id == id }
            ?: return Result.success()
        val json = runCatching { JSONObject(report.rawJson) }.getOrNull()
            ?: return Result.failure()
        val targets = json.optJSONArray("targets") ?: return Result.success()
        try {
            for (index in 0 until targets.length()) {
                if (!prefs.enabled) return Result.success()
                val target = targets.optJSONObject(index) ?: continue
                val appId = target.optString("packageName")
                if (appId.isBlank() || target.optString("scenarioName").isBlank()) continue
                val appDoc = db.collection("apps").document(appId).get().await()
                if (!appDoc.exists() || appDoc.getString("status") != "published") continue
                val ownerUid = appDoc.getString("ownerUid") ?: continue
                if (ownerUid != uid) {
                    val membership = db.collection("memberships").document(uid + "_" + appId)
                        .get().await()
                    if (membership.getBoolean("selfConfirmed") != true) continue
                }
                val scenarios = target.optJSONArray("scenarios") ?: JSONArray()
                // Each scenario invocation has its own immutable report. A skipped
                // scenario is not silently counted as a successful test run.
                for (caseIndex in 0 until scenarios.length()) {
                    val scenario = scenarios.optJSONObject(caseIndex) ?: continue
                    val outcome = when {
                        scenario.optString("status") == "FAIL" -> "FAIL"
                        scenario.optString("status") == "PASS" -> "PASS"
                        scenario.optString("status") == "STOPPED" -> "STOPPED"
                        // Keep the legacy immutable payload for old sessions.
                        // In new sessions, only the actually interrupted case is STOPPED.
                        report.status == "STOPPED" -> "STOPPED"
                        report.status == "INTERRUPTED" -> "INTERRUPTED"
                        else -> "INCOMPLETE"
                    }
                    val steps = scenario.optJSONArray("steps") ?: JSONArray()
                    val safeSteps = (0 until minOf(steps.length(), 150)).mapNotNull { i ->
                        steps.optJSONObject(i)?.let { step ->
                            mapOf(
                                "scenarioName" to step.optString("scenarioName").take(120),
                                "suite" to step.optString("suite").take(16),
                                "scenarioVersion" to step.optInt("scenarioVersion", 1),
                                "stepIndex" to step.optInt("stepIndex", 1),
                                "action" to step.optString("action").take(30),
                                "status" to step.optString("status").take(20),
                                "durationMs" to step.optLong("durationMs").coerceAtLeast(0),
                                "message" to step.optString("message").take(200)
                            )
                        }
                    }
                    val savedVersion = target.optString("appVersion", "")
                    val details = if (savedVersion.isNotBlank())
                        savedVersion.take(80) to target.optLong("appVersionCode", 0)
                    else appVersion(appId)
                    val started = scenario.optLong("startedAt", report.startedAt)
                        .coerceAtLeast(0L)
                    val ended = scenario.optLong("endedAt", report.endedAt)
                        .coerceAtLeast(started)
                    val record = mapOf(
                        "ownerUid" to ownerUid,
                        "testerUid" to uid,
                        "appId" to appId,
                        "runId" to report.id,
                        "sessionStatus" to report.status,
                        "status" to outcome,
                        "startedAt" to Timestamp(Date(started)),
                        "endedAt" to Timestamp(Date(ended)),
                        "elapsedSeconds" to scenario.optLong("actualSeconds", (ended - started) / 1000L).coerceAtLeast(0L),
                        "foregroundSeconds" to target.optLong("foregroundSeconds"),
                        "launches" to target.optInt("launches"),
                        "retries" to target.optInt("retries"),
                        "scenarioName" to scenario.optString("name").take(120),
                        "suite" to scenario.optString("suite").take(16),
                        "scenarioVersions" to scenario.optInt("version", 1).toString(),
                        "appVersion" to details.first,
                        "appVersionCode" to details.second,
                        "deviceManufacturer" to Build.MANUFACTURER.take(80),
                        "deviceModel" to Build.MODEL.take(100),
                        "androidApi" to Build.VERSION.SDK_INT,
                        "stepResults" to safeSteps
                    )
                    val upload = db.collection("testRuns")
                        .document(report.id + "_" + index + "_" + caseIndex)
                    // A read of a nonexistent report is denied by the current rules.
                    // Create directly; immutable identical-payload retries are allowed.
                    upload.set(record).await()
                }
            }
            prefs.lastStatus = "Last QA run uploaded"
            return Result.success()
        } catch (error: Exception) {
            prefs.lastStatus = "Upload not completed: " + error.javaClass.simpleName
            return if (runAttemptCount >= 8) Result.failure() else Result.retry()
        }
    }

    private fun JSONArray?.hasRequiredFailure(): Boolean {
        if (this == null) return false
        return (0 until length()).any {
            optJSONObject(it)?.optString("status") == "FAIL"
        }
    }

    @Suppress("DEPRECATION")
    private fun appVersion(packageName: String): Pair<String, Long> =
        runCatching {
            val pm = applicationContext.packageManager
            val info = if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(
                packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)
            ) else pm.getPackageInfo(packageName, 0)
            (info.versionName ?: "unknown").take(80) to
                if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
                else info.versionCode.toLong()
        }.getOrDefault("unknown" to 0L)
}
