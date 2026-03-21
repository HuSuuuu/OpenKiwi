package com.orizon.openkiwi.core.agent

import android.util.Log
import com.orizon.openkiwi.core.gui.GuiAgent
import com.orizon.openkiwi.core.tool.*
import com.orizon.openkiwi.service.KiwiAccessibilityService
import com.orizon.openkiwi.service.KiwiNotificationListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Tool that lets the LLM query another AI app (e.g. 豆包) via GUI automation.
 * GuiAgent handles all screen interaction; notification listener detects output completion.
 */
class ParasiticQueryTool(private val guiAgent: GuiAgent) : Tool {

    companion object {
        private const val TAG = "ParasiticQueryTool"

        val SUPPORTED_HOSTS = mapOf(
            "豆包" to HostAppConfig("com.larus.nova", "豆包", 120_000L)
        )

        private val _isActive = MutableStateFlow(false)
        val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

        var enabled: Boolean = false
    }

    data class HostAppConfig(
        val packageName: String,
        val displayName: String,
        val maxWaitMs: Long = 120_000L
    )

    override val definition = ToolDefinition(
        name = "parasitic_query",
        description = """通过自动操作手机上的其他AI应用来获取回复（寄生模式）。
当你自身无法完成某个任务（如缺少知识、需要其他AI的能力）时，可以把问题发给手机上安装的AI应用（如豆包），让它帮忙回答，然后把结果带回来。
流程：1.GuiAgent打开目标AI应用 2.输入问题并发送 3.等待通知确认回复完成 4.GuiAgent读取回复内容并返回
需要无障碍服务权限。""",
        category = ToolCategory.GUI.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "prompt" to ToolParamDef("string", "要发给宿主AI应用的问题/指令", true),
            "host" to ToolParamDef(
                "string",
                "目标AI应用名称，默认豆包",
                false,
                defaultValue = "豆包",
                enumValues = SUPPORTED_HOSTS.keys.toList()
            )
        ),
        requiredParams = listOf("prompt"),
        timeoutMs = 300_000L
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val prompt = params["prompt"]?.toString()
        if (prompt.isNullOrBlank()) {
            return@withContext ToolResult(definition.name, false, "", error = "缺少 prompt 参数")
        }

        val hostName = params["host"]?.toString() ?: "豆包"
        val config = SUPPORTED_HOSTS[hostName]
            ?: return@withContext ToolResult(definition.name, false, "", error = "不支持的宿主应用: $hostName")

        if (KiwiAccessibilityService.instance == null) {
            return@withContext ToolResult(definition.name, false, "",
                error = "无障碍服务未开启，请在系统设置中开启 OpenKiwi 无障碍服务")
        }

        _isActive.value = true
        val startTime = System.currentTimeMillis()
        val progressLog = StringBuilder()

        try {
            // Phase 1: GuiAgent sends the prompt to host app
            progressLog.appendLine("[Phase1] 让 GuiAgent 打开 ${config.displayName} 发送消息")
            val sendGoal = buildSendGoal(config, prompt)
            Log.i(TAG, "Phase1 goal: $sendGoal")

            val sendResult = guiAgent.executeTask(
                goal = sendGoal,
                onProgress = { progressLog.appendLine(it) }
            )
            Log.i(TAG, "Phase1 result: $sendResult")
            progressLog.appendLine("[Phase1] 结果: $sendResult")

            if (sendResult.contains("错误") || sendResult.contains("异常")) {
                return@withContext ToolResult(definition.name, false,
                    progressLog.toString(), error = "发送失败: $sendResult")
            }

            // Phase 2: Wait for notification from host app
            progressLog.appendLine("[Phase2] 等待 ${config.displayName} 通知...")
            val notifReceived = waitForHostNotification(config)
            progressLog.appendLine("[Phase2] 通知${if (notifReceived) "已收到" else "等待超时，尝试直接读取"}")

            // Phase 3: GuiAgent reads the response
            progressLog.appendLine("[Phase3] 让 GuiAgent 读取回复")
            val readGoal = buildReadGoal(config)
            Log.i(TAG, "Phase3 goal: $readGoal")

            val readResult = guiAgent.executeTask(
                goal = readGoal,
                onProgress = { progressLog.appendLine(it) }
            )
            Log.i(TAG, "Phase3 result (${readResult.length} chars): ${readResult.take(200)}")

            val response = extractResponse(readResult)

            if (response.isBlank()) {
                return@withContext ToolResult(definition.name, false,
                    progressLog.toString(), error = "无法读取 ${config.displayName} 的回复")
            }

            val elapsed = System.currentTimeMillis() - startTime
            ToolResult(
                toolName = definition.name,
                success = true,
                output = buildString {
                    appendLine("${config.displayName} 的回复：")
                    appendLine(response)
                    appendLine()
                    appendLine("--- 耗时 ${elapsed / 1000}s ---")
                },
                executionTimeMs = elapsed
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Parasitic query failed", e)
            guiAgent.cancel()
            ToolResult(definition.name, false, progressLog.toString(),
                error = "寄生查询异常: ${e.message}")
        } finally {
            _isActive.value = false
        }
    }

    private fun buildSendGoal(config: HostAppConfig, prompt: String): String {
        val escapedPrompt = prompt.replace("\"", "'").take(500)
        return """打开${config.displayName}，在聊天输入框中输入以下内容并发送：
"$escapedPrompt"
步骤：1.启动${config.displayName} 2.找到底部输入框并点击 3.输入上述文字 4.点击发送按钮
发送成功后立即用finish报告"已发送"。不要等待回复。"""
    }

    private fun buildReadGoal(config: HostAppConfig): String {
        return """当前在${config.displayName}的聊天界面。请仔细阅读屏幕上AI助手最新的回复内容。
你的任务是把回复的完整文字内容用finish报告出来。
注意：
- 如果回复很长，先向上滑动查看完整内容，然后再finish
- 只读取AI助手的最新一条回复，不要包含用户的消息
- 用finish(message="回复的完整文字内容")报告"""
    }

    private suspend fun waitForHostNotification(config: HostAppConfig): Boolean {
        val waitStart = System.currentTimeMillis()
        return try {
            withTimeout(config.maxWaitMs) {
                KiwiNotificationListener.latestNotification
                    .filter { it != null && it.packageName == config.packageName && it.postTime > waitStart }
                    .first()
                true
            }
        } catch (_: TimeoutCancellationException) {
            Log.w(TAG, "Notification wait timed out after ${config.maxWaitMs / 1000}s")
            false
        }
    }

    private fun extractResponse(guiResult: String): String {
        return guiResult
            .removePrefix("完成: ").removePrefix("完成：")
            .removePrefix("任务完成: ").removePrefix("任务完成：")
            .removePrefix("已发送")
            .trim()
    }
}
