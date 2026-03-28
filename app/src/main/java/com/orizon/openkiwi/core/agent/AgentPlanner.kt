package com.orizon.openkiwi.core.agent

import com.orizon.openkiwi.core.model.*
import com.orizon.openkiwi.network.OpenAIApiClient
import com.orizon.openkiwi.core.llm.LlmProviderFactory
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TaskPlan(
    val goal: String,
    val steps: List<PlanStep>,
    val reasoning: String = ""
)

@Serializable
data class PlanStep(
    val index: Int,
    val description: String,
    val toolHint: String = "",
    val dependsOn: List<Int> = emptyList()
)

class AgentPlanner(
    private val apiClient: OpenAIApiClient,
    private val llmProviderFactory: LlmProviderFactory? = null
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val PLANNING_PROMPT = """You are a task planner. Given the user's request, determine if it requires a multi-step plan.

If the task is simple (1-2 steps), respond with:
{"needsPlan": false}

If the task is complex (3+ steps), respond with a structured plan:
{"needsPlan": true, "plan": {"goal": "<goal>", "reasoning": "<why this plan>", "steps": [{"index": 1, "description": "<what to do>", "toolHint": "<suggested tool name or empty>", "dependsOn": []}]}}

Only output valid JSON, no other text."""
    }

    suspend fun createPlan(
        userMessage: String,
        config: ModelConfig,
        availableTools: List<String>
    ): TaskPlan? {
        val toolList = availableTools.joinToString(", ")
        val planningMessages = listOf(
            ChatMessage(role = ChatRole.SYSTEM, content = PLANNING_PROMPT),
            ChatMessage(role = ChatRole.USER, content = buildString {
                append("User request: $userMessage\n")
                append("Available tools: $toolList")
            })
        )

        val request = ChatCompletionRequest(
            model = config.modelName,
            messages = planningMessages,
            temperature = 0.3,
            maxTokens = 1024
        )

        val result = apiClient.chatCompletion(config.apiBaseUrl, config.apiKey, request)
        val response = result.getOrNull() ?: return null
        val content = response.choices.firstOrNull()?.message?.content ?: return null

        return parsePlanResponse(content)
    }

    private fun parsePlanResponse(content: String): TaskPlan? {
        return runCatching {
            val jsonContent = content.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            val obj = json.parseToJsonElement(jsonContent) as? kotlinx.serialization.json.JsonObject
                ?: return null

            val needsPlan = obj["needsPlan"]
                ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull() }
                ?: false

            if (!needsPlan) return null

            val planObj = obj["plan"] ?: return null
            json.decodeFromJsonElement(TaskPlan.serializer(), planObj)
        }.getOrNull()
    }

    fun formatPlanForDisplay(plan: TaskPlan): String = buildString {
        append("📋 任务规划: ${plan.goal}\n")
        if (plan.reasoning.isNotBlank()) append("💡 ${plan.reasoning}\n")
        append("\n")
        for (step in plan.steps) {
            append("  ${step.index}. ${step.description}")
            if (step.toolHint.isNotBlank()) append(" [${step.toolHint}]")
            append("\n")
        }
    }
}
