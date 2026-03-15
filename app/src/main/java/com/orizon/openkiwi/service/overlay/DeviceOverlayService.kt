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

    override val overlayTitle = "📡 设备"
    override val notificationId = 3005
    override val overlayColor = 0xF01B2838.toInt()
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
                textSize = 10f
                setTextColor(0xFF6B7280.toInt())
                text = "${getIcon(type)} ${type.name}: 未连接"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(2) }
            }
            connectionViews[type] = tv
            container.addView(tv)
        }
    }

    private fun setConnectionStatus(type: ConnectionType, status: ConnectionStatus, info: String) {
        post {
            val tv = connectionViews[type] ?: return@post
            val (icon, label, color) = when (status) {
                ConnectionStatus.CONNECTED -> Triple("●", "已连接", 0xFF3FB950.toInt())
                ConnectionStatus.DISCONNECTED -> Triple("○", "未连接", 0xFF6B7280.toInt())
                ConnectionStatus.CONNECTING -> Triple("◌", "连接中...", 0xFFFBBF24.toInt())
                ConnectionStatus.ERROR -> Triple("✗", "错误", 0xFFFF7B72.toInt())
            }
            val extra = if (info.isNotBlank()) " ($info)" else ""
            tv.text = "${getIcon(type)} ${type.name}: $icon $label$extra"
            tv.setTextColor(color)
        }
    }

    private fun getIcon(type: ConnectionType): String = when (type) {
        ConnectionType.USB -> "🔌"
        ConnectionType.SSH -> "🖥"
        ConnectionType.VNC -> "🖼"
        ConnectionType.COMPANION -> "📱"
        ConnectionType.WIFI -> "📶"
    }
}
