package com.orizon.openkiwi.core.tool.builtin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.orizon.openkiwi.core.tool.*

class ClipboardTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        name = "clipboard",
        description = "Read from or write to the system clipboard",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.NORMAL.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action to perform", true, enumValues = listOf("read", "write")),
            "text" to ToolParamDef("string", "Text to write (required for write action)")
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "Missing action parameter"
        )
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        return when (action) {
            "read" -> {
                val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                ToolResult(toolName = definition.name, success = true, output = text.ifEmpty { "(clipboard is empty)" })
            }
            "write" -> {
                val text = params["text"]?.toString() ?: return ToolResult(
                    toolName = definition.name, success = false, output = "", error = "Missing text parameter"
                )
                clipboard.setPrimaryClip(ClipData.newPlainText("OpenKiwi", text))
                ToolResult(toolName = definition.name, success = true, output = "Text copied to clipboard")
            }
            else -> ToolResult(toolName = definition.name, success = false, output = "", error = "Unknown action: $action")
        }
    }
}
