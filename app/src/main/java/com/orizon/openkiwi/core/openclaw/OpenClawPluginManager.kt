package com.orizon.openkiwi.core.openclaw

import android.util.Log
import com.orizon.openkiwi.core.agent.ProactiveMessageBus
import com.orizon.openkiwi.core.agent.ProactiveMessage
import com.orizon.openkiwi.core.tool.ToolRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

data class OpenClawInstance(
    val id: String,
    val url: String,
    val token: String? = null,
    val label: String = url,
    val connectedAt: Long = 0,
    val toolCount: Int = 0,
    val error: String? = null
)

/**
 * Manages connections to one or more OpenClaw Gateway instances.
 *
 * Responsibilities:
 *   1. Connect/disconnect to remote OpenClaw Gateways.
 *   2. Discover available tools via tools.catalog and bridge them into ToolRegistry.
 *   3. Forward Gateway events (chat, agent, session.tool) to ProactiveMessageBus.
 *   4. Support MCP-based connection as an alternative (via existing McpManager).
 */
class OpenClawPluginManager(
    private val toolRegistry: ToolRegistry
) {
    companion object {
        private const val TAG = "OpenClawPluginMgr"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clients = ConcurrentHashMap<String, OpenClawGatewayClient>()
    private val bridgedToolNames = ConcurrentHashMap<String, MutableList<String>>()
    private val eventJobs = ConcurrentHashMap<String, Job>()

    private val _instances = MutableStateFlow<Map<String, OpenClawInstance>>(emptyMap())
    val instances: StateFlow<Map<String, OpenClawInstance>> = _instances.asStateFlow()

    /**
     * Connect to an OpenClaw Gateway instance and discover its tools.
     */
    suspend fun connect(
        id: String,
        url: String,
        token: String? = null,
        sessionKey: String? = null
    ): Result<OpenClawInstance> {
        disconnect(id)
        updateInstance(id, OpenClawInstance(id, url, token, url))

        val client = OpenClawGatewayClient(
            okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        )

        return try {
            client.connect(url, token)
            clients[id] = client

            val tools = try {
                client.fetchToolsCatalog()
            } catch (e: Exception) {
                Log.w(TAG, "tools.catalog failed, trying tools.effective", e)
                if (sessionKey != null) client.fetchEffectiveTools(sessionKey) else emptyList()
            }

            val registered = mutableListOf<String>()
            for (spec in tools) {
                val bridge = OpenClawToolBridge(client, spec, sessionKey)
                toolRegistry.register(bridge)
                registered.add(bridge.definition.name)
            }
            bridgedToolNames[id] = registered

            startEventForwarding(id, client)

            val instance = OpenClawInstance(
                id = id,
                url = url,
                token = token,
                label = url,
                connectedAt = System.currentTimeMillis(),
                toolCount = tools.size
            )
            updateInstance(id, instance)
            Log.i(TAG, "Connected to OpenClaw '$url': ${tools.size} tools bridged")
            Result.success(instance)
        } catch (e: Exception) {
            val errInstance = OpenClawInstance(id, url, token, url, error = e.message)
            updateInstance(id, errInstance)
            Log.e(TAG, "Failed to connect to OpenClaw '$url'", e)
            Result.failure(e)
        }
    }

    /**
     * Disconnect from an OpenClaw instance and unregister its tools.
     */
    fun disconnect(id: String) {
        eventJobs.remove(id)?.cancel()
        bridgedToolNames.remove(id)?.forEach { toolRegistry.unregister(it) }
        clients.remove(id)?.destroy()
        val map = _instances.value.toMutableMap()
        map.remove(id)
        _instances.value = map
    }

    fun disconnectAll() {
        clients.keys.toList().forEach { disconnect(it) }
        scope.cancel()
    }

    fun getClient(id: String): OpenClawGatewayClient? = clients[id]

    fun getToolsForInstance(id: String): List<com.orizon.openkiwi.core.tool.Tool> =
        bridgedToolNames[id]?.mapNotNull { toolRegistry.getTool(it) } ?: emptyList()

    /**
     * Refresh tools for an existing connection (e.g. after plugin config changes on the remote).
     */
    suspend fun refreshTools(id: String, sessionKey: String? = null): Int {
        val client = clients[id] ?: return 0
        bridgedToolNames.remove(id)?.forEach { toolRegistry.unregister(it) }

        val tools = try {
            client.fetchToolsCatalog()
        } catch (_: Exception) {
            if (sessionKey != null) client.fetchEffectiveTools(sessionKey) else emptyList()
        }

        val registered = mutableListOf<String>()
        for (spec in tools) {
            val bridge = OpenClawToolBridge(client, spec, sessionKey)
            toolRegistry.register(bridge)
            registered.add(bridge.definition.name)
        }
        bridgedToolNames[id] = registered

        val inst = _instances.value[id]
        if (inst != null) {
            updateInstance(id, inst.copy(toolCount = tools.size))
        }
        Log.i(TAG, "Refreshed tools for '$id': ${tools.size} tools")
        return tools.size
    }

    /**
     * Send a chat message through a connected OpenClaw instance.
     */
    suspend fun sendChat(id: String, sessionKey: String, message: String): String {
        val client = clients[id]
            ?: return "Error: OpenClaw instance '$id' not connected"
        return try {
            val result = client.chatSend(sessionKey, message)
            result?.toString() ?: "Message sent"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * Call a Gateway RPC method directly.
     */
    suspend fun callMethod(id: String, method: String, params: JsonElement? = null): JsonElement? {
        val client = clients[id]
            ?: throw Exception("OpenClaw instance '$id' not connected")
        return client.request(method, params)
    }

    /**
     * List all connected instances with their status.
     */
    fun listInstances(): List<OpenClawInstance> = _instances.value.values.toList()

    private fun startEventForwarding(id: String, client: OpenClawGatewayClient) {
        eventJobs[id]?.cancel()
        eventJobs[id] = scope.launch {
            client.events.collect { event ->
                when (event.event) {
                    "chat" -> {
                        val msg = event.payload?.jsonObject
                        val state = msg?.get("state")?.jsonPrimitive?.content
                        if (state == "final") {
                            val content = msg["message"]?.jsonObject
                                ?.get("content")?.jsonPrimitive?.content
                            if (!content.isNullOrBlank()) {
                                ProactiveMessageBus.emit(ProactiveMessage(
                                    sessionId = null,
                                    content = "[OpenClaw:$id] $content",
                                    source = ProactiveMessage.Source.SYSTEM
                                ))
                            }
                        }
                    }
                    "agent", "session.tool" -> {
                        Log.d(TAG, "[$id] event: ${event.event} seq=${event.seq}")
                    }
                }
            }
        }
    }

    private fun updateInstance(id: String, instance: OpenClawInstance) {
        val map = _instances.value.toMutableMap()
        map[id] = instance
        _instances.value = map
    }
}
