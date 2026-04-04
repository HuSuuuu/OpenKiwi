package com.orizon.openkiwi.data.local.dao

import androidx.room.*
import com.orizon.openkiwi.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Delete
    suspend fun deleteSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSessionById(id: String)

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    suspend fun getAllSessionsOnce(): List<SessionEntity>
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSessionOnce(sessionId: String): List<MessageEntity>

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Insert
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)
}

@Dao
interface ArtifactDao {
    @Query("SELECT * FROM artifacts ORDER BY createdAt DESC")
    fun getAllArtifacts(): Flow<List<ArtifactEntity>>

    @Query("SELECT * FROM artifacts WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun getArtifactsForSession(sessionId: String): Flow<List<ArtifactEntity>>

    @Query("SELECT * FROM artifacts WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    suspend fun getArtifactsForSessionOnce(sessionId: String): List<ArtifactEntity>

    @Insert
    suspend fun insertArtifact(artifact: ArtifactEntity): Long

    @Insert
    suspend fun insertArtifacts(artifacts: List<ArtifactEntity>): List<Long>

    @Query("UPDATE artifacts SET messageId = :messageId WHERE id IN (:artifactIds)")
    suspend fun attachArtifactsToMessage(artifactIds: List<Long>, messageId: Long)

    @Query("DELETE FROM artifacts WHERE sessionId = :sessionId")
    suspend fun deleteArtifactsForSession(sessionId: String)
}

@Dao
interface ModelConfigDao {
    @Query("SELECT * FROM model_configs ORDER BY name ASC")
    fun getAllConfigs(): Flow<List<ModelConfigEntity>>

    @Query("SELECT * FROM model_configs WHERE id = :id")
    suspend fun getConfig(id: String): ModelConfigEntity?

    @Query("SELECT * FROM model_configs WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultConfig(): ModelConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ModelConfigEntity)

    @Update
    suspend fun updateConfig(config: ModelConfigEntity)

    @Delete
    suspend fun deleteConfig(config: ModelConfigEntity)

    @Query("UPDATE model_configs SET isDefault = 0")
    suspend fun clearDefaultFlags()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE type = :type ORDER BY lastAccessedAt DESC")
    fun getMemoriesByType(type: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE `key` LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY importance DESC LIMIT :limit")
    suspend fun searchMemories(query: String, limit: Int = 20): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemory(id: Long)

    @Query("DELETE FROM memories WHERE type = :type")
    suspend fun clearMemoriesByType(type: String)

    @Query("SELECT * FROM memories WHERE scope = :scope ORDER BY importance DESC LIMIT :limit")
    suspend fun getMemoriesByScope(scope: String, limit: Int = 50): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE (`key` LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') AND scope = :scope ORDER BY importance DESC LIMIT :limit")
    suspend fun searchMemoriesInScope(query: String, scope: String, limit: Int = 20): List<MemoryEntity>

    @Query("DELETE FROM memories WHERE scope = :scope")
    suspend fun clearMemoriesByScope(scope: String)

    @Query("SELECT * FROM memories ORDER BY lastAccessedAt DESC LIMIT :limit")
    suspend fun getAllMemories(limit: Int = 200): List<MemoryEntity>
}

@Dao
interface ToolConfigDao {
    @Query("SELECT * FROM tool_configs ORDER BY name ASC")
    fun getAllToolConfigs(): Flow<List<ToolConfigEntity>>

    @Query("SELECT * FROM tool_configs WHERE name = :name")
    suspend fun getToolConfig(name: String): ToolConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToolConfig(config: ToolConfigEntity)

    @Update
    suspend fun updateToolConfig(config: ToolConfigEntity)
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills ORDER BY name ASC")
    fun getAllSkills(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills WHERE isEnabled = 1")
    suspend fun getEnabledSkills(): List<SkillEntity>

    @Query("SELECT * FROM skills WHERE id = :id")
    suspend fun getSkill(id: String): SkillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkill(skill: SkillEntity)

    @Update
    suspend fun updateSkill(skill: SkillEntity)

    @Delete
    suspend fun deleteSkill(skill: SkillEntity)
}

@Dao
interface CustomToolDao {
    @Query("SELECT * FROM custom_tools ORDER BY createdAt DESC")
    fun getAllCustomTools(): Flow<List<CustomToolEntity>>

    @Query("SELECT * FROM custom_tools WHERE isEnabled = 1")
    suspend fun getEnabledTools(): List<CustomToolEntity>

    @Query("SELECT * FROM custom_tools WHERE name = :name")
    suspend fun getByName(name: String): CustomToolEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tool: CustomToolEntity)

    @Update
    suspend fun update(tool: CustomToolEntity)

    @Query("DELETE FROM custom_tools WHERE name = :name")
    suspend fun delete(name: String)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE status = :status ORDER BY createdAt DESC LIMIT :limit")
    fun getNotesByStatus(status: String, limit: Int = 200): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY createdAt DESC LIMIT :limit")
    fun getAllNotes(limit: Int = 500): Flow<List<NoteEntity>>

    @Query("SELECT COUNT(*) FROM notes WHERE status = 'pending'")
    fun getPendingCount(): Flow<Int>

    @Insert
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("UPDATE notes SET status = :status, processedAt = :processedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, processedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    @Query("DELETE FROM notes WHERE createdAt < :before")
    suspend fun deleteNotesBefore(before: Long)

    @Query("DELETE FROM notes WHERE status = :status")
    suspend fun deleteByStatus(status: String)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE actionType = :type ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsByType(type: String, limit: Int = 100): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getLogsForSession(sessionId: String): Flow<List<AuditLogEntity>>

    @Insert
    suspend fun insertLog(log: AuditLogEntity): Long

    @Query("DELETE FROM audit_logs WHERE timestamp < :before")
    suspend fun deleteLogsBefore(before: Long)
}

@Dao
interface ScheduledTaskDao {
    @Query("SELECT * FROM scheduled_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<ScheduledTaskEntity>

    @Query("SELECT * FROM scheduled_tasks WHERE enabled = 1")
    suspend fun getAllEnabled(): List<ScheduledTaskEntity>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id")
    suspend fun getById(id: String): ScheduledTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: ScheduledTaskEntity)

    @Update
    suspend fun update(task: ScheduledTaskEntity)

    @Delete
    suspend fun delete(task: ScheduledTaskEntity)

    @Query("UPDATE scheduled_tasks SET sessionId = :sessionId WHERE id = :id")
    suspend fun updateSessionId(id: String, sessionId: String)

    @Query("UPDATE scheduled_tasks SET lastRunAt = :time WHERE id = :id")
    suspend fun updateLastRun(id: String, time: Long)
}

@Dao
interface McpServerConfigDao {
    @Query("SELECT * FROM mcp_server_configs ORDER BY name ASC")
    fun getAllConfigs(): Flow<List<McpServerConfigEntity>>

    @Query("SELECT * FROM mcp_server_configs ORDER BY name ASC")
    suspend fun getAllConfigsOnce(): List<McpServerConfigEntity>

    @Query("SELECT * FROM mcp_server_configs WHERE isEnabled = 1")
    suspend fun getEnabledConfigs(): List<McpServerConfigEntity>

    @Query("SELECT * FROM mcp_server_configs WHERE id = :id")
    suspend fun getConfig(id: String): McpServerConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: McpServerConfigEntity)

    @Update
    suspend fun updateConfig(config: McpServerConfigEntity)

    @Delete
    suspend fun deleteConfig(config: McpServerConfigEntity)

    @Query("DELETE FROM mcp_server_configs WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY triggerAtMs ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY triggerAtMs ASC")
    suspend fun getAllOnce(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE enabled = 1 ORDER BY triggerAtMs ASC")
    suspend fun getAllEnabled(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity)

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM reminders WHERE triggerAtMs < :before AND repeatIntervalMs = 0")
    suspend fun deleteExpired(before: Long = System.currentTimeMillis())

    @Query("UPDATE reminders SET firedCount = firedCount + 1 WHERE id = :id")
    suspend fun incrementFired(id: String)
}

@Dao
interface RagChunkDao {
    @Query("DELETE FROM rag_chunks")
    suspend fun clearAll()

    @Query("DELETE FROM rag_chunks WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Insert
    suspend fun insertChunks(chunks: List<RagChunkEntity>)

    @Query(
        """SELECT * FROM rag_chunks WHERE content LIKE '%' || :q || '%' COLLATE NOCASE 
        OR path LIKE '%' || :q || '%' COLLATE NOCASE LIMIT :limit"""
    )
    suspend fun search(q: String, limit: Int = 24): List<RagChunkEntity>

    @Query("SELECT COUNT(*) FROM rag_chunks")
    suspend fun count(): Int
}
