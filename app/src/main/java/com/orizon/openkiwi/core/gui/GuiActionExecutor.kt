package com.orizon.openkiwi.core.gui

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import com.orizon.openkiwi.service.KiwiAccessibilityService
import com.orizon.openkiwi.service.TouchIndicatorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class GuiActionExecutor(private val context: Context) {

    private var screenWidth: Int = 1080
    private var screenHeight: Int = 2400

    companion object {
        private const val TAG = "GuiActionExecutor"
    }

    init { updateScreenSize() }

    fun updateScreenSize() {
        try {
            val dm = context.resources.displayMetrics
            if (dm.widthPixels > 0) { screenWidth = dm.widthPixels; screenHeight = dm.heightPixels; return }
        } catch (_: Exception) {}
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val b = wm.currentWindowMetrics.bounds; screenWidth = b.width(); screenHeight = b.height()
            } else {
                val dm = DisplayMetrics(); @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
                screenWidth = dm.widthPixels; screenHeight = dm.heightPixels
            }
        } catch (_: Exception) {}
    }

    fun getCurrentPackageName(): String? {
        return KiwiAccessibilityService.instance?.getCurrentPackageName()
    }

    suspend fun execute(action: GuiAction): GuiActionResult {
        val svc = KiwiAccessibilityService.instance
        if (svc == null && action !is GuiAction.Launch && action !is GuiAction.Finish && action !is GuiAction.Error && action !is GuiAction.Takeover && action !is GuiAction.Wait) {
            return GuiActionResult(false, message = "无障碍服务未运行")
        }
        return when (action) {
            is GuiAction.Tap -> execTap(svc!!, action)
            is GuiAction.Swipe -> execSwipe(svc!!, action)
            is GuiAction.Type -> execType(svc!!, action)
            is GuiAction.Launch -> execLaunch(action)
            is GuiAction.Back -> {
                svc!!.pressBack()
                delay(300)
                svc.invalidateNodeCache()
                GuiActionResult(true, message = "返回")
            }
            is GuiAction.Home -> {
                svc!!.pressHome()
                delay(300)
                svc.invalidateNodeCache()
                GuiActionResult(true, message = "主页")
            }
            is GuiAction.LongPress -> execLongPress(svc!!, action)
            is GuiAction.DoubleTap -> execDoubleTap(svc!!, action)
            is GuiAction.PinchZoom -> execPinchZoom(svc!!, action)
            is GuiAction.KeyEvent -> execKeyEvent(svc!!, action)
            is GuiAction.InstallApp -> execInstallApp(action)
            is GuiAction.UninstallApp -> execUninstallApp(action)
            is GuiAction.Wait -> { delay(action.durationMs); GuiActionResult(true, message = "等待${action.durationMs}ms") }
            is GuiAction.Takeover -> GuiActionResult(true, message = "需要用户介入: ${action.reason}")
            is GuiAction.Finish -> GuiActionResult(true, shouldFinish = true, message = action.message)
            is GuiAction.Error -> GuiActionResult(false, message = action.errorMessage)
            is GuiAction.Batch -> execBatch(action)
        }
    }

    private suspend fun execTap(svc: KiwiAccessibilityService, a: GuiAction.Tap): GuiActionResult {
        updateScreenSize()
        val (px, py) = if (a.isPixel) Pair(a.x.coerceIn(0, screenWidth - 1), a.y.coerceIn(0, screenHeight - 1))
                        else CoordinateUtils.normalizedToAbsolute(a.x, a.y, screenWidth, screenHeight)
        return withContext(Dispatchers.Main) {
            TouchIndicatorService.showTap(px.toFloat(), py.toFloat())
            val ok = svc.performTapAndWait(px.toFloat(), py.toFloat())
            delay(350)
            svc.invalidateNodeCache()
            GuiActionResult(ok, message = "点击($px,$py)")
        }
    }

    private suspend fun execSwipe(svc: KiwiAccessibilityService, a: GuiAction.Swipe): GuiActionResult {
        updateScreenSize()
        val (sx, sy) = CoordinateUtils.normalizedToAbsolute(a.startX, a.startY, screenWidth, screenHeight)
        val (ex, ey) = CoordinateUtils.normalizedToAbsolute(a.endX, a.endY, screenWidth, screenHeight)
        val dur = if (a.durationMs > 0) a.durationMs
                  else CoordinateUtils.calculateSwipeDuration(a.startX, a.startY, a.endX, a.endY, screenWidth, screenHeight)
        return withContext(Dispatchers.Main) {
            TouchIndicatorService.showSwipe(sx.toFloat(), sy.toFloat(), ex.toFloat(), ey.toFloat())
            svc.performGestureAndWait(sx.toFloat(), sy.toFloat(), ex.toFloat(), ey.toFloat(), dur)
            delay(300)
            svc.invalidateNodeCache()
            GuiActionResult(true, message = "滑动($sx,$sy)->($ex,$ey)")
        }
    }

    private suspend fun execType(svc: KiwiAccessibilityService, a: GuiAction.Type): GuiActionResult = withContext(Dispatchers.Main) {
        svc.invalidateNodeCache()

        val node = svc.findFocusedInput()
            ?: svc.findEditableNode()

        if (node != null) {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            delay(50)

            val setTextOk = svc.performSetText(node, a.text)
            if (setTextOk) {
                delay(150)
                svc.invalidateNodeCache()
                return@withContext GuiActionResult(true, message = "输入: ${a.text.take(20)}")
            }

            Log.w(TAG, "ACTION_SET_TEXT failed, falling back to clipboard paste")
            if (pasteViaClipboard(node, a.text)) {
                delay(150)
                svc.invalidateNodeCache()
                return@withContext GuiActionResult(true, message = "输入(粘贴): ${a.text.take(20)}")
            }
        }

        val editableInfo = svc.getScreenNodes().firstOrNull { it.isEditable }
        if (editableInfo != null) {
            val b = editableInfo.bounds
            svc.performTapAndWait(b.centerX().toFloat(), b.centerY().toFloat())
            delay(300)

            val retryNode = svc.findFocusedInput() ?: svc.findEditableNode()
            if (retryNode != null) {
                retryNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                delay(50)
                if (svc.performSetText(retryNode, a.text) || pasteViaClipboard(retryNode, a.text)) {
                    delay(150)
                    svc.invalidateNodeCache()
                    return@withContext GuiActionResult(true, message = "输入(点击后): ${a.text.take(20)}")
                }
            }
        }

        GuiActionResult(false, message = "未找到输入框或输入失败")
    }

    private fun pasteViaClipboard(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("input", text))
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (e: Exception) {
            Log.w(TAG, "pasteViaClipboard failed", e)
            false
        }
    }

    private suspend fun execLaunch(a: GuiAction.Launch): GuiActionResult {
        val pkg = a.packageName
            ?: AppPackages.getPackageName(a.appName)
            ?: findPackage(a.appName)
        if (pkg == null) return GuiActionResult(false, message = "未找到应用: ${a.appName}")
        if (pkg.contains("openkiwi", ignoreCase = true)) {
            return GuiActionResult(false, message = "禁止启动控制应用自身")
        }
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return GuiActionResult(false, message = "应用未安装: ${a.appName}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
            delay(2000)
            KiwiAccessibilityService.instance?.invalidateNodeCache()
            GuiActionResult(true, message = "启动: ${a.appName}")
        } catch (e: Exception) {
            GuiActionResult(false, message = "启动失败: ${e.message}")
        }
    }

    private fun findPackage(appName: String): String? {
        val pm = context.packageManager
        try {
            for (app in pm.getInstalledApplications(0)) {
                val label = pm.getApplicationLabel(app).toString()
                if (label.equals(appName, true)) return app.packageName
            }
            for (app in pm.getInstalledApplications(0)) {
                val label = pm.getApplicationLabel(app).toString()
                if (label.contains(appName, true) || appName.contains(label, true)) return app.packageName
            }
        } catch (_: Exception) {}
        return null
    }

    private suspend fun execLongPress(svc: KiwiAccessibilityService, a: GuiAction.LongPress): GuiActionResult {
        updateScreenSize()
        val (px, py) = CoordinateUtils.normalizedToAbsolute(a.x, a.y, screenWidth, screenHeight)
        return withContext(Dispatchers.Main) {
            TouchIndicatorService.showTap(px.toFloat(), py.toFloat())
            svc.performGestureAndWait(px.toFloat(), py.toFloat(), px.toFloat(), py.toFloat(), a.durationMs)
            delay(200)
            svc.invalidateNodeCache()
            GuiActionResult(true, message = "长按($px,$py)")
        }
    }

    private suspend fun execDoubleTap(svc: KiwiAccessibilityService, a: GuiAction.DoubleTap): GuiActionResult {
        updateScreenSize()
        val (px, py) = CoordinateUtils.normalizedToAbsolute(a.x, a.y, screenWidth, screenHeight)
        return withContext(Dispatchers.Main) {
            TouchIndicatorService.showTap(px.toFloat(), py.toFloat())
            svc.performTapAndWait(px.toFloat(), py.toFloat())
            delay(80)
            svc.performTapAndWait(px.toFloat(), py.toFloat())
            delay(200)
            svc.invalidateNodeCache()
            GuiActionResult(true, message = "双击($px,$py)")
        }
    }

    private suspend fun execBatch(a: GuiAction.Batch): GuiActionResult {
        val results = mutableListOf<String>()
        var allOk = true; var finish = false
        for ((i, sub) in a.actions.withIndex()) {
            val r = execute(sub)
            results.add("${i + 1}. ${r.message ?: "?"}: ${if (r.success) "OK" else "FAIL"}")
            if (!r.success) allOk = false
            if (r.shouldFinish) { finish = true; break }
            if (i < a.actions.size - 1) delay(a.delayBetweenMs)
        }
        return GuiActionResult(allOk, shouldFinish = finish, message = "批量${a.actions.size}步\n${results.joinToString("\n")}")
    }

    private suspend fun execPinchZoom(svc: KiwiAccessibilityService, a: GuiAction.PinchZoom): GuiActionResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return GuiActionResult(false, message = "双指缩放需要Android 7.0+")
        }
        updateScreenSize()
        val (cx, cy) = CoordinateUtils.normalizedToAbsolute(a.centerX, a.centerY, screenWidth, screenHeight)
        val offset = (100 * a.factor).toInt().coerceIn(50, 400)

        return withContext(Dispatchers.Main) {
            try {
                val builder = GestureDescription.Builder()
                val duration = 500L

                if (a.zoomIn) {
                    val path1 = Path().apply { moveTo(cx.toFloat(), cy.toFloat()); lineTo((cx - offset).toFloat(), (cy - offset).toFloat()) }
                    val path2 = Path().apply { moveTo(cx.toFloat(), cy.toFloat()); lineTo((cx + offset).toFloat(), (cy + offset).toFloat()) }
                    builder.addStroke(GestureDescription.StrokeDescription(path1, 0, duration))
                    builder.addStroke(GestureDescription.StrokeDescription(path2, 0, duration))
                } else {
                    val path1 = Path().apply { moveTo((cx - offset).toFloat(), (cy - offset).toFloat()); lineTo(cx.toFloat(), cy.toFloat()) }
                    val path2 = Path().apply { moveTo((cx + offset).toFloat(), (cy + offset).toFloat()); lineTo(cx.toFloat(), cy.toFloat()) }
                    builder.addStroke(GestureDescription.StrokeDescription(path1, 0, duration))
                    builder.addStroke(GestureDescription.StrokeDescription(path2, 0, duration))
                }

                svc.dispatchGestureCompat(builder.build())
                delay(600)
                svc.invalidateNodeCache()
                GuiActionResult(true, message = "缩放${if (a.zoomIn) "放大" else "缩小"}($cx,$cy)")
            } catch (e: Exception) {
                GuiActionResult(false, message = "缩放失败: ${e.message}")
            }
        }
    }

    private suspend fun execKeyEvent(svc: KiwiAccessibilityService, a: GuiAction.KeyEvent): GuiActionResult {
        if (a.keyCode.uppercase() == "VOLUME_UP" || a.keyCode.uppercase() == "VOLUME_DOWN") {
            return try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val direction = if (a.keyCode.uppercase() == "VOLUME_UP")
                    android.media.AudioManager.ADJUST_RAISE
                else
                    android.media.AudioManager.ADJUST_LOWER
                audioManager.adjustStreamVolume(
                    android.media.AudioManager.STREAM_MUSIC,
                    direction,
                    android.media.AudioManager.FLAG_SHOW_UI
                )
                delay(200)
                GuiActionResult(true, message = "音量${if (a.keyCode.uppercase() == "VOLUME_UP") "+" else "-"}")
            } catch (e: Exception) {
                GuiActionResult(false, message = "音量调节失败: ${e.message}")
            }
        }

        return withContext(Dispatchers.Main) {
            val ok = when (a.keyCode.uppercase()) {
                "BACK" -> svc.pressBack()
                "HOME" -> svc.pressHome()
                "RECENT", "RECENTS" -> svc.pressRecents()
                "NOTIFICATIONS" -> svc.openNotifications()
                "QUICK_SETTINGS" -> svc.openQuickSettings()
                "POWER" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                } else false
                else -> false
            }
            if (!ok) return@withContext GuiActionResult(false, message = "未知按键: ${a.keyCode}")
            delay(300)
            svc.invalidateNodeCache()
            GuiActionResult(true, message = "按键: ${a.keyCode}")
        }
    }

    private suspend fun execInstallApp(a: GuiAction.InstallApp): GuiActionResult {
        return try {
            val file = java.io.File(a.apkPath)
            if (!file.exists()) return GuiActionResult(false, message = "APK文件不存在: ${a.apkPath}")

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            delay(1000)
            GuiActionResult(true, message = "安装界面已打开: ${file.name}")
        } catch (e: Exception) {
            GuiActionResult(false, message = "安装失败: ${e.message}")
        }
    }

    private suspend fun execUninstallApp(a: GuiAction.UninstallApp): GuiActionResult {
        return try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${a.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            delay(1000)
            GuiActionResult(true, message = "卸载确认: ${a.packageName}")
        } catch (e: Exception) {
            GuiActionResult(false, message = "卸载失败: ${e.message}")
        }
    }

    fun getScreenSize() = Pair(screenWidth, screenHeight)
}
