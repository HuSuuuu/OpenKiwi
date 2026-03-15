package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.agent.SubAgentConfig
import com.orizon.openkiwi.core.agent.SubAgentManager
import com.orizon.openkiwi.core.tool.*
import kotlinx.coroutines.flow.toList

class SubAgentTool(private val manager: SubAgentManager) : Tool {
    override val definition = ToolDefinition(
        name = "sub_agent",
        description = "Create, manage, and assign tasks to specialized sub-agents. Each sub-agent has its own role, system prompt, model, and tool permissions.",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: create, execute, list, status, destroy", true,
                enumValues = listOf("create", "execute", "list", "status", "destroy")),
            "agent_id" to ToolParamDef("string", "SubAgent ID (for execute/status/destroy)"),
            "name" to ToolParamDef("string", "SubAgent name (for create)"),
            "role" to ToolParamDef("string", "SubAgent role description (for create)"),
            "system_prompt" to ToolParamDef("string", "System prompt (for create)"),
            "task" to ToolParamDef("string", "Task to execute (for execute)"),
            "tools" to ToolParamDef("string", "Comma-separated tool names to enable (for create, empty=all)")
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return errorResult("Missing action")
        return runCatching {
            when (action) {
                "create" -> {
                    val name = params["name"]?.toString() ?: "SubAgent"
                    val role = params["role"]?.toString() ?: ""
                    val prompt = params["system_prompt"]?.toString() ?: "You are a helpful assistant named $name. $role"
                    val tools = params["tools"]?.toString()?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                    val config = SubAgentConfig(name = name, role = role, systemPrompt = prompt, enabledTools = tools)
                    val id = manager.createAgent(config)
                    ToolResult(definition.name, true, "Created SubAgent '$name' (id=$id)")
                }
                "execute" -> {
                    val agentId = params["agent_id"]?.toString() ?: return@runCatching errorResult("Missing agent_id")
                    val task = params["task"]?.toString() ?: return@runCatching errorResult("Missing task")
                    val resultBuilder = StringBuilder()
                    manager.executeTask(agentId, task).collect { resultBuilder.append(it) }
                    ToolResult(definition.name, true, resultBuilder.toString())
                }
                "list" -> {
                    val agents = manager.listAgents()
                    if (agents.isEmpty()) ToolResult(definition.name, true, "No active sub-agents")
                    else {
                        val sb = StringBuilder("Active SubAgents (${agents.size}):\n")
                        agents.forEach { a -> sb.appendLine("  [${a.id.take(8)}] ${a.config.name} — ${a.status}") }
                        ToolResult(definition.name, true, sb.toString())
                    }
                }
                "status" -> {
                    val agentId = params["agent_id"]?.toString() ?: return@runCatching errorResult("Missing agent_id")
                    val state = manager.getAgent(agentId) ?: return@runCatching errorResult("Agent not found: $agentId")
                    val info = buildString {
                        appendLine("SubAgent: ${state.config.name}")
                        appendLine("ID: ${state.id}")
                        appendLine("Status: ${state.status}")
                        appendLine("Role: ${state.config.role}")
                        state.lastResult?.let { appendLine("Last result: ${it.take(200)}") }
                    }
                    ToolResult(definition.name, true, info)
                }
                "destroy" -> {
                    val agentId = params["agent_id"]?.toString() ?: return@runCatching errorResult("Missing agent_id")
                    manager.destroyAgent(agentId)
                    ToolResult(definition.name, true, "Destroyed SubAgent: $agentId")
                }
                else -> errorResult("Unknown action: $action")
            }
        }.getOrElse { ToolResult(definition.name, false, "", it.message) }
    }
    private fun errorResult(msg: String) = ToolResult(definition.name, false, "", msg)
}
