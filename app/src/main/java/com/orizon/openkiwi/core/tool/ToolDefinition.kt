package com.orizon.openkiwi.core.tool

import kotlinx.serialization.Serializable

enum class PermissionLevel {
    NORMAL,
    DANGEROUS,
    SENSITIVE
}

enum class ToolCategory {
    SYSTEM,
    GUI,
    NETWORK,
    FILE,
    COMMUNICATION,
    DEVICE,
    CODE_EXECUTION,
    SEARCH,
    CUSTOM
}

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val category: String,
    val permissionLevel: String = "NORMAL",
    val parameters: Map<String, ToolParamDef> = emptyMap(),
    val requiredParams: List<String> = emptyList(),
    val returnDescription: String = "",
    val isEnabled: Boolean = true,
    val timeoutMs: Long = 30_000
)

@Serializable
data class ToolParamDef(
    val type: String,
    val description: String,
    val required: Boolean = false,
    val defaultValue: String? = null,
    val enumValues: List<String>? = null
)

data class ToolResult(
    val toolName: String,
    val success: Boolean,
    val output: String,
    val error: String? = null,
    val executionTimeMs: Long = 0,
    val artifacts: List<ToolArtifact> = emptyList()
)

data class ToolArtifact(
    val filePath: String,
    val displayName: String = java.io.File(filePath).name,
    val mimeType: String? = null,
    val sizeBytes: Long? = null
)

interface ToolContext {
    suspend fun callTool(name: String, params: Map<String, Any?>): ToolResult
    fun listToolNames(): List<String>
}

interface Tool {
    val definition: ToolDefinition
    val supportsStreaming: Boolean get() = false
    var toolContext: ToolContext?
        get() = null
        set(_) {}
    suspend fun execute(params: Map<String, Any?>): ToolResult
    suspend fun executeStreaming(params: Map<String, Any?>): kotlinx.coroutines.flow.Flow<String> {
        val result = execute(params)
        return kotlinx.coroutines.flow.flowOf(result.output)
    }
    fun checkPermission(): Boolean = true
}
