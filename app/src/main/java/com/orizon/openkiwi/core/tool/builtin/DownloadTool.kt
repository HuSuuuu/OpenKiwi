package com.orizon.openkiwi.core.tool.builtin

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.orizon.openkiwi.core.tool.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class DownloadTool(private val context: Context, private val httpClient: OkHttpClient) : Tool {
    override val definition = ToolDefinition(
        name = "download",
        description = "Download files from URLs and upload files to servers",
        category = ToolCategory.NETWORK.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: download, upload, download_status", true,
                enumValues = listOf("download", "upload", "download_status")),
            "url" to ToolParamDef("string", "URL for download/upload"),
            "file_path" to ToolParamDef("string", "Local file path for upload"),
            "filename" to ToolParamDef("string", "Filename for downloaded file"),
            "download_id" to ToolParamDef("string", "Download ID for status check")
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val action = params["action"]?.toString() ?: return@withContext errorResult("Missing action")
        runCatching {
            when (action) {
                "download" -> {
                    val url = params["url"]?.toString() ?: return@runCatching errorResult("Missing url")
                    val filename = params["filename"]?.toString() ?: url.substringAfterLast('/').take(50).ifBlank { "download_${System.currentTimeMillis()}" }
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val request = DownloadManager.Request(Uri.parse(url)).apply {
                        setTitle("OpenKiwi: $filename")
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    }
                    val id = dm.enqueue(request)
                    ToolResult(definition.name, true, "Download started: $filename (id=$id)\nSaved to: Downloads/$filename")
                }
                "upload" -> {
                    val url = params["url"]?.toString() ?: return@runCatching errorResult("Missing url")
                    val filePath = params["file_path"]?.toString() ?: return@runCatching errorResult("Missing file_path")
                    val file = File(filePath)
                    if (!file.exists()) return@runCatching errorResult("File not found: $filePath")
                    val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("file", file.name, file.asRequestBody("application/octet-stream".toMediaType()))
                        .build()
                    val request = Request.Builder().url(url).post(body).build()
                    val response = httpClient.newCall(request).execute()
                    val respBody = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        ToolResult(definition.name, true, "Uploaded ${file.name} (${file.length() / 1024}KB)\nResponse: ${respBody.take(500)}")
                    } else {
                        ToolResult(definition.name, false, "", "Upload failed: ${response.code} $respBody")
                    }
                }
                "download_status" -> {
                    val id = params["download_id"]?.toString()?.toLongOrNull() ?: return@runCatching errorResult("Missing download_id")
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val query = DownloadManager.Query().setFilterById(id)
                    val cursor = dm.query(query)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            val statusStr = when (status) {
                                DownloadManager.STATUS_PENDING -> "Pending"
                                DownloadManager.STATUS_RUNNING -> "Running"
                                DownloadManager.STATUS_PAUSED -> "Paused"
                                DownloadManager.STATUS_SUCCESSFUL -> "Completed"
                                DownloadManager.STATUS_FAILED -> "Failed"
                                else -> "Unknown"
                            }
                            val pct = if (total > 0) (downloaded * 100 / total) else 0
                            return@withContext ToolResult(definition.name, true, "Download #$id: $statusStr ($pct%, ${downloaded/1024}/${total/1024}KB)")
                        }
                    }
                    ToolResult(definition.name, false, "", "Download #$id not found")
                }
                else -> errorResult("Unknown action: $action")
            }
        }.getOrElse { ToolResult(definition.name, false, "", it.message) }
    }
    private fun errorResult(msg: String) = ToolResult(definition.name, false, "", msg)
}
