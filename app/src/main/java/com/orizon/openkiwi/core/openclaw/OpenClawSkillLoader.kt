package com.orizon.openkiwi.core.openclaw

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File

/**
 * Parsed representation of an OpenClaw SKILL.md file.
 */
@Serializable
data class OpenClawSkill(
    val id: String,
    val name: String,
    val description: String,
    val body: String,
    val source: String = "unknown",
    val userInvocable: Boolean = true,
    val disableModelInvocation: Boolean = false,
    val commandDispatch: String? = null,
    val commandTool: String? = null,
    val emoji: String? = null,
    val homepage: String? = null,
    val isEnabled: Boolean = true,
    val frontmatter: Map<String, String> = emptyMap()
)

/**
 * Parses OpenClaw SKILL.md files into [OpenClawSkill] objects.
 *
 * Compatible with the OpenClaw skill format:
 *   - YAML frontmatter delimited by `---`
 *   - Required: `description` (non-empty after trim)
 *   - `name` falls back to directory name or filename
 *   - Markdown body after the frontmatter is the skill instructions
 */
object OpenClawSkillLoader {
    private const val TAG = "OCSkillLoader"
    private const val MAX_FILE_BYTES = 256 * 1024

    /**
     * Parse a single SKILL.md string content.
     */
    fun parse(content: String, fallbackName: String = "unknown", source: String = "file"): OpenClawSkill? {
        if (content.isBlank()) return null

        val (frontmatter, body) = extractFrontmatter(content)
        val description = frontmatter["description"]?.trim()
        if (description.isNullOrBlank()) {
            Log.d(TAG, "Skipping skill: no description ($fallbackName)")
            return null
        }

        val name = frontmatter["name"]?.trim()?.ifBlank { null } ?: fallbackName

        val metadataJson = frontmatter["metadata"]
        var emoji: String? = null
        var homepage: String? = null
        if (metadataJson != null) {
            val parsed = parseMetadata(metadataJson)
            emoji = parsed.first
            homepage = parsed.second
        }

        val userInvocable = frontmatter["user-invocable"]?.trim()?.lowercase() != "false"
        val disableModel = frontmatter["disable-model-invocation"]?.trim()?.lowercase() == "true"
        val commandDispatch = frontmatter["command-dispatch"]?.trim()
            ?: frontmatter["command_dispatch"]?.trim()
        val commandTool = frontmatter["command-tool"]?.trim()
            ?: frontmatter["command_tool"]?.trim()

        val id = name.lowercase().replace(Regex("[^a-z0-9]"), "_").take(64)

        return OpenClawSkill(
            id = id,
            name = name,
            description = description,
            body = body.trim(),
            source = source,
            userInvocable = userInvocable,
            disableModelInvocation = disableModel,
            commandDispatch = commandDispatch,
            commandTool = commandTool,
            emoji = emoji,
            homepage = homepage,
            frontmatter = frontmatter
        )
    }

    /**
     * Load a SKILL.md from a file path.
     */
    fun loadFromFile(file: File): OpenClawSkill? {
        if (!file.exists() || !file.isFile) return null
        if (file.length() > MAX_FILE_BYTES) {
            Log.w(TAG, "Skill file too large: ${file.absolutePath} (${file.length()} bytes)")
            return null
        }
        val content = file.readText(Charsets.UTF_8)
        val dirName = file.parentFile?.name ?: file.nameWithoutExtension
        return parse(content, fallbackName = dirName, source = "file:${file.absolutePath}")
    }

    /**
     * Scan a directory recursively for SKILL.md files.
     * Follows the OpenClaw convention:
     *   - If rootDir/SKILL.md exists, return just that one skill
     *   - Otherwise, check each subdirectory for SKILL.md
     */
    fun scanDirectory(dir: File, maxDepth: Int = 3): List<OpenClawSkill> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        val rootSkill = File(dir, "SKILL.md")
        if (rootSkill.exists()) {
            val skill = loadFromFile(rootSkill)
            return if (skill != null) listOf(skill) else emptyList()
        }

        val skills = mutableListOf<OpenClawSkill>()
        scanRecursive(dir, skills, 0, maxDepth)
        return skills
    }

    private fun scanRecursive(dir: File, results: MutableList<OpenClawSkill>, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (!child.isDirectory) continue
            if (child.name.startsWith(".") || child.name == "node_modules") continue

            val skillFile = File(child, "SKILL.md")
            if (skillFile.exists()) {
                loadFromFile(skillFile)?.let { results.add(it) }
            } else {
                scanRecursive(child, results, depth + 1, maxDepth)
            }
        }
    }

    /**
     * Load all bundled OpenClaw skills from Android assets.
     */
    fun loadFromAssets(context: Context, assetsDir: String = "openclaw_skills"): List<OpenClawSkill> {
        val skills = mutableListOf<OpenClawSkill>()
        try {
            val dirs = context.assets.list(assetsDir) ?: return emptyList()
            for (dirName in dirs) {
                val skillPath = "$assetsDir/$dirName/SKILL.md"
                try {
                    val content = context.assets.open(skillPath).bufferedReader().use { it.readText() }
                    parse(content, fallbackName = dirName, source = "bundled:$dirName")?.let {
                        skills.add(it)
                    }
                } catch (_: Exception) {
                    val subDirs = context.assets.list("$assetsDir/$dirName") ?: continue
                    for (subDir in subDirs) {
                        val subPath = "$assetsDir/$dirName/$subDir/SKILL.md"
                        try {
                            val content = context.assets.open(subPath).bufferedReader().use { it.readText() }
                            parse(content, fallbackName = subDir, source = "bundled:$dirName/$subDir")?.let {
                                skills.add(it)
                            }
                        } catch (_: Exception) { }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load bundled skills", e)
        }
        return skills
    }

    /**
     * Extract YAML frontmatter and markdown body from a SKILL.md string.
     */
    internal fun extractFrontmatter(content: String): Pair<Map<String, String>, String> {
        val trimmed = content.trimStart()
        if (!trimmed.startsWith("---")) {
            return emptyMap<String, String>() to content
        }

        val afterFirst = trimmed.substring(3)
        val endIdx = afterFirst.indexOf("\n---")
        if (endIdx < 0) {
            return emptyMap<String, String>() to content
        }

        val frontmatterBlock = afterFirst.substring(0, endIdx).trim()
        val body = afterFirst.substring(endIdx + 4)

        return parseFrontmatterBlock(frontmatterBlock) to body
    }

    /**
     * Parse a YAML-like frontmatter block into key-value pairs.
     * Handles multiline values (indented continuation or YAML `|` scalar).
     */
    private fun parseFrontmatterBlock(block: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val lines = block.lines()
        var currentKey: String? = null
        val currentValue = StringBuilder()

        for (line in lines) {
            val keyMatch = Regex("^([a-zA-Z_][a-zA-Z0-9_-]*)\\s*:\\s*(.*)$").matchEntire(line)
            if (keyMatch != null && !line.startsWith(" ") && !line.startsWith("\t")) {
                if (currentKey != null) {
                    result[currentKey] = currentValue.toString().trim()
                }
                currentKey = keyMatch.groupValues[1]
                val rawVal = keyMatch.groupValues[2].trim()
                currentValue.clear()
                if (rawVal == "|" || rawVal == ">") {
                    // Block scalar indicator: value on subsequent indented lines
                } else {
                    currentValue.append(rawVal)
                }
            } else if (currentKey != null && (line.startsWith("  ") || line.startsWith("\t") || line.isBlank())) {
                if (currentValue.isNotEmpty()) currentValue.append("\n")
                currentValue.append(line.trimStart())
            }
        }
        if (currentKey != null) {
            result[currentKey] = currentValue.toString().trim()
        }
        return result
    }

    /**
     * Extract emoji and homepage from metadata JSON string.
     * Handles both `{ "openclaw": { "emoji": "🔍" } }` and flat `{ "emoji": "🔍" }`.
     */
    private fun parseMetadata(metadataStr: String): Pair<String?, String?> {
        return try {
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val obj = json.parseToJsonElement(metadataStr)
            val root = if (obj is kotlinx.serialization.json.JsonObject) obj else return null to null

            val ocBlock = root["openclaw"] as? kotlinx.serialization.json.JsonObject ?: root
            val emoji = ocBlock["emoji"]?.let {
                (it as? kotlinx.serialization.json.JsonPrimitive)?.content
            }
            val homepage = ocBlock["homepage"]?.let {
                (it as? kotlinx.serialization.json.JsonPrimitive)?.content
            }
            emoji to homepage
        } catch (_: Exception) {
            null to null
        }
    }
}
