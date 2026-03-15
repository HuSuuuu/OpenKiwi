package com.orizon.openkiwi.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.orizon.openkiwi.MainActivity
import com.orizon.openkiwi.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class KiwiAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON
            notificationTimeout = 100
        }
        serviceInfo = info
        _isRunning.value = true
        startKeepAliveNotification()
    }

    @Volatile
    private var currentForegroundPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let { pkg ->
                if (pkg.isNotBlank()) currentForegroundPackage = pkg
            }
        }
        _lastEvent.value = EventInfo(
            eventType = event.eventType,
            packageName = event.packageName?.toString() ?: "",
            className = event.className?.toString() ?: "",
            text = event.text?.joinToString() ?: ""
        )
    }

    fun getCurrentPackageName(): String? {
        currentForegroundPackage?.let { return it }
        return rootInActiveWindow?.packageName?.toString()
    }

    override fun onInterrupt() {
        _isRunning.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        _instance = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startKeepAliveNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "无障碍服务保活",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "保持无障碍服务在后台运行"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenKiwi")
            .setContentText("无障碍服务运行中")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    @Volatile
    private var cachedNodes: List<NodeInfo>? = null
    private var cacheTimestamp = 0L
    private val cacheTtlMs = 500L

    fun getScreenNodes(maxDepth: Int = 15, maxCount: Int = 500): List<NodeInfo> {
        val now = System.currentTimeMillis()
        cachedNodes?.let { if (now - cacheTimestamp < cacheTtlMs) return it }

        val root = rootInActiveWindow ?: return emptyList()
        val result = mutableListOf<NodeInfo>()
        collectNodesLimited(root, 0, maxDepth, maxCount, result)
        cachedNodes = result
        cacheTimestamp = now
        return result
    }

    fun invalidateNodeCache() {
        cachedNodes = null
    }

    fun performClick(nodeInfo: AccessibilityNodeInfo): Boolean {
        return nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun performSetText(nodeInfo: AccessibilityNodeInfo, text: String): Boolean {
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun performGesture(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            dispatchGesture(gesture, null, null)
        }
    }

    suspend fun performGestureAndWait(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return suspendCancellableCoroutine { cont ->
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            val ok = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }, null)
            if (!ok && cont.isActive) cont.resume(false)
        }
    }

    suspend fun performTapAndWait(x: Float, y: Float): Boolean {
        return performGestureAndWait(x, y, x, y, 80)
    }

    suspend fun dispatchGestureCompat(gesture: GestureDescription): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return suspendCancellableCoroutine { cont ->
            val ok = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }, null)
            if (!ok && cont.isActive) cont.resume(false)
        }
    }


    fun performTap(x: Float, y: Float) {
        performGesture(x, y, x, y, 50)
    }

    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes?.firstOrNull()
    }

    fun findNodeById(viewId: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        return nodes?.firstOrNull()
    }

    fun findFocusedInput(): AccessibilityNodeInfo? {
        return rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    }

    fun findEditableNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findFirstEditable(root)
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findFirstEditable(child)?.let { return it }
            }
        }
        return null
    }

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun openQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    fun takeScreenshot(callback: TakeScreenshotCallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, callback)
        }
    }

    suspend fun takeScreenshotSuspend(): android.graphics.Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            android.util.Log.w(TAG, "takeScreenshot requires API 30+, current: ${Build.VERSION.SDK_INT}")
            return null
        }
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            try {
                takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            try {
                                val hwBmp = android.graphics.Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                                result.hardwareBuffer.close()
                                if (hwBmp == null) {
                                    android.util.Log.e(TAG, "wrapHardwareBuffer returned null")
                                    cont.resume(null) {}
                                    return
                                }
                                val softBmp = if (hwBmp.config == android.graphics.Bitmap.Config.HARDWARE) {
                                    val soft = hwBmp.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                    hwBmp.recycle()
                                    soft ?: run {
                                        android.util.Log.e(TAG, "Hardware bitmap copy returned null")
                                        cont.resume(null) {}
                                        return
                                    }
                                } else hwBmp
                                android.util.Log.d(TAG, "Screenshot OK: ${softBmp.width}x${softBmp.height}")
                                cont.resume(softBmp) {}
                            } catch (e: Exception) {
                                android.util.Log.e(TAG, "Screenshot bitmap conversion failed", e)
                                cont.resume(null) {}
                            }
                        }
                        override fun onFailure(errorCode: Int) {
                            android.util.Log.e(TAG, "takeScreenshot failed, errorCode=$errorCode")
                            cont.resume(null) {}
                        }
                    })
            } catch (e: Exception) {
                android.util.Log.e(TAG, "takeScreenshot exception", e)
                cont.resume(null) {}
            }
        }
    }

    private fun collectNodesLimited(
        node: AccessibilityNodeInfo,
        depth: Int,
        maxDepth: Int,
        maxCount: Int,
        result: MutableList<NodeInfo>
    ) {
        if (depth > maxDepth || result.size >= maxCount) return

        val rect = Rect()
        node.getBoundsInScreen(rect)

        val hasContent = node.text?.isNotEmpty() == true ||
                node.contentDescription?.isNotEmpty() == true ||
                node.isClickable || node.isEditable || node.isScrollable

        if (hasContent || depth <= 2) {
            result.add(
                NodeInfo(
                    className = node.className?.toString() ?: "",
                    text = node.text?.toString() ?: "",
                    contentDescription = node.contentDescription?.toString() ?: "",
                    viewId = node.viewIdResourceName ?: "",
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable,
                    bounds = rect,
                    depth = depth
                )
            )
        }

        for (i in 0 until node.childCount) {
            if (result.size >= maxCount) break
            node.getChild(i)?.let { child ->
                collectNodesLimited(child, depth + 1, maxDepth, maxCount, result)
            }
        }
    }

    init {
        _instance = this
    }

    companion object {
        private const val TAG = "KiwiA11y"
        private const val CHANNEL_ID = "accessibility_keepalive"
        private const val NOTIFICATION_ID = 1002

        private var _instance: KiwiAccessibilityService? = null
        val instance: KiwiAccessibilityService? get() = _instance

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _lastEvent = MutableStateFlow<EventInfo?>(null)
        val lastEvent: StateFlow<EventInfo?> = _lastEvent.asStateFlow()
    }
}

data class NodeInfo(
    val className: String,
    val text: String,
    val contentDescription: String,
    val viewId: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val bounds: Rect,
    val depth: Int
)

data class EventInfo(
    val eventType: Int,
    val packageName: String,
    val className: String,
    val text: String
)
