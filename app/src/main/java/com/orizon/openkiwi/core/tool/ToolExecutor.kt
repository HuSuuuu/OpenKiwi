package com.orizon.openkiwi.core.tool

import com.orizon.openkiwi.data.local.dao.AuditLogDao
import com.orizon.openkiwi.data.local.entity.AuditLogEntity
import kotlinx.coroutines.withTimeout

class ToolExecutor(
    private val registry: ToolRegistry,
    private val auditLogDao: AuditLogDao? = null
) {
    var requireConfirmationForDangerous: Boolean = true
    var onConfirmationRequired: (suspend (Tool, Map<String, Any?>) -> Boolean)? = null

    suspend fun execute(
        toolName: String,
        params: Map<String, Any?>,
        sessionId: String? = null,
        timeoutMs: Long? = null
    ): ToolResult {
        val tool = registry.getTool(toolName)
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
                actionDetail = "$toolName(${params.entries.joinToString { "${it.key}=${it.value}" }})",
                result = if (result.success) "SUCCESS" else "FAILED: ${result.error}",
                permissionUsed = tool.definition.permissionLevel,
                sessionId = sessionId
            )
        )

        return result
    }
}
