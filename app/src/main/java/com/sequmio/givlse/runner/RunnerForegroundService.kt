package com.sequmio.givlse.runner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.sequmio.givlse.MainActivity

/** User-initiated test foreground session. PARTIAL wake lock does not light the display. */
class RunnerForegroundService : Service() {
    companion object {
        private const val CHANNEL = "sequmio_active_test"
        private const val ID = 410
        private const val START = "com.sequmio.givlse.RUN_START"
        private const val STOP = "com.sequmio.givlse.RUN_STOP"

        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(
                context, Intent(context, RunnerForegroundService::class.java).setAction(START)
            )
            true
        } catch (_: RuntimeException) { false }

        fun stop(context: Context) {
            context.stopService(Intent(context, RunnerForegroundService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private val renewLock = object : Runnable {
        override fun run() {
            try {
                val lock = wakeLock ?: return
                if (!lock.isHeld) lock.acquire(10 * 60_000L)
                handler.postDelayed(this, 8 * 60_000L)
            } catch (_: RuntimeException) { }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            CycleAccessibilityService.instance?.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL, "Active Sequmio QA runs", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Active tests and Stop control." })
            val type = if (Build.VERSION.SDK_INT >= 34)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(ID, notification(), type)
            else
                startForeground(ID, notification())
            acquireCpuWakeLock()
        } catch (error: Exception) {
            CycleAccessibilityService.instance?.abort(
                "Failed to start foreground runner: " + error.javaClass.simpleName
            )
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, RunnerForegroundService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Sequmio test running")
            .setContentText("QA session active. Tap to view or stop.")
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop)
            .build()
    }

    private fun acquireCpuWakeLock() {
        val power = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sequmio:ActiveTest")
        wakeLock?.acquire(10 * 60_000L)
        handler.postDelayed(renewLock, 8 * 60_000L)
    }

    override fun onDestroy() {
        handler.removeCallbacks(renewLock)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }
}
