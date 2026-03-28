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

    override val overlayTitle = "\u901A\u77E5"
    override val notificationId = 3003
    override val overlayColor = 0xFF191919.toInt()
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
        val codeRegex = Regex("""(?:\u9A8C\u8BC1\u7801|code|\u7801)[\s:\uFF1A]*(\d{4,8})""", RegexOption.IGNORE_CASE)

        for (item in items.take(5)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, dp(4))
            }

            val contentCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val titleView = TextView(this).apply {
                text = "${item.app} \u00B7 ${item.title}"
                textSize = 12f
                setTextColor(textColor())
                maxLines = 1
            }
            contentCol.addView(titleView)

            val codeMatch = codeRegex.find(item.content)
            val bodyView = TextView(this).apply {
                text = if (codeMatch != null) "\u9A8C\u8BC1\u7801: ${codeMatch.groupValues[1]}" else item.content.take(80)
                textSize = 11f
                setTextColor(if (codeMatch != null) 0xFFFBBF24.toInt() else mutedColor())
                maxLines = 2
            }
            contentCol.addView(bodyView)

            row.addView(contentCol)

            val timeView = TextView(this).apply {
                text = item.time
                textSize = 10f
                setTextColor(mutedColor())
            }
            row.addView(timeView)

            listContainer?.addView(row)
        }
    }
}
