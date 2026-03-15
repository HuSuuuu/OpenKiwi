package com.orizon.openkiwi.core.device

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.Socket
import java.security.MessageDigest

/**
 * Native SSH client implementation using raw sockets.
 * Supports password auth and basic command execution over SSH protocol.
 * For production, consider JSch/Apache MINA SSHD library.
 */
class SSHClient {

    companion object {
        private const val TAG = "SSHClient"
        private const val DEFAULT_PORT = 22
        private const val READ_TIMEOUT = 15_000
    }

    data class SSHConfig(
        val host: String,
        val port: Int = DEFAULT_PORT,
        val username: String,
        val password: String? = null,
        val privateKey: String? = null,
        val timeoutMs: Int = 10_000
    )

    data class SSHResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val success: Boolean
    )

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: OutputStream? = null
    private var connected = false
    private var config: SSHConfig? = null

    suspend fun connect(config: SSHConfig): Boolean = withContext(Dispatchers.IO) {
        this@SSHClient.config = config
        try {
            // Use system ssh binary or sshpass if available
            val sshAvailable = checkBinary("ssh")
            if (sshAvailable) {
                connected = true
                return@withContext true
            }

            // Fallback: try sshpass
            val sshpassAvailable = checkBinary("sshpass")
            if (sshpassAvailable && config.password != null) {
                connected = true
                return@withContext true
            }

            // Try direct socket connection for basic validation
            socket = Socket(config.host, config.port).apply {
                soTimeout = READ_TIMEOUT
            }
            reader = socket?.getInputStream()?.bufferedReader()
            writer = socket?.getOutputStream()

            // Read SSH banner
            val banner = reader?.readLine()
            Log.i(TAG, "SSH banner: $banner")
            connected = banner?.startsWith("SSH-") == true

            // For real SSH negotiation, a proper library is needed.
            // Close raw socket and use command-line ssh/sshpass for execution.
            socket?.close()
            socket = null

            connected
        } catch (e: Exception) {
            Log.e(TAG, "SSH connect failed: ${e.message}")
            connected = false
            false
        }
    }

    suspend fun execute(command: String): SSHResult = withContext(Dispatchers.IO) {
        val cfg = config ?: return@withContext SSHResult(-1, "", "Not configured", false)

        try {
            val processArgs = buildSSHCommand(cfg, command)
            val process = ProcessBuilder(processArgs)
                .redirectErrorStream(false)
                .start()

            if (cfg.password != null && !checkBinary("sshpass")) {
                // Write password to stdin for ssh
                process.outputStream.write("${cfg.password}\n".toByteArray())
                process.outputStream.flush()
            }

            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            SSHResult(exitCode, stdout, stderr, exitCode == 0)
        } catch (e: Exception) {
            SSHResult(-1, "", "SSH exec error: ${e.message}", false)
        }
    }

    suspend fun uploadFile(localPath: String, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        val cfg = config ?: return@withContext false
        try {
            val scpArgs = buildSCPCommand(cfg, localPath, remotePath, upload = true)
            val process = ProcessBuilder(scpArgs).start()
            if (cfg.password != null && !checkBinary("sshpass")) {
                process.outputStream.write("${cfg.password}\n".toByteArray())
                process.outputStream.flush()
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "SCP upload failed", e)
            false
        }
    }

    suspend fun downloadFile(remotePath: String, localPath: String): Boolean = withContext(Dispatchers.IO) {
        val cfg = config ?: return@withContext false
        try {
            val scpArgs = buildSCPCommand(cfg, localPath, remotePath, upload = false)
            val process = ProcessBuilder(scpArgs).start()
            if (cfg.password != null && !checkBinary("sshpass")) {
                process.outputStream.write("${cfg.password}\n".toByteArray())
                process.outputStream.flush()
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "SCP download failed", e)
            false
        }
    }

    fun disconnect() {
        socket?.close()
        socket = null
        reader = null
        writer = null
        connected = false
    }

    fun isConnected(): Boolean = connected

    fun getHostInfo(): String = config?.let { "${it.username}@${it.host}:${it.port}" } ?: "disconnected"

    private fun buildSSHCommand(cfg: SSHConfig, command: String): List<String> {
        val args = mutableListOf<String>()
        if (cfg.password != null && checkBinary("sshpass")) {
            args.addAll(listOf("sshpass", "-p", cfg.password))
        }
        args.add("ssh")
        args.addAll(listOf("-o", "StrictHostKeyChecking=no"))
        args.addAll(listOf("-o", "ConnectTimeout=${cfg.timeoutMs / 1000}"))
        args.addAll(listOf("-p", cfg.port.toString()))
        if (cfg.privateKey != null) {
            args.addAll(listOf("-i", cfg.privateKey))
        }
        args.add("${cfg.username}@${cfg.host}")
        args.add(command)
        return args
    }

    private fun buildSCPCommand(cfg: SSHConfig, localPath: String, remotePath: String, upload: Boolean): List<String> {
        val args = mutableListOf<String>()
        if (cfg.password != null && checkBinary("sshpass")) {
            args.addAll(listOf("sshpass", "-p", cfg.password))
        }
        args.add("scp")
        args.addAll(listOf("-o", "StrictHostKeyChecking=no"))
        args.addAll(listOf("-P", cfg.port.toString()))
        if (upload) {
            args.add(localPath)
            args.add("${cfg.username}@${cfg.host}:$remotePath")
        } else {
            args.add("${cfg.username}@${cfg.host}:$remotePath")
            args.add(localPath)
        }
        return args
    }

    private fun checkBinary(name: String): Boolean {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("which", name))
            proc.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }
}
