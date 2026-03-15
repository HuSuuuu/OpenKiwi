package com.orizon.openkiwi.core.skill

import com.orizon.openkiwi.core.tool.ToolExecutor
import com.orizon.openkiwi.core.tool.ToolResult

class SkillExecutor(private val toolExecutor: ToolExecutor) {

    suspend fun executeSkill(skill: SkillDefinition, inputParams: Map<String, String> = emptyMap()): SkillExecutionResult {
        val results = mutableListOf<ToolResult>()
        val context = inputParams.toMutableMap()

        for ((index, step) in skill.steps.withIndex()) {
            val resolvedParams = step.params.mapValues { (_, v) ->
                var resolved = v
                context.forEach { (key, value) -> resolved = resolved.replace("\${$key}", value) }
                resolved
            }

            val result = toolExecutor.execute(
                toolName = step.toolName,
                params = resolvedParams,
                timeoutMs = 60_000
            )
            results.add(result)
            context["step_${index}_output"] = result.output
            context["step_${index}_success"] = result.success.toString()

            if (!result.success && step.onError == "stop") {
                return SkillExecutionResult(
                    skillId = skill.id, success = false, stepResults = results,
                    error = "Step $index (${step.toolName}) failed: ${result.error}"
                )
            }
        }

        return SkillExecutionResult(
            skillId = skill.id, success = true, stepResults = results,
            output = results.lastOrNull()?.output ?: ""
        )
    }
}

data class SkillExecutionResult(
    val skillId: String,
    val success: Boolean,
    val stepResults: List<ToolResult> = emptyList(),
    val output: String = "",
    val error: String? = null
)
