package com.orizon.openkiwi.core.device

import android.content.Context
import android.hardware.usb.*
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * USB-to-Serial driver supporting CDC ACM, CP210x, CH340, FTDI chips.
 * Provides bidirectional serial communication over USB.
 */
class USBSerialDriver(private val context: Context) {

    companion object {
        private const val TAG = "USBSerial"

        // Vendor IDs for common serial chips
        const val VID_FTDI = 0x0403
        const val VID_CP210X = 0x10C4
        const val VID_CH340 = 0x1A86
        const val VID_PROLIFIC = 0x067B
    }

    data class SerialConfig(
        val baudRate: Int = 9600,
        val dataBits: Int = 8,
        val stopBits: Int = 1,
        val parity: Int = 0 // 0=None, 1=Odd, 2=Even
    )

    private var connection: UsbDeviceConnection? = null
    private var serialInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null

    fun findSerialDevices(): List<UsbDevice> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return usbManager.deviceList.values.filter { device ->
            isSerialDevice(device) || device.interfaceCount > 0 && (0 until device.interfaceCount).any {
                device.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_CDC_DATA ||
                device.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_COMM
            }
        }
    }

    fun isSerialDevice(device: UsbDevice): Boolean {
        return device.vendorId in listOf(VID_FTDI, VID_CP210X, VID_CH340, VID_PROLIFIC)
    }

    fun getChipType(device: UsbDevice): String = when (device.vendorId) {
        VID_FTDI -> "FTDI"
        VID_CP210X -> "CP210x"
        VID_CH340 -> "CH340"
        VID_PROLIFIC -> "Prolific"
        else -> if (hasCDCInterface(device)) "CDC-ACM" else "Unknown"
    }

    fun connect(device: UsbDevice, config: SerialConfig = SerialConfig()): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        connection = usbManager.openDevice(device) ?: run {
            Log.e(TAG, "Cannot open device")
            return false
        }

        // Find data interface with bulk endpoints
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.endpointCount >= 2) {
                for (j in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(j)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN) inEndpoint = ep
                        else outEndpoint = ep
                    }
                }
                if (inEndpoint != null && outEndpoint != null) {
                    serialInterface = iface
                    break
                }
            }
        }

        if (serialInterface == null || inEndpoint == null || outEndpoint == null) {
            Log.e(TAG, "No suitable serial interface found")
            disconnect()
            return false
        }

        connection?.claimInterface(serialInterface, true)
        configureBaudRate(device, config)
        Log.i(TAG, "Connected to ${getChipType(device)} @ ${config.baudRate}baud")
        return true
    }

    suspend fun write(data: ByteArray): Int = withContext(Dispatchers.IO) {
        val conn = connection ?: return@withContext -1
        val ep = outEndpoint ?: return@withContext -1
        conn.bulkTransfer(ep, data, data.size, 1000)
    }

    suspend fun write(text: String): Int = write(text.toByteArray())

    suspend fun read(maxBytes: Int = 1024, timeoutMs: Int = 1000): ByteArray? = withContext(Dispatchers.IO) {
        val conn = connection ?: return@withContext null
        val ep = inEndpoint ?: return@withContext null
        val buffer = ByteArray(maxBytes)
        val received = conn.bulkTransfer(ep, buffer, buffer.size, timeoutMs)
        if (received > 0) buffer.copyOf(received) else null
    }

    suspend fun readString(maxBytes: Int = 1024, timeoutMs: Int = 1000): String? {
        return read(maxBytes, timeoutMs)?.toString(Charsets.UTF_8)
    }

    fun disconnect() {
        serialInterface?.let { connection?.releaseInterface(it) }
        connection?.close()
        connection = null
        serialInterface = null
        inEndpoint = null
        outEndpoint = null
    }

    fun isConnected(): Boolean = connection != null

    private fun configureBaudRate(device: UsbDevice, config: SerialConfig) {
        val conn = connection ?: return
        when (device.vendorId) {
            VID_CP210X -> {
                // CP210x: SET_BAUDRATE via vendor request
                conn.controlTransfer(0x41, 0x1E, 0, 0,
                    intToBytes(config.baudRate), 4, 1000)
            }
            VID_CH340 -> {
                // CH340: init + set baudrate
                conn.controlTransfer(0x40, 0xA1, 0, 0, null, 0, 1000)
                val factor = 1532620800 / config.baudRate
                val divisor = 3
                conn.controlTransfer(0x40, 0x9A, 0x1312,
                    (factor.toShort().toInt() and 0xFFFF) or (divisor shl 16), null, 0, 1000)
            }
            else -> {
                // CDC-ACM: SET_LINE_CODING
                val lineEncoding = ByteArray(7)
                val baud = config.baudRate
                lineEncoding[0] = (baud and 0xFF).toByte()
                lineEncoding[1] = (baud shr 8 and 0xFF).toByte()
                lineEncoding[2] = (baud shr 16 and 0xFF).toByte()
                lineEncoding[3] = (baud shr 24 and 0xFF).toByte()
                lineEncoding[4] = config.stopBits.toByte()
                lineEncoding[5] = config.parity.toByte()
                lineEncoding[6] = config.dataBits.toByte()
                conn.controlTransfer(0x21, 0x20, 0, 0, lineEncoding, 7, 1000)
                // SET_CONTROL_LINE_STATE (DTR + RTS)
                conn.controlTransfer(0x21, 0x22, 0x03, 0, null, 0, 1000)
            }
        }
    }

    private fun hasCDCInterface(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_COMM) return true
        }
        return false
    }

    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte(),
            (value shr 16 and 0xFF).toByte(),
            (value shr 24 and 0xFF).toByte()
        )
    }
}
