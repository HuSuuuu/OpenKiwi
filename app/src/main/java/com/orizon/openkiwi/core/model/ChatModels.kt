package com.orizon.openkiwi.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ChatRole {
    @SerialName("system") SYSTEM,
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT,
    @SerialName("tool") TOOL
}

@Serializable
data class ChatMessage(
    val role: ChatRole? = null,
    val content: String? = null,
    val name: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @kotlinx.serialization.Transient val imageUrl: String? = null,
    @kotlinx.serialization.Transient val videoUrl: String? = null,
    @kotlinx.serialization.Transient val messageId: Long = 0,
    @kotlinx.serialization.Transient val turnId: Long = 0,
    @kotlinx.serialization.Transient val artifacts: List<ChatArtifact> = emptyList()
)

data class ChatArtifact(
    val id: Long = 0,
    val sessionId: String,
    val messageId: Long? = null,
    val toolName: String,
    val filePath: String,
    val displayName: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class ToolCall(
    val id: String = "",
    val type: String = "function",
    val index: Int = 0,
    val function: FunctionCall
)

@Serializable
data class FunctionCall(
    val name: String = "",
    val arguments: String = ""
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false,
    val tools: List<ToolSpec>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Double? = null,
    @SerialName("presence_penalty") val presencePenalty: Double? = null,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
    @SerialName("stream_options") val streamOptions: StreamOptions? = null
)

@Serializable
data class StreamOptions(
    @SerialName("include_usage") val includeUsage: Boolean = true
)

/**
 * OpenAI / 方舟兼容的 tools 项。方舟 **Chat Completions** 要求 `tools[].type` 必须为 **function**；
 * 联网能力通过 `function.name` = [VOLCANO_WEB_SEARCH_TOOL_NAME] 声明（非 Responses API 里的 `type: web_search`）。
 */
@Serializable
data class ToolSpec(
    val type: String = TYPE_FUNCTION,
    val function: ToolFunction
) {
    companion object {
        const val TYPE_FUNCTION = "function"
        /** 方舟 chat/completions 内置联网工具在 function.name 中的标识 */
        const val VOLCANO_WEB_SEARCH_TOOL_NAME = "web_search"

        /**
         * 方舟 **chat/completions** 下的联网声明（非 Responses 里的 `type:web_search` 对象）。
         * `max_keyword` / `sources` 等仅见于 Responses API，见 [com.orizon.openkiwi.network.ArkWebSearchBodies]。
         */
        fun volcanoWebSearchToolSpec(): ToolSpec = ToolSpec(
            type = TYPE_FUNCTION,
            function = ToolFunction(
                name = VOLCANO_WEB_SEARCH_TOOL_NAME,
                description = "Search the public web for up-to-date information (Ark plugin).",
                parameters = ToolParameters()
            )
        )
    }
}

@Serializable
data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: ToolParameters
)

@Serializable
data class ToolParameters(
    val type: String = "object",
    val properties: Map<String, ToolProperty> = emptyMap(),
    val required: List<String> = emptyList()
)

@Serializable
data class ToolProperty(
    val type: String,
    val description: String = "",
    val enum: List<String>? = null
)

@Serializable
data class ChatCompletionResponse(
    val id: String = "",
    val model: String = "",
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ChatMessage? = null,
    val delta: ChatMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

@Serializable
data class StreamChunk(
    val id: String = "",
    val model: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null
)
