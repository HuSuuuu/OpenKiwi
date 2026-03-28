package com.orizon.openkiwi.service.overlay

import android.content.Context
import android.graphics.Typeface
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.orizon.openkiwi.OpenKiwiApp

class TerminalOverlayService : OverlayWindowManager() {

    companion object {
        @Volatile
        private var instance: TerminalOverlayService? = null
        @Volatile
        private var userDismissed = false

        fun isRunning(): Boolean = instance != null

        fun start(context: Context) {
            userDismissed = false
            ManagerCompanion.start(context, TerminalOverlayService::class.java)
        }
        fun stop(context: Context) {
            userDismissed = true
            ManagerCompanion.stop(context, TerminalOverlayService::class.java)
        }

        private fun ensureStarted() {
            if (instance != null || userDismissed) return
            try {
                val ctx = OpenKiwiApp.instance.applicationContext
                if (Settings.canDrawOverlays(ctx)) {
                    ManagerCompanion.start(ctx, TerminalOverlayService::class.java)
                }
            } catch (_: Exception) {}
        }

        fun appendOutput(text: String, isError: Boolean = false) {
            ensureStarted()
            instance?.appendToView(text, isError)
        }

        fun setCommand(command: String) {
            ensureStarted()
            instance?.showCommand(command)
        }

        fun setStatus(status: ExecutionStatus) {
            instance?.updateExecStatus(status)
        }

        fun resetDismissed() { userDismissed = false }
    }

    data class TerminalOutput(val text: String, val isError: Boolean = false)
    enum class ExecutionStatus { RUNNING, SUCCESS, FAILED }

    override val overlayTitle = "\u7EC8\u7AEF"
    override val notificationId = 3001
    override val overlayColor = 0xFF191919.toInt()
    override val initialYPosition = 200

    private var commandText: TextView? = null
    private var outputScroll: ScrollView? = null
    private var outputText: TextView? = null
    private var statusText: TextView? = null
    private val outputBuffer = StringBuilder()

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onOverlayDestroy() {
        userDismissed = true
        instance = null
    }

    override fun onCreateContent(container: LinearLayout) {
        commandText = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(mutedColor())
            text = "$ "
            maxLines = 2
        }
        container.addView(commandText)

        outputScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(120)
            ).apply { topMargin = dp(4) }
        }
        outputText = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(textColor())
            text = ""
        }
        outputScroll!!.addView(outputText)
        container.addView(outputScroll)

        statusText = TextView(this).apply {
            textSize = 11f
            setTextColor(mutedColor())
            text = "\u5C31\u7EEA"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        container.addView(statusText)
    }

    private fun showCommand(cmd: String) {
        post {
            commandText?.text = "$ $cmd"
            outputBuffer.clear()
            outputText?.text = ""
        }
    }

    private fun appendToView(text: String, isError: Boolean) {
        post {
            outputBuffer.append(text)
            if (outputBuffer.length > 5000) {
                outputBuffer.delete(0, outputBuffer.length - 4000)
            }
            outputText?.text = outputBuffer.toString()
            if (isError) outputText?.setTextColor(0xFFFF7B72.toInt())
            outputScroll?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun updateExecStatus(status: ExecutionStatus) {
        post {
            when (status) {
                ExecutionStatus.RUNNING -> {
                    statusText?.text = "● 运行中..."
                    statusText?.setTextColor(0xFF3FB950.toInt())
                }
                ExecutionStatus.SUCCESS -> {
                    statusText?.text = "✓ 完成"
                    statusText?.setTextColor(0xFF58A6FF.toInt())
                }
                ExecutionStatus.FAILED -> {
                    statusText?.text = "✗ 失败"
                    statusText?.setTextColor(0xFFFF7B72.toInt())
                }
            }
        }
    }
}
