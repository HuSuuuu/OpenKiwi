package com.orizon.openkiwi.core.tool.builtin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.orizon.openkiwi.core.tool.*

class AppManagerTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        name = "app_manager",
        description = "List installed apps, launch apps, or get app information",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.NORMAL.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: list, launch, info", true, enumValues = listOf("list", "launch", "info")),
            "package_name" to ToolParamDef("string", "Package name (required for launch/info)")
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "Missing action"
        )
        val pm = context.packageManager

        return when (action) {
            "list" -> {
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                    .map { "${pm.getApplicationLabel(it)} (${it.packageName})" }
                    .sorted()
                ToolResult(
                    toolName = definition.name, success = true,
                    output = "Installed apps (${apps.size}):\n${apps.joinToString("\n")}"
                )
            }
            "launch" -> {
                val pkg = params["package_name"]?.toString() ?: return ToolResult(
                    toolName = definition.name, success = false, output = "", error = "Missing package_name"
                )
                val intent = pm.getLaunchIntentForPackage(pkg) ?: return ToolResult(
                    toolName = definition.name, success = false, output = "", error = "App not found: $pkg"
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ToolResult(toolName = definition.name, success = true, output = "Launched $pkg")
            }
            "info" -> {
                val pkg = params["package_name"]?.toString() ?: return ToolResult(
                    toolName = definition.name, success = false, output = "", error = "Missing package_name"
                )
                runCatching {
                    val appInfo = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
                    val pkgInfo = pm.getPackageInfo(pkg, 0)
                    val info = buildString {
                        appendLine("Name: ${pm.getApplicationLabel(appInfo)}")
                        appendLine("Package: $pkg")
                        appendLine("Version: ${pkgInfo.versionName}")
                        appendLine("Target SDK: ${appInfo.targetSdkVersion}")
                        appendLine("Min SDK: ${appInfo.minSdkVersion}")
                    }
                    ToolResult(toolName = definition.name, success = true, output = info)
                }.getOrElse { e ->
                    ToolResult(toolName = definition.name, success = false, output = "", error = e.message)
                }
            }
            else -> ToolResult(toolName = definition.name, success = false, output = "", error = "Unknown action: $action")
        }
    }
}
