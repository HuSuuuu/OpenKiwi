package com.orizon.openkiwi.core.agent

import com.orizon.openkiwi.core.gui.AppPackages
import com.orizon.openkiwi.core.gui.GuiAgent
import com.orizon.openkiwi.core.tool.PermissionLevel
import com.orizon.openkiwi.core.tool.Tool
import com.orizon.openkiwi.core.tool.ToolCategory
import com.orizon.openkiwi.core.tool.ToolDefinition
import com.orizon.openkiwi.core.tool.ToolParamDef
import com.orizon.openkiwi.core.tool.ToolResult
import com.orizon.openkiwi.service.KiwiAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-level helper for WeChat / QQ / Telegram-style messaging UIs via [GuiAgent].
 */
class AppReplyBotTool(private val guiAgent: GuiAgent) : Tool {

    override val definition = ToolDefinition(
        name = "app_reply_bot",
        description = """Use GUI automation to operate a messaging app (WeChat, QQ, Telegram, etc.):
open chats, read context on screen, type a reply, and optionally send or leave as draft.
Prefer this when the user wants to compose or send messages inside 微信/QQ/Telegram without using notification inline-reply.""",
        category = ToolCategory.GUI.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "app" to ToolParamDef("string", "wechat | qq | telegram | signal (or Chinese names 微信/QQ)", true),
            "instruction" to ToolParamDef("string", "What to do in natural language, e.g. '打开与张三的聊天并回复：晚上见'", true),
            "mode" to ToolParamDef("string", "send = tap send; draft = type only (default send)", false)
        ),
        requiredParams = listOf("app", "instruction"),
        timeoutMs = 480_000L
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        if (KiwiAccessibilityService.instance == null) {
            return@withContext ToolResult(
                toolName = definition.name,
                success = false,
                output = "",
                error = "无障碍服务未开启，无法操作聊天应用。"
            )
        }
        val appKey = params["app"]?.toString()?.trim().orEmpty()
        val instruction = params["instruction"]?.toString()?.trim().orEmpty()
        val mode = params["mode"]?.toString()?.trim()?.lowercase() ?: "send"
        if (appKey.isBlank() || instruction.isBlank()) {
            return@withContext ToolResult(definition.name, false, "", "需要 app 与 instruction")
        }

        val pkg = resolvePackage(appKey)
            ?: return@withContext ToolResult(definition.name, false, "", "未知应用: $appKey")

        val sendHint = when (mode) {
            "draft", "仅输入", "草稿" -> "不要点击发送，只输入文字留在输入框。"
            else -> "输入完成后点击发送。"
        }

        val goal = buildString {
            appendLine("目标应用包名: $pkg")
            appendLine("任务: $instruction")
            appendLine(sendHint)
            appendLine("若需要登录或权限弹窗，停止并说明原因。")
        }

        return@withContext try {
            val result = guiAgent.executeTask(goal = goal)
            ToolResult(
                toolName = definition.name,
                success = !result.startsWith("错误") && !result.startsWith("GUI任务异常"),
                output = result
            )
        } catch (e: Exception) {
            guiAgent.cancel()
            ToolResult(definition.name, false, "", e.message ?: "GUI 异常")
        }
    }

    private fun resolvePackage(appKey: String): String? {
        val k = appKey.lowercase()
        return when {
            k.contains("wechat") || k.contains("微信") -> "com.tencent.mm"
            k.contains("qq") -> "com.tencent.mobileqq"
            k.contains("telegram") -> "org.telegram.messenger"
            k.contains("signal") -> "org.thoughtcrime.securesms"
            else -> AppPackages.getPackageName(appKey)
        }
    }
}
