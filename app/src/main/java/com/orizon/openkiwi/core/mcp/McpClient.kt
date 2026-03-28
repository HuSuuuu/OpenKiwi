package com.orizon.openkiwi.core.mcp

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class McpToolInfo(
    val name: String,
    val description: String,
    val inputSchemaJson: String
)

class McpClient(
    private val config: McpServerConfig,
    private val httpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "McpClient"
        private const val JSONRPC_VERSION = "2.0"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
    private val requestIdCounter = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JsonElement?>>()
    private var sseEventSource: EventSource? = null
    private var sseEndpoint: String? = null
    private var stdioProcess: Process? = null
    private var stdioWriter: OutputStreamWriter? = null
    private var stdioReaderJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initialized = false

    val isConnected: Boolean get() = initialized

    suspend fun connect() {
        when (config.transportType) {
            TransportType.SSE -> connectSse()
            TransportType.STDIO -> connectStdio()
        }
        initialize()
    }

    fun disconnect() {
        initialized = false
        sseEventSource?.cancel()
        sseEventSource = null
        stdioReaderJob?.cancel()
        runCatching { stdioProcess?.destroy() }
        stdioProcess = null
        stdioWriter = null
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()
    }

    private suspend fun connectSse() {
        val url = config.url.trimEnd('/')
        val sseUrl = if (url.endsWith("/sse")) url else "$url/sse"

        val connected = CompletableDeferred<Unit>()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                when (type) {
                    "endpoint" -> {
                        val baseUrl = sseUrl.substringBeforeLast("/")
                        sseEndpoint = if (data.startsWith("/")) "$baseUrl$data" else data
                        if (!connected.isCompleted) connected.complete(Unit)
                    }
                    "message" -> {
                        handleIncomingMessage(data)
                    }
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.e(TAG, "SSE connection failed: ${t?.message}", t)
                if (!connected.isCompleted) {
                    connected.completeExceptionally(
                        t ?: Exception("SSE failed: HTTP ${response?.code}")
                    )
                }
            }
        }

        val sseClient = httpClient.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val request = Request.Builder().url(sseUrl)
            .addHeader("Accept", "text/event-stream")
            .build()

        sseEventSource = EventSources.createFactory(sseClient).newEventSource(request, listener)
        withTimeout(15_000) { connected.await() }
    }

    private fun connectStdio() {
        val pb = ProcessBuilder(buildList {
            add(config.command)
            addAll(config.args)
        })
        config.env.forEach { (k, v) -> pb.environment()[k] = v }
        pb.redirectErrorStream(false)
        stdioProcess = pb.start()
        stdioWriter = OutputStreamWriter(stdioProcess!!.outputStream, Charsets.UTF_8)

        stdioReaderJob = scope.launch {
            val reader = BufferedReader(InputStreamReader(stdioProcess!!.inputStream, Charsets.UTF_8))
            try {
                while (isActive) {
                    val line = reader.readLine() ?: break
                    handleIncomingMessage(line)
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun initialize() {
        val params = buildJsonObject {
            putJsonObject("capabilities") {}
            putJsonObject("clientInfo") {
                put("name", "OpenKiwi")
                put("version", "1.0")
            }
            put("protocolVersion", "2024-11-05")
        }
        sendRequest("initialize", params)
        sendNotification("notifications/initialized", null)
        initialized = true
    }

    suspend fun listTools(): List<McpToolInfo> {
        val result = sendRequest("tools/list", null) ?: return emptyList()
        val tools = result.jsonObject["tools"]?.jsonArray ?: return emptyList()
        return tools.mapNotNull { elem ->
            val obj = elem.jsonObject
            McpToolInfo(
                name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                description = obj["description"]?.jsonPrimitive?.content ?: "",
                inputSchemaJson = obj["inputSchema"]?.toString() ?: """{"type":"object","properties":{}}"""
            )
        }
    }

    suspend fun callTool(name: String, arguments: JsonObject): String {
        val params = buildJsonObject {
            put("name", name)
            put("arguments", arguments)
        }
        val result = sendRequest("tools/call", params)
        val content = result?.jsonObject?.get("content")?.jsonArray
        return content?.joinToString("\n") { elem ->
            val obj = elem.jsonObject
            when (obj["type"]?.jsonPrimitive?.content) {
                "text" -> obj["text"]?.jsonPrimitive?.content ?: ""
                "image" -> "[image: ${obj["data"]?.jsonPrimitive?.content?.take(20)}...]"
                else -> obj.toString()
            }
        } ?: result?.toString() ?: ""
    }

    private suspend fun sendRequest(method: String, params: JsonElement?): JsonElement? {
        val id = requestIdCounter.getAndIncrement()
        val message = buildJsonObject {
            put("jsonrpc", JSONRPC_VERSION)
            put("id", id)
            put("method", method)
            params?.let { put("params", it) }
        }
        val deferred = CompletableDeferred<JsonElement?>()
        pendingRequests[id] = deferred
        sendRaw(message.toString())
        return withTimeout(30_000) { deferred.await() }
    }

    private fun sendNotification(method: String, params: JsonElement?) {
        val message = buildJsonObject {
            put("jsonrpc", JSONRPC_VERSION)
            put("method", method)
            params?.let { put("params", it) }
        }
        scope.launch { sendRaw(message.toString()) }
    }

    private suspend fun sendRaw(message: String) {
        when (config.transportType) {
            TransportType.SSE -> {
                val endpoint = sseEndpoint ?: throw IllegalStateException("SSE endpoint not established")
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(endpoint)
                        .addHeader("Content-Type", "application/json")
                        .post(message.toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.w(TAG, "MCP POST failed: ${response.code}")
                        }
                    }
                }
            }
            TransportType.STDIO -> {
                withContext(Dispatchers.IO) {
                    stdioWriter?.let { writer ->
                        writer.write(message)
                        writer.write("\n")
                        writer.flush()
                    }
                }
            }
        }
    }

    private fun handleIncomingMessage(raw: String) {
        runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            val id = obj["id"]?.jsonPrimitive?.int
            if (id != null) {
                val error = obj["error"]?.jsonObject
                if (error != null) {
                    val msg = error["message"]?.jsonPrimitive?.content ?: "MCP error"
                    pendingRequests.remove(id)?.completeExceptionally(Exception(msg))
                } else {
                    pendingRequests.remove(id)?.complete(obj["result"])
                }
            }
        }.onFailure {
            Log.w(TAG, "Failed to parse MCP message: ${raw.take(200)}", it)
        }
    }
}
