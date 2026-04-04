package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.openclaw.OpenClawSkillLoader
import com.orizon.openkiwi.core.openclaw.OpenClawSkillRegistry
import com.orizon.openkiwi.core.tool.*

/**
 * AI-facing tool for managing and using OpenClaw-compatible skills natively.
 *
 * Actions:
 *   list          — List all available OpenClaw skills.
 *   read          — Read the full instructions of a skill (rewritten for OpenKiwi).
 *   search        — Search skills by keyword.
 *   import_text   — Import a skill from raw SKILL.md text.
 *   import_file   — Import a skill from a file path.
 *   import_dir    — Scan a directory and import all SKILL.md files found.
 *   enable        — Enable a skill.
 *   disable       — Disable a skill.
 *   remove        — Remove an imported skill.
 *   info          — Show detailed info about a skill.
 */
class OpenClawSkillsTool(
    private val registry: OpenClawSkillRegistry
) : Tool {

    override val definition = ToolDefinition(
        name = "openclaw_skills",
        description = "Manage and use OpenClaw-compatible skills. " +
                "Skills are AI instruction sets from the OpenClaw ecosystem that teach you " +
                "how to perform complex tasks using existing tools. " +
                "Read a skill before performing tasks that match its description.",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.NORMAL.name,
        parameters = mapOf(
            "action" to ToolParamDef(
                "string",
                "Action: list, read, search, import_text, import_file, import_dir, enable, disable, remove, info",
                required = true,
                enumValues = listOf("list", "read", "search", "import_text", "import_file",
                    "import_dir", "enable", "disable", "remove", "info")
            ),
            "id" to ToolParamDef("string", "Skill ID (for read/enable/disable/remove/info)"),
            "query" to ToolParamDef("string", "Search query (for search action)"),
            "content" to ToolParamDef("string", "Raw SKILL.md content (for import_text)"),
            "path" to ToolParamDef("string", "File or directory path (for import_file / import_dir)")
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString()
            ?: return errorResult("Missing action")

        return runCatching {
            when (action) {
                "list" -> handleList()
                "read" -> handleRead(params)
                "search" -> handleSearch(params)
                "import_text" -> handleImportText(params)
                "import_file" -> handleImportFile(params)
                "import_dir" -> handleImportDir(params)
                "enable" -> handleToggle(params, true)
                "disable" -> handleToggle(params, false)
                "remove" -> handleRemove(params)
                "info" -> handleInfo(params)
                else -> errorResult("Unknown action: $action")
            }
        }.getOrElse { e ->
            errorResult(e.message ?: "Unknown error")
        }
    }

    private fun handleList(): ToolResult {
        val skills = registry.getAllSkills()
        if (skills.isEmpty()) {
            return successResult(
                "No OpenClaw skills loaded.\n\n" +
                "You can import skills using:\n" +
                "  • action=import_file path=/path/to/SKILL.md\n" +
                "  • action=import_dir path=/path/to/skills_directory\n" +
                "  • action=import_text content=\"---\\nname: ...\\n---\\n...\""
            )
        }

        val sb = StringBuilder("OpenClaw Skills (${skills.size}):\n\n")
        for (skill in skills.sortedBy { it.name }) {
            val status = if (skill.isEnabled) "✓" else "✗"
            val emoji = skill.emoji?.let { "$it " } ?: ""
            sb.appendLine("  [$status] ${emoji}${skill.name} (id: ${skill.id})")
            sb.appendLine("      ${skill.description.take(100)}")
            sb.appendLine("      Source: ${skill.source}")
        }
        sb.appendLine("\nUse action=read id=<skill_id> to read full skill instructions.")
        return successResult(sb.toString())
    }

    private fun handleRead(params: Map<String, Any?>): ToolResult {
        val id = params["id"]?.toString()
            ?: return errorResult("Missing 'id' — specify which skill to read")
        val instructions = registry.getRewrittenInstructions(id)
            ?: return errorResult("Skill '$id' not found. Use action=list to see available skills.")

        val skill = registry.getSkill(id)!!
        return successResult(buildString {
            appendLine("# ${skill.emoji ?: ""}${skill.name}")
            appendLine("_${skill.description}_\n")
            appendLine("---\n")
            appendLine(instructions)
            appendLine("\n---")
            appendLine("_Note: OpenClaw tool names have been mapped to OpenKiwi equivalents where applicable._")
        })
    }

    private fun handleSearch(params: Map<String, Any?>): ToolResult {
        val query = params["query"]?.toString()
            ?: return errorResult("Missing 'query'")
        val results = registry.search(query)
        if (results.isEmpty()) {
            return successResult("No skills match '$query'.")
        }

        val sb = StringBuilder("Skills matching '$query' (${results.size}):\n\n")
        for (skill in results) {
            val emoji = skill.emoji?.let { "$it " } ?: ""
            sb.appendLine("  • ${emoji}${skill.name} (id: ${skill.id})")
            sb.appendLine("    ${skill.description.take(100)}")
        }
        return successResult(sb.toString())
    }

    private fun handleImportText(params: Map<String, Any?>): ToolResult {
        val content = params["content"]?.toString()
            ?: return errorResult("Missing 'content' — provide raw SKILL.md content")
        val skill = registry.importFromText(content, source = "text_import")
            ?: return errorResult("Failed to parse skill. Ensure it has YAML frontmatter with 'description'.")
        return successResult("Imported skill '${skill.name}' (id: ${skill.id}).\n${skill.description}")
    }

    private fun handleImportFile(params: Map<String, Any?>): ToolResult {
        val path = params["path"]?.toString()
            ?: return errorResult("Missing 'path'")
        val skill = registry.importFromFile(path)
            ?: return errorResult("Failed to import from '$path'. File must be a valid SKILL.md.")
        return successResult("Imported skill '${skill.name}' (id: ${skill.id}).\n${skill.description}")
    }

    private fun handleImportDir(params: Map<String, Any?>): ToolResult {
        val path = params["path"]?.toString()
            ?: return errorResult("Missing 'path'")
        val imported = registry.importFromDirectory(path)
        if (imported.isEmpty()) {
            return successResult("No SKILL.md files found in '$path'.")
        }
        val sb = StringBuilder("Imported ${imported.size} skills:\n\n")
        for (skill in imported) {
            sb.appendLine("  • ${skill.name} (${skill.id}): ${skill.description.take(80)}")
        }
        return successResult(sb.toString())
    }

    private fun handleToggle(params: Map<String, Any?>, enabled: Boolean): ToolResult {
        val id = params["id"]?.toString()
            ?: return errorResult("Missing 'id'")
        registry.setEnabled(id, enabled)
        val state = if (enabled) "enabled" else "disabled"
        return successResult("Skill '$id' $state.")
    }

    private fun handleRemove(params: Map<String, Any?>): ToolResult {
        val id = params["id"]?.toString()
            ?: return errorResult("Missing 'id'")
        val success = registry.remove(id)
        return if (success) {
            successResult("Removed skill '$id'.")
        } else {
            errorResult("Cannot remove skill '$id'. Bundled skills cannot be removed (only disabled).")
        }
    }

    private fun handleInfo(params: Map<String, Any?>): ToolResult {
        val id = params["id"]?.toString()
            ?: return errorResult("Missing 'id'")
        val skill = registry.getSkill(id)
            ?: return errorResult("Skill '$id' not found.")
        return successResult(buildString {
            appendLine("Skill: ${skill.name}")
            appendLine("ID: ${skill.id}")
            appendLine("Description: ${skill.description}")
            appendLine("Source: ${skill.source}")
            appendLine("Enabled: ${skill.isEnabled}")
            appendLine("User Invocable: ${skill.userInvocable}")
            appendLine("Model Visible: ${!skill.disableModelInvocation}")
            skill.emoji?.let { appendLine("Emoji: $it") }
            skill.homepage?.let { appendLine("Homepage: $it") }
            skill.commandDispatch?.let { appendLine("Command Dispatch: $it") }
            skill.commandTool?.let { appendLine("Command Tool: $it") }
            appendLine("Body size: ${skill.body.length} chars")
        })
    }

    private fun successResult(output: String) = ToolResult(
        toolName = definition.name, success = true, output = output
    )

    private fun errorResult(msg: String) = ToolResult(
        toolName = definition.name, success = false, output = "", error = msg
    )
}
