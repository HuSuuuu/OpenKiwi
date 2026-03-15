package com.orizon.openkiwi.data.repository

import com.orizon.openkiwi.core.model.ChatMessage
import com.orizon.openkiwi.core.model.ChatRole
import com.orizon.openkiwi.data.local.dao.MessageDao
import com.orizon.openkiwi.data.local.dao.SessionDao
import com.orizon.openkiwi.data.local.entity.MessageEntity
import com.orizon.openkiwi.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ChatRepository(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao
) {
    fun getAllSessions(): Flow<List<SessionEntity>> = sessionDao.getAllSessions()

    suspend fun getAllSessionsOnce(): List<SessionEntity> = sessionDao.getAllSessionsOnce()

    suspend fun getSession(id: String): SessionEntity? = sessionDao.getSession(id)

    suspend fun createSession(
        modelConfigId: String = "",
        systemPrompt: String = "",
        title: String = "New Chat"
    ): String {
        val id = UUID.randomUUID().toString()
        sessionDao.insertSession(
            SessionEntity(
                id = id,
                title = title,
                modelConfigId = modelConfigId,
                systemPrompt = systemPrompt
            )
        )
        return id
    }

    suspend fun updateSessionTitle(id: String, title: String) {
        val session = sessionDao.getSession(id) ?: return
        sessionDao.updateSession(session.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteSession(id: String) {
        messageDao.deleteMessagesForSession(id)
        sessionDao.deleteSessionById(id)
    }

    fun getMessages(sessionId: String): Flow<List<ChatMessage>> =
        messageDao.getMessagesForSession(sessionId).map { entities ->
            entities.map { it.toChatMessage() }
        }

    suspend fun getMessagesOnce(sessionId: String): List<ChatMessage> =
        messageDao.getMessagesForSessionOnce(sessionId).map { it.toChatMessage() }

    suspend fun addMessage(sessionId: String, message: ChatMessage): Long {
        val entity = MessageEntity(
            sessionId = sessionId,
            role = message.role.name,
            content = message.content,
            toolCallsJson = null,
            toolCallId = message.toolCallId
        )
        val id = messageDao.insertMessage(entity)
        sessionDao.getSession(sessionId)?.let { session ->
            sessionDao.updateSession(session.copy(updatedAt = System.currentTimeMillis()))
        }
        return id
    }

    private fun MessageEntity.toChatMessage(): ChatMessage = ChatMessage(
        role = ChatRole.valueOf(role),
        content = content,
        toolCallId = toolCallId
    )
}
