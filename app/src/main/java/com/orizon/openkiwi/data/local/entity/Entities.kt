package com.orizon.openkiwi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.orizon.openkiwi.data.local.Converters

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String = "",
    val modelConfigId: String = "",
    val systemPrompt: String = "",
    val status: String = "IDLE",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String,
    val content: String? = null,
    val toolCallsJson: String? = null,
    val toolCallId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "artifacts")
data class ArtifactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val messageId: Long? = null,
    val toolName: String,
    val filePath: String,
    val displayName: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "model_configs")
data class ModelConfigEntity(
    @PrimaryKey val id: String,
    val name: String,
    val apiBaseUrl: String,
    val apiKey: String,
    val modelName: String,
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
    val topP: Double = 1.0,
    val frequencyPenalty: Double = 0.0,
    val presencePenalty: Double = 0.0,
    val timeoutSeconds: Int = 60,
    val maxRetries: Int = 3,
    val proxyHost: String? = null,
    val proxyPort: Int? = null,
    val isDefault: Boolean = false,
    val supportsVision: Boolean = false,
    val supportsTools: Boolean = true,
    val supportsStreaming: Boolean = true,
    val sceneTagsJson: String = "[]",
    val reasoningEffort: String = "low",
    val isSmallModel: Boolean = false,
    val includeWebSearchTool: Boolean = false,
    val webSearchExclusive: Boolean = false,
    val providerType: String = "openai",
    val maxToolIterations: Int = 10
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val category: String = "",
    val key: String,
    val content: String,
    val embedding: String? = null,
    val importance: Float = 0.5f,
    val accessCount: Int = 0,
    val scope: String = "",
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tool_configs")
data class ToolConfigEntity(
    @PrimaryKey val name: String,
    val isEnabled: Boolean = true,
    val permissionLevel: String = "NORMAL",
    val configJson: String = "{}"
)

@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val type: String,
    val category: String = "",
    val version: String = "1.0",
    val definitionJson: String,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourcePackage: String,
    val sourceTitle: String,
    val sourceContent: String,
    val summary: String = "",
    val category: String = "general",
    val status: String = "pending",
    val importance: Int = 0,
    val suggestedAction: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val processedAt: Long? = null
)

@Entity(tableName = "custom_tools")
data class CustomToolEntity(
    @PrimaryKey val name: String,
    val description: String,
    val paramsJson: String = "{}",
    val requiredParamsJson: String = "[]",
    val script: String,
    val language: String = "shell",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String = "agent"
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val actor: String,
    val actionType: String,
    val actionDetail: String,
    val result: String,
    val permissionUsed: String? = null,
    val sessionId: String? = null
)

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey val id: String,
    val name: String,
    val prompt: String,
    /** Repeat interval; WorkManager requires at least 15 minutes. */
    val intervalMinutes: Long = 60L,
    val enabled: Boolean = true,
    val sessionId: String? = null,
    val lastRunAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "mcp_server_configs")
data class McpServerConfigEntity(
    @PrimaryKey val id: String,
    val name: String,
    val transportType: String = "sse",
    val url: String = "",
    val command: String = "",
    val args: String = "[]",
    val env: String = "{}",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val message: String,
    val triggerAtMs: Long,
    val repeatIntervalMs: Long = 0,
    val sessionId: String? = null,
    val enabled: Boolean = true,
    val firedCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "rag_chunks",
    indices = [androidx.room.Index(value = ["path"])]
)
data class RagChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val chunkIndex: Int,
    val content: String,
    val updatedAt: Long = System.currentTimeMillis()
)
