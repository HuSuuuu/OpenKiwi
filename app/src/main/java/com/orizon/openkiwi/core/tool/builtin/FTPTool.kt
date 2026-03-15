package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.tool.*
import com.orizon.openkiwi.network.FTPClient

class FTPTool : Tool {

    override val definition = ToolDefinition(
        name = "ftp",
        description = "FTP file transfer: connect, list, upload, download, mkdir, delete files on FTP servers",
        category = ToolCategory.NETWORK.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: connect, list, download, upload, mkdir, delete, pwd, cd, disconnect",
                required = true, enumValues = listOf("connect", "list", "download", "upload", "mkdir", "delete", "pwd", "cd", "disconnect")),
            "host" to ToolParamDef("string", "FTP server hostname (for connect)"),
            "port" to ToolParamDef("string", "FTP port (default 21)"),
            "username" to ToolParamDef("string", "FTP username (default anonymous)"),
            "password" to ToolParamDef("string", "FTP password"),
            "remote_path" to ToolParamDef("string", "Remote file/directory path"),
            "local_path" to ToolParamDef("string", "Local file path (for upload/download)")
        ),
        requiredParams = listOf("action"),
        returnDescription = "FTP operation result",
        timeoutMs = 60_000
    )

    private var client: FTPClient? = null

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return ToolResult("ftp", false, "", "Missing action")

        return when (action.lowercase()) {
            "connect" -> {
                val host = params["host"]?.toString() ?: return ToolResult("ftp", false, "", "Missing host")
                val port = params["port"]?.toString()?.toIntOrNull() ?: 21
                val username = params["username"]?.toString() ?: "anonymous"
                val password = params["password"]?.toString() ?: ""

                client = FTPClient()
                val config = FTPClient.FTPConfig(host, port, username, password)
                val ok = client!!.connect(config)
                ToolResult("ftp", ok, if (ok) "Connected to $host:$port" else "Connection failed")
            }
            "list" -> {
                val c = client ?: return ToolResult("ftp", false, "", "Not connected")
                val path = params["remote_path"]?.toString() ?: "."
                val entries = c.listFiles(path)
                val output = entries.joinToString("\n") {
                    "${if (it.isDirectory) "d" else "-"} ${it.size.toString().padStart(10)} ${it.modified} ${it.name}"
                }
                ToolResult("ftp", true, output.ifBlank { "(empty directory)" })
            }
            "download" -> {
                val c = client ?: return ToolResult("ftp", false, "", "Not connected")
                val remote = params["remote_path"]?.toString() ?: return ToolResult("ftp", false, "", "Missing remote_path")
                val local = params["local_path"]?.toString() ?: return ToolResult("ftp", false, "", "Missing local_path")
                val ok = c.downloadFile(remote, local)
                ToolResult("ftp", ok, if (ok) "Downloaded: $remote → $local" else "Download failed")
            }
            "upload" -> {
                val c = client ?: return ToolResult("ftp", false, "", "Not connected")
                val local = params["local_path"]?.toString() ?: return ToolResult("ftp", false, "", "Missing local_path")
                val remote = params["remote_path"]?.toString() ?: return ToolResult("ftp", false, "", "Missing remote_path")
                val ok = c.uploadFile(local, remote)
                ToolResult("ftp", ok, if (ok) "Uploaded: $local → $remote" else "Upload failed")
            }
            "mkdir" -> {
                val c = client ?: return ToolResult("ftp", false, "", "Not connected")
                val path = params["remote_path"]?.toString() ?: return ToolResult("ftp", false, "", "Missing path")
                val ok = c.makeDirectory(path)
                ToolResult("ftp", ok, if (ok) "Created: $path" else "mkdir failed")
            }
            "delete" -> {
                val c = client ?: return ToolResult("ftp", false, "", "Not connected")
                val path = params["remote_path"]?.toString() ?: return ToolResult("ftp", false, "", "Missing path")
                val ok = c.deleteFile(path)
                ToolResult("ftp", ok, if (ok) "Deleted: $path" else "Delete failed")
            }
            "pwd" -> {
                val c = client ?: return ToolResult("ftp", false, "", "Not connected")
                ToolResult("ftp", true, c.getCurrentDirectory())
            }
            "cd" -> {
                val c = client ?: return ToolResult("ftp", false, "", "Not connected")
                val path = params["remote_path"]?.toString() ?: return ToolResult("ftp", false, "", "Missing path")
                val ok = c.changeDirectory(path)
                ToolResult("ftp", ok, if (ok) "Changed to: $path" else "cd failed")
            }
            "disconnect" -> {
                client?.disconnect()
                client = null
                ToolResult("ftp", true, "Disconnected")
            }
            else -> ToolResult("ftp", false, "", "Unknown action: $action")
        }
    }
}
