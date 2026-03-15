package com.orizon.openkiwi.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "openkiwi_screen_capture"
        private const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.orizon.openkiwi.START_CAPTURE"
        const val ACTION_STOP = "com.orizon.openkiwi.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        @Volatile
        var instance: ScreenCaptureService? = null
            private set

        fun isRunning(): Boolean = instance?.isCapturing == true

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ScreenCaptureService::class.java).apply { action = ACTION_STOP }
            )
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private var isCapturing = false
    private val captureMutex = Mutex()

    @Volatile
    private var latestBitmap: Bitmap? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        screenWidth = dm.widthPixels
        screenHeight = dm.heightPixels
        screenDensity = dm.densityDpi
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                @Suppress("DEPRECATION")
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    startForegroundNotification()
                    startCapture(resultCode, resultData)
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP -> { stopCapture(); stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "屏幕截图服务", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于后台截取屏幕"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val pi = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenKiwi")
            .setContentText("屏幕截图服务运行中")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = pm.getMediaProjection(resultCode, resultData) ?: return

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                latestBitmap?.recycle()
                latestBitmap = imageToBitmap(image)
            } finally {
                image.close()
            }
        }, null)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "OpenKiwiCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
        isCapturing = true
    }

    private fun stopCapture() {
        isCapturing = false
        virtualDisplay?.release(); virtualDisplay = null
        imageReader?.close(); imageReader = null
        mediaProjection?.stop(); mediaProjection = null
        latestBitmap?.recycle(); latestBitmap = null
    }

    suspend fun captureScreen(): Bitmap? = withContext(Dispatchers.IO) {
        captureMutex.withLock {
            if (!isCapturing) return@withContext null
            try {
                withTimeout(3000) {
                    val deferred = CompletableDeferred<Bitmap?>()
                    val image = imageReader?.acquireLatestImage()
                    if (image != null) {
                        try { deferred.complete(imageToBitmap(image)) } finally { image.close() }
                    } else {
                        val cached = latestBitmap
                        deferred.complete(cached?.config?.let { cached.copy(it, false) })
                    }
                    deferred.await()
                }
            } catch (e: Exception) {
                Log.e(TAG, "captureScreen failed", e)
                null
            }
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bmp = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888
        )
        bmp.copyPixelsFromBuffer(buffer)
        return if (rowPadding > 0) Bitmap.createBitmap(bmp, 0, 0, image.width, image.height) else bmp
    }

    fun getScreenSize(): Pair<Int, Int> = Pair(screenWidth, screenHeight)
}
