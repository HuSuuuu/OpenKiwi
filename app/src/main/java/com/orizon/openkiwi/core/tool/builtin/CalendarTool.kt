package com.orizon.openkiwi.core.tool.builtin

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.orizon.openkiwi.core.tool.*
import java.text.SimpleDateFormat
import java.util.*

class CalendarTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        name = "calendar",
        description = "Read, add, update, and delete events in the system calendar. Use for scheduling meetings, reminders, appointments, etc.",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action to perform", true,
                enumValues = listOf("add_event", "query_events", "delete_event", "update_event", "list_calendars")),
            "title" to ToolParamDef("string", "Event title (for add/update)"),
            "start_time" to ToolParamDef("string", "Start time in ISO 8601 format: yyyy-MM-dd'T'HH:mm, e.g. 2026-03-29T17:00"),
            "end_time" to ToolParamDef("string", "End time in ISO 8601 format (defaults to start_time + 1 hour)"),
            "description" to ToolParamDef("string", "Event description/notes"),
            "location" to ToolParamDef("string", "Event location"),
            "reminder_minutes" to ToolParamDef("string", "Reminder before event in minutes (default 15)"),
            "event_id" to ToolParamDef("string", "Event ID (for delete/update)"),
            "start_date" to ToolParamDef("string", "Query range start: yyyy-MM-dd (for query_events)"),
            "end_date" to ToolParamDef("string", "Query range end: yyyy-MM-dd (for query_events)"),
            "query" to ToolParamDef("string", "Search keyword in event titles (for query_events)"),
            "calendar_id" to ToolParamDef("string", "Calendar ID (defaults to primary calendar)"),
            "limit" to ToolParamDef("string", "Max results for query (default 20)")
        ),
        requiredParams = listOf("action")
    )

    companion object {
        private val ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        private val DISPLAY_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return err("Missing action")
        return runCatching {
            when (action) {
                "add_event" -> addEvent(params)
                "query_events" -> queryEvents(params)
                "delete_event" -> deleteEvent(params)
                "update_event" -> updateEvent(params)
                "list_calendars" -> listCalendars()
                else -> err("Unknown action: $action")
            }
        }.getOrElse { ToolResult(definition.name, false, "", it.message) }
    }

    private fun getPrimaryCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.IS_PRIMARY
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val isPrimary = cursor.getInt(2)
                if (isPrimary == 1) return id
            }
            // Fallback: return first writable calendar
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
                arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                null
            )?.use { c2 ->
                if (c2.moveToFirst()) return c2.getLong(0)
            }
        }
        return null
    }

    private fun addEvent(params: Map<String, Any?>): ToolResult {
        val title = params["title"]?.toString() ?: return err("Missing title")
        val startStr = params["start_time"]?.toString() ?: return err("Missing start_time")
        val startMs = ISO_FORMAT.parse(startStr)?.time ?: return err("Invalid start_time format. Use: yyyy-MM-dd'T'HH:mm")
        val endMs = params["end_time"]?.toString()?.let {
            ISO_FORMAT.parse(it)?.time ?: return err("Invalid end_time format")
        } ?: (startMs + 3600_000L)
        val calId = params["calendar_id"]?.toString()?.toLongOrNull() ?: getPrimaryCalendarId()
            ?: return err("No writable calendar found on device")
        val reminderMin = params["reminder_minutes"]?.toString()?.toIntOrNull() ?: 15

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMs)
            put(CalendarContract.Events.DTEND, endMs)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            params["description"]?.toString()?.let { put(CalendarContract.Events.DESCRIPTION, it) }
            params["location"]?.toString()?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
        }
        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return err("Failed to insert event")
        val eventId = eventUri.lastPathSegment?.toLongOrNull() ?: -1

        if (reminderMin > 0) {
            val reminderValues = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, reminderMin)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        }

        val startDisplay = DISPLAY_FORMAT.format(Date(startMs))
        val endDisplay = DISPLAY_FORMAT.format(Date(endMs))
        return ToolResult(definition.name, true,
            "Event created successfully.\nID: $eventId\nTitle: $title\nStart: $startDisplay\nEnd: $endDisplay\nReminder: ${reminderMin}min before")
    }

    private fun queryEvents(params: Map<String, Any?>): ToolResult {
        val limit = params["limit"]?.toString()?.toIntOrNull() ?: 20
        val query = params["query"]?.toString()

        val cal = Calendar.getInstance()
        val startMs = params["start_date"]?.toString()?.let {
            DATE_FORMAT.parse(it)?.time
        } ?: cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis
        val endMs = params["end_date"]?.toString()?.let {
            DATE_FORMAT.parse(it)?.time?.plus(86400_000L)
        } ?: (startMs + 30L * 86400_000L)

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION
        )

        val selection = StringBuilder("${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?")
        val selArgs = mutableListOf(startMs.toString(), endMs.toString())
        if (!query.isNullOrBlank()) {
            selection.append(" AND ${CalendarContract.Events.TITLE} LIKE ?")
            selArgs.add("%$query%")
        }

        val events = mutableListOf<String>()
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI, projection,
            selection.toString(), selArgs.toTypedArray(),
            "${CalendarContract.Events.DTSTART} ASC"
        )?.use { cursor ->
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val id = cursor.getLong(0)
                val title = cursor.getString(1) ?: "(no title)"
                val start = cursor.getLong(2)
                val end = cursor.getLong(3)
                val location = cursor.getString(4) ?: ""
                val desc = cursor.getString(5) ?: ""
                val line = buildString {
                    append("[ID:$id] $title | ${DISPLAY_FORMAT.format(Date(start))} ~ ${DISPLAY_FORMAT.format(Date(end))}")
                    if (location.isNotBlank()) append(" @ $location")
                    if (desc.isNotBlank()) append(" | $desc")
                }
                events.add(line)
                count++
            }
        }

        return if (events.isEmpty()) {
            ToolResult(definition.name, true, "No events found in the specified range.")
        } else {
            ToolResult(definition.name, true, "Found ${events.size} events:\n${events.joinToString("\n")}")
        }
    }

    private fun deleteEvent(params: Map<String, Any?>): ToolResult {
        val eventId = params["event_id"]?.toString()?.toLongOrNull() ?: return err("Missing or invalid event_id")
        val uri = android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val deleted = context.contentResolver.delete(uri, null, null)
        return if (deleted > 0) {
            ToolResult(definition.name, true, "Event $eventId deleted successfully.")
        } else {
            err("Event $eventId not found or could not be deleted.")
        }
    }

    private fun updateEvent(params: Map<String, Any?>): ToolResult {
        val eventId = params["event_id"]?.toString()?.toLongOrNull() ?: return err("Missing or invalid event_id")
        val values = ContentValues()
        params["title"]?.toString()?.let { values.put(CalendarContract.Events.TITLE, it) }
        params["start_time"]?.toString()?.let { t ->
            ISO_FORMAT.parse(t)?.time?.let { values.put(CalendarContract.Events.DTSTART, it) }
        }
        params["end_time"]?.toString()?.let { t ->
            ISO_FORMAT.parse(t)?.time?.let { values.put(CalendarContract.Events.DTEND, it) }
        }
        params["description"]?.toString()?.let { values.put(CalendarContract.Events.DESCRIPTION, it) }
        params["location"]?.toString()?.let { values.put(CalendarContract.Events.EVENT_LOCATION, it) }

        if (values.size() == 0) return err("No fields to update")

        val uri = android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val updated = context.contentResolver.update(uri, values, null, null)
        return if (updated > 0) {
            ToolResult(definition.name, true, "Event $eventId updated successfully.")
        } else {
            err("Event $eventId not found or could not be updated.")
        }
    }

    private fun listCalendars(): ToolResult {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val calendars = mutableListOf<String>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val name = cursor.getString(1) ?: "?"
                val account = cursor.getString(2) ?: "?"
                val primary = cursor.getInt(3) == 1
                val access = cursor.getInt(4)
                val writable = access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                calendars.add("[ID:$id] $name ($account)${if (primary) " [PRIMARY]" else ""}${if (writable) " [writable]" else " [read-only]"}")
            }
        }
        return if (calendars.isEmpty()) {
            ToolResult(definition.name, true, "No calendars found on device.")
        } else {
            ToolResult(definition.name, true, "Calendars:\n${calendars.joinToString("\n")}")
        }
    }

    private fun err(msg: String) = ToolResult(definition.name, false, "", msg)
}
