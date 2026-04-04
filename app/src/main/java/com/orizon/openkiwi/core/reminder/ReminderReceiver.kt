package com.orizon.openkiwi.core.reminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.orizon.openkiwi.MainActivity
import com.orizon.openkiwi.OpenKiwiApp
import com.orizon.openkiwi.R
import com.orizon.openkiwi.core.agent.ProactiveMessage
import com.orizon.openkiwi.core.agent.ProactiveMessageBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderManager.ACTION_FIRE -> handleFire(context, intent)
            Intent.ACTION_BOOT_COMPLETED -> handleBoot(context)
        }
    }

    private fun handleFire(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderManager.EXTRA_REMINDER_ID) ?: return
        val message = intent.getStringExtra(ReminderManager.EXTRA_REMINDER_MSG) ?: "提醒"
        val sessionId = intent.getStringExtra(ReminderManager.EXTRA_SESSION_ID)

        Log.i(TAG, "Reminder fired: id=$id msg=$message")

        postNotification(context, id, message)

        ProactiveMessageBus.emit(
            ProactiveMessage(
                sessionId = sessionId,
                content = message,
                source = ProactiveMessage.Source.SCHEDULE
            )
        )

        scope.launch {
            try {
                val app = context.applicationContext as? OpenKiwiApp ?: return@launch
                val reminderManager = ReminderManager(context, app.container.database.reminderDao())
                reminderManager.onFired(id)

                val engine = app.container.agentEngine
                engine.sendProactiveMessage(sessionId, "⏰ 提醒: $message", ProactiveMessage.Source.SCHEDULE)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process reminder fire", e)
            }
        }
    }

    private fun handleBoot(context: Context) {
        scope.launch {
            try {
                val app = context.applicationContext as? OpenKiwiApp ?: return@launch
                val reminderManager = ReminderManager(context, app.container.database.reminderDao())
                reminderManager.rescheduleAll()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule after boot", e)
            }
        }
    }

    private fun postNotification(context: Context, id: String, message: String) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, id.hashCode(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, OpenKiwiApp.CHANNEL_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⏰ Kiwi 提醒")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id.hashCode(), notification)
    }

    companion object {
        private const val TAG = "ReminderReceiver"
    }
}
