package com.orizon.openkiwi.core.security

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

object EmergencyStop {
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val activeJobs = ConcurrentLinkedQueue<Job>()
    private val stopCallbacks = ConcurrentLinkedQueue<() -> Unit>()

    fun registerJob(job: Job) { activeJobs.add(job) }
    fun unregisterJob(job: Job) { activeJobs.remove(job) }
    fun registerStopCallback(callback: () -> Unit) { stopCallbacks.add(callback) }

    fun activate() {
        _isActive.value = true
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
        stopCallbacks.forEach { runCatching { it() } }
    }

    fun reset() {
        _isActive.value = false
    }

    fun isRunning(): Boolean = activeJobs.any { it.isActive }
}
