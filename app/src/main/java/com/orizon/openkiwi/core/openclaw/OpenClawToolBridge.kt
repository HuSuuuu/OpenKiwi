package com.orizon.openkiwi.core.openclaw

import com.orizon.openkiwi.core.tool.Tool
import com.orizon.openkiwi.core.tool.ToolDefinition
import com.orizon.openkiwi.core.tool.ToolParamDef
import com.orizon.openkiwi.core.tool.ToolResult
import kotlinx.serialization.json.*

/**
 * Bridges a single OpenClaw extension tool to the OpenKiwi Tool interface.
 *
 * Two execution paths are supported:
 *   1. Gateway HTTP invocation (tools.invoke) — default for direct Gateway connections.
 *   2. MCP invocation — when the OpenClaw instance exposes plugins via MCP stdio server.
 *
 * Both are transparent to the agent: the tool registers with a prefixed name
 * (openclaw_<pluginId>_<toolName>) and converts params/results automatically.
 */
class OpenClawToolBridge(
    private val gatewayClient: OpenClawGatewayClient,
    private val spec: OpenClawToolSpec,
    private val sessionKey: String? = null
) : Tool {

    private val json = Json { ignoreUnknownKeys = true }

    override val definition: ToolDefinition by lazy {
        val (params, required) = parseInputSchema(spec.inputSchema)
        val prefix = spec.pluginId ?: "ext"
        ToolDefinition(
            name = "openclaw_${prefix}_${spec.name}",
            description = "[OpenClaw:${spec.pluginName ?: spec.pluginId ?: "remote"}] ${spec.description}",
            category = "OPENCLAW",
            permissionLevel = if (spec.ownerOnly) "DANGEROUS" else "NORMAL",
            parameters = params,
            requiredParams = required,
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
                        is List<*> -> put(key, buildJsonArray {
                            value.forEach { item ->
                                when (item) {
                                    is String -> add(item)
                                    is Number -> add(item.toDouble())
                                    is Boolean -> add(item)
                                    null -> add(JsonNull)
                                    else -> add(item.toString())
                                }
                            }
                        })
                        null -> put(key, JsonNull)
                        else -> put(key, value.toString())
                    }
                }
            }

            val result = gatewayClient.invokeTool(
                toolName = spec.name,
                args = argsObj,
                sessionKey = sessionKey
            )

            val output = extractTextContent(result)

            ToolResult(
                toolName = definition.name,
                success = true,
                output = output,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = definition.name,
                success = false,
                output = "",
                error = "OpenClaw tool error: ${e.message}",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    private fun extractTextContent(result: JsonElement?): String {
        if (result == null) return ""
        return when {
            result is JsonPrimitive -> result.content
            result is JsonObject && result.containsKey("content") -> {
                val content = result["content"]
                when {
                    content is JsonArray -> content.joinToString("\n") { elem ->
                        val obj = elem.jsonObject
                        when (obj["type"]?.jsonPrimitive?.content) {
                            "text" -> obj["text"]?.jsonPrimitive?.content ?: ""
                            "image" -> "[image]"
                            else -> obj.toString()
                        }
                    }
                    content is JsonPrimitive -> content.content
                    else -> content.toString()
                }
            }
            result is JsonObject && result.containsKey("result") ->
                extractTextContent(result["result"])
            else -> result.toString()
        }
    }

    private fun parseInputSchema(schema: JsonElement?): Pair<Map<String, ToolParamDef>, List<String>> {
        if (schema == null) return emptyMap<String, ToolParamDef>() to emptyList()

        val params = mutableMapOf<String, ToolParamDef>()
        val required = mutableListOf<String>()
        runCatching {
            val obj = schema.jsonObject
            val properties = obj["properties"]?.jsonObject ?: return@runCatching
            val requiredArr = obj["required"]?.jsonArray

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
