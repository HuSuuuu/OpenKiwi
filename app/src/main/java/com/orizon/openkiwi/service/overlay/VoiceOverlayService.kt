package com.orizon.openkiwi.service.overlay

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class VoiceOverlayService : OverlayWindowManager() {

    companion object {
        @Volatile
        private var instance: VoiceOverlayService? = null

        fun isRunning(): Boolean = instance != null
        fun start(context: Context) = ManagerCompanion.start(context, VoiceOverlayService::class.java)
        fun stop(context: Context) = ManagerCompanion.stop(context, VoiceOverlayService::class.java)

        fun updateListeningState(isListening: Boolean, partialText: String = "") {
            instance?.setListeningState(isListening, partialText)
        }

        fun updatePlayingState(isPlaying: Boolean, text: String = "", progress: Int = 0) {
            instance?.setPlayingState(isPlaying, text, progress)
        }
    }

    override val overlayTitle = "\u8BED\u97F3"
    override val notificationId = 3002
    override val overlayColor = 0xFF191919.toInt()
    override val initialYPosition = 350

    private var stateLabel: TextView? = null
    private var contentText: TextView? = null
    private var progressBar: ProgressBar? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onOverlayDestroy() { instance = null }

    override fun onCreateContent(container: LinearLayout) {
        stateLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(mutedColor())
            text = "\u5F85\u547D"
        }
        container.addView(stateLabel)

        contentText = TextView(this).apply {
            textSize = 12f
            setTextColor(textColor())
            text = ""
            maxLines = 4
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        container.addView(contentText)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(2)
            ).apply { topMargin = dp(4) }
        }
        container.addView(progressBar)
    }

    private fun setListeningState(listening: Boolean, partial: String) {
        post {
            if (listening) {
                stateLabel?.text = "● 正在聆听..."
                stateLabel?.setTextColor(0xFF3FB950.toInt())
                contentText?.text = partial.ifBlank { "..." }
                progressBar?.visibility = View.GONE
            } else {
                stateLabel?.text = "待命"
                stateLabel?.setTextColor(0xFF8B5CF6.toInt())
            }
        }
    }

    private fun setPlayingState(playing: Boolean, text: String, progress: Int) {
        post {
            if (playing) {
                stateLabel?.text = "▶ 正在播放"
                stateLabel?.setTextColor(0xFF58A6FF.toInt())
                contentText?.text = text.take(100)
                progressBar?.visibility = View.VISIBLE
                progressBar?.progress = progress
            } else {
                stateLabel?.text = "待命"
                stateLabel?.setTextColor(0xFF8B5CF6.toInt())
                progressBar?.visibility = View.GONE
            }
        }
    }
}
