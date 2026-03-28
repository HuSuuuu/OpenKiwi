package com.orizon.openkiwi.core.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Anthropic Messages API provider.
 * Supports extended_thinking via the thinking block type.
 */
class AnthropicProvider(private val httpClient: OkHttpClient) : LlmProvider {

    override val id: String = "anthropic"

    override val supportedFeatures: Set<LlmFeature> = setOf(
        LlmFeature.STREAMING, LlmFeature.TOOLS, LlmFeature.VISION, LlmFeature.THINKING
    )

    private val sseClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    private fun buildUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.endsWith("/messages")) return trimmed
        return "$trimmed/messages"
    }

    override suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        request: UnifiedRequest
    ): Result<UnifiedResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildUrl(baseUrl)
            val body = buildRequestBody(request, stream = false)
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(httpRequest).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw IOException("Anthropic HTTP ${response.code}: $errorBody")
            }
            val responseBody = response.body?.string() ?: throw IOException("Empty response body")
            parseResponse(responseBody)
        }
    }

    override fun streamChatCompletion(
        baseUrl: String,
        apiKey: String,
        request: UnifiedRequest
    ): Flow<UnifiedChunk> = callbackFlow {
        val url = buildUrl(baseUrl)
        val body = buildRequestBody(request, stream = true)
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val contentAccumulator = StringBuilder()
        val thinkingAccumulator = StringBuilder()
        var currentToolIndex = -1
        val toolCalls = mutableListOf<UnifiedToolCall>()
        val toolArgBuffers = mutableMapOf<Int, StringBuilder>()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data.trim() == "[DONE]") {
                    close()
                    return
                }
                runCatching {
                    val obj = json.parseToJsonElement(data).jsonObject
                    when (type) {
                        "content_block_start" -> {
                            val block = obj["content_block"]?.jsonObject ?: return
                            val blockType = block["type"]?.jsonPrimitive?.content
                            if (blockType == "tool_use") {
                                currentToolIndex++
                                val toolId = block["id"]?.jsonPrimitive?.content ?: "call_$currentToolIndex"
                                val toolName = block["name"]?.jsonPrimitive?.content ?: ""
                                toolCalls.add(UnifiedToolCall(id = toolId, name = toolName, arguments = ""))
                                toolArgBuffers[currentToolIndex] = StringBuilder()
                            }
                        }
                        "content_block_delta" -> {
                            val delta = obj["delta"]?.jsonObject ?: return
                            val deltaType = delta["type"]?.jsonPrimitive?.content
                            when (deltaType) {
                                "text_delta" -> {
                                    val text = delta["text"]?.jsonPrimitive?.content ?: ""
                                    contentAccumulator.append(text)
                                    trySend(UnifiedChunk(contentDelta = text))
                                }
                                "thinking_delta" -> {
                                    val thinking = delta["thinking"]?.jsonPrimitive?.content ?: ""
                                    thinkingAccumulator.append(thinking)
                                    trySend(UnifiedChunk(reasoningDelta = thinking))
                                }
                                "input_json_delta" -> {
                                    val partial = delta["partial_json"]?.jsonPrimitive?.content ?: ""
                                    if (currentToolIndex >= 0) {
                                        toolArgBuffers[currentToolIndex]?.append(partial)
                                        trySend(UnifiedChunk(
                                            toolCalls = listOf(UnifiedToolCallDelta(
                                                index = currentToolIndex,
                                                argumentsDelta = partial
                                            ))
                                        ))
                                    }
                                }
                            }
                        }
                        "content_block_stop" -> {}
                        "message_delta" -> {
                            val delta = obj["delta"]?.jsonObject ?: return
                            val stopReason = delta["stop_reason"]?.jsonPrimitive?.content
                            val usageObj = obj["usage"]?.jsonObject
                            val usage = usageObj?.let {
                                UnifiedUsage(
                                    promptTokens = 0,
                                    completionTokens = it["output_tokens"]?.jsonPrimitive?.int ?: 0,
                                    totalTokens = it["output_tokens"]?.jsonPrimitive?.int ?: 0
                                )
                            }
                            trySend(UnifiedChunk(finishReason = mapStopReason(stopReason), usage = usage))
                        }
                        "message_stop" -> {
                            close()
                        }
                        "message_start" -> {
                            val message = obj["message"]?.jsonObject
                            val usageObj = message?.get("usage")?.jsonObject
                            if (usageObj != null) {
                                trySend(UnifiedChunk(usage = UnifiedUsage(
                                    promptTokens = usageObj["input_tokens"]?.jsonPrimitive?.int ?: 0,
                                    completionTokens = 0,
                                    totalTokens = usageObj["input_tokens"]?.jsonPrimitive?.int ?: 0
                                )))
                            }
                        }
                        "error" -> {
                            val error = obj["error"]?.jsonObject
                            val msg = error?.get("message")?.jsonPrimitive?.content ?: "Unknown Anthropic error"
                            close(IOException(msg))
                        }
                    }
                }.onFailure {
                    android.util.Log.w("AnthropicProvider", "Failed to parse SSE: ${data.take(200)}", it)
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val msg = t?.message ?: response?.body?.string()?.take(500) ?: "Unknown error"
                close(IOException("Anthropic stream error: $msg"))
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }

        val eventSource = EventSources.createFactory(sseClient).newEventSource(httpRequest, listener)
        awaitClose { runCatching { eventSource.cancel() } }
    }.flowOn(Dispatchers.IO)

    private fun buildRequestBody(request: UnifiedRequest, stream: Boolean): String {
        val systemMessages = request.messages.filter { it.role == UnifiedRole.SYSTEM }
        val nonSystemMessages = request.messages.filter { it.role != UnifiedRole.SYSTEM }

        return buildJsonObject {
            put("model", request.model)
            request.maxTokens?.let { put("max_tokens", it) } ?: put("max_tokens", 4096)
            request.temperature?.let { put("temperature", it) }
            request.topP?.let { put("top_p", it) }
            if (stream) put("stream", true)

            if (systemMessages.isNotEmpty()) {
                put("system", systemMessages.joinToString("\n") { it.content ?: "" })
            }

            putJsonArray("messages") {
                for (msg in nonSystemMessages) {
                    addJsonObject {
                        put("role", when (msg.role) {
                            UnifiedRole.USER -> "user"
                            UnifiedRole.ASSISTANT -> "assistant"
                            UnifiedRole.TOOL -> "user"
                            else -> "user"
                        })
                        if (msg.role == UnifiedRole.TOOL) {
                            putJsonArray("content") {
                                addJsonObject {
                                    put("type", "tool_result")
                                    put("tool_use_id", msg.toolCallId ?: "")
                                    put("content", msg.content ?: "")
                                }
                            }
                        } else if (msg.imageUrl != null) {
                            putJsonArray("content") {
                                msg.imageUrl.let { url ->
                                    addJsonObject {
                                        put("type", "image")
                                        putJsonObject("source") {
                                            put("type", "url")
                                            put("url", url)
                                        }
                                    }
                                }
                                addJsonObject {
                                    put("type", "text")
                                    put("text", msg.content ?: "")
                                }
                            }
                        } else if (msg.toolCalls != null) {
                            putJsonArray("content") {
                                if (!msg.content.isNullOrBlank()) {
                                    addJsonObject {
                                        put("type", "text")
                                        put("text", msg.content)
                                    }
                                }
                                for (tc in msg.toolCalls) {
                                    addJsonObject {
                                        put("type", "tool_use")
                                        put("id", tc.id)
                                        put("name", tc.name)
                                        put("input", json.parseToJsonElement(tc.arguments.ifBlank { "{}" }))
                                    }
                                }
                            }
                        } else {
                            put("content", msg.content ?: "")
                        }
                    }
                }
            }

            if (!request.tools.isNullOrEmpty()) {
                putJsonArray("tools") {
                    for (tool in request.tools) {
                        addJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", json.parseToJsonElement(
                                tool.parametersJson.ifBlank { """{"type":"object","properties":{}}""" }
                            ))
                        }
                    }
                }
            }
        }.toString()
    }

    private fun parseResponse(body: String): UnifiedResponse {
        val obj = json.parseToJsonElement(body).jsonObject
        val contentBlocks = obj["content"]?.jsonArray ?: JsonArray(emptyList())
        val textContent = StringBuilder()
        val thinkingContent = StringBuilder()
        val toolCalls = mutableListOf<UnifiedToolCall>()

        for (block in contentBlocks) {
            val blockObj = block.jsonObject
            when (blockObj["type"]?.jsonPrimitive?.content) {
                "text" -> textContent.append(blockObj["text"]?.jsonPrimitive?.content ?: "")
                "thinking" -> thinkingContent.append(blockObj["thinking"]?.jsonPrimitive?.content ?: "")
                "tool_use" -> toolCalls.add(UnifiedToolCall(
                    id = blockObj["id"]?.jsonPrimitive?.content ?: "",
                    name = blockObj["name"]?.jsonPrimitive?.content ?: "",
                    arguments = blockObj["input"]?.toString() ?: "{}"
                ))
            }
        }

        val usageObj = obj["usage"]?.jsonObject
        val usage = usageObj?.let {
            UnifiedUsage(
                promptTokens = it["input_tokens"]?.jsonPrimitive?.int ?: 0,
                completionTokens = it["output_tokens"]?.jsonPrimitive?.int ?: 0,
                totalTokens = (it["input_tokens"]?.jsonPrimitive?.int ?: 0) +
                        (it["output_tokens"]?.jsonPrimitive?.int ?: 0)
            )
        }

        val stopReason = obj["stop_reason"]?.jsonPrimitive?.content

        return UnifiedResponse(
            content = textContent.toString().takeIf { it.isNotEmpty() },
            reasoningContent = thinkingContent.toString().takeIf { it.isNotEmpty() },
            toolCalls = toolCalls.takeIf { it.isNotEmpty() },
            finishReason = mapStopReason(stopReason),
            usage = usage
        )
    }

    private fun mapStopReason(reason: String?): String? = when (reason) {
        "end_turn" -> "stop"
        "tool_use" -> "tool_calls"
        "max_tokens" -> "length"
        else -> reason
    }
}
