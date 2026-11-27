package com.sequmio.givlse.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal data class EvidenceItem(val key: String, val appId: String, val scenario: String,
    val step: Int, val capturedAt: Long, val image: File)

internal class EvidenceStore(context: Context) {
    private val ctx = context.applicationContext
    private val prefs = ctx.getSharedPreferences("qa_evidence_v1",Context.MODE_PRIVATE)
    companion object {
        private val rev = MutableStateFlow(0L)
        val revision: StateFlow<Long> = rev
        private val lock = Any()
        const val MAX_BYTES = 200_000
        private const val KEEP_MS = 7L*24*60*60*1000
        private val KEY = Regex("[0-9a-fA-F-]{36}_[0-9]+_[0-9]+")
        fun validKey(key: String) = KEY.matches(key)
        fun decode(bytes: ByteArray): Bitmap? {
            if(bytes.size > MAX_BYTES || bytes.isEmpty()) return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
            if(options.outWidth !in 1..1280 || options.outHeight !in 1..1280) return null
            return BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        }
    }
    private fun dir(uid: String): File {
        val hash=MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        return File(ctx.noBackupFilesDir,"qa_evidence/"+hash)
    }
    fun enabled(uid: String) = prefs.getBoolean("capture:$uid",false)
    fun setEnabled(uid: String,value: Boolean) { prefs.edit().putBoolean("capture:$uid",value).apply() }
    private fun deleteFiles(dir: File,key: String) {
        File(dir,"$key.jpg").delete();File(dir,"$key.json").delete()
    }
    private fun scan(uid: String): List<EvidenceItem> {
        val dir=dir(uid)
        val now=System.currentTimeMillis()
        val found=dir.listFiles().orEmpty().filter { it.extension=="json" && validKey(it.nameWithoutExtension) }.mapNotNull { meta ->
            val key=meta.nameWithoutExtension
            val img=File(dir,"$key.jpg")
            val row=runCatching { JSONObject(meta.readText()) }.getOrNull()
            val at=row?.optLong("at") ?: 0L
            if(row==null || now-at>KEEP_MS || at<=0L || !img.isFile || img.length() !in 1L..MAX_BYTES.toLong()) {
                deleteFiles(dir,key);null
            } else EvidenceItem(key,row.optString("appId"),row.optString("scenario"),row.optInt("step"),at,img)
        }.sortedByDescending { it.capturedAt }
        found.drop(20).forEach { deleteFiles(dir,it.key) }
        return found.take(20)
    }
    fun list(uid: String): List<EvidenceItem> = synchronized(lock) { scan(uid) }
    fun save(uid: String,key: String,appId: String,scenario: String,step: Int,at: Long,bitmap: Bitmap) {
        if(!validKey(key) || !enabled(uid) || QaLocalAccount.uid(ctx)!=uid) return
        val ratio=minOf(1f,1280f/maxOf(bitmap.width,bitmap.height))
        val scaled=Bitmap.createScaledBitmap(bitmap,(bitmap.width*ratio).toInt().coerceAtLeast(1),(bitmap.height*ratio).toInt().coerceAtLeast(1),true)
        val bytes=try {
            var output=ByteArray(0)
            for(quality in listOf(82,65,45,30)) {
                val stream=ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG,quality,stream)
                output=stream.toByteArray()
                if(output.size<=MAX_BYTES) break
            }
            output
        } finally { if(scaled!==bitmap) scaled.recycle() }
        if(bytes.isEmpty() || bytes.size>MAX_BYTES) return
        synchronized(lock) {
            if(!enabled(uid) || QaLocalAccount.uid(ctx)!=uid) return
            val dir=dir(uid);dir.mkdirs()
            val meta=File(dir,"$key.json")
            if(meta.exists()) return
            val img=File(dir,"$key.jpg")
            try {
                img.writeBytes(bytes)
                meta.writeText(JSONObject().put("appId",appId).put("scenario",scenario.take(120)).put("step",step).put("at",at).toString())
                scan(uid)
                rev.value += 1
            } catch(error:Exception) { deleteFiles(dir,key);throw error }
        }
    }
    fun delete(uid: String,key: String) = synchronized(lock) {
        if(validKey(key)) { deleteFiles(dir(uid),key);rev.value+=1 }
    }
    fun clear(uid: String) = synchronized(lock) {
        dir(uid).deleteRecursively()
        prefs.edit().remove("capture:$uid").apply()
        rev.value+=1
    }
}
