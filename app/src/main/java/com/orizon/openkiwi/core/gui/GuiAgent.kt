package com.orizon.openkiwi.core.gui

import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import android.util.Log
import com.orizon.openkiwi.core.model.*
import com.orizon.openkiwi.data.repository.ModelRepository
import com.orizon.openkiwi.network.OpenAIApiClient
import com.orizon.openkiwi.service.KiwiAccessibilityService
import com.orizon.openkiwi.service.ThinkingBubbleService
import com.orizon.openkiwi.service.TouchIndicatorService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class GuiAgent(
    private val context: Context,
    private val apiClient: OpenAIApiClient,
    private val modelRepository: ModelRepository,
    private val actionParser: GuiActionParser,
    private val actionExecutor: GuiActionExecutor
) {
    private val taskContext = GuiTaskContext()
    private val actionValidator = ActionValidator(taskContext)
    private val takeoverManager = TakeoverManager()
    private val taskDecomposer = TaskDecomposer()
    private val intentClassifier = IntentClassifier()
    private val conversationHistory = mutableListOf<ChatMessage>()
    private var stepCount = 0
    private val recentActions = mutableListOf<String>()

    private val maxSteps = 50
    private val maxHistoryMessages = 16
    private val maxImagesKept = 1
    private val oscillationWindowSize = 6

    val takeoverState get() = takeoverManager.takeoverState

    fun classifyIntent(input: String) = intentClassifier.classify(input)
    fun decomposeTask(task: String) = taskDecomposer.decompose(task)

    suspend fun executeTask(
        goal: String,
        onStep: suspend (GuiStepResult) -> Unit = {},
        onProgress: suspend (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        if (KiwiAccessibilityService.instance == null) {
            return@withContext "错误: 无障碍服务未开启，请在系统设置中开启 OpenKiwi 无障碍服务后重试"
        }

        taskContext.initTask(goal)
        takeoverManager.reset()
        actionValidator.reset()
        conversationHistory.clear()
        recentActions.clear()
        stepCount = 0

        val config = modelRepository.getDefaultConfig()
            ?: return@withContext "错误: 未配置模型"

        conversationHistory.add(ChatMessage(role = ChatRole.SYSTEM, content = SYSTEM_PROMPT))

        onProgress("开始执行: $goal")
        startOverlayServices()
        ThinkingBubbleService.updateThinking("开始: ${goal.take(20)}...")

        try {
            while (stepCount < maxSteps) {
                stepCount++
                ThinkingBubbleService.showStep(stepCount, "截屏...")

                val screenshot = captureScreen()
                Log.i(TAG, "[$stepCount] screenshot=${if (screenshot != null) "${screenshot.width}x${screenshot.height}" else "NULL"}")

                if (screenshot != null) {
                    taskContext.updateScreenHash(screenshot.generationId)
                }

                if (taskContext.isStuck()) {
                    Log.w(TAG, "[$stepCount] stuck detected: sameScreen=${taskContext.sameScreenCount}, waits=${taskContext.consecutiveWaits}, fails=${taskContext.consecutiveFailures}")
                }

                val oscillation = detectOscillation()
                if (oscillation != null) {
                    Log.w(TAG, "[$stepCount] oscillation detected: $oscillation")
                }

                val userMsg = buildUserMessage(goal, stepCount == 1, screenshot)
                val msgSize = userMsg.content?.length ?: 0
                val hasImage = userMsg.imageUrl != null
                Log.i(TAG, "[$stepCount] userMsg: ${msgSize}chars, hasImage=$hasImage")

                conversationHistory.add(userMsg)
                cleanupHistory()

                ThinkingBubbleService.showStep(stepCount, "AI分析...")
                val t0 = System.currentTimeMillis()
                val aiResponse = callApi(config)
                val elapsed = System.currentTimeMillis() - t0
                Log.i(TAG, "[$stepCount] API ${elapsed}ms, response=${aiResponse?.take(200) ?: "NULL"}")

                if (aiResponse == null) {
                    onProgress("[$stepCount] API失败(${elapsed}ms), 重试")
                    ThinkingBubbleService.showStep(stepCount, "API失败,重试...")
                    delay(500)
                    continue
                }

                conversationHistory.add(ChatMessage(role = ChatRole.ASSISTANT, content = aiResponse))
                cleanupHistory()

                val parsed = actionParser.parse(aiResponse)
                Log.i(TAG, "[$stepCount] parsed: think=${parsed.thinking.take(50)}, action=${parsed.action}")

                val validated = actionValidator.validate(
                    parsed.action,
                    actionExecutor.getCurrentPackageName(),
                    taskContext.consecutiveWaits
                )
                val finalAction = when (validated) {
                    is ActionValidator.ValidationResult.Valid -> parsed.action
                    is ActionValidator.ValidationResult.Modified -> {
                        Log.w(TAG, "[$stepCount] action modified: ${validated.reason}")
                        validated.newAction
                    }
                    is ActionValidator.ValidationResult.Invalid -> {
                        Log.w(TAG, "[$stepCount] action rejected: ${validated.reason}")
                        validated.suggestedAction ?: GuiAction.Wait(500)
                    }
                }

                val actionDesc = describeAction(finalAction)
                Log.i(TAG, "[$stepCount] EXEC: $actionDesc")
                onProgress("[$stepCount] $actionDesc")
                ThinkingBubbleService.showStep(stepCount, actionDesc)
                _stepUpdates.tryEmit(StepUpdate(stepCount, parsed.thinking, actionDesc))

                recentActions.add(actionDesc)
                if (recentActions.size > oscillationWindowSize * 2) {
                    recentActions.removeAt(0)
                }

                if (finalAction is GuiAction.Launch) {
                    actionValidator.recordLaunch(finalAction.appName)
                }

                val result = actionExecutor.execute(finalAction)
                Log.i(TAG, "[$stepCount] result: ok=${result.success}, msg=${result.message}, finish=${result.shouldFinish}")
                taskContext.updateAction(actionDesc, result.success)

                val settleMs = when (finalAction) {
                    is GuiAction.Launch -> 0L
                    is GuiAction.Wait -> 0L
                    is GuiAction.Tap -> 800L
                    is GuiAction.Type -> 500L
                    is GuiAction.Swipe -> 600L
                    else -> 400L
                }
                if (settleMs > 0) delay(settleMs)

                val stepResult = GuiStepResult(stepCount, result.success, result.shouldFinish, parsed.thinking, finalAction, result)
                onStep(stepResult)

                if (result.shouldFinish) {
                    onProgress("完成: ${result.message}")
                    return@withContext result.message ?: "任务完成"
                }

                if (finalAction is GuiAction.Takeover) {
                    takeoverManager.requestTakeover(finalAction.reason)
                    return@withContext "需要用户介入: ${result.message}"
                }
            }

            return@withContext "达到最大步数限制"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "GUI task error", e)
            return@withContext "GUI任务异常: ${e.message}"
        } finally {
            _stepUpdates.tryEmit(StepUpdate(stepCount, "", "", done = true))
            taskContext.reset()
            forceStopOverlays()
        }
    }

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(context)

    private fun startOverlayServices() {
        if (!hasOverlayPermission()) return
        try { TouchIndicatorService.start(context) } catch (_: Exception) {}
        try { ThinkingBubbleService.start(context) } catch (_: Exception) {}
    }

    private fun forceStopOverlays() {
        try { ThinkingBubbleService.hide() } catch (_: Exception) {}
        try { ThinkingBubbleService.stop(context) } catch (_: Exception) {}
        try { TouchIndicatorService.stop(context) } catch (_: Exception) {}
    }

    private suspend fun captureScreen(): Bitmap? {
        val svc = KiwiAccessibilityService.instance
        if (svc == null) {
            Log.w(TAG, "captureScreen: accessibility service is null")
            return null
        }
        return withContext(Dispatchers.Main) {
            try {
                svc.takeScreenshotSuspend()
            } catch (e: Exception) {
                Log.e(TAG, "captureScreen exception", e)
                null
            }
        }
    }

    private fun buildUserMessage(goal: String, isFirst: Boolean, screenshot: Bitmap?): ChatMessage {
        val currentApp = actionExecutor.getCurrentPackageName()
        val isOwnApp = currentApp?.contains("openkiwi", ignoreCase = true) == true
        val isTargetApp = taskContext.targetPackage?.let {
            currentApp?.contains(it, ignoreCase = true) == true
        } ?: false

        if (!taskContext.hasEnteredTargetApp && isTargetApp) {
            taskContext.markEnteredTargetApp()
        }

        val text = buildString {
            if (isFirst) append("目标: $goal\n")
            else append("目标: ${taskContext.originalGoal}\n")

            append("步骤$stepCount")
            currentApp?.let {
                val appName = AppPackages.getAppName(it) ?: it.substringAfterLast('.')
                append(" | $appName")
                if (isTargetApp) append("(目标)")
                if (isOwnApp) append("(控制app!)")
            }
            append("\n")
            taskContext.lastAction?.let { append("上步: $it${if (!taskContext.lastActionSuccess) " 失败" else ""}\n") }

            if (isOwnApp) {
                append("!! 当前在控制应用，必须Launch ${taskContext.targetApp ?: "目标应用"}\n")
            }

            if (screenshot == null) {
                append("[无截图,使用界面元素]\n")
                val svc = KiwiAccessibilityService.instance
                if (svc != null) {
                    val nodes = svc.getScreenNodes()
                    val interactive = nodes.filter { it.text.isNotBlank() || it.contentDescription.isNotBlank() || it.isClickable || it.isEditable }
                    if (interactive.isNotEmpty()) {
                        append("界面元素:\n")
                        val (sw, sh) = actionExecutor.getScreenSize()
                        interactive.take(30).forEach { n ->
                            val label = when {
                                n.text.isNotBlank() -> "\"${n.text.take(30)}\""
                                n.contentDescription.isNotBlank() -> "d=\"${n.contentDescription.take(20)}\""
                                else -> ""
                            }
                            val flags = buildList { if (n.isClickable) add("C"); if (n.isEditable) add("E") }.joinToString("")
                            if (label.isNotBlank() || flags.isNotBlank()) {
                                val b = n.bounds
                                val (nx, ny) = CoordinateUtils.absoluteToNormalized(b.centerX(), b.centerY(), sw, sh)
                                append("$label [$flags] (${nx.coerceIn(0, 999)},${ny.coerceIn(0, 999)})\n")
                            }
                        }
                    } else {
                        append("[无可用界面元素]\n")
                    }
                }
            }

            val hints = taskContext.buildHints()
            if (hints.isNotEmpty()) {
                append("⚠ ")
                append(hints.joinToString("; "))
                append("\n")
            }

            val oscillation = detectOscillation()
            if (oscillation != null) {
                append("!! 检测到操作循环($oscillation)，不要重复相同动作序列。尝试完全不同的策略，或者如果任务已完成请用finish。\n")
            }

            when {
                isOwnApp -> append("立即Launch目标应用。")
                isTargetApp -> append("已在目标应用，继续操作。注意看截图中按钮位置。")
                else -> append("分析屏幕，执行下一步。")
            }
        }

        if (screenshot != null) {
            val scaled = ImageUtils.scaleBitmap(screenshot, 1280)
            val b64 = ImageUtils.bitmapToBase64Url(scaled, quality = 55)
            Log.d(TAG, "image payload: ${b64.length} chars, scaled=${scaled.width}x${scaled.height}")
            return ChatMessage(role = ChatRole.USER, content = text, imageUrl = b64)
        }
        return ChatMessage(role = ChatRole.USER, content = text)
    }

    private suspend fun callApi(config: ModelConfig): String? {
        val request = ChatCompletionRequest(
            model = config.modelName,
            messages = conversationHistory.toList(),
            temperature = 0.1,
            maxTokens = 400,
            reasoningEffort = "low"
        )
        return try {
            withTimeout(25_000L) {
                val result = apiClient.chatCompletion(config.apiBaseUrl, config.apiKey, request)
                if (result.isFailure) {
                    Log.e(TAG, "API error: ${result.exceptionOrNull()?.message}")
                }
                result.getOrNull()?.choices?.firstOrNull()?.message?.content
            }
        } catch (e: Exception) {
            Log.e(TAG, "API call failed", e)
            null
        }
    }

    private fun cleanupHistory() {
        if (conversationHistory.size > maxHistoryMessages) {
            val sys = conversationHistory.firstOrNull { it.role == ChatRole.SYSTEM }
            val recent = conversationHistory.takeLast(maxHistoryMessages - 1)
            conversationHistory.clear()
            sys?.let { conversationHistory.add(it) }
            conversationHistory.addAll(recent)
        }
        var imgKept = 0
        for (i in (conversationHistory.size - 1) downTo 0) {
            val msg = conversationHistory[i]
            if (msg.imageUrl != null) {
                if (imgKept >= maxImagesKept) {
                    conversationHistory[i] = msg.copy(imageUrl = null)
                } else imgKept++
            }
        }
    }

    data class StepUpdate(val step: Int, val thinking: String, val action: String, val done: Boolean = false)

    companion object {
        private const val TAG = "GuiAgent"

        private val _stepUpdates = MutableSharedFlow<StepUpdate>(replay = 1, extraBufferCapacity = 64)
        val stepUpdates: SharedFlow<StepUpdate> = _stepUpdates.asSharedFlow()

        private val SYSTEM_PROMPT = """
你是Android手机操作助手。看截图/界面执行操作。

格式:
<think>简短分析(1-2句)</think>
<answer>操作</answer>

操作(坐标0-999):
- do(action="Tap", element=[x,y])
- do(action="Swipe", start=[x1,y1], end=[x2,y2])
- do(action="Long Press", element=[x,y])
- do(action="Type", text="xxx")
- do(action="Launch", app="应用名")
- do(action="Back") / do(action="Home") / do(action="Wait", duration=3000)
- do(action="Take_over", message="原因")
- finish(message="结果")

多步:
<answer>
do(action="Tap", element=[100,200])
do(action="Type", text="你好")
</answer>

规则: 禁止启动OpenKiwi; 点击元素中心; 弹窗找X关闭; 支付登录用Take_over
禁止重复启动: 如果应用已经打开并显示在屏幕上，绝对不要再次Launch它！直接在当前界面操作。Launch只用于第一次打开应用。
重要: 操作后仔细观察界面变化。如果拍照后看到预览/确认界面,说明已拍照成功,用finish报告。如果连续2次以上执行相同动作但界面没变化,必须换一种策略或用finish结束。
""".trimIndent()
    }

    private fun describeAction(action: GuiAction): String = when (action) {
        is GuiAction.Tap -> "点击(${action.x},${action.y})"
        is GuiAction.Swipe -> "滑动(${action.startX},${action.startY}→${action.endX},${action.endY})"
        is GuiAction.Type -> "输入\"${action.text.take(10)}\""
        is GuiAction.Launch -> "启动${action.appName}"
        is GuiAction.Back -> "返回"
        is GuiAction.Home -> "主页"
        is GuiAction.LongPress -> "长按(${action.x},${action.y})"
        is GuiAction.DoubleTap -> "双击(${action.x},${action.y})"
        is GuiAction.PinchZoom -> "${if (action.zoomIn) "放大" else "缩小"}(${action.centerX},${action.centerY})"
        is GuiAction.KeyEvent -> "按键${action.keyCode}"
        is GuiAction.InstallApp -> "安装${action.apkPath.substringAfterLast("/")}"
        is GuiAction.UninstallApp -> "卸载${action.packageName}"
        is GuiAction.Wait -> "等待${action.durationMs}ms"
        is GuiAction.Takeover -> "用户接管"
        is GuiAction.Finish -> "完成: ${action.message.take(20)}"
        is GuiAction.Error -> "错误: ${action.errorMessage.take(20)}"
        is GuiAction.Batch -> "批量${action.actions.size}步"
    }

    /**
     * Detects repeating action patterns (e.g., Launch→Tap→Launch→Tap).
     * Returns a description of the pattern if oscillation is found, null otherwise.
     */
    private fun detectOscillation(): String? {
        if (recentActions.size < 4) return null
        for (patternLen in 1..oscillationWindowSize / 2) {
            if (recentActions.size < patternLen * 2) continue
            val tail = recentActions.takeLast(patternLen * 2)
            val first = tail.subList(0, patternLen)
            val second = tail.subList(patternLen, patternLen * 2)
            if (first == second) {
                return first.joinToString("→")
            }
        }
        return null
    }

    fun cancel() {
        taskContext.reset()
        conversationHistory.clear()
        recentActions.clear()
        forceStopOverlays()
    }
}
