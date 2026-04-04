package com.orizon.openkiwi.core.openclaw

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

enum class GatewayConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR
}

/**
 * Client for the OpenClaw Gateway WebSocket protocol (req/res/event frames).
 * Also supports HTTP tool invocation via the tools.invoke endpoint.
 */
class OpenClawGatewayClient(
    private val httpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "OpenClawGateway"
        private const val RECONNECT_DELAY_MS = 5000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestIdCounter = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JsonElement?>>()

    private var webSocket: WebSocket? = null
    private var gatewayUrl: String = ""
    private var httpBaseUrl: String = ""
    private var authToken: String? = null
    private var reconnectAttempts = 0

    private val _status = MutableStateFlow(GatewayConnectionStatus.DISCONNECTED)
    val status: StateFlow<GatewayConnectionStatus> = _status.asStateFlow()

    private val _events = MutableSharedFlow<EventFrame>(extraBufferCapacity = 64)
    val events: SharedFlow<EventFrame> = _events.asSharedFlow()

    val isConnected: Boolean get() = _status.value == GatewayConnectionStatus.CONNECTED

    fun connect(url: String, token: String? = null) {
        disconnect()
        gatewayUrl = url.trimEnd('/')
        httpBaseUrl = gatewayUrl
            .replace("ws://", "http://")
            .replace("wss://", "https://")
        authToken = token
        reconnectAttempts = 0
        doConnect()
    }

    fun disconnect() {
        _status.value = GatewayConnectionStatus.DISCONNECTED
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()
    }

    private fun doConnect() {
        _status.value = GatewayConnectionStatus.CONNECTING
        val wsUrl = gatewayUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .let { if (it.endsWith("/ws")) it else "$it/ws" }

        val requestBuilder = Request.Builder().url(wsUrl)
        authToken?.let { requestBuilder.addHeader("Authorization", "Bearer $it") }

        val wsClient = httpClient.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        webSocket = wsClient.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "Gateway connected: $wsUrl")
                _status.value = GatewayConnectionStatus.CONNECTED
                reconnectAttempts = 0
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleFrame(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Gateway closing: $code $reason")
                ws.close(code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Gateway closed: $code $reason")
                handleDisconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Gateway failure: ${t.message}", t)
                handleDisconnect()
            }
        })
    }

    private fun handleDisconnect() {
        if (_status.value == GatewayConnectionStatus.DISCONNECTED) return
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()

        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            _status.value = GatewayConnectionStatus.RECONNECTING
            reconnectAttempts++
            scope.launch {
                delay(RECONNECT_DELAY_MS * reconnectAttempts)
                if (_status.value == GatewayConnectionStatus.RECONNECTING) {
                    Log.i(TAG, "Reconnecting attempt $reconnectAttempts")
                    doConnect()
                }
            }
        } else {
            _status.value = GatewayConnectionStatus.ERROR
        }
    }

    private fun handleFrame(raw: String) {
        runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            when (obj["type"]?.jsonPrimitive?.content) {
                "res" -> {
                    val id = obj["id"]?.jsonPrimitive?.content ?: return
                    val ok = obj["ok"]?.jsonPrimitive?.boolean ?: false
                    val deferred = pendingRequests.remove(id) ?: return
                    if (ok) {
                        deferred.complete(obj["payload"])
                    } else {
                        val errMsg = obj["error"]?.jsonObject
                            ?.get("message")?.jsonPrimitive?.content ?: "Gateway error"
                        deferred.completeExceptionally(Exception(errMsg))
                    }
                }
                "event" -> {
                    val eventName = obj["event"]?.jsonPrimitive?.content ?: return
                    val frame = EventFrame(
                        event = eventName,
                        payload = obj["payload"],
                        seq = obj["seq"]?.jsonPrimitive?.intOrNull,
                        stateVersion = obj["stateVersion"]?.jsonPrimitive?.intOrNull
                    )
                    scope.launch { _events.emit(frame) }
                }
            }
        }.onFailure {
            Log.w(TAG, "Failed to parse frame: ${raw.take(300)}", it)
        }
    }

    /**
     * Send a request frame and await the response.
     */
    suspend fun request(method: String, params: JsonElement? = null, timeoutMs: Long = 30_000): JsonElement? {
        val id = "kiwi-${requestIdCounter.getAndIncrement()}"
        val frame = buildJsonObject {
            put("type", "req")
            put("id", id)
            put("method", method)
            params?.let { put("params", it) }
        }

        val deferred = CompletableDeferred<JsonElement?>()
        pendingRequests[id] = deferred

        val sent = webSocket?.send(frame.toString()) ?: false
        if (!sent) {
            pendingRequests.remove(id)
            throw Exception("WebSocket not connected")
        }

        return withTimeout(timeoutMs) { deferred.await() }
    }

    /**
     * Fetch available tools from the Gateway via tools.catalog.
     */
    suspend fun fetchToolsCatalog(agentId: String? = null): List<OpenClawToolSpec> {
        val params = buildJsonObject {
            agentId?.let { put("agentId", it) }
            put("includePlugins", true)
        }
        val result = request("tools.catalog", params) ?: return emptyList()
        return parseToolsCatalog(result)
    }

    /**
     * Fetch effective tools for a session.
     */
    suspend fun fetchEffectiveTools(sessionKey: String, agentId: String? = null): List<OpenClawToolSpec> {
        val params = buildJsonObject {
            put("sessionKey", sessionKey)
            agentId?.let { put("agentId", it) }
        }
        val result = request("tools.effective", params) ?: return emptyList()
        return parseToolsCatalog(result)
    }

    private fun parseToolsCatalog(payload: JsonElement): List<OpenClawToolSpec> {
        val tools = mutableListOf<OpenClawToolSpec>()
        val arr = when {
            payload is JsonArray -> payload
            payload is JsonObject && payload.containsKey("tools") ->
                payload["tools"]?.jsonArray ?: return emptyList()
            else -> return emptyList()
        }

        for (elem in arr) {
            val obj = elem.jsonObject
            tools.add(OpenClawToolSpec(
                name = obj["name"]?.jsonPrimitive?.content ?: continue,
                description = obj["description"]?.jsonPrimitive?.content ?: "",
                pluginId = obj["pluginId"]?.jsonPrimitive?.content,
                pluginName = obj["pluginName"]?.jsonPrimitive?.content,
                ownerOnly = obj["ownerOnly"]?.jsonPrimitive?.boolean ?: false,
                inputSchema = obj["inputSchema"] ?: obj["parameters"]
            ))
        }
        return tools
    }

    /**
     * Invoke a tool via the HTTP tools.invoke endpoint.
     */
    suspend fun invokeTool(
        toolName: String,
        args: JsonObject,
        sessionKey: String? = null,
        agentId: String? = null,
        timeoutMs: Long = 60_000
    ): JsonElement? = withContext(Dispatchers.IO) {
        val url = "$httpBaseUrl/tools/invoke"
        val body = buildJsonObject {
            put("tool", toolName)
            put("args", args)
            sessionKey?.let { put("sessionKey", it) }
            agentId?.let { put("agentId", it) }
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        authToken?.let { requestBuilder.addHeader("Authorization", "Bearer $it") }

        val call = httpClient.newBuilder()
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
            .newCall(requestBuilder.build())

        call.execute().use { response ->
            val responseBody = response.body?.string()
            if (!response.isSuccessful) {
                throw Exception("tools.invoke failed (${response.code}): ${responseBody?.take(500)}")
            }
            responseBody?.let { json.parseToJsonElement(it) }
        }
    }

    /**
     * Send a chat message through the Gateway.
     */
    suspend fun chatSend(
        sessionKey: String,
        message: String,
        attachments: List<JsonElement>? = null
    ): JsonElement? {
        val runId = UUID.randomUUID().toString()
        val params = buildJsonObject {
            put("sessionKey", sessionKey)
            put("message", message)
            put("deliver", false)
            put("idempotencyKey", runId)
            attachments?.let {
                put("attachments", JsonArray(it))
            }
        }
        return request("chat.send", params, timeoutMs = 120_000)
    }

    /**
     * Get chat history for a session.
     */
    suspend fun chatHistory(sessionKey: String, limit: Int = 50): JsonElement? {
        val params = buildJsonObject {
            put("sessionKey", sessionKey)
            put("limit", limit)
        }
        return request("chat.history", params)
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }
}
