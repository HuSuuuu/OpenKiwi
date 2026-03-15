package com.orizon.openkiwi.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.Socket

/**
 * Basic FTP client supporting upload, download, list, and directory operations.
 */
class FTPClient {

    companion object {
        private const val TAG = "FTPClient"
    }

    data class FTPConfig(
        val host: String,
        val port: Int = 21,
        val username: String = "anonymous",
        val password: String = "",
        val passive: Boolean = true
    )

    data class FTPEntry(
        val name: String,
        val size: Long = 0,
        val isDirectory: Boolean = false,
        val modified: String = ""
    )

    private var controlSocket: Socket? = null
    private var controlReader: BufferedReader? = null
    private var controlWriter: PrintWriter? = null
    private var connected = false

    suspend fun connect(config: FTPConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            controlSocket = Socket(config.host, config.port)
            controlReader = controlSocket!!.getInputStream().bufferedReader()
            controlWriter = PrintWriter(controlSocket!!.getOutputStream(), true)

            val welcome = readResponse()
            if (!welcome.startsWith("220")) return@withContext false

            sendCommand("USER ${config.username}")
            val userResp = readResponse()

            if (userResp.startsWith("331")) {
                sendCommand("PASS ${config.password}")
                val passResp = readResponse()
                if (!passResp.startsWith("230")) return@withContext false
            }

            sendCommand("TYPE I")
            readResponse()

            connected = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "FTP connect failed", e)
            false
        }
    }

    suspend fun listFiles(path: String = "."): List<FTPEntry> = withContext(Dispatchers.IO) {
        if (!connected) return@withContext emptyList()
        try {
            val dataSocket = openDataConnection() ?: return@withContext emptyList()
            sendCommand("LIST $path")
            val cmdResp = readResponse()
            if (!cmdResp.startsWith("150") && !cmdResp.startsWith("125")) {
                dataSocket.close()
                return@withContext emptyList()
            }

            val listing = dataSocket.getInputStream().bufferedReader().use { it.readText() }
            dataSocket.close()
            readResponse()

            parseListing(listing)
        } catch (e: Exception) {
            Log.e(TAG, "FTP list failed", e)
            emptyList()
        }
    }

    suspend fun downloadFile(remotePath: String, localPath: String): Boolean = withContext(Dispatchers.IO) {
        if (!connected) return@withContext false
        try {
            val dataSocket = openDataConnection() ?: return@withContext false
            sendCommand("RETR $remotePath")
            val resp = readResponse()
            if (!resp.startsWith("150") && !resp.startsWith("125")) {
                dataSocket.close()
                return@withContext false
            }

            FileOutputStream(localPath).use { fos ->
                dataSocket.getInputStream().copyTo(fos)
            }
            dataSocket.close()
            readResponse()
            true
        } catch (e: Exception) {
            Log.e(TAG, "FTP download failed", e)
            false
        }
    }

    suspend fun uploadFile(localPath: String, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        if (!connected) return@withContext false
        try {
            val dataSocket = openDataConnection() ?: return@withContext false
            sendCommand("STOR $remotePath")
            val resp = readResponse()
            if (!resp.startsWith("150") && !resp.startsWith("125")) {
                dataSocket.close()
                return@withContext false
            }

            FileInputStream(localPath).use { fis ->
                fis.copyTo(dataSocket.getOutputStream())
            }
            dataSocket.close()
            readResponse()
            true
        } catch (e: Exception) {
            Log.e(TAG, "FTP upload failed", e)
            false
        }
    }

    suspend fun changeDirectory(path: String): Boolean = withContext(Dispatchers.IO) {
        sendCommand("CWD $path")
        readResponse().startsWith("250")
    }

    suspend fun makeDirectory(path: String): Boolean = withContext(Dispatchers.IO) {
        sendCommand("MKD $path")
        readResponse().startsWith("257")
    }

    suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        sendCommand("DELE $path")
        readResponse().startsWith("250")
    }

    suspend fun getCurrentDirectory(): String = withContext(Dispatchers.IO) {
        sendCommand("PWD")
        val resp = readResponse()
        Regex("\"(.+?)\"").find(resp)?.groupValues?.get(1) ?: "/"
    }

    fun disconnect() {
        try {
            sendCommand("QUIT")
            controlSocket?.close()
        } catch (_: Exception) {}
        connected = false
        controlSocket = null
        controlReader = null
        controlWriter = null
    }

    fun isConnected(): Boolean = connected

    private fun sendCommand(cmd: String) {
        controlWriter?.println(cmd)
    }

    private fun readResponse(): String {
        val sb = StringBuilder()
        var line = controlReader?.readLine() ?: return ""
        sb.appendLine(line)
        // Multi-line response
        while (line.length >= 4 && line[3] == '-') {
            line = controlReader?.readLine() ?: break
            sb.appendLine(line)
        }
        return sb.toString().trim()
    }

    private fun openDataConnection(): Socket? {
        sendCommand("PASV")
        val resp = readResponse()
        if (!resp.startsWith("227")) return null

        val match = Regex("\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\)").find(resp) ?: return null
        val parts = match.groupValues.drop(1).map { it.toInt() }
        val host = "${parts[0]}.${parts[1]}.${parts[2]}.${parts[3]}"
        val port = parts[4] * 256 + parts[5]

        return try {
            Socket(host, port)
        } catch (e: Exception) {
            Log.e(TAG, "Data connection failed", e)
            null
        }
    }

    private fun parseListing(listing: String): List<FTPEntry> {
        return listing.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            try {
                val isDir = line.startsWith("d")
                val parts = line.split(Regex("\\s+"), limit = 9)
                if (parts.size >= 9) {
                    FTPEntry(
                        name = parts[8],
                        size = parts[4].toLongOrNull() ?: 0,
                        isDirectory = isDir,
                        modified = "${parts[5]} ${parts[6]} ${parts[7]}"
                    )
                } else null
            } catch (_: Exception) { null }
        }
    }
}
