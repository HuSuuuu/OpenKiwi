package com.orizon.openkiwi.core.agent

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class ProactiveMessage(
    val sessionId: String?,
    val content: String,
    val source: Source,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Source {
        NOTIFICATION,
        SCHEDULE,
        REFLECTION,
        AGENT_INSIGHT,
        SYSTEM
    }

    val sourceName: String get() = when (source) {
        Source.NOTIFICATION -> "通知"
        Source.SCHEDULE -> "定时任务"
        Source.REFLECTION -> "反思"
        Source.AGENT_INSIGHT -> "主动发现"
        Source.SYSTEM -> "系统"
    }
}

object ProactiveMessageBus {
    private val _messages = MutableSharedFlow<ProactiveMessage>(extraBufferCapacity = 32)
    val messages: SharedFlow<ProactiveMessage> = _messages.asSharedFlow()

    fun emit(message: ProactiveMessage) {
        _messages.tryEmit(message)
    }

    fun emit(sessionId: String?, content: String, source: ProactiveMessage.Source) {
        _messages.tryEmit(ProactiveMessage(sessionId, content, source))
    }
}
