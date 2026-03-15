package com.orizon.openkiwi.core.code

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

data class SandboxConfig(
    val allowNetwork: Boolean = false,
    val allowFileWrite: Boolean = true,
    val maxExecutionTimeMs: Long = 30_000,
    val maxOutputBytes: Int = 100_000,
    val workDir: String? = null
)

data class ExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val executionTimeMs: Long,
    val truncated: Boolean = false
)

/**
 * Sandboxed code execution environment.
 * Supports Shell, Python (if available via termux/proot), and basic scripts.
 */
class CodeSandbox(private val context: Context) {

    companion object {
        private const val TAG = "CodeSandbox"
        private val BLOCKED_COMMANDS = listOf(
            "rm -rf /", "mkfs", "dd if=/dev/zero",
            ":(){ :|:& };:", "chmod -R 777 /",
            "format", "fdisk", "> /dev/sda"
        )
    }

    private val sandboxDir: File by lazy {
        File(context.filesDir, "sandbox").also { it.mkdirs() }
    }

    suspend fun executeShell(
        command: String,
        config: SandboxConfig = SandboxConfig()
    ): ExecutionResult = withContext(Dispatchers.IO) {
        if (BLOCKED_COMMANDS.any { command.contains(it, ignoreCase = true) }) {
            return@withContext ExecutionResult(-1, "", "Blocked: dangerous command", 0)
        }

        val workDir = config.workDir?.let { File(it) } ?: sandboxDir
        workDir.mkdirs()

        val startTime = System.currentTimeMillis()
        try {
            withTimeout(config.maxExecutionTimeMs) {
                val process = ProcessBuilder("sh", "-c", command)
                    .directory(workDir)
                    .redirectErrorStream(false)
                    .start()

                val stdout = process.inputStream.bufferedReader().use {
                    it.readText().take(config.maxOutputBytes)
                }
                val stderr = process.errorStream.bufferedReader().use {
                    it.readText().take(config.maxOutputBytes)
                }
                val exitCode = process.waitFor()
                val elapsed = System.currentTimeMillis() - startTime

                ExecutionResult(
                    exitCode = exitCode,
                    stdout = stdout,
                    stderr = stderr,
                    executionTimeMs = elapsed,
                    truncated = stdout.length >= config.maxOutputBytes
                )
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            ExecutionResult(-1, "", "Execution timed out after ${config.maxExecutionTimeMs}ms",
                System.currentTimeMillis() - startTime)
        } catch (e: Exception) {
            ExecutionResult(-1, "", "Execution error: ${e.message}",
                System.currentTimeMillis() - startTime)
        }
    }

    suspend fun executeScript(
        code: String,
        language: String,
        config: SandboxConfig = SandboxConfig()
    ): ExecutionResult {
        return when (language.lowercase()) {
            "sh", "bash", "shell" -> executeShell(code, config)
            else -> ExecutionResult(
                -1, "",
                "当前设备仅支持 Shell/Bash 脚本执行。不支持的语言: $language",
                0
            )
        }
    }

    fun cleanup() {
        sandboxDir.listFiles()?.forEach { it.deleteRecursively() }
    }
}
