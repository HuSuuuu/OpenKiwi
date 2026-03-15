package com.orizon.openkiwi.service.overlay

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView

class MemoryOverlayService : OverlayWindowManager() {

    companion object {
        @Volatile
        private var instance: MemoryOverlayService? = null

        fun isRunning(): Boolean = instance != null
        fun start(context: Context) = ManagerCompanion.start(context, MemoryOverlayService::class.java)
        fun stop(context: Context) = ManagerCompanion.stop(context, MemoryOverlayService::class.java)

        fun updateTokenUsage(used: Int, max: Int) {
            instance?.setTokenUsage(used, max)
        }

        fun updateMemoryItems(items: List<String>) {
            instance?.setMemoryItems(items)
        }

        fun updateCompressionStatus(status: String) {
            instance?.setCompression(status)
        }
    }

    override val overlayTitle = "🧠 记忆"
    override val notificationId = 3004
    override val overlayColor = 0xF01E293B.toInt()
    override val initialYPosition = 650

    private var tokenLabel: TextView? = null
    private var tokenBar: TextView? = null
    private var memoryList: LinearLayout? = null
    private var compressionLabel: TextView? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onOverlayDestroy() { instance = null }

    override fun onCreateContent(container: LinearLayout) {
        tokenLabel = TextView(this).apply {
            textSize = 10f
            setTextColor(0xFFD1D5DB.toInt())
            text = "上下文 Token: -"
        }
        container.addView(tokenLabel)

        tokenBar = TextView(this).apply {
            textSize = 9f
            setTextColor(0xFF3FB950.toInt())
            text = ""
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) }
        }
        container.addView(tokenBar)

        val label = TextView(this).apply {
            textSize = 10f
            setTextColor(0xFF8B949E.toInt())
            text = "最近检索记忆:"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }
        container.addView(label)

        memoryList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(memoryList)

        compressionLabel = TextView(this).apply {
            textSize = 9f
            setTextColor(0xFF6B7280.toInt())
            text = ""
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        container.addView(compressionLabel)
    }

    private fun setTokenUsage(used: Int, max: Int) {
        post {
            tokenLabel?.text = "上下文 Token: $used / $max"
            val ratio = if (max > 0) used.toFloat() / max else 0f
            val barLen = (ratio * 20).toInt().coerceIn(0, 20)
            val color = when {
                ratio > 0.9f -> 0xFFFF7B72.toInt()
                ratio > 0.7f -> 0xFFFBBF24.toInt()
                else -> 0xFF3FB950.toInt()
            }
            tokenBar?.text = "▓".repeat(barLen) + "░".repeat(20 - barLen) + " ${(ratio * 100).toInt()}%"
            tokenBar?.setTextColor(color)
        }
    }

    private fun setMemoryItems(items: List<String>) {
        post {
            memoryList?.removeAllViews()
            for (item in items.take(5)) {
                val tv = TextView(this).apply {
                    text = "• ${item.take(40)}"
                    textSize = 9f
                    setTextColor(0xFFB0B0B0.toInt())
                    maxLines = 1
                }
                memoryList?.addView(tv)
            }
        }
    }

    private fun setCompression(status: String) {
        post {
            compressionLabel?.text = status
        }
    }
}
