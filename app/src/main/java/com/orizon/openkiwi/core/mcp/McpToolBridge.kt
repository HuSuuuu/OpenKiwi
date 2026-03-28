package com.orizon.openkiwi.core.mcp

import com.orizon.openkiwi.core.tool.Tool
import com.orizon.openkiwi.core.tool.ToolDefinition
import com.orizon.openkiwi.core.tool.ToolParamDef
import com.orizon.openkiwi.core.tool.ToolResult
import kotlinx.serialization.json.*

/**
 * Bridges an MCP server tool to the OpenKiwi Tool interface.
 * Each McpToolBridge instance wraps a single tool exposed by an MCP server.
 */
class McpToolBridge(
    private val mcpClient: McpClient,
    private val toolInfo: McpToolInfo,
    private val serverName: String
) : Tool {

    private val json = Json { ignoreUnknownKeys = true }

    override val definition: ToolDefinition by lazy {
        val params = parseInputSchema(toolInfo.inputSchemaJson)
        ToolDefinition(
            name = "mcp_${serverName}_${toolInfo.name}",
            description = "[MCP:$serverName] ${toolInfo.description}",
            category = "MCP",
            permissionLevel = "NORMAL",
            parameters = params.first,
            requiredParams = params.second,
            timeoutMs = 60_000
        )
    }

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val startTime = System.currentTimeMillis()
        return try {
            val argsObj = buildJsonObject {
                for ((key, value) in params) {
                    when (value) {
                        is String -> put(key, value)
                        is Number -> put(key, value.toDouble())
                        is Boolean -> put(key, value)
                        null -> put(key, JsonNull)
                        else -> put(key, value.toString())
                    }
                }
            }
            val result = mcpClient.callTool(toolInfo.name, argsObj)
            ToolResult(
                toolName = definition.name,
                success = true,
                output = result,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = definition.name,
                success = false,
                output = "",
                error = "MCP tool error: ${e.message}",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    private fun parseInputSchema(schemaJson: String): Pair<Map<String, ToolParamDef>, List<String>> {
        val params = mutableMapOf<String, ToolParamDef>()
        val required = mutableListOf<String>()
        runCatching {
            val schema = json.parseToJsonElement(schemaJson).jsonObject
            val properties = schema["properties"]?.jsonObject ?: return@runCatching
            val requiredArr = schema["required"]?.jsonArray

            requiredArr?.forEach { elem ->
                elem.jsonPrimitive.content.let { required.add(it) }
            }

            for ((name, propElem) in properties) {
                val prop = propElem.jsonObject
                params[name] = ToolParamDef(
                    type = prop["type"]?.jsonPrimitive?.content ?: "string",
                    description = prop["description"]?.jsonPrimitive?.content ?: "",
                    required = name in required,
                    enumValues = prop["enum"]?.jsonArray?.map { it.jsonPrimitive.content }
                )
            }
        }
        return params to required
    }
}
