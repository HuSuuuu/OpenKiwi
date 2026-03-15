package com.orizon.openkiwi.core.tool.builtin

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import com.orizon.openkiwi.core.tool.*

class ConnectivityTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        name = "connectivity",
        description = "Check network status, WiFi info, Bluetooth devices, and connectivity state",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: status, wifi_info, wifi_scan, bluetooth_list, bluetooth_paired", true,
                enumValues = listOf("status", "wifi_info", "wifi_scan", "bluetooth_list", "bluetooth_paired"))
        ),
        requiredParams = listOf("action")
    )

    @Suppress("MissingPermission")
    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return errorResult("Missing action")
        return runCatching {
            when (action) {
                "status" -> {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val network = cm.activeNetwork
                    val caps = cm.getNetworkCapabilities(network)
                    val sb = StringBuilder("Network Status:\n")
                    if (caps == null) {
                        sb.appendLine("  No active network")
                    } else {
                        sb.appendLine("  WiFi: ${caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}")
                        sb.appendLine("  Cellular: ${caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}")
                        sb.appendLine("  VPN: ${caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)}")
                        sb.appendLine("  Internet: ${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            sb.appendLine("  Down: ${caps.linkDownstreamBandwidthKbps}kbps")
                            sb.appendLine("  Up: ${caps.linkUpstreamBandwidthKbps}kbps")
                        }
                    }
                    ToolResult(definition.name, true, sb.toString())
                }
                "wifi_info" -> {
                    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    @Suppress("DEPRECATION")
                    val info = wm.connectionInfo
                    val sb = StringBuilder("WiFi Info:\n")
                    sb.appendLine("  Enabled: ${wm.isWifiEnabled}")
                    @Suppress("DEPRECATION")
                    sb.appendLine("  SSID: ${info.ssid}")
                    sb.appendLine("  BSSID: ${info.bssid}")
                    sb.appendLine("  RSSI: ${info.rssi}dBm")
                    sb.appendLine("  Link Speed: ${info.linkSpeed}Mbps")
                    sb.appendLine("  Frequency: ${info.frequency}MHz")
                    @Suppress("DEPRECATION")
                    sb.appendLine("  IP: ${android.text.format.Formatter.formatIpAddress(info.ipAddress)}")
                    ToolResult(definition.name, true, sb.toString())
                }
                "wifi_scan" -> {
                    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    @Suppress("DEPRECATION")
                    val results = wm.scanResults
                    val sb = StringBuilder("WiFi Networks (${results.size}):\n")
                    results.sortedByDescending { it.level }.take(20).forEach { r ->
                        sb.appendLine("  ${r.SSID.ifBlank { "(hidden)" }} | ${r.level}dBm | ${r.frequency}MHz | ${r.BSSID}")
                    }
                    ToolResult(definition.name, true, sb.toString())
                }
                "bluetooth_paired" -> {
                    val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    val adapter = bm.adapter ?: return@runCatching errorResult("Bluetooth not available")
                    val sb = StringBuilder("Bluetooth Status: ${if (adapter.isEnabled) "ON" else "OFF"}\n")
                    val paired = adapter.bondedDevices
                    sb.appendLine("Paired Devices (${paired.size}):")
                    paired.forEach { d -> sb.appendLine("  ${d.name ?: "Unknown"} | ${d.address} | Type=${d.type}") }
                    ToolResult(definition.name, true, sb.toString())
                }
                "bluetooth_list" -> execute(mapOf("action" to "bluetooth_paired"))
                else -> errorResult("Unknown action: $action")
            }
        }.getOrElse { ToolResult(definition.name, false, "", it.message) }
    }
    private fun errorResult(msg: String) = ToolResult(definition.name, false, "", msg)
}
