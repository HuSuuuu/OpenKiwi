package com.orizon.openkiwi.service.overlay

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView

class DeviceOverlayService : OverlayWindowManager() {

    companion object {
        @Volatile
        private var instance: DeviceOverlayService? = null

        fun isRunning(): Boolean = instance != null
        fun start(context: Context) = ManagerCompanion.start(context, DeviceOverlayService::class.java)
        fun stop(context: Context) = ManagerCompanion.stop(context, DeviceOverlayService::class.java)

        fun updateConnection(type: ConnectionType, status: ConnectionStatus, info: String = "") {
            instance?.setConnectionStatus(type, status, info)
        }
    }

    enum class ConnectionType { USB, SSH, VNC, COMPANION, WIFI }
    enum class ConnectionStatus { CONNECTED, DISCONNECTED, CONNECTING, ERROR }

    override val overlayTitle = "\u8BBE\u5907"
    override val notificationId = 3005
    override val overlayColor = 0xFF191919.toInt()
    override val initialYPosition = 800

    private val connectionViews = mutableMapOf<ConnectionType, TextView>()

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onOverlayDestroy() { instance = null }

    override fun onCreateContent(container: LinearLayout) {
        for (type in ConnectionType.entries) {
            val tv = TextView(this).apply {
                textSize = 12f
                setTextColor(mutedColor())
                text = "${type.name}: \u672A\u8FDE\u63A5"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(3) }
            }
            connectionViews[type] = tv
            container.addView(tv)
        }
    }

    private fun setConnectionStatus(type: ConnectionType, status: ConnectionStatus, info: String) {
        post {
            val tv = connectionViews[type] ?: return@post
            val (dot, label, color) = when (status) {
                ConnectionStatus.CONNECTED -> Triple("\u25CF", "\u5DF2\u8FDE\u63A5", 0xFF3FB950.toInt())
                ConnectionStatus.DISCONNECTED -> Triple("\u25CB", "\u672A\u8FDE\u63A5", mutedColor())
                ConnectionStatus.CONNECTING -> Triple("\u25CB", "\u8FDE\u63A5\u4E2D...", 0xFFFBBF24.toInt())
                ConnectionStatus.ERROR -> Triple("\u25CF", "\u9519\u8BEF", 0xFFFF7B72.toInt())
            }
            val extra = if (info.isNotBlank()) " ($info)" else ""
            tv.text = "$dot ${type.name}: $label$extra"
            tv.setTextColor(color)
        }
    }
}
