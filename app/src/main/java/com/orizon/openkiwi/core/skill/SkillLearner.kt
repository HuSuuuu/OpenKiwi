package com.orizon.openkiwi.core.skill

import com.orizon.openkiwi.core.tool.ToolResult
import java.util.UUID

/**
 * Learns and extracts skills from successful task execution traces.
 * Analyzes tool call sequences to create reusable skill definitions.
 */
class SkillLearner(private val skillManager: SkillManager) {

    data class TaskTrace(
        val goal: String,
        val toolCalls: List<ToolCallRecord>,
        val success: Boolean,
        val totalTimeMs: Long = 0
    )

    data class ToolCallRecord(
        val toolName: String,
        val params: Map<String, String>,
        val result: ToolResult,
        val stepIndex: Int
    )

    suspend fun learnFromTrace(trace: TaskTrace): SkillDefinition? {
        if (!trace.success || trace.toolCalls.size < 2) return null

        val successfulCalls = trace.toolCalls.filter { it.result.success }
        if (successfulCalls.size < 2) return null

        val steps = successfulCalls.map { call ->
            SkillStep(
                toolName = call.toolName,
                params = call.params.mapValues { (key, value) ->
                    if (isLikelyVariable(key, value)) "\${$key}" else value
                },
                description = "Step ${call.stepIndex + 1}: ${call.toolName}"
            )
        }

        val inputParams = extractInputParams(successfulCalls)
        val name = generateSkillName(trace.goal)
        val description = "Auto-learned from: ${trace.goal}"

        val definition = SkillDefinition(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            type = "flow",
            category = "auto_learned",
            version = "1.0",
            steps = steps,
            inputParams = inputParams,
            outputDescription = "Executes the learned task: ${trace.goal}"
        )

        skillManager.createSkill(definition)
        return definition
    }

    suspend fun optimizeSkill(skillId: String, traces: List<TaskTrace>): SkillDefinition? {
        val skill = skillManager.getSkill(skillId) ?: return null

        val successTraces = traces.filter { it.success }
        if (successTraces.size < 2) return null

        // Find common successful patterns
        val commonSteps = findCommonSteps(successTraces)
        if (commonSteps.isEmpty()) return null

        val currentVersion = skill.version.toDoubleOrNull() ?: 1.0
        val newVersion = String.format("%.1f", currentVersion + 0.1)

        val optimized = skill.copy(
            steps = commonSteps,
            version = newVersion
        )

        skillManager.updateSkill(optimized)
        return optimized
    }

    private fun isLikelyVariable(key: String, value: String): Boolean {
        val variableHints = listOf("name", "query", "text", "path", "url", "input", "target", "keyword")
        return variableHints.any { key.contains(it, ignoreCase = true) } ||
                value.length > 50 ||
                value.contains(" ")
    }

    private fun extractInputParams(calls: List<ToolCallRecord>): Map<String, String> {
        val params = mutableMapOf<String, String>()
        for (call in calls) {
            for ((key, value) in call.params) {
                if (isLikelyVariable(key, value)) {
                    params[key] = "string"
                }
            }
        }
        return params
    }

    private fun generateSkillName(goal: String): String {
        val maxLen = 30
        val cleaned = goal.replace(Regex("[^\\p{L}\\p{N}\\s_-]"), "").trim()
        return if (cleaned.length > maxLen) cleaned.take(maxLen) + "..." else cleaned
    }

    private fun findCommonSteps(traces: List<TaskTrace>): List<SkillStep> {
        if (traces.isEmpty()) return emptyList()

        // Use the shortest successful trace as baseline
        val baseline = traces.minByOrNull { it.toolCalls.size } ?: return emptyList()

        return baseline.toolCalls
            .filter { it.result.success }
            .map { call ->
                SkillStep(
                    toolName = call.toolName,
                    params = call.params.mapValues { (key, value) ->
                        if (isLikelyVariable(key, value)) "\${$key}" else value
                    },
                    description = call.toolName
                )
            }
    }
}
