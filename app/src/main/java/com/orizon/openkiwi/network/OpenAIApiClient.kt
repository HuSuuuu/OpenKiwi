package com.orizon.openkiwi.network

import com.orizon.openkiwi.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAIApiClient(
    private val httpClient: OkHttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
) {
    private val sseClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun buildUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.endsWith("/chat/completions")) return trimmed
        return "$trimmed/chat/completions"
    }

    suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        request: ChatCompletionRequest
    ): Result<ChatCompletionResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildUrl(baseUrl)
            val hasVision = request.messages.any { it.imageUrl != null }
            val body = if (hasVision) encodeVisionRequest(request, stream = false) else json.encodeToString(ChatCompletionRequest.serializer(), request.copy(stream = false))
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(httpRequest).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw IOException(buildErrorMessage(response.code, url, request.model, errorBody))
            }
            val responseBody = response.body?.string() ?: throw IOException("Empty response body")
            json.decodeFromString(ChatCompletionResponse.serializer(), responseBody)
        }
    }

    private fun encodeVisionRequest(request: ChatCompletionRequest, stream: Boolean = false): String {
        val messagesJson = request.messages.joinToString(",") { msg ->
            if (msg.imageUrl != null) {
                """{"role":"${msg.role.name.lowercase()}","content":[{"type":"image_url","image_url":{"url":"${msg.imageUrl}"}},{"type":"text","text":${json.encodeToString(String.serializer(), msg.content ?: "")}}]}"""
            } else if (msg.toolCalls != null) {
                json.encodeToString(ChatMessage.serializer(), msg)
            } else {
                """{"role":"${msg.role.name.lowercase()}","content":${json.encodeToString(String.serializer(), msg.content ?: "")}}"""
            }
        }
        val extras = buildString {
            request.temperature?.let { append(""","temperature":$it""") }
            request.maxTokens?.let { append(""","max_tokens":$it""") }
            request.reasoningEffort?.let { append(""","reasoning_effort":"$it"""") }
            if (stream) append(""","stream_options":{"include_usage":true}""")
        }
        return """{"model":"${request.model}","messages":[$messagesJson],"stream":$stream$extras}"""
    }

    fun streamChatCompletion(
        baseUrl: String,
        apiKey: String,
        request: ChatCompletionRequest
    ): Flow<StreamChunk> = callbackFlow {
        val url = buildUrl(baseUrl)
        val hasVision = request.messages.any { it.imageUrl != null }
        val streamRequest = request.copy(stream = true, streamOptions = StreamOptions(includeUsage = true))
        val body = if (hasVision) encodeVisionRequest(streamRequest, stream = true)
                   else json.encodeToString(ChatCompletionRequest.serializer(), streamRequest)
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .addHeader("Connection", "keep-alive")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data.trim() == "[DONE]") {
                    close()
                    return
                }
                runCatching {
                    val chunk = json.decodeFromString(StreamChunk.serializer(), data)
                    trySend(chunk)
                }.onFailure {
                    android.util.Log.w("OpenAIApiClient", "Failed to parse SSE chunk: ${data.take(200)}", it)
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val code = response?.code
                val errorBody = runCatching { response?.body?.string() }.getOrNull() ?: ""
                val msg = if (code != null) {
                    buildErrorMessage(code, url, request.model, errorBody)
                } else {
                    val cause = t?.message ?: "Unknown SSE error"
                    "连接中断: $cause\n请检查网络连接后重试。\nURL: $url\nModel: ${request.model}"
                }
                close(IOException(msg))
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }

        val eventSource = EventSources.createFactory(sseClient)
            .newEventSource(httpRequest, listener)

        awaitClose { eventSource.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun buildErrorMessage(code: Int, url: String, model: String, body: String): String {
        val hint = when (code) {
            401 -> "API Key 无效或已过期，请检查"
            403 -> "无权访问该模型，请检查 API Key 权限"
            404 -> "接口或模型不存在。火山方舟需使用接入点ID（如 ep-xxxx），DashScope 需使用正确模型名（如 qwen-plus）"
            429 -> "请求频率超限，请稍后重试"
            500, 502, 503 -> "服务端错误，请稍后重试"
            else -> ""
        }
        return buildString {
            append("HTTP $code")
            if (hint.isNotBlank()) append(" — $hint")
            append("\nURL: $url")
            append("\nModel: $model")
            if (body.isNotBlank()) append("\n${body.take(500)}")
        }
    }
}
