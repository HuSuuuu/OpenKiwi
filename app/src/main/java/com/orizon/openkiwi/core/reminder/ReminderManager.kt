package com.orizon.openkiwi.core.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.orizon.openkiwi.data.local.dao.ReminderDao
import com.orizon.openkiwi.data.local.entity.ReminderEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ReminderManager(
    private val context: Context,
    private val reminderDao: ReminderDao
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun schedule(
        message: String,
        triggerAtMs: Long,
        repeatIntervalMs: Long = 0,
        sessionId: String? = null,
        id: String = UUID.randomUUID().toString().take(12)
    ): ReminderEntity {
        val entity = ReminderEntity(
            id = id,
            message = message,
            triggerAtMs = triggerAtMs,
            repeatIntervalMs = repeatIntervalMs,
            sessionId = sessionId,
            enabled = true
        )
        reminderDao.insert(entity)
        setAlarm(entity)
        Log.i(TAG, "Scheduled reminder '$id': $message at ${formatTime(triggerAtMs)}")
        return entity
    }

    suspend fun cancel(id: String): Boolean {
        val entity = reminderDao.getById(id) ?: return false
        cancelAlarm(entity)
        reminderDao.deleteById(id)
        Log.i(TAG, "Cancelled reminder '$id'")
        return true
    }

    suspend fun listAll(): List<ReminderEntity> = reminderDao.getAllOnce()

    suspend fun listEnabled(): List<ReminderEntity> = reminderDao.getAllEnabled()

    suspend fun getById(id: String): ReminderEntity? = reminderDao.getById(id)

    suspend fun onFired(id: String) {
        val entity = reminderDao.getById(id) ?: return
        reminderDao.incrementFired(id)

        if (entity.repeatIntervalMs > 0) {
            val next = entity.copy(
                triggerAtMs = System.currentTimeMillis() + entity.repeatIntervalMs,
                firedCount = entity.firedCount + 1
            )
            reminderDao.update(next)
            setAlarm(next)
            Log.i(TAG, "Rescheduled repeating reminder '$id' for ${formatTime(next.triggerAtMs)}")
        } else {
            reminderDao.update(entity.copy(enabled = false))
            Log.i(TAG, "One-shot reminder '$id' fired and disabled")
        }
    }

    suspend fun rescheduleAll() {
        val enabled = reminderDao.getAllEnabled()
        enabled.forEach { setAlarm(it) }
        Log.i(TAG, "Rescheduled ${enabled.size} alarms after boot")
    }

    suspend fun cleanup() {
        reminderDao.deleteExpired(System.currentTimeMillis() - 7 * 86400_000L)
    }

    private fun setAlarm(entity: ReminderEntity) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_REMINDER_ID, entity.id)
            putExtra(EXTRA_REMINDER_MSG, entity.message)
            putExtra(EXTRA_SESSION_ID, entity.sessionId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            entity.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entity.triggerAtMs, pi)
        } else {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entity.triggerAtMs, pi)
            } catch (e: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entity.triggerAtMs, pi)
            }
        }
    }

    private fun cancelAlarm(entity: ReminderEntity) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE
        }
        val pi = PendingIntent.getBroadcast(
            context,
            entity.id.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let { alarmManager.cancel(it) }
    }

    companion object {
        private const val TAG = "ReminderManager"
        const val ACTION_FIRE = "com.orizon.openkiwi.REMINDER_FIRE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_REMINDER_MSG = "reminder_msg"
        const val EXTRA_SESSION_ID = "session_id"

        fun formatTime(ms: Long): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ms))
    }
}
