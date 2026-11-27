package com.sequmio.givlse

import android.os.Build
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sequmio.givlse.account.SequmioAccount
import com.sequmio.givlse.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun EvidenceSettingsDialog(account: SequmioAccount,onDismiss:()->Unit) {
    val ctx=LocalContext.current
    val uid=account.profile?.uid
    val store=remember { EvidenceStore(ctx) }
    var enabled by remember(uid) { mutableStateOf(uid?.let(store::enabled)==true) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Screenshot Evidence")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Optional: after a confirmed failed assertion, capture the tested app's window while a test you started is running. Images may contain private information and are stored on this device. No whole-screen capture, protected windows, video or automatic image uploads. Review each image before choosing to share it with the app owner via Firebase.")
                Text("Local images: up to 20 per account, 7 days, Android 14+. Editable/password windows are skipped, but other sensitive pixels may remain.",fontSize=12.sp,color=SeqColors.Muted)
                if(uid==null) Text("Sign in to enable evidence.",color=SeqColors.Muted)
                if(Build.VERSION.SDK_INT<34) Text("Window capture unavailable on this Android version.",color=SeqColors.Muted)
            }
        },confirmButton={
            if(uid!=null && Build.VERSION.SDK_INT>=34 && !enabled) SeqTextButton(onClick={
                store.setEnabled(uid,true);enabled=true;onDismiss()
            }) { Text("Enable local capture") }
            else if(uid!=null && enabled) SeqTextButton(onClick={
                store.setEnabled(uid,false);enabled=false;onDismiss()
            }) { Text("Disable capture") }
            else SeqTextButton(onClick=onDismiss) { Text("Close") }
        },dismissButton={ SeqTextButton(onClick=onDismiss) { Text("Cancel") } })
}

@Composable
internal fun SequmioEvidenceReview(account:SequmioAccount,runId:String,items:List<EvidenceItem>,onBack:()->Unit) {
    val ctx=LocalContext.current
    val store=remember { EvidenceStore(ctx) }
    val repository=remember(account) { EvidenceRepository(account) }
    val uid=account.profile?.uid
    val scope=rememberCoroutineScope()
    var confirm by remember(uid) { mutableStateOf<EvidenceItem?>(null) }
    var message by remember(uid) { mutableStateOf("") }
    var busy by remember(uid) { mutableStateOf(false) }
    BackHandler { if(!busy) onBack() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        item { SeqTopBar("Screenshot Evidence",onBack)
            Text("Review each image. Do not share personal data, credentials, or other confidential content. Images are never sent automatically.",fontSize=12.sp,color=SeqColors.Muted)
            if(message.isNotBlank()) Text(message,fontSize=12.sp,color=SeqColors.Muted)
        }
        if(items.isEmpty()) item { Text("No local screenshots retained for this run.",color=SeqColors.Muted) }
        items(items,key={it.key}) { item ->
            val bytes by produceState<ByteArray?>(null,item.key,uid) {
                value=withContext(Dispatchers.IO) {
                    if(uid!=null && QaLocalAccount.uid(ctx)==uid && item.image.isFile && item.image.length() in 1L..EvidenceStore.MAX_BYTES.toLong())
                        runCatching { item.image.readBytes() }.getOrNull() else null
                }
            }
            val bitmap by produceState<Bitmap?>(null,bytes) { value=withContext(Dispatchers.Default) { bytes?.let { EvidenceStore.decode(it) } } }
            val image=bitmap
            SeqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text(item.appId,fontSize=12.sp,color=SeqColors.Muted)
                    Text(item.scenario+" · Step "+item.step,fontWeight=FontWeight.SemiBold)
                    if(image!=null) Image(image.asImageBitmap(),contentDescription="Failure screenshot",modifier=Modifier.fillMaxWidth().heightIn(max=460.dp))
                    else Text("Image unavailable",color=SeqColors.Muted,fontSize=12.sp)
                    SeqTextButton(onClick={confirm=item},enabled=uid!=null && image!=null && !busy && CloudUploadPrefs(ctx).enabled) { Text("Share with app developer") }
                    SeqTextButton(onClick={
                        val current=uid ?: return@SeqTextButton
                        scope.launch {
                            busy=true
                            try { repository.remove(item.key);message="Shared copy removed." }
                            catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
                            catch(_:Exception){message="Shared copy could not be removed (may not exist)."}
                            finally{busy=false}
                        }
                    },enabled=uid!=null && !busy) { Text("Remove shared copy") }
                    SeqTextButton(onClick={
                        uid?.let { store.delete(it,item.key) }
                        message="Local screenshot deleted. Any shared copy must be removed separately."
                    },enabled=!busy) { Text("Delete local image") }
                    if(!CloudUploadPrefs(ctx).enabled) Text("Enable QA report sharing first to attach evidence to an uploaded failed case.",fontSize=11.sp,color=SeqColors.Muted)
                }
            }
        }
    }
    confirm?.let { item ->
        AlertDialog(onDismissRequest={confirm=null},title={Text("Share this screenshot?")},
            text={Text("The tested app's owner will be able to view this image in QA Dashboard. Confirm that you reviewed it and it does not contain personal or confidential information.")},
            confirmButton={SeqTextButton(onClick={
                confirm=null
                val current=uid ?: return@SeqTextButton
                busy=true
                scope.launch {
                    try {
                        val bytes=withContext(Dispatchers.IO) {
                            require(item.image.length() in 1L..EvidenceStore.MAX_BYTES.toLong())
                            item.image.readBytes()
                        }
                        require(account.profile?.uid==current && CloudUploadPrefs(ctx).enabled)
                        repository.share(item,bytes,current);message="Screenshot shared."
                    } catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
                    catch(e:Exception){message="Share failed: "+(e.message?.take(150) ?: "Try again after QA metadata uploads.")}
                    finally{busy=false}
                }
            }){Text("Confirm and share")}},
            dismissButton={SeqTextButton(onClick={confirm=null}){Text("Cancel")}})
    }
}

@Composable
internal fun QaSharedEvidence(account:SequmioAccount,reportId:String) {
    val uid=account.profile?.uid
    val repository=remember(account){EvidenceRepository(account)}
    var open by remember(reportId,uid){mutableStateOf(false)}
    var message by remember(reportId,uid){mutableStateOf("")}
    var bitmap by remember(reportId,uid){mutableStateOf<Bitmap?>(null)}
    var loading by remember(reportId,uid){mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    SeqTextButton(onClick={open=true},enabled=!loading){Text("Screenshot evidence")}
    LaunchedEffect(open,uid,reportId) {
        if(!open || uid==null) return@LaunchedEffect
        loading=true;message="";bitmap=null
        try {
            val bytes=repository.read(reportId)
            bitmap=withContext(Dispatchers.Default){bytes?.let { EvidenceStore.decode(it) }}
            if(bitmap==null) message="No shared screenshot for this case."
        } catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
        catch(_:Exception){message="Evidence unavailable."}
        finally{loading=false}
    }
    if(open) AlertDialog(onDismissRequest={open=false;bitmap=null},title={Text("Screenshot Evidence")},
        text={
            Column {
                if(loading) CircularProgressIndicator()
                bitmap?.let { Image(it.asImageBitmap(),contentDescription="Shared failure evidence",modifier=Modifier.fillMaxWidth().heightIn(max=420.dp)) }
                if(message.isNotBlank()) Text(message,fontSize=12.sp)
            }
        },confirmButton={SeqTextButton(onClick={open=false;bitmap=null}){Text("Close")}},
        dismissButton={if(bitmap!=null) SeqTextButton(onClick={
            scope.launch {
                try { repository.remove(reportId);bitmap=null;message="Shared image removed." }
                catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
                catch(_:Exception){message="Removal failed."}
            }
        }){Text("Delete shared image")}})
}
