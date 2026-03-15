package com.orizon.openkiwi.core.gui

import com.orizon.openkiwi.core.tool.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GuiAgentTool(private val guiAgent: GuiAgent) : Tool {
    override val definition = ToolDefinition(
        name = "gui_agent",
        description = """Autonomous GUI agent that operates the phone screen to complete tasks.
Give it a goal in natural language (e.g. "打开微信给张三发消息说你好") and it will:
1. Take screenshots to see the screen
2. AI-analyze the UI to plan next action
3. Execute actions (tap, swipe, type, launch apps...)
4. Repeat until task is complete

Use this for any task that requires navigating apps, clicking buttons, filling forms, etc.
The agent uses normalized coordinates and vision AI for robust operation.
Prefer this over manual gui_operation for complex multi-step UI tasks.""",
        category = ToolCategory.GUI.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "goal" to ToolParamDef("string", "The task to accomplish, in natural language (e.g. '打开设置查看电池用量')", true)
        ),
        requiredParams = listOf("goal"),
        timeoutMs = 600_000L
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val goal = params["goal"]?.toString()
        if (goal.isNullOrBlank()) {
            return@withContext ToolResult(toolName = definition.name, success = false, output = "", error = "Missing 'goal' parameter")
        }

        if (com.orizon.openkiwi.service.KiwiAccessibilityService.instance == null) {
            return@withContext ToolResult(
                toolName = definition.name,
                success = false,
                output = "",
                error = "无障碍服务未开启。请告知用户：需要在系统设置 → 无障碍 → OpenKiwi 中开启无障碍服务，才能执行屏幕操作任务。"
            )
        }

        val progressLog = StringBuilder()

        try {
            val result = guiAgent.executeTask(
                goal = goal,
                onProgress = { msg -> progressLog.appendLine(msg) }
            )

            ToolResult(
                toolName = definition.name,
                success = !result.startsWith("错误") && !result.startsWith("GUI任务异常"),
                output = buildString {
                    appendLine("任务结果: $result")
                    appendLine()
                    appendLine("执行过程:")
                    append(progressLog)
                }
            )
        } catch (e: Exception) {
            guiAgent.cancel()
            ToolResult(
                toolName = definition.name,
                success = false,
                output = progressLog.toString(),
                error = "GUI Agent 异常: ${e.message}"
            )
        }
    }
}
