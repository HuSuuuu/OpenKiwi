package com.orizon.openkiwi.core.gui

sealed class GuiAction {
    abstract val metadata: String

    data class Tap(val x: Int, val y: Int, val isPixel: Boolean = false, override val metadata: String = "do") : GuiAction()
    data class Swipe(val startX: Int, val startY: Int, val endX: Int, val endY: Int, val durationMs: Long = 500, override val metadata: String = "do") : GuiAction()
    data class Type(val text: String, val submit: Boolean = false, override val metadata: String = "do") : GuiAction()
    data class Launch(val appName: String, val packageName: String? = null, override val metadata: String = "do") : GuiAction()
    data class Back(override val metadata: String = "do") : GuiAction()
    data class Home(override val metadata: String = "do") : GuiAction()
    data class LongPress(val x: Int, val y: Int, val durationMs: Long = 1500, override val metadata: String = "do") : GuiAction()
    data class DoubleTap(val x: Int, val y: Int, override val metadata: String = "do") : GuiAction()
    data class PinchZoom(val centerX: Int, val centerY: Int, val zoomIn: Boolean = true, val factor: Float = 2.0f, override val metadata: String = "do") : GuiAction()
    data class KeyEvent(val keyCode: String, override val metadata: String = "do") : GuiAction()
    data class InstallApp(val apkPath: String, override val metadata: String = "do") : GuiAction()
    data class UninstallApp(val packageName: String, override val metadata: String = "do") : GuiAction()
    data class Wait(val durationMs: Long = 3000, override val metadata: String = "do") : GuiAction()
    data class Takeover(val reason: String, override val metadata: String = "do") : GuiAction()
    data class Finish(val message: String, override val metadata: String = "finish") : GuiAction()
    data class Error(val errorMessage: String, override val metadata: String = "error") : GuiAction()
    data class Batch(val actions: List<GuiAction>, val delayBetweenMs: Long = 300, override val metadata: String = "do") : GuiAction()
}

data class GuiAgentResponse(
    val thinking: String,
    val action: GuiAction,
    val rawResponse: String
)

data class GuiActionResult(
    val success: Boolean,
    val shouldFinish: Boolean = false,
    val message: String? = null
)

data class GuiStepResult(
    val stepNumber: Int,
    val success: Boolean,
    val finished: Boolean,
    val thinking: String,
    val action: GuiAction,
    val result: GuiActionResult
)
