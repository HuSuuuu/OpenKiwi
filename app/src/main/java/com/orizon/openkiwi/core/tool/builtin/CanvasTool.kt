package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.canvas.CanvasBus
import com.orizon.openkiwi.core.canvas.CanvasEvent
import com.orizon.openkiwi.core.canvas.CanvasSnapshot
import com.orizon.openkiwi.core.tool.PermissionLevel
import com.orizon.openkiwi.core.tool.Tool
import com.orizon.openkiwi.core.tool.ToolCategory
import com.orizon.openkiwi.core.tool.ToolDefinition
import com.orizon.openkiwi.core.tool.ToolParamDef
import com.orizon.openkiwi.core.tool.ToolResult

/**
 * 在应用内「画布」WebView 中展示富 HTML/图表/表单；用户可通过 JS Bridge 将操作回传给对话。
 */
class CanvasTool : Tool {

    override val definition = ToolDefinition(
        name = "canvas",
        description = "在独立画布页展示富交互内容。action=push 推送完整 HTML；update 执行 JavaScript；clear 清空。适合表格、可视化、表单、仪表盘。",
        category = ToolCategory.CUSTOM.name,
        permissionLevel = PermissionLevel.NORMAL.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "push | update | clear", required = true),
            "html" to ToolParamDef("string", "完整 HTML 文档或片段（push 时）"),
            "javascript" to ToolParamDef("string", "要在当前页面执行的 JS（update 时，可用别名 js）")
        ),
        requiredParams = listOf("action"),
        returnDescription = "操作结果说明",
        timeoutMs = 30_000
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString()?.trim()?.lowercase().orEmpty()
        return when (action) {
            "push" -> {
                val html = params["html"]?.toString().orEmpty()
                if (html.isBlank()) {
                    return ToolResult("canvas", false, "", "缺少 html 参数")
                }
                CanvasSnapshot.lastHtml = html
                CanvasBus.tryEmit(CanvasEvent.PushHtml(html))
                ToolResult(
                    "canvas", true,
                    "已推送到画布。用户可在侧栏「画布」或聊天中的「打开画布」查看。"
                )
            }
            "update" -> {
                val js = params["javascript"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: params["js"]?.toString()?.takeIf { it.isNotBlank() }
                if (js == null) {
                    return ToolResult("canvas", false, "", "缺少 javascript 或 js 参数")
                }
                CanvasBus.tryEmit(CanvasEvent.EvalJs(js))
                ToolResult("canvas", true, "已在画布页执行 JavaScript。")
            }
            "clear" -> {
                CanvasSnapshot.lastHtml = null
                CanvasBus.tryEmit(CanvasEvent.Clear)
                ToolResult("canvas", true, "画布已清空。")
            }
            else -> ToolResult("canvas", false, "", "未知 action，请使用 push、update 或 clear")
        }
    }
}
