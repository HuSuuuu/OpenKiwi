package com.orizon.openkiwi.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.orizon.openkiwi.MainActivity
import com.orizon.openkiwi.R

/**
 * Foreground service: listens for clipboard changes and posts a notification with quick actions
 * (AI / search / translate) that forward text via [com.orizon.openkiwi.core.clipboard.ClipboardQuickBus].
 */
class ClipboardMonitorService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var clipboardManager: ClipboardManager? = null
    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var debounceRunnable: Runnable? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        debounceRunnable?.let { mainHandler.removeCallbacks(it) }
        debounceRunnable = Runnable { refreshNotificationFromClipboard() }
        mainHandler.postDelayed(debounceRunnable!!, 450)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildBaseNotification("监听剪贴板…").build())
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        listener = clipListener
        clipboardManager?.addPrimaryClipChangedListener(listener!!)
        refreshNotificationFromClipboard()
    }

    override fun onDestroy() {
        listener?.let { clipboardManager?.removePrimaryClipChangedListener(it) }
        debounceRunnable?.let { mainHandler.removeCallbacks(it) }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun refreshNotificationFromClipboard() {
        val text = readClipboardText().trim()
        val preview = if (text.isBlank()) "（空或非文本）" else text.take(180).let { if (text.length > 180) "$it…" else it }
        val nm = getSystemService(NotificationManager::class.java)
        val b = buildBaseNotification(preview)
        if (text.isNotBlank()) {
            b.setContentText(preview)
            b.setStyle(NotificationCompat.BigTextStyle().bigText(text.take(3500)))
            b.addAction(actionPending("analyze", text, "AI 分析", 1))
            b.addAction(actionPending("search", text, "搜索/摘要", 2))
            b.addAction(actionPending("translate", text, "翻译", 3))
        }
        nm.notify(NOTIFICATION_ID, b.build())
    }

    private fun readClipboardText(): String {
        return runCatching {
            val clip = clipboardManager?.primaryClip ?: return ""
            val item = clip.getItemAt(0) ?: return ""
            item.coerceToText(this).toString()
        }.getOrDefault("")
    }

    private fun actionPending(action: String, text: String, label: String, reqCode: Int): NotificationCompat.Action {
        val intent = Intent(this, ClipboardActionReceiver::class.java).apply {
            this.action = ClipboardActionReceiver.ACTION
            putExtra(ClipboardActionReceiver.EXTRA_CLIP_ACTION, action)
            putExtra(ClipboardActionReceiver.EXTRA_CLIP_TEXT, text.take(12_000))
        }
        val pi = PendingIntent.getBroadcast(
            this,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action(0, label, pi)
    }

    private fun buildBaseNotification(summary: String): NotificationCompat.Builder {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("OpenKiwi 剪贴板")
            .setContentText(summary)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "剪贴板监听",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    companion object {
        private const val CHANNEL_ID = "clipboard_monitor"
        private const val NOTIFICATION_ID = 7101

        fun start(context: Context) {
            val i = Intent(context, ClipboardMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ClipboardMonitorService::class.java))
        }
    }
}
