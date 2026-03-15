package com.orizon.openkiwi.core.agent

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class AgentMessage(
    val fromAgentId: String,
    val toAgentId: String,
    val type: MessageType,
    val payload: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageType {
    TASK_ASSIGN, TASK_RESULT, STATUS_UPDATE, DATA_SHARE, QUERY, RESPONSE
}

class AgentCommunicationBus {
    private val _messages = MutableSharedFlow<AgentMessage>(replay = 10, extraBufferCapacity = 50)
    val messages: SharedFlow<AgentMessage> = _messages.asSharedFlow()

    suspend fun send(message: AgentMessage) {
        _messages.emit(message)
    }

    suspend fun sendTask(fromId: String, toId: String, task: String) {
        send(AgentMessage(fromId, toId, MessageType.TASK_ASSIGN, task))
    }

    suspend fun sendResult(fromId: String, toId: String, result: String) {
        send(AgentMessage(fromId, toId, MessageType.TASK_RESULT, result))
    }

    suspend fun sendStatus(agentId: String, status: String) {
        send(AgentMessage(agentId, "broadcast", MessageType.STATUS_UPDATE, status))
    }

    suspend fun shareData(fromId: String, toId: String, data: String) {
        send(AgentMessage(fromId, toId, MessageType.DATA_SHARE, data))
    }
}
