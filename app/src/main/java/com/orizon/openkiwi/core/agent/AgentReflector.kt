package com.orizon.openkiwi.core.agent

import com.orizon.openkiwi.core.model.*
import com.orizon.openkiwi.network.OpenAIApiClient

enum class ReflectionDecision {
    RETRY_SAME,
    RETRY_DIFFERENT,
    SKIP,
    ABORT
}

data class ReflectionResult(
    val decision: ReflectionDecision,
    val reasoning: String,
    val suggestedAction: String = ""
)

class AgentReflector(
    private val apiClient: OpenAIApiClient
) {
    companion object {
        private const val REFLECTION_PROMPT = """You are an AI agent reflection module. A tool call has failed. Analyze the failure and decide what to do.

Respond with ONLY valid JSON in this format:
{
  "decision": "RETRY_SAME" | "RETRY_DIFFERENT" | "SKIP" | "ABORT",
  "reasoning": "<why this decision>",
  "suggestedAction": "<if RETRY_DIFFERENT, describe what to try instead>"
}

Rules:
- RETRY_SAME: The error is transient (network, timeout). Retry the same call.
- RETRY_DIFFERENT: The error suggests wrong parameters or approach. Suggest a fix.
- SKIP: This step is optional, move on without it.
- ABORT: The error is fatal, stop the entire task."""

        private const val MAX_RETRIES = 2
    }

    private val failureCounts = mutableMapOf<String, Int>()

    suspend fun reflect(
        toolName: String,
        params: Map<String, Any?>,
        error: String,
        config: ModelConfig,
        contextMessages: List<ChatMessage>
    ): ReflectionResult {
        val key = "$toolName:${params.hashCode()}"
        val retryCount = failureCounts.getOrDefault(key, 0)

        if (retryCount >= MAX_RETRIES) {
            return ReflectionResult(
                decision = ReflectionDecision.SKIP,
                reasoning = "已重试 $MAX_RETRIES 次仍然失败，跳过此步骤"
            )
        }

        val reflectionMessages = listOf(
            ChatMessage(role = ChatRole.SYSTEM, content = REFLECTION_PROMPT),
            ChatMessage(role = ChatRole.USER, content = buildString {
                append("Failed tool call:\n")
                append("  Tool: $toolName\n")
                append("  Params: $params\n")
                append("  Error: $error\n")
                append("  Retry count: $retryCount\n")
                append("\nRecent context:\n")
                contextMessages.takeLast(4).forEach { msg ->
                    append("  [${msg.role}]: ${msg.content?.take(200)}\n")
                }
            })
        )

        val request = ChatCompletionRequest(
            model = config.modelName,
            messages = reflectionMessages,
            temperature = 0.2,
            maxTokens = 256
        )

        val result = apiClient.chatCompletion(config.apiBaseUrl, config.apiKey, request)
        val response = result.getOrNull()
        val content = response?.choices?.firstOrNull()?.message?.content

        val parsed = content?.let { parseReflection(it) }
            ?: ReflectionResult(
                decision = if (retryCount == 0) ReflectionDecision.RETRY_SAME else ReflectionDecision.SKIP,
                reasoning = "无法分析失败原因，${if (retryCount == 0) "尝试重试" else "跳过"}"
            )

        if (parsed.decision == ReflectionDecision.RETRY_SAME || parsed.decision == ReflectionDecision.RETRY_DIFFERENT) {
            failureCounts[key] = retryCount + 1
        }

        return parsed
    }

    fun resetForNewTask() {
        failureCounts.clear()
    }

    private fun parseReflection(content: String): ReflectionResult? {
        return runCatching {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val cleaned = content.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val obj = json.parseToJsonElement(cleaned) as? kotlinx.serialization.json.JsonObject
                ?: return null

            val decisionStr = obj["decision"]
                ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                ?: return null

            val decision = runCatching { ReflectionDecision.valueOf(decisionStr) }
                .getOrDefault(ReflectionDecision.SKIP)

            ReflectionResult(
                decision = decision,
                reasoning = obj["reasoning"]
                    ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: "",
                suggestedAction = obj["suggestedAction"]
                    ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: ""
            )
        }.getOrNull()
    }
}
