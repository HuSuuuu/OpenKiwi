package com.orizon.openkiwi.core.openclaw

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry for OpenClaw-compatible skills loaded natively in OpenKiwi.
 *
 * Responsibilities:
 *   1. Store and manage loaded OpenClaw skills (from assets, files, URLs).
 *   2. Map OpenClaw tool names referenced in skill instructions to OpenKiwi equivalents.
 *   3. Provide skill instructions for injection into the agent's system prompt.
 *   4. Support searching/filtering skills by keyword, category, or context.
 */
class OpenClawSkillRegistry(private val context: Context) {

    companion object {
        private const val TAG = "OCSkillRegistry"
        private const val PREFS_KEY = "openclaw_skills_json"
        private const val SKILLS_DIR = "openclaw_skills_imported"

        /**
         * Maps OpenClaw tool names to OpenKiwi equivalents.
         * Skills that reference these tools will have their instructions automatically
         * rewritten to use the correct OpenKiwi tool name.
         */
        val TOOL_NAME_MAP = mapOf(
            "web_search" to "web_search",
            "tavily_search" to "web_search",
            "tavily_extract" to "web_fetch",
            "web_fetch" to "web_fetch",
            "read" to "file_manager",
            "write" to "file_manager",
            "exec" to "shell_command",
            "message" to "notification",
            "sessions_spawn" to "sub_agent",
            "cron" to "scheduled_task",
            "feishu_doc" to "feishu",
            "feishu_wiki" to "feishu",
            "feishu_drive" to "feishu",
            "feishu_perm" to "feishu",
            "canvas" to "gui_agent",
            "diffs" to "code_execution",
            "qqbot_remind" to "reminder"
        )
    }

    private val skills = ConcurrentHashMap<String, OpenClawSkill>()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val _skillsFlow = MutableStateFlow<List<OpenClawSkill>>(emptyList())
    val skillsFlow: StateFlow<List<OpenClawSkill>> = _skillsFlow.asStateFlow()

    private val importDir: File by lazy {
        File(context.filesDir, SKILLS_DIR).also { it.mkdirs() }
    }

    /**
     * Initialize: load bundled skills + persisted imported skills.
     */
    fun initialize() {
        val bundled = OpenClawSkillLoader.loadFromAssets(context)
        for (skill in bundled) {
            skills[skill.id] = skill
        }
        Log.i(TAG, "Loaded ${bundled.size} bundled OpenClaw skills")

        loadPersistedSkills()

        val imported = OpenClawSkillLoader.scanDirectory(importDir)
        for (skill in imported) {
            skills[skill.id] = skill.copy(source = "imported:${skill.id}")
        }
        if (imported.isNotEmpty()) {
            Log.i(TAG, "Loaded ${imported.size} imported OpenClaw skills from disk")
        }

        updateFlow()
    }

    /**
     * Import a SKILL.md picked via Storage Access Framework (file picker).
     */
    fun importFromUri(uri: Uri): OpenClawSkill? {
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        } catch (e: Exception) {
            Log.w(TAG, "importFromUri read failed: $uri", e)
            null
        } ?: return null
        return importFromText(text, source = uri.toString())
    }

    /**
     * Import a SKILL.md from raw text content.
     */
    fun importFromText(content: String, source: String = "text"): OpenClawSkill? {
        val skill = OpenClawSkillLoader.parse(content, source = source) ?: return null
        skills[skill.id] = skill
        persistSkill(skill)
        updateFlow()
        return skill
    }

    /**
     * Import a SKILL.md from a file path.
     */
    fun importFromFile(path: String): OpenClawSkill? {
        val file = File(path)
        val skill = OpenClawSkillLoader.loadFromFile(file) ?: return null
        skills[skill.id] = skill
        persistSkill(skill)
        updateFlow()
        return skill
    }

    /**
     * Scan a directory for SKILL.md files and import all found skills.
     */
    fun importFromDirectory(dirPath: String): List<OpenClawSkill> {
        val dir = File(dirPath)
        val found = OpenClawSkillLoader.scanDirectory(dir)
        for (skill in found) {
            skills[skill.id] = skill
            persistSkill(skill)
        }
        updateFlow()
        return found
    }

    fun getSkill(id: String): OpenClawSkill? = skills[id]

    fun getAllSkills(): List<OpenClawSkill> = skills.values.toList()

    fun getEnabledSkills(): List<OpenClawSkill> = skills.values.filter { it.isEnabled }

    fun getModelVisibleSkills(): List<OpenClawSkill> =
        skills.values.filter { it.isEnabled && !it.disableModelInvocation }

    /**
     * Search skills by keyword across name, description, and body.
     */
    fun search(query: String): List<OpenClawSkill> {
        val q = query.lowercase()
        return skills.values.filter { skill ->
            skill.name.lowercase().contains(q) ||
            skill.description.lowercase().contains(q) ||
            skill.body.lowercase().contains(q)
        }
    }

    /**
     * Get the full instructions for a skill, with OpenClaw tool names
     * rewritten to their OpenKiwi equivalents.
     */
    fun getRewrittenInstructions(skillId: String): String? {
        val skill = skills[skillId] ?: return null
        return rewriteToolReferences(skill.body)
    }

    /**
     * Build a skill catalog snippet for injection into the system prompt.
     * Lists all enabled, model-visible skills with name + description.
     */
    fun buildSkillCatalog(): String {
        val visible = getModelVisibleSkills()
        if (visible.isEmpty()) return ""

        return buildString {
            appendLine("\n## Available OpenClaw Skills")
            appendLine("When user intent matches a skill below, read the full skill instructions first using `openclaw_skills action=read id=<skill_id>`.\n")
            for (skill in visible) {
                val emoji = skill.emoji?.let { "$it " } ?: ""
                appendLine("- **${emoji}${skill.name}** (id: `${skill.id}`): ${skill.description}")
            }
        }
    }

    /**
     * Toggle skill enabled state.
     */
    fun setEnabled(skillId: String, enabled: Boolean) {
        val skill = skills[skillId] ?: return
        skills[skillId] = skill.copy(isEnabled = enabled)
        updateFlow()
    }

    /**
     * Remove a skill (only imported ones, not bundled).
     */
    fun remove(skillId: String): Boolean {
        val skill = skills[skillId] ?: return false
        if (skill.source.startsWith("bundled:")) return false
        skills.remove(skillId)
        File(importDir, "$skillId/SKILL.md").parentFile?.deleteRecursively()
        updateFlow()
        return true
    }

    /**
     * Rewrite OpenClaw tool name references in markdown to OpenKiwi equivalents.
     * Handles both inline code (`tool_name`) and plain text references.
     */
    private fun rewriteToolReferences(body: String): String {
        var result = body
        for ((ocTool, kiwiTool) in TOOL_NAME_MAP) {
            if (ocTool == kiwiTool) continue
            result = result.replace("`$ocTool`", "`$kiwiTool` _(openclaw: $ocTool)_")
        }
        return result
    }

    /**
     * Persist a single skill to the import directory as SKILL.md.
     */
    private fun persistSkill(skill: OpenClawSkill) {
        try {
            val skillDir = File(importDir, skill.id)
            skillDir.mkdirs()
            val skillFile = File(skillDir, "SKILL.md")
            val content = buildString {
                appendLine("---")
                appendLine("name: ${skill.name}")
                appendLine("description: ${skill.description}")
                if (!skill.userInvocable) appendLine("user-invocable: false")
                if (skill.disableModelInvocation) appendLine("disable-model-invocation: true")
                skill.commandDispatch?.let { appendLine("command-dispatch: $it") }
                skill.commandTool?.let { appendLine("command-tool: $it") }
                val meta = buildMetadataLine(skill)
                if (meta != null) appendLine("metadata: $meta")
                appendLine("---")
                appendLine()
                append(skill.body)
            }
            skillFile.writeText(content, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist skill ${skill.id}", e)
        }
    }

    private fun buildMetadataLine(skill: OpenClawSkill): String? {
        val parts = mutableListOf<String>()
        skill.emoji?.let { parts.add("\"emoji\": \"$it\"") }
        skill.homepage?.let { parts.add("\"homepage\": \"$it\"") }
        if (parts.isEmpty()) return null
        return "{ \"openclaw\": { ${parts.joinToString(", ")} } }"
    }

    private fun loadPersistedSkills() {
        val imported = OpenClawSkillLoader.scanDirectory(importDir)
        for (skill in imported) {
            skills[skill.id] = skill.copy(source = "imported:${skill.id}")
        }
    }

    private fun updateFlow() {
        _skillsFlow.value = skills.values.toList().sortedBy { it.name }
    }
}
