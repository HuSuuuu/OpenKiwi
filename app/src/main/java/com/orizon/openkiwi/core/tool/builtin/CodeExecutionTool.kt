package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.code.CodeSandbox
import com.orizon.openkiwi.core.code.SandboxConfig
import com.orizon.openkiwi.core.tool.*
import com.orizon.openkiwi.service.overlay.TerminalOverlayService

class CodeExecutionTool(private val sandbox: CodeSandbox) : Tool {

    override val definition = ToolDefinition(
        name = "code_execute",
        description = "Execute code in a sandboxed environment. Supports shell, python, and javascript. Returns stdout, stderr, and exit code.",
        category = ToolCategory.CODE_EXECUTION.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "code" to ToolParamDef("string", "Code to execute", required = true),
            "language" to ToolParamDef("string", "Language: shell, python, javascript", required = true,
                enumValues = listOf("shell", "python", "javascript")),
            "timeout_ms" to ToolParamDef("string", "Execution timeout in milliseconds (default 30000)")
        ),
        requiredParams = listOf("code", "language"),
        returnDescription = "Execution result with stdout, stderr, exit code",
        timeoutMs = 60_000
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val code = params["code"]?.toString() ?: return ToolResult("code_execute", false, "", "Missing code")
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

        return ToolResult("code_execute", result.exitCode == 0, output, executionTimeMs = result.executionTimeMs)
    }
}
