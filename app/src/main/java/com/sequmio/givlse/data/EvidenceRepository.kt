package com.sequmio.givlse.data

import android.util.Base64
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import com.sequmio.givlse.account.SequmioAccount
import kotlinx.coroutines.tasks.await

internal class EvidenceRepository(private val account:SequmioAccount) {
    private fun db()=requireNotNull(account.cloudStore()) { "Cloud unavailable" }
    suspend fun share(item:EvidenceItem,bytes:ByteArray,uid:String) {
        require(account.profile?.uid==uid && EvidenceStore.validKey(item.key))
        require(bytes.size in 1..EvidenceStore.MAX_BYTES)
        val report=db().collection("testRuns").document(item.key).get(Source.SERVER).await()
        require(report.exists() && report.getString("testerUid")==uid &&
            report.getString("appId")==item.appId && report.getString("status")=="FAIL") { "Failed QA report must be uploaded first." }
        val owner=report.getString("ownerUid") ?: error("Missing owner")
        require(account.profile?.uid==uid)
        db().collection("runEvidence").document(item.key).set(mapOf(
            "reportId" to item.key,"appId" to item.appId,"ownerUid" to owner,"testerUid" to uid,
            "stepIndex" to item.step,"createdAt" to FieldValue.serverTimestamp(),
            "imageBase64" to Base64.encodeToString(bytes,Base64.NO_WRAP))).await()
    }
    suspend fun read(key:String):ByteArray? {
        val uid=requireNotNull(account.profile?.uid)
        val doc=db().collection("runEvidence").document(key).get(Source.SERVER).await()
        require(account.profile?.uid==uid)
        if(!doc.exists()) return null
        val encoded=doc.getString("imageBase64") ?: return null
        if(encoded.length>280000) return null
        return Base64.decode(encoded,Base64.NO_WRAP).takeIf { it.size<=EvidenceStore.MAX_BYTES }
    }
    suspend fun remove(key:String) {
        requireNotNull(account.profile?.uid)
        db().collection("runEvidence").document(key).delete().await()
    }
}
