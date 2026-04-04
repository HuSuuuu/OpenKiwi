package com.orizon.openkiwi.core.schedule

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.orizon.openkiwi.OpenKiwiApp
import kotlinx.coroutines.flow.collect

class ScheduleWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val app = applicationContext as? OpenKiwiApp ?: return Result.failure()
        val container = app.container
        val dao = container.database.scheduledTaskDao()
        val task = dao.getById(taskId) ?: return Result.success()
        if (!task.enabled) return Result.success()

        return try {
            var sid = task.sessionId
            if (sid.isNullOrBlank()) {
                sid = container.chatRepository.createSession(title = "定时: ${task.name}")
                dao.updateSessionId(taskId, sid)
            }
            val sb = StringBuilder()
            container.agentEngine.processMessage(sid, task.prompt).collect { chunk ->
                if (!chunk.startsWith("§T§") && !chunk.startsWith("\n[Calling tool:")) {
                    sb.append(chunk)
                }
            }
            dao.updateLastRun(taskId, System.currentTimeMillis())

            val resultSummary = sb.toString().take(500).ifBlank { "任务已完成" }
            container.agentEngine.sendProactiveMessage(
                sid,
                "定时任务「${task.name}」执行完毕:\n$resultSummary",
                com.orizon.openkiwi.core.agent.ProactiveMessage.Source.SCHEDULE
            )

            Log.i(TAG, "ScheduleWorker done task=$taskId len=${sb.length}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "ScheduleWorker failed task=$taskId", e)
            Result.retry()
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        private const val TAG = "ScheduleWorker"
    }
}
