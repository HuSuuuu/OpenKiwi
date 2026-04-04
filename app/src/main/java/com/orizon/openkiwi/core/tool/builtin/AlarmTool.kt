package com.orizon.openkiwi.core.tool.builtin

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import com.orizon.openkiwi.core.tool.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AlarmTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        name = "alarm",
        description = "Set alarms, timers, and countdowns via Android system alarm clock or AlarmManager",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef(
                "string", "Action to perform", true,
                enumValues = listOf("set_alarm", "set_timer", "dismiss_alarm", "snooze_alarm", "show_alarms", "show_timers")
            ),
            "hour" to ToolParamDef("string", "Hour (0-23) for set_alarm"),
            "minute" to ToolParamDef("string", "Minute (0-59) for set_alarm"),
            "message" to ToolParamDef("string", "Label/message for alarm or timer"),
            "days" to ToolParamDef("string", "Comma-separated days for repeating alarm: 1=Sun,2=Mon,...,7=Sat, e.g. '2,3,4,5,6'"),
            "seconds" to ToolParamDef("string", "Duration in seconds for set_timer"),
            "skip_ui" to ToolParamDef("string", "If 'true', skip the alarm app UI (default true)")
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return errorResult("Missing action")
        val skipUi = params["skip_ui"]?.toString() != "false"

        return runCatching {
            when (action) {
                "set_alarm" -> {
                    val hour = params["hour"]?.toString()?.toIntOrNull()
                        ?: return@runCatching errorResult("Missing or invalid 'hour' (0-23)")
                    val minute = params["minute"]?.toString()?.toIntOrNull() ?: 0
                    val message = params["message"]?.toString() ?: "OpenKiwi Alarm"

                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, message)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    val daysStr = params["days"]?.toString()
                    if (!daysStr.isNullOrBlank()) {
                        val dayList = daysStr.split(",").mapNotNull { it.trim().toIntOrNull() }
                            .filter { it in 1..7 }
                        if (dayList.isNotEmpty()) {
                            intent.putExtra(AlarmClock.EXTRA_DAYS, ArrayList(dayList))
                        }
                    }

                    context.startActivity(intent)
                    val dayNames = arrayOf("", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                    val repeatInfo = params["days"]?.toString()?.let { d ->
                        val names = d.split(",").mapNotNull { it.trim().toIntOrNull() }
                            .filter { it in 1..7 }.joinToString(",") { dayNames[it] }
                        " (repeat: $names)"
                    } ?: ""
                    ToolResult(definition.name, true,
                        "Alarm set for %02d:%02d — %s%s".format(hour, minute, message, repeatInfo))
                }
                "set_timer" -> {
                    val seconds = params["seconds"]?.toString()?.toIntOrNull()
                        ?: return@runCatching errorResult("Missing or invalid 'seconds'")
                    val message = params["message"]?.toString() ?: "OpenKiwi Timer"
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                        putExtra(AlarmClock.EXTRA_MESSAGE, message)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult(definition.name, true, "Timer set for ${seconds}s — $message")
                }
                "dismiss_alarm" -> {
                    val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult(definition.name, true, "Dismiss alarm intent sent")
                }
                "snooze_alarm" -> {
                    val intent = Intent(AlarmClock.ACTION_SNOOZE_ALARM).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult(definition.name, true, "Snooze alarm intent sent")
                }
                "show_alarms" -> {
                    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult(definition.name, true, "Opened alarms list")
                }
                "show_timers" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val intent = Intent(AlarmClock.ACTION_SHOW_TIMERS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        ToolResult(definition.name, true, "Opened timers list")
                    } else {
                        errorResult("show_timers requires API 26+")
                    }
                }
                else -> errorResult("Unknown action: $action")
            }
        }.getOrElse { ToolResult(definition.name, false, "", it.message) }
    }

    private fun errorResult(msg: String) = ToolResult(definition.name, false, "", msg)
}
