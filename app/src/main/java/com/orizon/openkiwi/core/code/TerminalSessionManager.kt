package com.orizon.openkiwi.core.code

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class TerminalSession(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Terminal",
    val workingDirectory: String = "/",
    val isRoot: Boolean = false,
    val isAlive: Boolean = true,
    val outputHistory: StringBuilder = StringBuilder(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val output: String get() = outputHistory.toString()
}

/**
 * Manages multiple terminal sessions with persistent state.
 */
class TerminalSessionManager {

    companion object {
        private const val TAG = "TerminalMgr"
        private const val MAX_SESSIONS = 10
        private const val MAX_OUTPUT_SIZE = 500_000
    }

    private val sessions = mutableMapOf<String, TerminalSession>()
    private val _activeSessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val activeSessions: StateFlow<List<TerminalSession>> = _activeSessions.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    fun createSession(name: String = "Terminal ${sessions.size + 1}", useRoot: Boolean = false): String {
        if (sessions.size >= MAX_SESSIONS) {
            val oldest = sessions.values.minByOrNull { it.createdAt }
            oldest?.let { removeSession(it.id) }
        }

        val session = TerminalSession(
            name = name,
            workingDirectory = "/data/local/tmp",
            isRoot = useRoot
        )
        sessions[session.id] = session
        if (_currentSessionId.value == null) _currentSessionId.value = session.id
        refreshSessions()
        return session.id
    }

    fun switchSession(sessionId: String) {
        if (sessions.containsKey(sessionId)) {
            _currentSessionId.value = sessionId
        }
    }

    fun removeSession(sessionId: String) {
        sessions.remove(sessionId)
        if (_currentSessionId.value == sessionId) {
            _currentSessionId.value = sessions.keys.firstOrNull()
        }
        refreshSessions()
    }

    fun renameSession(sessionId: String, newName: String) {
        sessions[sessionId]?.let {
            sessions[sessionId] = it.copy(name = newName)
            refreshSessions()
        }
    }

    suspend fun executeInSession(
        sessionId: String,
        command: String
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val session = sessions[sessionId]
            ?: return@withContext ExecutionResult(-1, "", "Session not found", 0)

        val prefix = if (session.isRoot) "su -c" else "sh -c"
        val fullCommand = "cd ${session.workingDirectory} && $command"

        val startTime = System.currentTimeMillis()
        try {
            val process = if (session.isRoot) {
                ProcessBuilder("su", "-c", fullCommand)
            } else {
                ProcessBuilder("sh", "-c", fullCommand)
            }
                .directory(File(session.workingDirectory))
                .redirectErrorStream(false)
                .start()

            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            val elapsed = System.currentTimeMillis() - startTime

            session.outputHistory.append("\$ $command\n$stdout")
            if (stderr.isNotBlank()) session.outputHistory.append(stderr)
            if (session.outputHistory.length > MAX_OUTPUT_SIZE) {
                val trimmed = session.outputHistory.substring(session.outputHistory.length - MAX_OUTPUT_SIZE / 2)
                session.outputHistory.clear().append("...(truncated)...\n").append(trimmed)
            }

            if (command.trim().startsWith("cd ")) {
                val newDir = command.trim().removePrefix("cd ").trim()
                val resolvedDir = if (newDir.startsWith("/")) newDir
                else "${session.workingDirectory}/$newDir"
                val dirFile = File(resolvedDir)
                if (dirFile.exists() && dirFile.isDirectory) {
                    sessions[sessionId] = session.copy(workingDirectory = dirFile.canonicalPath)
                }
            }

            refreshSessions()
            ExecutionResult(exitCode, stdout, stderr, elapsed)
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            session.outputHistory.append("\$ $command\nError: ${e.message}\n")
            refreshSessions()
            ExecutionResult(-1, "", "Error: ${e.message}", elapsed)
        }
    }

    fun getSession(sessionId: String): TerminalSession? = sessions[sessionId]

    fun getCurrentSession(): TerminalSession? = _currentSessionId.value?.let { sessions[it] }

    fun listSessions(): List<TerminalSession> = sessions.values.toList()

    private fun refreshSessions() {
        _activeSessions.value = sessions.values.toList()
    }
}
