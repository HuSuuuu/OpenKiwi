package com.orizon.openkiwi.core.code

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.orizon.openkiwi.network.CompanionServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
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

class CodeSandbox(private val context: Context) {

    companion object {
        private const val TAG = "CodeSandbox"
        private val BLOCKED_COMMANDS = listOf(
            "rm -rf /", "mkfs", "dd if=/dev/zero",
            ":(){ :|:& };:", "chmod -R 777 /",
            "format", "fdisk", "> /dev/sda"
        )
    }

    var companionServer: CompanionServer? = null

    private val json = Json { ignoreUnknownKeys = true }

    private val sandboxDir: File by lazy {
        File(context.filesDir, "sandbox").also { it.mkdirs() }
    }

    @Synchronized
    private fun ensurePythonStarted(): Boolean {
        if (Python.isStarted()) return true
        return try {
            Python.start(AndroidPlatform(context.applicationContext))
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize embedded Python on demand", t)
            false
        }
    }

    suspend fun executeShell(
        command: String,
        config: SandboxConfig = SandboxConfig()
    ): ExecutionResult = withContext(Dispatchers.IO) {
        if (BLOCKED_COMMANDS.any { command.contains(it, ignoreCase = true) }) {
            return@withContext ExecutionResult(-1, "", "Blocked: dangerous command", 0)
        }
        if (ShellPythonHint.commandInvokesSystemPython(command)) {
            return@withContext ExecutionResult(
                -1, "", ShellPythonHint.USE_CODE_EXECUTION_ZH, 0
            )
        }

        val workDir = config.workDir?.let { File(it) } ?: sandboxDir
        workDir.mkdirs()

        val startTime = System.currentTimeMillis()
        try {
            withTimeout(config.maxExecutionTimeMs) {
                val envVars = buildShellEnv()
                val pb = ProcessBuilder("sh", "-c", command)
                    .directory(workDir)
                    .redirectErrorStream(false)
                pb.environment().putAll(envVars)
                val process = pb.start()

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

    private fun buildShellEnv(): Map<String, String> {
        val env = mutableMapOf<String, String>()
        env["HOME"] = context.filesDir.absolutePath
        env["TMPDIR"] = context.cacheDir.absolutePath
        env["ANDROID_DATA"] = "/data"
        env["ANDROID_ROOT"] = "/system"
        val pathDirs = listOf(
            "/system/bin", "/system/xbin",
            "/vendor/bin", "/sbin",
            context.applicationInfo.nativeLibraryDir
        )
        env["PATH"] = pathDirs.joinToString(":")
        return env
    }

    suspend fun executePythonLocal(
        code: String,
        config: SandboxConfig = SandboxConfig()
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            withTimeout(config.maxExecutionTimeMs) {
                if (!ensurePythonStarted()) {
                    return@withTimeout ExecutionResult(
                        -1, "", ShellPythonHint.CHAQUOPY_INIT_FAILED_ZH,
                        System.currentTimeMillis() - startTime
                    )
                }
                val py = Python.getInstance()
                val execModule = py.getModule("kiwi_exec")
                val resultMap = execModule.callAttr("run_code", code)

                val stdout = resultMap.callAttr("get", "stdout", "").toString()
                    .take(config.maxOutputBytes)
                val stderr = resultMap.callAttr("get", "stderr", "").toString()
                    .take(config.maxOutputBytes)
                val exitCode = resultMap.callAttr("get", "exit_code", 0)
                    .toString().toIntOrNull() ?: -1
                val elapsed = System.currentTimeMillis() - startTime

                Log.i(TAG, "Local Python done: exit=$exitCode, stdout=${stdout.length}b, stderr=${stderr.length}b")

                ExecutionResult(
                    exitCode = exitCode,
                    stdout = stdout,
                    stderr = stderr,
                    executionTimeMs = elapsed,
                    truncated = stdout.length >= config.maxOutputBytes
                )
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            ExecutionResult(
                -1, "",
                "本地 Python 执行超时（${config.maxExecutionTimeMs}ms）",
                System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "Local Python execution failed", e)
            ExecutionResult(-1, "", "本地 Python 执行失败: ${e.message}\n${e.stackTraceToString().take(1000)}",
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

            "python", "python3", "py" -> {
                // 不可先用 Python.isStarted() 判断：冷启动时尚未 start，会误判为不可用。
                // executePythonLocal 内会 ensurePythonStarted() 按需启动 Chaquopy。
                val local = executePythonLocal(code, config)
                val initFailed = local.exitCode == -1 && (
                    local.stderr.contains("初始化失败", ignoreCase = true) ||
                        local.stderr.contains("启动失败", ignoreCase = true) ||
                        local.stderr.contains("Chaquopy", ignoreCase = true) ||
                        local.stderr.contains("Failed to initialize", ignoreCase = true)
                    )
                if (initFailed) {
                    val pc = companionServer
                    if (pc != null && pc.hasConnectedClients()) {
                        executeOnCompanionPC(code, language, config)
                    } else {
                        local
                    }
                } else {
                    local
                }
            }

            "javascript", "js", "node",
            "powershell", "ps", "cmd" -> executeOnCompanionPC(code, language, config)

            else -> {
                val pc = companionServer
                if (pc != null && pc.hasConnectedClients()) {
                    executeOnCompanionPC(code, language, config)
                } else {
                    ExecutionResult(
                        -1, "",
                        "当前设备不支持 $language。请连接 Companion PC 后重试。",
                        0
                    )
                }
            }
        }
    }

    private suspend fun executeOnCompanionPC(
        code: String,
        language: String,
        config: SandboxConfig
    ): ExecutionResult {
        val pc = companionServer
        if (pc == null || !pc.hasConnectedClients()) {
            return ExecutionResult(
                -1, "",
                "未连接 Companion PC。请在电脑上运行 companion-pc 并连接后，即可执行 $language 代码。",
                0
            )
        }

        Log.i(TAG, "Routing $language code to companion PC (${code.length} chars)")
        val startTime = System.currentTimeMillis()

        val resultJson = pc.executeOnPC(code, language, config.maxExecutionTimeMs)

        if (resultJson == null) {
            return ExecutionResult(
                -1, "",
                "Companion PC 执行超时或通信失败",
                System.currentTimeMillis() - startTime
            )
        }

        return try {
            val obj = json.parseToJsonElement(resultJson).jsonObject
            ExecutionResult(
                exitCode = obj["exit_code"]?.jsonPrimitive?.intOrNull ?: -1,
                stdout = obj["stdout"]?.jsonPrimitive?.content ?: "",
                stderr = obj["stderr"]?.jsonPrimitive?.content ?: "",
                executionTimeMs = obj["execution_time_ms"]?.jsonPrimitive?.intOrNull?.toLong()
                    ?: (System.currentTimeMillis() - startTime),
                truncated = obj["truncated"]?.jsonPrimitive?.booleanOrNull ?: false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse PC result", e)
            ExecutionResult(-1, resultJson.take(5000), "结果解析失败: ${e.message}",
                System.currentTimeMillis() - startTime)
        }
    }

    fun cleanup() {
        sandboxDir.listFiles()?.forEach { it.deleteRecursively() }
    }
}
