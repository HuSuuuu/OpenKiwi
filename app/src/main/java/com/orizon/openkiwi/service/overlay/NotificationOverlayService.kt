package com.orizon.openkiwi.service.overlay

import android.content.Context
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.orizon.openkiwi.OpenKiwiApp
import java.text.SimpleDateFormat
import java.util.*

class NotificationOverlayService : OverlayWindowManager() {

    companion object {
        @Volatile
        private var instance: NotificationOverlayService? = null
        @Volatile
        private var userDismissed = false

        fun isRunning(): Boolean = instance != null
        fun start(context: Context) {
            userDismissed = false
            ManagerCompanion.start(context, NotificationOverlayService::class.java)
        }
        fun stop(context: Context) {
            userDismissed = true
            ManagerCompanion.stop(context, NotificationOverlayService::class.java)
        }

        private fun ensureStarted() {
            if (instance != null || userDismissed) return
            try {
                val ctx = OpenKiwiApp.instance.applicationContext
                if (Settings.canDrawOverlays(ctx)) {
                    ManagerCompanion.start(ctx, NotificationOverlayService::class.java)
                }
            } catch (_: Exception) {}
        }

        fun onNotification(app: String, title: String, content: String) {
            ensureStarted()
            instance?.addNotification(app, title, content)
        }

        fun resetDismissed() { userDismissed = false }
    }

    override val overlayTitle = "🔔 通知"
    override val notificationId = 3003
    override val overlayColor = 0xF0162447.toInt()
    override val initialYPosition = 500

    private var scrollView: ScrollView? = null
    private var listContainer: LinearLayout? = null
    private val items = mutableListOf<NotifItem>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    data class NotifItem(val app: String, val title: String, val content: String, val time: String)

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onOverlayDestroy() {
        userDismissed = true
        instance = null
    }

    override fun onCreateContent(container: LinearLayout) {
        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(140)
            )
        }
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView!!.addView(listContainer)
        container.addView(scrollView)
    }

    private fun addNotification(app: String, title: String, content: String) {
        val item = NotifItem(app, title, content, timeFormat.format(Date()))
        items.add(0, item)
        if (items.size > 10) items.removeAt(items.lastIndex)

        post { rebuildList() }
    }

    private fun rebuildList() {
        listContainer?.removeAllViews()
        val codeRegex = Regex("""(?:验证码|code|码)[\s:：]*(\d{4,8})""", RegexOption.IGNORE_CASE)

        for (item in items.take(5)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }

            val header = TextView(this).apply {
                text = "${item.app}  ${item.time}"
                textSize = 9f
                setTextColor(0xFF6B7280.toInt())
            }
            row.addView(header)

            val titleView = TextView(this).apply {
                text = item.title
                textSize = 10f
                setTextColor(0xFFE0E0E0.toInt())
                maxLines = 1
            }
            row.addView(titleView)

            val codeMatch = codeRegex.find(item.content)
            val bodyView = TextView(this).apply {
                text = if (codeMatch != null) "📋 验证码: ${codeMatch.groupValues[1]}" else item.content.take(80)
                textSize = 10f
                setTextColor(if (codeMatch != null) 0xFFFBBF24.toInt() else 0xFFB0B0B0.toInt())
                maxLines = 2
            }
            row.addView(bodyView)

            listContainer?.addView(row)
        }
    }
}
