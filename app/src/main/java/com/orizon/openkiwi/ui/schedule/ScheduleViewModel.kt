package com.orizon.openkiwi.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orizon.openkiwi.core.schedule.ScheduleManager
import com.orizon.openkiwi.data.local.dao.ScheduledTaskDao
import com.orizon.openkiwi.data.local.entity.ScheduledTaskEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class ScheduleViewModel(
    private val dao: ScheduledTaskDao,
    private val scheduleManager: ScheduleManager
) : ViewModel() {

    val tasks = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTask(name: String, prompt: String, intervalMinutes: Long) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val safeInterval = intervalMinutes.coerceAtLeast(ScheduleManager.MIN_INTERVAL_MINUTES)
            val entity = ScheduledTaskEntity(
                id = id,
                name = name.trim().ifBlank { "定时任务" },
                prompt = prompt.trim(),
                intervalMinutes = safeInterval,
                enabled = true
            )
            dao.insert(entity)
            scheduleManager.enqueueOrUpdate(entity)
        }
    }

    fun setEnabled(task: ScheduledTaskEntity, enabled: Boolean) {
        viewModelScope.launch {
            val updated = task.copy(enabled = enabled)
            dao.update(updated)
            if (enabled) scheduleManager.enqueueOrUpdate(updated) else scheduleManager.cancel(task.id)
        }
    }

    fun deleteTask(task: ScheduledTaskEntity) {
        viewModelScope.launch {
            scheduleManager.cancel(task.id)
            dao.delete(task)
        }
    }

    class Factory(
        private val dao: ScheduledTaskDao,
        private val scheduleManager: ScheduleManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScheduleViewModel(dao, scheduleManager) as T
        }
    }
}
