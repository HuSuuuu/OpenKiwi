package com.orizon.openkiwi.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.orizon.openkiwi.data.local.dao.*
import com.orizon.openkiwi.data.local.entity.*

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        ModelConfigEntity::class,
        MemoryEntity::class,
        ToolConfigEntity::class,
        SkillEntity::class,
        NoteEntity::class,
        CustomToolEntity::class,
        AuditLogEntity::class,
        ArtifactEntity::class,
        ScheduledTaskEntity::class,
        ReminderEntity::class,
        RagChunkEntity::class,
        McpServerConfigEntity::class
    ],
    version = 13,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun modelConfigDao(): ModelConfigDao
    abstract fun memoryDao(): MemoryDao
    abstract fun toolConfigDao(): ToolConfigDao
    abstract fun skillDao(): SkillDao
    abstract fun noteDao(): NoteDao
    abstract fun customToolDao(): CustomToolDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun artifactDao(): ArtifactDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao
    abstract fun ragChunkDao(): RagChunkDao
    abstract fun reminderDao(): ReminderDao
    abstract fun mcpServerConfigDao(): McpServerConfigDao

    companion object {
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE model_configs ADD COLUMN includeWebSearchTool INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE model_configs ADD COLUMN webSearchExclusive INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE model_configs ADD COLUMN providerType TEXT NOT NULL DEFAULT 'openai'")
                db.execSQL("ALTER TABLE model_configs ADD COLUMN maxToolIterations INTEGER NOT NULL DEFAULT 10")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS mcp_server_configs (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        transportType TEXT NOT NULL DEFAULT 'sse',
                        url TEXT NOT NULL DEFAULT '',
                        command TEXT NOT NULL DEFAULT '',
                        args TEXT NOT NULL DEFAULT '[]',
                        env TEXT NOT NULL DEFAULT '{}',
                        isEnabled INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS reminders (
                        id TEXT NOT NULL PRIMARY KEY,
                        message TEXT NOT NULL,
                        triggerAtMs INTEGER NOT NULL,
                        repeatIntervalMs INTEGER NOT NULL DEFAULT 0,
                        sessionId TEXT,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        firedCount INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "openkiwi_database"
                )
                    .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_12_13)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
