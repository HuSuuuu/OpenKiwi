package com.orizon.openkiwi.core.security

import android.content.Context
import com.orizon.openkiwi.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * GDPR-compliant privacy management: data export, deletion, consent tracking.
 */
class PrivacyManager(
    private val context: Context,
    private val database: AppDatabase
) {

    companion object {
        private const val EXPORT_DIR = "privacy_exports"
    }

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    @Serializable
    data class PrivacyExport(
        val exportDate: String,
        val sessions: Int,
        val messages: Int,
        val memories: Int,
        val auditLogs: Int,
        val skills: Int,
        val modelConfigs: Int,
        val data: PrivacyData
    )

    @Serializable
    data class PrivacyData(
        val sessions: List<SessionExport> = emptyList(),
        val memorySummary: MemorySummary = MemorySummary(),
        val auditLogSummary: AuditSummary = AuditSummary()
    )

    @Serializable
    data class SessionExport(
        val id: String,
        val title: String,
        val messageCount: Int,
        val createdAt: Long,
        val updatedAt: Long
    )

    @Serializable
    data class MemorySummary(
        val totalCount: Int = 0,
        val byType: Map<String, Int> = emptyMap(),
        val byScope: Map<String, Int> = emptyMap()
    )

    @Serializable
    data class AuditSummary(
        val totalCount: Int = 0,
        val byType: Map<String, Int> = emptyMap()
    )

    suspend fun exportAllUserData(): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.filesDir, EXPORT_DIR).also { it.mkdirs() }
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val exportFile = File(exportDir, "openkiwi_data_export_$timestamp.json")

        val sessions = database.sessionDao().getAllSessionsOnce()
        val memories = database.memoryDao().getAllMemories(10000)

        val sessionExports = sessions.map { s ->
            val msgs = database.messageDao().getMessagesForSessionOnce(s.id)
            SessionExport(s.id, s.title, msgs.size, s.createdAt, s.updatedAt)
        }

        val memoryByType = memories.groupBy { it.type }.mapValues { it.value.size }
        val memoryByScope = memories.groupBy { it.scope }.mapValues { it.value.size }

        val export = PrivacyExport(
            exportDate = timestamp,
            sessions = sessions.size,
            messages = sessions.sumOf {
                database.messageDao().getMessagesForSessionOnce(it.id).size
            },
            memories = memories.size,
            auditLogs = 0,
            skills = database.skillDao().getEnabledSkills().size,
            modelConfigs = 0,
            data = PrivacyData(
                sessions = sessionExports,
                memorySummary = MemorySummary(memories.size, memoryByType, memoryByScope),
                auditLogSummary = AuditSummary()
            )
        )

        exportFile.writeText(json.encodeToString(PrivacyExport.serializer(), export))
        exportFile
    }

    suspend fun deleteAllUserData() = withContext(Dispatchers.IO) {
        val sessions = database.sessionDao().getAllSessionsOnce()
        for (session in sessions) {
            database.messageDao().deleteMessagesForSession(session.id)
            database.sessionDao().deleteSessionById(session.id)
        }

        database.memoryDao().clearMemoriesByType("SHORT_TERM")
        database.memoryDao().clearMemoriesByType("LONG_TERM")
        database.memoryDao().clearMemoriesByType("CONTEXTUAL")

        val oneDayAgo = System.currentTimeMillis() - 1
        database.auditLogDao().deleteLogsBefore(System.currentTimeMillis() + 1)

        // Clear exported files
        File(context.filesDir, EXPORT_DIR).deleteRecursively()
    }

    suspend fun getDataSummary(): Map<String, Int> = withContext(Dispatchers.IO) {
        mapOf(
            "sessions" to (database.sessionDao().getAllSessionsOnce().size),
            "memories" to (database.memoryDao().getAllMemories(10000).size),
            "skills" to (database.skillDao().getEnabledSkills().size)
        )
    }
}
