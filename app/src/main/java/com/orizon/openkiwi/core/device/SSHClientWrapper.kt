package com.orizon.openkiwi.core.device

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class SSHConfig(
    val host: String,
    val port: Int = 22,
    val username: String,
    val password: String? = null,
    val privateKey: String? = null
)

data class SSHCommandResult(
    val command: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int
)

class SSHClientWrapper {
    private var config: SSHConfig? = null
    private var isConnected = false

    suspend fun connect(sshConfig: SSHConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            config = sshConfig
            isConnected = true
            "Connected to ${sshConfig.host}:${sshConfig.port} as ${sshConfig.username}"
        }
    }

    suspend fun executeCommand(command: String, timeoutSec: Int = 30): Result<SSHCommandResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cfg = config ?: throw IllegalStateException("Not connected")
            val processArgs = mutableListOf("sh", "-c",
                "sshpass -p '${cfg.password}' ssh -o StrictHostKeyChecking=no -p ${cfg.port} ${cfg.username}@${cfg.host} '$command'"
            )
            val process = ProcessBuilder(processArgs).redirectErrorStream(false).start()
            val completed = process.waitFor(timeoutSec.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                throw RuntimeException("SSH command timed out after ${timeoutSec}s")
            }
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
            SSHCommandResult(command = command, stdout = stdout, stderr = stderr, exitCode = process.exitValue())
        }
    }

    fun disconnect() {
        isConnected = false
        config = null
    }

    fun isConnected(): Boolean = isConnected
}
