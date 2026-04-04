package com.orizon.openkiwi.core.memory

import com.orizon.openkiwi.core.model.ChatMessage
import com.orizon.openkiwi.core.model.ChatRole
import com.orizon.openkiwi.data.local.dao.MemoryDao
import com.orizon.openkiwi.data.local.entity.MemoryEntity

class MemoryManager(private val memoryDao: MemoryDao) {

    private val bm25Engine = BM25Engine()
    private val vectorWeight = 0.7
    private val bm25Weight = 0.3

    suspend fun storeMemory(
        type: MemoryType,
        key: String,
        content: String,
        category: String = "",
        importance: Float = 0.5f,
        scope: String = ""
    ): Long {
        return memoryDao.insertMemory(
            MemoryEntity(
                type = type.name,
                category = category,
                key = key,
                content = content,
                importance = importance,
                scope = scope
            )
        )
    }

    /**
     * Hybrid search combining vector similarity (70%) and BM25 full-text (30%).
     */
    suspend fun searchMemories(query: String, limit: Int = 20, scope: String? = null): List<MemoryEntry> {
        val allMemories = if (scope != null) {
            memoryDao.searchMemoriesInScope(query, scope, limit * 3)
        } else {
            memoryDao.searchMemories(query, limit * 3)
        }

        if (allMemories.isEmpty()) return emptyList()

        val docs = allMemories.map { it.id to "${it.key} ${it.content}" }

        val vectorResults = VectorEngine.search(query, docs, limit * 2)
        val bm25Results = bm25Engine.search(query, docs, limit * 2)

        val vectorMax = vectorResults.maxOfOrNull { it.second } ?: 1.0
        val bm25Max = bm25Results.maxOfOrNull { it.second } ?: 1.0

        val mergedScores = mutableMapOf<Long, Double>()

        for ((id, score) in vectorResults) {
            val normalized = if (vectorMax > 0) score / vectorMax else 0.0
            mergedScores[id] = (mergedScores[id] ?: 0.0) + normalized * vectorWeight
        }
        for ((id, score) in bm25Results) {
            val normalized = if (bm25Max > 0) score / bm25Max else 0.0
            mergedScores[id] = (mergedScores[id] ?: 0.0) + normalized * bm25Weight
        }

        val entityMap = allMemories.associateBy { it.id }
        return mergedScores.entries
            .sortedByDescending { it.value }
            .take(limit)
            .mapNotNull { (id, _) ->
                entityMap[id]?.let { toMemoryEntry(it) }
            }
    }

    suspend fun searchByScope(scope: String, limit: Int = 50): List<MemoryEntry> {
        return memoryDao.getMemoriesByScope(scope, limit).map { toMemoryEntry(it) }
    }

    suspend fun deleteMemory(id: Long) {
        memoryDao.deleteMemory(id)
    }

    suspend fun clearMemories(type: MemoryType) {
        memoryDao.clearMemoriesByType(type.name)
    }

    suspend fun clearScope(scope: String) {
        memoryDao.clearMemoriesByScope(scope)
    }

    suspend fun compressContext(
        messages: List<ChatMessage>,
        maxTokenEstimate: Int = 4000,
        summarizer: (suspend (String) -> String)? = null
    ): List<ChatMessage> {
        val estimatedTokens = messages.sumOf { (it.content?.length ?: 0) / 4 + 10 }
        if (estimatedTokens <= maxTokenEstimate) return messages

        val systemMessages = messages.filter { it.role == ChatRole.SYSTEM }
        val recentCount = (messages.size * 0.3).toInt().coerceAtLeast(4)
        val recentMessages = messages.takeLast(recentCount)
        val middleMessages = messages.drop(systemMessages.size).dropLast(recentCount)

        return buildList {
            addAll(systemMessages)
            if (middleMessages.isNotEmpty()) {
                val summary = middleMessages.joinToString("\n") {
                    "[${(it.role ?: ChatRole.ASSISTANT).name}]: ${it.content?.take(100) ?: ""}"
                }
                add(ChatMessage(
                    role = ChatRole.SYSTEM,
                    content = "Previous conversation summary:\n$summary"
                ))
            }
            addAll(recentMessages)
        }
    }

    private fun toMemoryEntry(entity: MemoryEntity) = MemoryEntry(
        id = entity.id,
        type = runCatching { MemoryType.valueOf(entity.type) }.getOrDefault(MemoryType.LONG_TERM),
        key = entity.key,
        content = entity.content,
        category = entity.category,
        importance = entity.importance,
        accessCount = entity.accessCount,
        scope = entity.scope
    )
}

enum class MemoryType {
    SHORT_TERM, LONG_TERM, CONTEXTUAL
}

data class MemoryEntry(
    val id: Long,
    val type: MemoryType,
    val key: String,
    val content: String,
    val category: String = "",
    val importance: Float = 0.5f,
    val accessCount: Int = 0,
    val scope: String = ""
)
