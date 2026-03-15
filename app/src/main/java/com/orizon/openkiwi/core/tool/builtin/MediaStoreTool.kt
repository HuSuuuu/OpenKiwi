package com.orizon.openkiwi.core.tool.builtin

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.orizon.openkiwi.core.tool.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        name = "media_store",
        description = "Query and manage media files (images, videos, audio, documents) on the device",
        category = ToolCategory.FILE.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: query_images, query_videos, query_audio, query_files, stats", true,
                enumValues = listOf("query_images", "query_videos", "query_audio", "query_files", "stats")),
            "query" to ToolParamDef("string", "Search filter for file name"),
            "limit" to ToolParamDef("string", "Max results", false, "20")
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val action = params["action"]?.toString() ?: return@withContext errorResult("Missing action")
        val limit = params["limit"]?.toString()?.toIntOrNull() ?: 20
        val query = params["query"]?.toString()
        runCatching {
            when (action) {
                "query_images" -> queryMediaType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "Images", query, limit)
                "query_videos" -> queryMediaType(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Videos", query, limit)
                "query_audio" -> queryMediaType(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "Audio", query, limit)
                "query_files" -> queryMediaType(MediaStore.Files.getContentUri("external"), "Files", query, limit)
                "stats" -> {
                    val images = countMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    val videos = countMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                    val audio = countMedia(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                    ToolResult(definition.name, true, "Media Stats:\n  Images: $images\n  Videos: $videos\n  Audio: $audio")
                }
                else -> errorResult("Unknown action: $action")
            }
        }.getOrElse { ToolResult(definition.name, false, "", it.message) }
    }

    private fun queryMediaType(uri: Uri, label: String, query: String?, limit: Int): ToolResult {
        val selection = if (query != null) "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?" else null
        val args = if (query != null) arrayOf("%$query%") else null
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns._ID),
            selection, args, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )
        val sb = StringBuilder("$label:\n")
        var count = 0
        cursor?.use {
            while (it.moveToNext() && count < limit) {
                val name = it.getString(0) ?: "unknown"
                val size = it.getLong(1) / 1024
                sb.appendLine("  $name (${size}KB)")
                count++
            }
        }
        if (count == 0) sb.appendLine("  (none found)")
        return ToolResult(definition.name, true, sb.toString())
    }

    private fun countMedia(uri: Uri): Int {
        val cursor = context.contentResolver.query(uri, arrayOf("count(*)"), null, null, null)
        cursor?.use { if (it.moveToFirst()) return it.getInt(0) }
        return 0
    }
    private fun errorResult(msg: String) = ToolResult(definition.name, false, "", msg)
}
