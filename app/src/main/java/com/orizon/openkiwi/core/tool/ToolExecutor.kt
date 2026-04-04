package com.orizon.openkiwi.core.tool

import com.orizon.openkiwi.data.local.dao.AuditLogDao
import com.orizon.openkiwi.data.local.entity.AuditLogEntity
import kotlinx.coroutines.withTimeout

class ToolExecutor(
    private val registry: ToolRegistry,
    private val auditLogDao: AuditLogDao? = null
) : ToolContext {
    var requireConfirmationForDangerous: Boolean = true
    var onConfirmationRequired: (suspend (Tool, Map<String, Any?>) -> Boolean)? = null

    override suspend fun callTool(name: String, params: Map<String, Any?>): ToolResult {
        return execute(name, params)
    }

    override fun listToolNames(): List<String> {
        return registry.getEnabledTools().map { it.definition.name }
    }

    companion object {
        /**
         * 系统提示写的是 code_execution，历史上工具名为 code_execute；模型任一名称都应能路由到同一实现。
         */
        private val TOOL_NAME_ALIASES: Map<String, String> = mapOf(
            "code_execute" to "code_execution"
        )
    }

    suspend fun execute(
        toolName: String,
        params: Map<String, Any?>,
        sessionId: String? = null,
        timeoutMs: Long? = null
    ): ToolResult {
        val canonical = TOOL_NAME_ALIASES[toolName] ?: toolName
        val tool = registry.getTool(canonical) ?: registry.getTool(toolName)
            ?: return ToolResult(
                toolName = toolName,
                success = false,
                output = "",
                error = "Tool not found: $toolName"
            )

        val permLevel = runCatching {
            PermissionLevel.valueOf(tool.definition.permissionLevel)
        }.getOrDefault(PermissionLevel.NORMAL)

        if (permLevel != PermissionLevel.NORMAL && requireConfirmationForDangerous && onConfirmationRequired != null) {
            val confirmed = onConfirmationRequired!!.invoke(tool, params)
            if (!confirmed) {
                return ToolResult(
                    toolName = toolName,
                    success = false,
                    output = "",
                    error = "User denied permission for ${permLevel.name} operation"
                )
            }
        }

        val effectiveTimeout = timeoutMs ?: tool.definition.timeoutMs
        tool.toolContext = this

        val startTime = System.currentTimeMillis()
        val result = runCatching {
            withTimeout(effectiveTimeout) {
                tool.execute(params)
            }
        }.getOrElse { e ->
            ToolResult(
                toolName = toolName,
                success = false,
                output = "",
                error = e.message ?: "Unknown error",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        auditLogDao?.insertLog(
            AuditLogEntity(
                actor = "agent",
                actionType = "TOOL_CALL",
                actionDetail = "$canonical(${params.entries.joinToString { "${it.key}=${it.value}" }})",
                result = if (result.success) "SUCCESS" else "FAILED: ${result.error}",
                permissionUsed = tool.definition.permissionLevel,
                sessionId = sessionId
            )
        )

        return result
    }
}
