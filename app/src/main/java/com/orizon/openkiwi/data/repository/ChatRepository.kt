package com.orizon.openkiwi.data.repository

import com.orizon.openkiwi.core.model.ChatMessage
import com.orizon.openkiwi.core.model.ChatRole
import com.orizon.openkiwi.core.tool.ToolArtifact
import com.orizon.openkiwi.data.local.dao.ArtifactDao
import com.orizon.openkiwi.data.local.dao.MessageDao
import com.orizon.openkiwi.data.local.dao.SessionDao
import com.orizon.openkiwi.data.local.entity.ArtifactEntity
import com.orizon.openkiwi.data.local.entity.MessageEntity
import com.orizon.openkiwi.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

class ChatRepository(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val artifactDao: ArtifactDao
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
        artifactDao.deleteArtifactsForSession(id)
        sessionDao.deleteSessionById(id)
    }

    fun getMessages(sessionId: String): Flow<List<ChatMessage>> =
        combine(
            messageDao.getMessagesForSession(sessionId),
            artifactDao.getArtifactsForSession(sessionId)
        ) { entities, artifacts ->
            val byMessageId = artifacts
                .filter { it.messageId != null }
                .groupBy { it.messageId!! }
            var turnId = 0L
            entities.map { entity ->
                if (entity.role == ChatRole.USER.name) turnId++
                entity.toChatMessage(
                    turnId = turnId,
                    artifacts = byMessageId[entity.id].orEmpty()
                )
            }
        }

    suspend fun getMessagesOnce(sessionId: String): List<ChatMessage> {
        val entities = messageDao.getMessagesForSessionOnce(sessionId)
        val artifacts = artifactDao.getArtifactsForSessionOnce(sessionId)
            .filter { it.messageId != null }
            .groupBy { it.messageId!! }
        var turnId = 0L
        return entities.map { entity ->
            if (entity.role == ChatRole.USER.name) turnId++
            entity.toChatMessage(
                turnId = turnId,
                artifacts = artifacts[entity.id].orEmpty()
            )
        }
    }

    suspend fun addMessage(sessionId: String, message: ChatMessage): Long {
        val entity = MessageEntity(
            sessionId = sessionId,
            role = (message.role ?: ChatRole.ASSISTANT).name,
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

    suspend fun addMessages(sessionId: String, messages: List<ChatMessage>) {
        messages.forEach { addMessage(sessionId, it.copy(messageId = 0)) }
    }

    suspend fun saveToolArtifacts(
        sessionId: String,
        messageId: Long,
        toolName: String,
        artifacts: List<ToolArtifact>
    ) {
        if (artifacts.isEmpty()) return
        artifactDao.insertArtifacts(
            artifacts.map {
                ArtifactEntity(
                    sessionId = sessionId,
                    messageId = messageId,
                    toolName = toolName,
                    filePath = it.filePath,
                    displayName = it.displayName.ifBlank { java.io.File(it.filePath).name },
                    mimeType = it.mimeType,
                    sizeBytes = it.sizeBytes
                )
            }
        )
    }

    private fun MessageEntity.toChatMessage(
        turnId: Long,
        artifacts: List<ArtifactEntity>
    ): ChatMessage = ChatMessage(
        role = ChatRole.valueOf(role),
        content = content,
        toolCallId = toolCallId,
        messageId = id,
        turnId = turnId,
        artifacts = artifacts.map {
            com.orizon.openkiwi.core.model.ChatArtifact(
                id = it.id,
                sessionId = it.sessionId,
                messageId = it.messageId,
                toolName = it.toolName,
                filePath = it.filePath,
                displayName = it.displayName,
                mimeType = it.mimeType,
                sizeBytes = it.sizeBytes,
                createdAt = it.createdAt
            )
        }
    )
}
