package com.orizon.openkiwi.core.tool.builtin

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.orizon.openkiwi.core.tool.*

class HapticsTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        name = "haptics",
        description = "Control device vibration: single pulse, pattern, or predefined effects (click, tick, heavy_click, double_click)",
        category = ToolCategory.DEVICE.name,
        permissionLevel = PermissionLevel.NORMAL.name,
        parameters = mapOf(
            "action" to ToolParamDef(
                "string", "Action to perform", true,
                enumValues = listOf("vibrate", "pattern", "effect", "cancel", "has_vibrator")
            ),
            "duration_ms" to ToolParamDef("string", "Duration in milliseconds for 'vibrate' (default 200)"),
            "amplitude" to ToolParamDef("string", "Vibration amplitude 1-255 for 'vibrate' (default 128, requires API 26+)"),
            "pattern" to ToolParamDef("string", "Comma-separated wait,vibrate,wait,vibrate... pattern in ms, e.g. '0,100,50,100,50,200'"),
            "repeat" to ToolParamDef("string", "Repeat index for pattern (-1 = no repeat, default -1)"),
            "effect_type" to ToolParamDef(
                "string", "Predefined effect type for 'effect' action",
                enumValues = listOf("click", "tick", "heavy_click", "double_click")
            )
        ),
        requiredParams = listOf("action")
    )

    @Suppress("DEPRECATION")
    private fun vibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    @Suppress("DEPRECATION")
    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return errorResult("Missing action")
        val vib = vibrator()

        return runCatching {
            when (action) {
                "has_vibrator" -> {
                    ToolResult(definition.name, true, "Has vibrator: ${vib.hasVibrator()}")
                }
                "vibrate" -> {
                    val ms = params["duration_ms"]?.toString()?.toLongOrNull() ?: 200L
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val amp = params["amplitude"]?.toString()?.toIntOrNull()?.coerceIn(1, 255) ?: 128
                        vib.vibrate(VibrationEffect.createOneShot(ms, amp))
                    } else {
                        vib.vibrate(ms)
                    }
                    ToolResult(definition.name, true, "Vibrated for ${ms}ms")
                }
                "pattern" -> {
                    val raw = params["pattern"]?.toString() ?: return@runCatching errorResult("Missing 'pattern'")
                    val timings = raw.split(",").mapNotNull { it.trim().toLongOrNull() }.toLongArray()
                    if (timings.size < 2) return@runCatching errorResult("Pattern needs at least 2 values (wait,vibrate)")
                    val repeat = params["repeat"]?.toString()?.toIntOrNull() ?: -1
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createWaveform(timings, repeat))
                    } else {
                        vib.vibrate(timings, repeat)
                    }
                    ToolResult(definition.name, true, "Vibration pattern started (${timings.size} segments)")
                }
                "effect" -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        return@runCatching errorResult("Predefined effects require API 29+")
                    }
                    val effectId = when (params["effect_type"]?.toString()) {
                        "click" -> VibrationEffect.EFFECT_CLICK
                        "tick" -> VibrationEffect.EFFECT_TICK
                        "heavy_click" -> VibrationEffect.EFFECT_HEAVY_CLICK
                        "double_click" -> VibrationEffect.EFFECT_DOUBLE_CLICK
                        else -> return@runCatching errorResult("Unknown effect_type, use: click, tick, heavy_click, double_click")
                    }
                    vib.vibrate(VibrationEffect.createPredefined(effectId))
                    ToolResult(definition.name, true, "Played effect: ${params["effect_type"]}")
                }
                "cancel" -> {
                    vib.cancel()
                    ToolResult(definition.name, true, "Vibration cancelled")
                }
                else -> errorResult("Unknown action: $action")
            }
        }.getOrElse { ToolResult(definition.name, false, "", it.message) }
    }

    private fun errorResult(msg: String) = ToolResult(definition.name, false, "", msg)
}
