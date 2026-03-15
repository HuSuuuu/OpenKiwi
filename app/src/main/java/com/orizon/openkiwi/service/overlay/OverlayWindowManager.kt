package com.orizon.openkiwi.service.overlay

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.orizon.openkiwi.MainActivity
import com.orizon.openkiwi.R
import kotlin.math.abs

abstract class OverlayWindowManager : Service() {

    protected lateinit var windowManager: WindowManager
    protected var rootView: View? = null
    protected var windowParams: WindowManager.LayoutParams? = null
    protected var contentContainer: LinearLayout? = null
    protected var titleTextView: TextView? = null
    protected var isMiniMode = false

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    abstract val overlayTitle: String
    abstract val notificationId: Int
    abstract val overlayColor: Int
    abstract val initialYPosition: Int

    open fun onCreateContent(container: LinearLayout) {}
    open fun onOverlayDestroy() {}

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForegroundNotification()
        if (rootView == null) showOverlay()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        onOverlayDestroy()
        hideOverlay()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "悬浮窗服务", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenKiwi")
            .setContentText(overlayTitle)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        startForeground(notificationId, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        val dp = resources.displayMetrics.density

        val outerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(overlayColor)
            setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
            elevation = 8 * dp
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        titleTextView = TextView(this).apply {
            text = overlayTitle
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFE0E0E0.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleRow.addView(titleTextView)

        val miniBtn = TextView(this).apply {
            text = "  ─  "
            textSize = 14f
            setTextColor(0xFFAAAAAA.toInt())
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener { toggleMiniMode() }
        }
        titleRow.addView(miniBtn)

        val closeBtn = TextView(this).apply {
            text = "  ✕  "
            textSize = 14f
            setTextColor(0xFFEF4444.toInt())
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener { stopSelf() }
        }
        titleRow.addView(closeBtn)
        outerContainer.addView(titleRow)

        contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4 * dp).toInt() }
        }
        onCreateContent(contentContainer!!)
        outerContainer.addView(contentContainer)

        rootView = outerContainer

        // Drag via title text only; buttons remain clickable
        titleTextView!!.setOnTouchListener(object : View.OnTouchListener {
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
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            isClick = false
                            params.x = initialX + dx; params.y = initialY + dy
                            runCatching { windowManager.updateViewLayout(rootView, params) }
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
            (240 * dp).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20; y = initialYPosition
        }

        windowManager.addView(rootView, windowParams)
    }

    private fun hideOverlay() {
        rootView?.let { runCatching { windowManager.removeView(it) } }
        rootView = null
    }

    protected fun toggleMiniMode() {
        isMiniMode = !isMiniMode
        contentContainer?.visibility = if (isMiniMode) View.GONE else View.VISIBLE
    }

    protected fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    protected fun createLabel(text: String, color: Int = 0xFFCCCCCC.toInt(), size: Float = 11f): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
        }
    }

    protected fun createScrollableText(maxLines: Int = 8): Pair<ScrollView, TextView> {
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val tv = TextView(this).apply {
            textSize = 10f
            setTextColor(0xFFB0B0B0.toInt())
            this.maxLines = maxLines
        }
        scroll.addView(tv)
        return scroll to tv
    }

    protected fun post(action: () -> Unit) {
        rootView?.post(action)
    }

    companion object ManagerCompanion {
        private const val CHANNEL_ID = "overlay_channel"

        fun <T : OverlayWindowManager> start(context: Context, cls: Class<T>) {
            val intent = Intent(context, cls)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun <T : OverlayWindowManager> stop(context: Context, cls: Class<T>) {
            context.stopService(Intent(context, cls))
        }
    }
}
