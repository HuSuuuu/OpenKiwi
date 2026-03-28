package com.orizon.openkiwi.core.llm

import kotlinx.coroutines.flow.Flow

enum class LlmFeature {
    STREAMING, TOOLS, VISION, THINKING, GROUNDING
}

data class UnifiedRequest(
    val model: String,
    val messages: List<UnifiedMessage>,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val topP: Double? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val tools: List<UnifiedToolSpec>? = null,
    val toolChoice: String? = null,
    val reasoningEffort: String? = null
)

data class UnifiedMessage(
    val role: UnifiedRole,
    val content: String? = null,
    val toolCalls: List<UnifiedToolCall>? = null,
    val toolCallId: String? = null,
    val reasoningContent: String? = null,
    val imageUrl: String? = null,
    val videoUrl: String? = null
)

enum class UnifiedRole { SYSTEM, USER, ASSISTANT, TOOL }

data class UnifiedToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

data class UnifiedToolSpec(
    val name: String,
    val description: String,
    val parametersJson: String
)

data class UnifiedResponse(
    val content: String? = null,
    val reasoningContent: String? = null,
    val toolCalls: List<UnifiedToolCall>? = null,
    val finishReason: String? = null,
    val usage: UnifiedUsage? = null
)

data class UnifiedChunk(
    val contentDelta: String? = null,
    val reasoningDelta: String? = null,
    val toolCalls: List<UnifiedToolCallDelta>? = null,
    val finishReason: String? = null,
    val usage: UnifiedUsage? = null
)

data class UnifiedToolCallDelta(
    val index: Int,
    val id: String = "",
    val name: String = "",
    val argumentsDelta: String = ""
)

data class UnifiedUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

interface LlmProvider {
    val id: String
    val supportedFeatures: Set<LlmFeature>

    suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        request: UnifiedRequest
    ): Result<UnifiedResponse>

    fun streamChatCompletion(
        baseUrl: String,
        apiKey: String,
        request: UnifiedRequest
    ): Flow<UnifiedChunk>
}
