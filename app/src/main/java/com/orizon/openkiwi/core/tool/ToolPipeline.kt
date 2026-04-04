package com.orizon.openkiwi.core.tool

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PipelineStep(
    val toolName: String,
    val params: Map<String, String> = emptyMap(),
    val outputKey: String = "output"
)

@Serializable
data class PipelineDefinition(
    val name: String,
    val description: String,
    val steps: List<PipelineStep>,
    val pipelineParams: Map<String, ToolParamDef> = emptyMap(),
    val requiredParams: List<String> = emptyList()
)

class PipelineTool(
    private val pipeline: PipelineDefinition,
    private val toolRegistry: ToolRegistry
) : Tool {

    override val definition = ToolDefinition(
        name = "pipeline_${pipeline.name}",
        description = pipeline.description,
        category = ToolCategory.CUSTOM.name,
        permissionLevel = PermissionLevel.NORMAL.name,
        parameters = pipeline.pipelineParams,
        requiredParams = pipeline.requiredParams,
        timeoutMs = 120_000
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val context = mutableMapOf<String, String>()
        params.forEach { (k, v) -> context[k] = v?.toString() ?: "" }

        val results = StringBuilder()
        for ((index, step) in pipeline.steps.withIndex()) {
            val tool = toolRegistry.getTool(step.toolName)
                ?: return ToolResult(definition.name, false, results.toString(),
                    "Step ${index + 1}: tool '${step.toolName}' not found")

            val resolvedParams = step.params.mapValues { (_, v) -> resolveTemplate(v, context) as Any? }
            val stepResult = tool.execute(resolvedParams)

            context[step.outputKey] = stepResult.output
            context["step_${index + 1}"] = stepResult.output
            context["prev"] = stepResult.output

            results.appendLine("[Step ${index + 1}: ${step.toolName}] ${if (stepResult.success) "OK" else "FAILED"}")
            if (!stepResult.success) {
                results.appendLine("Error: ${stepResult.error}")
                return ToolResult(definition.name, false, results.toString(),
                    "Pipeline failed at step ${index + 1} (${step.toolName}): ${stepResult.error}")
            }
        }

        val finalOutput = context["prev"] ?: ""
        results.appendLine("---\n$finalOutput")
        return ToolResult(definition.name, true, results.toString())
    }

    private fun resolveTemplate(template: String, context: Map<String, String>): String {
        var result = template
        context.forEach { (key, value) ->
            result = result.replace("\${$key}", value)
            result = result.replace("{{$key}}", value)
        }
        return result
    }
}

class PipelineManager(private val toolRegistry: ToolRegistry) {
    private val pipelines = mutableMapOf<String, PipelineDefinition>()
    private val json = Json { ignoreUnknownKeys = true }

    fun registerPipeline(pipeline: PipelineDefinition) {
        pipelines[pipeline.name] = pipeline
        toolRegistry.register(PipelineTool(pipeline, toolRegistry))
    }

    fun unregisterPipeline(name: String) {
        pipelines.remove(name)
        toolRegistry.unregister("pipeline_$name")
    }

    fun getPipeline(name: String): PipelineDefinition? = pipelines[name]

    fun getAllPipelines(): List<PipelineDefinition> = pipelines.values.toList()

    fun parsePipeline(jsonStr: String): PipelineDefinition = json.decodeFromString(jsonStr)
}
