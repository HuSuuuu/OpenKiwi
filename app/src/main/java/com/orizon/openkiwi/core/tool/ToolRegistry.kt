package com.orizon.openkiwi.core.tool

import com.orizon.openkiwi.core.model.ToolFunction
import com.orizon.openkiwi.core.model.ToolParameters
import com.orizon.openkiwi.core.model.ToolProperty
import com.orizon.openkiwi.core.model.ToolSpec

class ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.definition.name] = tool
    }

    fun unregister(name: String) {
        tools.remove(name)
    }

    fun getTool(name: String): Tool? = tools[name]

    fun getAllTools(): List<Tool> = tools.values.toList()

    fun getEnabledTools(): List<Tool> = tools.values.filter { it.definition.isEnabled }

    fun getToolsByCategory(category: String): List<Tool> =
        tools.values.filter { it.definition.category == category }

    fun getToolsByPermissionLevel(level: PermissionLevel): List<Tool> =
        tools.values.filter { it.definition.permissionLevel == level.name }

    fun toToolSpecs(): List<ToolSpec> = getEnabledTools().map { tool ->
        val def = tool.definition
        ToolSpec(
            type = "function",
            function = ToolFunction(
                name = def.name,
                description = def.description,
                parameters = ToolParameters(
                    type = "object",
                    properties = def.parameters.mapValues { (_, param) ->
                        ToolProperty(
                            type = param.type,
                            description = param.description,
                            enum = param.enumValues
                        )
                    },
                    required = def.requiredParams
                )
            )
        )
    }
}
