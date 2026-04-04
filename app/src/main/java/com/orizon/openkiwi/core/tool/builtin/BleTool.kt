package com.orizon.openkiwi.core.tool.builtin

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.orizon.openkiwi.core.device.BluetoothGattSessionManager
import com.orizon.openkiwi.core.tool.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

class BleTool(
    private val context: Context,
    private val sessionManager: BluetoothGattSessionManager
) : Tool {
    override val definition = ToolDefinition(
        name = "ble",
        description = "Bluetooth Low Energy: scan for devices, connect, discover services, read/write characteristics, and subscribe to notifications. Payload uses hex encoding.",
        category = ToolCategory.DEVICE.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef(
                "string",
                "BLE action to perform",
                true,
                enumValues = listOf(
                    "scan", "connect", "disconnect", "disconnect_all",
                    "discover", "read", "write", "write_no_response",
                    "subscribe", "unsubscribe", "read_notify",
                    "sessions", "adapter_status"
                )
            ),
            "address" to ToolParamDef("string", "Device MAC address (e.g. AA:BB:CC:DD:EE:FF)"),
            "service_uuid" to ToolParamDef("string", "GATT service UUID"),
            "char_uuid" to ToolParamDef("string", "GATT characteristic UUID"),
            "value_hex" to ToolParamDef("string", "Hex-encoded bytes to write, e.g. '0102ff'"),
            "scan_duration_ms" to ToolParamDef("string", "Scan duration in ms (default 5000, max 15000)"),
            "filter_name" to ToolParamDef("string", "Filter scan results by device name prefix"),
            "filter_service_uuid" to ToolParamDef("string", "Filter scan by advertised service UUID")
        ),
        requiredParams = listOf("action"),
        timeoutMs = 20_000
    )

    @SuppressLint("MissingPermission")
    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return errorResult("Missing action")
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

        return runCatching {
            when (action) {
                "adapter_status" -> {
                    if (adapter == null) return@runCatching ToolResult(definition.name, true, "Bluetooth not available on this device")
                    val sb = buildString {
                        appendLine("Bluetooth enabled: ${adapter.isEnabled}")
                        appendLine("Name: ${adapter.name}")
                        appendLine("Address: ${adapter.address}")
                        appendLine("Scan mode: ${scanModeName(adapter.scanMode)}")
                        appendLine("Active BLE sessions: ${sessionManager.getAllSessions().size}")
                    }
                    ToolResult(definition.name, true, sb)
                }

                "scan" -> scan(params, adapter)

                "connect" -> {
                    val address = requireAddress(params) ?: return@runCatching errorResult("Missing 'address'")
                    val result = sessionManager.connect(address)
                    result.fold(
                        onSuccess = { session ->
                            val services = session.services.joinToString("\n") { svc ->
                                "  ${svc.uuid} (${svc.characteristics.size} chars)"
                            }
                            ToolResult(definition.name, true,
                                "Connected to $address\nServices:\n$services")
                        },
                        onFailure = { errorResult("Connect failed: ${it.message}") }
                    )
                }

                "disconnect" -> {
                    val address = requireAddress(params) ?: return@runCatching errorResult("Missing 'address'")
                    val ok = sessionManager.disconnect(address)
                    ToolResult(definition.name, true, if (ok) "Disconnected $address" else "No active session for $address")
                }

                "disconnect_all" -> {
                    sessionManager.disconnectAll()
                    ToolResult(definition.name, true, "All BLE sessions disconnected")
                }

                "discover" -> {
                    val address = requireAddress(params) ?: return@runCatching errorResult("Missing 'address'")
                    val session = sessionManager.getSession(address)
                        ?: return@runCatching errorResult("Not connected to $address")
                    val sb = StringBuilder("Services for $address:\n")
                    session.services.forEach { svc ->
                        sb.appendLine("Service: ${svc.uuid}")
                        svc.characteristics.forEach { ch ->
                            val props = charPropsString(ch.properties)
                            sb.appendLine("  Char: ${ch.uuid} [$props]")
                            ch.descriptors.forEach { d ->
                                sb.appendLine("    Desc: ${d.uuid}")
                            }
                        }
                    }
                    ToolResult(definition.name, true, sb.toString())
                }

                "read" -> {
                    val address = requireAddress(params) ?: return@runCatching errorResult("Missing 'address'")
                    val svcUuid = requireUuid(params, "service_uuid") ?: return@runCatching errorResult("Missing 'service_uuid'")
                    val charUuid = requireUuid(params, "char_uuid") ?: return@runCatching errorResult("Missing 'char_uuid'")
                    val result = sessionManager.readCharacteristic(address, svcUuid, charUuid)
                    result.fold(
                        onSuccess = { bytes ->
                            ToolResult(definition.name, true,
                                "Read ${bytes.size} bytes: ${bytes.toHex()}\nUTF-8: ${bytes.toString(Charsets.UTF_8)}")
                        },
                        onFailure = { errorResult("Read failed: ${it.message}") }
                    )
                }

                "write", "write_no_response" -> {
                    val address = requireAddress(params) ?: return@runCatching errorResult("Missing 'address'")
                    val svcUuid = requireUuid(params, "service_uuid") ?: return@runCatching errorResult("Missing 'service_uuid'")
                    val charUuid = requireUuid(params, "char_uuid") ?: return@runCatching errorResult("Missing 'char_uuid'")
                    val hex = params["value_hex"]?.toString() ?: return@runCatching errorResult("Missing 'value_hex'")
                    val bytes = hex.hexToBytes() ?: return@runCatching errorResult("Invalid hex in 'value_hex'")
                    val writeType = if (action == "write_no_response")
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    else
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    val result = sessionManager.writeCharacteristic(address, svcUuid, charUuid, bytes, writeType)
                    result.fold(
                        onSuccess = { ToolResult(definition.name, true, "Wrote ${bytes.size} bytes to $charUuid") },
                        onFailure = { errorResult("Write failed: ${it.message}") }
                    )
                }

                "subscribe" -> {
                    val address = requireAddress(params) ?: return@runCatching errorResult("Missing 'address'")
                    val svcUuid = requireUuid(params, "service_uuid") ?: return@runCatching errorResult("Missing 'service_uuid'")
                    val charUuid = requireUuid(params, "char_uuid") ?: return@runCatching errorResult("Missing 'char_uuid'")
                    sessionManager.setNotification(address, svcUuid, charUuid, true).fold(
                        onSuccess = { ToolResult(definition.name, true, "Subscribed to $charUuid on $address") },
                        onFailure = { errorResult("Subscribe failed: ${it.message}") }
                    )
                }

                "unsubscribe" -> {
                    val address = requireAddress(params) ?: return@runCatching errorResult("Missing 'address'")
                    val svcUuid = requireUuid(params, "service_uuid") ?: return@runCatching errorResult("Missing 'service_uuid'")
                    val charUuid = requireUuid(params, "char_uuid") ?: return@runCatching errorResult("Missing 'char_uuid'")
                    sessionManager.setNotification(address, svcUuid, charUuid, false).fold(
                        onSuccess = { ToolResult(definition.name, true, "Unsubscribed from $charUuid on $address") },
                        onFailure = { errorResult("Unsubscribe failed: ${it.message}") }
                    )
                }

                "read_notify" -> {
                    val address = requireAddress(params) ?: return@runCatching errorResult("Missing 'address'")
                    val session = sessionManager.getSession(address)
                        ?: return@runCatching errorResult("Not connected to $address")
                    val value = session.lastNotifyValue
                    if (value != null) {
                        ToolResult(definition.name, true,
                            "Last notification: ${value.toHex()}\nUTF-8: ${value.toString(Charsets.UTF_8)}")
                    } else {
                        ToolResult(definition.name, true, "No notification received yet")
                    }
                }

                "sessions" -> {
                    val all = sessionManager.getAllSessions()
                    if (all.isEmpty()) {
                        ToolResult(definition.name, true, "No active BLE sessions")
                    } else {
                        val sb = StringBuilder("Active BLE sessions (${all.size}):\n")
                        all.forEach { (addr, session) ->
                            val stateStr = when (session.state) {
                                BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
                                BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
                                BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
                                else -> "DISCONNECTED"
                            }
                            sb.appendLine("  $addr — $stateStr (${session.services.size} services)")
                        }
                        ToolResult(definition.name, true, sb.toString())
                    }
                }

                else -> errorResult("Unknown action: $action")
            }
        }.getOrElse { ToolResult(definition.name, false, "", it.message) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun scan(params: Map<String, Any?>, adapter: BluetoothAdapter?): ToolResult {
        if (adapter == null || !adapter.isEnabled) return errorResult("Bluetooth is off or unavailable")
        val scanner = adapter.bluetoothLeScanner ?: return errorResult("BLE scanner unavailable")

        val durationMs = (params["scan_duration_ms"]?.toString()?.toLongOrNull() ?: 5000L).coerceIn(1000, 15000)
        val filterName = params["filter_name"]?.toString()
        val filterSvcUuid = params["filter_service_uuid"]?.toString()

        val filters = mutableListOf<ScanFilter>()
        val filterBuilder = ScanFilter.Builder()
        if (!filterSvcUuid.isNullOrBlank()) {
            filterBuilder.setServiceUuid(ParcelUuid(UUID.fromString(filterSvcUuid)))
        }
        filters.add(filterBuilder.build())

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val results = mutableMapOf<String, ScanResult>()

        val scanResult = suspendCancellableCoroutine { cont ->
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    results[result.device.address] = result
                }
                override fun onScanFailed(errorCode: Int) {
                    if (cont.isActive) cont.resume(errorCode)
                }
            }

            scanner.startScan(filters, settings, callback)
            cont.invokeOnCancellation { scanner.stopScan(callback) }

            GlobalScope.launch {
                delay(durationMs)
                scanner.stopScan(callback)
                if (cont.isActive) cont.resume(0)
            }
        }

        if (scanResult != 0) return errorResult("Scan failed with error code $scanResult")

        var filtered = results.values.toList()
        if (!filterName.isNullOrBlank()) {
            filtered = filtered.filter { it.device.name?.startsWith(filterName, ignoreCase = true) == true }
        }
        filtered = filtered.sortedByDescending { it.rssi }

        val sb = StringBuilder("BLE Scan Results (${filtered.size} devices, ${durationMs}ms):\n")
        filtered.take(30).forEach { r ->
            val name = r.device.name ?: "(unknown)"
            val svcUuids = r.scanRecord?.serviceUuids?.joinToString(",") { it.uuid.toString() } ?: ""
            sb.appendLine("  $name | ${r.device.address} | RSSI=${r.rssi}dBm | Services=[$svcUuids]")
        }
        return ToolResult(definition.name, true, sb.toString())
    }

    private fun requireAddress(params: Map<String, Any?>): String? =
        params["address"]?.toString()?.uppercase()?.takeIf { it.matches(Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}")) }

    private fun requireUuid(params: Map<String, Any?>, key: String): UUID? =
        params[key]?.toString()?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun charPropsString(props: Int): String {
        val parts = mutableListOf<String>()
        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) parts.add("R")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) parts.add("W")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) parts.add("WNR")
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) parts.add("N")
        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) parts.add("I")
        if (props and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) parts.add("B")
        if (props and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) parts.add("SW")
        return parts.joinToString("|")
    }

    private fun scanModeName(mode: Int): String = when (mode) {
        BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE -> "DISCOVERABLE"
        BluetoothAdapter.SCAN_MODE_CONNECTABLE -> "CONNECTABLE"
        BluetoothAdapter.SCAN_MODE_NONE -> "NONE"
        else -> "UNKNOWN($mode)"
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray? {
        val clean = replace(" ", "").replace(":", "")
        if (clean.length % 2 != 0) return null
        return try {
            ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) { null }
    }

    private fun errorResult(msg: String) = ToolResult(definition.name, false, "", msg)
}
