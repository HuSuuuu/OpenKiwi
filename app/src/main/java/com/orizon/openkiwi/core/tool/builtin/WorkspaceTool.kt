package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.agent.AgentWorkspace
import com.orizon.openkiwi.core.tool.*

/**
 * Lets the AI agent read, write, and manage its own workspace files.
 * This is the mechanism for self-evolution: the agent can rewrite its own
 * rules (AGENTS.md), record user preferences (USER.md), learn skills
 * (SKILLS.md), and persist memory (MEMORY.md).
 */
class WorkspaceTool(private val workspace: AgentWorkspace) : Tool {
    override val definition = ToolDefinition(
        name = "workspace",
        description = "Read and write your own workspace files to evolve your behavior. " +
            "Well-known files: AGENTS.md (your operating rules), USER.md (user preferences), " +
            "TOOLS.md (tool notes), MEMORY.md (long-term memory), SKILLS.md (learned skills), " +
            "HEARTBEAT.md (periodic tasks). You can also create custom files. " +
            "Changes persist across sessions and take effect on the next conversation.",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.NORMAL.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action to perform", true,
                enumValues = listOf("read", "write", "append", "list", "delete")),
            "file" to ToolParamDef("string", "File name, e.g. AGENTS.md, USER.md, or any custom name"),
            "content" to ToolParamDef("string", "Content to write or append (for write/append actions)")
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return err("Missing action")
        return runCatching {
            when (action) {
                "read" -> doRead(params)
                "write" -> doWrite(params)
                "append" -> doAppend(params)
                "list" -> doList()
                "delete" -> doDelete(params)
                else -> err("Unknown action: $action")
            }
        }.getOrElse { ToolResult(definition.name, false, "", it.message) }
    }

    private fun doRead(params: Map<String, Any?>): ToolResult {
        val file = params["file"]?.toString() ?: return err("Missing file name")
        val content = workspace.read(file)
            ?: return ToolResult(definition.name, true, "(file '$file' does not exist yet)")
        return ToolResult(definition.name, true, content)
    }

    private fun doWrite(params: Map<String, Any?>): ToolResult {
        val file = params["file"]?.toString() ?: return err("Missing file name")
        val content = params["content"]?.toString() ?: return err("Missing content")
        return if (workspace.write(file, content)) {
            ToolResult(definition.name, true, "Written to '$file' (${content.length} chars). Changes take effect next conversation.")
        } else {
            err("Failed to write '$file' — invalid path")
        }
    }

    private fun doAppend(params: Map<String, Any?>): ToolResult {
        val file = params["file"]?.toString() ?: return err("Missing file name")
        val content = params["content"]?.toString() ?: return err("Missing content")
        return if (workspace.append(file, content)) {
            ToolResult(definition.name, true, "Appended to '$file' (${content.length} chars).")
        } else {
            err("Failed to append to '$file' — invalid path")
        }
    }

    private fun doList(): ToolResult {
        val files = workspace.list()
        return if (files.isEmpty()) {
            ToolResult(definition.name, true, "Workspace is empty.")
        } else {
            ToolResult(definition.name, true, "Workspace files:\n${files.joinToString("\n") { "- $it" }}")
        }
    }

    private fun doDelete(params: Map<String, Any?>): ToolResult {
        val file = params["file"]?.toString() ?: return err("Missing file name")
        return if (workspace.delete(file)) {
            ToolResult(definition.name, true, "Deleted '$file'.")
        } else {
            err("Cannot delete '$file' — well-known files cannot be deleted, or file not found.")
        }
    }

    private fun err(msg: String) = ToolResult(definition.name, false, "", msg)
}
