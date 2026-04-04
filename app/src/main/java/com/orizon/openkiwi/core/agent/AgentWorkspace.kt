package com.orizon.openkiwi.core.agent

import android.content.Context
import java.io.File

/**
 * Persistent workspace that the AI agent can read and write to evolve its own
 * behavior, rules, and knowledge across sessions — similar to OpenClaw's
 * SOUL.md / AGENTS.md / SKILL.md pattern.
 *
 * Files live in `{filesDir}/agent_workspace/` and survive app restarts.
 * The agent has explicit read/write access via [WorkspaceTool].
 */
class AgentWorkspace(context: Context) {

    private val root = File(context.filesDir, "agent_workspace").also { it.mkdirs() }

    companion object {
        /** Well-known workspace files. The agent may also create arbitrary files. */
        const val AGENTS = "AGENTS.md"
        const val USER = "USER.md"
        const val TOOLS = "TOOLS.md"
        const val MEMORY = "MEMORY.md"
        const val SKILLS = "SKILLS.md"
        const val HEARTBEAT = "HEARTBEAT.md"

        private val WELL_KNOWN = listOf(AGENTS, USER, TOOLS, MEMORY, SKILLS, HEARTBEAT)

        private val DEFAULT_CONTENT = mapOf(
            AGENTS to """# Agent Rules
Write operating instructions here. The agent reads this on every conversation start.
- Be concise and helpful.
- Respect user preferences stored in USER.md.
- Learn from interactions and update SKILLS.md / MEMORY.md.
""",
            USER to """# User Profile
Record user preferences, habits, and context here.
The agent should proactively update this file when learning new preferences.
""",
            TOOLS to """# Tool Notes
Record observations about tools here (which tools work well for what, gotchas, etc.).
""",
            MEMORY to """# Long-term Memory
Important facts, decisions, and context the agent should remember across sessions.
""",
            SKILLS to """# Learned Skills
Document reusable procedures, workflows, and techniques the agent has learned.
""",
            HEARTBEAT to """# Heartbeat Tasks
Periodic ambient tasks the agent should consider. Checked on session start.
"""
        )
    }

    fun initialize() {
        WELL_KNOWN.forEach { name ->
            val file = File(root, name)
            if (!file.exists()) {
                file.writeText(DEFAULT_CONTENT[name] ?: "")
            }
        }
    }

    fun read(fileName: String): String? {
        val file = resolve(fileName) ?: return null
        return if (file.exists()) file.readText() else null
    }

    fun write(fileName: String, content: String): Boolean {
        if (fileName.contains("..") || fileName.startsWith("/")) return false
        val file = File(root, fileName)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return true
    }

    fun append(fileName: String, content: String): Boolean {
        if (fileName.contains("..") || fileName.startsWith("/")) return false
        val file = File(root, fileName)
        file.parentFile?.mkdirs()
        file.appendText(content)
        return true
    }

    fun list(): List<String> {
        return root.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).path }
            .sorted()
            .toList()
    }

    fun delete(fileName: String): Boolean {
        val file = resolve(fileName) ?: return false
        if (fileName in WELL_KNOWN) return false
        return file.delete()
    }

    /**
     * Build a combined context string from well-known files for injection
     * into the system prompt. Only includes files that have been modified
     * from their defaults (to save tokens).
     */
    fun buildPromptContext(): String {
        val sections = mutableListOf<String>()
        WELL_KNOWN.forEach { name ->
            val content = read(name) ?: return@forEach
            val default = DEFAULT_CONTENT[name] ?: ""
            if (content.trim() != default.trim() && content.isNotBlank()) {
                sections.add("## $name\n$content")
            }
        }
        return if (sections.isEmpty()) ""
        else "# Agent Workspace (self-maintained)\n\n${sections.joinToString("\n\n---\n\n")}"
    }

    private fun resolve(fileName: String): File? {
        if (fileName.contains("..") || fileName.startsWith("/")) return null
        return File(root, fileName)
    }
}
