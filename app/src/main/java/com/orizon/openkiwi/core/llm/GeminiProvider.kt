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
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Google Gemini (generateContent / streamGenerateContent) provider.
 * Supports grounding with Google Search.
 */
class GeminiProvider(private val httpClient: OkHttpClient) : LlmProvider {

    override val id: String = "gemini"

    override val supportedFeatures: Set<LlmFeature> = setOf(
        LlmFeature.STREAMING, LlmFeature.TOOLS, LlmFeature.VISION, LlmFeature.GROUNDING
    )

    private val longClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    private fun buildUrl(baseUrl: String, model: String, stream: Boolean): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        val action = if (stream) "streamGenerateContent?alt=sse" else "generateContent"
        if (trimmed.contains("/models/")) return "$trimmed:$action"
        return "$trimmed/models/$model:$action"
    }

    override suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        request: UnifiedRequest
    ): Result<UnifiedResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildUrl(baseUrl, request.model, stream = false) + "&key=$apiKey"
            val body = buildRequestBody(request)
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(httpRequest).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw IOException("Gemini HTTP ${response.code}: $errorBody")
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
        val url = buildUrl(baseUrl, request.model, stream = true) + "&key=$apiKey"
        val body = buildRequestBody(request)
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val call = longClient.newCall(httpRequest)
        val response = try {
            call.execute()
        } catch (e: Exception) {
            close(IOException("Gemini connection failed: ${e.message}"))
            return@callbackFlow
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            close(IOException("Gemini HTTP ${response.code}: $errorBody"))
            return@callbackFlow
        }

        val source = response.body?.source()
        if (source == null) {
            close(IOException("Gemini: empty response stream"))
            return@callbackFlow
        }

        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data.isEmpty() || data == "[DONE]") continue

                runCatching {
                    val obj = json.parseToJsonElement(data).jsonObject
                    val candidates = obj["candidates"]?.jsonArray ?: return@runCatching
                    val candidate = candidates.firstOrNull()?.jsonObject ?: return@runCatching
                    val content = candidate["content"]?.jsonObject
                    val parts = content?.get("parts")?.jsonArray

                    parts?.forEach { part ->
                        val partObj = part.jsonObject
                        val text = partObj["text"]?.jsonPrimitive?.content
                        val functionCall = partObj["functionCall"]?.jsonObject

                        if (text != null) {
                            trySend(UnifiedChunk(contentDelta = text))
                        }
                        if (functionCall != null) {
                            val name = functionCall["name"]?.jsonPrimitive?.content ?: ""
                            val args = functionCall["args"]?.toString() ?: "{}"
                            trySend(UnifiedChunk(
                                toolCalls = listOf(UnifiedToolCallDelta(
                                    index = 0, id = "gemini_call_$name", name = name, argumentsDelta = args
                                )),
                                finishReason = "tool_calls"
                            ))
                        }
                    }

                    val finishReason = candidate["finishReason"]?.jsonPrimitive?.content
                    if (finishReason != null && finishReason != "STOP") {
                        trySend(UnifiedChunk(finishReason = mapFinishReason(finishReason)))
                    }

                    val usageMeta = obj["usageMetadata"]?.jsonObject
                    if (usageMeta != null) {
                        trySend(UnifiedChunk(
                            finishReason = finishReason?.let { mapFinishReason(it) },
                            usage = UnifiedUsage(
                                promptTokens = usageMeta["promptTokenCount"]?.jsonPrimitive?.int ?: 0,
                                completionTokens = usageMeta["candidatesTokenCount"]?.jsonPrimitive?.int ?: 0,
                                totalTokens = usageMeta["totalTokenCount"]?.jsonPrimitive?.int ?: 0
                            )
                        ))
                    }
                }.onFailure {
                    android.util.Log.w("GeminiProvider", "Failed to parse chunk: ${data.take(200)}", it)
                }
            }
        } catch (e: Exception) {
            close(IOException("Gemini stream error: ${e.message}"))
        } finally {
            runCatching { response.close() }
            close()
        }

        awaitClose { runCatching { call.cancel() } }
    }.flowOn(Dispatchers.IO)

    private fun buildRequestBody(request: UnifiedRequest): String {
        val systemInstruction = request.messages
            .filter { it.role == UnifiedRole.SYSTEM }
            .joinToString("\n") { it.content ?: "" }

        val nonSystemMessages = request.messages.filter { it.role != UnifiedRole.SYSTEM }

        return buildJsonObject {
            if (systemInstruction.isNotBlank()) {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject { put("text", systemInstruction) }
                    }
                }
            }

            putJsonArray("contents") {
                for (msg in nonSystemMessages) {
                    if (msg.role == UnifiedRole.TOOL) {
                        addJsonObject {
                            put("role", "function")
                            putJsonArray("parts") {
                                addJsonObject {
                                    putJsonObject("functionResponse") {
                                        put("name", msg.toolCallId ?: "unknown")
                                        putJsonObject("response") {
                                            put("result", msg.content ?: "")
                                        }
                                    }
                                }
                            }
                        }
                        continue
                    }

                    addJsonObject {
                        put("role", when (msg.role) {
                            UnifiedRole.USER -> "user"
                            UnifiedRole.ASSISTANT -> "model"
                            else -> "user"
                        })
                        putJsonArray("parts") {
                            if (msg.imageUrl != null) {
                                addJsonObject {
                                    putJsonObject("fileData") {
                                        put("mimeType", "image/jpeg")
                                        put("fileUri", msg.imageUrl)
                                    }
                                }
                            }
                            if (msg.toolCalls != null) {
                                for (tc in msg.toolCalls) {
                                    addJsonObject {
                                        putJsonObject("functionCall") {
                                            put("name", tc.name)
                                            put("args", json.parseToJsonElement(tc.arguments.ifBlank { "{}" }))
                                        }
                                    }
                                }
                            }
                            if (!msg.content.isNullOrBlank()) {
                                addJsonObject { put("text", msg.content) }
                            }
                        }
                    }
                }
            }

            if (!request.tools.isNullOrEmpty()) {
                putJsonArray("tools") {
                    addJsonObject {
                        putJsonArray("functionDeclarations") {
                            for (tool in request.tools) {
                                addJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("parameters", json.parseToJsonElement(
                                        tool.parametersJson.ifBlank { """{"type":"object","properties":{}}""" }
                                    ))
                                }
                            }
                        }
                    }
                }
            }

            putJsonObject("generationConfig") {
                request.temperature?.let { put("temperature", it) }
                request.maxTokens?.let { put("maxOutputTokens", it) }
                request.topP?.let { put("topP", it) }
            }
        }.toString()
    }

    private fun parseResponse(body: String): UnifiedResponse {
        val obj = json.parseToJsonElement(body).jsonObject
        val candidates = obj["candidates"]?.jsonArray
        val candidate = candidates?.firstOrNull()?.jsonObject
        val content = candidate?.get("content")?.jsonObject
        val parts = content?.get("parts")?.jsonArray

        val textContent = StringBuilder()
        val toolCalls = mutableListOf<UnifiedToolCall>()

        parts?.forEach { part ->
            val partObj = part.jsonObject
            partObj["text"]?.jsonPrimitive?.content?.let { textContent.append(it) }
            partObj["functionCall"]?.jsonObject?.let { fc ->
                toolCalls.add(UnifiedToolCall(
                    id = "gemini_call_${fc["name"]?.jsonPrimitive?.content ?: ""}",
                    name = fc["name"]?.jsonPrimitive?.content ?: "",
                    arguments = fc["args"]?.toString() ?: "{}"
                ))
            }
        }

        val finishReason = candidate?.get("finishReason")?.jsonPrimitive?.content
        val usageMeta = obj["usageMetadata"]?.jsonObject
        val usage = usageMeta?.let {
            UnifiedUsage(
                promptTokens = it["promptTokenCount"]?.jsonPrimitive?.int ?: 0,
                completionTokens = it["candidatesTokenCount"]?.jsonPrimitive?.int ?: 0,
                totalTokens = it["totalTokenCount"]?.jsonPrimitive?.int ?: 0
            )
        }

        return UnifiedResponse(
            content = textContent.toString().takeIf { it.isNotEmpty() },
            toolCalls = toolCalls.takeIf { it.isNotEmpty() },
            finishReason = mapFinishReason(finishReason),
            usage = usage
        )
    }

    private fun mapFinishReason(reason: String?): String? = when (reason) {
        "STOP" -> "stop"
        "MAX_TOKENS" -> "length"
        "SAFETY" -> "content_filter"
        else -> reason
    }
}
