package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.code.ShellPythonHint
import com.orizon.openkiwi.core.tool.*
import com.orizon.openkiwi.service.overlay.TerminalOverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class ShellCommandTool : Tool {
    override val definition = ToolDefinition(
        name = "shell_command",
        description = "Execute a shell command on Android (non-root, sh). No python binary — use code_execution for Python.",
        category = ToolCategory.CODE_EXECUTION.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "command" to ToolParamDef("string", "The shell command to execute", true),
            "timeout_seconds" to ToolParamDef("integer", "Timeout in seconds", false, "30")
        ),
        requiredParams = listOf("command")
    )

    private val blockedCommands = setOf("rm -rf /", "mkfs", "dd if=", "reboot", "shutdown")

    override suspend fun execute(params: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val command = params["command"]?.toString() ?: return@withContext ToolResult(
            toolName = definition.name, success = false, output = "", error = "Missing command"
        )
        val timeout = (params["timeout_seconds"]?.toString()?.toLongOrNull() ?: 30L)
            .coerceIn(1, 120)

        if (blockedCommands.any { command.contains(it) }) {
            return@withContext ToolResult(
                toolName = definition.name, success = false, output = "", error = "Command blocked for safety"
            )
        }
        if (ShellPythonHint.commandInvokesSystemPython(command)) {
            return@withContext ToolResult(
                toolName = definition.name,
                success = false,
                output = "",
                error = ShellPythonHint.USE_CODE_EXECUTION_ZH
            )
        }

        TerminalOverlayService.setCommand(command)
        TerminalOverlayService.setStatus(TerminalOverlayService.ExecutionStatus.RUNNING)

        runCatching {
            val pb = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
            pb.environment()["PATH"] = "/system/bin:/system/xbin:/vendor/bin:/sbin"
            val process = pb.start()

            val completed = process.waitFor(timeout, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                TerminalOverlayService.setStatus(TerminalOverlayService.ExecutionStatus.FAILED)
                TerminalOverlayService.appendOutput("Command timed out after ${timeout}s", isError = true)
                return@runCatching ToolResult(
                    toolName = definition.name, success = false, output = "", error = "Command timed out after ${timeout}s"
                )
            }

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val exitCode = process.exitValue()

            TerminalOverlayService.appendOutput(output.take(2000))
            if (exitCode == 0) {
                TerminalOverlayService.setStatus(TerminalOverlayService.ExecutionStatus.SUCCESS)
            } else {
                TerminalOverlayService.setStatus(TerminalOverlayService.ExecutionStatus.FAILED)
            }

            ToolResult(
                toolName = definition.name,
                success = exitCode == 0,
                output = output.take(10_000),
                error = if (exitCode != 0) "Exit code: $exitCode" else null,
                artifacts = extractArtifacts(output)
            )
        }.getOrElse { e ->
            ToolResult(toolName = definition.name, success = false, output = "", error = e.message)
        }
    }

    private fun extractArtifacts(output: String): List<ToolArtifact> {
        val pathRegex = Regex("""(?m)(?:saved to|written to|created file|output file|file:)\s+([/\w\-. ]+\.\w+)""", RegexOption.IGNORE_CASE)
        return pathRegex.findAll(output)
            .mapNotNull { match ->
                val file = File(match.groupValues[1].trim().trim('"'))
                if (!file.exists() || !file.isFile) return@mapNotNull null
                ToolArtifact(
                    filePath = file.absolutePath,
                    displayName = file.name,
                    sizeBytes = file.length()
                )
            }
            .distinctBy { it.filePath }
            .toList()
    }
}
