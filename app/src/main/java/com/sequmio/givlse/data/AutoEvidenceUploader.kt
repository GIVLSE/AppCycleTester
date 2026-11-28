package com.sequmio.givlse.data

import android.content.Context
import androidx.work.*
import com.sequmio.givlse.account.SequmioAccount
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import java.security.MessageDigest

internal object AutoEvidenceUploader {
    private fun tag(uid:String) = "qa_evidence_" + MessageDigest.getInstance("SHA-256")
        .digest(uid.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun cancel(context:Context,uid:String) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(tag(uid))
    }
    fun enqueue(context:Context,uid:String,key:String) {
        if(!EvidenceStore.validKey(key) || !EvidenceStore(context).enabled(uid)) return
        val manager=WorkManager.getInstance(context.applicationContext)
        val input=workDataOf("uid" to uid,"key" to key)
        val upload=OneTimeWorkRequestBuilder<AutoEvidenceWorker>()
            .setInputData(input).addTag(tag(uid))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build()
        manager.enqueueUniqueWork("qa_evidence_upload_"+key,ExistingWorkPolicy.KEEP,upload)
        val expiry=OneTimeWorkRequestBuilder<EvidenceExpiryWorker>()
            .setInputData(input).setInitialDelay(24,TimeUnit.HOURS).build()
        manager.enqueueUniqueWork("qa_evidence_expiry_"+key,ExistingWorkPolicy.KEEP,expiry)
    }
}

class AutoEvidenceWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val uid=inputData.getString("uid") ?: return Result.failure()
        val key=inputData.getString("key") ?: return Result.failure()
        val store=EvidenceStore(applicationContext)
        if(!EvidenceStore.validKey(key)) return Result.failure()
        if(QaLocalAccount.uid(applicationContext)!=uid || !store.enabled(uid)) {
            store.delete(uid,key);return Result.success()
        }
        val item=store.list(uid).firstOrNull{it.key==key} ?: return Result.success()
        try {
            val bytes=item.image.readBytes()
            require(bytes.size in 1..EvidenceStore.MAX_BYTES)
            val account=SequmioAccount(applicationContext)
            if(account.profile?.uid!=uid || !store.enabled(uid)) {
                store.delete(uid,key);return Result.success()
            }
            // Metadata may still be uploading. Retry without delaying Runner or
            // blocking successful metadata uploads for unrelated cases.
            EvidenceRepository(account).share(item,bytes,uid)
            store.delete(uid,key)
            android.util.Log.i("SequmioEvidence","Automatic evidence uploaded")
            return Result.success()
        } catch(cancel:CancellationException) { throw cancel }
        catch(error:Exception) {
            android.util.Log.w("SequmioEvidence","Evidence upload not completed: "+error.javaClass.simpleName+
                if(error is FirebaseFirestoreException) " "+error.code.name else "")
            if(!store.enabled(uid) || QaLocalAccount.uid(applicationContext)!=uid ||
                runAttemptCount>=10 || System.currentTimeMillis()-item.capturedAt>=EvidenceStore.KEEP_MS) {
                store.delete(uid,key);return Result.failure()
            }
            return Result.retry()
        }
    }
}

class EvidenceExpiryWorker(context:Context,params:WorkerParameters):Worker(context,params) {
    override fun doWork():Result {
        val uid=inputData.getString("uid") ?: return Result.failure()
        val key=inputData.getString("key") ?: return Result.failure()
        EvidenceStore(applicationContext).expire(uid,key)
        return Result.success()
    }
}
