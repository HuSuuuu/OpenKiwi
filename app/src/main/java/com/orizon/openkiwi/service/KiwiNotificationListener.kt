package com.orizon.openkiwi.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.orizon.openkiwi.OpenKiwiApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KiwiNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return

        val info = extractNotificationInfo(sbn)
        val current = _notifications.value.toMutableList()
        current.add(0, info)
        if (current.size > MAX_CACHED) current.removeAt(current.lastIndex)
        _notifications.value = current
        _latestNotification.value = info

        val code = extractVerificationCode(info.title) ?: extractVerificationCode(info.text)
        if (code != null) {
            _latestVerificationCode.value = VerificationCode(code, info.packageName, System.currentTimeMillis())
            copyToClipboard(code)
            Log.d(TAG, "Verification code [$code] from ${info.packageName} → clipboard")
        }

        val category = classifyNotification(info)
        val enrichedInfo = info.copy(category = category)
        val enrichedList = _notifications.value.toMutableList()
        if (enrichedList.isNotEmpty()) enrichedList[0] = enrichedInfo
        _notifications.value = enrichedList

        runCatching { OpenKiwiApp.instance.container.notificationProcessor.process(enrichedInfo) }
        runCatching { OpenKiwiApp.instance.container.autoReplyManager.maybeReplyAsync(sbn, enrichedInfo) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        val current = _notifications.value.toMutableList()
        current.removeAll { it.key == sbn.key }
        _notifications.value = current
    }

    override fun onListenerConnected() {
        _isRunning.value = true
    }

    override fun onListenerDisconnected() {
        _isRunning.value = false
    }

    private fun extractNotificationInfo(sbn: StatusBarNotification): NotificationInfo {
        val extras = sbn.notification.extras
        var title = extras?.getCharSequence("android.title")?.toString() ?: ""
        var text = extras?.getCharSequence("android.text")?.toString() ?: ""

        val bigText = extras?.getCharSequence("android.bigText")?.toString()
        if (!bigText.isNullOrBlank()) {
            text = bigText
        }

        val messages = extras?.getParcelableArray("android.messages")
        if (messages != null && messages.isNotEmpty()) {
            val lastMessage = messages.last() as? android.os.Bundle
            if (lastMessage != null) {
                val msgText = lastMessage.getCharSequence("text")?.toString()
                if (!msgText.isNullOrBlank()) {
                    text = msgText
                }
                val sender = lastMessage.getCharSequence("sender")?.toString()
                if (!sender.isNullOrBlank() && title.isBlank()) {
                    title = sender
                }
            }
        }

        val textLines = extras?.getCharSequenceArray("android.textLines")
        if (textLines != null && textLines.isNotEmpty() && text.isBlank()) {
            text = textLines.joinToString("\n")
        }

        if (title.isBlank() && text.isBlank()) {
            val tickerText = sbn.notification.tickerText?.toString()
            if (!tickerText.isNullOrBlank()) {
                text = tickerText
            }
        }

        return NotificationInfo(
            key = sbn.key,
            packageName = sbn.packageName,
            title = title,
            text = text,
            subText = extras?.getCharSequence("android.subText")?.toString() ?: "",
            postTime = sbn.postTime,
            isOngoing = sbn.isOngoing,
            isClearable = sbn.isClearable
        )
    }

    private fun extractVerificationCode(text: String): String? {
        if (text.isBlank()) return null
        val patterns = listOf(
            Regex("""(?:验证码|校验码|确认码|动态码|安全码|code|Code|CODE)[\s:：为是]*(\d{4,8})"""),
            Regex("""(\d{4,8})[\s]*(?:是你的|为你的|验证码|校验码)"""),
            Regex("""(?:code|Code|CODE)\s*(?:is|:)\s*(\d{4,8})""")
        )
        for (pattern in patterns) {
            pattern.find(text)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
    }

    private fun classifyNotification(info: NotificationInfo): String {
        val combined = "${info.title} ${info.text}".lowercase()
        return when {
            extractVerificationCode(info.title) != null || extractVerificationCode(info.text) != null -> "verification"
            combined.containsAny("快递", "包裹", "取件", "签收", "配送", "delivery") -> "delivery"
            combined.containsAny("日程", "提醒", "会议", "schedule", "reminder", "calendar") -> "reminder"
            combined.containsAny("转账", "支付", "到账", "扣款", "payment", "transaction") -> "finance"
            combined.containsAny("来电", "未接", "missed call") -> "call"
            info.isOngoing -> "ongoing"
            else -> "general"
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { contains(it, ignoreCase = true) }

    private fun copyToClipboard(text: String) {
        Handler(Looper.getMainLooper()).post {
            runCatching {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("verification_code", text))
            }
        }
    }

    fun getActiveNotificationInfos(): List<NotificationInfo> {
        return runCatching {
            activeNotifications?.map { extractNotificationInfo(it) } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun dismissNotification(key: String) {
        runCatching { cancelNotification(key) }
    }

    fun getNotificationsByCategory(category: String): List<NotificationInfo> {
        return _notifications.value.filter { it.category == category }
    }

    companion object {
        private const val TAG = "KiwiNotificationListener"
        private const val MAX_CACHED = 200

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _notifications = MutableStateFlow<List<NotificationInfo>>(emptyList())
        val notifications: StateFlow<List<NotificationInfo>> = _notifications.asStateFlow()

        private val _latestNotification = MutableStateFlow<NotificationInfo?>(null)
        val latestNotification: StateFlow<NotificationInfo?> = _latestNotification.asStateFlow()

        private val _latestVerificationCode = MutableStateFlow<VerificationCode?>(null)
        val latestVerificationCode: StateFlow<VerificationCode?> = _latestVerificationCode.asStateFlow()
    }
}

data class VerificationCode(
    val code: String,
    val fromPackage: String,
    val timestamp: Long
)

data class NotificationInfo(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val subText: String = "",
    val postTime: Long,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    val category: String = "general"
)
