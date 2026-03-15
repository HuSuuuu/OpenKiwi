package com.orizon.openkiwi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.text.TextUtils
import androidx.core.app.NotificationCompat

object SuperIslandService {
    private const val CHANNEL_ID = "ai_task_focus"
    private const val NOTIFICATION_ID = 2002
    private var nm: NotificationManager? = null

    fun isMiuiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        if (manufacturer != "xiaomi" && manufacturer != "redmi") return false
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            val value = method.invoke(null, "ro.miui.ui.version.name") as? String
            value.isNullOrEmpty().not()
        } catch (e: Exception) { false }
    }

    private fun ensureChannel(context: Context) {
        if (nm == null) nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "任务执行状态", NotificationManager.IMPORTANCE_LOW).apply {
                description = "显示 AI 任务执行进度"; setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm?.createNotificationChannel(channel)
        }
    }

    fun showTaskProgress(context: Context, taskName: String, progress: Float, statusText: String) {
        ensureChannel(context)
        val progressInt = (progress * 100).toInt()
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = intent?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("🤖 $taskName").setContentText(statusText)
            .setProgress(100, progressInt, false)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pi).build()
        nm?.notify(NOTIFICATION_ID, n)
    }

    fun showTaskComplete(context: Context, message: String, success: Boolean = true) {
        ensureChannel(context)
        val icon = if (success) android.R.drawable.ic_dialog_info else android.R.drawable.ic_dialog_alert
        val title = if (success) "✅ 任务完成" else "❌ 任务失败"
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon).setContentTitle(title).setContentText(message)
            .setAutoCancel(true).setTimeoutAfter(5000).build()
        nm?.notify(NOTIFICATION_ID, n)
    }

    fun dismiss(context: Context) {
        if (nm == null) nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm?.cancel(NOTIFICATION_ID)
    }
}
