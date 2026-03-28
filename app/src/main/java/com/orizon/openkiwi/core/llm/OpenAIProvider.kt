package com.orizon.openkiwi.core.llm

import com.orizon.openkiwi.core.model.*
import com.orizon.openkiwi.network.OpenAIApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OpenAIProvider(
    private val apiClient: OpenAIApiClient
) : LlmProvider {

    override val id: String = "openai"

    override val supportedFeatures: Set<LlmFeature> = setOf(
        LlmFeature.STREAMING, LlmFeature.TOOLS, LlmFeature.VISION, LlmFeature.THINKING
    )

    override suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        request: UnifiedRequest
    ): Result<UnifiedResponse> {
        val openAIRequest = request.toOpenAIRequest()
        return apiClient.chatCompletion(baseUrl, apiKey, openAIRequest).map { response ->
            response.toUnifiedResponse()
        }
    }

    override fun streamChatCompletion(
        baseUrl: String,
        apiKey: String,
        request: UnifiedRequest
    ): Flow<UnifiedChunk> {
        val openAIRequest = request.toOpenAIRequest()
        return apiClient.streamChatCompletion(baseUrl, apiKey, openAIRequest).map { chunk ->
            chunk.toUnifiedChunk()
        }
    }
}

internal fun UnifiedRequest.toOpenAIRequest(): ChatCompletionRequest {
    return ChatCompletionRequest(
        model = model,
        messages = messages.map { it.toOpenAIMessage() },
        temperature = temperature,
        maxTokens = maxTokens,
        topP = topP,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty,
        tools = tools?.map { it.toOpenAIToolSpec() },
        toolChoice = toolChoice,
        reasoningEffort = reasoningEffort
    )
}

internal fun UnifiedMessage.toOpenAIMessage(): ChatMessage {
    return ChatMessage(
        role = when (role) {
            UnifiedRole.SYSTEM -> ChatRole.SYSTEM
            UnifiedRole.USER -> ChatRole.USER
            UnifiedRole.ASSISTANT -> ChatRole.ASSISTANT
            UnifiedRole.TOOL -> ChatRole.TOOL
        },
        content = content,
        toolCalls = toolCalls?.map {
            ToolCall(id = it.id, function = FunctionCall(name = it.name, arguments = it.arguments))
        },
        toolCallId = toolCallId,
        reasoningContent = reasoningContent,
        imageUrl = imageUrl,
        videoUrl = videoUrl
    )
}

internal fun UnifiedToolSpec.toOpenAIToolSpec(): ToolSpec {
    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    val params = runCatching {
        json.decodeFromString(ToolParameters.serializer(), parametersJson)
    }.getOrDefault(ToolParameters())
    return ToolSpec(
        type = ToolSpec.TYPE_FUNCTION,
        function = ToolFunction(name = name, description = description, parameters = params)
    )
}

internal fun ChatCompletionResponse.toUnifiedResponse(): UnifiedResponse {
    val choice = choices.firstOrNull()
    val message = choice?.message
    return UnifiedResponse(
        content = message?.content,
        reasoningContent = message?.reasoningContent,
        toolCalls = message?.toolCalls?.map {
            UnifiedToolCall(id = it.id, name = it.function.name, arguments = it.function.arguments)
        },
        finishReason = choice?.finishReason,
        usage = usage?.toUnifiedUsage()
    )
}

internal fun StreamChunk.toUnifiedChunk(): UnifiedChunk {
    val choice = choices.firstOrNull()
    return UnifiedChunk(
        contentDelta = choice?.delta?.content,
        reasoningDelta = choice?.delta?.reasoningContent,
        toolCalls = choice?.delta?.toolCalls?.map {
            UnifiedToolCallDelta(
                index = it.index,
                id = it.id,
                name = it.function.name,
                argumentsDelta = it.function.arguments
            )
        },
        finishReason = choice?.finishReason,
        usage = usage?.toUnifiedUsage()
    )
}

internal fun Usage.toUnifiedUsage() = UnifiedUsage(
    promptTokens = promptTokens,
    completionTokens = completionTokens,
    totalTokens = totalTokens
)
