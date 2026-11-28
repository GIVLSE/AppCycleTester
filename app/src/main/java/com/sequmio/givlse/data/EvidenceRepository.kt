package com.sequmio.givlse.data

import android.util.Base64
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import com.sequmio.givlse.account.SequmioAccount
import kotlinx.coroutines.tasks.await

internal class EvidenceRepository(private val account:SequmioAccount) {
    private fun db()=requireNotNull(account.cloudStore()) { "Cloud unavailable" }
    // Called only from the automatic evidence worker after the metadata exists.
    suspend fun share(item:EvidenceItem,bytes:ByteArray,uid:String) {
        require(account.profile?.uid==uid && EvidenceStore.validKey(item.key))
        require(bytes.size in 1..EvidenceStore.MAX_BYTES)
        val context = account.evidenceContext()
        val store=EvidenceStore(context)
        val database=db()
        val reportRef=database.collection("testRuns").document(item.key)
        val evidenceRef=database.collection("runEvidence").document(item.key)
        // Online-only transaction; no SDK offline write waiting to send after revocation.
        database.runTransaction { tx ->
            require(QaLocalAccount.uid(context)==uid && store.enabled(uid)) { "Evidence consent revoked" }
            val report=tx.get(reportRef)
            require(report.exists() && report.getString("testerUid")==uid &&
                report.getString("appId")==item.appId && report.getString("status")=="FAIL") { "Failed QA report not ready" }
            val steps=(report.get("stepResults") as? List<*>).orEmpty()
            require(steps.any { value ->
                val step=value as? Map<*,*> ?: return@any false
                (step["stepIndex"] as? Number)?.toInt()==item.step && step["status"]=="FAIL"
            }) { "No matching failed step" }
            val owner=report.getString("ownerUid") ?: error("Missing owner")
            val existing=tx.get(evidenceRef)
            if(!existing.exists()) {
                require(QaLocalAccount.uid(context)==uid && store.enabled(uid))
                tx.set(evidenceRef,mapOf(
                    "reportId" to item.key,"appId" to item.appId,"ownerUid" to owner,"testerUid" to uid,
                    "stepIndex" to item.step,"createdAt" to FieldValue.serverTimestamp(),
                    "imageBase64" to Base64.encodeToString(bytes,Base64.NO_WRAP)))
            }
            true
        }.await()
    }
    suspend fun read(key:String):ByteArray? {
        val uid=requireNotNull(account.profile?.uid)
        val doc=db().collection("runEvidence").document(key).get(Source.SERVER).await()
        require(account.profile?.uid==uid)
        if(!doc.exists()) return null
        val encoded=doc.getString("imageBase64") ?: error("Invalid screenshot data")
        require(encoded.length<=280000) { "Screenshot exceeds size limit" }
        return Base64.decode(encoded,Base64.NO_WRAP).also { require(it.size in 1..EvidenceStore.MAX_BYTES) }
    }
    suspend fun remove(key:String) {
        requireNotNull(account.profile?.uid)
        db().collection("runEvidence").document(key).delete().await()
    }
}
