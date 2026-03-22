package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.schedule.ScheduleManager
import com.orizon.openkiwi.core.tool.PermissionLevel
import com.orizon.openkiwi.core.tool.Tool
import com.orizon.openkiwi.core.tool.ToolCategory
import com.orizon.openkiwi.core.tool.ToolDefinition
import com.orizon.openkiwi.core.tool.ToolParamDef
import com.orizon.openkiwi.core.tool.ToolResult
import com.orizon.openkiwi.data.local.dao.ScheduledTaskDao
import com.orizon.openkiwi.data.local.entity.ScheduledTaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Lets the agent manage periodic WorkManager tasks that run [com.orizon.openkiwi.core.schedule.ScheduleWorker].
 * Interval is coerced to at least [ScheduleManager.MIN_INTERVAL_MINUTES] (Android constraint).
 */
class ScheduledTaskTool(
    private val dao: ScheduledTaskDao,
    private val scheduleManager: ScheduleManager
) : Tool {

    override val definition = ToolDefinition(
        name = "scheduled_task",
        description = """Manage periodic scheduled tasks on this device. Each task stores a natural-language prompt that is sent to the OpenKiwi agent on a fixed interval (WorkManager periodic work).
Actions: list (all tasks), add (create), remove (delete by id), set_enabled (enable/disable), update (change name/prompt/interval by id).
Minimum interval is ${ScheduleManager.MIN_INTERVAL_MINUTES} minutes (Android system limit). Use task_id from list output for remove/update/set_enabled.""",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef(
                "string",
                "One of: list, add, remove, set_enabled, update",
                true,
                enumValues = listOf("list", "add", "remove", "set_enabled", "update")
            ),
            "task_id" to ToolParamDef("string", "Task id (UUID) from list; required for remove, set_enabled, update"),
            "name" to ToolParamDef("string", "Short label; required for add; optional for update"),
            "prompt" to ToolParamDef("string", "User message sent to the agent each run; required for add; optional for update"),
            "interval_minutes" to ToolParamDef(
                "string",
                "Repeat interval in minutes (integer as string); default 60 for add; min ${ScheduleManager.MIN_INTERVAL_MINUTES}",
                false,
                "60"
            ),
            "enabled" to ToolParamDef("string", "For set_enabled: true or false", false)
        ),
        requiredParams = listOf("action"),
        timeoutMs = 60_000L
    )

    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override suspend fun execute(params: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val action = params["action"]?.toString()?.trim()?.lowercase()
            ?: return@withContext fail("Missing action")

        when (action) {
            "list" -> listTasks()
            "add" -> addTask(params)
            "remove" -> removeTask(params)
            "set_enabled" -> setEnabled(params)
            "update" -> updateTask(params)
            else -> fail("Unknown action: $action")
        }
    }

    private fun fail(msg: String) = ToolResult(
        toolName = definition.name,
        success = false,
        output = "",
        error = msg
    )

    private suspend fun listTasks(): ToolResult {
        val tasks = dao.getAllOnce()
        if (tasks.isEmpty()) {
            return ToolResult(
                toolName = definition.name,
                success = true,
                output = "No scheduled tasks. Use action=add to create one."
            )
        }
        val sb = StringBuilder("Scheduled tasks (${tasks.size}):\n")
        tasks.forEach { t ->
            sb.appendLine("—".repeat(36))
            sb.appendLine("task_id: ${t.id}")
            sb.appendLine("name: ${t.name}")
            sb.appendLine("enabled: ${t.enabled}")
            sb.appendLine("interval_minutes: ${t.intervalMinutes}")
            sb.appendLine("prompt: ${t.prompt.take(500)}${if (t.prompt.length > 500) "…" else ""}")
            sb.appendLine(
                "last_run: " + if (t.lastRunAt > 0) timeFmt.format(Date(t.lastRunAt)) else "(never)"
            )
        }
        return ToolResult(toolName = definition.name, success = true, output = sb.toString())
    }

    private suspend fun addTask(params: Map<String, Any?>): ToolResult {
        val name = params["name"]?.toString()?.trim().orEmpty()
        val prompt = params["prompt"]?.toString()?.trim().orEmpty()
        if (prompt.isEmpty()) {
            return fail("add requires non-empty prompt")
        }
        val interval = params["interval_minutes"]?.toString()?.toLongOrNull()
            ?: definition.parameters["interval_minutes"]?.defaultValue?.toLongOrNull()
            ?: 60L
        val safeInterval = interval.coerceAtLeast(ScheduleManager.MIN_INTERVAL_MINUTES)
        val id = UUID.randomUUID().toString()
        val entity = ScheduledTaskEntity(
            id = id,
            name = name.ifBlank { "定时任务" },
            prompt = prompt,
            intervalMinutes = safeInterval,
            enabled = true
        )
        dao.insert(entity)
        scheduleManager.enqueueOrUpdate(entity)
        return ToolResult(
            toolName = definition.name,
            success = true,
            output = "Created scheduled task task_id=$id name=${entity.name} interval_minutes=$safeInterval. It will run the prompt with the agent on each interval."
        )
    }

    private suspend fun removeTask(params: Map<String, Any?>): ToolResult {
        val id = params["task_id"]?.toString()?.trim().orEmpty()
        if (id.isEmpty()) return fail("remove requires task_id")
        val existing = dao.getById(id) ?: return fail("No task with task_id=$id")
        scheduleManager.cancel(id)
        dao.delete(existing)
        return ToolResult(
            toolName = definition.name,
            success = true,
            output = "Removed task task_id=$id (${existing.name})."
        )
    }

    private suspend fun setEnabled(params: Map<String, Any?>): ToolResult {
        val id = params["task_id"]?.toString()?.trim().orEmpty()
        if (id.isEmpty()) return fail("set_enabled requires task_id")
        val en = parseBool(params["enabled"]?.toString())
            ?: return fail("set_enabled requires enabled=true or enabled=false")
        val existing = dao.getById(id) ?: return fail("No task with task_id=$id")
        val updated = existing.copy(enabled = en)
        dao.update(updated)
        if (en) scheduleManager.enqueueOrUpdate(updated) else scheduleManager.cancel(id)
        return ToolResult(
            toolName = definition.name,
            success = true,
            output = "Task task_id=$id (${existing.name}) enabled=$en."
        )
    }

    private suspend fun updateTask(params: Map<String, Any?>): ToolResult {
        val id = params["task_id"]?.toString()?.trim().orEmpty()
        if (id.isEmpty()) return fail("update requires task_id")
        val existing = dao.getById(id) ?: return fail("No task with task_id=$id")
        val newName = params["name"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val newPrompt = params["prompt"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val intervalStr = params["interval_minutes"]?.toString()?.trim()
        val newInterval = intervalStr?.toLongOrNull()?.coerceAtLeast(ScheduleManager.MIN_INTERVAL_MINUTES)
        val updated = existing.copy(
            name = newName ?: existing.name,
            prompt = newPrompt ?: existing.prompt,
            intervalMinutes = newInterval ?: existing.intervalMinutes
        )
        dao.update(updated)
        if (updated.enabled) scheduleManager.enqueueOrUpdate(updated)
        return ToolResult(
            toolName = definition.name,
            success = true,
            output = "Updated task_id=$id: name=${updated.name}, interval_minutes=${updated.intervalMinutes}, prompt length=${updated.prompt.length}."
        )
    }

    private fun parseBool(s: String?): Boolean? = when (s?.trim()?.lowercase()) {
        "true", "1", "yes", "on" -> true
        "false", "0", "no", "off" -> false
        else -> null
    }
}
