package com.orizon.openkiwi.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.orizon.openkiwi.MainActivity
import com.orizon.openkiwi.OpenKiwiApp
import com.orizon.openkiwi.R

class AgentForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundWithNotification()
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, OpenKiwiApp.CHANNEL_AGENT_SERVICE)
            .setContentTitle("OpenKiwi Agent")
            .setContentText("Agent is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.orizon.openkiwi.START_AGENT"
        const val ACTION_STOP = "com.orizon.openkiwi.STOP_AGENT"
        private const val NOTIFICATION_ID = 1001
    }
}
