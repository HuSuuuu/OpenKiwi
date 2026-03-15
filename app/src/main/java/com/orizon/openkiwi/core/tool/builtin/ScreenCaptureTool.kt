package com.orizon.openkiwi.core.tool.builtin

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import com.orizon.openkiwi.core.tool.*
import com.orizon.openkiwi.service.KiwiAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScreenCaptureTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        name = "screen_capture",
        description = "Capture and analyze the current screen content. Returns a structured description of all visible UI elements with their positions, text, and states. Use this to understand what's currently displayed on screen before performing GUI operations.",
        category = ToolCategory.GUI.name,
        permissionLevel = PermissionLevel.NORMAL.name,
        parameters = mapOf(
            "detail_level" to ToolParamDef("string", "Level of detail: 'summary' for key elements only, 'full' for complete tree", false, "summary", listOf("summary", "full"))
        ),
        requiredParams = emptyList()
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult = withContext(Dispatchers.Main) {
        val service = KiwiAccessibilityService.instance ?: return@withContext ToolResult(
            toolName = definition.name, success = false, output = "",
            error = "Accessibility Service not running. Enable in Settings > Accessibility > OpenKiwi."
        )

        val detailLevel = params["detail_level"]?.toString() ?: "summary"
        val nodes = service.getScreenNodes()

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)

        val sb = StringBuilder()
        sb.appendLine("=== Screen Capture ===")
        sb.appendLine("Resolution: ${metrics.widthPixels}x${metrics.heightPixels}")
        sb.appendLine("Density: ${metrics.density}x (${metrics.densityDpi}dpi)")
        sb.appendLine()

        if (nodes.isEmpty()) {
            sb.appendLine("No UI elements detected. Screen may be locked or the current app has no accessibility nodes.")
            return@withContext ToolResult(toolName = definition.name, success = true, output = sb.toString())
        }

        when (detailLevel) {
            "summary" -> {
                val interactive = nodes.filter {
                    it.text.isNotBlank() || it.contentDescription.isNotBlank() || it.isClickable || it.isEditable
                }
                val clickables = interactive.count { it.isClickable }
                val editables = interactive.count { it.isEditable }

                sb.appendLine("${interactive.size} interactive (${clickables}C ${editables}E) / ${nodes.size} total")

                interactive.take(50).forEach { node ->
                    val cls = node.className.substringAfterLast('.')
                    val label = when {
                        node.text.isNotBlank() -> "\"${node.text.take(60)}\""
                        node.contentDescription.isNotBlank() -> "desc=\"${node.contentDescription.take(40)}\""
                        else -> ""
                    }
                    val flags = buildList {
                        if (node.isClickable) add("C")
                        if (node.isEditable) add("E")
                        if (node.isScrollable) add("S")
                    }.joinToString("")
                    sb.appendLine("$cls $label [$flags] (${node.bounds.centerX()},${node.bounds.centerY()})")
                }
            }
            "full" -> {
                sb.appendLine("UI Tree: ${nodes.size} nodes")
                nodes.take(120).forEach { node ->
                    val indent = "  ".repeat(node.depth.coerceAtMost(6))
                    val cls = node.className.substringAfterLast('.')
                    sb.append("$indent$cls")
                    if (node.text.isNotBlank()) sb.append(" \"${node.text.take(60)}\"")
                    if (node.contentDescription.isNotBlank()) sb.append(" d=\"${node.contentDescription.take(30)}\"")
                    if (node.viewId.isNotBlank()) sb.append(" #${node.viewId.substringAfterLast('/')}")
                    val flags = buildList {
                        if (node.isClickable) add("C")
                        if (node.isEditable) add("E")
                        if (node.isScrollable) add("S")
                    }.joinToString("")
                    if (flags.isNotBlank()) sb.append(" [$flags]")
                    sb.append(" (${node.bounds.centerX()},${node.bounds.centerY()})")
                    sb.appendLine()
                }
            }
        }

        ToolResult(toolName = definition.name, success = true, output = sb.toString())
    }
}
