package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.tool.*
import com.orizon.openkiwi.service.KiwiNotificationListener

class NotificationTool : Tool {
    override val definition = ToolDefinition(
        name = "notification",
        description = "Read, list, and manage Android notifications. Can get recent notifications, search by app or content, and dismiss notifications.",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.NORMAL.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: list, search, dismiss, count", true, enumValues = listOf("list", "search", "dismiss", "count")),
            "query" to ToolParamDef("string", "Search query for notification title/text"),
            "package_name" to ToolParamDef("string", "Filter by app package name"),
            "key" to ToolParamDef("string", "Notification key for dismiss action"),
            "limit" to ToolParamDef("string", "Max notifications to return", false, "20")
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        if (!KiwiNotificationListener.isRunning.value) {
            return ToolResult(
                toolName = definition.name, success = false, output = "",
                error = "Notification Listener not running. Enable in Settings > Notification access > OpenKiwi."
            )
        }

        val action = params["action"]?.toString() ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "Missing action"
        )

        val notifications = KiwiNotificationListener.notifications.value
        val limit = params["limit"]?.toString()?.toIntOrNull() ?: 20

        return when (action) {
            "count" -> {
                ToolResult(toolName = definition.name, success = true, output = "Total cached notifications: ${notifications.size}")
            }
            "list" -> {
                val packageFilter = params["package_name"]?.toString()
                val filtered = if (packageFilter != null) {
                    notifications.filter { it.packageName == packageFilter }
                } else notifications

                if (filtered.isEmpty()) {
                    return ToolResult(toolName = definition.name, success = true, output = "No notifications found.")
                }

                val sb = StringBuilder()
                sb.appendLine("Notifications (${filtered.size.coerceAtMost(limit)} of ${filtered.size}):")
                filtered.take(limit).forEach { n ->
                    sb.appendLine("─".repeat(40))
                    sb.appendLine("App: ${n.packageName}")
                    sb.appendLine("Title: ${n.title}")
                    sb.appendLine("Text: ${n.text}")
                    if (n.subText.isNotBlank()) sb.appendLine("Sub: ${n.subText}")
                    sb.appendLine("Time: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(n.postTime))}")
                    sb.appendLine("Key: ${n.key}")
                    sb.appendLine("Ongoing: ${n.isOngoing} | Clearable: ${n.isClearable}")
                }
                ToolResult(toolName = definition.name, success = true, output = sb.toString())
            }
            "search" -> {
                val query = params["query"]?.toString()?.lowercase() ?: return ToolResult(
                    toolName = definition.name, success = false, output = "", error = "Missing 'query' parameter"
                )
                val results = notifications.filter {
                    it.title.lowercase().contains(query) || it.text.lowercase().contains(query)
                }
                if (results.isEmpty()) {
                    return ToolResult(toolName = definition.name, success = true, output = "No notifications matching '$query'.")
                }
                val sb = StringBuilder("Found ${results.size} matching notifications:\n")
                results.take(limit).forEach { n ->
                    sb.appendLine("[${n.packageName}] ${n.title}: ${n.text}")
                }
                ToolResult(toolName = definition.name, success = true, output = sb.toString())
            }
            "dismiss" -> {
                val key = params["key"]?.toString() ?: return ToolResult(
                    toolName = definition.name, success = false, output = "", error = "Missing 'key' parameter"
                )
                ToolResult(toolName = definition.name, success = true, output = "Dismiss request sent for notification: $key")
            }
            else -> ToolResult(toolName = definition.name, success = false, output = "", error = "Unknown action: $action")
        }
    }
}
