package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.tool.*
import kotlinx.serialization.json.Json

class PipelineManagementTool(
    private val pipelineManager: PipelineManager,
    private val toolRegistry: ToolRegistry
) : Tool {
    override val definition = ToolDefinition(
        name = "tool_pipeline",
        description = "Create, list, run, and delete tool pipelines. Pipelines chain multiple tools together, passing output from one step to the next via template variables like \${prev} or \${step_1}.",
        category = ToolCategory.CUSTOM.name,
        permissionLevel = PermissionLevel.NORMAL.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action to perform", true,
                enumValues = listOf("create", "list", "run", "delete", "list_tools")),
            "name" to ToolParamDef("string", "Pipeline name (for create/run/delete)"),
            "description" to ToolParamDef("string", "Pipeline description (for create)"),
            "steps_json" to ToolParamDef("string", "JSON array of steps: [{\"toolName\":\"...\",\"params\":{\"action\":\"...\",\"query\":\"\${prev}\"},\"outputKey\":\"result\"}]"),
            "params_json" to ToolParamDef("string", "JSON object of runtime parameters to pass when running the pipeline")
        ),
        requiredParams = listOf("action")
    )

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return err("Missing action")
        return runCatching {
            when (action) {
                "create" -> createPipeline(params)
                "list" -> listPipelines()
                "run" -> runPipeline(params)
                "delete" -> deletePipeline(params)
                "list_tools" -> listAvailableTools()
                else -> err("Unknown action: $action")
            }
        }.getOrElse { ToolResult(definition.name, false, "", it.message) }
    }

    private fun createPipeline(params: Map<String, Any?>): ToolResult {
        val name = params["name"]?.toString() ?: return err("Missing name")
        val desc = params["description"]?.toString() ?: ""
        val stepsJson = params["steps_json"]?.toString() ?: return err("Missing steps_json")
        val steps: List<PipelineStep> = json.decodeFromString(stepsJson)
        if (steps.isEmpty()) return err("steps_json must contain at least one step")

        for (step in steps) {
            if (toolRegistry.getTool(step.toolName) == null) {
                return err("Tool '${step.toolName}' not found in registry")
            }
        }

        val pipeline = PipelineDefinition(name = name, description = desc, steps = steps)
        pipelineManager.registerPipeline(pipeline)
        return ToolResult(definition.name, true,
            "Pipeline '$name' created with ${steps.size} steps: ${steps.joinToString(" → ") { it.toolName }}")
    }

    private fun listPipelines(): ToolResult {
        val all = pipelineManager.getAllPipelines()
        if (all.isEmpty()) return ToolResult(definition.name, true, "No pipelines registered.")
        val lines = all.map { p ->
            "${p.name}: ${p.description} (${p.steps.size} steps: ${p.steps.joinToString("→") { it.toolName }})"
        }
        return ToolResult(definition.name, true, "Pipelines:\n${lines.joinToString("\n")}")
    }

    private suspend fun runPipeline(params: Map<String, Any?>): ToolResult {
        val name = params["name"]?.toString() ?: return err("Missing name")
        val pipeline = pipelineManager.getPipeline(name)
            ?: return err("Pipeline '$name' not found")
        val runtimeParams = params["params_json"]?.toString()?.let {
            runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrDefault(emptyMap())
        } ?: emptyMap()

        val tool = PipelineTool(pipeline, toolRegistry)
        return tool.execute(runtimeParams)
    }

    private fun deletePipeline(params: Map<String, Any?>): ToolResult {
        val name = params["name"]?.toString() ?: return err("Missing name")
        pipelineManager.unregisterPipeline(name)
        return ToolResult(definition.name, true, "Pipeline '$name' deleted.")
    }

    private fun listAvailableTools(): ToolResult {
        val tools = toolRegistry.getEnabledTools()
        val lines = tools.map { t ->
            val paramStr = t.definition.parameters.entries.take(5).joinToString(", ") { (k, v) -> "$k(${v.type})" }
            "${t.definition.name}: ${t.definition.description.take(60)}... [$paramStr]"
        }
        return ToolResult(definition.name, true, "Available tools (${tools.size}):\n${lines.joinToString("\n")}")
    }

    private fun err(msg: String) = ToolResult(definition.name, false, "", msg)
}
