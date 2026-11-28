package com.sequmio.givlse

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sequmio.givlse.account.SequmioAccount
import com.sequmio.givlse.data.*
import com.sequmio.givlse.model.InstalledApp
import java.time.LocalDate
import java.util.Locale

@Composable
internal fun SequmioQaProgress(account: SequmioAccount, joined: List<PublishedApp>?,
    installed: List<InstalledApp>, revision: Long, notificationsGranted: Boolean,
    requestNotifications: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val uid = account.profile?.uid
    val store = remember { QaProgressStore(context) }
    var reminder by remember(uid) { mutableStateOf(uid?.let(store::enabled) == true) }
    var minute by remember(uid) { mutableIntStateOf(uid?.let(store::minute) ?: 1080) }
    val today = LocalDate.now()
    val dates = QaProgressPolicy.dates(today)
    BackHandler { onBack() }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp,vertical=12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SeqTopBar("My QA Progress",onBack)
            Text("Local testing activity for the last 14 calendar days. PASS and confirmed FAIL count as testing. This is not Google Play enrollment verification.", color=SeqColors.Muted,fontSize=12.sp)
        }
        if (uid == null) item { Text("Sign in to view joined-app progress.",color=SeqColors.Muted) }
        else {
            item {
                SeqCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text("Daily reminder",Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
                            Switch(checked=reminder,onCheckedChange={ value ->
                                reminder=value; store.setEnabled(uid,value); QaReminderScheduler.reconcile(context)
                                if (value && !notificationsGranted) requestNotifications()
                            })
                        }
                        SeqTextButton(onClick={
                            TimePickerDialog(context,{ _,h,m ->
                                minute=h*60+m;store.setMinute(uid,minute);QaReminderScheduler.reconcile(context)
                            },minute/60,minute%60,true).show()
                        },enabled=reminder) { Text(String.format(Locale.ROOT,"Reminder time %02d:%02d",minute/60,minute%60)) }
                        Text("Approximate local time; Android may delay delivery. Only if an installed joined app has not been tested today.",color=SeqColors.Muted,fontSize=11.sp)
                        if (reminder && !notificationsGranted) SeqTextButton(onClick=requestNotifications) { Text("Allow notifications") }
                    }
                }
            }
            if (joined == null) item { Text("Waiting for verified joined apps. Reopen App List or reconnect.",color=SeqColors.Muted,fontSize=12.sp) }
            else if (joined.isEmpty()) item { Text("No joined published apps.",color=SeqColors.Muted) }
            else items(joined,key={it.packageName}) { app ->
                val days = remember(uid,app.packageName,revision,today) { store.days(uid,app.packageName) }
                val count = dates.count { it in days }
                SeqCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(app.name,Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
                            Text("$count / 14 days",color=SeqColors.Teal,fontSize=12.sp)
                        }
                        LinearProgressIndicator(progress={count/14f},modifier=Modifier.fillMaxWidth(),color=SeqColors.Teal)
                        dates.chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                week.forEach { day ->
                                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp))
                                        .background(if(day in days) SeqColors.Success.copy(alpha=.22f) else SeqColors.CardRaised),
                                        contentAlignment=Alignment.Center) {
                                        Text(day.takeLast(2),color=if(day in days) SeqColors.Success else SeqColors.Muted,fontSize=12.sp)
                                    }
                                }
                            }
                        }
                        Text(if(today.toString() in days) "Tested today" else if(installed.none { it.packageName == app.packageName }) "Not installed" else "Not tested today",fontSize=12.sp,color=if(today.toString() in days) SeqColors.Success else SeqColors.Muted)
                    }
                }
            }
        }
    }
}
