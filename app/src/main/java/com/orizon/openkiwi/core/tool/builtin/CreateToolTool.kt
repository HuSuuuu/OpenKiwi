package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.tool.*
import com.orizon.openkiwi.data.local.dao.CustomToolDao
import com.orizon.openkiwi.data.local.entity.CustomToolEntity
import kotlinx.serialization.json.Json

class CreateToolTool(
    private val customToolDao: CustomToolDao,
    private val toolRegistry: ToolRegistry,
    private val codeSandbox: com.orizon.openkiwi.core.code.CodeSandbox? = null
) : Tool {

    override val definition = ToolDefinition(
        name = "create_tool",
        description = "Create, update, or delete a custom tool. " +
                "Action 'create': define a new tool with name, description, params, and a shell/python script. " +
                "Action 'delete': remove a custom tool by name. " +
                "Action 'list': list all custom tools.",
        category = ToolCategory.CUSTOM.name,
        permissionLevel = PermissionLevel.NORMAL.name,
        parameters = mapOf(
            "action" to ToolParamDef(
                type = "string",
                description = "Action to perform",
                required = true,
                enumValues = listOf("create", "delete", "list")
            ),
            "name" to ToolParamDef(
                type = "string",
                description = "Unique tool name (lowercase_snake_case)",
                required = false
            ),
            "description" to ToolParamDef(
                type = "string",
                description = "Human-readable description of what the tool does",
                required = false
            ),
            "params_json" to ToolParamDef(
                type = "string",
                description = "JSON object defining parameters, e.g. {\"query\":{\"type\":\"string\",\"description\":\"search query\"}}",
                required = false
            ),
            "required_params" to ToolParamDef(
                type = "string",
                description = "JSON array of required parameter names, e.g. [\"query\"]",
                required = false
            ),
            "language" to ToolParamDef(
                type = "string",
                description = "Script language: 'shell' (default) or 'python'",
                required = false,
                defaultValue = "shell",
                enumValues = listOf("shell", "python")
            ),
            "script" to ToolParamDef(
                type = "string",
                description = "Script that implements the tool. Use \$param_name or \${param_name} to reference parameters. For shell: also available as TOOL_param_name env vars. For python: params are injected as local variables.",
                required = false
            )
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString()
            ?: return ToolResult(definition.name, false, "", error = "Missing action")

        return when (action) {
            "create" -> createTool(params)
            "delete" -> deleteTool(params)
            "list" -> listTools()
            else -> ToolResult(definition.name, false, "", error = "Unknown action: $action")
        }
    }

    private suspend fun createTool(params: Map<String, Any?>): ToolResult {
        val name = params["name"]?.toString()?.trim()
        val desc = params["description"]?.toString()?.trim()
        val script = params["script"]?.toString()
        val paramsJson = params["params_json"]?.toString() ?: "{}"
        val requiredParams = params["required_params"]?.toString() ?: "[]"

        if (name.isNullOrBlank() || desc.isNullOrBlank() || script.isNullOrBlank()) {
            return ToolResult(
                definition.name, false, "",
                error = "create requires 'name', 'description', and 'script'"
            )
        }

        val reservedNames = setOf(
            "create_tool", "shell_command", "memory", "code_execution",
            "web_search", "web_fetch", "file_manager", "system_info"
        )
        if (name in reservedNames) {
            return ToolResult(
                definition.name, false, "",
                error = "Cannot overwrite built-in tool: $name"
            )
        }

        runCatching { Json.parseToJsonElement(paramsJson) }.onFailure {
            return ToolResult(definition.name, false, "", error = "Invalid params_json: ${it.message}")
        }
        runCatching { Json.parseToJsonElement(requiredParams) }.onFailure {
            return ToolResult(definition.name, false, "", error = "Invalid required_params: ${it.message}")
        }

        val language = params["language"]?.toString()?.lowercase() ?: "shell"

        val entity = CustomToolEntity(
            name = name,
            description = desc,
            paramsJson = paramsJson,
            requiredParamsJson = requiredParams,
            script = script,
            language = language
        )
        customToolDao.insert(entity)

        val dynamicTool = DynamicTool(entity, codeSandbox)
        toolRegistry.register(dynamicTool)

        return ToolResult(
            definition.name, true,
            "Tool '$name' created and registered successfully.\n" +
                    "Description: $desc\n" +
                    "The tool is now available for use."
        )
    }

    private suspend fun deleteTool(params: Map<String, Any?>): ToolResult {
        val name = params["name"]?.toString()?.trim()
            ?: return ToolResult(definition.name, false, "", error = "delete requires 'name'")

        val existing = customToolDao.getByName(name)
            ?: return ToolResult(definition.name, false, "", error = "Custom tool not found: $name")

        customToolDao.delete(name)
        toolRegistry.unregister(name)

        return ToolResult(definition.name, true, "Tool '$name' deleted and unregistered.")
    }

    private suspend fun listTools(): ToolResult {
        val tools = customToolDao.getEnabledTools()
        if (tools.isEmpty()) {
            return ToolResult(definition.name, true, "No custom tools created yet.")
        }
        val text = tools.joinToString("\n") { t ->
            "• ${t.name}: ${t.description} (created by ${t.createdBy})"
        }
        return ToolResult(definition.name, true, "Custom tools (${tools.size}):\n$text")
    }
}
