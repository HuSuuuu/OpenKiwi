package com.orizon.openkiwi.core.mcp

import android.util.Log
import com.orizon.openkiwi.core.tool.Tool
import com.orizon.openkiwi.core.tool.ToolRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

data class McpServerState(
    val config: McpServerConfig,
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val toolCount: Int = 0,
    val error: String? = null
)

enum class ConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

class McpManager(
    private val toolRegistry: ToolRegistry,
    private val httpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "McpManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clients = ConcurrentHashMap<String, McpClient>()
    private val registeredTools = ConcurrentHashMap<String, MutableList<String>>()

    private val _serverStates = MutableStateFlow<Map<String, McpServerState>>(emptyMap())
    val serverStates: StateFlow<Map<String, McpServerState>> = _serverStates.asStateFlow()

    fun connectServer(config: McpServerConfig) {
        updateState(config.id, McpServerState(config, ConnectionStatus.CONNECTING))
        scope.launch {
            try {
                val client = McpClient(config, httpClient)
                client.connect()
                clients[config.id] = client

                val tools = client.listTools()
                val bridgedTools = mutableListOf<String>()
                for (toolInfo in tools) {
                    val bridge = McpToolBridge(client, toolInfo, config.name)
                    toolRegistry.register(bridge)
                    bridgedTools.add(bridge.definition.name)
                }
                registeredTools[config.id] = bridgedTools

                updateState(config.id, McpServerState(
                    config, ConnectionStatus.CONNECTED, toolCount = tools.size
                ))
                Log.i(TAG, "Connected to MCP server '${config.name}': ${tools.size} tools")
            } catch (e: Exception) {
                updateState(config.id, McpServerState(
                    config, ConnectionStatus.ERROR, error = e.message
                ))
                Log.e(TAG, "Failed to connect to MCP server '${config.name}'", e)
            }
        }
    }

    fun disconnectServer(serverId: String) {
        registeredTools.remove(serverId)?.forEach { toolName ->
            toolRegistry.unregister(toolName)
        }
        clients.remove(serverId)?.disconnect()
        val states = _serverStates.value.toMutableMap()
        states.remove(serverId)
        _serverStates.value = states
    }

    fun disconnectAll() {
        val ids = clients.keys.toList()
        ids.forEach { disconnectServer(it) }
        scope.cancel()
    }

    fun connectAll(configs: List<McpServerConfig>) {
        configs.filter { it.isEnabled }.forEach { connectServer(it) }
    }

    fun getToolsForServer(serverId: String): List<Tool> {
        return registeredTools[serverId]?.mapNotNull { toolRegistry.getTool(it) } ?: emptyList()
    }

    private fun updateState(serverId: String, state: McpServerState) {
        val states = _serverStates.value.toMutableMap()
        states[serverId] = state
        _serverStates.value = states
    }
}
