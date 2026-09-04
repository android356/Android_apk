package com.autodeploy.infinityfree.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.autodeploy.infinityfree.AutoDeployApplication
import com.autodeploy.infinityfree.MainActivity
import com.autodeploy.infinityfree.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class AutoSyncForegroundService : Service() {

    companion object {
        private const val TAG = "AutoSyncService"
        const val CHANNEL_ID = "auto_sync_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, AutoSyncForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AutoSyncForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var coordinator: SyncCoordinator

    override fun onCreate() {
        super.onCreate()
        val app = application as AutoDeployApplication
        coordinator = app.container.syncCoordinator
        createNotificationChannel()
        startForegroundWithNotification("Auto Sync Active - Monitoring")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startSyncLoop()
        return START_STICKY
    }

    private fun startSyncLoop() {
        serviceScope.launch {
            val app = application as AutoDeployApplication
            val prefs = app.container.preferences

            while (isActive) {
                val isEnabled = prefs.isAutoSyncEnabled.first()
                if (!isEnabled) {
                    Log.d(TAG, "Auto Sync turned OFF in preferences, stopping service")
                    stopSelf()
                    break
                }

                val intervalSeconds = prefs.reconciliationIntervalSeconds.first().coerceAtLeast(10)
                updateNotification("Reconciling project files...")
                coordinator.runReconciliationCycle()
                updateNotification("Auto Sync Active - Idle")

                delay(intervalSeconds * 1000L)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto Sync Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors and automatically syncs local files to GitHub and InfinityFree"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification(contentText: String) {
        val notification = buildNotification(contentText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Android Auto Deploy")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
