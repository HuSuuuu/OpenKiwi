package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.device.USBHostManager
import com.orizon.openkiwi.core.tool.*

class USBTool(private val usbManager: USBHostManager) : Tool {
    override val definition = ToolDefinition(
        name = "usb",
        description = "List, connect to, and communicate with USB devices connected via Type-C/OTG",
        category = ToolCategory.DEVICE.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: list, refresh, info", true,
                enumValues = listOf("list", "refresh", "info")),
            "device_id" to ToolParamDef("string", "USB device ID")
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return errorResult("Missing action")
        return when (action) {
            "list", "refresh" -> {
                usbManager.refreshDevices()
                val devices = usbManager.devices.value
                if (devices.isEmpty()) ToolResult(definition.name, true, "No USB devices connected")
                else {
                    val sb = StringBuilder("USB Devices (${devices.size}):\n")
                    devices.forEach { d ->
                        sb.appendLine("  ${d.name} | VID=0x${d.vendorId.toString(16)} PID=0x${d.productId.toString(16)} | Class=${d.deviceClass} | Interfaces=${d.interfaceCount} | Permission=${d.hasPermission}")
                    }
                    ToolResult(definition.name, true, sb.toString())
                }
            }
            "info" -> {
                val deviceId = params["device_id"]?.toString()?.toIntOrNull() ?: return errorResult("Missing device_id")
                val device = usbManager.getUsbDevice(deviceId) ?: return errorResult("Device $deviceId not found")
                val sb = buildString {
                    appendLine("USB Device: ${device.deviceName}")
                    appendLine("Vendor ID: 0x${device.vendorId.toString(16)}")
                    appendLine("Product ID: 0x${device.productId.toString(16)}")
                    appendLine("Class: ${device.deviceClass}")
                    appendLine("Subclass: ${device.deviceSubclass}")
                    appendLine("Protocol: ${device.deviceProtocol}")
                    appendLine("Interfaces: ${device.interfaceCount}")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        appendLine("Manufacturer: ${device.manufacturerName ?: "N/A"}")
                        appendLine("Product: ${device.productName ?: "N/A"}")
                        appendLine("Serial: ${device.serialNumber ?: "N/A"}")
                    }
                }
                ToolResult(definition.name, true, sb)
            }
            else -> errorResult("Unknown action: $action")
        }
    }
    private fun errorResult(msg: String) = ToolResult(definition.name, false, "", msg)
}
