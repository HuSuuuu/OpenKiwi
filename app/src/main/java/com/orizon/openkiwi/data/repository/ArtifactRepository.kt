package com.orizon.openkiwi.data.repository

import com.orizon.openkiwi.core.model.ChatArtifact
import com.orizon.openkiwi.data.local.dao.ArtifactDao
import com.orizon.openkiwi.data.local.entity.ArtifactEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ArtifactRepository(private val artifactDao: ArtifactDao) {
    fun getAllArtifacts(): Flow<List<ChatArtifact>> =
        artifactDao.getAllArtifacts().map { list -> list.map { it.toModel() } }

    fun getArtifactsForSession(sessionId: String): Flow<List<ChatArtifact>> =
        artifactDao.getArtifactsForSession(sessionId).map { list -> list.map { it.toModel() } }

    suspend fun getArtifactsForSessionOnce(sessionId: String): List<ChatArtifact> =
        artifactDao.getArtifactsForSessionOnce(sessionId).map { it.toModel() }

    private fun ArtifactEntity.toModel(): ChatArtifact = ChatArtifact(
        id = id,
        sessionId = sessionId,
        messageId = messageId,
        toolName = toolName,
        filePath = filePath,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        createdAt = createdAt
    )
}
