package com.sequmio.givlse.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.sequmio.givlse.MainActivity
import com.sequmio.givlse.model.RunPhase
import com.sequmio.givlse.runner.RunnerRuntime
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

internal object QaReminderScheduler {
    private const val WORK = "qa_progress_reminder"
    const val CHANNEL = "qa_progress_reminder"
    const val ID = 415
    fun reconcile(context: Context) {
        val uid = QaLocalAccount.uid(context)
        val manager = WorkManager.getInstance(context.applicationContext)
        if (uid == null || !QaProgressStore(context).enabled(uid)) {
            manager.cancelUniqueWork(WORK)
            context.getSystemService(NotificationManager::class.java).cancel(ID)
        } else {
            manager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<QaReminderWorker>(15, TimeUnit.MINUTES).build())
        }
    }
}
class QaReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val uid = QaLocalAccount.uid(applicationContext) ?: return Result.success()
        val store = QaProgressStore(applicationContext)
        if (!store.enabled(uid)) return Result.success()
        if (RunnerRuntime.state.value.phase in listOf(RunPhase.RUNNING,RunPhase.PAUSED)) return Result.success()
        val day = LocalDate.now().toString()
        val now = LocalTime.now()
        val pending = store.pending(uid,day).intersect(AppCatalog.list(applicationContext).map { it.packageName }.toSet())
        if (!QaProgressPolicy.due(now.hour*60+now.minute,store.minute(uid),day,store.lastSent(uid),pending.size))
            return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled() || QaLocalAccount.uid(applicationContext) != uid) return Result.success()
        manager.createNotificationChannel(NotificationChannel(QaReminderScheduler.CHANNEL,"QA reminders",NotificationManager.IMPORTANCE_DEFAULT))
        if (manager.getNotificationChannel(QaReminderScheduler.CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE)
            return Result.success()
        val open = PendingIntent.getActivity(applicationContext,415,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(QaReminderScheduler.ID, NotificationCompat.Builder(applicationContext,QaReminderScheduler.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("Sequmio daily QA")
            .setContentText("You have ${pending.size} joined apps not tested today.")
            .setContentIntent(open).setAutoCancel(true).build())
        store.markSent(uid,day)
        return Result.success()
    }
}
