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
    val metadataEnabled=CloudUploadPrefs(ctx).enabled
    val enabled=uid?.let(store::consent)==true
    AlertDialog(onDismissRequest=onDismiss,title={Text("Automatic Screenshot Evidence")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("If enabled, Sequmio automatically captures the tested app's window when a required assertion fails during your test, and sends the image to the app's developer via Firebase. No review or confirmation is requested for individual screenshots. Images may contain personal or confidential information. Use only test accounts and non-sensitive data.")
                Text("Only Android 14+; protected windows and detected editable/password fields are skipped. A private temporary upload queue is cleared after successful delivery or expiry (24 hours). No screenshots in your gallery or History.",fontSize=12.sp,color=SeqColors.Muted)
                Text("Disable this setting or QA Reports to stop future screenshot uploads and clear pending images. Already shared images remain with the report until deleted.",fontSize=12.sp,color=SeqColors.Muted)
                if(uid==null) Text("Sign in to enable automatic sharing.",color=SeqColors.Muted)
                if(!metadataEnabled) Text("Enable QA Reports first.",color=SeqColors.Muted)
                if(Build.VERSION.SDK_INT<34) Text("Window capture is unavailable on this Android version.",color=SeqColors.Muted)
            }
        },confirmButton={
            if(uid!=null && enabled) SeqTextButton(onClick={store.setEnabled(uid,false);onDismiss()}) { Text("Disable automatic sharing") }
            else if(uid!=null && metadataEnabled && Build.VERSION.SDK_INT>=34) SeqTextButton(onClick={
                store.setEnabled(uid,true);onDismiss()
            }) { Text("Agree and enable automatic sharing") }
            else SeqTextButton(onClick=onDismiss) { Text("Close") }
        },dismissButton={SeqTextButton(onClick=onDismiss){Text("Cancel")}})
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
            if(bitmap==null) message=if(bytes==null) "No shared screenshot for this case." else "Screenshot could not be decoded."
        } catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
        catch(e:com.google.firebase.firestore.FirebaseFirestoreException){message="Evidence unavailable: "+e.code.name}
        catch(_:Exception){message="Invalid screenshot or network error."}
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
