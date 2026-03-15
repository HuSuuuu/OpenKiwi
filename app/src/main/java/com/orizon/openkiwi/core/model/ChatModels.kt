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
    val role: ChatRole,
    val content: String? = null,
    val name: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @kotlinx.serialization.Transient val imageUrl: String? = null
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

@Serializable
data class ToolSpec(
    val type: String = "function",
    val function: ToolFunction
)

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
    val id: String,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val index: Int,
    val message: ChatMessage? = null,
    val delta: ChatMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)

@Serializable
data class StreamChunk(
    val id: String,
    val model: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null
)
