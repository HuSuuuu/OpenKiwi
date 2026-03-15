package com.orizon.openkiwi.core.tool.builtin

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.orizon.openkiwi.core.tool.*

class PowerTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        name = "power",
        description = "Get battery status, power info, screen brightness, and power management state",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.NORMAL.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: battery, brightness, set_brightness, power_info", true,
                enumValues = listOf("battery", "brightness", "set_brightness", "power_info")),
            "value" to ToolParamDef("string", "Brightness value 0-255 for set_brightness")
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return errorResult("Missing action")
        return runCatching {
            when (action) {
                "battery" -> {
                    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                    val batteryStatus = context.registerReceiver(null, filter)
                    val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
                    val temp = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
                    val voltage = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000.0
                    val health = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
                    val sb = buildString {
                        appendLine("Battery: $level%")
                        appendLine("Charging: $charging (${when(plugged) { 1 -> "AC"; 2 -> "USB"; 4 -> "Wireless"; else -> "None" }})")
                        appendLine("Temperature: ${temp}°C")
                        appendLine("Voltage: ${voltage}V")
                        appendLine("Health: ${when(health) { 2 -> "Good"; 3 -> "Overheat"; 4 -> "Dead"; 5 -> "Over voltage"; else -> "Unknown" }}")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            appendLine("Current (avg): ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) / 1000}mA")
                        }
                    }
                    ToolResult(definition.name, true, sb)
                }
                "brightness" -> {
                    val brightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
                    ToolResult(definition.name, true, "Screen brightness: $brightness/255")
                }
                "set_brightness" -> {
                    val value = params["value"]?.toString()?.toIntOrNull() ?: return@runCatching errorResult("Missing value (0-255)")
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(0, 255))
                    ToolResult(definition.name, true, "Brightness set to $value")
                }
                "power_info" -> {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    val sb = buildString {
                        appendLine("Interactive (screen on): ${pm.isInteractive}")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            appendLine("Power save mode: ${pm.isPowerSaveMode}")
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            appendLine("Battery optimization ignored: ${pm.isIgnoringBatteryOptimizations(context.packageName)}")
                        }
                    }
                    ToolResult(definition.name, true, sb)
                }
                else -> errorResult("Unknown action: $action")
            }
        }.getOrElse { ToolResult(definition.name, false, "", it.message) }
    }
    private fun errorResult(msg: String) = ToolResult(definition.name, false, "", msg)
}
