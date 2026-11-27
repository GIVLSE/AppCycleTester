package com.sequmio.givlse.runner

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.os.PowerManager
import android.os.Build
import android.os.Handler
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.sequmio.givlse.data.EvidenceStore
import com.sequmio.givlse.data.QaLocalAccount
import java.util.concurrent.Executors

/** Best-effort window-only evidence. Never captures other apps or a whole display. */
internal object FailureEvidenceCapture {
    private val io=Executors.newSingleThreadExecutor()
    private fun eligibleWindow(service: AccessibilityService,pkg:String): Int? = runCatching {
        if(!service.getSystemService(PowerManager::class.java).isInteractive ||
            service.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return null
        val root=service.rootInActiveWindow ?: return null
        if(root.packageName?.toString()!=pkg) return null
        val window=service.windows.firstOrNull { it.id==root.windowId &&
            it.type==AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive } ?: return null
        if(window.root?.packageName?.toString()!=pkg) return null
        // Fail closed on editable/password nodes or overly large trees. Pixels can
        // still contain sensitive information; the tester must review before sharing.
        val queue=ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count=0
        while(queue.isNotEmpty()) {
            val node=queue.removeFirst()
            if(++count>1000 || node.isPassword || node.isEditable) return null
            for(i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        window.id
    }.getOrNull()
    fun request(service: AccessibilityService,handler: Handler,uid:String,key:String,pkg:String,
        scenario:String,step:Int,stillCurrent:()->Boolean,done:()->Unit) {
        val store=EvidenceStore(service)
        if(Build.VERSION.SDK_INT<34 || !store.enabled(uid) || QaLocalAccount.uid(service)!=uid) { done();return }
        val window=eligibleWindow(service,pkg)
        if(window==null || !stillCurrent()) { done();return }
        var finished=false
        fun complete() {
            if(finished) return
            finished=true
            done()
        }
        val at=System.currentTimeMillis()
        val timeout=Runnable { complete() }
        handler.postDelayed(timeout,900L)
        try {
            service.takeScreenshotOfWindow(window,service.mainExecutor,
                object:AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result:AccessibilityService.ScreenshotResult) {
                        val buffer=result.hardwareBuffer
                        try {
                            if(finished || !stillCurrent() || !store.enabled(uid) ||
                                QaLocalAccount.uid(service)!=uid || eligibleWindow(service,pkg)!=window) return
                            val hw=Bitmap.wrapHardwareBuffer(buffer,result.colorSpace) ?: return
                            val copy=try { hw.copy(Bitmap.Config.ARGB_8888,false) } finally { hw.recycle() }
                            if(copy!=null) io.execute {
                                try { store.save(uid,key,pkg,scenario,step,at,copy) }
                                catch(e:Exception) { android.util.Log.w("SequmioEvidence","Local evidence unavailable",e) }
                                finally { copy.recycle() }
                            }
                        } catch(e:Exception) { android.util.Log.w("SequmioEvidence","Screenshot unavailable",e) }
                        finally { buffer.close();handler.removeCallbacks(timeout);complete() }
                    }
                    override fun onFailure(errorCode:Int) { handler.removeCallbacks(timeout);complete() }
                })
        } catch(e:Exception) { handler.removeCallbacks(timeout);complete() }
    }
}
