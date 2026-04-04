package com.orizon.openkiwi.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.orizon.openkiwi.OpenKiwiApp
import com.orizon.openkiwi.core.reminder.ReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            scope.launch {
                try {
                    val app = context.applicationContext as? OpenKiwiApp ?: return@launch
                    val reminderManager = ReminderManager(context, app.container.database.reminderDao())
                    reminderManager.rescheduleAll()
                    Log.i("BootReceiver", "Reminders rescheduled after boot")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to reschedule reminders", e)
                }
            }
        }
    }
}
