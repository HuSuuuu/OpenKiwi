package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.code.TerminalSessionManager
import com.orizon.openkiwi.core.tool.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout
import java.io.File

class TerminalTool(
    private val sessionManager: TerminalSessionManager
) : Tool {

    override val supportsStreaming: Boolean get() = true

    override val definition = ToolDefinition(
        name = "terminal",
        description = "Persistent terminal with session/cwd memory. Actions: execute, create_session, list_sessions, set_cwd. Optional root.",
        category = ToolCategory.CODE_EXECUTION.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "execute, create_session, list_sessions, set_cwd",
                required = true, enumValues = listOf("execute", "create_session", "list_sessions", "set_cwd")),
            "command" to ToolParamDef("string", "Shell command (for execute)"),
            "session_id" to ToolParamDef("string", "Session ID (auto-uses current if omitted)"),
            "path" to ToolParamDef("string", "Directory path (for set_cwd)"),
            "name" to ToolParamDef("string", "Session name (for create_session)"),
            "root" to ToolParamDef("string", "Use root (true/false, for create_session)"),
            "timeout_seconds" to ToolParamDef("integer", "Timeout in seconds (default 60, max 300)")
        ),
        requiredParams = listOf("action"),
        timeoutMs = 300_000
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString()?.trim()?.lowercase()
            ?: return ToolResult("terminal", false, "", "Missing action")

        return when (action) {
            "create_session" -> createSession(params)
            "list_sessions" -> listSessions()
            "set_cwd" -> setCwd(params)
            "execute" -> executeCommand(params)
            else -> ToolResult("terminal", false, "", "Unknown action: $action")
        }
    }

    private fun createSession(params: Map<String, Any?>): ToolResult {
        val name = params["name"]?.toString() ?: "Terminal"
        val useRoot = params["root"]?.toString()?.lowercase() == "true"
        val id = sessionManager.createSession(name, useRoot)
        val session = sessionManager.getSession(id)
        return ToolResult("terminal", true,
            "Session created: id=$id, name=$name, root=$useRoot, cwd=${session?.workingDirectory}")
    }

    private fun listSessions(): ToolResult {
        val sessions = sessionManager.listSessions()
        if (sessions.isEmpty()) return ToolResult("terminal", true, "No active sessions. Use action=create_session to create one.")
        val list = sessions.joinToString("\n") { s ->
            "- ${s.id} | ${s.name} | cwd=${s.workingDirectory} | root=${s.isRoot}"
        }
        return ToolResult("terminal", true, "Active sessions (${sessions.size}):\n$list")
    }

    private fun setCwd(params: Map<String, Any?>): ToolResult {
        val path = params["path"]?.toString()
            ?: return ToolResult("terminal", false, "", "Missing path")
        val sid = resolveSessionId(params)
            ?: return ToolResult("terminal", false, "", "No session. Use action=create_session first.")
        val dir = java.io.File(path)
        if (!dir.exists() || !dir.isDirectory) {
            return ToolResult("terminal", false, "", "Directory does not exist: $path")
        }
        val session = sessionManager.getSession(sid) ?: return ToolResult("terminal", false, "", "Session not found")
        val updated = session.copy(workingDirectory = dir.canonicalPath)
        sessionManager.removeSession(sid)
        sessionManager.createSession(updated.name, updated.isRoot)
        return ToolResult("terminal", true, "cwd set to ${dir.canonicalPath}")
    }

    private suspend fun executeCommand(params: Map<String, Any?>): ToolResult {
        val command = params["command"]?.toString()?.trim()
            ?: return ToolResult("terminal", false, "", "Missing command")

        var sid = resolveSessionId(params)
        if (sid == null) {
            sid = sessionManager.createSession()
        }

        val timeout = params["timeout_seconds"]?.toString()?.toLongOrNull()?.coerceIn(1, 300) ?: 60

        val startTime = System.currentTimeMillis()
        val result = withTimeout(timeout * 1000) {
            sessionManager.executeInSession(sid, command)
        }
        val session = sessionManager.getSession(sid)

        val output = buildString {
            append("$ ").append(command).append("\n")
            if (result.stdout.isNotBlank()) {
                val stdout = result.stdout.take(100_000)
                append(stdout)
                if (!stdout.endsWith("\n")) append("\n")
            }
            if (result.stderr.isNotBlank()) {
                append("--- stderr ---\n")
                append(result.stderr.take(20_000))
                if (!result.stderr.endsWith("\n")) append("\n")
            }
            append("exit=${result.exitCode} time=${result.executionTimeMs}ms cwd=${session?.workingDirectory ?: "?"}")
        }

        return ToolResult(
            toolName = "terminal",
            success = result.exitCode == 0,
            output = output,
            error = if (result.exitCode != 0) "Exit code: ${result.exitCode}" else null,
            executionTimeMs = result.executionTimeMs
        )
    }

    override suspend fun executeStreaming(params: Map<String, Any?>): Flow<String> {
        val action = params["action"]?.toString()?.trim()?.lowercase()
        if (action != "execute") {
            val result = execute(params)
            return kotlinx.coroutines.flow.flowOf(result.output)
        }
        val command = params["command"]?.toString()?.trim()
            ?: return kotlinx.coroutines.flow.flowOf("Error: Missing command")

        var sid = resolveSessionId(params)
        if (sid == null) sid = sessionManager.createSession()

        val session = sessionManager.getSession(sid)
            ?: return kotlinx.coroutines.flow.flowOf("Error: Session not found")

        val timeout = params["timeout_seconds"]?.toString()?.toLongOrNull()?.coerceIn(1, 300) ?: 60
        val fullCommand = "cd ${session.workingDirectory} && $command"

        return flow {
            emit("$ $command\n")
            val process = if (session.isRoot) {
                ProcessBuilder("su", "-c", fullCommand)
            } else {
                ProcessBuilder("sh", "-c", fullCommand)
            }
                .directory(File(session.workingDirectory))
                .redirectErrorStream(false)
                .start()

            val stdoutReader = process.inputStream.bufferedReader()
            val stderrReader = process.errorStream.bufferedReader()

            var totalChars = 0
            val maxChars = 100_000
            var line = stdoutReader.readLine()
            while (line != null) {
                if (totalChars < maxChars) {
                    emit(line + "\n")
                    totalChars += line.length + 1
                }
                line = stdoutReader.readLine()
            }

            val stderr = stderrReader.readText()
            if (stderr.isNotBlank()) {
                emit("--- stderr ---\n")
                emit(stderr.take(20_000))
            }

            val exitCode = process.waitFor()
            emit("\nexit=$exitCode cwd=${session.workingDirectory}")
        }.flowOn(Dispatchers.IO)
    }

    private fun resolveSessionId(params: Map<String, Any?>): String? {
        val explicit = params["session_id"]?.toString()?.trim()?.takeIf { it.isNotBlank() }
        if (explicit != null && sessionManager.getSession(explicit) != null) return explicit
        return sessionManager.currentSessionId.value
    }
}
