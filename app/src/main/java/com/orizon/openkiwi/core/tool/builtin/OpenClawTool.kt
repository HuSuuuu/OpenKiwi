package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.openclaw.OpenClawPluginManager
import com.orizon.openkiwi.core.openclaw.OpenClawSavedGateway
import com.orizon.openkiwi.core.tool.Tool
import com.orizon.openkiwi.core.tool.ToolCategory
import com.orizon.openkiwi.core.tool.ToolDefinition
import com.orizon.openkiwi.core.tool.ToolParamDef
import com.orizon.openkiwi.core.tool.ToolResult
import com.orizon.openkiwi.data.preferences.UserPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/**
 * AI-facing tool for managing OpenClaw ecosystem connections.
 *
 * Actions:
 *   connect       — Connect to an OpenClaw Gateway and import its tools.
 *   disconnect    — Disconnect from an OpenClaw instance.
 *   list          — List connected OpenClaw instances and their tool counts.
 *   refresh       — Re-scan tools from a connected instance.
 *   tools         — List all tools imported from a specific OpenClaw instance.
 *   chat          — Send a message through an OpenClaw instance's chat.
 *   call          — Call an arbitrary Gateway RPC method.
 *   status        — Check connection status of a specific instance.
 */
class OpenClawTool(
    private val pluginManager: OpenClawPluginManager,
    private val userPreferences: UserPreferences? = null
) : Tool {

    private val json = Json { encodeDefaults = false; ignoreUnknownKeys = true }

    override val definition = ToolDefinition(
        name = "openclaw",
        description = "Connect to OpenClaw Gateway instances to import their extension tools " +
                "(web search, browser, code execution, channel messaging, etc). " +
                "OpenClaw extensions become native OpenKiwi tools after connection.",
        category = ToolCategory.NETWORK.name,
        permissionLevel = "NORMAL",
        parameters = mapOf(
            "action" to ToolParamDef(
                "string",
                "Action: connect, disconnect, list, refresh, tools, chat, call, status",
                required = true,
                enumValues = listOf("connect", "disconnect", "list", "refresh", "tools", "chat", "call", "status")
            ),
            "url" to ToolParamDef("string", "Gateway URL (for connect). e.g. http://192.168.1.100:3000"),
            "token" to ToolParamDef("string", "Auth token for the Gateway (optional)"),
            "id" to ToolParamDef("string", "Instance id (auto-generated on connect, used for other actions)"),
            "session_key" to ToolParamDef("string", "Session key for chat/tools.effective"),
            "message" to ToolParamDef("string", "Chat message content (for chat action)"),
            "method" to ToolParamDef("string", "Gateway RPC method name (for call action)"),
            "params" to ToolParamDef("string", "JSON string of RPC params (for call action)")
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString()
            ?: return errorResult("Missing action")

        return runCatching {
            when (action) {
                "connect" -> handleConnect(params)
                "disconnect" -> handleDisconnect(params)
                "list" -> handleList()
                "refresh" -> handleRefresh(params)
                "tools" -> handleTools(params)
                "chat" -> handleChat(params)
                "call" -> handleCall(params)
                "status" -> handleStatus(params)
                else -> errorResult("Unknown action: $action")
            }
        }.getOrElse { e ->
            errorResult(e.message ?: "Unknown error")
        }
    }

    private suspend fun handleConnect(params: Map<String, Any?>): ToolResult {
        val url = params["url"]?.toString()
            ?: return errorResult("Missing 'url' — provide the OpenClaw Gateway URL")
        val token = params["token"]?.toString()
        val sessionKey = params["session_key"]?.toString()

        val id = url.replace(Regex("[^a-zA-Z0-9]"), "_").take(32)

        val result = pluginManager.connect(id, url, token, sessionKey)
        return result.fold(
            onSuccess = { instance ->
                persistGateways()
                successResult(buildString {
                    appendLine("Connected to OpenClaw Gateway: ${instance.url}")
                    appendLine("Instance ID: ${instance.id}")
                    appendLine("Tools imported: ${instance.toolCount}")
                    appendLine()
                    appendLine("All imported tools are now available as regular tools with prefix 'openclaw_'.")
                    appendLine("Use 'openclaw action=tools id=${instance.id}' to see the full list.")
                })
            },
            onFailure = { e ->
                errorResult("Connection failed: ${e.message}")
            }
        )
    }

    private suspend fun handleDisconnect(params: Map<String, Any?>): ToolResult {
        val id = params["id"]?.toString()
            ?: return errorResult("Missing 'id' — specify which instance to disconnect")
        pluginManager.disconnect(id)
        persistGateways()
        return successResult("Disconnected from OpenClaw instance '$id'. All imported tools removed.")
    }

    private fun handleList(): ToolResult {
        val instances = pluginManager.listInstances()
        if (instances.isEmpty()) {
            return successResult("No OpenClaw instances connected.\n\nUse 'openclaw action=connect url=<gateway_url>' to connect.")
        }
        val sb = StringBuilder("Connected OpenClaw instances:\n\n")
        for (inst in instances) {
            sb.appendLine("  [${inst.id}] ${inst.url}")
            sb.appendLine("    Tools: ${inst.toolCount}")
            sb.appendLine("    Connected: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(inst.connectedAt))}")
            if (inst.error != null) sb.appendLine("    Error: ${inst.error}")
            sb.appendLine()
        }
        return successResult(sb.toString())
    }

    private suspend fun handleRefresh(params: Map<String, Any?>): ToolResult {
        val id = params["id"]?.toString()
            ?: return errorResult("Missing 'id'")
        val sessionKey = params["session_key"]?.toString()
        val count = pluginManager.refreshTools(id, sessionKey)
        return successResult("Refreshed: $count tools now available from instance '$id'.")
    }

    private fun handleTools(params: Map<String, Any?>): ToolResult {
        val id = params["id"]?.toString()
            ?: return errorResult("Missing 'id'")
        val tools = pluginManager.getToolsForInstance(id)
        if (tools.isEmpty()) {
            return successResult("No tools from instance '$id'.")
        }
        val sb = StringBuilder("Tools from OpenClaw instance '$id' (${tools.size}):\n\n")
        for (tool in tools) {
            sb.appendLine("  • ${tool.definition.name}")
            sb.appendLine("    ${tool.definition.description.take(120)}")
        }
        return successResult(sb.toString())
    }

    private suspend fun handleChat(params: Map<String, Any?>): ToolResult {
        val id = params["id"]?.toString()
            ?: return errorResult("Missing 'id'")
        val sessionKey = params["session_key"]?.toString()
            ?: return errorResult("Missing 'session_key' — required for chat")
        val message = params["message"]?.toString()
            ?: return errorResult("Missing 'message'")
        val result = pluginManager.sendChat(id, sessionKey, message)
        return successResult(result)
    }

    private suspend fun handleCall(params: Map<String, Any?>): ToolResult {
        val id = params["id"]?.toString()
            ?: return errorResult("Missing 'id'")
        val method = params["method"]?.toString()
            ?: return errorResult("Missing 'method'")
        val paramsJson = params["params"]?.toString()
        val rpcParams = paramsJson?.let {
            runCatching { Json.parseToJsonElement(it) }.getOrNull()
        }
        val result = pluginManager.callMethod(id, method, rpcParams)
        return successResult(result?.toString() ?: "null")
    }

    private fun handleStatus(params: Map<String, Any?>): ToolResult {
        val id = params["id"]?.toString()
            ?: return errorResult("Missing 'id'")
        val client = pluginManager.getClient(id)
            ?: return errorResult("Instance '$id' not found")
        val inst = pluginManager.listInstances().find { it.id == id }
        return successResult(buildString {
            appendLine("Instance: $id")
            appendLine("URL: ${inst?.url ?: "?"}")
            appendLine("Status: ${client.status.value}")
            appendLine("Connected: ${client.isConnected}")
            appendLine("Tools: ${inst?.toolCount ?: 0}")
            inst?.error?.let { appendLine("Last error: $it") }
        })
    }

    private suspend fun persistGateways() {
        userPreferences ?: return
        val entries = pluginManager.listInstances().map {
            OpenClawSavedGateway(it.id, it.url, it.token)
        }
        userPreferences.setString("openclaw_gateways", json.encodeToString(entries))
    }

    private fun successResult(output: String) = ToolResult(
        toolName = definition.name, success = true, output = output
    )

    private fun errorResult(msg: String) = ToolResult(
        toolName = definition.name, success = false, output = "", error = msg
    )
}
