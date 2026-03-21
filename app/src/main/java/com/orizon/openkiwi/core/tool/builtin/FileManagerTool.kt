package com.orizon.openkiwi.core.tool.builtin

import android.content.Context
import android.os.Environment
import com.orizon.openkiwi.core.tool.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileManagerTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        name = "file_manager",
        description = "Read, write, list, and manage files on the device storage",
        category = ToolCategory.FILE.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action to perform", true,
                enumValues = listOf("read", "write", "list", "delete", "exists", "mkdir")),
            "path" to ToolParamDef("string", "File or directory path (relative to app storage or absolute)", true),
            "content" to ToolParamDef("string", "Content to write (for write action)")
        ),
        requiredParams = listOf("action", "path")
    )

    private fun resolveFile(path: String): File {
        val file = File(path)
        if (file.isAbsolute) return file
        return File(context.filesDir, path)
    }

    override suspend fun execute(params: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val action = params["action"]?.toString() ?: return@withContext ToolResult(
            toolName = definition.name, success = false, output = "", error = "Missing action"
        )
        val path = params["path"]?.toString() ?: return@withContext ToolResult(
            toolName = definition.name, success = false, output = "", error = "Missing path"
        )
        val file = resolveFile(path)

        runCatching {
            when (action) {
                "read" -> {
                    if (!file.exists()) return@runCatching ToolResult(
                        toolName = definition.name, success = false, output = "", error = "File not found: $path"
                    )
                    val content = file.readText().take(50_000)
                    ToolResult(toolName = definition.name, success = true, output = content)
                }
                "write" -> {
                    val content = params["content"]?.toString() ?: return@runCatching ToolResult(
                        toolName = definition.name, success = false, output = "", error = "Missing content"
                    )
                    file.parentFile?.mkdirs()
                    file.writeText(content)
                    ToolResult(
                        toolName = definition.name,
                        success = true,
                        output = "Written ${content.length} chars to $path",
                        artifacts = listOf(
                            ToolArtifact(
                                filePath = file.absolutePath,
                                displayName = file.name,
                                sizeBytes = file.length()
                            )
                        )
                    )
                }
                "list" -> {
                    if (!file.exists() || !file.isDirectory) return@runCatching ToolResult(
                        toolName = definition.name, success = false, output = "", error = "Not a directory: $path"
                    )
                    val entries = file.listFiles()?.map { f ->
                        val type = if (f.isDirectory) "DIR" else "FILE"
                        val size = if (f.isFile) " (${f.length()} bytes)" else ""
                        "[$type] ${f.name}$size"
                    }?.sorted() ?: emptyList()
                    ToolResult(toolName = definition.name, success = true,
                        output = "Contents of $path (${entries.size} items):\n${entries.joinToString("\n")}")
                }
                "delete" -> {
                    if (!file.exists()) return@runCatching ToolResult(
                        toolName = definition.name, success = false, output = "", error = "File not found: $path"
                    )
                    val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                    ToolResult(toolName = definition.name, success = deleted,
                        output = if (deleted) "Deleted: $path" else "",
                        error = if (!deleted) "Failed to delete: $path" else null)
                }
                "exists" -> ToolResult(
                    toolName = definition.name, success = true,
                    output = if (file.exists()) "EXISTS (${if (file.isDirectory) "directory" else "file, ${file.length()} bytes"})" else "NOT_FOUND"
                )
                "mkdir" -> {
                    val created = file.mkdirs()
                    ToolResult(toolName = definition.name, success = true,
                        output = if (created) "Created directory: $path" else "Directory already exists: $path")
                }
                else -> ToolResult(toolName = definition.name, success = false, output = "", error = "Unknown action: $action")
            }
        }.getOrElse { e ->
            ToolResult(toolName = definition.name, success = false, output = "", error = e.message)
        }
    }
}
