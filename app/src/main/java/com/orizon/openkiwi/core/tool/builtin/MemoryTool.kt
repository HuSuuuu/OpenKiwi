package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.memory.MemoryManager
import com.orizon.openkiwi.core.memory.MemoryType
import com.orizon.openkiwi.core.tool.*

class MemoryTool(private val memoryManager: MemoryManager) : Tool {
    override val definition = ToolDefinition(
        name = "memory",
        description = "Read or write long-term memory. Use 'store' to save important facts, user preferences, or key decisions. Use 'search' to recall previously stored information. Use 'delete' to remove a memory entry by id.",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.NORMAL.name,
        parameters = mapOf(
            "action" to ToolParamDef(
                type = "string",
                description = "Action to perform",
                required = true,
                enumValues = listOf("store", "search", "delete")
            ),
            "key" to ToolParamDef(
                type = "string",
                description = "Short label for the memory (used when storing)",
                required = false
            ),
            "content" to ToolParamDef(
                type = "string",
                description = "Content to store, or query string to search",
                required = false
            ),
            "category" to ToolParamDef(
                type = "string",
                description = "Category tag (e.g. preference, fact, decision)",
                required = false
            ),
            "importance" to ToolParamDef(
                type = "string",
                description = "Importance from 0.0 to 1.0 (default 0.5)",
                required = false
            ),
            "memory_id" to ToolParamDef(
                type = "string",
                description = "Memory ID to delete (used with action=delete)",
                required = false
            )
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "Missing action"
        )

        return when (action) {
            "store" -> {
                val key = params["key"]?.toString()
                val content = params["content"]?.toString()
                if (key.isNullOrBlank() || content.isNullOrBlank()) {
                    return ToolResult(definition.name, false, "", error = "store requires 'key' and 'content'")
                }
                val category = params["category"]?.toString() ?: ""
                val importance = params["importance"]?.toString()?.toFloatOrNull() ?: 0.5f

                val id = memoryManager.storeMemory(
                    type = MemoryType.LONG_TERM,
                    key = key,
                    content = content,
                    category = category,
                    importance = importance.coerceIn(0f, 1f)
                )
                ToolResult(definition.name, true, "Memory stored (id=$id): [$key] $content")
            }

            "search" -> {
                val query = params["content"]?.toString()
                if (query.isNullOrBlank()) {
                    return ToolResult(definition.name, false, "", error = "search requires 'content' as query")
                }
                val results = memoryManager.searchMemories(query, limit = 10)
                if (results.isEmpty()) {
                    ToolResult(definition.name, true, "No memories found for: $query")
                } else {
                    val text = results.joinToString("\n") { entry ->
                        "[id=${entry.id}] (${entry.category.ifBlank { entry.type.name }}) ${entry.key}: ${entry.content}"
                    }
                    ToolResult(definition.name, true, "Found ${results.size} memories:\n$text")
                }
            }

            "delete" -> {
                val id = params["memory_id"]?.toString()?.toLongOrNull()
                    ?: return ToolResult(definition.name, false, "", error = "delete requires 'memory_id'")
                memoryManager.deleteMemory(id)
                ToolResult(definition.name, true, "Memory id=$id deleted")
            }

            else -> ToolResult(definition.name, false, "", error = "Unknown action: $action. Use store/search/delete.")
        }
    }
}
