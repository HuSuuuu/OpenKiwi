package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.device.SSHClientWrapper
import com.orizon.openkiwi.core.device.SSHConfig
import com.orizon.openkiwi.core.tool.*

class SSHTool : Tool {
    override val definition = ToolDefinition(
        name = "ssh",
        description = "Connect to remote computers via SSH and execute commands",
        category = ToolCategory.DEVICE.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: connect, exec, disconnect, status", true,
                enumValues = listOf("connect", "exec", "disconnect", "status")),
            "host" to ToolParamDef("string", "SSH host address"),
            "port" to ToolParamDef("string", "SSH port", false, "22"),
            "username" to ToolParamDef("string", "SSH username"),
            "password" to ToolParamDef("string", "SSH password"),
            "command" to ToolParamDef("string", "Command to execute"),
            "timeout" to ToolParamDef("string", "Command timeout in seconds", false, "30")
        ),
        requiredParams = listOf("action")
    )

    private val clients = mutableMapOf<String, SSHClientWrapper>()

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return errorResult("Missing action")
        return runCatching {
            when (action) {
                "connect" -> {
                    val host = params["host"]?.toString() ?: return@runCatching errorResult("Missing host")
                    val port = params["port"]?.toString()?.toIntOrNull() ?: 22
                    val user = params["username"]?.toString() ?: return@runCatching errorResult("Missing username")
                    val pass = params["password"]?.toString()
                    val config = SSHConfig(host = host, port = port, username = user, password = pass)
                    val client = SSHClientWrapper()
                    val result = client.connect(config)
                    result.fold(
                        onSuccess = {
                            clients[host] = client
                            ToolResult(definition.name, true, it)
                        },
                        onFailure = { ToolResult(definition.name, false, "", "Connection failed: ${it.message}") }
                    )
                }
                "exec" -> {
                    val host = params["host"]?.toString() ?: clients.keys.firstOrNull() ?: return@runCatching errorResult("No active connection. Specify host or connect first.")
                    val client = clients[host] ?: return@runCatching errorResult("Not connected to $host")
                    val command = params["command"]?.toString() ?: return@runCatching errorResult("Missing command")
                    val timeout = params["timeout"]?.toString()?.toIntOrNull() ?: 30
                    val result = client.executeCommand(command, timeout)
                    result.fold(
                        onSuccess = { r ->
                            val sb = buildString {
                                appendLine("$ $command")
                                if (r.stdout.isNotBlank()) appendLine(r.stdout.take(10000))
                                if (r.stderr.isNotBlank()) appendLine("[stderr] ${r.stderr.take(2000)}")
                                appendLine("[exit: ${r.exitCode}]")
                            }
                            ToolResult(definition.name, r.exitCode == 0, sb)
                        },
                        onFailure = { ToolResult(definition.name, false, "", it.message) }
                    )
                }
                "disconnect" -> {
                    val host = params["host"]?.toString() ?: clients.keys.firstOrNull()
                    if (host != null) {
                        clients[host]?.disconnect()
                        clients.remove(host)
                        ToolResult(definition.name, true, "Disconnected from $host")
                    } else ToolResult(definition.name, true, "No active connections")
                }
                "status" -> {
                    if (clients.isEmpty()) ToolResult(definition.name, true, "No active SSH connections")
                    else {
                        val sb = StringBuilder("SSH Connections:\n")
                        clients.forEach { (h, c) -> sb.appendLine("  $h — ${if (c.isConnected()) "connected" else "disconnected"}") }
                        ToolResult(definition.name, true, sb.toString())
                    }
                }
                else -> errorResult("Unknown action: $action")
            }
        }.getOrElse { ToolResult(definition.name, false, "", it.message) }
    }
    private fun errorResult(msg: String) = ToolResult(definition.name, false, "", msg)
}
