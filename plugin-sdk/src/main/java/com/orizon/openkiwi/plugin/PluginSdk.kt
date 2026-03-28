package com.orizon.openkiwi.plugin

import kotlinx.serialization.Serializable

/**
 * OpenKiwi Plugin SDK — interfaces for building plugins.
 *
 * Plugins are APK/DEX files placed in the app's plugin directory.
 * Each plugin must implement [PluginEntry] and declare a plugin.json manifest.
 */

enum class PermissionLevel {
    NORMAL, DANGEROUS, SENSITIVE
}

@Serializable
data class ToolParamDef(
    val type: String,
    val description: String,
    val required: Boolean = false,
    val defaultValue: String? = null,
    val enumValues: List<String>? = null
)

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val category: String = "CUSTOM",
    val permissionLevel: String = "NORMAL",
    val parameters: Map<String, ToolParamDef> = emptyMap(),
    val requiredParams: List<String> = emptyList(),
    val returnDescription: String = "",
    val isEnabled: Boolean = true,
    val timeoutMs: Long = 30_000
)

data class ToolResult(
    val toolName: String,
    val success: Boolean,
    val output: String,
    val error: String? = null,
    val executionTimeMs: Long = 0
)

interface PluginTool {
    val definition: ToolDefinition
    suspend fun execute(params: Map<String, Any?>): ToolResult
}

interface PluginEntry {
    val id: String
    val name: String
    val version: String
    val description: String
    val requiredPermissions: List<String>
        get() = emptyList()

    fun onLoad()
    fun onUnload()
    fun getTools(): List<PluginTool>
}

@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val entryClass: String,
    val minSdkVersion: Int = 1,
    val permissions: List<String> = emptyList(),
    val tools: List<PluginToolDeclaration> = emptyList()
)

@Serializable
data class PluginToolDeclaration(
    val name: String,
    val description: String = ""
)
