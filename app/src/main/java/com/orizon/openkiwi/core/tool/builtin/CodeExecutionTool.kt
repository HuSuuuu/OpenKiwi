package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.code.CodeSandbox
import com.orizon.openkiwi.core.code.PythonEmbeddedEnv
import com.orizon.openkiwi.core.code.SandboxConfig
import com.orizon.openkiwi.core.tool.*
import com.orizon.openkiwi.service.overlay.TerminalOverlayService
import java.io.File

class CodeExecutionTool(private val sandbox: CodeSandbox) : Tool {

    override val definition = ToolDefinition(
        name = "code_execution",
        description = "Execute code in sandbox. python/shell run locally; javascript/powershell require Companion PC. " +
            PythonEmbeddedEnv.toolDescriptionSuffix(),
        category = ToolCategory.CODE_EXECUTION.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "code" to ToolParamDef("string", "Code to execute", required = true),
            "language" to ToolParamDef("string", "Language: shell, python, javascript, powershell", required = true,
                enumValues = listOf("shell", "python", "javascript", "powershell")),
            "timeout_ms" to ToolParamDef("string", "Execution timeout in milliseconds (default 30000)")
        ),
        requiredParams = listOf("code", "language"),
        returnDescription = "Execution result with stdout, stderr, exit code",
        timeoutMs = 60_000
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val code = params["code"]?.toString() ?: return ToolResult(definition.name, false, "", "Missing code")
        val language = params["language"]?.toString() ?: "shell"
        val timeoutMs = params["timeout_ms"]?.toString()?.toLongOrNull() ?: 30_000L

        TerminalOverlayService.setCommand("[$language] ${code.take(60)}")
        TerminalOverlayService.setStatus(TerminalOverlayService.ExecutionStatus.RUNNING)

        val config = SandboxConfig(maxExecutionTimeMs = timeoutMs)
        val result = sandbox.executeScript(code, language, config)

        TerminalOverlayService.appendOutput(result.stdout.take(2000))
        if (result.stderr.isNotBlank()) {
            TerminalOverlayService.appendOutput(result.stderr.take(500), isError = true)
        }
        TerminalOverlayService.setStatus(
            if (result.exitCode == 0) TerminalOverlayService.ExecutionStatus.SUCCESS
            else TerminalOverlayService.ExecutionStatus.FAILED
        )

        val output = buildString {
            appendLine("Exit code: ${result.exitCode}")
            if (result.stdout.isNotBlank()) {
                appendLine("--- stdout ---")
                appendLine(result.stdout)
            }
            if (result.stderr.isNotBlank()) {
                appendLine("--- stderr ---")
                appendLine(result.stderr)
            }
            appendLine("Execution time: ${result.executionTimeMs}ms")
            if (result.truncated) appendLine("[Output truncated]")
        }

        return ToolResult(
            definition.name,
            result.exitCode == 0,
            output,
            executionTimeMs = result.executionTimeMs,
            artifacts = extractArtifacts(result.stdout, result.stderr)
        )
    }

    private fun extractArtifacts(stdout: String, stderr: String): List<ToolArtifact> {
        val text = "$stdout\n$stderr"
        val pathRegex = Regex("""(?m)(?:saved to|written to|created file|output file|file:)\s+([/\w\-. ]+\.\w+)""", RegexOption.IGNORE_CASE)
        return pathRegex.findAll(text)
            .mapNotNull { match ->
                val raw = match.groupValues[1].trim().trim('"')
                val file = File(raw)
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
