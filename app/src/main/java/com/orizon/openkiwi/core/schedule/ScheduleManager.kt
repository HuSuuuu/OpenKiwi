package com.orizon.openkiwi.core.schedule

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.orizon.openkiwi.data.local.dao.ScheduledTaskDao
import com.orizon.openkiwi.data.local.entity.ScheduledTaskEntity
import java.util.concurrent.TimeUnit

class ScheduleManager(private val context: Context) {

    private fun uniqueName(taskId: String) = "openkiwi_sched_$taskId"

    fun enqueueOrUpdate(task: ScheduledTaskEntity) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(uniqueName(task.id))
        if (!task.enabled) return
        val minutes = task.intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES)
        val req = PeriodicWorkRequestBuilder<ScheduleWorker>(minutes, TimeUnit.MINUTES)
            .setInputData(workDataOf(ScheduleWorker.KEY_TASK_ID to task.id))
            .addTag(WORK_TAG)
            .build()
        wm.enqueueUniquePeriodicWork(uniqueName(task.id), ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun cancel(taskId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(taskId))
    }

    suspend fun syncAll(dao: ScheduledTaskDao) {
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag(WORK_TAG)
        dao.getAllEnabled().forEach { enqueueOrUpdate(it) }
    }

    companion object {
        const val MIN_INTERVAL_MINUTES = 15L
        private const val WORK_TAG = "openkiwi_schedule"
    }
}
