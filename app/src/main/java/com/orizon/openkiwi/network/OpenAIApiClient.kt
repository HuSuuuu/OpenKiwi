package com.orizon.openkiwi.network

import com.orizon.openkiwi.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
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
        coerceInputValues = true
        isLenient = true
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

    /**
     * 某些 OpenAI 兼容网关（小米 MiMo、MiniMax 等）不支持 `stream_options` / `reasoning_effort`，
     * 收到这些字段会 400 或返回空。根据 base URL 判断是否需要剔除。
     */
    private fun needsParamStrip(baseUrl: String): Boolean {
        val host = runCatching { java.net.URI(baseUrl.trim()).host?.lowercase() }.getOrNull() ?: ""
        val knownStrictHosts = listOf("xiaomimimo.com", "mimo-v2.com", "minimaxi.com")
        return knownStrictHosts.any { host.endsWith(it) }
    }

    private fun sanitizeRequest(request: ChatCompletionRequest, baseUrl: String): ChatCompletionRequest {
        if (!needsParamStrip(baseUrl)) return request
        return request.copy(
            reasoningEffort = null,
            streamOptions = null,
            frequencyPenalty = request.frequencyPenalty.takeIf { it != 0.0 },
            presencePenalty = request.presencePenalty.takeIf { it != 0.0 }
        )
    }

    suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        request: ChatCompletionRequest
    ): Result<ChatCompletionResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildUrl(baseUrl)
            val safeRequest = sanitizeRequest(request, baseUrl)
            val hasVision = safeRequest.messages.any { it.imageUrl != null || it.videoUrl != null }
            val body = if (hasVision) encodeVisionRequest(safeRequest, stream = false) else json.encodeToString(ChatCompletionRequest.serializer(), safeRequest.copy(stream = false))
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
            if (msg.imageUrl != null || msg.videoUrl != null) {
                val contentParts = buildString {
                    append("[")
                    var first = true
                    msg.videoUrl?.let { url ->
                        if (!first) append(",")
                        first = false
                        append("""{"type":"video_url","video_url":{"url":"$url"}}""")
                    }
                    msg.imageUrl?.let { url ->
                        if (!first) append(",")
                        first = false
                        append("""{"type":"image_url","image_url":{"url":"$url"}}""")
                    }
                    if (!first) append(",")
                    append("""{"type":"text","text":${json.encodeToString(String.serializer(), msg.content ?: "")}}""")
                    append("]")
                }
                val roleName = (msg.role ?: ChatRole.USER).name.lowercase()
                """{"role":"$roleName","content":$contentParts}"""
            } else if (msg.toolCalls != null) {
                json.encodeToString(ChatMessage.serializer(), msg)
            } else {
                val roleName = (msg.role ?: ChatRole.USER).name.lowercase()
                """{"role":"$roleName","content":${json.encodeToString(String.serializer(), msg.content ?: "")}}"""
            }
        }
        val extras = buildString {
            request.temperature?.let { append(""","temperature":$it""") }
            request.maxTokens?.let { append(""","max_tokens":$it""") }
            request.reasoningEffort?.let { append(""","reasoning_effort":"$it"""") }
            if (stream && request.streamOptions != null) append(""","stream_options":{"include_usage":true}""")
        }
        val toolsJson = buildToolsAndToolChoiceJson(request)
        return """{"model":"${request.model}","messages":[$messagesJson],"stream":$stream$extras$toolsJson}"""
    }

    /** Vision 路径手写 JSON，须与 [ChatCompletionRequest] 一致地带上 tools / tool_choice。 */
    private fun buildToolsAndToolChoiceJson(request: ChatCompletionRequest): String = buildString {
        val tools = request.tools
        if (!tools.isNullOrEmpty()) {
            append(""","tools":""")
            append(json.encodeToString(ListSerializer(ToolSpec.serializer()), tools))
        }
        request.toolChoice?.takeIf { it.isNotBlank() }?.let { choice ->
            append(""","tool_choice":""")
            append(json.encodeToString(String.serializer(), choice))
        }
    }

    fun streamChatCompletion(
        baseUrl: String,
        apiKey: String,
        request: ChatCompletionRequest
    ): Flow<StreamChunk> = callbackFlow {
        val url = buildUrl(baseUrl)
        val safeRequest = sanitizeRequest(request, baseUrl)
        val hasVision = safeRequest.messages.any { it.imageUrl != null || it.videoUrl != null }
        val streamRequest = if (needsParamStrip(baseUrl)) {
            safeRequest.copy(stream = true)
        } else {
            safeRequest.copy(stream = true, streamOptions = StreamOptions(includeUsage = true))
        }
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

        /**
         * 很多网关/代理在最后一个包里带上 `finish_reason`（stop / length / tool_calls），
         * 但 **不发 `[DONE]`、也不关 TCP**，OkHttp 会一直等到 read timeout，界面便长期停在「生成中」。
         *
         * 用 [streamDone] 标记「已正常结束」，在 `onFailure` 里据此决定是真错误还是 cancel 副作用。
         * 不在 `finishStreamIfTerminated` 里直接 `cancel()` EventSource——
         * 因为 cancel 会异步触发 `onFailure`，导致 channel 被 `close(IOException)` 关闭，
         * 上层 `collect` 会抛异常而不是正常结束，内容就丢了。
         * 改为只 `close()` channel，让 `awaitClose` 统一回收 EventSource。
         */
        val streamDone = java.util.concurrent.atomic.AtomicBoolean(false)

        fun finishStreamIfTerminated(chunk: StreamChunk) {
            val terminalReason = chunk.choices.any { !it.finishReason.isNullOrBlank() }
            val usageOnlyTerminator = chunk.choices.isEmpty() && chunk.usage != null
            if (terminalReason || usageOnlyTerminator) {
                streamDone.set(true)
                close()
            }
        }

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {}

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data.trim() == "[DONE]") {
                    streamDone.set(true)
                    close()
                    return
                }
                runCatching {
                    val chunk = json.decodeFromString(StreamChunk.serializer(), data)
                    trySend(chunk)
                    finishStreamIfTerminated(chunk)
                }.onFailure {
                    android.util.Log.w("OpenAIApiClient", "Failed to parse SSE chunk: ${data.take(200)}", it)
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                if (streamDone.get()) {
                    close()
                    return
                }
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
                streamDone.set(true)
                close()
            }
        }

        val eventSource = EventSources.createFactory(sseClient)
            .newEventSource(httpRequest, listener)

        awaitClose { runCatching { eventSource.cancel() } }
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
