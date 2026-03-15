package com.orizon.openkiwi.core.tool.builtin

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.orizon.openkiwi.core.tool.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CameraTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        name = "camera",
        description = "Get recent photos/videos from the device camera roll",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: recent_photos, recent_videos, photo_info", true,
                enumValues = listOf("recent_photos", "recent_videos", "photo_info")),
            "limit" to ToolParamDef("string", "Max items to return", false, "10"),
            "uri" to ToolParamDef("string", "Photo URI for info action")
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val action = params["action"]?.toString() ?: return@withContext errorResult("Missing action")
        val limit = params["limit"]?.toString()?.toIntOrNull() ?: 10
        runCatching {
            when (action) {
                "recent_photos" -> {
                    val photos = queryMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, limit)
                    ToolResult(definition.name, true, "Recent photos (${ photos.size}):\n${photos.joinToString("\n")}")
                }
                "recent_videos" -> {
                    val videos = queryMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, limit)
                    ToolResult(definition.name, true, "Recent videos (${videos.size}):\n${videos.joinToString("\n")}")
                }
                "photo_info" -> {
                    val uriStr = params["uri"]?.toString() ?: return@runCatching errorResult("Missing uri")
                    val cursor = context.contentResolver.query(
                        Uri.parse(uriStr),
                        arrayOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE,
                            MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT,
                            MediaStore.Images.Media.DATE_TAKEN),
                        null, null, null
                    )
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val info = buildString {
                                appendLine("Name: ${it.getString(0)}")
                                appendLine("Size: ${it.getLong(1) / 1024}KB")
                                appendLine("Dimensions: ${it.getInt(2)}x${it.getInt(3)}")
                                appendLine("Taken: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it.getLong(4)))}")
                            }
                            return@withContext ToolResult(definition.name, true, info)
                        }
                    }
                    ToolResult(definition.name, false, "", "Photo not found")
                }
                else -> errorResult("Unknown action: $action")
            }
        }.getOrElse { ToolResult(definition.name, false, "", it.message) }
    }

    private fun queryMedia(uri: Uri, limit: Int): List<String> {
        val results = mutableListOf<String>()
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns._ID),
            null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )
        cursor?.use {
            var count = 0
            while (it.moveToNext() && count < limit) {
                val name = it.getString(0)
                val size = it.getLong(1) / 1024
                val id = it.getLong(3)
                results.add("$name (${size}KB) [id=$id]")
                count++
            }
        }
        return results
    }
    private fun errorResult(msg: String) = ToolResult(definition.name, false, "", msg)
}
