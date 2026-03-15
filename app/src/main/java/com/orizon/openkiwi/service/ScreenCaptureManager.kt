package com.orizon.openkiwi.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScreenCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCaptureManager"
        const val REQUEST_CODE_SCREEN_CAPTURE = 1001
    }

    enum class CaptureMethod { NONE, ACCESSIBILITY, MEDIA_PROJECTION, SHELL }

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _captureMethod = MutableStateFlow(CaptureMethod.NONE)
    val captureMethod: StateFlow<CaptureMethod> = _captureMethod.asStateFlow()

    private var savedResultCode: Int? = null
    private var savedResultData: Intent? = null
    private var accessibilityFailCount = 0

    fun supportsAccessibilityScreenshot(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun isAccessibilityScreenshotAvailable(): Boolean =
        supportsAccessibilityScreenshot() &&
        KiwiAccessibilityService.instance != null &&
        accessibilityFailCount < 3

    fun getScreenCaptureIntent(): Intent {
        val pm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return pm.createScreenCaptureIntent()
    }

    fun handleActivityResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            savedResultCode = resultCode
            savedResultData = data
            ScreenCaptureService.start(context, resultCode, data)
            _isCapturing.value = true
        } else {
            _isCapturing.value = false
        }
    }

    fun stopCapture() {
        ScreenCaptureService.stop(context)
        _isCapturing.value = false
    }

    fun refreshState() {
        _isCapturing.value = ScreenCaptureService.isRunning()
        if (KiwiAccessibilityService.instance != null) accessibilityFailCount = 0
    }

    suspend fun captureScreen(): Bitmap? {
        if (isAccessibilityScreenshotAvailable()) {
            try {
                val svc = KiwiAccessibilityService.instance
                val bmp = svc?.takeScreenshotSuspend()
                if (bmp != null) {
                    _captureMethod.value = CaptureMethod.ACCESSIBILITY
                    accessibilityFailCount = 0
                    return bmp
                }
                accessibilityFailCount++
            } catch (e: Exception) {
                accessibilityFailCount++
                Log.w(TAG, "Accessibility screenshot failed", e)
            }
        }

        val service = ScreenCaptureService.instance
        if (service != null) {
            try {
                val bmp = service.captureScreen()
                if (bmp != null) {
                    _captureMethod.value = CaptureMethod.MEDIA_PROJECTION
                    return bmp
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaProjection screenshot failed", e)
            }
        }

        try {
            val bmp = captureViaShell()
            if (bmp != null) {
                _captureMethod.value = CaptureMethod.SHELL
                return bmp
            }
        } catch (e: Exception) {
            Log.w(TAG, "Shell screenshot failed", e)
        }

        _captureMethod.value = CaptureMethod.NONE
        return null
    }

    private fun captureViaShell(): Bitmap? = try {
        val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "screencap -p"))
        val bmp = android.graphics.BitmapFactory.decodeStream(proc.inputStream)
        proc.inputStream.close()
        proc.waitFor()
        bmp
    } catch (_: Exception) { null }

    fun canCaptureScreen(): Boolean =
        isAccessibilityScreenshotAvailable() || ScreenCaptureService.isRunning()

    fun getCaptureMethodDescription(): String = when {
        isAccessibilityScreenshotAvailable() -> "无障碍截图"
        ScreenCaptureService.isRunning() -> "屏幕共享"
        else -> "未就绪"
    }

    fun tryRestartService(): Boolean {
        val code = savedResultCode; val data = savedResultData
        if (code != null && data != null) {
            ScreenCaptureService.start(context, code, data)
            _isCapturing.value = true
            return true
        }
        return false
    }

    fun resetAccessibilityFailCount() { accessibilityFailCount = 0 }
}
