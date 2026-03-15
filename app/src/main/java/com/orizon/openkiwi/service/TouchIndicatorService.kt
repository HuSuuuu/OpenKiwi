package com.orizon.openkiwi.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator

class TouchIndicatorService : Service() {

    companion object {
        @Volatile private var instance: TouchIndicatorService? = null
        fun isRunning(): Boolean = instance != null

        fun showTap(x: Float, y: Float) { instance?.showTapIndicator(x.toInt(), y.toInt()) }
        fun showSwipe(sx: Float, sy: Float, ex: Float, ey: Float) { instance?.showSwipeIndicator(sx.toInt(), sy.toInt(), ex.toInt(), ey.toInt()) }

        fun start(context: Context) {
            try { context.startService(Intent(context, TouchIndicatorService::class.java)) } catch (_: Exception) {}
        }
        fun stop(context: Context) {
            try { context.stopService(Intent(context, TouchIndicatorService::class.java)) } catch (_: Exception) {}
        }
    }

    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onDestroy() { super.onDestroy(); instance = null }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun showTapIndicator(x: Int, y: Int) {
        mainHandler.post {
            try {
                val view = TapView(this, x, y)
                val params = overlayParams()
                windowManager.addView(view, params)
                view.startAnim { mainHandler.post { try { windowManager.removeView(view) } catch (_: Exception) {} } }
            } catch (_: Exception) {}
        }
    }

    private fun showSwipeIndicator(sx: Int, sy: Int, ex: Int, ey: Int) {
        mainHandler.post {
            try {
                val view = SwipeView(this, sx, sy, ex, ey)
                val params = overlayParams()
                windowManager.addView(view, params)
                view.startAnim { mainHandler.post { try { windowManager.removeView(view) } catch (_: Exception) {} } }
            } catch (_: Exception) {}
        }
    }

    private fun overlayParams() = WindowManager.LayoutParams().apply {
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        format = PixelFormat.TRANSLUCENT
    }

    private inner class TapView(context: Context, private val tx: Int, private val ty: Int) : View(context) {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 28f; textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
        }
        private var progress = 0f
        private var animator: ValueAnimator? = null

        fun startAnim(onDone: () -> Unit) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 600; interpolator = DecelerateInterpolator()
                addUpdateListener { progress = it.animatedValue as Float; invalidate() }
                addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { onDone() } })
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val alpha = ((1f - progress) * 255).toInt()
            val outerR = 30f + 50f * progress

            fillPaint.color = Color.argb((alpha * 0.4f).toInt(), 0, 122, 255) // Apple Blue
            canvas.drawCircle(tx.toFloat(), ty.toFloat(), outerR, fillPaint)

            val innerR = 24f * (1f - progress * 0.3f)
            fillPaint.color = Color.argb(alpha, 0, 122, 255)
            canvas.drawCircle(tx.toFloat(), ty.toFloat(), innerR, fillPaint)

            strokePaint.color = Color.argb(alpha, 255, 255, 255)
            strokePaint.strokeWidth = 2f
            canvas.drawCircle(tx.toFloat(), ty.toFloat(), innerR, strokePaint)

            if (progress < 0.5f) {
                textPaint.alpha = ((1f - progress * 2) * 255).toInt()
                canvas.drawText("($tx,$ty)", tx.toFloat(), ty - outerR - 12f, textPaint)
            }
        }

        override fun onDetachedFromWindow() { super.onDetachedFromWindow(); animator?.cancel() }
    }

    private inner class SwipeView(context: Context, private val sx: Int, private val sy: Int, private val ex: Int, private val ey: Int) : View(context) {
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 10f; strokeCap = Paint.Cap.ROUND }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 24f; textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
        }
        private var progress = 0f
        private var animator: ValueAnimator? = null

        fun startAnim(onDone: () -> Unit) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 600; interpolator = DecelerateInterpolator()
                addUpdateListener { progress = it.animatedValue as Float; invalidate() }
                addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { onDone() } })
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val alpha = ((1f - progress * 0.6f) * 255).toInt()
            val cx = sx + (ex - sx) * progress; val cy = sy + (ey - sy) * progress

            linePaint.color = Color.argb((alpha * 0.8f).toInt(), 0, 122, 255)
            canvas.drawLine(sx.toFloat(), sy.toFloat(), cx, cy, linePaint)

            dotPaint.color = Color.argb(alpha, 100, 150, 255)
            canvas.drawCircle(sx.toFloat(), sy.toFloat(), 16f, dotPaint)
            dotPaint.color = Color.argb(255, 0, 122, 255)
            canvas.drawCircle(cx, cy, 20f, dotPaint)

            if (progress < 0.4f) {
                textPaint.alpha = ((1f - progress * 2.5f) * 255).toInt()
                canvas.drawText("swipe", (sx + ex) / 2f, minOf(sy, ey).toFloat() - 20f, textPaint)
            }
        }

        override fun onDetachedFromWindow() { super.onDetachedFromWindow(); animator?.cancel() }
    }
}
