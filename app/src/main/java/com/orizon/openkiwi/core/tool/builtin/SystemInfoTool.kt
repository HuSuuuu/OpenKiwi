package com.orizon.openkiwi.core.tool.builtin

import android.os.Build
import com.orizon.openkiwi.core.tool.*

class SystemInfoTool : Tool {
    override val definition = ToolDefinition(
        name = "get_system_info",
        description = "Get Android device system information including model, OS version, memory, etc.",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.NORMAL.name
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val info = buildString {
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Hardware: ${Build.HARDWARE}")
            appendLine("Product: ${Build.PRODUCT}")
            appendLine("Board: ${Build.BOARD}")
            val runtime = Runtime.getRuntime()
            appendLine("Available Processors: ${runtime.availableProcessors()}")
            appendLine("Max Memory: ${runtime.maxMemory() / 1024 / 1024} MB")
            appendLine("Total Memory: ${runtime.totalMemory() / 1024 / 1024} MB")
            appendLine("Free Memory: ${runtime.freeMemory() / 1024 / 1024} MB")
        }
        return ToolResult(toolName = definition.name, success = true, output = info)
    }
}
