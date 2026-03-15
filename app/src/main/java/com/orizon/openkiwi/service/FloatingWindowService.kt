package com.orizon.openkiwi.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.orizon.openkiwi.MainActivity
import com.orizon.openkiwi.R

class FloatingWindowService : Service() {

    companion object {
        private const val CHANNEL_ID = "floating_channel"
        private const val NOTIFICATION_ID = 2001

        @Volatile
        private var instance: FloatingWindowService? = null

        fun isRunning(): Boolean = instance != null

        fun start(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingWindowService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var windowParams: WindowManager.LayoutParams? = null

    private var statusText: TextView? = null
    private var stepCounter: TextView? = null
    private var progressBar: ProgressBar? = null

    private var isMiniMode = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForegroundNotification()
        showFloatingWindow()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        hideFloatingWindow()
        instance = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "悬浮窗服务", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenKiwi 运行中")
            .setContentText("GUI Agent 悬浮窗已启动")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingWindow() {
        if (floatingView != null) return

        val dp = resources.displayMetrics.density

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF0FFFFFF.toInt())
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            elevation = 8 * dp
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "🤖 OpenKiwi"
            textSize = 13f
            setTextColor(0xFF111827.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleRow.addView(title)

        stepCounter = TextView(this).apply {
            text = ""
            textSize = 11f
            setTextColor(0xFF6B7280.toInt())
            visibility = View.GONE
        }
        titleRow.addView(stepCounter)

        val closeBtn = TextView(this).apply {
            text = " ✕ "
            textSize = 14f
            setTextColor(0xFFEF4444.toInt())
            setPadding((4 * dp).toInt(), 0, 0, 0)
            setOnClickListener { stopSelf() }
        }
        titleRow.addView(closeBtn)
        container.addView(titleRow)

        statusText = TextView(this).apply {
            text = "准备就绪"
            textSize = 11f
            setTextColor(0xFF6B7280.toInt())
            maxLines = 2
        }
        container.addView(statusText)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (3 * dp).toInt()).apply {
                topMargin = (4 * dp).toInt()
            }
        }
        container.addView(progressBar)

        floatingView = container

        container.setOnTouchListener(object : View.OnTouchListener {
            private var isClick = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val params = windowParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                        isClick = true; return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                            isClick = false
                            params.x = initialX + dx; params.y = initialY + dy
                            try { windowManager.updateViewLayout(floatingView, params) } catch (_: Exception) {}
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> { if (isClick) v.performClick(); return true }
                    else -> return false
                }
            }
        })

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100; y = 300
        }

        windowManager.addView(floatingView, windowParams)
    }

    private fun hideFloatingWindow() {
        floatingView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        floatingView = null
    }

    fun updateStatus(message: String) {
        statusText?.post { statusText?.text = message }
    }

    fun updateProgress(step: Int, maxSteps: Int) {
        stepCounter?.post {
            stepCounter?.text = "$step/$maxSteps"
            stepCounter?.visibility = View.VISIBLE
            progressBar?.visibility = View.VISIBLE
            progressBar?.max = maxSteps
            progressBar?.progress = step
        }
    }

    fun resetProgress() {
        stepCounter?.post {
            stepCounter?.visibility = View.GONE
            progressBar?.visibility = View.GONE
            statusText?.text = "准备就绪"
        }
    }
}
