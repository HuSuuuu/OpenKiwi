package com.orizon.openkiwi.core.tool.builtin

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import com.orizon.openkiwi.core.reminder.ReminderManager
import com.orizon.openkiwi.core.tool.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReminderTool(
    private val context: Context,
    private val reminderManager: ReminderManager
) : Tool {
    override val definition = ToolDefinition(
        name = "reminder",
        description = "App reminders (notification + chat) OR system Clock/Calendar: use system_alarm/system_timer for system alarm clock; open_calendar for system calendar UI; use calendar tool for silent calendar insert with reminder_minutes.",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.NORMAL.name,
        parameters = mapOf(
            "action" to ToolParamDef(
                "string", "Action to perform", true,
                enumValues = listOf(
                    "set", "set_delay", "list", "cancel", "cleanup",
                    "system_alarm", "system_timer", "open_calendar"
                )
            ),
            "message" to ToolParamDef("string", "Reminder message text"),
            "time" to ToolParamDef("string", "Target time in HH:mm format (24h) for today/tomorrow, or yyyy-MM-dd HH:mm for a specific date"),
            "delay_minutes" to ToolParamDef("string", "Minutes from now (for set_delay action)"),
            "repeat_minutes" to ToolParamDef("string", "Repeat interval in minutes (0 = one-shot, default)"),
            "id" to ToolParamDef("string", "Reminder ID (for cancel action)"),
            "hour" to ToolParamDef("string", "0-23 for system_alarm"),
            "minute" to ToolParamDef("string", "0-59 for system_alarm"),
            "seconds" to ToolParamDef("string", "Countdown seconds for system_timer"),
            "skip_ui" to ToolParamDef("string", "For system_alarm: 'true' to set without opening clock UI (default true)")
        )
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        return when (params["action"]?.toString()) {
            "set" -> setByTime(params)
            "set_delay" -> setByDelay(params)
            "list" -> list()
            "cancel" -> cancel(params)
            "cleanup" -> cleanup()
            "system_alarm" -> systemAlarm(params)
            "system_timer" -> systemTimer(params)
            "open_calendar" -> openCalendarUi(params)
            else -> errorResult("Unknown action. Use: set, set_delay, list, cancel, cleanup, system_alarm, system_timer, open_calendar")
        }
    }

    private fun systemAlarm(params: Map<String, Any?>): ToolResult {
        val hour = params["hour"]?.toString()?.toIntOrNull()
            ?: return errorResult("system_alarm 需要 hour (0-23)")
        val minute = params["minute"]?.toString()?.toIntOrNull() ?: 0
        val message = params["message"]?.toString() ?: "提醒"
        val skipUi = params["skip_ui"]?.toString() != "false"
        return runCatching {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            successResult("已调用系统时钟设置闹钟: %02d:%02d — %s".format(hour, minute, message))
        }.getOrElse { errorResult("无法打开系统闹钟: ${it.message}") }
    }

    private fun systemTimer(params: Map<String, Any?>): ToolResult {
        val seconds = params["seconds"]?.toString()?.toIntOrNull()
            ?: return errorResult("system_timer 需要 seconds（正整数）")
        val message = params["message"]?.toString() ?: "倒计时"
        return runCatching {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            successResult("已调用系统时钟设置倒计时: ${seconds} 秒 — $message")
        }.getOrElse { errorResult("无法打开系统倒计时: ${it.message}") }
    }

    private fun openCalendarUi(params: Map<String, Any?>): ToolResult {
        val title = params["message"]?.toString() ?: return errorResult("open_calendar 需要 message 作为日程标题")
        val timeStr = params["time"]?.toString() ?: return errorResult("Missing time (HH:mm or yyyy-MM-dd HH:mm)")
        val startMs = parseTime(timeStr) ?: return errorResult("Cannot parse time: $timeStr")
        val endMs = startMs + 3600_000L
        return runCatching {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
                putExtra(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            successResult("已打开系统日历新建界面: \"$title\"（${ReminderManager.formatTime(startMs)}），用户可在界面中保存并设置提醒方式")
        }.getOrElse { errorResult("无法打开日历: ${it.message}") }
    }

    private suspend fun setByTime(params: Map<String, Any?>): ToolResult {
        val message = params["message"]?.toString() ?: return errorResult("Missing 'message'")
        val timeStr = params["time"]?.toString() ?: return errorResult("Missing 'time' (HH:mm or yyyy-MM-dd HH:mm)")
        val repeatMin = params["repeat_minutes"]?.toString()?.toLongOrNull() ?: 0

        val triggerMs = parseTime(timeStr) ?: return errorResult("Cannot parse time: '$timeStr'. Use HH:mm or yyyy-MM-dd HH:mm")

        if (triggerMs <= System.currentTimeMillis()) {
            return errorResult("Time is in the past. Current: ${ReminderManager.formatTime(System.currentTimeMillis())}")
        }

        val entity = reminderManager.schedule(
            message = message,
            triggerAtMs = triggerMs,
            repeatIntervalMs = repeatMin * 60_000L
        )

        val repeatText = if (repeatMin > 0) "，每${repeatMin}分钟重复" else ""
        return successResult("提醒已设置: \"$message\"\n时间: ${ReminderManager.formatTime(triggerMs)}$repeatText\nID: ${entity.id}")
    }

    private suspend fun setByDelay(params: Map<String, Any?>): ToolResult {
        val message = params["message"]?.toString() ?: return errorResult("Missing 'message'")
        val delayMin = params["delay_minutes"]?.toString()?.toLongOrNull()
            ?: return errorResult("Missing 'delay_minutes'")
        val repeatMin = params["repeat_minutes"]?.toString()?.toLongOrNull() ?: 0

        if (delayMin <= 0) return errorResult("delay_minutes must be > 0")

        val triggerMs = System.currentTimeMillis() + delayMin * 60_000L
        val entity = reminderManager.schedule(
            message = message,
            triggerAtMs = triggerMs,
            repeatIntervalMs = repeatMin * 60_000L
        )

        val repeatText = if (repeatMin > 0) "，每${repeatMin}分钟重复" else ""
        return successResult("提醒已设置: \"$message\"\n${delayMin}分钟后 (${ReminderManager.formatTime(triggerMs)})$repeatText\nID: ${entity.id}")
    }

    private suspend fun list(): ToolResult {
        val all = reminderManager.listAll()
        if (all.isEmpty()) return successResult("当前没有提醒")

        val now = System.currentTimeMillis()
        val sb = StringBuilder("共 ${all.size} 条提醒:\n")
        all.forEach { r ->
            val status = when {
                !r.enabled -> "已完成"
                r.triggerAtMs > now -> "等待中"
                else -> "已过期"
            }
            sb.append("• [${r.id}] \"${r.message}\" → ${ReminderManager.formatTime(r.triggerAtMs)} ($status)")
            if (r.repeatIntervalMs > 0) sb.append(" 🔁每${r.repeatIntervalMs / 60000}分钟")
            sb.append("\n")
        }
        return successResult(sb.toString())
    }

    private suspend fun cancel(params: Map<String, Any?>): ToolResult {
        val id = params["id"]?.toString() ?: return errorResult("Missing 'id'")
        return if (reminderManager.cancel(id)) {
            successResult("已取消提醒: $id")
        } else {
            errorResult("未找到提醒: $id")
        }
    }

    private suspend fun cleanup(): ToolResult {
        reminderManager.cleanup()
        return successResult("已清理过期提醒")
    }

    private fun parseTime(s: String): Long? {
        val fullFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        runCatching { fullFormat.parse(s) }.getOrNull()?.let { return it.time }

        val shortFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        runCatching { shortFormat.parse(s) }.getOrNull()?.let { parsed ->
            val cal = Calendar.getInstance()
            val parsedCal = Calendar.getInstance().apply { time = parsed }
            cal.set(Calendar.HOUR_OF_DAY, parsedCal.get(Calendar.HOUR_OF_DAY))
            cal.set(Calendar.MINUTE, parsedCal.get(Calendar.MINUTE))
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
            return cal.timeInMillis
        }

        return null
    }

    private fun successResult(msg: String) = ToolResult("reminder", true, msg)
    private fun errorResult(msg: String) = ToolResult("reminder", false, "", error = msg)
}
