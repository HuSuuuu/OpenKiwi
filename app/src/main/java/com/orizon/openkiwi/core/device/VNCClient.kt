package com.orizon.openkiwi.core.device

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

/**
 * Minimal VNC (RFB) client for remote desktop viewing and control.
 * Implements RFB 3.3/3.8 protocol basics.
 */
class VNCClient {

    companion object {
        private const val TAG = "VNCClient"
        private const val RFB_VERSION = "RFB 003.008\n"
    }

    data class VNCConfig(
        val host: String,
        val port: Int = 5900,
        val password: String = "",
        val viewOnly: Boolean = false
    )

    data class ScreenInfo(
        val width: Int,
        val height: Int,
        val name: String
    )

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _screenInfo = MutableStateFlow<ScreenInfo?>(null)
    val screenInfo: StateFlow<ScreenInfo?> = _screenInfo.asStateFlow()

    suspend fun connect(config: VNCConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            socket = Socket(config.host, config.port).apply {
                soTimeout = 10_000
                tcpNoDelay = true
            }
            input = DataInputStream(socket!!.getInputStream())
            output = DataOutputStream(socket!!.getOutputStream())

            // Read server version
            val serverVersion = ByteArray(12)
            input!!.readFully(serverVersion)
            val version = String(serverVersion).trim()
            Log.i(TAG, "Server version: $version")

            // Send client version
            output!!.write(RFB_VERSION.toByteArray())
            output!!.flush()

            // Security handshake
            val numSecTypes = input!!.readUnsignedByte()
            val secTypes = ByteArray(numSecTypes)
            input!!.readFully(secTypes)

            // Select security type (1=None, 2=VNC Auth)
            val hasNone = secTypes.any { it.toInt() == 1 }
            val hasVNCAuth = secTypes.any { it.toInt() == 2 }

            when {
                hasNone && config.password.isEmpty() -> {
                    output!!.writeByte(1) // None
                    output!!.flush()
                }
                hasVNCAuth && config.password.isNotEmpty() -> {
                    output!!.writeByte(2) // VNC Auth
                    output!!.flush()
                    performVNCAuth(config.password)
                }
                hasNone -> {
                    output!!.writeByte(1)
                    output!!.flush()
                }
                else -> {
                    Log.e(TAG, "No supported security type")
                    disconnect()
                    return@withContext false
                }
            }

            // Check security result
            val secResult = input!!.readInt()
            if (secResult != 0) {
                Log.e(TAG, "Authentication failed: $secResult")
                disconnect()
                return@withContext false
            }

            // ClientInit - shared flag
            output!!.writeByte(1)
            output!!.flush()

            // ServerInit
            val width = input!!.readUnsignedShort()
            val height = input!!.readUnsignedShort()

            // Skip pixel format (16 bytes)
            input!!.skipBytes(16)

            // Read desktop name
            val nameLen = input!!.readInt()
            val nameBytes = ByteArray(nameLen)
            input!!.readFully(nameBytes)
            val name = String(nameBytes)

            _screenInfo.value = ScreenInfo(width, height, name)
            _connected.value = true
            Log.i(TAG, "Connected to VNC: ${width}x${height} - $name")
            true
        } catch (e: Exception) {
            Log.e(TAG, "VNC connect failed", e)
            disconnect()
            false
        }
    }

    suspend fun sendMouseEvent(x: Int, y: Int, buttonMask: Int = 0) = withContext(Dispatchers.IO) {
        try {
            output?.apply {
                writeByte(5) // PointerEvent
                writeByte(buttonMask)
                writeShort(x)
                writeShort(y)
                flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Mouse event failed", e)
        }
    }

    suspend fun sendKeyEvent(key: Int, pressed: Boolean) = withContext(Dispatchers.IO) {
        try {
            output?.apply {
                writeByte(4) // KeyEvent
                writeByte(if (pressed) 1 else 0)
                writeShort(0) // padding
                writeInt(key)
                flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Key event failed", e)
        }
    }

    suspend fun sendText(text: String) {
        for (ch in text) {
            val keysym = ch.code
            sendKeyEvent(keysym, true)
            sendKeyEvent(keysym, false)
        }
    }

    fun disconnect() {
        _connected.value = false
        _screenInfo.value = null
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        input = null
        output = null
    }

    fun isConnected(): Boolean = _connected.value

    private fun performVNCAuth(password: String) {
        val challenge = ByteArray(16)
        input!!.readFully(challenge)

        // VNC DES authentication
        val key = password.toByteArray().copyOf(8)
        // Reverse bits in each byte (VNC DES quirk)
        for (i in key.indices) {
            var b = key[i].toInt() and 0xFF
            b = (b and 0x55 shl 1) or (b and 0xAA ushr 1)
            b = (b and 0x33 shl 2) or (b and 0xCC ushr 2)
            b = (b and 0x0F shl 4) or (b and 0xF0 ushr 4)
            key[i] = b.toByte()
        }

        // Simple XOR-based response (real impl needs DES)
        val response = ByteArray(16)
        for (i in 0..15) {
            response[i] = (challenge[i].toInt() xor key[i % 8].toInt()).toByte()
        }

        output!!.write(response)
        output!!.flush()
    }
}
