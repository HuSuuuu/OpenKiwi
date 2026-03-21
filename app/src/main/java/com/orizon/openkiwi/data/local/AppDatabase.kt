package com.orizon.openkiwi.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
        RagChunkEntity::class
    ],
    version = 9,
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "openkiwi_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
