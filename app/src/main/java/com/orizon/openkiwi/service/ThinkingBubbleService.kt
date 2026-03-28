package com.orizon.openkiwi.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView

class ThinkingBubbleService : Service() {

    companion object {
        @Volatile private var instance: ThinkingBubbleService? = null
        fun isRunning(): Boolean = instance != null

        fun start(context: Context) {
            try { context.startService(Intent(context, ThinkingBubbleService::class.java)) } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            instance?.destroySelf()
            try { context.stopService(Intent(context, ThinkingBubbleService::class.java)) } catch (_: Exception) {}
        }

        fun updateThinking(text: String) { instance?.showThinking(text) }
        fun showStep(step: Int, action: String) { instance?.showThinking("[$step] $action") }

        fun hide() {
            instance?.let { svc ->
                svc.mainHandler.post {
                    svc.removeBubbleView()
                    svc.stopSelf()
                }
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var textView: TextView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var initialX = 0; private var initialY = 0
    private var initialTouchX = 0f; private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createBubble()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        super.onDestroy()
        removeBubbleView()
        instance = null
    }

    private fun destroySelf() {
        mainHandler.post {
            removeBubbleView()
            stopSelf()
        }
    }

    private fun removeBubbleView() {
        bubbleView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        bubbleView = null
        textView = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createBubble() {
        val dp = resources.displayMetrics.density
        val isDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        val bgColor = if (isDark) 0xFF161616.toInt() else 0xFFF9F8F6.toInt()
        val borderColor = if (isDark) 0xFF333333.toInt() else 0xFFE2E0DC.toInt()
        val txtColor = if (isDark) 0xFF999999.toInt() else 0xFF6E6E6E.toInt()

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = 8 * dp
                setStroke((1 * dp).toInt(), borderColor)
            }
            elevation = 4 * dp
        }

        val spinner = ProgressBar(this, null, android.R.attr.progressBarStyleSmall).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams((14 * dp).toInt(), (14 * dp).toInt()).apply {
                rightMargin = (8 * dp).toInt()
            }
        }
        container.addView(spinner)

        textView = TextView(this).apply {
            textSize = 13f
            setTextColor(txtColor)
            isSingleLine = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        container.addView(textView)

        bubbleView = container

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val displayHeight = resources.displayMetrics.heightPixels

        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (80 * dp).toInt()
        }

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = windowParams?.x ?: 0; initialY = windowParams?.y ?: 0
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    windowParams?.x = initialX + (event.rawX - initialTouchX).toInt()
                    windowParams?.y = initialY - (event.rawY - initialTouchY).toInt()
                    try { windowManager.updateViewLayout(bubbleView, windowParams) } catch (_: Exception) {}
                }
            }
            true
        }

        try { windowManager.addView(bubbleView, windowParams) } catch (_: Exception) {}
    }

    private fun showThinking(text: String) {
        mainHandler.post {
            textView?.text = text
        }
    }
}
