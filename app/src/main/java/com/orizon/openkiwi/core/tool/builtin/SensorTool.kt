package com.orizon.openkiwi.core.tool.builtin

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.orizon.openkiwi.core.tool.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

class SensorTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        name = "sensor",
        description = "Read device sensor data: accelerometer, gyroscope, light, proximity, pressure, magnetic field",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.NORMAL.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: read, list", true, enumValues = listOf("read", "list")),
            "sensor_type" to ToolParamDef("string", "Sensor: accelerometer, gyroscope, light, proximity, pressure, magnetic, gravity, rotation",
                false, enumValues = listOf("accelerometer", "gyroscope", "light", "proximity", "pressure", "magnetic", "gravity", "rotation"))
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val action = params["action"]?.toString() ?: return errorResult("Missing action")
        return when (action) {
            "list" -> {
                val sensors = sm.getSensorList(Sensor.TYPE_ALL)
                val sb = StringBuilder("Available sensors (${sensors.size}):\n")
                sensors.forEach { s -> sb.appendLine("  ${s.name} [${s.stringType}] range=${s.maximumRange}") }
                ToolResult(definition.name, true, sb.toString())
            }
            "read" -> {
                val sensorType = params["sensor_type"]?.toString() ?: return errorResult("Missing sensor_type")
                val type = when (sensorType) {
                    "accelerometer" -> Sensor.TYPE_ACCELEROMETER
                    "gyroscope" -> Sensor.TYPE_GYROSCOPE
                    "light" -> Sensor.TYPE_LIGHT
                    "proximity" -> Sensor.TYPE_PROXIMITY
                    "pressure" -> Sensor.TYPE_PRESSURE
                    "magnetic" -> Sensor.TYPE_MAGNETIC_FIELD
                    "gravity" -> Sensor.TYPE_GRAVITY
                    "rotation" -> Sensor.TYPE_ROTATION_VECTOR
                    else -> return errorResult("Unknown sensor: $sensorType")
                }
                val sensor = sm.getDefaultSensor(type) ?: return errorResult("Sensor $sensorType not available")
                runCatching {
                    withTimeout(3000) {
                        val values = readSensor(sm, sensor)
                        val sb = StringBuilder("Sensor: ${sensor.name}\n")
                        sb.appendLine("Values: ${values.joinToString(", ") { "%.4f".format(it) }}")
                        when (type) {
                            Sensor.TYPE_ACCELEROMETER -> sb.appendLine("X=${values.getOrNull(0)} Y=${values.getOrNull(1)} Z=${values.getOrNull(2)} (m/s²)")
                            Sensor.TYPE_LIGHT -> sb.appendLine("Illuminance: ${values.getOrNull(0)} lux")
                            Sensor.TYPE_PROXIMITY -> sb.appendLine("Distance: ${values.getOrNull(0)} cm")
                            Sensor.TYPE_PRESSURE -> sb.appendLine("Pressure: ${values.getOrNull(0)} hPa")
                        }
                        ToolResult(definition.name, true, sb.toString())
                    }
                }.getOrElse { ToolResult(definition.name, false, "", "Sensor read timeout: ${it.message}") }
            }
            else -> errorResult("Unknown action: $action")
        }
    }

    private suspend fun readSensor(sm: SensorManager, sensor: Sensor): FloatArray =
        suspendCancellableCoroutine { cont ->
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    sm.unregisterListener(this)
                    if (cont.isActive) cont.resume(event.values.clone())
                }
                override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            cont.invokeOnCancellation { sm.unregisterListener(listener) }
        }

    private fun errorResult(msg: String) = ToolResult(definition.name, false, "", msg)
}
