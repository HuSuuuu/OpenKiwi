package com.orizon.openkiwi.core.agent

import com.orizon.openkiwi.core.model.ChatMessage
import java.util.UUID

enum class SessionStatus {
    IDLE, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
}

data class AgentSession(
    val id: String = UUID.randomUUID().toString(),
    val modelConfigId: String = "",
    val systemPrompt: String = "",
    val messages: MutableList<ChatMessage> = mutableListOf(),
    var status: SessionStatus = SessionStatus.IDLE,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    val metadata: MutableMap<String, String> = mutableMapOf()
)
